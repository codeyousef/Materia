package io.materia.validation.exceptions

/**
 * Base exception for all validation-related errors in the Materia validation system.
 */
open class ValidationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when compilation validation fails for one or more platforms.
 */
class CompilationValidationException(
    val failedPlatforms: List<String>,
    message: String = "Compilation failed for platforms: ${failedPlatforms.joinToString(", ")}",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when test coverage does not meet the required threshold.
 */
class TestCoverageException(
    val actualCoverage: Float,
    val requiredCoverage: Float,
    message: String = "Test coverage ${actualCoverage}% is below required ${requiredCoverage}%",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when performance benchmarks fail to meet requirements.
 */
class PerformanceValidationException(
    val metric: String,
    val actual: Number,
    val required: Number,
    message: String = "Performance metric '$metric' failed: $actual does not meet requirement $required",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when security vulnerabilities are detected.
 */
class SecurityValidationException(
    val vulnerabilities: List<SecurityVulnerability>,
    message: String = "Found ${vulnerabilities.size} security vulnerabilities",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Represents a security vulnerability found during validation.
 */
data class SecurityVulnerability(
    val cve: String?,
    val severity: String,
    val dependency: String,
    val description: String
)

/**
 * Thrown when constitutional requirements are violated.
 */
class ConstitutionalViolationException(
    val violations: List<String>,
    message: String = "Constitutional violations found: ${violations.joinToString("; ")}",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when validation configuration is invalid.
 */
class ValidationConfigurationException(
    message: String,
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when a required validation platform is not available.
 */
class PlatformNotAvailableException(
    val platform: String,
    message: String = "Platform '$platform' is not available for validation",
    cause: Throwable? = null
) : ValidationException(message, cause)

/**
 * Thrown when validation times out.
 */
class ValidationTimeoutException(
    val timeoutMillis: Long,
    message: String = "Validation timed out after ${timeoutMillis}ms",
    cause: Throwable? = null
) : ValidationException(message, cause)