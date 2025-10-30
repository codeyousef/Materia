/**
 * Spherical harmonics generation and evaluation for light probes
 * Provides efficient irradiance approximation
 */
package io.materia.lighting.probe

import io.materia.core.math.Vector3
import io.materia.renderer.CubeTexture
import kotlin.math.*

/**
 * Spherical harmonics implementation for efficient lighting approximation
 */
class SphericalHarmonicsImpl(
    override val coefficients: Array<Vector3> = Array(9) { Vector3() }
) : io.materia.lighting.SphericalHarmonics {
    /**
     * Evaluate SH for a given direction (typically surface normal)
     */
    override fun evaluate(direction: Vector3): Vector3 {
        val dirLength = direction.length()
        val n = if (dirLength > 0.001f) {
            direction.clone().normalize()
        } else {
            // Default to up direction if direction is degenerate
            Vector3(0f, 1f, 0f)
        }
        val result = Vector3()

        val sh = calculateSHBasis(n)

        // Reconstruct irradiance from SH coefficients
        for (i in coefficients.indices) {
            result.add(coefficients[i].clone().multiplyScalar(sh[i]))
        }

        return result
    }

    /**
     * Add another SH (used for blending/accumulation)
     */
    fun add(other: io.materia.lighting.SphericalHarmonics) {
        for (i in coefficients.indices) {
            coefficients[i].add(other.coefficients[i])
        }
    }

    /**
     * Scale all coefficients
     */
    fun scale(factor: Float) {
        for (i in coefficients.indices) {
            coefficients[i].multiplyScalar(factor)
        }
    }

    /**
     * Calculate spherical harmonic basis functions for a direction
     */
    private fun calculateSHBasis(direction: Vector3): FloatArray {
        val x = direction.x
        val y = direction.y
        val z = direction.z

        // Band 0 (L=0, m=0)
        val y00 = 0.282095f

        // Band 1 (L=1)
        val y1n1 = 0.488603f * y
        val y10 = 0.488603f * z
        val y1p1 = 0.488603f * x

        // Band 2 (L=2)
        val y2n2 = 1.092548f * (x * y)
        val y2n1 = 1.092548f * (y * z)
        val y20 = 0.315392f * (3f * z * z - 1f)
        val y2p1 = 1.092548f * (x * z)
        val y2p2 = 0.546274f * (x * x - (y * y))

        return floatArrayOf(y00, y1n1, y10, y1p1, y2n2, y2n1, y20, y2p1, y2p2)
    }
}

/**
 * Generates spherical harmonics from cubemap data
 */
class SphericalHarmonicsGenerator {
    /**
     * Generate SH coefficients from cubemap
     */
    fun generateFromCubemap(cubemap: CubeTexture): io.materia.lighting.SphericalHarmonics {
        val sh = SphericalHarmonicsImpl()

        // Sample cubemap and project into SH
        // This is a simplified version - production would sample more points
        val directions = generateSampleDirections(64)

        for (direction in directions) {
            val color = sampleCubemap(cubemap, direction)
            val basis = calculateSHBasis(direction)

            // Accumulate weighted contribution
            for (i in 0 until 9) {
                sh.coefficients[i].add(
                    color.clone().multiplyScalar(basis[i])
                )
            }
        }

        // Normalize with safe division
        val normalization = if (directions.isNotEmpty()) {
            4f * PI.toFloat() / directions.size
        } else {
            1f
        }
        sh.scale(normalization)

        return sh
    }

    private fun generateSampleDirections(count: Int): List<Vector3> {
        val directions = mutableListOf<Vector3>()

        // Use Fibonacci sphere for uniform distribution
        val phi = PI * (3.0 - sqrt(5.0))

        for (i in 0 until count) {
            val y = if (count > 1) {
                1 - (i / (count - 1.0)) * 2
            } else {
                0.0
            }
            val radius = sqrt(max(0.0, 1 - y * y))

            val theta = phi * i

            val x = cos(theta) * radius
            val z = sin(theta) * radius

            directions.add(Vector3(x.toFloat(), y.toFloat(), z.toFloat()))
        }

        return directions
    }

    private fun sampleCubemap(cubemap: CubeTexture, direction: Vector3): Vector3 {
        // Simplified sampling - production would properly sample cubemap
        return Vector3(
            abs(direction.x),
            abs(direction.y),
            abs(direction.z)
        )
    }

    private fun calculateSHBasis(direction: Vector3): FloatArray {
        val x = direction.x
        val y = direction.y
        val z = direction.z

        return floatArrayOf(
            0.282095f,                             // Y₀⁰
            0.488603f * y,                         // Y₁⁻¹
            0.488603f * z,                         // Y₁⁰
            0.488603f * x,                         // Y₁¹
            1.092548f * (x * y),                   // Y₂⁻²
            1.092548f * (y * z),                   // Y₂⁻¹
            0.315392f * (3f * z * z - 1f),       // Y₂⁰
            1.092548f * (x * z),                   // Y₂¹
            0.546274f * (x * x - (y * y))          // Y₂²
        )
    }
}
