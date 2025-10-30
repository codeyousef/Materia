/**
 * Environment map convolution for irradiance and prefilter generation
 */
package io.materia.lighting.ibl

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.renderer.CubeTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Processes environment maps for IBL lighting
 */
internal object ConvolutionProcessor {

    internal const val PREFILTER_SAMPLE_COUNT = 1024
    internal const val IRRADIANCE_SAMPLE_STEPS = 64
    internal const val IRRADIANCE_SAMPLES_PER_TEXEL =
        IRRADIANCE_SAMPLE_STEPS * IRRADIANCE_SAMPLE_STEPS

    /**
     * Generate irradiance face data
     */
    suspend fun generateIrradianceFace(environment: CubeTexture, size: Int, face: Int): FloatArray =
        withContext(Dispatchers.Default) {
            val data = FloatArray(size * (size * 4))

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val u = (x + 0.5f) / size * 2f - 1f
                    val v = (y + 0.5f) / size * 2f - 1f

                    val direction = CubemapSampler.cubeFaceUVToDirection(face, u, v)
                    val irradiance = computeIrradiance(environment, direction)

                    val index = (y * size + x) * 4
                    data[index] = irradiance.r
                    data[index + 1] = irradiance.g
                    data[index + 2] = irradiance.b
                    data[index + 3] = 1.0f
                }
            }

            data
        }

    /**
     * Generate prefilter face data
     */
    suspend fun generatePrefilterFace(
        environment: CubeTexture,
        size: Int,
        face: Int,
        roughness: Float
    ): FloatArray = withContext(Dispatchers.Default) {
        val data = FloatArray(size * (size * 4))

        for (y in 0 until size) {
            for (x in 0 until size) {
                val u = if (size > 0) (x + 0.5f) / size * 2f - 1f else 0f
                val v = if (size > 0) (y + 0.5f) / size * 2f - 1f else 0f

                val direction = CubemapSampler.cubeFaceUVToDirection(face, u, v)
                val prefiltered = computePrefilter(environment, direction, roughness)

                val index = (y * size + x) * 4
                data[index] = prefiltered.r
                data[index + 1] = prefiltered.g
                data[index + 2] = prefiltered.b
                data[index + 3] = 1.0f
            }
        }

        data
    }

    /**
     * Compute irradiance by integrating over hemisphere
     */
    private fun computeIrradiance(environment: CubeTexture, normal: Vector3): Color {
        var irradiance = Vector3.ZERO
        var sampleCount = 0

        val samples = IRRADIANCE_SAMPLE_STEPS
        val deltaTheta = PI.toFloat() / samples
        val deltaPhi = 2f * PI.toFloat() / samples

        for (i in 0 until samples) {
            for (j in 0 until samples) {
                val theta = i * deltaTheta
                val phi = j * deltaPhi

                val sampleDirection = SamplingUtils.sphericalToCartesian(theta, phi, normal)

                val nDotL = max(0f, normal.dot(sampleDirection))
                if (nDotL > 0f) {
                    val color = CubemapSampler.sampleCubemap(environment, sampleDirection)
                    val weight = sin(theta) * deltaTheta * deltaPhi

                    irradiance = irradiance + color.toVector3() * nDotL * weight
                    sampleCount++
                }
            }
        }

        val normalizedIrradiance = if (sampleCount > 0) {
            irradiance * (PI.toFloat() / sampleCount)
        } else {
            Vector3.ZERO
        }
        return Color.fromVector3(normalizedIrradiance)
    }

    /**
     * Compute prefiltered environment map for given roughness
     */
    private fun computePrefilter(
        environment: CubeTexture,
        direction: Vector3,
        roughness: Float
    ): Color {
        val normal = direction
        val view = direction

        var prefilteredColor = Vector3.ZERO
        var totalWeight = 0f

        val sampleCount = PREFILTER_SAMPLE_COUNT
        for (i in 0 until sampleCount) {
            val xi = SamplingUtils.hammersley(i, sampleCount)
            val halfVector = SamplingUtils.importanceSampleGGX(xi, normal, roughness)
            val lightDirection = SamplingUtils.reflect(-view, halfVector)

            val nDotL = max(0f, normal.dot(lightDirection))
            if (nDotL > 0f) {
                val nDotH = max(0f, normal.dot(halfVector))
                val vDotH = max(0f, view.dot(halfVector))

                val distribution = BRDFCalculator.distributionGGX(nDotH, roughness)
                val pdf = if (abs(vDotH) > 0.0001f) {
                    distribution * nDotH / (4f * vDotH) + 0.0001f
                } else {
                    0.0001f
                }

                val resolution = environment.size.toFloat()
                val resolutionSquared = resolution * resolution
                val saTexel = if (resolutionSquared > 0f) {
                    4f * PI.toFloat() / (6f * resolutionSquared)
                } else {
                    1f
                }
                val saSample = if (pdf > 0.0001f) {
                    1f / (sampleCount * pdf)
                } else {
                    1f
                }

                val mipLevel = if (roughness == 0f || saTexel == 0f) {
                    0f
                } else {
                    0.5f * SamplingUtils.log2(saSample / saTexel)
                }

                val color = CubemapSampler.sampleCubemapLOD(environment, lightDirection, mipLevel)
                prefilteredColor = prefilteredColor + color.toVector3() * nDotL
                totalWeight = totalWeight + nDotL
            }
        }

        val normalizedColor = if (totalWeight > 0.0001f) {
            prefilteredColor / totalWeight
        } else {
            Vector3.ZERO
        }
        return Color.fromVector3(normalizedColor)
    }
}

// Extension functions
private fun Color.toVector3(): Vector3 = Vector3(r, g, b)
private fun Color.Companion.fromVector3(v: Vector3): Color = Color(v.x, v.y, v.z)
