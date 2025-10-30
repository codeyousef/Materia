package io.materia.renderer.backend

import kotlinx.serialization.Serializable

/**
 * Tracks feature parity coverage across backends and platforms.
 * Used for validation suites and release gates to ensure consistent behavior.
 *
 * @property featureId Feature identifier (e.g., "PBR_SHADING", "OMNI_SHADOWS")
 * @property webgpuStatus WebGPU implementation status
 * @property vulkanStatus Vulkan implementation status
 * @property notes Additional context or known issues
 * @property mitigation Reference to documentation or issue for degraded/blocked features
 * @property visualBaselineId Link to reference render output for visual regression
 */
@Serializable
data class FeatureParityMatrix(
    val featureId: String,
    val webgpuStatus: ParityStatus,
    val vulkanStatus: ParityStatus,
    val notes: String = "",
    val mitigation: String? = null,
    val visualBaselineId: String? = null
) {
    init {
        require(featureId.isNotBlank()) { "Feature ID cannot be blank" }

        // Degraded or blocked features must have mitigation documentation
        if (webgpuStatus in listOf(ParityStatus.DEGRADED, ParityStatus.BLOCKED) ||
            vulkanStatus in listOf(ParityStatus.DEGRADED, ParityStatus.BLOCKED)
        ) {
            require(!mitigation.isNullOrBlank()) {
                "Feature $featureId has degraded/blocked status but no mitigation provided"
            }
        }
    }

    /**
     * Check if feature has complete parity across both backends.
     */
    fun hasCompleteParity(): Boolean {
        return webgpuStatus == ParityStatus.COMPLETE && vulkanStatus == ParityStatus.COMPLETE
    }

    /**
     * Check if feature is usable (complete or degraded, but not blocked).
     */
    fun isUsable(): Boolean {
        return webgpuStatus != ParityStatus.BLOCKED && vulkanStatus != ParityStatus.BLOCKED
    }

    /**
     * Get the worst parity status across backends.
     */
    fun worstStatus(): ParityStatus {
        return listOf(webgpuStatus, vulkanStatus).minByOrNull { it.ordinal } ?: ParityStatus.BLOCKED
    }
}

/**
 * Parity implementation status for a feature.
 */
enum class ParityStatus {
    COMPLETE,   // Full parity, tests passing
    DEGRADED,   // Partial implementation or known quality issues
    BLOCKED     // Not implemented or major blockers prevent usage
}

/**
 * Collection of feature parity entries for comprehensive tracking.
 */
@Serializable
data class FeatureParityCollection(
    val entries: List<FeatureParityMatrix>,
    val generatedAt: String,
    val version: String = "1.0"
) {
    /**
     * Get parity status for a specific feature.
     */
    fun getFeature(featureId: String): FeatureParityMatrix? {
        return entries.firstOrNull { it.featureId == featureId }
    }

    /**
     * Get all features with complete parity.
     */
    fun completeFeatures(): List<FeatureParityMatrix> {
        return entries.filter { it.hasCompleteParity() }
    }

    /**
     * Get all features needing attention (degraded or blocked).
     */
    fun attentionNeeded(): List<FeatureParityMatrix> {
        return entries.filter { !it.hasCompleteParity() }
    }
}
