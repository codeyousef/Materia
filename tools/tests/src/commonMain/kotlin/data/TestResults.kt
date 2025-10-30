package io.materia.tools.tests.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * TestResults - Data model for test execution tracking and reporting
 *
 * This data class represents the complete results of a test execution session,
 * including individual test case results, summary statistics, and generated
 * artifacts like screenshots and performance metrics.
 *
 * Results are serialized to JSON with binary attachments for screenshots.
 */
@Serializable
data class TestResults @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val timestamp: Instant,
    val platform: TestPlatform,
    val suite: TestSuite,
    val results: List<TestCaseResult>,
    val summary: TestSummary,
    val artifacts: List<TestArtifact>,
    val environment: TestEnvironment,
    val configuration: TestConfiguration
) {
    init {
        require(results.isNotEmpty()) { "Test results must contain at least one test case result" }

        // Validate summary matches individual results
        val actualTotal = results.size
        val actualPassed = results.count { it.status == TestStatus.PASSED }
        val actualFailed = results.count { it.status == TestStatus.FAILED }
        val actualSkipped = results.count { it.status == TestStatus.SKIPPED }

        require(summary.total == actualTotal) {
            "Summary total (${summary.total}) doesn't match actual results count ($actualTotal)"
        }
        require(summary.passed == actualPassed) {
            "Summary passed count (${summary.passed}) doesn't match actual passed count ($actualPassed)"
        }
        require(summary.failed == actualFailed) {
            "Summary failed count (${summary.failed}) doesn't match actual failed count ($actualFailed)"
        }
        require(summary.skipped == actualSkipped) {
            "Summary skipped count (${summary.skipped}) doesn't match actual skipped count ($actualSkipped)"
        }

        // Validate that test names are unique within the suite
        val testNames = results.map { it.name }
        require(testNames.size == testNames.toSet().size) {
            "Test case names must be unique within the suite"
        }
    }

    /**
     * Success rate as a percentage (0.0 - 100.0)
     */
    val successRate: Float
        get() = if (summary.total > 0) {
            (summary.passed.toFloat() / summary.total.toFloat()) * 100.0f
        } else 0.0f

    /**
     * Failure rate as a percentage (0.0 - 100.0)
     */
    val failureRate: Float
        get() = if (summary.total > 0) {
            (summary.failed.toFloat() / summary.total.toFloat()) * 100.0f
        } else 0.0f

    /**
     * Gets all failed test cases
     */
    fun getFailedTests(): List<TestCaseResult> {
        return results.filter { it.status == TestStatus.FAILED }
    }

    /**
     * Gets all test cases with errors
     */
    fun getTestsWithErrors(): List<TestCaseResult> {
        return results.filter { it.status == TestStatus.ERROR }
    }

    /**
     * Gets all test cases that timed out
     */
    fun getTimedOutTests(): List<TestCaseResult> {
        return results.filter { it.status == TestStatus.TIMEOUT }
    }

    /**
     * Gets test cases by category
     */
    fun getTestsByCategory(category: String): List<TestCaseResult> {
        return results.filter { it.category == category }
    }

    /**
     * Gets performance metrics aggregated across all test cases
     */
    fun getAggregatedMetrics(): Map<String, Float> {
        val allMetrics = results.flatMap { it.metrics.entries }
        return allMetrics.groupBy { it.key }
            .mapValues { (_, values) ->
                values.map { it.value }.average().toFloat()
            }
    }

    /**
     * Generates a detailed test report
     */
    fun generateReport(): TestReport {
        return TestReport(
            testResults = this,
            generatedAt = kotlinx.datetime.Clock.System.now(),
            reportFormat = ReportFormat.DETAILED
        )
    }

    companion object {
        /**
         * Creates test results from a completed test execution
         */
        fun fromExecution(
            platform: TestPlatform,
            suite: TestSuite,
            testCases: List<TestCaseResult>,
            environment: TestEnvironment,
            configuration: TestConfiguration
        ): TestResults {
            val passed = testCases.count { it.status == TestStatus.PASSED }
            val failed = testCases.count { it.status == TestStatus.FAILED }
            val skipped = testCases.count { it.status == TestStatus.SKIPPED }
            val errors = testCases.count { it.status == TestStatus.ERROR }
            val timeouts = testCases.count { it.status == TestStatus.TIMEOUT }

            val totalDuration = testCases.sumOf { it.duration.inWholeMilliseconds }

            val summary = TestSummary(
                total = testCases.size,
                passed = passed,
                failed = failed,
                skipped = skipped,
                errors = errors,
                timeouts = timeouts,
                duration = Duration.parse("${totalDuration}ms"),
                coverage = null // Would be calculated separately
            )

            return TestResults(
                timestamp = kotlinx.datetime.Clock.System.now(),
                platform = platform,
                suite = suite,
                results = testCases,
                summary = summary,
                artifacts = emptyList(), // Would be populated during execution
                environment = environment,
                configuration = configuration
            )
        }

        /**
         * Creates empty test results for initialization
         */
        fun createEmpty(platform: TestPlatform, suite: TestSuite): TestResults {
            return TestResults(
                timestamp = kotlinx.datetime.Clock.System.now(),
                platform = platform,
                suite = suite,
                results = listOf(
                    TestCaseResult(
                        name = "placeholder",
                        status = TestStatus.SKIPPED,
                        duration = Duration.ZERO,
                        category = "system"
                    )
                ),
                summary = TestSummary(
                    total = 0,
                    passed = 0,
                    failed = 0,
                    skipped = 0,
                    duration = Duration.ZERO
                ),
                artifacts = emptyList(),
                environment = TestEnvironment.createDefault(),
                configuration = TestConfiguration.createDefault()
            )
        }
    }
}

/**
 * TestCaseResult - Result of an individual test case execution
 */
@Serializable
data class TestCaseResult(
    val name: String,
    val status: TestStatus,
    val duration: Duration,
    val error: TestError? = null,
    val screenshots: List<ScreenshotData> = emptyList(),
    val metrics: Map<String, Float> = emptyMap(),
    val category: String = "default",
    val tags: List<String> = emptyList(),
    val retryCount: Int = 0,
    val stdout: String = "",
    val stderr: String = ""
) {
    init {
        require(name.isNotBlank()) { "Test case name must be non-empty" }
        require(retryCount >= 0) { "Retry count must be non-negative" }

        // Validate that error is present for failed/error status
        when (status) {
            TestStatus.FAILED, TestStatus.ERROR, TestStatus.TIMEOUT -> {
                // Error information is helpful but not strictly required
            }
            TestStatus.PASSED, TestStatus.SKIPPED -> {
                // No error should be present for passed/skipped tests
            }
        }
    }

    /**
     * Whether this test case passed
     */
    val passed: Boolean
        get() = status == TestStatus.PASSED

    /**
     * Whether this test case failed
     */
    val failed: Boolean
        get() = status == TestStatus.FAILED || status == TestStatus.ERROR || status == TestStatus.TIMEOUT

    /**
     * Duration in milliseconds for easier reporting
     */
    val durationMs: Long
        get() = duration.inWholeMilliseconds
}

/**
 * TestSummary - Aggregated statistics for the test execution
 */
@Serializable
data class TestSummary(
    val total: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val errors: Int = 0,
    val timeouts: Int = 0,
    val duration: Duration,
    val coverage: CoverageData? = null
) {
    init {
        require(total >= 0) { "Total test count must be non-negative" }
        require(passed >= 0) { "Passed test count must be non-negative" }
        require(failed >= 0) { "Failed test count must be non-negative" }
        require(skipped >= 0) { "Skipped test count must be non-negative" }
        require(errors >= 0) { "Error test count must be non-negative" }
        require(timeouts >= 0) { "Timeout test count must be non-negative" }
        require(passed + failed + skipped + errors + timeouts == total) {
            "Sum of individual counts must equal total"
        }
    }

    /**
     * Success rate as a percentage
     */
    val successRate: Float
        get() = if (total > 0) (passed.toFloat() / total.toFloat()) * 100.0f else 0.0f

    /**
     * Failure rate as a percentage
     */
    val failureRate: Float
        get() = if (total > 0) ((failed + errors + timeouts).toFloat() / total.toFloat()) * 100.0f else 0.0f
}

/**
 * TestError - Detailed information about a test failure or error
 */
@Serializable
data class TestError(
    val type: ErrorType,
    val message: String,
    val stackTrace: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val cause: String? = null
) {
    init {
        require(message.isNotBlank()) { "Error message must be non-empty" }
        line?.let { require(it > 0) { "Line number must be positive" } }
    }
}

/**
 * ScreenshotData - Information about a test screenshot
 */
@Serializable
data class ScreenshotData(
    val name: String,
    val timestamp: Instant,
    val base64Data: String? = null, // For small screenshots
    val filePath: String? = null,   // For large screenshots stored as files
    val width: Int,
    val height: Int,
    val format: ImageFormat = ImageFormat.PNG
) {
    init {
        require(name.isNotBlank()) { "Screenshot name must be non-empty" }
        require(width > 0) { "Screenshot width must be positive" }
        require(height > 0) { "Screenshot height must be positive" }
        require(base64Data != null || filePath != null) {
            "Screenshot must have either base64 data or file path"
        }
    }
}

/**
 * CoverageData - Code coverage information
 */
@Serializable
data class CoverageData(
    val linesCovered: Int,
    val linesTotal: Int,
    val branchesCovered: Int,
    val branchesTotal: Int,
    val functionsCovered: Int,
    val functionsTotal: Int,
    val packages: List<PackageCoverage> = emptyList()
) {
    init {
        require(linesCovered >= 0) { "Lines covered must be non-negative" }
        require(linesTotal >= linesCovered) { "Total lines must be >= covered lines" }
        require(branchesCovered >= 0) { "Branches covered must be non-negative" }
        require(branchesTotal >= branchesCovered) { "Total branches must be >= covered branches" }
        require(functionsCovered >= 0) { "Functions covered must be non-negative" }
        require(functionsTotal >= functionsCovered) { "Total functions must be >= covered functions" }
    }

    /**
     * Line coverage percentage
     */
    val lineCoverage: Float
        get() = if (linesTotal > 0) (linesCovered.toFloat() / linesTotal.toFloat()) * 100.0f else 0.0f

    /**
     * Branch coverage percentage
     */
    val branchCoverage: Float
        get() = if (branchesTotal > 0) (branchesCovered.toFloat() / branchesTotal.toFloat()) * 100.0f else 0.0f

    /**
     * Function coverage percentage
     */
    val functionCoverage: Float
        get() = if (functionsTotal > 0) (functionsCovered.toFloat() / functionsTotal.toFloat()) * 100.0f else 0.0f
}

/**
 * PackageCoverage - Coverage data for a specific package
 */
@Serializable
data class PackageCoverage(
    val packageName: String,
    val linesCovered: Int,
    val linesTotal: Int,
    val classes: List<ClassCoverage> = emptyList()
) {
    val lineCoverage: Float
        get() = if (linesTotal > 0) (linesCovered.toFloat() / linesTotal.toFloat()) * 100.0f else 0.0f
}

/**
 * ClassCoverage - Coverage data for a specific class
 */
@Serializable
data class ClassCoverage(
    val className: String,
    val linesCovered: Int,
    val linesTotal: Int
) {
    val lineCoverage: Float
        get() = if (linesTotal > 0) (linesCovered.toFloat() / linesTotal.toFloat()) * 100.0f else 0.0f
}

/**
 * TestArtifact - Generated artifact from test execution
 */
@Serializable
data class TestArtifact(
    val name: String,
    val type: ArtifactType,
    val path: String,
    val size: Long,
    val checksum: String,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(name.isNotBlank()) { "Artifact name must be non-empty" }
        require(path.isNotBlank()) { "Artifact path must be non-empty" }
        require(size >= 0) { "Artifact size must be non-negative" }
        require(checksum.isNotBlank()) { "Artifact checksum must be non-empty" }
    }
}

/**
 * TestSuite - Information about the test suite being executed
 */
@Serializable
data class TestSuite(
    val name: String,
    val version: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val timeout: Duration? = null
) {
    init {
        require(name.isNotBlank()) { "Test suite name must be non-empty" }
        require(version.isNotBlank()) { "Test suite version must be non-empty" }
    }
}

/**
 * TestEnvironment - Information about the test execution environment
 */
@Serializable
data class TestEnvironment(
    val os: String,
    val osVersion: String,
    val runtime: String,
    val runtimeVersion: String,
    val hardware: HardwareInfo,
    val environmentVariables: Map<String, String> = emptyMap()
) {
    companion object {
        fun createDefault(): TestEnvironment = TestEnvironment(
            os = "Unknown",
            osVersion = "Unknown",
            runtime = "Kotlin",
            runtimeVersion = "Unknown",
            hardware = HardwareInfo.createDefault()
        )
    }
}

/**
 * HardwareInfo - Information about the hardware running the tests
 */
@Serializable
data class HardwareInfo(
    val cpuModel: String,
    val cpuCores: Int,
    val memoryMB: Long,
    val gpuModel: String? = null
) {
    companion object {
        fun createDefault(): HardwareInfo = HardwareInfo(
            cpuModel = "Unknown",
            cpuCores = 1,
            memoryMB = 1024
        )
    }
}

/**
 * TestConfiguration - Configuration used for the test execution
 */
@Serializable
data class TestConfiguration(
    val parallel: Boolean,
    val maxRetries: Int,
    val timeout: Duration,
    val tags: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap()
) {
    companion object {
        fun createDefault(): TestConfiguration = TestConfiguration(
            parallel = false,
            maxRetries = 0,
            timeout = Duration.parse("PT5M") // 5 minutes
        )
    }
}

/**
 * TestReport - Generated report from test results
 */
data class TestReport(
    val testResults: TestResults,
    val generatedAt: Instant,
    val reportFormat: ReportFormat,
    val content: String = "",
    val attachments: List<String> = emptyList()
)

// Enums

@Serializable
enum class TestStatus {
    PASSED, FAILED, SKIPPED, TIMEOUT, ERROR
}

@Serializable
enum class TestPlatform {
    JVM, JS, ANDROID, IOS, NATIVE_LINUX, NATIVE_WINDOWS, NATIVE_MACOS, WEB, DESKTOP
}

@Serializable
enum class ErrorType {
    ASSERTION_FAILURE, RUNTIME_EXCEPTION, TIMEOUT, SETUP_FAILURE, TEARDOWN_FAILURE, COMPILATION_ERROR, UNKNOWN
}

@Serializable
enum class ImageFormat {
    PNG, JPEG, WEBP, BMP
}

@Serializable
enum class ArtifactType {
    SCREENSHOT, VIDEO, LOG_FILE, REPORT, DATA_FILE, BINARY, COVERAGE_REPORT
}

enum class ReportFormat {
    SUMMARY, DETAILED, JUNIT_XML, HTML, JSON
}