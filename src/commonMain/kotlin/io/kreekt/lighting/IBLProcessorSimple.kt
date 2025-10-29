/**
 * Simple IBL Processor Implementation
 * Provides a lightweight CPU implementation of key IBL preprocessing steps.
 */
package io.kreekt.lighting

import io.kreekt.core.math.Vector2
import io.kreekt.core.math.Vector3
import io.kreekt.renderer.CubeFace
import io.kreekt.renderer.CubeTexture
import io.kreekt.renderer.CubeTextureImpl
import io.kreekt.renderer.Texture
import io.kreekt.renderer.TextureFilter
import io.kreekt.renderer.TextureFormat
import io.kreekt.texture.Texture2D
import io.kreekt.texture.CubeTexture as RuntimeCubeTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class IBLProcessorSimple : IBLProcessor {

    companion object {
        private const val IRRADIANCE_SAMPLE_COUNT = 128
        private const val PREFILTER_SAMPLE_COUNT = 256
        private const val BRDF_SAMPLE_COUNT = 256
    }

    override suspend fun generateEquirectangularMap(
        cubeMap: CubeTexture,
        width: Int,
        height: Int
    ): Texture = withContext(Dispatchers.Default) {
        val texture = Texture2D(
            width = width,
            height = height,
            format = TextureFormat.RGBA32F,
            textureName = "Equirect_${width}x${height}"
        )

        val pixels = FloatArray(width * height * 4)
        for (y in 0 until height) {
            val v = (y + 0.5f) / height.toFloat()
            for (x in 0 until width) {
                val u = (x + 0.5f) / width.toFloat()
                val direction = equirectangularDirection(u, v)
                val color = sampleEnvironment(cubeMap, direction)
                val idx = (y * width + x) * 4
                pixels[idx] = color.x
                pixels[idx + 1] = color.y
                pixels[idx + 2] = color.z
                pixels[idx + 3] = 1f
            }
        }

        texture.setFloatData(pixels)
        texture
    }

    override suspend fun generateIrradianceMap(
        environmentMap: Texture,
        size: Int
    ): CubeTexture = withContext(Dispatchers.Default) {
        val result = CubeTextureImpl(
            size = size,
            format = TextureFormat.RGBA32F,
            filter = TextureFilter.LINEAR,
            generateMipmaps = false,
            textureName = "Irradiance_$size"
        )

        for (face in CubeFace.values()) {
            val faceData = FloatArray(size * size * 4)
            for (y in 0 until size) {
                val v = (y + 0.5f) / size.toFloat()
                for (x in 0 until size) {
                    val u = (x + 0.5f) / size.toFloat()
                    val normal = faceTexelDirection(face, u, v)
                    val (tangent, bitangent) = buildTangentBasis(normal)

                    val accumulated = Vector3(0f, 0f, 0f)
                    repeat(IRRADIANCE_SAMPLE_COUNT) { i ->
                        val xi = hammersley(i, IRRADIANCE_SAMPLE_COUNT)
                        val hemisphereSample = importanceSampleCosine(xi)
                        val worldDir = toWorld(hemisphereSample, tangent, bitangent, normal)
                        val sample = sampleEnvironment(environmentMap, worldDir)
                        accumulated.add(sample)
                    }

                    accumulated.multiplyScalar(1f / IRRADIANCE_SAMPLE_COUNT.toFloat())
                    val idx = (y * size + x) * 4
                    faceData[idx] = accumulated.x
                    faceData[idx + 1] = accumulated.y
                    faceData[idx + 2] = accumulated.z
                    faceData[idx + 3] = 1f
                }
            }
            result.setFaceData(face, faceData)
        }
        result
    }

    override suspend fun generatePrefilterMap(
        environmentMap: Texture,
        size: Int,
        roughnessLevels: Int
    ): CubeTexture = withContext(Dispatchers.Default) {
        val result = CubeTextureImpl(
            size = size,
            format = TextureFormat.RGBA32F,
            filter = TextureFilter.LINEAR,
            generateMipmaps = true,
            textureName = "Prefilter_$size"
        )

        val levels = max(1, roughnessLevels)
        for (level in 0 until levels) {
            val mipSize = max(1, size shr level)
            val roughness = if (levels == 1) 0f else level.toFloat() / (levels - 1).toFloat()
            val sampleCount = if (level == 0) PREFILTER_SAMPLE_COUNT / 2 else PREFILTER_SAMPLE_COUNT

            for (face in CubeFace.values()) {
                val faceData = FloatArray(mipSize * mipSize * 4)
                for (y in 0 until mipSize) {
                    val v = (y + 0.5f) / mipSize.toFloat()
                    for (x in 0 until mipSize) {
                        val u = (x + 0.5f) / mipSize.toFloat()
                        val normal = faceTexelDirection(face, u, v)
                        val (tangent, bitangent) = buildTangentBasis(normal)

                        val accumulated = Vector3(0f, 0f, 0f)
                        var totalWeight = 0f
                        repeat(sampleCount) { i ->
                            val xi = hammersley(i, sampleCount)
                            val halfVectorLocal = importanceSampleGGX(xi, roughness)
                            val halfVector = toWorld(halfVectorLocal, tangent, bitangent, normal)
                            val dotNH = normal.dot(halfVector)
                            if (dotNH > 0f) {
                                val reflection = halfVector.clone()
                                    .multiplyScalar(2f * dotNH)
                                    .subtract(normal)
                                    .normalize()

                                val ndotl = normal.dot(reflection).coerceAtLeast(0f)
                                if (ndotl > 0f) {
                                    val sample = sampleEnvironment(environmentMap, reflection)
                                    accumulated.add(sample.multiplyScalar(ndotl))
                                    totalWeight += ndotl
                                }
                            }
                        }

                        if (totalWeight > 0f) {
                            accumulated.multiplyScalar(1f / totalWeight)
                        }

                        val idx = (y * mipSize + x) * 4
                        faceData[idx] = accumulated.x
                        faceData[idx + 1] = accumulated.y
                        faceData[idx + 2] = accumulated.z
                        faceData[idx + 3] = 1f
                    }
                }
                result.setFaceData(face, faceData, level)
            }
        }
        result
    }

    override fun generateBRDFLUT(size: Int): Texture {
        val texture = Texture2D(
            width = size,
            height = size,
            format = TextureFormat.RGBA32F,
            textureName = "BRDF_LUT_$size"
        )

        val data = FloatArray(size * size * 4)
        for (y in 0 until size) {
            val roughness = (y + 0.5f) / size.toFloat()
            for (x in 0 until size) {
                val ndotv = (x + 0.5f) / size.toFloat()
                val integrated = integrateBRDF(ndotv.coerceIn(0.0001f, 0.9999f), roughness)
                val idx = (y * size + x) * 4
                data[idx] = integrated.x
                data[idx + 1] = integrated.y
                data[idx + 2] = 0f
                data[idx + 3] = 1f
            }
        }

        texture.setFloatData(data)
        return texture
    }

    private fun equirectangularDirection(u: Float, v: Float): Vector3 {
        val theta = (u - 0.5f) * 2f * PI.toFloat()
        val phi = v * PI.toFloat()
        val sinPhi = sin(phi)
        return Vector3(
            sinPhi * cos(theta),
            cos(phi),
            sinPhi * sin(theta)
        ).normalize()
    }

    private fun faceTexelDirection(face: CubeFace, u: Float, v: Float): Vector3 {
        val texU = 2f * u - 1f
        val texV = 2f * v - 1f
        val direction = when (face) {
            CubeFace.POSITIVE_X -> Vector3(1f, -texV, -texU)
            CubeFace.NEGATIVE_X -> Vector3(-1f, -texV, texU)
            CubeFace.POSITIVE_Y -> Vector3(texU, 1f, texV)
            CubeFace.NEGATIVE_Y -> Vector3(texU, -1f, -texV)
            CubeFace.POSITIVE_Z -> Vector3(texU, -texV, 1f)
            CubeFace.NEGATIVE_Z -> Vector3(-texU, -texV, -1f)
        }
        return direction.normalize()
    }

    private fun buildTangentBasis(normal: Vector3): Pair<Vector3, Vector3> {
        val n = normal.clone().normalize()
        val up = if (abs(n.y) < 0.999f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
        val tangent = Vector3().crossVectors(up, n).normalize()
        val bitangent = Vector3().crossVectors(n, tangent)
        return tangent to bitangent
    }

    private fun toWorld(local: Vector3, tangent: Vector3, bitangent: Vector3, normal: Vector3): Vector3 {
        return Vector3(
            tangent.x * local.x + bitangent.x * local.y + normal.x * local.z,
            tangent.y * local.x + bitangent.y * local.y + normal.y * local.z,
            tangent.z * local.x + bitangent.z * local.y + normal.z * local.z
        ).normalize()
    }

    private fun sampleEnvironment(texture: Texture, direction: Vector3): Vector3 {
        return when (texture) {
            is CubeTexture -> sampleCubeTexture(texture, direction)
            is Texture2D -> sampleEquirectangularTexture(texture, direction)
            else -> Vector3.ZERO.clone()
        }
    }

    private fun sampleCubeTexture(cubeTexture: CubeTexture, direction: Vector3): Vector3 {
        val dir = direction.clone().normalize()
        val absX = abs(dir.x)
        val absY = abs(dir.y)
        val absZ = abs(dir.z)

        val (face, u, v) = when {
            absX >= absY && absX >= absZ ->
                if (dir.x > 0) Triple(CubeFace.POSITIVE_X, -dir.z / dir.x, -dir.y / dir.x)
                else Triple(CubeFace.NEGATIVE_X, dir.z / dir.x, -dir.y / dir.x)

            absY >= absZ ->
                if (dir.y > 0) Triple(CubeFace.POSITIVE_Y, dir.x / dir.y, dir.z / dir.y)
                else Triple(CubeFace.NEGATIVE_Y, dir.x / dir.y, -dir.z / dir.y)

            else ->
                if (dir.z > 0) Triple(CubeFace.POSITIVE_Z, dir.x / dir.z, -dir.y / dir.z)
                else Triple(CubeFace.NEGATIVE_Z, -dir.x / dir.z, -dir.y / dir.z)
        }

        val texU = ((u + 1f) * 0.5f).coerceIn(0f, 1f)
        val texV = ((v + 1f) * 0.5f).coerceIn(0f, 1f)
        return when (cubeTexture) {
            is CubeTextureImpl -> sampleCubeTextureImpl(cubeTexture, face, texU, texV)
            is RuntimeCubeTexture -> cubeTexture.sampleFace(face.ordinal, texU, texV)
            else -> Vector3.ZERO.clone()
        }
    }

    private fun sampleCubeTextureImpl(texture: CubeTextureImpl, face: CubeFace, u: Float, v: Float): Vector3 {
        val floatData = texture.getFaceFloatData(face)
        if (floatData == null) {
            return Vector3.ZERO.clone()
        }

        val size = texture.size
        val x = (u * (size - 1)).roundToInt().coerceIn(0, size - 1)
        val y = (v * (size - 1)).roundToInt().coerceIn(0, size - 1)
        val idx = (y * size + x) * 4
        return Vector3(
            floatData.getOrElse(idx) { 0f },
            floatData.getOrElse(idx + 1) { 0f },
            floatData.getOrElse(idx + 2) { 0f }
        )
    }

    private fun sampleEquirectangularTexture(texture: Texture2D, direction: Vector3): Vector3 {
        val data = texture.getFloatData()
        val bytes = texture.getData()
        require(data != null || bytes != null) { "Texture ${texture.name} has no pixel data" }

        val dir = direction.clone().normalize()
        val phi = atan2(dir.z, dir.x)
        val theta = acos(dir.y.coerceIn(-1f, 1f))

        val u = ((phi / (2f * PI.toFloat())) + 1f) % 1f
        val v = theta / PI.toFloat()

        val x = ((u * (texture.width - 1)).roundToInt()).coerceIn(0, texture.width - 1)
        val y = ((v * (texture.height - 1)).roundToInt()).coerceIn(0, texture.height - 1)
        val index = (y * texture.width + x) * 4

        return if (data != null) {
            Vector3(data[index], data[index + 1], data[index + 2])
        } else {
            val byteData = bytes!!
            Vector3(
                (byteData[index].toInt() and 0xFF) / 255f,
                (byteData[index + 1].toInt() and 0xFF) / 255f,
                (byteData[index + 2].toInt() and 0xFF) / 255f
            )
        }
    }

    private fun hammersley(i: Int, n: Int): Vector2 {
        return Vector2(i.toFloat() / n.toFloat(), radicalInverseVdC(i))
    }

    private fun radicalInverseVdC(bits: Int): Float {
        var b = bits
        b = (b shl 16) or (b ushr 16)
        b = ((b and 0x55555555) shl 1) or ((b and 0xAAAAAAAA.toInt()) ushr 1)
        b = ((b and 0x33333333) shl 2) or ((b and 0xCCCCCCCC.toInt()) ushr 2)
        b = ((b and 0x0F0F0F0F) shl 4) or ((b and 0xF0F0F0F0.toInt()) ushr 4)
        b = ((b and 0x00FF00FF) shl 8) or ((b and 0xFF00FF00.toInt()) ushr 8)
        return (b ushr 1) * 2.3283064365386963e-10f
    }

    private fun importanceSampleCosine(xi: Vector2): Vector3 {
        val r = sqrt(xi.x)
        val phi = 2f * PI.toFloat() * xi.y
        val x = r * cos(phi)
        val y = r * sin(phi)
        val z = sqrt(max(0f, 1f - x * x - y * y))
        return Vector3(x, y, z)
    }

    private fun importanceSampleGGX(xi: Vector2, roughness: Float): Vector3 {
        val a = roughness * roughness
        val phi = 2f * PI.toFloat() * xi.x
        val cosTheta = sqrt((1f - xi.y) / (1f + (a * a - 1f) * xi.y))
        val sinTheta = sqrt(max(0f, 1f - cosTheta * cosTheta))
        val x = cos(phi) * sinTheta
        val y = sin(phi) * sinTheta
        val z = cosTheta
        return Vector3(x, y, z)
    }

    private fun integrateBRDF(ndotv: Float, roughness: Float): Vector2 {
        val v = Vector3(sqrt(max(0f, 1f - ndotv * ndotv)), 0f, ndotv)
        var a = 0f
        var b = 0f

        repeat(BRDF_SAMPLE_COUNT) { i ->
            val xi = hammersley(i, BRDF_SAMPLE_COUNT)
                val hLocal = importanceSampleGGX(xi, roughness)
                val h = toWorld(hLocal, Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
                val l = h.clone()
                    .multiplyScalar(2f * v.dot(h))
                    .subtract(v)
                    .normalize()

            val ndotl = max(l.z, 0f)
            val ndoth = max(h.z, 0f)
            val vdoth = max(v.dot(h), 0f)

            if (ndotl > 0f) {
                val g = geometrySmith(ndotv, ndotl, roughness)
                val numerator = g * vdoth
                val denominator = ndoth * ndotv + 1e-6f
                val spec = numerator / denominator
                val fc = (1f - vdoth).pow(5)
                a += (1f - fc) * spec
                b += fc * spec
            }
        }

        val scale = 1f / BRDF_SAMPLE_COUNT.toFloat()
        return Vector2(a * scale, b * scale)
    }

    private fun geometrySmith(ndotv: Float, ndotl: Float, roughness: Float): Float {
        val ggx1 = geometrySchlickGGX(ndotv, roughness)
        val ggx2 = geometrySchlickGGX(ndotl, roughness)
        return ggx1 * ggx2
    }

    private fun geometrySchlickGGX(ndotv: Float, roughness: Float): Float {
        val r = roughness + 1f
        val k = (r * r) / 8f
        return ndotv / (ndotv * (1f - k) + k)
    }
}
