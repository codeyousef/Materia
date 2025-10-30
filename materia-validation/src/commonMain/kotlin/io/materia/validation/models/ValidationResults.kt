package io.materia.validation.models

import io.materia.validation.api.ValidationResult
import kotlinx.serialization.Serializable

/**
 * Result from compilation validation.
 *
 * @property status The overall compilation status
 * @property score Compilation success rate (0.0 to 1.0)
 * @property message Summary of compilation results
 * @property platformResults Platform-specific compilation statuses
 * @property errors List of compilation errors encountered
 * @property warnings List of compilation warnings
 * @property compilationTime Time taken to compile in milliseconds
 */
@Serializable
data class CompilationResult(
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String,
    val platformResults: Map<String, PlatformCompilationStatus>,
    val errors: List<CompilationError> = emptyList(),
    val warnings: List<CompilationWarning> = emptyList(),
    val compilationTime: Long = 0L
) : ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "platformCount" to platformResults.size,
            "successfulPlatforms" to platformResults.count { it.value.success },
            "errorCount" to errors.size,
            "warningCount" to warnings.size,
            "compilationTimeMs" to compilationTime
        )
}

/**
 * Platform-specific compilation status.
 */
@Serializable
data class PlatformCompilationStatus(
    val platform: String,
    val success: Boolean,
    val errorMessages: List<String> = emptyList(),
    val warningMessages: List<String> = emptyList(),
    val duration: Long = 0L
)

/**
 * Compilation error details.
 */
@Serializable
data class CompilationError(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String = "ERROR"
)

/**
 * Compilation warning details.
 */
@Serializable
data class CompilationWarning(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: String = "WARNING"
)

/**
 * Result from test coverage validation.
 *
 * @property status The overall test status
 * @property score Test success rate (0.0 to 1.0)
 * @property message Summary of test results
 * @property totalTests Total number of tests
 * @property passedTests Number of tests that passed
 * @property failedTests Number of tests that failed
 * @property skippedTests Number of tests that were skipped
 * @property lineCoverage Line coverage percentage (0.0 to 100.0)
 * @property branchCoverage Branch coverage percentage (0.0 to 100.0)
 * @property executionTime Time taken to run tests in milliseconds
 */
@Serializable
data class TestResults(
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val skippedTests: Int,
    val lineCoverage: Float,
    val branchCoverage: Float,
    val executionTime: Long,
    val failedTestDetails: List<FailedTest> = emptyList()
) : ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "totalTests" to totalTests,
            "passedTests" to passedTests,
            "failedTests" to failedTests,
            "skippedTests" to skippedTests,
            "successRate" to if (totalTests > 0) (passedTests.toFloat() / totalTests * 100) else 0f,
            "lineCoverage" to lineCoverage,
            "branchCoverage" to branchCoverage,
            "executionTimeMs" to executionTime
        )
}

/**
 * Details about a failed test.
 */
@Serializable
data class FailedTest(
    val testName: String,
    val className: String,
    val errorMessage: String,
    val stackTrace: String? = null
)

/**
 * Result from performance validation.
 *
 * @property status The overall performance status
 * @property score Performance score (0.0 to 1.0)
 * @property message Summary of performance results
 * @property averageFps Average frames per second
 * @property minFps Minimum FPS observed
 * @property maxFps Maximum FPS observed
 * @property memoryUsageMb Current memory usage in MB
 * @property peakMemoryMb Peak memory usage in MB
 * @property frameTimeMs Average frame time in milliseconds
 * @property gcPauseTimeMs Total GC pause time in milliseconds
 */
@Serializable
data class PerformanceMetrics(
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String,
    val averageFps: Float,
    val minFps: Float,
    val maxFps: Float,
    val memoryUsageMb: Float,
    val peakMemoryMb: Float,
    val frameTimeMs: Float,
    val gcPauseTimeMs: Long,
    val benchmarkResults: List<BenchmarkResult> = emptyList()
) : ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "averageFps" to averageFps,
            "minFps" to minFps,
            "maxFps" to maxFps,
            "memoryUsageMb" to memoryUsageMb,
            "peakMemoryMb" to peakMemoryMb,
            "frameTimeMs" to frameTimeMs,
            "gcPauseTimeMs" to gcPauseTimeMs,
            "meets60FpsTarget" to (minFps >= 60f)
        )
}

/**
 * Individual benchmark result.
 */
@Serializable
data class BenchmarkResult(
    val name: String,
    val value: Float,
    val unit: String,
    val baseline: Float? = null,
    val percentageChange: Float? = null
)

/**
 * Result from security validation.
 *
 * @property status The overall security status
 * @property score Security score (0.0 to 1.0)
 * @property message Summary of security results
 * @property vulnerabilities List of discovered vulnerabilities
 * @property securityPatternViolations List of code pattern violations
 * @property dependencyVulnerabilities Vulnerable dependencies found
 */
@Serializable
data class SecurityValidationResult(
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String,
    val vulnerabilities: List<SecurityVulnerability>,
    val securityPatternViolations: List<SecurityPatternViolation>,
    val dependencyVulnerabilities: List<DependencyVulnerability>,
    val codeIssues: List<SecurityCodeIssue>? = null,
    val skipped: Boolean = false
) : ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "totalVulnerabilities" to vulnerabilities.size,
            "criticalVulnerabilities" to vulnerabilities.count { it.severity == "CRITICAL" },
            "highVulnerabilities" to vulnerabilities.count { it.severity == "HIGH" },
            "patternViolations" to securityPatternViolations.size,
            "vulnerableDependencies" to dependencyVulnerabilities.size,
            "codeIssuesCount" to (codeIssues?.size ?: 0),
            "skipped" to skipped
        )
}

/**
 * Security code issue (from code scanning).
 */
@Serializable
data class SecurityCodeIssue(
    val type: String,
    val location: String,
    val message: String,
    val severity: String = "MEDIUM"
)

/**
 * Security vulnerability details.
 */
@Serializable
data class SecurityVulnerability(
    val id: String,
    val title: String,
    val description: String,
    val severity: String,
    val cvssScore: Float? = null,
    val affectedFile: String? = null,
    val remediation: String? = null,
    val cve: String? = null,
    val dependency: String? = null
)

/**
 * Security pattern violation.
 */
@Serializable
data class SecurityPatternViolation(
    val pattern: String,
    val file: String,
    val line: Int,
    val description: String,
    val recommendation: String
)

/**
 * Vulnerable dependency information.
 */
@Serializable
data class DependencyVulnerability(
    val dependency: String,
    val currentVersion: String,
    val vulnerabilities: List<String>,
    val safeVersion: String? = null,
    val severity: String
)

/**
 * Result from constitutional (code quality) validation.
 *
 * @property status The overall constitutional compliance status
 * @property score Compliance score (0.0 to 1.0)
 * @property message Summary of compliance results
 * @property tddCompliance Whether TDD practices are followed
 * @property placeholderCodeCount Number of placeholder code instances
 * @property expectActualPairs Expect/actual implementation pairs status
 * @property codeSmells List of detected code smells
 */
@Serializable
data class ConstitutionalCompliance(
    override val status: ValidationStatus,
    override val score: Float,
    override val message: String,
    val tddCompliance: TddComplianceResult,
    val placeholderCodeCount: Int,
    val expectActualPairs: ExpectActualValidation,
    val codeSmells: List<CodeSmell>,
    val placeholderLocations: List<PlaceholderLocation> = emptyList()
) : ValidationResult {
    override val details: Map<String, Any>
        get() = mapOf(
            "tddCompliant" to tddCompliance.isCompliant,
            "placeholderCount" to placeholderCodeCount,
            "unmatchedExpects" to expectActualPairs.unmatchedExpects,
            "codeSmellCount" to codeSmells.size,
            "testsBeforeCode" to tddCompliance.testsWrittenFirst,
            "testCoverage" to tddCompliance.testCoveragePercent
        )
}

/**
 * TDD compliance information.
 */
@Serializable
data class TddComplianceResult(
    val isCompliant: Boolean,
    val testsWrittenFirst: Boolean,
    val testCoveragePercent: Float,
    val untestableFunctions: List<String> = emptyList()
)

/**
 * Expect/actual validation result.
 */
@Serializable
data class ExpectActualValidation(
    val totalExpects: Int,
    val matchedPairs: Int,
    val unmatchedExpects: Int,
    val missingActuals: List<String> = emptyList(),
    val platformCoverage: Map<String, Boolean> = emptyMap()
)

/**
 * Code smell detection.
 */
@Serializable
data class CodeSmell(
    val type: String,
    val file: String,
    val line: Int,
    val description: String,
    val severity: String
)

/**
 * Location of placeholder code.
 */
@Serializable
data class PlaceholderLocation(
    val file: String,
    val line: Int,
    val type: String, // Placeholder marker identifier (e.g., pending task, fix-me note)
    val content: String
)
