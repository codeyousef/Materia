/**
 * Mathematical sampling utilities for IBL processing
 */
package io.materia.lighting.ibl

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * Sampling and mathematical utilities for IBL
 */
internal object SamplingUtils {

    /**
     * Hammersley sequence for low-discrepancy sampling
     */
    fun hammersley(i: Int, n: Int): Vector2 {
        val x = if (n > 0) i.toFloat() / n else 0f
        val y = radicalInverseVDC(i)
        return Vector2(x, y)
    }

    /**
     * Radical inverse Van der Corput sequence
     */
    fun radicalInverseVDC(bits: Int): Float {
        var b = bits
        b = (b shl 16) or (b ushr 16)
        b = ((b and 0x55555555) shl 1) or ((b and 0xAAAAAAAA.toInt()) ushr 1)
        b = ((b and 0x33333333) shl 2) or ((b and 0xCCCCCCCC.toInt()) ushr 2)
        b = ((b and 0x0F0F0F0F) shl 4) or ((b and 0xF0F0F0F0.toInt()) ushr 4)
        b = ((b and 0x00FF00FF) shl 8) or ((b and 0xFF00FF00.toInt()) ushr 8)
        return b * 2.3283064365386963e-10f
    }

    /**
     * Importance sample GGX distribution
     */
    fun importanceSampleGGX(xi: Vector2, normal: Vector3, roughness: Float): Vector3 {
        val alpha = roughness * roughness

        val phi = 2f * PI.toFloat() * xi.x
        val denominator = 1f + (alpha * alpha - 1f) * xi.y
        val cosTheta = if (abs(denominator) > 0.000001f) {
            sqrt(max(0f, (1f - xi.y) / denominator))
        } else {
            1f
        }
        val sinTheta = sqrt(max(0f, 1f - (cosTheta * cosTheta)))

        val halfVector = Vector3(
            cos(phi) * sinTheta,
            sin(phi) * sinTheta,
            cosTheta
        )

        val up = if (abs(normal.z) < 0.999f) Vector3(0f, 0f, 1f) else Vector3(1f, 0f, 0f)
        val tangentRaw = up.cross(normal)
        val tangentLength = tangentRaw.length()
        val tangent = if (tangentLength > 0.001f) {
            tangentRaw.normalize()
        } else {
            Vector3(1f, 0f, 0f)
        }
        val bitangent = normal.cross(tangent)

        return tangent * halfVector.x + bitangent * halfVector.y + normal * halfVector.z
    }

    /**
     * Convert spherical coordinates to Cartesian in tangent space
     */
    fun sphericalToCartesian(theta: Float, phi: Float, normal: Vector3): Vector3 {
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)

        val tangentRaw = normal.cross(Vector3.UP)
        val tangentLength = tangentRaw.length()
        val tangent = if (tangentLength > 0.001f) {
            tangentRaw.normalize()
        } else {
            Vector3(1f, 0f, 0f)
        }
        val bitangent = normal.cross(tangent)

        return tangent * sinTheta * cosPhi + bitangent * sinTheta * sinPhi + (normal * cosTheta)
    }

    /**
     * Reflect vector
     */
    fun reflect(incident: Vector3, normal: Vector3): Vector3 {
        return incident - normal * 2f * incident.dot(normal)
    }

    /**
     * Evaluate spherical harmonics basis functions
     */
    fun evaluateSphericalHarmonics(direction: Vector3): FloatArray {
        val x = direction.x
        val y = direction.y
        val z = direction.z

        return floatArrayOf(
            0.282095f,
            0.488603f * y,
            0.488603f * z,
            0.488603f * x,
            1.092548f * (x * y),
            1.092548f * (y * z),
            0.315392f * (3f * z * z - 1f),
            1.092548f * (x * z),
            0.546274f * (x * x - (y * y))
        )
    }

    fun log2(x: Float): Float = ln(x) / ln(2f)
}
