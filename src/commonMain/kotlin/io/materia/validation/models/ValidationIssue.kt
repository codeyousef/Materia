package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * Represents a validation issue found during production readiness checking.
 *
 * Issues can range from minor code style problems to critical implementation gaps,
 * with optional auto-fix capabilities and improvement suggestions.
 */
@Serializable
data class ValidationIssue(
    /**
     * Type of validation issue.
     */
    val type: IssueType,

    /**
     * Location in code where issue was found.
     */
    val location: CodeLocation,

    /**
     * Description of the issue.
     */
    val message: String,

    /**
     * Optional suggestion for fixing the issue.
     */
    val suggestion: String? = null,

    /**
     * Whether this issue can be automatically fixed.
     */
    val canAutoFix: Boolean = false
) {

    /**
     * Checks if this is a critical issue that blocks production.
     * @return true if issue type is CRITICAL_BUG, MISSING_IMPLEMENTATION, or SECURITY_VULNERABILITY
     */
    fun isCritical(): Boolean {
        return type in setOf(
            IssueType.CRITICAL_BUG,
            IssueType.MISSING_IMPLEMENTATION,
            IssueType.SECURITY_VULNERABILITY
        )
    }

    /**
     * Checks if this is a performance-related issue.
     * @return true if issue type relates to performance
     */
    fun isPerformanceRelated(): Boolean {
        return type in setOf(
            IssueType.PERFORMANCE_ISSUE,
            IssueType.MEMORY_LEAK,
            IssueType.INEFFICIENT_CODE
        )
    }

    /**
     * Gets the severity level of this issue.
     * @return severity from 1 (low) to 5 (critical)
     */
    fun getSeverity(): Int {
        return when (type) {
            IssueType.CRITICAL_BUG -> 5
            IssueType.SECURITY_VULNERABILITY -> 5
            IssueType.MISSING_IMPLEMENTATION -> 5
            IssueType.MEMORY_LEAK -> 4
            IssueType.RUNTIME_ERROR -> 4
            IssueType.COMPILATION_ERROR -> 4
            IssueType.PERFORMANCE_ISSUE -> 3
            IssueType.MISSING_TEST -> 3
            IssueType.TYPE_SAFETY_ISSUE -> 3
            IssueType.DEPRECATED_API -> 2
            IssueType.INEFFICIENT_CODE -> 2
            IssueType.DOCUMENTATION_MISSING -> 2
            IssueType.CODE_SMELL -> 1
            IssueType.STYLE_VIOLATION -> 1
        }
    }

    /**
     * Gets a formatted description including location and suggestion.
     * @return complete issue description
     */
    fun getFullDescription(): String {
        val locationStr = location.toString()
        val suggestionStr = suggestion?.let { "\n  Suggestion: $it" } ?: ""
        val autoFixStr = if (canAutoFix) " [Auto-fixable]" else ""

        return "$locationStr: $message$autoFixStr$suggestionStr"
    }

    /**
     * Creates an auto-fix command if applicable.
     * @return fix command string, or null if not auto-fixable
     */
    fun getAutoFixCommand(): String? {
        if (!canAutoFix) return null

        return when (type) {
            IssueType.STYLE_VIOLATION -> "ktlintFormat ${location.file}"
            IssueType.DEPRECATED_API -> "refactor --update-deprecated ${location.file}"
            IssueType.DOCUMENTATION_MISSING -> "dokka --generate ${location.file}"
            else -> null
        }
    }
}

/**
 * Location in source code where an issue was found.
 */
@Serializable
data class CodeLocation(
    /**
     * File path relative to project root.
     */
    val file: String,

    /**
     * Line number where issue occurs (1-indexed), null if not applicable.
     */
    val line: Int? = null,

    /**
     * Column number where issue starts (1-indexed), null if not applicable.
     */
    val column: Int? = null
) {

    /**
     * Gets a formatted location string.
     * @return location in format "file:line:column" or just "file" if no line/column
     */
    override fun toString(): String {
        return when {
            line != null && column != null -> "$file:$line:$column"
            line != null -> "$file:$line"
            else -> file
        }
    }

    /**
     * Gets the module name from the file path.
     * @return module name extracted from path, or "unknown" if cannot determine
     */
    fun getModuleName(): String {
        val parts = file.split("/")

        // Look for module pattern like "materia-core", "materia-renderer"
        parts.forEach { part ->
            if (part.startsWith("materia-")) {
                return part
            }
        }

        // Try to extract from standard structure src/[platform]Main/kotlin/io/materia/[module]
        val materiaIndex = parts.indexOf("materia")
        if (materiaIndex >= 0 && materiaIndex < parts.size - 1) {
            return "materia-${parts[materiaIndex + 1]}"
        }

        return "unknown"
    }

    /**
     * Gets the platform from the file path if determinable.
     * @return platform name (commonMain, jvmMain, etc.) or null
     */
    fun getPlatform(): String? {
        val parts = file.split("/")

        // Look for platform patterns like "commonMain", "jvmMain", "jsMain"
        val platformPatterns = listOf(
            "commonMain", "jvmMain", "jsMain", "nativeMain",
            "androidMain", "iosMain", "linuxMain", "windowsMain", "macosMain"
        )

        parts.forEach { part ->
            if (part in platformPatterns) {
                return part
            }
        }

        return null
    }

    /**
     * Creates a clickable link for IDEs that support file:line format.
     * @return IDE-compatible link string
     */
    fun toIDELink(): String {
        return if (line != null) {
            "file://$file:$line"
        } else {
            "file://$file"
        }
    }
}

/**
 * Types of validation issues.
 */
@Serializable
enum class IssueType {
    /** Critical bug that causes crashes or data loss */
    CRITICAL_BUG,

    /** Security vulnerability that could be exploited */
    SECURITY_VULNERABILITY,

    /** Required functionality not implemented */
    MISSING_IMPLEMENTATION,

    /** Memory leak detected */
    MEMORY_LEAK,

    /** Runtime error or exception */
    RUNTIME_ERROR,

    /** Code fails to compile */
    COMPILATION_ERROR,

    /** Performance below constitutional requirements */
    PERFORMANCE_ISSUE,

    /** Missing test coverage */
    MISSING_TEST,

    /** Type safety violation (uses Any, unchecked casts, etc.) */
    TYPE_SAFETY_ISSUE,

    /** Uses deprecated API */
    DEPRECATED_API,

    /** Inefficient algorithm or data structure */
    INEFFICIENT_CODE,

    /** Missing or inadequate documentation */
    DOCUMENTATION_MISSING,

    /** Code smell (duplication, long methods, etc.) */
    CODE_SMELL,

    /** Code style guideline violation */
    STYLE_VIOLATION
}