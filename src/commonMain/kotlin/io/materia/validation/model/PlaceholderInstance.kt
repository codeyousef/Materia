package io.materia.validation.model

import kotlinx.serialization.Serializable

/**
 * Represents a detected placeholder pattern in the codebase.
 *
 * Placeholders are temporary markers like TODO, FIXME, STUB that indicate
 * incomplete or temporary implementations that need to be addressed before
 * production deployment.
 */
@Serializable
data class PlaceholderInstance(
    /**
     * Absolute path to file containing the placeholder.
     */
    val filePath: String,

    /**
     * Line number where placeholder was found (1-based).
     */
    val lineNumber: Int,

    /**
     * Column position of placeholder start (1-based).
     */
    val columnNumber: Int,

    /**
     * The specific pattern matched (e.g., "TODO", "FIXME", "STUB").
     */
    val pattern: String,

    /**
     * Surrounding code context (±2 lines).
     */
    val context: String,

    /**
     * Classification of placeholder type.
     */
    val type: PlaceholderType,

    /**
     * Impact level of this placeholder on production readiness.
     */
    val criticality: CriticalityLevel,

    /**
     * Materia module where placeholder exists.
     */
    val module: String,

    /**
     * Platform-specific context (if applicable).
     */
    val platform: String? = null
) {

    /**
     * Validates that this placeholder instance has valid data.
     * @return SimpleValidationResult indicating success or failure with details
     */
    fun validate(): SimpleValidationResult {
        val errors = mutableListOf<String>()

        if (filePath.isBlank()) {
            errors.add("File path cannot be blank")
        }

        if (lineNumber <= 0) {
            errors.add("Line number must be positive, got: $lineNumber")
        }

        if (columnNumber <= 0) {
            errors.add("Column number must be positive, got: $columnNumber")
        }

        if (pattern.isBlank()) {
            errors.add("Pattern cannot be blank")
        }

        if (module.isBlank()) {
            errors.add("Module cannot be blank")
        }

        return if (errors.isEmpty()) {
            SimpleValidationResult.success()
        } else {
            SimpleValidationResult.failure(errors)
        }
    }

    /**
     * Checks if this placeholder blocks production deployment.
     * @return true if criticality is CRITICAL or HIGH
     */
    fun blocksProduction(): Boolean {
        return criticality == CriticalityLevel.CRITICAL || criticality == CriticalityLevel.HIGH
    }

    /**
     * Gets a human-readable description of this placeholder.
     * @return formatted description including type, criticality, and location
     */
    fun getDescription(): String {
        val platformSuffix = platform?.let { " [$it]" } ?: ""
        return "${type.name} (${criticality.name}) in $module$platformSuffix: $pattern"
    }

    /**
     * Creates a replacement suggestion for this placeholder.
     * @param suggestion the suggested replacement text
     * @param rationale explanation for the suggestion
     * @return ReplacementSuggestion for this placeholder
     */
    fun createReplacementSuggestion(suggestion: String, rationale: String): ReplacementSuggestion {
        return ReplacementSuggestion(
            placeholder = this,
            suggestedReplacement = suggestion,
            rationale = rationale,
            estimatedEffort = when (criticality) {
                CriticalityLevel.CRITICAL -> EffortLevel.LARGE
                CriticalityLevel.HIGH -> EffortLevel.MEDIUM
                CriticalityLevel.MEDIUM -> EffortLevel.SMALL
                CriticalityLevel.LOW -> EffortLevel.TRIVIAL
            }
        )
    }

    /**
     * Checks if this placeholder is platform-specific.
     * @return true if platform is not null
     */
    fun isPlatformSpecific(): Boolean = platform != null

    /**
     * Gets the priority score for replacement ordering.
     * Higher scores indicate higher priority.
     * @return priority score (0-100)
     */
    fun getPriorityScore(): Int {
        val criticalityScore = when (criticality) {
            CriticalityLevel.CRITICAL -> 40
            CriticalityLevel.HIGH -> 30
            CriticalityLevel.MEDIUM -> 20
            CriticalityLevel.LOW -> 10
        }

        val typeScore = when (type) {
            PlaceholderType.FIXME -> 25
            PlaceholderType.STUB -> 20
            PlaceholderType.TODO -> 15
            PlaceholderType.PLACEHOLDER -> 10
            PlaceholderType.TEMPORARY -> 10
            PlaceholderType.MOCK -> 5
        }

        return criticalityScore + typeScore
    }
}

/**
 * Classification of placeholder types found in the codebase.
 */
@Serializable
enum class PlaceholderType {
    /** Work item marker */
    TODO,

    /** Bug or issue marker */
    FIXME,

    /** Incomplete implementation */
    STUB,

    /** Temporary content */
    PLACEHOLDER,

    /** Interim solution marker */
    TEMPORARY,

    /** Test or development mock */
    MOCK
}

/**
 * Impact level of placeholders on production readiness.
 */
@Serializable
enum class CriticalityLevel {
    /** Blocks production deployment */
    CRITICAL,

    /** Significant functionality impact */
    HIGH,

    /** Minor functionality impact */
    MEDIUM,

    /** Documentation or non-functional impact */
    LOW
}

/**
 * Replacement suggestion for a placeholder instance.
 */
@Serializable
data class ReplacementSuggestion(
    val placeholder: PlaceholderInstance,
    val suggestedReplacement: String,
    val rationale: String,
    val estimatedEffort: EffortLevel
)

/**
 * Simple validation result for data validation operations.
 */
@Serializable
data class SimpleValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success() = SimpleValidationResult(true)
        fun failure(errors: List<String>) = SimpleValidationResult(false, errors)
        fun failure(error: String) = SimpleValidationResult(false, listOf(error))
    }
}

/**
 * Estimated implementation effort levels.
 */
@Serializable
enum class EffortLevel {
    /** <1 hour implementation */
    TRIVIAL,

    /** 1-4 hours implementation */
    SMALL,

    /** 4-16 hours implementation */
    MEDIUM,

    /** >16 hours implementation */
    LARGE
}