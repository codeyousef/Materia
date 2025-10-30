package io.materia.verification.model

/**
 * Represents a source code file and its implementation status
 */
@OptIn(kotlin.time.ExperimentalTime::class)
data class ImplementationArtifact(
    val filePath: String,
    val moduleType: ModuleType,
    val implementationStatus: ImplementationStatus,
    val placeholderCount: Int,
    val placeholderTypes: List<PlaceholderType>,
    val priority: Priority,
    val lastModified: Long, // Changed from kotlinx.datetime.Instant to Long (epoch milliseconds)
    val testCoverage: Float,
    val constitutionalCompliance: Boolean
) {
    init {
        require(placeholderCount >= 0) { "Placeholder count must be non-negative" }
        require(testCoverage in 0f..1f) { "Test coverage must be between 0.0 and 1.0" }
        require(!constitutionalCompliance || placeholderCount == 0) {
            "Constitutional compliance requires zero placeholders in production paths"
        }
    }
}

/**
 * Represents a specific incomplete implementation found in code
 */
data class PlaceholderPattern(
    val id: String,
    val type: PlaceholderType,
    val location: CodeLocation,
    val content: String,
    val context: String,
    val severity: Severity,
    val estimatedEffort: Duration,
    val dependencies: List<String>
) {
    init {
        require(content.isNotBlank()) { "Content must not be empty" }
        require(estimatedEffort.amount > 0) { "Estimated effort must be positive" }
    }
}

/**
 * Code location information
 */
data class CodeLocation(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val length: Int
) {
    fun toDisplayString(): String = "$filePath:$lineNumber:$columnNumber"
}

/**
 * Duration representation for time estimates
 */
data class Duration(
    val amount: Long,
    val unit: TimeUnit
) {
    enum class TimeUnit {
        MINUTES, HOURS, DAYS, WEEKS
    }

    fun toHours(): Double = when (unit) {
        TimeUnit.MINUTES -> amount / 60.0
        TimeUnit.HOURS -> amount.toDouble()
        TimeUnit.DAYS -> amount * 24.0
        TimeUnit.WEEKS -> amount * 24.0 * 7.0
    }
}

/**
 * Categorizes implementation areas by functional domain
 */
enum class ModuleType {
    CORE_MATH,
    SCENE_GRAPH,
    RENDERER,
    MATERIAL,
    TEXTURE,
    LIGHTING,
    ANIMATION,
    PHYSICS,
    GEOMETRY,
    CONTROLS,
    XR_AR,
    OPTIMIZATION,
    PROFILING
}

/**
 * Categorizes types of incomplete implementations
 */
enum class PlaceholderType {
    TODO,
    FIXME,
    STUB,
    PLACEHOLDER,
    WORKAROUND,
    FOR_NOW,
    IN_THE_MEANTIME,
    REAL_IMPLEMENTATION,
    NOT_IMPLEMENTED
}

/**
 * Implementation priority based on impact and dependencies
 */
enum class Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Current state of implementation artifact
 */
enum class ImplementationStatus {
    INCOMPLETE,
    IN_PROGRESS,
    COMPLETE,
    VERIFIED
}

/**
 * Severity levels for issues and violations
 */
enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Represents criteria that must be met for production readiness
 */
data class QualityGate(
    val name: String,
    val description: String,
    val criteria: List<QualityCriteria>,
    val required: Boolean,
    val automatable: Boolean,
    val constitutionalRequirement: Boolean
) {
    init {
        require(!constitutionalRequirement || required) {
            "Constitutional requirement gates cannot be marked as optional"
        }
    }
}

/**
 * Specific quality criteria for gates
 */
data class QualityCriteria(
    val name: String,
    val description: String,
    val threshold: String,
    val measurement: String
)

/**
 * Represents test coverage for implementation artifacts
 */
data class TestArtifact(
    val artifactPath: String,
    val testPath: String,
    val testType: TestType,
    val coverage: Float,
    val tddCompliant: Boolean,
    val passRate: Float
) {
    init {
        require(coverage in 0f..1f) { "Coverage must be between 0.0 and 1.0" }
        require(passRate in 0f..1f) { "Pass rate must be between 0.0 and 1.0" }
    }
}

/**
 * Categories of tests for different validation levels
 */
enum class TestType {
    UNIT,
    INTEGRATION,
    PLATFORM,
    PERFORMANCE,
    VISUAL,
    CONTRACT
}