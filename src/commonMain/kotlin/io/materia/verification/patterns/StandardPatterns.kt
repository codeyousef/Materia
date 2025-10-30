package io.materia.verification.patterns

import io.materia.verification.DetectionPattern
import io.materia.verification.model.PlaceholderType
import io.materia.verification.model.Severity

/**
 * Standard detection patterns for common placeholder types in Kotlin code
 * Provides comprehensive patterns for identifying incomplete implementations
 */
object StandardPatterns {

    /**
     * Enhanced TODO pattern detection including various comment styles
     */
    val TODO = DetectionPattern(
        type = PlaceholderType.TODO,
        regex = """(?i)//\s*todo\b.*|/\*\s*todo\b.*?\*/|TODO\s*:.*|@TODO\b.*""",
        description = "TODO comments indicating incomplete implementation",
        severity = Severity.HIGH
    )

    /**
     * Enhanced FIXME pattern detection for known issues
     */
    val FIXME = DetectionPattern(
        type = PlaceholderType.FIXME,
        regex = """(?i)//\s*fixme\b.*|/\*\s*fixme\b.*?\*/|FIXME\s*:.*|@FIXME\b.*""",
        description = "FIXME comments indicating known issues",
        severity = Severity.CRITICAL
    )

    /**
     * Stub implementation detection
     */
    val STUB = DetectionPattern(
        type = PlaceholderType.STUB,
        regex = """(?i)\bstub\b.*implementation|placeholder.*implementation|stub\s*\{|stub.*method""",
        description = "Stub implementations that need completion",
        severity = Severity.HIGH
    )

    /**
     * General placeholder detection
     */
    val PLACEHOLDER = DetectionPattern(
        type = PlaceholderType.PLACEHOLDER,
        regex = """(?i)//.*placeholder|/\*.*placeholder.*\*/|\bplaceholder\b|PLACEHOLDER.*:|@Placeholder""",
        description = "Placeholder code or comments",
        severity = Severity.MEDIUM
    )

    /**
     * Workaround detection for temporary solutions
     */
    val WORKAROUND = DetectionPattern(
        type = PlaceholderType.WORKAROUND,
        regex = """(?i)//.*workaround|/\*.*workaround.*\*/|\bworkaround\b|WORKAROUND.*:|@Workaround""",
        description = "Temporary workaround solutions",
        severity = Severity.HIGH
    )

    /**
     * "For now" temporary implementations
     */
    val FOR_NOW = DetectionPattern(
        type = PlaceholderType.FOR_NOW,
        regex = """(?i)//.*for now|/\*.*for now.*\*/|\bfor now\b|FOR NOW.*:""",
        description = "Explicitly temporary code marked 'for now'",
        severity = Severity.HIGH
    )

    /**
     * "In the meantime" interim solutions
     */
    val IN_THE_MEANTIME = DetectionPattern(
        type = PlaceholderType.IN_THE_MEANTIME,
        regex = """(?i)//.*in the meantime|/\*.*in the meantime.*\*/|\bin the meantime\b""",
        description = "Interim solutions marked 'in the meantime'",
        severity = Severity.HIGH
    )

    /**
     * References to future real implementations
     */
    val REAL_IMPLEMENTATION = DetectionPattern(
        type = PlaceholderType.REAL_IMPLEMENTATION,
        regex = """(?i)//.*real implementation|/\*.*real implementation.*\*/|\bin a real implementation\b|real.*implementation.*needed""",
        description = "References to future real implementations",
        severity = Severity.CRITICAL
    )

    /**
     * Explicitly unimplemented functionality
     */
    val NOT_IMPLEMENTED = DetectionPattern(
        type = PlaceholderType.NOT_IMPLEMENTED,
        regex = """(?i)\bnot implemented\b|\bunimplemented\b|throw.*NotImplementedError|NotImplementedError\(\)|TODO\(\)|TODO\(".*"\)""",
        description = "Explicitly unimplemented functionality",
        severity = Severity.CRITICAL
    )

    /**
     * Kotlin-specific patterns for incomplete implementations
     */
    val KOTLIN_SPECIFIC = listOf(
        DetectionPattern(
            type = PlaceholderType.NOT_IMPLEMENTED,
            regex = """TODO\(\s*"[^"]*"\s*\)|TODO\(\s*\)|NotImplementedError\(\s*"[^"]*"\s*\)""",
            description = "Kotlin TODO() and NotImplementedError() calls",
            severity = Severity.CRITICAL
        ),
        DetectionPattern(
            type = PlaceholderType.STUB,
            regex = """return\s+TODO\(\)|return\s+NotImplementedError\(\)|return\s+null\s*//.*placeholder""",
            description = "Placeholder return statements",
            severity = Severity.HIGH
        ),
        DetectionPattern(
            type = PlaceholderType.PLACEHOLDER,
            regex = """@Suppress\(".*NotImplemented.*"\)|@Suppress\(".*TODO.*"\)""",
            description = "Suppressed implementation warnings",
            severity = Severity.MEDIUM
        )
    )

    /**
     * Materia library specific patterns for 3D graphics placeholders
     */
    val MATERIA_SPECIFIC = listOf(
        DetectionPattern(
            type = PlaceholderType.STUB,
            regex = """(?i)//.*WebGPU.*placeholder|//.*Vulkan.*placeholder|//.*platform.*specific.*stub""",
            description = "Platform-specific rendering placeholders",
            severity = Severity.HIGH
        ),
        DetectionPattern(
            type = PlaceholderType.REAL_IMPLEMENTATION,
            regex = """(?i)//.*physics.*engine.*needed|//.*animation.*system.*incomplete|//.*shader.*compilation.*missing""",
            description = "Core 3D feature implementations needed",
            severity = Severity.CRITICAL
        ),
        DetectionPattern(
            type = PlaceholderType.WORKAROUND,
            regex = """(?i)//.*temporary.*buffer|//.*simplified.*rendering|//.*mock.*GPU.*state""",
            description = "Temporary 3D graphics workarounds",
            severity = Severity.HIGH
        )
    )

    /**
     * Performance-related placeholder patterns
     */
    val PERFORMANCE_PLACEHOLDERS = listOf(
        DetectionPattern(
            type = PlaceholderType.PLACEHOLDER,
            regex = """(?i)//.*optimize.*later|//.*performance.*todo|//.*slow.*implementation""",
            description = "Performance optimization placeholders",
            severity = Severity.MEDIUM
        ),
        DetectionPattern(
            type = PlaceholderType.WORKAROUND,
            regex = """(?i)//.*memory.*leak|//.*inefficient.*but.*works|//.*quick.*hack""",
            description = "Performance workarounds needing fixes",
            severity = Severity.HIGH
        )
    )

    /**
     * Test-related placeholder patterns
     */
    val TEST_PLACEHOLDERS = listOf(
        DetectionPattern(
            type = PlaceholderType.TODO,
            regex = """(?i)//.*test.*needed|//.*add.*test.*case|//.*untested.*code""",
            description = "Missing test implementations",
            severity = Severity.HIGH
        ),
        DetectionPattern(
            type = PlaceholderType.STUB,
            regex = """(?i)@Test.*\{\s*//.*implement.*test|@Test.*\{\s*TODO\(""",
            description = "Stub test methods",
            severity = Severity.HIGH
        )
    )

    /**
     * All standard detection patterns combined
     */
    val ALL = listOf(
        TODO, FIXME, STUB, PLACEHOLDER, WORKAROUND,
        FOR_NOW, IN_THE_MEANTIME, REAL_IMPLEMENTATION, NOT_IMPLEMENTED
    ) + KOTLIN_SPECIFIC + MATERIA_SPECIFIC + PERFORMANCE_PLACEHOLDERS + TEST_PLACEHOLDERS

    /**
     * Critical patterns that must be addressed before production
     */
    val CRITICAL_PATTERNS = ALL.filter { it.severity == Severity.CRITICAL }

    /**
     * Constitutional violation patterns (production readiness blockers)
     */
    val CONSTITUTIONAL_VIOLATIONS = listOf(
        FIXME, REAL_IMPLEMENTATION, NOT_IMPLEMENTED
    ) + KOTLIN_SPECIFIC.filter { it.severity == Severity.CRITICAL }

    /**
     * Platform-specific patterns that may be legitimate
     */
    val PLATFORM_SPECIFIC_ALLOWABLE = listOf(
        DetectionPattern(
            type = PlaceholderType.PLACEHOLDER,
            regex = """(?i)//.*platform.*specific.*implementation|//.*expect.*actual.*pattern""",
            description = "Legitimate platform-specific implementations",
            severity = Severity.LOW
        )
    )

    /**
     * Get patterns by severity level
     */
    fun getPatternsBySeverity(severity: Severity): List<DetectionPattern> {
        return ALL.filter { it.severity == severity }
    }

    /**
     * Get patterns by placeholder type
     */
    fun getPatternsByType(type: PlaceholderType): List<DetectionPattern> {
        return ALL.filter { it.type == type }
    }

    /**
     * Get constitutional compliance patterns
     */
    fun getConstitutionalPatterns(): List<DetectionPattern> {
        return CONSTITUTIONAL_VIOLATIONS
    }

    /**
     * Get performance-related patterns
     */
    fun getPerformancePatterns(): List<DetectionPattern> {
        return PERFORMANCE_PLACEHOLDERS
    }

    /**
     * Get test-related patterns
     */
    fun getTestPatterns(): List<DetectionPattern> {
        return TEST_PLACEHOLDERS
    }
}