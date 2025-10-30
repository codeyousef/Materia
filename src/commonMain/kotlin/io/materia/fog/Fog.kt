package io.materia.fog

import io.materia.core.math.Color
import io.materia.geometry.BufferGeometry
import kotlin.math.max
import kotlin.math.min

/**
 * Linear fog with near/far distance
 * Implements FR-F001, FR-F002, FR-F003 from contracts/fog-api.kt
 *
 * Formula: factor = (far - depth) / (far - near)
 * - factor = 1.0 at near distance (no fog)
 * - factor = 0.0 at far distance (full fog)
 * - factor is clamped to [0, 1]
 */
class Fog(
    override var color: Color = Color(0xffffff),
    var near: Float = 1f,
    var far: Float = 1000f
) : FogBase {
    val isFog: Boolean = true
    override val name: String = "Fog"

    /**
     * Empty geometry for Three.js API compatibility
     * (Fog objects don't actually use geometry, but this property exists for compatibility)
     */
    @Deprecated(
        "Fog objects don't use geometry. This exists for Three.js API compatibility only.",
        ReplaceWith("")
    )
    val geometry: BufferGeometry = BufferGeometry()

    /**
     * Calculate fog factor at given depth
     *
     * @param depth Distance from camera
     * @return Fog factor in [0, 1] where 1.0 = no fog, 0.0 = full fog
     */
    fun getFogFactor(depth: Float): Float {
        // Check for division by zero using epsilon comparison
        if (kotlin.math.abs(far - near) < io.materia.core.math.EPSILON) {
            return 1.0f // Invalid configuration, no fog
        }

        // Linear interpolation: factor = (far - depth) / (far - near)
        val factor = (far - depth) / (far - near)

        // Clamp to [0, 1]
        return max(0f, min(1f, factor))
    }

    /**
     * Generate shader code for fog calculations
     *
     * @return WGSL/GLSL fog shader code
     */
    fun generateShaderCode(): String {
        return """
            // Linear Fog
            uniform vec3 fogColor;
            uniform float fogNear;
            uniform float fogFar;

            float getFogFactor(float depth) {
                float factor = (fogFar - depth) / (fogFar - fogNear);
                return clamp(factor, 0.0, 1.0);
            }

            vec3 applyFog(vec3 color, float depth) {
                float fogFactor = getFogFactor(depth);
                return mix(fogColor, color, fogFactor);
            }
        """.trimIndent()
    }

    /**
     * Clone fog instance
     */
    fun clone(): Fog {
        return Fog(color.clone(), near, far)
    }

    /**
     * Serialize fog to JSON
     */
    fun toJSON(): Map<String, Any> {
        return mapOf(
            "type" to "Fog",
            "color" to color.getHex(),
            "near" to near,
            "far" to far
        )
    }
}