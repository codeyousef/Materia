package io.materia.validation.api

import io.materia.validation.models.ValidationStatus

/**
 * Base interface for all validation services in the Materia validation system.
 *
 * Validators are responsible for checking specific aspects of the codebase
 * and returning structured results that can be aggregated into a comprehensive
 * production readiness report.
 *
 * @param T The type of result returned by this validator
 */
interface Validator<T : ValidationResult> {
    /**
     * The name of this validator for reporting purposes.
     */
    val name: String

    /**
     * Performs the validation check asynchronously.
     *
     * @param context The validation context containing configuration and paths
     * @return The validation result of type T
     * @throws ValidationException if the validation cannot be performed
     */
    suspend fun validate(context: ValidationContext): T

    /**
     * Determines if this validator is applicable for the current context.
     * Some validators may not apply to all platforms or configurations.
     *
     * @param context The validation context to check
     * @return true if this validator should run, false otherwise
     */
    fun isApplicable(context: ValidationContext): Boolean = true
}

/**
 * Base interface for all validation results.
 */
interface ValidationResult {
    /**
     * The overall status of the validation.
     */
    val status: ValidationStatus

    /**
     * A numeric score from 0.0 to 1.0 representing the validation quality.
     */
    val score: Float

    /**
     * Human-readable message describing the validation result.
     */
    val message: String

    /**
     * Detailed findings or metrics from the validation.
     */
    val details: Map<String, Any>
}

/**
 * Context information provided to validators.
 *
 * @property projectPath The root path of the project being validated
 * @property platforms The list of platforms to validate (null means all)
 * @property configuration The validation configuration settings
 * @property metadata Additional context-specific metadata
 */
data class ValidationContext(
    val projectPath: String,
    val platforms: List<String>? = null,
    val configuration: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Exception thrown when validation fails to execute.
 */
class ValidationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)