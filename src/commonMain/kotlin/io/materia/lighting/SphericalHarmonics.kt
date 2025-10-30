/**
 * Spherical Harmonics for efficient irradiance representation
 */
package io.materia.lighting

import io.materia.core.math.*
import io.materia.renderer.CubeTexture

/**
 * Spherical harmonics coefficients for lighting
 */
class SphericalHarmonicsImpl(
    val order: Int = 2
) : SphericalHarmonics {
    override val coefficients: Array<Vector3> = Array((order + 1) * (order + 1)) { Vector3() }

    /**
     * Evaluate spherical harmonics for a given direction
     */
    override fun evaluate(direction: Vector3): Vector3 {
        var result = Vector3()

        // L0
        result = result + coefficients[0] * 0.282095f

        if (order >= 1) {
            // L1
            result = result + coefficients[1] * 0.488603f * direction.y
            result = result + coefficients[2] * 0.488603f * direction.z
            result = result + coefficients[3] * 0.488603f * direction.x
        }

        if (order >= 2) {
            // L2
            val x = direction.x
            val y = direction.y
            val z = direction.z

            result = result + coefficients[4] * 1.092548f * x * y
            result = result + coefficients[5] * 1.092548f * y * z
            result = result + coefficients[6] * 0.315392f * (3f * z * z - 1f)
            result = result + coefficients[7] * 1.092548f * x * z
            result = result + coefficients[8] * 0.546274f * (x * x - (y * y))
        }

        return result
    }

    /**
     * Add a light sample to the spherical harmonics
     */
    fun addLightSample(direction: Vector3, radiance: Color, weight: Float = 1f) {
        val r = Vector3(radiance.r, radiance.g, radiance.b) * weight

        // L0
        coefficients[0] = coefficients[0] + (r * 0.282095f)

        if (order >= 1) {
            // L1
            coefficients[1] = coefficients[1] + (r * (0.488603f * direction.y))
            coefficients[2] = coefficients[2] + (r * (0.488603f * direction.z))
            coefficients[3] = coefficients[3] + (r * (0.488603f * direction.x))
        }

        if (order >= 2) {
            // L2
            val x = direction.x
            val y = direction.y
            val z = direction.z

            coefficients[4] = coefficients[4] + (r * (1.092548f * (x * y)))
            coefficients[5] = coefficients[5] + (r * (1.092548f * (y * z)))
            coefficients[6] = coefficients[6] + (r * (0.315392f * (3f * z * z - 1f)))
            coefficients[7] = coefficients[7] + (r * (1.092548f * (x * z)))
            coefficients[8] = coefficients[8] + (r * (0.546274f * (x * x - (y * y))))
        }
    }

    /**
     * Scale all coefficients
     */
    fun scale(factor: Float) {
        for (coeff in coefficients) {
            coeff.x = coeff.x * factor
            coeff.y = coeff.y * factor
            coeff.z = coeff.z * factor
        }
    }

    /**
     * Clear all coefficients
     */
    fun clear() {
        for (coeff in coefficients) {
            coeff.set(0f, 0f, 0f)
        }
    }

    companion object {
        /**
         * Create from irradiance cubemap
         */
        fun fromCubemap(cubemap: CubeTexture, samples: Int = 1024): SphericalHarmonics {
            val sh = SphericalHarmonicsImpl()

            // Sample the cubemap and accumulate SH coefficients
            // Implementation would sample cubemap faces

            return sh
        }
    }
}
