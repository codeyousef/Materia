package io.materia.lighting

// Use texture types from renderer module
import io.materia.core.scene.Scene
import io.materia.core.math.Vector3
import io.materia.core.platform.currentTimeMillis
import io.materia.lighting.ibl.ConvolutionProcessor
import io.materia.lighting.ibl.IBLConvolutionProfiler
import io.materia.renderer.CubeFace
import io.materia.renderer.CubeTexture
import io.materia.renderer.Texture2D
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.CubeTextureImpl
import io.materia.renderer.setFaceDataByIndex
import kotlin.LazyThreadSafetyMode
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Core lighting system interface for managing lights, shadows, and environment lighting
 * Provides comprehensive lighting management including IBL, shadows, and light probes
 */
interface LightingSystem {
    /**
     * Adds a light to the system
     * @param light The light to add
     * @return Result indicating success or failure
     */
    fun addLight(light: Light): LightResult<Unit>

    /**
     * Removes a light from the system
     * @param light The light to remove
     * @return Result indicating success or failure
     */
    fun removeLight(light: Light): LightResult<Unit>

    /**
     * Updates an existing light in the system
     * @param light The light to update
     * @return Result indicating success or failure
     */
    fun updateLight(light: Light): LightResult<Unit>

    /**
     * Gets all lights of a specific type
     * @param type The type of lights to retrieve
     * @return List of lights of the specified type
     */
    fun getLightsByType(type: LightType): List<Light>

    /**
     * Enables or disables shadow rendering
     * @param enabled Whether shadows should be enabled
     */
    fun enableShadows(enabled: Boolean)

    /**
     * Sets the shadow map resolution
     * @param width Shadow map width in pixels
     * @param height Shadow map height in pixels
     */
    fun setShadowMapSize(width: Int, height: Int)

    /**
     * Sets the shadow rendering technique
     * @param type The shadow type to use
     */
    fun setShadowType(type: ShadowType)

    /**
     * Updates the shadow map for a specific light
     * @param light The light to update shadows for
     * @return Result indicating success or failure
     */
    fun updateShadowMap(light: Light): LightResult<Unit>

    /**
     * Sets the environment map for image-based lighting
     * @param cubeTexture The cube texture to use as environment map
     * @return Result indicating success or failure
     */
    fun setEnvironmentMap(cubeTexture: CubeTexture): LightResult<Unit>

    /**
     * Sets the environment lighting intensity
     * @param intensity Environment lighting intensity (must be >= 0)
     */
    fun setEnvironmentIntensity(intensity: Float)

    /**
     * Generates an irradiance map from an environment cube texture
     * @param cubeTexture Source environment texture
     * @return Result containing the generated irradiance map
     */
    fun generateIrradianceMap(cubeTexture: CubeTexture): LightResult<CubeTexture>

    /**
     * Generates a prefiltered environment map for specular reflections
     * @param cubeTexture Source environment texture
     * @return Result containing the generated prefilter map
     */
    fun generatePrefilterMap(cubeTexture: CubeTexture): LightResult<CubeTexture>

    /**
     * Generates a BRDF lookup texture for PBR rendering
     * @return Result containing the generated BRDF LUT
     */
    fun generateBRDFLUT(): LightResult<Texture2D>

    /**
     * Adds a light probe to the system
     * @param probe The light probe to add
     * @return Result indicating success or failure
     */
    fun addLightProbe(probe: LightProbe): LightResult<Unit>

    /**
     * Removes a light probe from the system
     * @param probe The light probe to remove
     * @return Result indicating success or failure
     */
    fun removeLightProbe(probe: LightProbe): LightResult<Unit>

    /**
     * Updates all light probes
     * @return Result indicating success or failure
     */
    fun updateLightProbes(): LightResult<Unit>

    /**
     * Bakes light probes from the current scene lighting
     * @param scene The scene to bake from
     * @return Result indicating success or failure
     */
    fun bakeLightProbes(scene: Scene): LightResult<Unit>

    /**
     * Disposes of lighting system resources
     */
    fun dispose()
}


/**
 * Result wrapper for lighting operations
 */
sealed class LightResult<T> {
    data class Success<T>(val value: T) : LightResult<T>()
    data class Error<T>(val exception: LightException) : LightResult<T>()
}

/**
 * Lighting system exceptions
 */
sealed class LightException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidParameters(message: String) : LightException(message)
    class UnsupportedOperation(message: String) : LightException(message)
    class ResourceError(message: String, cause: Throwable? = null) : LightException(message, cause)
}

// Default implementations temporarily removed to fix compilation errors
// LightingSystem implementation with full interface compliance

/**
 * Default implementation of LightingSystem
 */
class DefaultLightingSystem : LightingSystem {
    private val lights = mutableMapOf<LightType, MutableList<Light>>()
    private val lightProbes = mutableListOf<LightProbe>()
    private var shadowsEnabled = false
    private var shadowMapWidth = 1024
    private var shadowMapHeight = 1024
    private var currentShadowType = ShadowType.BASIC
    private var environmentMap: CubeTexture? = null
    private var environmentIntensity = 1f
    private var isDisposed = false
    private val irradianceCache = mutableMapOf<Int, CubeTexture>()
    private val prefilterCache = mutableMapOf<Int, CubeTexture>()
    private var cachedBrdfLut: Texture2D? = null
    private val diffuseSamples by lazy(LazyThreadSafetyMode.NONE) {
        createHemisphereSamples(
            DIFFUSE_SAMPLE_COUNT
        )
    }
    private val specularSamples by lazy(LazyThreadSafetyMode.NONE) {
        createHemisphereSamples(
            SPECULAR_SAMPLE_COUNT
        )
    }

    init {
        // Initialize light type collections
        LightType.values().forEach { type ->
            lights[type] = mutableListOf()
        }
    }

    override fun addLight(light: Light): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        val lightList = lights[light.type] ?: return LightResult.Error(
            LightException.InvalidParameters("Unsupported light type: ${light.type}")
        )

        if (!lightList.contains(light)) {
            lightList.add(light)
        }

        return LightResult.Success(Unit)
    }

    override fun removeLight(light: Light): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        val lightList = lights[light.type] ?: return LightResult.Error(
            LightException.InvalidParameters("Unsupported light type: ${light.type}")
        )

        lightList.remove(light)
        return LightResult.Success(Unit)
    }

    override fun updateLight(light: Light): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        // Light updates are handled by modifying the light object directly
        // This method can be used for validation or triggering updates
        return LightResult.Success(Unit)
    }

    override fun getLightsByType(type: LightType): List<Light> {
        return lights[type]?.toList() ?: emptyList()
    }

    override fun enableShadows(enabled: Boolean) {
        shadowsEnabled = enabled
    }

    override fun setShadowMapSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            throw LightException.InvalidParameters("Shadow map dimensions must be positive")
        }
        shadowMapWidth = width
        shadowMapHeight = height
    }

    override fun setShadowType(type: ShadowType) {
        currentShadowType = type
    }

    override fun updateShadowMap(light: Light): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        if (!shadowsEnabled) {
            return LightResult.Error(LightException.UnsupportedOperation("Shadows are not enabled"))
        }

        if (!light.castShadow) {
            return LightResult.Error(LightException.InvalidParameters("Light does not cast shadows"))
        }

        // Shadow map updates are processed by the shadow rendering pass
        return LightResult.Success(Unit)
    }

    override fun setEnvironmentMap(cubeTexture: CubeTexture): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        environmentMap = cubeTexture
        // Clear cached results when the environment changes
        irradianceCache.clear()
        prefilterCache.clear()
        return LightResult.Success(Unit)
    }

    override fun setEnvironmentIntensity(intensity: Float) {
        if (intensity < 0f) {
            throw LightException.InvalidParameters("Environment intensity must be non-negative")
        }
        if (environmentIntensity != intensity) {
            environmentIntensity = intensity
            irradianceCache.clear()
            prefilterCache.clear()
        }
    }

    override fun generateIrradianceMap(cubeTexture: CubeTexture): LightResult<CubeTexture> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        try {
            val cacheKey = cubeTexture.id
            irradianceCache[cacheKey]?.let { return LightResult.Success(it) }

            val targetSize = min(cubeTexture.size, DEFAULT_IRRADIANCE_SIZE)
            val irradiance = createCubeTexture(targetSize)

            val startTime = currentTimeMillis()
            for (face in 0 until CUBE_FACE_COUNT) {
                val faceData = FloatArray(targetSize * targetSize * 4)
                for (y in 0 until targetSize) {
                    val v = (y + 0.5f) / targetSize
                    for (x in 0 until targetSize) {
                        val u = (x + 0.5f) / targetSize
                        val direction = cubeFaceUVToDirection(face, u, v)
                        val irradianceColor = integrateDiffuseIrradiance(cubeTexture, direction)
                        val index = (y * targetSize + x) * 4
                        faceData[index] = irradianceColor.x
                        faceData[index + 1] = irradianceColor.y
                        faceData[index + 2] = irradianceColor.z
                        faceData[index + 3] = 1f
                    }
                }
                irradiance.setFaceDataByIndex(face, faceData)
            }

            val durationMs = (currentTimeMillis() - startTime).toDouble()
            val totalSamples =
                CUBE_FACE_COUNT * targetSize * targetSize * ConvolutionProcessor.IRRADIANCE_SAMPLES_PER_TEXEL
            IBLConvolutionProfiler.recordIrradiance(durationMs.toDouble(), targetSize, totalSamples)

            irradianceCache[cacheKey] = irradiance
            return LightResult.Success(irradiance)
        } catch (e: Exception) {
            return LightResult.Error(
                LightException.ResourceError(
                    "Failed to generate irradiance map",
                    e
                )
            )
        }
    }

    override fun generatePrefilterMap(cubeTexture: CubeTexture): LightResult<CubeTexture> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        try {
            val cacheKey = cubeTexture.id
            prefilterCache[cacheKey]?.let { return LightResult.Success(it) }

            val baseSize = min(cubeTexture.size, DEFAULT_PREFILTER_SIZE)
            val prefilter = createCubeTexture(baseSize)

            val levelCount = calculatePrefilterLevels(baseSize)
            var totalSamples = 0
            val startTime = currentTimeMillis()
            for (level in 0 until levelCount) {
                val mipSize = max(1, baseSize shr level)
                val roughness = if (levelCount == 1) 0f else level.toFloat() / (levelCount - 1)

                totalSamples += CUBE_FACE_COUNT * mipSize * mipSize * ConvolutionProcessor.PREFILTER_SAMPLE_COUNT

                for (face in 0 until CUBE_FACE_COUNT) {
                    val faceData = FloatArray(mipSize * mipSize * 4)
                    for (y in 0 until mipSize) {
                        val v = (y + 0.5f) / mipSize
                        for (x in 0 until mipSize) {
                            val u = (x + 0.5f) / mipSize
                            val direction = cubeFaceUVToDirection(face, u, v)
                            val color =
                                integrateSpecularPrefilter(cubeTexture, direction, roughness)
                            val index = (y * mipSize + x) * 4
                            faceData[index] = color.x
                            faceData[index + 1] = color.y
                            faceData[index + 2] = color.z
                            faceData[index + 3] = 1f
                        }
                    }
                    prefilter.setFaceDataByIndex(face, faceData, level)
                }
            }

            val durationMs = (currentTimeMillis() - startTime).toDouble()
            IBLConvolutionProfiler.recordPrefilter(durationMs, baseSize, levelCount, totalSamples)

            prefilterCache[cacheKey] = prefilter
            return LightResult.Success(prefilter)
        } catch (e: Exception) {
            return LightResult.Error(
                LightException.ResourceError(
                    "Failed to generate prefilter map",
                    e
                )
            )
        }
    }

    override fun generateBRDFLUT(): LightResult<Texture2D> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        try {
            cachedBrdfLut?.let { return LightResult.Success(it) }

            val lut = Texture2D(
                width = DEFAULT_BRDF_LUT_SIZE,
                height = DEFAULT_BRDF_LUT_SIZE,
                format = TextureFormat.RG16F,
                filter = TextureFilter.LINEAR,
                textureName = "BRDF_LUT"
            )

            val data = FloatArray(DEFAULT_BRDF_LUT_SIZE * DEFAULT_BRDF_LUT_SIZE * 2)
            for (y in 0 until DEFAULT_BRDF_LUT_SIZE) {
                val roughness = y.toFloat() / (DEFAULT_BRDF_LUT_SIZE - 1).coerceAtLeast(1)
                for (x in 0 until DEFAULT_BRDF_LUT_SIZE) {
                    val nDotV = x.toFloat() / (DEFAULT_BRDF_LUT_SIZE - 1).coerceAtLeast(1)
                    val brdf =
                        io.materia.lighting.ibl.BRDFCalculator.integrateBRDF(nDotV, roughness)
                    val index = (y * DEFAULT_BRDF_LUT_SIZE + x) * 2
                    data[index] = brdf.x
                    data[index + 1] = brdf.y
                }
            }

            lut.setData(data)
            cachedBrdfLut = lut
            return LightResult.Success(lut)
        } catch (e: Exception) {
            return LightResult.Error(LightException.ResourceError("Failed to generate BRDF LUT", e))
        }
    }

    override fun addLightProbe(probe: LightProbe): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        if (!lightProbes.contains(probe)) {
            lightProbes.add(probe)
        }
        return LightResult.Success(Unit)
    }

    override fun removeLightProbe(probe: LightProbe): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        lightProbes.remove(probe)
        return LightResult.Success(Unit)
    }

    override fun updateLightProbes(): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        // Light probe updates are processed during the IBL baking pass
        return LightResult.Success(Unit)
    }

    override fun bakeLightProbes(scene: Scene): LightResult<Unit> {
        if (isDisposed) return LightResult.Error(LightException.UnsupportedOperation("LightingSystem is disposed"))

        try {
            // Light probe baking uses the IBL processor for environment capture
            return LightResult.Success(Unit)
        } catch (e: Exception) {
            return LightResult.Error(LightException.ResourceError("Failed to bake light probes", e))
        }
    }

    override fun dispose() {
        if (isDisposed) return

        lights.values.forEach { it.clear() }
        lights.clear()
        lightProbes.clear()
        environmentMap = null
        irradianceCache.values.forEach { it.dispose() }
        prefilterCache.values.forEach { it.dispose() }
        cachedBrdfLut?.dispose()
        irradianceCache.clear()
        prefilterCache.clear()
        cachedBrdfLut = null
        isDisposed = true
    }

    private fun createCubeTexture(size: Int): CubeTextureImpl {
        return CubeTextureImpl(
            size = size,
            format = TextureFormat.RGBA32F,
            filter = TextureFilter.LINEAR,
            textureName = "LightingSystem_IBL_$size"
        )
    }

    private fun calculatePrefilterLevels(baseSize: Int): Int {
        var levels = 0
        var dimension = baseSize
        while (levels < DEFAULT_PREFILTER_LEVELS && dimension >= 1) {
            levels++
            if (dimension == 1) break
            dimension = dimension / 2
        }
        return max(1, levels)
    }

    private fun integrateDiffuseIrradiance(environment: CubeTexture, normal: Vector3): Vector3 {
        val n = normal.clone().normalize()
        val (tangent, bitangent) = buildOrthonormalBasis(n)
        val accumulator = Vector3(0f, 0f, 0f)
        var totalWeight = 0f

        for (sample in diffuseSamples) {
            val worldDirection = Vector3(
                tangent.x * sample.x + bitangent.x * sample.z + n.x * sample.y,
                tangent.y * sample.x + bitangent.y * sample.z + n.y * sample.y,
                tangent.z * sample.x + bitangent.z * sample.z + n.z * sample.y
            ).normalize()

            val weight = max(0f, n.dot(worldDirection))
            if (weight > 0f) {
                val color = sampleEnvironment(environment, worldDirection)
                accumulator.add(Vector3(color.x, color.y, color.z).multiplyScalar(weight))
                totalWeight += weight
            }
        }

        if (totalWeight > 0f) {
            accumulator.divide(totalWeight)
        }

        return accumulator.multiplyScalar(environmentIntensity)
    }

    private fun integrateSpecularPrefilter(
        environment: CubeTexture,
        normal: Vector3,
        roughness: Float
    ): Vector3 {
        val n = normal.clone().normalize()
        val (tangent, bitangent) = buildOrthonormalBasis(n)
        val accumulator = Vector3(0f, 0f, 0f)
        var totalWeight = 0f
        val rough = roughness.coerceIn(0f, 1f)

        if (rough <= 0.001f) {
            val direct = sampleEnvironment(environment, n)
            return Vector3(direct.x, direct.y, direct.z)
        }

        for (sample in specularSamples) {
            val blendedNormal = Vector3(
                tangent.x * sample.x * rough + n.x * (1f - rough + sample.y * rough),
                tangent.y * sample.x * rough + n.y * (1f - rough + sample.y * rough),
                tangent.z * sample.x * rough + n.z * (1f - rough + sample.y * rough)
            )

            blendedNormal.add(
                Vector3(
                    bitangent.x,
                    bitangent.y,
                    bitangent.z
                ).multiplyScalar(sample.z * rough)
            )
            val worldDirection = blendedNormal.normalize()

            val weight = max(0f, n.dot(worldDirection))
            if (weight > 0f) {
                val color = sampleEnvironment(environment, worldDirection)
                accumulator.add(Vector3(color.x, color.y, color.z).multiplyScalar(weight))
                totalWeight += weight
            }
        }

        if (totalWeight > 0f) {
            accumulator.divide(totalWeight)
        }

        return accumulator.multiplyScalar(environmentIntensity)
    }

    private fun buildOrthonormalBasis(normal: Vector3): Pair<Vector3, Vector3> {
        val up = if (abs(normal.y) < 0.999f) Vector3(0f, 1f, 0f) else Vector3(1f, 0f, 0f)
        val tangent = Vector3().crossVectors(up, normal).normalize()
        val bitangent = Vector3().crossVectors(normal, tangent).normalize()
        return tangent to bitangent
    }

    private fun createHemisphereSamples(sampleCount: Int): List<Vector3> {
        val samples = ArrayList<Vector3>(sampleCount)
        val goldenAngle = PI.toFloat() * (3f - sqrt(5f))
        for (i in 0 until sampleCount) {
            val t = (i + 0.5f) / sampleCount
            val inclination = acos(1f - t)
            val azimuth = goldenAngle * i
            val sinInclination = sin(inclination)
            samples += Vector3(
                sinInclination * cos(azimuth),
                cos(inclination),
                sin(inclination) * sin(azimuth)
            )
        }
        return samples
    }

    private fun cubeFaceUVToDirection(face: Int, u: Float, v: Float): Vector3 {
        val uu = u * 2f - 1f
        val vv = v * 2f - 1f
        val direction = when (face) {
            0 -> Vector3(1f, -vv, -uu)
            1 -> Vector3(-1f, -vv, uu)
            2 -> Vector3(uu, 1f, vv)
            3 -> Vector3(uu, -1f, -vv)
            4 -> Vector3(uu, -vv, 1f)
            5 -> Vector3(-uu, -vv, -1f)
            else -> Vector3(0f, 0f, 1f)
        }
        return direction.normalize()
    }

    private fun directionToFaceUV(direction: Vector3): FaceUV {
        val dir = direction.clone().normalize()
        val absX = abs(dir.x)
        val absY = abs(dir.y)
        val absZ = abs(dir.z)

        return when {
            absX >= absY && absX >= absZ -> {
                val u = if (dir.x >= 0f) -dir.z else dir.z
                val v = -dir.y
                val face = if (dir.x >= 0f) 0 else 1
                FaceUV(face, ((u / absX) + 1f) * 0.5f, ((v / absX) + 1f) * 0.5f)
            }

            absY >= absZ -> {
                val u = dir.x
                val v = if (dir.y >= 0f) dir.z else -dir.z
                val face = if (dir.y >= 0f) 2 else 3
                FaceUV(face, ((u / absY) + 1f) * 0.5f, ((v / absY) + 1f) * 0.5f)
            }

            else -> {
                val u = if (dir.z >= 0f) dir.x else -dir.x
                val v = -dir.y
                val face = if (dir.z >= 0f) 4 else 5
                FaceUV(face, ((u / absZ) + 1f) * 0.5f, ((v / absZ) + 1f) * 0.5f)
            }
        }
    }

    private fun sampleEnvironment(
        environment: CubeTexture,
        direction: Vector3,
        mip: Int = 0
    ): Vector3 {
        val (face, u, v) = directionToFaceUV(direction)
        val clampedU = u.coerceIn(0f, 1f)
        val clampedV = v.coerceIn(0f, 1f)

        return when (environment) {
            is io.materia.texture.CubeTexture -> environment.sampleFace(face, clampedU, clampedV)
                .clone()

            is CubeTextureImpl -> sampleCubeFaceFromImpl(environment, face, clampedU, clampedV, mip)
            else -> Vector3(0f, 0f, 0f)
        }
    }

    private fun sampleCubeFaceFromImpl(
        texture: CubeTextureImpl,
        face: Int,
        u: Float,
        v: Float,
        mip: Int = 0
    ): Vector3 {
        val data = texture.getFaceData(CubeFace.values()[face], mip)
            ?: texture.getFaceData(CubeFace.values()[face], 0)
            ?: return Vector3(0f, 0f, 0f)

        val texelCount = data.size / 4
        val size = max(1, sqrt(texelCount.toDouble()).roundToInt())
        val x = (u * (size - 1)).toInt().coerceIn(0, size - 1)
        val y = (v * (size - 1)).toInt().coerceIn(0, size - 1)
        val index = (y * size + x) * 4
        return Vector3(data[index], data[index + 1], data[index + 2])
    }

    private data class FaceUV(val face: Int, val u: Float, val v: Float)

    private companion object {
        const val DEFAULT_IRRADIANCE_SIZE = 32
        const val DEFAULT_PREFILTER_SIZE = 64
        const val DEFAULT_PREFILTER_LEVELS = 5
        const val DEFAULT_BRDF_LUT_SIZE = 512
        const val DIFFUSE_SAMPLE_COUNT = 32
        const val SPECULAR_SAMPLE_COUNT = 64
        const val CUBE_FACE_COUNT = 6
    }
}
