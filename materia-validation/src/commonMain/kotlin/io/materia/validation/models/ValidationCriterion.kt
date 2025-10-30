package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * Individual validation check with detailed results.
 *
 * Represents a specific requirement or constraint that is validated,
 * such as "Frame rate >= 60 FPS", "Test coverage >= 80%", or
 * "No TODO comments in production code". Each criterion provides
 * detailed information about what was expected versus what was found.
 *
 * @property id Unique identifier for this criterion (e.g., "perf.fps.requirement").
 * @property name Human-readable name of the criterion.
 * @property description Detailed explanation of what this criterion validates.
 * @property requirement The expected value or threshold (e.g., ">= 60 FPS").
 * @property actual The measured or observed value (e.g., "45 FPS").
 * @property status The validation result for this specific criterion.
 * @property severity The importance level of this criterion for production readiness.
 * @property details Additional metadata and context about the validation.
 * @property platform Optional platform-specific criterion (null for cross-platform criteria).
 */
@Serializable
data class ValidationCriterion(
    val id: String,
    val name: String,
    val description: String,
    val requirement: String,
    val actual: String,
    val status: ValidationStatus,
    val severity: Severity,
    val details: Map<String, String>, // Changed from Any to String for better serialization
    val platform: Platform? = null
) {
    /**
     * Determines if this criterion passed validation.
     */
    val passed: Boolean
        get() = status == ValidationStatus.PASSED

    /**
     * Determines if this criterion failed validation.
     */
    val failed: Boolean
        get() = status == ValidationStatus.FAILED

    /**
     * Determines if this is a critical failure that blocks production.
     */
    val isCriticalFailure: Boolean
        get() = severity == Severity.CRITICAL && failed

    /**
     * Determines if this is a platform-specific criterion.
     */
    val isPlatformSpecific: Boolean
        get() = platform != null

    /**
     * Gets a formatted platform prefix for display.
     */
    val platformPrefix: String
        get() = platform?.let { "[$it] " } ?: ""

    /**
     * Generates a concise summary of the validation result.
     */
    fun generateSummary(): String = buildString {
        val statusIcon = when (status) {
            ValidationStatus.PASSED -> "✓"
            ValidationStatus.FAILED -> "✗"
            ValidationStatus.WARNING -> "⚠"
            ValidationStatus.SKIPPED -> "○"
            ValidationStatus.ERROR -> "!"
        }

        append("$statusIcon $platformPrefix$name: ")
        append("Expected $requirement, ")
        append("got $actual")

        if (severity == Severity.CRITICAL && failed) {
            append(" [CRITICAL]")
        }
    }

    /**
     * Generates a detailed report of this criterion.
     */
    fun generateDetailedReport(): String = buildString {
        appendLine("Criterion: $name")
        appendLine("  ID: $id")
        appendLine("  Description: $description")
        platform?.let { appendLine("  Platform: $it") }
        appendLine("  Status: $status")
        appendLine("  Severity: $severity")
        appendLine("  Requirement: $requirement")
        appendLine("  Actual: $actual")
        if (details.isNotEmpty()) {
            appendLine("  Details:")
            details.forEach { (key, value) ->
                appendLine("    $key: $value")
            }
        }
    }

    /**
     * Creates a remediation suggestion based on the criterion failure.
     */
    fun generateRemediationHint(): String? = when {
        !failed -> null
        id.startsWith("perf.fps") -> "Optimize rendering performance to achieve $requirement frame rate"
        id.startsWith("test.coverage") -> "Add more tests to achieve $requirement coverage"
        id.startsWith("size.artifact") -> "Reduce dependencies or optimize code to meet $requirement size limit"
        id.startsWith("compile") -> "Fix compilation errors in $actual"
        id.startsWith("security") -> "Address security vulnerability: $actual"
        else -> "Fix issue: Expected $requirement but got $actual"
    }

    companion object {
        /**
         * Common criterion ID prefixes for categorization.
         */
        object IdPrefixes {
            const val PERFORMANCE = "perf"
            const val TESTING = "test"
            const val COMPILATION = "compile"
            const val SECURITY = "security"
            const val SIZE = "size"
            const val CONSTITUTIONAL = "const"
            const val DOCUMENTATION = "docs"
            const val DEPENDENCIES = "deps"
            const val CODE_QUALITY = "quality"
        }

        /**
         * Creates a criterion for a successful validation.
         */
        fun success(
            id: String,
            name: String,
            description: String,
            requirement: String,
            actual: String,
            severity: Severity = Severity.MEDIUM,
            details: Map<String, String> = emptyMap(),
            platform: Platform? = null
        ) = ValidationCriterion(
            id = id,
            name = name,
            description = description,
            requirement = requirement,
            actual = actual,
            status = ValidationStatus.PASSED,
            severity = severity,
            details = details,
            platform = platform
        )

        /**
         * Creates a criterion for a failed validation.
         */
        fun failure(
            id: String,
            name: String,
            description: String,
            requirement: String,
            actual: String,
            severity: Severity = Severity.MEDIUM,
            details: Map<String, String> = emptyMap(),
            platform: Platform? = null
        ) = ValidationCriterion(
            id = id,
            name = name,
            description = description,
            requirement = requirement,
            actual = actual,
            status = ValidationStatus.FAILED,
            severity = severity,
            details = details,
            platform = platform
        )
    }
}