package io.materia.renderer

import io.materia.datetime.currentTimeString
import io.materia.renderer.backend.*
import kotlinx.serialization.Serializable

/**
 * Feature parity evaluator integrated into renderer lifecycle.
 * Evaluates backend capabilities against parity matrix and feeds diagnostics to tests.
 */
class FeatureParityEvaluator {

    private val parityCollection = loadParityMatrix()

    /**
     * Evaluate backend selection against feature parity requirements.
     * Returns updated selection with parity matrix attached.
     */
    fun evaluateSelection(
        selection: BackendSelection,
        report: DeviceCapabilityReport
    ): BackendSelection {
        if (!selection.isSuccessful) {
            return selection
        }

        val backendId = selection.backendId!!

        // Evaluate each feature for parity
        val parityResults = evaluateFeatureParity(backendId, report)

        // Check if any critical features are blocked
        val hasBlockedFeatures = parityResults.any { it.worstStatus() == ParityStatus.BLOCKED }

        return if (hasBlockedFeatures) {
            // Downgrade selection to failure if critical features are blocked
            BackendSelection(
                backendId = null,
                reason = SelectionReason.FAILED,
                parityMatrix = null,
                errorMessage = "Backend $backendId has blocked features: ${
                    parityResults.filter { it.worstStatus() == ParityStatus.BLOCKED }
                        .map { it.featureId }
                }"
            )
        } else {
            // Selection is valid, attach parity information
            selection.copy(
                parityMatrix = parityResults.firstOrNull() // Attach first parity entry as representative
            )
        }
    }

    /**
     * Evaluate feature parity for a backend based on device capabilities.
     */
    private fun evaluateFeatureParity(
        backendId: BackendId,
        report: DeviceCapabilityReport
    ): List<FeatureParityMatrix> {
        val results = mutableListOf<FeatureParityMatrix>()

        // Define key features to evaluate
        val featuresToEvaluate = listOf(
            "PBR_SHADING",
            "OMNI_SHADOWS",
            "COMPUTE_SKINNING",
            "RAY_TRACING",
            "XR_RENDERING"
        )

        featuresToEvaluate.forEach { featureId ->
            val status = evaluateFeatureStatus(featureId, backendId, report)

            results.add(
                FeatureParityMatrix(
                    featureId = featureId,
                    webgpuStatus = if (backendId == BackendId.WEBGPU) status else ParityStatus.COMPLETE,
                    vulkanStatus = if (backendId == BackendId.VULKAN) status else ParityStatus.COMPLETE,
                    notes = generateParityNotes(featureId, status, report),
                    mitigation = if (status != ParityStatus.COMPLETE) generateMitigation(featureId) else null,
                    visualBaselineId = "baseline-$featureId-v1"
                )
            )
        }

        return results
    }

    /**
     * Evaluate status of a specific feature.
     */
    private fun evaluateFeatureStatus(
        featureId: String,
        backendId: BackendId,
        report: DeviceCapabilityReport
    ): ParityStatus {
        return when (featureId) {
            "PBR_SHADING" -> ParityStatus.COMPLETE // Supported on both backends
            "OMNI_SHADOWS" -> ParityStatus.COMPLETE // Supported on both backends
            "COMPUTE_SKINNING" -> {
                if (report.featureFlags[BackendFeature.COMPUTE] == FeatureStatus.SUPPORTED) {
                    ParityStatus.COMPLETE
                } else {
                    ParityStatus.DEGRADED
                }
            }

            "RAY_TRACING" -> {
                when (report.featureFlags[BackendFeature.RAY_TRACING]) {
                    FeatureStatus.SUPPORTED -> ParityStatus.COMPLETE
                    FeatureStatus.EMULATED -> ParityStatus.DEGRADED
                    FeatureStatus.MISSING -> ParityStatus.BLOCKED
                    null -> ParityStatus.BLOCKED
                }
            }

            "XR_RENDERING" -> {
                if (report.featureFlags[BackendFeature.XR_SURFACE] == FeatureStatus.SUPPORTED) {
                    ParityStatus.COMPLETE
                } else {
                    ParityStatus.BLOCKED
                }
            }

            else -> ParityStatus.COMPLETE
        }
    }

    /**
     * Generate notes for parity entry.
     */
    private fun generateParityNotes(
        featureId: String,
        status: ParityStatus,
        report: DeviceCapabilityReport
    ): String {
        return when (status) {
            ParityStatus.COMPLETE -> "Full parity on ${report.deviceId}"
            ParityStatus.DEGRADED -> "Degraded performance or emulated on ${report.deviceId}"
            ParityStatus.BLOCKED -> "Not supported on ${report.deviceId}"
        }
    }

    /**
     * Generate mitigation documentation reference.
     */
    private fun generateMitigation(featureId: String): String {
        return "docs/parity-mitigations.md#${featureId.lowercase().replace("_", "-")}"
    }

    /**
     * Load parity matrix from configuration.
     */
    private fun loadParityMatrix(): FeatureParityCollection {
        // In production, this would load from configuration file
        // For now, return empty collection
        return FeatureParityCollection(
            entries = emptyList(),
            generatedAt = currentTimeString(),
            version = "1.0"
        )
    }

    /**
     * Generate parity report for diagnostics.
     */
    fun generateParityReport(
        backendId: BackendId,
        report: DeviceCapabilityReport
    ): ParityReport {
        val parityResults = evaluateFeatureParity(backendId, report)

        return ParityReport(
            backendId = backendId,
            deviceId = report.deviceId,
            totalFeatures = parityResults.size,
            completeFeatures = parityResults.count { it.hasCompleteParity() },
            degradedFeatures = parityResults.count { it.worstStatus() == ParityStatus.DEGRADED },
            blockedFeatures = parityResults.count { it.worstStatus() == ParityStatus.BLOCKED },
            parityScore = calculateParityScore(parityResults),
            details = parityResults
        )
    }

    /**
     * Calculate overall parity score (0.0 to 1.0).
     */
    private fun calculateParityScore(results: List<FeatureParityMatrix>): Double {
        if (results.isEmpty()) return 1.0

        val weights = mapOf(
            ParityStatus.COMPLETE to 1.0,
            ParityStatus.DEGRADED to 0.5,
            ParityStatus.BLOCKED to 0.0
        )

        val totalScore = results.sumOf { weights[it.worstStatus()] ?: 0.0 }
        return totalScore / results.size
    }
}

/**
 * Parity evaluation report.
 */
@Serializable
data class ParityReport(
    val backendId: BackendId,
    val deviceId: String,
    val totalFeatures: Int,
    val completeFeatures: Int,
    val degradedFeatures: Int,
    val blockedFeatures: Int,
    val parityScore: Double,
    val details: List<FeatureParityMatrix>
) {
    /**
     * Check if parity meets minimum requirements (>= 0.8 score).
     */
    fun meetsMinimumRequirements(): Boolean {
        return parityScore >= 0.8 && blockedFeatures == 0
    }

    /**
     * Generate human-readable summary.
     */
    fun summary(): String = buildString {
        appendLine("Feature Parity Report for $backendId on $deviceId")
        val scorePercent = ((parityScore * 1000).toInt() / 10.0)
        appendLine("Overall Score: $scorePercent%")
        appendLine("Complete: $completeFeatures / $totalFeatures")
        if (degradedFeatures > 0) appendLine("Degraded: $degradedFeatures")
        if (blockedFeatures > 0) appendLine("Blocked: $blockedFeatures")
    }
}
