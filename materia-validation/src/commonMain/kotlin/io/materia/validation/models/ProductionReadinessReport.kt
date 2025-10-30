package io.materia.validation.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * Comprehensive report assessing the production readiness of the Materia codebase.
 *
 * This is the top-level entity that aggregates all validation results,
 * including category assessments, remediation actions, and overall scoring.
 * The report provides a complete snapshot of the codebase's state at a specific
 * point in time, helping teams understand what needs to be addressed before
 * production deployment.
 *
 * @property timestamp The exact time when the validation was performed.
 * @property branchName The Git branch name that was validated.
 * @property commitHash The Git commit hash that was validated.
 * @property overallStatus The aggregate validation status based on all categories.
 * @property overallScore The weighted score from 0.0 (failing) to 1.0 (perfect).
 * @property categories List of validation categories with their individual assessments.
 * @property remediationActions List of actionable steps to fix identified issues.
 * @property executionTime The total time taken to perform all validations.
 */
@Serializable
data class ProductionReadinessReport(
    val timestamp: Instant,
    val branchName: String,
    val commitHash: String,
    val overallStatus: ValidationStatus,
    val overallScore: Float, // 0.0 to 1.0
    val categories: List<ValidationCategory>,
    val remediationActions: List<RemediationAction>,
    val executionTime: Duration
) {
    init {
        require(overallScore in 0.0f..1.0f) {
            "Overall score must be between 0.0 and 1.0, got $overallScore"
        }
    }

    /**
     * Determines if the codebase is production ready based on the overall status.
     * Only PASSED and WARNING statuses are considered production ready.
     */
    val isProductionReady: Boolean
        get() = overallStatus in listOf(ValidationStatus.PASSED, ValidationStatus.WARNING)

    /**
     * Gets the count of failed critical criteria across all categories.
     */
    val criticalFailureCount: Int
        get() = categories.flatMap { it.criteria }
            .count { it.severity == Severity.CRITICAL && it.status == ValidationStatus.FAILED }

    /**
     * Gets high-priority remediation actions (priority 1 or 2).
     */
    val highPriorityActions: List<RemediationAction>
        get() = remediationActions.filter { it.priority <= 2 }

    /**
     * Gets remediation actions that can be automated.
     */
    val automatableActions: List<RemediationAction>
        get() = remediationActions.filter { it.automatable }

    /**
     * Generates a summary string for quick overview.
     */
    fun generateSummary(): String = buildString {
        appendLine("Production Readiness Report")
        appendLine("===========================")
        appendLine("Branch: $branchName")
        appendLine("Commit: ${commitHash.take(8)}")
        appendLine("Status: $overallStatus")
        appendLine("Score: ${(overallScore * 100).toInt()}%")
        appendLine("Execution Time: $executionTime")
        appendLine()
        appendLine("Categories:")
        categories.forEach { category ->
            val statusIcon = when (category.status) {
                ValidationStatus.PASSED -> "✓"
                ValidationStatus.FAILED -> "✗"
                ValidationStatus.WARNING -> "⚠"
                ValidationStatus.SKIPPED -> "○"
                ValidationStatus.ERROR -> "!"
            }
            appendLine("  $statusIcon ${category.name}: ${(category.score * 100).toInt()}%")
        }
        if (remediationActions.isNotEmpty()) {
            appendLine()
            appendLine("Remediation Actions: ${remediationActions.size}")
            appendLine("  High Priority: ${highPriorityActions.size}")
            appendLine("  Automatable: ${automatableActions.size}")
        }
    }
}