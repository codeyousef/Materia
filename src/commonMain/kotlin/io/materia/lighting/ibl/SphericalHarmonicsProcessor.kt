/**
 * Spherical harmonics computation for fast irradiance approximation
 */
package io.materia.lighting.ibl

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.renderer.CubeTexture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Computes spherical harmonics coefficients from environment maps
 */
internal object SphericalHarmonicsProcessor {

    private val cache = mutableMapOf<String, SphericalHarmonics>()

    suspend fun computeSphericalHarmonics(
        environmentMap: CubeTexture,
        order: Int
    ): IBLResult<SphericalHarmonics> = withContext(Dispatchers.Default) {
        try {
            val cacheKey = "sh_${environmentMap.hashCode()}_$order"
            cache[cacheKey]?.let { return@withContext IBLResult.Success(it) }

            val coefficients = Array(9) { Vector3.ZERO }

            val sampleCount = 64 * 64
            val deltaPhi = 2f * PI.toFloat() / 64f
            val deltaTheta = PI.toFloat() / 64f

            for (face in 0 until 6) {
                for (i in 0 until 64) {
                    for (j in 0 until 64) {
                        val theta = (i + 0.5f) * deltaTheta
                        val phi = (j + 0.5f) * deltaPhi

                        val direction = CubemapSampler.cubeFaceToDirection(face, theta, phi)
                        val color = CubemapSampler.sampleCubemap(environmentMap, direction)

                        val weight = sin(theta) * deltaTheta * deltaPhi

                        val sh = SamplingUtils.evaluateSphericalHarmonics(direction)
                        for (k in 0 until 9) {
                            coefficients[k] = coefficients[k].add(
                                (color.toVector3() * sh[k]) * weight
                            )
                        }
                    }
                }
            }

            val normalizer = 4f * PI.toFloat() / sampleCount
            for (k in 0 until 9) {
                coefficients[k] = coefficients[k] * normalizer
            }

            val sh = IBLSphericalHarmonics(coefficients)
            cache[cacheKey] = sh
            io.materia.lighting.ibl.IBLResult.Success(sh)
        } catch (e: Exception) {
            io.materia.lighting.ibl.IBLResult.Error("CubemapGenerationFailed: Failed to generate spherical harmonics, ${e.message}")
        }
    }

    fun applySHLighting(sh: SphericalHarmonics, normal: Vector3): Color {
        val shBasis = SamplingUtils.evaluateSphericalHarmonics(normal)
        var result = Vector3.ZERO

        for (i in 0 until 9) {
            result = result.add(sh.coefficients[i].clone().multiplyScalar(shBasis[i]))
        }

        return Color(
            result.x.coerceAtLeast(0f),
            result.y.coerceAtLeast(0f),
            result.z.coerceAtLeast(0f)
        )
    }

    fun clearCache() {
        cache.clear()
    }
}

private fun Color.toVector3(): Vector3 = Vector3(r, g, b)
