/**
 * BRDF calculation utilities for physically-based rendering
 */
package io.materia.lighting.ibl

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * BRDF (Bidirectional Reflectance Distribution Function) calculations
 */
internal object BRDFCalculator {

    /**
     * GGX distribution function
     */
    fun distributionGGX(nDotH: Float, roughness: Float): Float {
        val alpha = roughness * roughness
        val alpha2 = alpha * alpha
        val denom = nDotH * nDotH * (alpha2 - 1f) + 1f
        val denomSq = denom * denom
        return if (abs(denomSq) > 0.000001f) {
            alpha2 / (PI.toFloat() * denomSq)
        } else {
            0f
        }
    }

    /**
     * Smith geometry function
     */
    fun geometrySmith(normal: Vector3, view: Vector3, light: Vector3, roughness: Float): Float {
        val nDotV = max(0f, normal.dot(view))
        val nDotL = max(0f, normal.dot(light))
        val ggx2 = geometrySchlickGGX(nDotV, roughness)
        val ggx1 = geometrySchlickGGX(nDotL, roughness)
        return (ggx1 * ggx2)
    }

    private fun geometrySchlickGGX(nDotV: Float, roughness: Float): Float {
        val r = roughness + 1f
        val k = r * r / 8f
        val num = nDotV
        val denom = nDotV * (1f - k) + k
        return if (abs(denom) > 0.000001f) {
            num / denom
        } else {
            0f
        }
    }

    /**
     * Integrate BRDF for environment map
     */
    fun integrateBRDF(nDotV: Float, roughness: Float): Vector2 {
        val view = Vector3(sqrt(1f - (nDotV * nDotV)), 0f, nDotV)
        val normal = Vector3(0f, 0f, 1f)

        var scale = 0f
        var bias = 0f

        val sampleCount = 1024
        for (i in 0 until sampleCount) {
            val xi = SamplingUtils.hammersley(i, sampleCount)
            val halfVector = SamplingUtils.importanceSampleGGX(xi, normal, roughness)
            val lightDirection = SamplingUtils.reflect(-view, halfVector)

            val nDotL = max(0f, lightDirection.z)
            val nDotH = max(0f, halfVector.z)
            val vDotH = max(0f, view.dot(halfVector))

            if (nDotL > 0f) {
                val g = geometrySmith(normal, view, lightDirection, roughness)
                val denominator = nDotH * nDotV
                val gVis = if (abs(denominator) > 0.000001f) {
                    g * vDotH / denominator
                } else {
                    0f
                }
                val fc = (1f - vDotH).pow(5f)

                scale += (1f - fc) * gVis
                bias += (fc * gVis)
            }
        }

        return if (sampleCount > 0) {
            Vector2(scale / sampleCount, bias / sampleCount)
        } else {
            Vector2(0f, 0f)
        }
    }
}
