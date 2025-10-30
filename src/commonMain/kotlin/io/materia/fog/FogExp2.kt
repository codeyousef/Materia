package io.materia.fog

import io.materia.core.math.Color
import io.materia.geometry.BufferGeometry
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Exponential squared fog with density parameter
 * Implements FR-F004, FR-F005, FR-F006 from contracts/fog-api.kt
 *
 * Formula: factor = exp(-(density * depth)^2)
 * - factor = 1.0 at origin (no fog)
 * - factor decreases exponentially with distance
 * - Higher density = thicker fog
 */
class FogExp2(
    override var color: Color = Color(0xffffff),
    var density: Float = 0.00025f
) : FogBase {
    val isFogExp2: Boolean = true
    override val name: String = "FogExp2"

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
        // Exponential squared formula: factor = exp(-(density * depth)^2)
        val densityDepth = density * depth
        val factor = exp(-(densityDepth * densityDepth))

        // Clamp to [0, 1] for safety
        return max(0f, min(1f, factor))
    }

    /**
     * Generate shader code for fog calculations
     *
     * @return WGSL/GLSL fog shader code
     */
    fun generateShaderCode(): String {
        return """
            // Exponential Squared Fog
            uniform vec3 fogColor;
            uniform float fogDensity;

            float getFogFactor(float depth) {
                float densityDepth = fogDensity * depth;
                float factor = exp(-(densityDepth * densityDepth));
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
    fun clone(): FogExp2 {
        return FogExp2(color.clone(), density)
    }

    /**
     * Serialize fog to JSON
     */
    fun toJSON(): Map<String, Any> {
        return mapOf(
            "type" to "FogExp2",
            "color" to color.getHex(),
            "density" to density
        )
    }
}