package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * High-level grouping of related validation criteria.
 *
 * Represents a major aspect of production readiness such as Compilation,
 * Testing, Performance, Security, or Constitutional compliance.
 * Each category contains multiple criteria that are evaluated together
 * to produce an aggregate score and status.
 *
 * @property name The category identifier (e.g., "Compilation", "Testing", "Performance", "Security", "Constitutional").
 * @property status The aggregate status based on all criteria in this category.
 * @property score The weighted average score from 0.0 (failing) to 1.0 (perfect).
 * @property weight The importance weight used for calculating the overall readiness score.
 * @property criteria List of individual validation checks within this category.
 */
@Serializable
data class ValidationCategory(
    val name: String,
    val status: ValidationStatus,
    val score: Float, // 0.0 to 1.0
    val weight: Float, // Importance weight for overall score
    val criteria: List<ValidationCriterion>
) {
    init {
        require(score in 0.0f..1.0f) {
            "Category score must be between 0.0 and 1.0, got $score"
        }
        require(weight > 0.0f) {
            "Category weight must be positive, got $weight"
        }
    }

    /**
     * Companion object containing standard category names.
     */
    companion object {
        const val COMPILATION = "Compilation"
        const val TESTING = "Testing"
        const val PERFORMANCE = "Performance"
        const val SECURITY = "Security"
        const val CONSTITUTIONAL = "Constitutional"
        const val DOCUMENTATION = "Documentation"
        const val DEPENDENCIES = "Dependencies"
        const val CODE_QUALITY = "Code Quality"
    }

    /**
     * Gets the count of passed criteria in this category.
     */
    val passedCriteriaCount: Int
        get() = criteria.count { it.status == ValidationStatus.PASSED }

    /**
     * Gets the count of failed criteria in this category.
     */
    val failedCriteriaCount: Int
        get() = criteria.count { it.status == ValidationStatus.FAILED }

    /**
     * Gets the count of critical issues in this category.
     */
    val criticalIssueCount: Int
        get() = criteria.count {
            it.severity == Severity.CRITICAL && it.status == ValidationStatus.FAILED
        }

    /**
     * Determines if this category has any critical failures.
     */
    val hasCriticalFailures: Boolean
        get() = criticalIssueCount > 0

    /**
     * Gets criteria that failed in this category.
     */
    val failedCriteria: List<ValidationCriterion>
        get() = criteria.filter { it.status == ValidationStatus.FAILED }

    /**
     * Gets criteria with warnings in this category.
     */
    val warningCriteria: List<ValidationCriterion>
        get() = criteria.filter { it.status == ValidationStatus.WARNING }

    /**
     * Calculates the pass rate as a percentage.
     */
    val passRate: Float
        get() = if (criteria.isEmpty()) 100.0f
        else (passedCriteriaCount.toFloat() / criteria.size.toFloat()) * 100.0f

    /**
     * Generates a detailed summary of this category.
     */
    fun generateSummary(): String = buildString {
        appendLine("Category: $name")
        appendLine("  Status: $status")
        appendLine("  Score: ${(score * 100).toInt()}%")
        appendLine("  Weight: $weight")
        appendLine("  Pass Rate: ${passRate.toInt()}%")
        appendLine("  Criteria: ${criteria.size}")
        appendLine("    Passed: $passedCriteriaCount")
        appendLine("    Failed: $failedCriteriaCount")
        appendLine("    Warnings: ${warningCriteria.size}")
        if (hasCriticalFailures) {
            appendLine("  ⚠ Critical Issues: $criticalIssueCount")
        }
    }

    /**
     * Determines the effective status based on criteria and severity.
     * This can be used to override the provided status with calculated logic.
     */
    fun calculateStatus(): ValidationStatus = when {
        criteria.isEmpty() -> ValidationStatus.SKIPPED
        hasCriticalFailures -> ValidationStatus.FAILED
        failedCriteriaCount > 0 -> ValidationStatus.FAILED
        warningCriteria.isNotEmpty() -> ValidationStatus.WARNING
        criteria.all { it.status == ValidationStatus.PASSED } -> ValidationStatus.PASSED
        criteria.any { it.status == ValidationStatus.ERROR } -> ValidationStatus.ERROR
        else -> ValidationStatus.WARNING
    }
}