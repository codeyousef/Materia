package io.materia.validation.models

/**
 * Categorizes different types of issues that can be detected during validation.
 *
 * This enum helps classify validation findings to enable targeted fixes
 * and prioritization of different issue categories.
 */
enum class IssueType {
    /**
     * Placeholder or stub code that needs implementation.
     * Includes TODO, FIXME, STUB comments and NotImplementedError calls.
     * Indicates incomplete features that must be finished before production.
     */
    PLACEHOLDER_CODE,

    /**
     * Missing test cases for functionality.
     * Indicates components without adequate test coverage
     * that could harbor undetected bugs.
     */
    MISSING_TEST,

    /**
     * Test coverage below acceptable thresholds.
     * Indicates insufficient testing that doesn't meet
     * the constitutional 80% coverage requirement.
     */
    LOW_COVERAGE,

    /**
     * Performance degradation compared to baseline.
     * Indicates the code doesn't meet the constitutional
     * 60 FPS performance requirement.
     */
    PERFORMANCE_REGRESSION,

    /**
     * Security vulnerability detected.
     * Includes known CVEs, unsafe operations, or
     * potential attack vectors that need immediate attention.
     */
    SECURITY_VULNERABILITY,

    /**
     * Code compilation errors.
     * Indicates syntax errors, type mismatches, or
     * other issues preventing successful compilation.
     */
    COMPILATION_ERROR,

    /**
     * Missing expect/actual implementations in multiplatform code.
     * Indicates platform-specific implementations are missing
     * for declared expected declarations.
     */
    MISSING_EXPECT_ACTUAL,

    /**
     * API inconsistency across platforms.
     * Indicates the same API behaves differently on different
     * platforms, violating cross-platform consistency requirements.
     */
    API_INCONSISTENCY,

    /**
     * Missing or inadequate documentation.
     * Indicates public APIs, complex logic, or important
     * components lacking proper documentation.
     */
    DOCUMENTATION_MISSING,

    /**
     * Violation of Materia constitutional requirements.
     * Includes failures to meet core requirements like:
     * - 60 FPS performance target
     * - 5MB library size limit
     * - Type safety guarantees
     * - Cross-platform consistency
     */
    CONSTITUTIONAL_VIOLATION
}