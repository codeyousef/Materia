package io.materia.verification

import io.materia.verification.model.*

/**
 * Contract interface for detecting incomplete implementations in source code
 * Identifies TODO, FIXME, stub, placeholder, and workaround patterns
 */
interface PlaceholderDetector {

    /**
     * Detects all placeholder patterns in a source file
     * @param filePath Path to the source file to analyze
     * @param fileContent Content of the file (optional, will read if not provided)
     * @return List of detected placeholder patterns
     */
    suspend fun detectPlaceholders(
        filePath: String,
        fileContent: String? = null
    ): DetectionResult<List<PlaceholderPattern>>

    /**
     * Scans multiple files for placeholder patterns
     * @param filePaths List of file paths to scan
     * @return Map of file paths to their placeholder patterns
     */
    suspend fun scanFiles(
        filePaths: List<String>
    ): DetectionResult<Map<String, List<PlaceholderPattern>>>

    /**
     * Configures detection patterns and rules
     * @param patterns Custom patterns to detect
     * @param exclusions Patterns to exclude from detection
     * @return Updated detector configuration
     */
    fun configureDetection(
        patterns: List<DetectionPattern>,
        exclusions: List<String> = emptyList()
    ): DetectorConfiguration

    /**
     * Validates that a file is free of placeholders
     * @param filePath Path to validate
     * @return Validation result indicating if file is production-ready
     */
    suspend fun validateProductionReady(filePath: String): DetectionResult<ValidationStatus>

    /**
     * Categorizes placeholders by severity and impact
     * @param placeholders List of detected placeholders
     * @return Categorized and prioritized placeholders
     */
    fun categorizePlaceholders(
        placeholders: List<PlaceholderPattern>
    ): CategorizedPlaceholders

    /**
     * Estimates implementation effort for placeholders
     * @param placeholders List of placeholders to estimate
     * @param historicalData Optional historical effort data
     * @return Effort estimates for each placeholder
     */
    suspend fun estimateImplementationEffort(
        placeholders: List<PlaceholderPattern>,
        historicalData: Map<PlaceholderType, io.materia.verification.model.Duration>? = null
    ): DetectionResult<Map<PlaceholderPattern, io.materia.verification.model.Duration>>
}

/**
 * Result wrapper for detection operations
 */
sealed class DetectionResult<out T> {
    data class Success<T>(val data: T) : DetectionResult<T>()
    data class Failure(val error: DetectionError) : DetectionResult<Nothing>()
}

/**
 * Error types for detection operations
 */
sealed class DetectionError(open val message: String, open val cause: Throwable? = null) {
    data class FileReadError(override val message: String, override val cause: Throwable?) :
        DetectionError(message, cause)

    data class PatternError(override val message: String, val pattern: String) :
        DetectionError(message)

    data class ConfigurationError(override val message: String) : DetectionError(message)
}

/**
 * Detection pattern configuration
 */
data class DetectionPattern(
    val type: PlaceholderType,
    val regex: String,
    val description: String,
    val severity: Severity,
    val contextLines: Int = 2,
    val caseSensitive: Boolean = false
)

/**
 * Detector configuration and settings
 */
data class DetectorConfiguration(
    val patterns: List<DetectionPattern>,
    val exclusions: List<String>,
    val contextLines: Int,
    val enableMultilineDetection: Boolean,
    val strictMode: Boolean
)

/**
 * Validation status for production readiness
 */
data class ValidationStatus(
    val isProductionReady: Boolean,
    val placeholderCount: Int,
    val violations: List<ProductionViolation>,
    val recommendations: List<String>
)

/**
 * Production readiness violation
 */
data class ProductionViolation(
    val type: PlaceholderType,
    val location: CodeLocation,
    val message: String,
    val severity: Severity,
    val constitutionalImpact: Boolean
)

/**
 * Categorized placeholders by priority and type
 */
data class CategorizedPlaceholders(
    val critical: List<PlaceholderPattern>,
    val high: List<PlaceholderPattern>,
    val medium: List<PlaceholderPattern>,
    val low: List<PlaceholderPattern>,
    val byType: Map<PlaceholderType, List<PlaceholderPattern>>,
    val byModule: Map<ModuleType, List<PlaceholderPattern>>
)

/**
 * Built-in detection patterns for common placeholder types
 */
object StandardDetectionPatterns {

    val TODO = DetectionPattern(
        type = PlaceholderType.TODO,
        regex = "(?i)//\\s*todo\\b.*|/\\*\\s*todo\\b.*?\\*/",
        description = "TODO comments indicating incomplete implementation",
        severity = Severity.HIGH
    )

    val FIXME = DetectionPattern(
        type = PlaceholderType.FIXME,
        regex = "(?i)//\\s*fixme\\b.*|/\\*\\s*fixme\\b.*?\\*/",
        description = "FIXME comments indicating known issues",
        severity = Severity.CRITICAL
    )

    val STUB = DetectionPattern(
        type = PlaceholderType.STUB,
        regex = "(?i)\\bstub\\b.*implementation|placeholder.*implementation",
        description = "Stub implementations that need completion",
        severity = Severity.HIGH
    )

    val PLACEHOLDER = DetectionPattern(
        type = PlaceholderType.PLACEHOLDER,
        regex = "(?i)//.*placeholder|/\\*.*placeholder.*\\*/|\\bplaceholder\\b",
        description = "Placeholder code or comments",
        severity = Severity.MEDIUM
    )

    val WORKAROUND = DetectionPattern(
        type = PlaceholderType.WORKAROUND,
        regex = "(?i)//.*workaround|/\\*.*workaround.*\\*/|\\bworkaround\\b",
        description = "Temporary workaround solutions",
        severity = Severity.HIGH
    )

    val FOR_NOW = DetectionPattern(
        type = PlaceholderType.FOR_NOW,
        regex = "(?i)//.*for now|/\\*.*for now.*\\*/|\\bfor now\\b",
        description = "Explicitly temporary code marked 'for now'",
        severity = Severity.HIGH
    )

    val IN_THE_MEANTIME = DetectionPattern(
        type = PlaceholderType.IN_THE_MEANTIME,
        regex = "(?i)//.*in the meantime|/\\*.*in the meantime.*\\*/|\\bin the meantime\\b",
        description = "Interim solutions marked 'in the meantime'",
        severity = Severity.HIGH
    )

    val REAL_IMPLEMENTATION = DetectionPattern(
        type = PlaceholderType.REAL_IMPLEMENTATION,
        regex = "(?i)//.*real implementation|/\\*.*real implementation.*\\*/|\\bin a real implementation\\b",
        description = "References to future real implementations",
        severity = Severity.CRITICAL
    )

    val NOT_IMPLEMENTED = DetectionPattern(
        type = PlaceholderType.NOT_IMPLEMENTED,
        regex = "(?i)\\bnot implemented\\b|\\bunimplemented\\b|throw.*NotImplementedError",
        description = "Explicitly unimplemented functionality",
        severity = Severity.CRITICAL
    )

    /**
     * All standard detection patterns
     */
    val ALL = listOf(
        TODO, FIXME, STUB, PLACEHOLDER, WORKAROUND,
        FOR_NOW, IN_THE_MEANTIME, REAL_IMPLEMENTATION, NOT_IMPLEMENTED
    )
}