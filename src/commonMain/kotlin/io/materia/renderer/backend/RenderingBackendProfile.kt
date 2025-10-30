package io.materia.renderer.backend

import kotlinx.serialization.Serializable

/**
 * Describes a rendering backend implementation (WebGPU or Vulkan) for a specific platform.
 *
 * @property backendId Identifies the backend type
 * @property supportedFeatures Set of features supported by this backend
 * @property performanceBudget Performance requirements and targets
 * @property fallbackPriority Lower number = higher priority for selection
 * @property apiVersion API version string (e.g., "WebGPU 1.0", "Vulkan 1.3")
 * @property platformTargets Platforms where this backend is available
 */
@Serializable
data class RenderingBackendProfile(
    val backendId: BackendId,
    val supportedFeatures: Set<BackendFeature>,
    val performanceBudget: PerformanceBudget,
    val fallbackPriority: Int,
    val apiVersion: String,
    val platformTargets: List<PlatformTarget>
) {
    init {
        require(supportedFeatures.containsAll(REQUIRED_PARITY_FEATURES)) {
            "Backend $backendId missing required parity features. " +
                    "Required: $REQUIRED_PARITY_FEATURES, Got: $supportedFeatures"
        }
        require(performanceBudget.targetFps >= 60) {
            "Target FPS must be >= 60, got ${performanceBudget.targetFps}"
        }
        require(performanceBudget.minFps >= 30) {
            "Minimum FPS must be >= 30, got ${performanceBudget.minFps}"
        }
        require(fallbackPriority >= 0) {
            "Fallback priority must be non-negative, got $fallbackPriority"
        }
    }

    companion object {
        val REQUIRED_PARITY_FEATURES = setOf(
            BackendFeature.COMPUTE,
            BackendFeature.RAY_TRACING,
            BackendFeature.XR_SURFACE
        )
    }
}

/**
 * Performance budget requirements for a backend.
 */
@Serializable
data class PerformanceBudget(
    val targetFps: Int = 60,
    val minFps: Int = 30,
    val initBudgetMs: Long = 3000
)

/**
 * Platform target identifiers.
 */
@Serializable
enum class PlatformTarget {
    WEB,
    DESKTOP,
    ANDROID,
    IOS
}
