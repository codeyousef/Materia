package io.kreekt.texture

import io.kreekt.core.math.Vector2
import io.kreekt.core.math.Vector3
import io.kreekt.renderer.Renderer
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

/**
 * PMREMGenerator - Pre-filtered Mipmap Roughness Environment Map Generator.
 *
 * Produces filtered cube maps suitable for physically based lighting by
 * importance sampling the source environment with a GGX distribution.
 */
class PMREMGenerator(private val renderer: Renderer) {

    /**
     * Generate a pre-filtered environment map from an existing cube texture.
     *
     * @param cubeTexture Source texture (must contain data for all six faces).
     * @param sampleCount Number of samples per texel used for integration.
     * @param roughnessLevels Number of roughness levels to generate (≥ 1).
     */
    fun fromCubeMap(
        cubeTexture: CubeTexture,
        sampleCount: Int = 256,
        roughnessLevels: Int = DEFAULT_ROUGHNESS_LEVELS
    ): CubeTexture {
        require(cubeTexture.isComplete()) { "Cube texture must have data for all faces" }
        require(sampleCount > 0) { "sampleCount must be > 0 (was $sampleCount)" }
        require(roughnessLevels > 0) { "roughnessLevels must be > 0 (was $roughnessLevels)" }

        val baseSize = cubeTexture.size
        val clampedLevels = roughnessLevels.coerceAtMost(1 + log2Floor(baseSize))
        val mipData = mutableListOf<Array<ByteArray>>()

        var currentSize = baseSize
        for (level in 0 until clampedLevels) {
            val roughness = if (clampedLevels == 1) 0f else (level.toFloat() / (clampedLevels - 1)).pow(2f)
            val levelData = prefilterLevel(
                cubeTexture = cubeTexture,
                targetSize = currentSize,
                roughness = roughness,
                sampleCount = if (roughness == 0f) 1 else sampleCount
            )
            mipData.add(levelData)
            if (currentSize > 1) {
                currentSize = max(1, currentSize / 2)
            }
        }

        // Create resulting cube texture and populate mip levels.
        val result = CubeTexture(baseSize).apply {
            setTextureName("${cubeTexture.name}_pmrem")
            generateMipmaps = mipData.size > 1
        }

        val baseLevel = mipData.first()
        CubeFace.values().forEach { face ->
            result.setFaceData(face, baseLevel[face.ordinal])
        }

        if (mipData.size > 1) {
            result.mipmaps = Array(mipData.size - 1) { level ->
                mipData[level + 1]
            }
        }

        return result
    }

    /**
     * Convert an equirectangular texture to a cube map and pre-filter it.
     *
     * @param texture Equirectangular 2D texture (Texture2D required).
     * @param cubeSize Desired cube face resolution.
     */
    fun fromEquirectangular(
        texture: Texture,
        cubeSize: Int = DEFAULT_CUBE_SIZE,
        sampleCount: Int = 256,
        roughnessLevels: Int = DEFAULT_ROUGHNESS_LEVELS
    ): CubeTexture {
        val source = texture as? Texture2D
            ?: throw IllegalArgumentException("PMREMGenerator expects a Texture2D for equirectangular input")

        val faces = Array(6) { ByteArray(cubeSize * cubeSize * 4) }
        for (face in CubeFace.values()) {
            val target = faces[face.ordinal]
            for (y in 0 until cubeSize) {
                for (x in 0 until cubeSize) {
                    val u = (x + 0.5f) / cubeSize.toFloat()
                    val v = (y + 0.5f) / cubeSize.toFloat()
                    val direction = faceTexelDirection(face, u, v)
                    val color = sampleEquirectangular(source, direction)
                    val index = (y * cubeSize + x) * 4
                    target[index] = (color.x.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 1] = (color.y.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 2] = (color.z.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 3] = 255.toByte()
                }
            }
        }

        val cubeTexture = CubeTexture(cubeSize).apply {
            CubeFace.values().forEach { face ->
                setFaceData(face, faces[face.ordinal])
            }
            setTextureName("${texture.name}_cubemap")
        }

        return fromCubeMap(cubeTexture, sampleCount, roughnessLevels)
    }

    /**
     * Generate GGX importance sampling directions for a given roughness.
     */
    fun generateGGXSamples(roughness: Float, sampleCount: Int): List<Vector3> {
        require(sampleCount > 0) { "sampleCount must be > 0 (was $sampleCount)" }
        val samples = ArrayList<Vector3>(sampleCount)
        val clampedRoughness = roughness.coerceIn(0f, 1f).coerceAtLeast(1e-4f)

        for (i in 0 until sampleCount) {
            val xi = hammersley(i, sampleCount)
            samples += importanceSampleGGX(xi, clampedRoughness)
        }
        return samples
    }

    /**
     * Compute third-order spherical harmonics coefficients from the cube map.
     */
    fun generateSphericalHarmonics(
        cubeTexture: CubeTexture,
        sampleCount: Int = 1024
    ): SphericalHarmonics {
        require(sampleCount > 0) { "sampleCount must be > 0 (was $sampleCount)" }
        val coefficients = Array(9) { Vector3(0f, 0f, 0f) }
        val weight = (4f * PI.toFloat()) / sampleCount

        uniformSampleDirections(sampleCount).forEach { direction ->
            val color = sampleCubeTexture(cubeTexture, direction)
            val basis = shBasis(direction)
            for (i in coefficients.indices) {
                val contribution = color.clone().multiply(basis[i] * weight)
                coefficients[i].add(contribution)
            }
        }

        return SphericalHarmonics(coefficients.toList())
    }

    fun dispose() {
        // No GPU allocations are owned directly by PMREMGenerator.
    }

    private fun prefilterLevel(
        cubeTexture: CubeTexture,
        targetSize: Int,
        roughness: Float,
        sampleCount: Int
    ): Array<ByteArray> {
        val levelData = Array(6) { ByteArray(targetSize * targetSize * 4) }

        for (face in CubeFace.values()) {
            val target = levelData[face.ordinal]
            for (y in 0 until targetSize) {
                for (x in 0 until targetSize) {
                    val u = (x + 0.5f) / targetSize.toFloat()
                    val v = (y + 0.5f) / targetSize.toFloat()
                    val direction = faceTexelDirection(face, u, v)

                    val color = if (roughness <= 0f) {
                        sampleCubeTexture(cubeTexture, direction)
                    } else {
                        prefilterColor(cubeTexture, direction, roughness, sampleCount)
                    }

                    val index = (y * targetSize + x) * 4
                    target[index] = (color.x.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 1] = (color.y.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 2] = (color.z.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                    target[index + 3] = 255.toByte()
                }
            }
        }
        return levelData
    }

    private fun prefilterColor(
        cubeTexture: CubeTexture,
        direction: Vector3,
        roughness: Float,
        sampleCount: Int
    ): Vector3 {
        val normal = direction.clone().normalize()
        val (tangent, bitangent, normalizedNormal) = createTangentBasis(normal)
        val samples = generateGGXSamples(roughness, sampleCount)

        var totalWeight = 0f
        val result = Vector3(0f, 0f, 0f)

        samples.forEach { sample ->
            val worldSample = toWorldSpace(sample, tangent, bitangent, normalizedNormal)
            val ndotl = normalizedNormal.dot(worldSample).coerceAtLeast(0f)
            if (ndotl > 0f) {
                val color = sampleCubeTexture(cubeTexture, worldSample).multiply(ndotl)
                result.add(color)
                totalWeight += ndotl
            }
        }

        return if (totalWeight > 0f) result.divide(totalWeight) else result
    }

    private fun faceTexelDirection(face: CubeFace, u: Float, v: Float): Vector3 {
        val nx = 2f * u - 1f
        val ny = 2f * v - 1f
        val direction = when (face) {
            CubeFace.POSITIVE_X -> Vector3(1f, -ny, -nx)
            CubeFace.NEGATIVE_X -> Vector3(-1f, -ny, nx)
            CubeFace.POSITIVE_Y -> Vector3(nx, 1f, ny)
            CubeFace.NEGATIVE_Y -> Vector3(nx, -1f, -ny)
            CubeFace.POSITIVE_Z -> Vector3(nx, -ny, 1f)
            CubeFace.NEGATIVE_Z -> Vector3(-nx, -ny, -1f)
        }
        return direction.normalize()
    }

    private fun sampleEquirectangular(texture: Texture2D, direction: Vector3): Vector3 {
        val data = texture.getFloatData()
        val bytes = texture.getData()
        require(data != null || bytes != null) { "Texture ${texture.name} has no pixel data" }

        val dir = direction.clone().normalize()
        val phi = atan2Safe(dir.z, dir.x)
        val theta = acos(dir.y.coerceIn(-1f, 1f))

        val u = ((phi / (2f * PI.toFloat())) + 1f) % 1f
        val v = theta / PI.toFloat()

        val x = ((u * (texture.width - 1)).roundToInt()).coerceIn(0, texture.width - 1)
        val y = ((v * (texture.height - 1)).roundToInt()).coerceIn(0, texture.height - 1)
        val index = (y * texture.width + x) * 4

        return if (data != null) {
            Vector3(data[index], data[index + 1], data[index + 2])
        } else {
            Vector3(
                ((bytes!![index].toInt() and 0xFF) / 255f),
                ((bytes[index + 1].toInt() and 0xFF) / 255f),
                ((bytes[index + 2].toInt() and 0xFF) / 255f)
            )
        }
    }

    private fun sampleCubeTexture(cubeTexture: CubeTexture, direction: Vector3): Vector3 {
        val dir = direction.clone().normalize()
        val absX = abs(dir.x)
        val absY = abs(dir.y)
        val absZ = abs(dir.z)

        val (face, u, v) = when {
            absX >= absY && absX >= absZ -> {
                if (dir.x > 0) {
                    Triple(CubeFace.POSITIVE_X, -dir.z / dir.x, -dir.y / dir.x)
                } else {
                    Triple(CubeFace.NEGATIVE_X, dir.z / dir.x, -dir.y / dir.x)
                }
            }

            absY >= absZ -> {
                if (dir.y > 0) {
                    Triple(CubeFace.POSITIVE_Y, dir.x / dir.y, dir.z / dir.y)
                } else {
                    Triple(CubeFace.NEGATIVE_Y, dir.x / dir.y, -dir.z / dir.y)
                }
            }

            else -> {
                if (dir.z > 0) {
                    Triple(CubeFace.POSITIVE_Z, dir.x / dir.z, -dir.y / dir.z)
                } else {
                    Triple(CubeFace.NEGATIVE_Z, -dir.x / dir.z, -dir.y / dir.z)
                }
            }
        }

        val texU = (u + 1f) * 0.5f
        val texV = (v + 1f) * 0.5f
        return cubeTexture.sampleFace(face.ordinal, texU, texV)
    }

    private fun hammersley(i: Int, n: Int): Vector2 {
        return Vector2(i.toFloat() / n.toFloat(), radicalInverseVdC(i))
    }

    private fun importanceSampleGGX(xi: Vector2, roughness: Float): Vector3 {
        val alpha = roughness * roughness
        val phi = 2f * PI.toFloat() * xi.x
        val cosTheta = sqrt((1f - xi.y) / (1f + (alpha * alpha - 1f) * xi.y))
        val sinTheta = sqrt(1f - cosTheta * cosTheta)
        val x = cos(phi) * sinTheta
        val y = sin(phi) * sinTheta
        val z = cosTheta
        return Vector3(x, y, z).normalize()
    }

    private fun createTangentBasis(normal: Vector3): Triple<Vector3, Vector3, Vector3> {
        val n = normal.clone().normalize()
        val up = if (abs(n.z) < 0.999f) Vector3(0f, 0f, 1f) else Vector3(1f, 0f, 0f)
        val tangent = Vector3().crossVectors(up, n).normalize()
        val bitangent = Vector3().crossVectors(n, tangent).normalize()
        return Triple(tangent, bitangent, n)
    }

    private fun toWorldSpace(sample: Vector3, tangent: Vector3, bitangent: Vector3, normal: Vector3): Vector3 {
        return Vector3(
            sample.x * tangent.x + sample.y * bitangent.x + sample.z * normal.x,
            sample.x * tangent.y + sample.y * bitangent.y + sample.z * normal.y,
            sample.x * tangent.z + sample.y * bitangent.z + sample.z * normal.z
        ).normalize()
    }

    private fun radicalInverseVdC(bits: Int): Float {
        var b = bits
        b = (b shl 16) or (b ushr 16)
        b = ((b and 0x55555555) shl 1) or ((b and 0xAAAAAAAA.toInt()) ushr 1)
        b = ((b and 0x33333333) shl 2) or ((b and 0xCCCCCCCC.toInt()) ushr 2)
        b = ((b and 0x0F0F0F0F) shl 4) or ((b and 0xF0F0F0F0.toInt()) ushr 4)
        b = ((b and 0x00FF00FF) shl 8) or ((b and 0xFF00FF00.toInt()) ushr 8)
        val unsigned = java.lang.Integer.toUnsignedLong(b)
        return (unsigned.toDouble() * 2.3283064365386963e-10).toFloat()
    }

    private fun uniformSampleDirections(sampleCount: Int): List<Vector3> {
        val directions = ArrayList<Vector3>(sampleCount)
        val increment = PI.toFloat() * (3f - sqrt(5f))
        val offset = 2f / sampleCount

        for (i in 0 until sampleCount) {
            val y = i * offset - 1f + offset / 2f
            val r = sqrt(1f - y * y)
            val phi = i * increment
            val x = cos(phi) * r
            val z = sin(phi) * r
            directions += Vector3(x, y, z).normalize()
        }

        return directions
    }

    private fun shBasis(direction: Vector3): FloatArray {
        val x = direction.x
        val y = direction.y
        val z = direction.z

        return floatArrayOf(
            0.282095f,
            0.488603f * y,
            0.488603f * z,
            0.488603f * x,
            1.092548f * x * y,
            1.092548f * y * z,
            0.315392f * (3f * z * z - 1f),
            1.092548f * x * z,
            0.546274f * (x * x - y * y)
        )
    }

    private fun log2Floor(value: Int): Int {
        var result = 0
        var current = value
        while (current > 1) {
            current /= 2
            result++
        }
        return result
    }

    private fun atan2Safe(y: Float, x: Float): Float {
        val angle = atan2(y, x)
        return if (angle < 0f) angle + 2f * PI.toFloat() else angle
    }

    companion object {
        private const val DEFAULT_CUBE_SIZE = 256
        private const val DEFAULT_ROUGHNESS_LEVELS = 6
    }
}

/**
 * Spherical harmonics data structure.
 */
data class SphericalHarmonics(
    val coefficients: List<Vector3>
)
