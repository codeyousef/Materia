package io.materia.validation.models

/**
 * Represents the severity level of validation issues and findings.
 *
 * Used to prioritize issues and determine whether they block
 * production deployment or can be addressed later.
 */
enum class Severity {
    /**
     * Critical severity issues that must be fixed immediately.
     * These issues block production deployment and may cause
     * application crashes, data loss, or security breaches.
     */
    CRITICAL,

    /**
     * High severity issues that should be fixed before production.
     * These issues significantly impact functionality, performance,
     * or user experience but don't cause immediate failures.
     */
    HIGH,

    /**
     * Medium severity issues that should be addressed soon.
     * These issues affect quality or performance but have workarounds
     * and don't block core functionality.
     */
    MEDIUM,

    /**
     * Low severity issues that can be fixed in future releases.
     * These are typically minor improvements, optimizations,
     * or cosmetic issues that don't impact functionality.
     */
    LOW
}