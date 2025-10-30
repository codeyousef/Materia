package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * Represents the codebase's compliance with Materia constitutional principles.
 *
 * The Materia constitution defines core principles that must be upheld:
 * - Test-Driven Development (TDD)
 * - Production-ready code quality
 * - Cross-platform compatibility
 * - Performance standards (60 FPS, 5MB size)
 * - Type safety (no runtime casts)
 */
@Serializable
data class ConstitutionalCompliance(
    /**
     * Whether Test-Driven Development practices are followed.
     * Requires tests written before implementation, >80% coverage.
     */
    val tddCompliance: Boolean,

    /**
     * Whether code meets production readiness standards.
     * No placeholders, complete implementations, proper error handling.
     */
    val productionReadyCode: Boolean,

    /**
     * Whether code works consistently across all target platforms.
     * API behavior must be identical on JVM, JS, Native, Android, iOS.
     */
    val crossPlatformCompatibility: Boolean,

    /**
     * Whether performance meets constitutional requirements.
     * 60 FPS with 100k triangles, <5MB library size, <1s initialization.
     */
    val performanceStandards: Boolean,

    /**
     * Whether code maintains compile-time type safety.
     * No use of Any type, no unchecked casts, no runtime type checking.
     */
    val typeSafety: Boolean,

    /**
     * List of constitutional violations found.
     */
    val violations: List<ConstitutionalViolation> = emptyList()
) {

    /**
     * Checks if fully compliant with all constitutional principles.
     * @return true if all compliance flags are true and no violations
     */
    fun isFullyCompliant(): Boolean {
        return tddCompliance &&
                productionReadyCode &&
                crossPlatformCompatibility &&
                performanceStandards &&
                typeSafety &&
                violations.isEmpty()
    }

    /**
     * Calculates overall compliance score (0.0-1.0).
     * @return weighted average of compliance areas
     */
    fun calculateComplianceScore(): Float {
        val baseScore = listOf(
            tddCompliance,
            productionReadyCode,
            crossPlatformCompatibility,
            performanceStandards,
            typeSafety
        ).count { it } / 5.0f

        // Penalty for violations
        val violationPenalty = (violations.size * 0.05f).coerceAtMost(0.3f)

        return (baseScore - violationPenalty).coerceIn(0.0f, 1.0f)
    }

    /**
     * Gets critical violations that must be addressed immediately.
     * @return violations of CRITICAL severity
     */
    fun getCriticalViolations(): List<ConstitutionalViolation> {
        return violations.filter { it.isCritical() }
    }

    /**
     * Gets violations grouped by principle.
     * @return map of principle to list of violations
     */
    fun getViolationsByPrinciple(): Map<ConstitutionalPrinciple, List<ConstitutionalViolation>> {
        return violations.groupBy { it.principle }
    }

    /**
     * Generates compliance report summary.
     * @return human-readable compliance summary
     */
    fun generateSummary(): String {
        val score = (calculateComplianceScore() * 100).toInt()
        val compliantAreas = mutableListOf<String>()
        val nonCompliantAreas = mutableListOf<String>()

        if (tddCompliance) compliantAreas.add("TDD") else nonCompliantAreas.add("TDD")
        if (productionReadyCode) compliantAreas.add("Production Ready") else nonCompliantAreas.add("Production Ready")
        if (crossPlatformCompatibility) compliantAreas.add("Cross-Platform") else nonCompliantAreas.add(
            "Cross-Platform"
        )
        if (performanceStandards) compliantAreas.add("Performance") else nonCompliantAreas.add("Performance")
        if (typeSafety) compliantAreas.add("Type Safety") else nonCompliantAreas.add("Type Safety")

        return buildString {
            append("Constitutional Compliance: $score%\n")

            if (compliantAreas.isNotEmpty()) {
                append("✅ Compliant: ${compliantAreas.joinToString(", ")}\n")
            }

            if (nonCompliantAreas.isNotEmpty()) {
                append("❌ Non-Compliant: ${nonCompliantAreas.joinToString(", ")}\n")
            }

            if (violations.isNotEmpty()) {
                append("⚠️ ${violations.size} violations found")
                val critical = getCriticalViolations()
                if (critical.isNotEmpty()) {
                    append(" (${critical.size} critical)")
                }
            }
        }
    }

    /**
     * Creates an action plan to achieve full compliance.
     * @return prioritized list of actions needed
     */
    fun createComplianceActionPlan(): List<ComplianceAction> {
        val actions = mutableListOf<ComplianceAction>()

        if (!tddCompliance) {
            actions.add(
                ComplianceAction(
                    principle = ConstitutionalPrinciple.TEST_DRIVEN_DEVELOPMENT,
                    action = "Implement comprehensive test suite with >80% coverage",
                    priority = 4,
                    estimatedEffort = "16-32 hours"
                )
            )
        }

        if (!productionReadyCode) {
            actions.add(
                ComplianceAction(
                    principle = ConstitutionalPrinciple.PRODUCTION_READINESS,
                    action = "Replace all placeholders and complete implementations",
                    priority = 5,
                    estimatedEffort = "24-48 hours"
                )
            )
        }

        if (!crossPlatformCompatibility) {
            actions.add(
                ComplianceAction(
                    principle = ConstitutionalPrinciple.CROSS_PLATFORM,
                    action = "Ensure API consistency across all platforms",
                    priority = 4,
                    estimatedEffort = "8-16 hours"
                )
            )
        }

        if (!performanceStandards) {
            actions.add(
                ComplianceAction(
                    principle = ConstitutionalPrinciple.PERFORMANCE,
                    action = "Optimize to achieve 60 FPS and <5MB size",
                    priority = 3,
                    estimatedEffort = "16-24 hours"
                )
            )
        }

        if (!typeSafety) {
            actions.add(
                ComplianceAction(
                    principle = ConstitutionalPrinciple.TYPE_SAFETY,
                    action = "Remove runtime type checks and unchecked casts",
                    priority = 3,
                    estimatedEffort = "8-12 hours"
                )
            )
        }

        // Add actions for violations
        violations.forEach { violation ->
            actions.add(
                ComplianceAction(
                    principle = violation.principle,
                    action = "Fix: ${violation.description}",
                    priority = if (violation.isCritical()) 5 else 2,
                    estimatedEffort = "1-4 hours"
                )
            )
        }

        return actions.sortedByDescending { it.priority }
    }
}

/**
 * Represents a specific constitutional violation.
 */
@Serializable
data class ConstitutionalViolation(
    /**
     * Constitutional principle that was violated.
     */
    val principle: ConstitutionalPrinciple,

    /**
     * Description of the violation.
     */
    val description: String,

    /**
     * Evidence or example of the violation.
     */
    val evidence: String,

    /**
     * Location in code where violation occurs, null if not specific to one location.
     */
    val location: CodeLocation? = null
) {

    /**
     * Determines if this is a critical violation requiring immediate attention.
     * @return true if violation affects production readiness or performance
     */
    fun isCritical(): Boolean {
        return principle in setOf(
            ConstitutionalPrinciple.PRODUCTION_READINESS,
            ConstitutionalPrinciple.PERFORMANCE
        )
    }

    /**
     * Gets formatted violation description with location.
     * @return complete violation details
     */
    fun getFullDescription(): String {
        val locationStr = location?.toString()?.let { " at $it" } ?: ""
        return "${principle.displayName}: $description$locationStr\n  Evidence: $evidence"
    }
}

/**
 * Constitutional principles that must be upheld.
 */
@Serializable
enum class ConstitutionalPrinciple(val displayName: String) {
    /** Test-Driven Development with comprehensive coverage */
    TEST_DRIVEN_DEVELOPMENT("TDD Compliance"),

    /** Production-ready code without placeholders */
    PRODUCTION_READINESS("Production Readiness"),

    /** Consistent behavior across all platforms */
    CROSS_PLATFORM("Cross-Platform Compatibility"),

    /** 60 FPS, 5MB size, fast initialization */
    PERFORMANCE("Performance Standards"),

    /** Compile-time type safety, no runtime casts */
    TYPE_SAFETY("Type Safety")
}

/**
 * Action needed to achieve constitutional compliance.
 */
@Serializable
data class ComplianceAction(
    /**
     * Principle this action addresses.
     */
    val principle: ConstitutionalPrinciple,

    /**
     * Description of action to take.
     */
    val action: String,

    /**
     * Priority level (1-5, 5 being highest).
     */
    val priority: Int,

    /**
     * Estimated effort to complete.
     */
    val estimatedEffort: String
) {

    /**
     * Gets formatted action item for task tracking.
     * @return formatted action string
     */
    fun toTaskItem(): String {
        return "[$priority] ${principle.displayName}: $action ($estimatedEffort)"
    }
}