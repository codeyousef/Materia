/**
 * T013: RendererConfig Data Class
 * Feature: 019-we-should-not
 *
 * Configuration for renderer initialization.
 */

package io.materia.renderer

/**
 * Configuration for renderer initialization.
 *
 * @property preferredBackend Preferred graphics backend (null = auto-detect)
 * @property enableValidation Enable debug/validation layers
 * @property vsync Enable V-sync for frame pacing
 * @property msaaSamples MSAA sample count (must be power of 2: 1, 2, 4, 8, 16)
 * @property powerPreference GPU power preference (high-performance vs low-power)
 */
data class RendererConfig(
    val preferredBackend: BackendType? = null,
    val enableValidation: Boolean = true,
    val vsync: Boolean = true,
    val msaaSamples: Int = 4,
    val powerPreference: PowerPreference = PowerPreference.HIGH_PERFORMANCE
) {
    init {
        // Validate msaaSamples is power of 2
        require(msaaSamples in setOf(1, 2, 4, 8, 16)) {
            "msaaSamples must be power of 2 (1, 2, 4, 8, 16), got: $msaaSamples"
        }

        // Warn if preferredBackend is WEBGL (violates FR-001/FR-002)
        if (preferredBackend == BackendType.WEBGL) {
            println("⚠️ Warning: Explicitly requesting WebGL backend (should only be fallback per FR-001/FR-002)")
        }
    }
}

/**
 * GPU power preference for renderer initialization.
 */
enum class PowerPreference {
    /**
     * Prefer integrated GPU (lower power consumption).
     */
    LOW_POWER,

    /**
     * Prefer discrete GPU (higher performance).
     * Recommended for 60 FPS target (FR-019).
     */
    HIGH_PERFORMANCE
}
