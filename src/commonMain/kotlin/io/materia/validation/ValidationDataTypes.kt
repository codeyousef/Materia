package io.materia.validation

// Common data types for validation system

// Platform enumeration
enum class Platform(val sourceDir: String) {
    JVM("jvmMain"),
    JS("jsMain"),
    ANDROID("androidMain"),
    IOS("iosMain"),
    NATIVE("nativeMain"),
    UNSUPPORTED("unsupported")
}

// Validation Configuration
data class ValidationConfiguration(
    val strictMode: Boolean = true,
    val enablePerformanceTests: Boolean = true,
    val requireAllPlatforms: Boolean = true,
    val allowPlaceholders: Boolean = false,
    val incrementalMode: Boolean = false,
    val customThresholds: Map<String, Float> = emptyMap()
) {
    companion object {
        fun strict() = ValidationConfiguration(
            strictMode = true,
            requireAllPlatforms = true,
            allowPlaceholders = false
        )

        fun permissive() = ValidationConfiguration(
            strictMode = false,
            requireAllPlatforms = false,
            allowPlaceholders = true
        )

        fun incremental() = ValidationConfiguration(
            incrementalMode = true
        )
    }
}

// Placeholder Detection Data Types
data class ScanResult(
    val scanTimestamp: Long,
    val scannedPaths: List<String>,
    val placeholders: List<PlaceholderInstance>,
    val totalFilesScanned: Int,
    val scanDurationMs: Long
)

data class PlaceholderInstance(
    val filePath: String,
    val lineNumber: Int,
    val columnNumber: Int,
    val pattern: String,
    val context: String,
    val type: PlaceholderType,
    val criticality: CriticalityLevel,
    val module: String,
    val platform: String?
)

enum class PlaceholderType {
    TODO, FIXME, STUB, PLACEHOLDER, TEMPORARY, MOCK
}

enum class CriticalityLevel {
    CRITICAL, HIGH, MEDIUM, LOW
}

enum class EffortLevel {
    TRIVIAL, SMALL, MEDIUM, LARGE
}

// Implementation Validation Data Types
data class GapAnalysisResult(
    val gaps: List<ImplementationGap>,
    val analysisTimestamp: Long,
    val totalExpectDeclarations: Int,
    val platformsCovered: List<Platform>,
    val modulesCovered: List<String>
)

data class ImplementationGap(
    val filePath: String,
    val expectedSignature: String,
    val platform: Platform,
    val module: String,
    val lineNumber: Int,
    val gapType: GapType,
    val severity: GapSeverity,
    val context: String
)

enum class GapType {
    MISSING_ACTUAL,
    INCOMPLETE_IMPLEMENTATION,
    STUB_IMPLEMENTATION,
    PLATFORM_SPECIFIC_MISSING
}

enum class GapSeverity {
    CRITICAL,    // Core functionality missing
    HIGH,        // Important feature missing
    MEDIUM,      // Nice-to-have feature missing
    LOW          // Optional or minor feature missing
}

enum class ImplementationStatus {
    COMPLETE,         // Fully implemented and functional
    INCOMPLETE,       // Contains TODOs or stubs
    POOR_QUALITY,     // Implemented but with quality issues
    MISSING,          // No implementation found
    PLATFORM_SPECIFIC // Requires platform-specific analysis
}

// Renderer Validation Data Types
data class RendererConfiguration(
    val enableDebugging: Boolean = false,
    val vsyncEnabled: Boolean = true,
    val msaaSamples: Int = 4,
    val maxTextureSize: Int = 2048,
    val preferHighPerformance: Boolean = false,
    val customSettings: Map<String, Any> = emptyMap()
) {
    companion object {
        fun default() = RendererConfiguration()
    }
}

sealed class RendererResult<out T> {
    data class Success<T>(val value: T) : RendererResult<T>()
    data class Failure(val exception: Throwable) : RendererResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun exceptionOrNull(): Throwable? = when (this) {
        is Success -> null
        is Failure -> exception
    }
}

data class RendererComponent(
    val renderer: Renderer?,
    val isProductionReady: Boolean?,
    val performanceScore: Float,
    val featureCompleteness: Float,
    val validationResults: Map<String, Boolean>,
    val issues: List<String>,
    val recommendations: List<String>,
    val constitutionalCompliance: Map<String, Boolean>
)

data class RendererValidationSuite(
    val performanceTests: Boolean = true,
    val featureTests: Boolean = true,
    val compatibilityTests: Boolean = true,
    val stressTests: Boolean = false,
    val constitutionalTests: Boolean = true
) {
    companion object {
        fun performance() = RendererValidationSuite(
            performanceTests = true,
            featureTests = false,
            compatibilityTests = false,
            stressTests = true
        )

        fun constitutional() = RendererValidationSuite(
            performanceTests = true,
            featureTests = true,
            compatibilityTests = true,
            constitutionalTests = true
        )
    }
}

data class PerformanceData(
    val frameRate: Float,
    val frameTime: Float,
    val trianglesPerSecond: Long,
    val gpuMemoryUsage: Long,
    val cpuMemoryUsage: Long,
    val drawCalls: Int,
    val shaderCompileTime: Float,
    val isProductionReady: Boolean
)

data class RendererAuditResult(
    val rendererComponents: Map<Platform, RendererComponent>,
    val overallRendererScore: Float,
    val missingPlatforms: List<Platform>,
    val performanceIssues: List<String>
)

// Test Execution Data Types
data class TestExecutionResult(
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val skippedTests: Int,
    val executionTimeMs: Long,
    val codeCoverage: Float,
    val unitTestResults: TestCategoryResult,
    val integrationTestResults: TestCategoryResult,
    val performanceTestResults: TestCategoryResult,
    val testFailures: List<TestFailure>
)

data class TestCategoryResult(
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val coverage: Float
)

data class TestFailure(
    val testName: String,
    val errorMessage: String,
    val stackTrace: String,
    val category: String
)

// Example Validation Data Types
data class ExampleValidationResult(
    val totalExamples: Int,
    val exampleResults: Map<String, ExampleResult>,
    val overallExampleScore: Float,
    val compilationFailures: List<String>,
    val executionFailures: List<String>
)

data class ExampleResult(
    val compilationStatus: CompilationStatus,
    val executionStatus: ExecutionStatus,
    val performanceMetrics: Map<String, Float>
)

enum class CompilationStatus {
    SUCCESS,
    FAILED,
    WARNINGS
}

enum class ExecutionStatus {
    SUCCESS,
    FAILED,
    TIMEOUT,
    NOT_EXECUTED
}

// Performance Validation Data Types
data class PerformanceValidationResult(
    val frameRateResults: Map<Platform, Float>,
    val memorySizeResults: Map<String, Long>,
    val meetsFrameRateRequirement: Boolean?,
    val meetsSizeRequirement: Boolean?,
    val averageFrameRate: Float,
    val librarySize: Long,
    val performanceIssues: List<String>
)

// Overall Validation Data Types
data class ValidationResult(
    val overallStatus: ValidationStatus,
    val overallScore: Float,
    val validationTimestamp: Long,
    val scanDurationMs: Long,
    val placeholderScan: ScanResult,
    val implementationGaps: GapAnalysisResult,
    val rendererAudit: RendererAuditResult,
    val testResults: TestExecutionResult,
    val exampleValidation: ExampleValidationResult,
    val performanceValidation: PerformanceValidationResult,
    val componentScores: Map<String, Float>,
    val criticalIssues: List<String>,
    val ignoredIssues: List<String>,
    val incrementalUpdates: List<String>
)

enum class ValidationStatus {
    PASSED,     // All validations passed
    WARNING,    // Minor issues found
    FAILED,     // Critical issues found
    INCOMPLETE  // Validation could not complete
}

// Compliance and Reporting Data Types
data class ComplianceResult(
    val overallCompliance: Boolean,
    val complianceScore: Float,
    val constitutionalRequirements: Map<String, Boolean>,
    val nonCompliantAreas: List<String>,
    val recommendations: List<String>
)

data class ProductionReadinessReport(
    val executiveSummary: String,
    val overallScore: Float,
    val detailedFindings: Map<String, String>,
    val recommendations: List<String>,
    val constitutionalCompliance: Map<String, Boolean>,
    val componentBreakdown: Map<String, Float>,
    val estimatedEffort: Map<String, String>,
    val readinessTimeline: String?
)

// Basic interfaces that need to be defined
interface Renderer {
    val platform: Platform
    val configuration: RendererConfiguration
    val isInitialized: Boolean
    val isDebuggingEnabled: Boolean
    val isVsyncEnabled: Boolean

    fun beginFrame()
    fun endFrame()
    fun dispose()
}

interface Scene {
    val triangleCount: Int
    val meshCount: Int
    val lightCount: Int
}

// Exception classes
class UnsupportedPlatformException(message: String) : Exception(message)
class RendererDisposedException(message: String = "Renderer has been disposed") : Exception(message)