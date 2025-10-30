package io.materia.verification

import io.materia.verification.model.QualityGate
import io.materia.verification.model.QualityCriteria

/**
 * Constitutional quality gates for Materia library production readiness
 * These gates enforce the constitutional requirements and ensure production quality
 */
object QualityGates {

    /**
     * Constitutional Gate 1: Zero Placeholder Implementations
     * Enforces the "Production-Ready Code Only" constitutional principle
     */
    val ZERO_PLACEHOLDERS = QualityGate(
        name = "Zero Placeholders",
        description = "All placeholder implementations must be replaced with production code",
        criteria = listOf(
            QualityCriteria(
                name = "Zero Placeholders",
                description = "No TODO, FIXME, stub, or placeholder patterns in production paths",
                threshold = "0",
                measurement = "Count of placeholder patterns"
            ),
            QualityCriteria(
                name = "No NotImplementedError",
                description = "No Kotlin TODO() or NotImplementedError() calls",
                threshold = "0",
                measurement = "Count of NotImplementedError instances"
            ),
            QualityCriteria(
                name = "No Critical Placeholders",
                description = "No critical severity placeholder patterns",
                threshold = "0",
                measurement = "Count of critical placeholders"
            )
        ),
        required = true,
        automatable = true,
        constitutionalRequirement = true
    )

    /**
     * Constitutional Gate 2: Test-Driven Development Compliance
     * Enforces the TDD methodology constitutional requirement
     */
    val TDD_COMPLIANCE = QualityGate(
        name = "TDD Compliance",
        description = "All code must follow Test-Driven Development methodology",
        criteria = listOf(
            QualityCriteria(
                name = "Test Coverage",
                description = "Minimum test coverage for all production code",
                threshold = "80%",
                measurement = "Percentage of code covered by tests"
            ),
            QualityCriteria(
                name = "Test-First Implementation",
                description = "Tests written before implementation (TDD compliance)",
                threshold = "100%",
                measurement = "Percentage of features with test-first implementation"
            ),
            QualityCriteria(
                name = "Failing Tests Before Implementation",
                description = "All tests must fail before implementation (Red phase)",
                threshold = "100%",
                measurement = "Percentage of tests that failed before implementation"
            )
        ),
        required = true,
        automatable = true,
        constitutionalRequirement = true
    )

    /**
     * Constitutional Gate 3: Performance Standards
     * Enforces the 60 FPS performance constitutional requirement
     */
    val PERFORMANCE_STANDARDS = QualityGate(
        name = "Performance Standards",
        description = "Library must meet constitutional performance requirements",
        criteria = listOf(
            QualityCriteria(
                name = "Frame Rate",
                description = "Maintain 60 FPS with 100k+ triangles",
                threshold = "60 FPS",
                measurement = "Frames per second under standard load"
            ),
            QualityCriteria(
                name = "Initialization Time",
                description = "Library initialization under 100ms",
                threshold = "100ms",
                measurement = "Time to initialize renderer"
            ),
            QualityCriteria(
                name = "Memory Usage",
                description = "Base library size under 5MB",
                threshold = "5MB",
                measurement = "Compiled library size"
            ),
            QualityCriteria(
                name = "GPU Memory Efficiency",
                description = "Efficient GPU memory usage without leaks",
                threshold = "0 leaks",
                measurement = "GPU memory leak detection"
            )
        ),
        required = true,
        automatable = true,
        constitutionalRequirement = true
    )

    /**
     * Constitutional Gate 4: Cross-Platform Compatibility
     * Enforces multiplatform constitutional requirement
     */
    val CROSS_PLATFORM_COMPATIBILITY = QualityGate(
        name = "Cross-Platform Compatibility",
        description = "Library must compile and function on all target platforms",
        criteria = listOf(
            QualityCriteria(
                name = "JVM Compilation",
                description = "Successful compilation for JVM target",
                threshold = "100%",
                measurement = "Compilation success rate"
            ),
            QualityCriteria(
                name = "JavaScript Compilation",
                description = "Successful compilation for JS target",
                threshold = "100%",
                measurement = "Compilation success rate"
            ),
            QualityCriteria(
                name = "Native Compilation",
                description = "Successful compilation for Native targets",
                threshold = "100%",
                measurement = "Compilation success rate"
            ),
            QualityCriteria(
                name = "Platform API Consistency",
                description = "Consistent API behavior across platforms",
                threshold = "100%",
                measurement = "Cross-platform test pass rate"
            )
        ),
        required = true,
        automatable = true,
        constitutionalRequirement = true
    )

    /**
     * Constitutional Gate 5: Type Safety and API Design
     * Enforces type-safe API constitutional requirement
     */
    val TYPE_SAFETY = QualityGate(
        name = "Type Safety",
        description = "API must be type-safe with no runtime casts",
        criteria = listOf(
            QualityCriteria(
                name = "No Runtime Casts",
                description = "No unsafe casting in public API",
                threshold = "0",
                measurement = "Count of unsafe cast operations"
            ),
            QualityCriteria(
                name = "Compile-Time Validation",
                description = "All API contracts validated at compile time",
                threshold = "100%",
                measurement = "Percentage of type-safe operations"
            ),
            QualityCriteria(
                name = "Null Safety",
                description = "Proper null safety throughout API",
                threshold = "100%",
                measurement = "Null safety compliance"
            )
        ),
        required = true,
        automatable = true,
        constitutionalRequirement = true
    )

    /**
     * Quality Gate: Documentation Completeness
     * Ensures proper documentation for production readiness
     */
    val DOCUMENTATION_COMPLETENESS = QualityGate(
        name = "Documentation Completeness",
        description = "All public API must be properly documented",
        criteria = listOf(
            QualityCriteria(
                name = "API Documentation",
                description = "KDoc comments for all public classes and methods",
                threshold = "100%",
                measurement = "Percentage of documented public API"
            ),
            QualityCriteria(
                name = "Usage Examples",
                description = "Code examples for major features",
                threshold = "100%",
                measurement = "Percentage of features with examples"
            ),
            QualityCriteria(
                name = "Migration Guide",
                description = "Complete migration guide from Three.js",
                threshold = "100%",
                measurement = "Migration documentation completeness"
            )
        ),
        required = false,
        automatable = true,
        constitutionalRequirement = false
    )

    /**
     * Quality Gate: Security Standards
     * Ensures security best practices
     */
    val SECURITY_STANDARDS = QualityGate(
        name = "Security Standards",
        description = "Code must follow security best practices",
        criteria = listOf(
            QualityCriteria(
                name = "No Hardcoded Secrets",
                description = "No API keys, passwords, or secrets in code",
                threshold = "0",
                measurement = "Count of potential secrets"
            ),
            QualityCriteria(
                name = "Input Validation",
                description = "Proper validation of all external inputs",
                threshold = "100%",
                measurement = "Percentage of validated inputs"
            ),
            QualityCriteria(
                name = "Memory Safety",
                description = "No buffer overflows or memory safety issues",
                threshold = "0",
                measurement = "Count of memory safety issues"
            )
        ),
        required = false,
        automatable = true,
        constitutionalRequirement = false
    )

    /**
     * All constitutional gates that must pass
     */
    val CONSTITUTIONAL_GATES = listOf(
        ZERO_PLACEHOLDERS,
        TDD_COMPLIANCE,
        PERFORMANCE_STANDARDS,
        CROSS_PLATFORM_COMPATIBILITY,
        TYPE_SAFETY
    )

    /**
     * Optional quality gates for production excellence
     */
    val OPTIONAL_GATES = listOf(
        DOCUMENTATION_COMPLETENESS,
        SECURITY_STANDARDS
    )

    /**
     * All quality gates
     */
    val ALL_GATES = CONSTITUTIONAL_GATES + OPTIONAL_GATES

    /**
     * Get gates by requirement level
     */
    fun getRequiredGates(): List<QualityGate> = ALL_GATES.filter { it.required }
    fun getOptionalGates(): List<QualityGate> = ALL_GATES.filter { !it.required }
    fun getConstitutionalGates(): List<QualityGate> =
        ALL_GATES.filter { it.constitutionalRequirement }

    /**
     * Get gates that can be automated
     */
    fun getAutomatableGates(): List<QualityGate> = ALL_GATES.filter { it.automatable }

    /**
     * Validate that constitutional gates are properly configured
     */
    fun validateGateConfiguration(): List<String> {
        val errors = mutableListOf<String>()

        for (gate in CONSTITUTIONAL_GATES) {
            if (!gate.required) {
                errors.add("Constitutional gate '${gate.name}' must be required")
            }
            if (!gate.constitutionalRequirement) {
                errors.add("Gate '${gate.name}' should be marked as constitutional requirement")
            }
        }

        return errors
    }
}