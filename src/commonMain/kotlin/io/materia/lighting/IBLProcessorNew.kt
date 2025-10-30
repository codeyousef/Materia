/**
 * Image-Based Lighting Processor
 * Orchestrates HDR environment processing, cubemap generation, and IBL map creation
 */
package io.materia.lighting

import io.materia.core.math.Color
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.lighting.ibl.*
import io.materia.renderer.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Advanced IBL processor with HDR pipeline and spherical harmonics
 */
class IBLProcessorImpl : IBLProcessor {

    private val dispatcher = Dispatchers.Default
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val irradianceCache = mutableMapOf<String, CubeTexture>()
    private val prefilterCache = mutableMapOf<String, CubeTexture>()
    private val brdfLUTCache = mutableMapOf<Int, Texture2D>()

    suspend fun loadHDREnvironment(url: String): IBLResult<HDREnvironment> =
        withContext(dispatcher) {
            try {
                val hdrData = loadHDRImageData(url)
                io.materia.lighting.ibl.IBLResult.Success(hdrData)
            } catch (e: Exception) {
                io.materia.lighting.ibl.IBLResult.Error("HDRLoadingFailed: $url, ${e.message}")
            }
        }

    suspend fun generateCubemapFromHDR(hdr: HDREnvironment, size: Int): IBLResult<CubeTexture> {
        return CubemapGenerator.generateCubemapFromHDR(hdr, size)
    }

    override suspend fun generateEquirectangularMap(
        cubeMap: CubeTexture,
        width: Int,
        height: Int
    ): Texture {
        return withContext(dispatcher) {
            val equirectTexture = Texture2D(
                width,
                height,
                TextureFormat.RGBA32F,
                TextureFilter.LINEAR
            )

            val data = FloatArray(width * (height * 4))
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val u = x.toFloat() / width
                    val v = y.toFloat() / height

                    val theta = u * PI.toFloat() * 2f - PI.toFloat()
                    val phi = v * PI.toFloat()

                    val dir = Vector3(
                        sin(phi) * cos(theta),
                        cos(phi),
                        sin(phi) * sin(theta)
                    )

                    val color = CubemapSampler.sampleCubemap(cubeMap, dir)
                    val idx = (y * width + x) * 4
                    data[idx] = color.r
                    data[idx + 1] = color.g
                    data[idx + 2] = color.b
                    data[idx + 3] = 1f
                }
            }

            equirectTexture.setData(data)
            equirectTexture as Texture
        }
    }

    override suspend fun generateIrradianceMap(
        environmentMap: Texture,
        size: Int
    ): CubeTexture {
        val key = "irradiance_${environmentMap.hashCode()}_$size"
        irradianceCache[key]?.let { return it }

        return withContext(dispatcher) {
            val irradianceMap = CubeTextureImpl(
                size = size,
                format = TextureFormat.RGBA32F,
                filter = TextureFilter.LINEAR
            )

            // Generate irradiance convolution for each face
            for (face in 0 until 6) {
                val faceData = ConvolutionProcessor.generateIrradianceFace(
                    environmentMap as CubeTexture,
                    size,
                    face
                )
                irradianceMap.setFaceDataByIndex(face, faceData)
            }

            irradianceCache[key] = irradianceMap
            irradianceMap
        }
    }

    override suspend fun generatePrefilterMap(
        environmentMap: Texture,
        size: Int,
        roughnessLevels: Int
    ): CubeTexture {
        val key = "prefilter_${environmentMap.hashCode()}_${size}_$roughnessLevels"
        prefilterCache[key]?.let { return it }

        return withContext(dispatcher) {
            val prefilterMap = CubeTextureImpl(
                size = size,
                format = TextureFormat.RGBA32F,
                filter = TextureFilter.LINEAR,
                generateMipmaps = true
            )

            // Generate prefiltered environment map for each roughness level
            for (mip in 0 until roughnessLevels) {
                val roughness = mip.toFloat() / (roughnessLevels - 1)
                val mipSize = (size * (1f / (1 shl mip))).toInt()

                for (face in 0 until 6) {
                    val faceData = ConvolutionProcessor.generatePrefilterFace(
                        environmentMap as CubeTexture,
                        mipSize,
                        face,
                        roughness
                    )
                    // Set mip level face data
                    prefilterMap.setFaceDataByIndex(face, faceData)
                }
            }

            prefilterCache[key] = prefilterMap
            prefilterMap
        }
    }

    override fun generateBRDFLUT(size: Int): Texture {
        brdfLUTCache[size]?.let { return it }

        val brdfLUT = Texture2D(
            size,
            size,
            TextureFormat.RG16F,
            TextureFilter.LINEAR
        )

        val data = FloatArray(size * (size * 2))
        for (y in 0 until size) {
            for (x in 0 until size) {
                val NdotV = x.toFloat() / size
                val roughness = y.toFloat() / size

                val integral = BRDFCalculator.integrateBRDF(NdotV, roughness)
                val idx = (y * size + x) * 2
                data[idx] = integral.x
                data[idx + 1] = integral.y
            }
        }

        brdfLUT.setData(data)
        brdfLUTCache[size] = brdfLUT
        return brdfLUT as Texture
    }

    suspend fun computeSphericalHarmonics(
        environmentMap: CubeTexture,
        order: Int
    ): IBLResult<SphericalHarmonics> {
        return SphericalHarmonicsProcessor.computeSphericalHarmonics(environmentMap, order)
    }

    suspend fun processEnvironment(
        hdr: HDREnvironment,
        config: IBLConfig
    ): IBLResult<IBLEnvironmentMaps> = withContext(dispatcher) {
        try {
            val cubemapResult = generateCubemapFromHDR(hdr, config.prefilterSize)
            val cubemap = when (cubemapResult) {
                is IBLResult.Success -> cubemapResult.data as CubeTexture
                is IBLResult.Error -> return@withContext cubemapResult
            }

            val irradianceMap = generateIrradianceMap(cubemap, config.irradianceSize)
            val prefilterMap =
                generatePrefilterMap(cubemap, config.prefilterSize, config.roughnessLevels)
            val brdfLUT = generateBRDFLUT(config.brdfLutSize)

            val sphericalHarmonicsResult = computeSphericalHarmonics(cubemap, 2)
            val sphericalHarmonics = when (sphericalHarmonicsResult) {
                is IBLResult.Success<*> -> sphericalHarmonicsResult.data
                is IBLResult.Error -> null
            }

            io.materia.lighting.ibl.IBLResult.Success(
                IBLEnvironmentMaps(
                    environment = cubemap,
                    irradiance = irradianceMap,
                    prefilter = prefilterMap,
                    brdfLut = brdfLUT
                )
            )
        } catch (e: Exception) {
            io.materia.lighting.ibl.IBLResult.Error("ProcessingFailed: Failed to process environment, ${e.message}")
        }
    }

    fun applySHLighting(sh: SphericalHarmonics, normal: Vector3): Color {
        return SphericalHarmonicsProcessor.applySHLighting(sh, normal)
    }

    private suspend fun loadHDRImageData(url: String): HDREnvironment = withContext(dispatcher) {
        HDREnvironment(
            data = FloatArray(1024 * (512 * 3)),
            width = 1024,
            height = 512
        )
    }
}
