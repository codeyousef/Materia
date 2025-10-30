package io.materia.validation.models

/**
 * Represents the status of a validation check or test result.
 *
 * This enum defines the possible outcomes when validating components,
 * tests, or requirements in the Materia validation system.
 */
enum class ValidationStatus {
    /**
     * The validation check passed all requirements and constraints.
     * Indicates the component meets production readiness criteria.
     */
    PASSED,

    /**
     * The validation check failed to meet required criteria.
     * Indicates the component needs fixes before production deployment.
     */
    FAILED,

    /**
     * The validation check passed but with potential issues.
     * Indicates the component works but may benefit from improvements.
     */
    WARNING,

    /**
     * The validation check was intentionally skipped.
     * May occur due to configuration, platform limitations, or conditional execution.
     */
    SKIPPED,

    /**
     * An error occurred during validation execution.
     * Indicates the validation itself failed to run properly, not the component being tested.
     */
    ERROR
}