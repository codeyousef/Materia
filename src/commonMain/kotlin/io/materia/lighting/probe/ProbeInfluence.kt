/**
 * Light probe influence calculation and falloff
 * Determines how much a probe affects a given position
 */
package io.materia.lighting.probe

import io.materia.core.math.Box3
import io.materia.core.math.Vector3
import io.materia.lighting.ProbeFalloff
import kotlin.math.exp

/**
 * Calculates influence for a light probe
 */
class ProbeInfluenceCalculator(
    private val falloffType: ProbeFalloff = ProbeFalloff.SMOOTH,
    private val falloffStrength: Float = 1.0f
) {
    /**
     * Calculate influence at a position
     */
    fun calculateInfluence(
        probePosition: Vector3,
        probeDistance: Float,
        position: Vector3,
        influenceBounds: Box3? = null,
        hasValidData: Boolean = true
    ): Float {
        if (!hasValidData) return 0f

        val distanceToProbe = probePosition.distanceTo(position)
        if (distanceToProbe > probeDistance) return 0f

        // Check influence bounds if defined
        influenceBounds?.let { bounds ->
            if (!bounds.containsPoint(position)) return 0f
        }

        // Calculate influence based on falloff type
        val normalizedDistance = distanceToProbe / probeDistance

        return when (falloffType) {
            ProbeFalloff.LINEAR -> (1f - normalizedDistance).coerceIn(0f, 1f)
            ProbeFalloff.SMOOTH -> {
                val t = 1f - normalizedDistance
                (t * t * (3f - (2f * t))).coerceIn(0f, 1f)
            }

            ProbeFalloff.EXPONENTIAL -> {
                exp(-(normalizedDistance * falloffStrength)).coerceIn(0f, 1f)
            }

            ProbeFalloff.CUSTOM -> {
                // Custom falloff can be implemented by extending this class
                1f - normalizedDistance
            }
        }
    }
}
