package io.materia.validation.models

import io.materia.validation.Platform
import io.materia.validation.ValidationStatus
import kotlinx.serialization.Serializable

/**
 * Represents the assessment results for a specific module in the codebase.
 *
 * Module assessments track implementation quality, test coverage, compilation status,
 * performance metrics, and issues for individual Materia modules.
 */
@Serializable
data class ModuleAssessment(
    /**
     * Name of the module (e.g., "materia-core", "materia-renderer").
     */
    val moduleName: String,

    /**
     * Relative path to the module directory.
     */
    val path: String,

    /**
     * Overall validation status for this module.
     */
    val status: ValidationStatus,

    /**
     * Test coverage percentage (0.0-1.0), null if not measured.
     */
    val testCoverage: Float? = null,

    /**
     * Compilation status per platform.
     * Maps platform to true if compilation successful, false otherwise.
     */
    val compilationStatus: Map<Platform, Boolean> = emptyMap(),

    /**
     * Performance metrics for this module, null if not measured.
     */
    val performanceMetrics: PerformanceMetrics? = null,

    /**
     * List of validation issues found in this module.
     */
    val issues: List<ValidationIssue> = emptyList()
) {

    /**
     * Checks if the module compiles on all platforms.
     * @return true if compilation succeeds on all platforms
     */
    fun compilesOnAllPlatforms(): Boolean {
        return compilationStatus.isNotEmpty() && compilationStatus.values.all { it }
    }

    /**
     * Checks if the module compiles on core platforms (JVM and JS).
     * @return true if compilation succeeds on core platforms
     */
    fun compilesOnCorePlatforms(): Boolean {
        val jvmCompiles = compilationStatus[Platform.JVM] ?: false
        val jsCompiles = compilationStatus[Platform.JS] ?: false
        return jvmCompiles && jsCompiles
    }

    /**
     * Checks if the module meets test coverage requirements.
     * @param threshold minimum required coverage (default 0.8)
     * @return true if coverage meets threshold
     */
    fun meetsTestCoverageRequirement(threshold: Float = 0.8f): Boolean {
        return testCoverage?.let { it >= threshold } ?: false
    }

    /**
     * Gets critical issues for this module.
     * @return list of issues with HIGH or CRITICAL severity
     */
    fun getCriticalIssues(): List<ValidationIssue> {
        return issues.filter { it.isCritical() }
    }

    /**
     * Gets auto-fixable issues for this module.
     * @return list of issues that can be automatically fixed
     */
    fun getAutoFixableIssues(): List<ValidationIssue> {
        return issues.filter { it.canAutoFix }
    }

    /**
     * Calculates health score for this module (0.0-1.0).
     * @return module health score based on compilation, coverage, and issues
     */
    fun calculateHealthScore(): Float {
        val compilationScore = when {
            compilesOnAllPlatforms() -> 1.0f
            compilesOnCorePlatforms() -> 0.7f
            compilationStatus.any { it.value } -> 0.3f
            else -> 0.0f
        }

        val coverageScore = testCoverage ?: 0.5f

        val issueScore = when {
            issues.isEmpty() -> 1.0f
            getCriticalIssues().isEmpty() -> 0.7f
            issues.size <= 5 -> 0.5f
            else -> 0.2f
        }

        val performanceScore = performanceMetrics?.calculateOverallScore() ?: 0.5f

        return (compilationScore * 0.3f) +
                (coverageScore * 0.3f) +
                (issueScore * 0.2f) +
                (performanceScore * 0.2f)
    }

    /**
     * Generates a summary description of the module assessment.
     * @return human-readable summary string
     */
    fun getSummary(): String {
        val healthPercent = (calculateHealthScore() * 100).toInt()
        val coveragePercent = testCoverage?.let { (it * 100).toInt() } ?: 0
        val platformCount = compilationStatus.count { it.value }
        val criticalCount = getCriticalIssues().size

        return "$moduleName: $healthPercent% healthy, " +
                "$coveragePercent% coverage, " +
                "$platformCount/${compilationStatus.size} platforms compile" +
                if (criticalCount > 0) ", $criticalCount critical issues" else ""
    }
}