package io.materia.validation.api

import io.materia.validation.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ValidationPipelineTest {

    private val criteriaExtractor = CriteriaExtractor()
    private val remediationGenerator = RemediationActionGenerator()
    private val recommendationEngine = RecommendationEngine()
    private val aggregator = ResultAggregator(criteriaExtractor, remediationGenerator)

    @Test
    fun `criteria extractor covers all result types and fallback`() {
        val compilation = CompilationResult(
            status = ValidationStatus.FAILED,
            score = 0.6f,
            message = "Compilation issues detected",
            platformResults = mapOf(
                "jvm" to PlatformCompilationStatus(
                    platform = "jvm",
                    success = false,
                    errorMessages = listOf("Missing symbol")
                ),
                "js" to PlatformCompilationStatus(platform = "js", success = true)
            ),
            errors = listOf(
                CompilationError(
                    file = "Main.kt",
                    line = 42,
                    column = 5,
                    message = "Unresolved reference"
                )
            )
        )
        val tests = TestResults(
            status = ValidationStatus.FAILED,
            score = 0.5f,
            message = "Tests failing",
            totalTests = 100,
            passedTests = 90,
            failedTests = 10,
            skippedTests = 0,
            lineCoverage = 72.5f,
            branchCoverage = 60f,
            executionTime = 12_000L
        )
        val performance = PerformanceMetrics(
            status = ValidationStatus.FAILED,
            score = 0.7f,
            message = "Frame rate below target",
            averageFps = 58f,
            minFps = 45f,
            maxFps = 110f,
            memoryUsageMb = 420f,
            peakMemoryMb = 512f,
            frameTimeMs = 18f,
            gcPauseTimeMs = 30L
        )
        val constitutional = ConstitutionalCompliance(
            status = ValidationStatus.WARNING,
            score = 0.8f,
            message = "Placeholder code detected",
            tddCompliance = TddComplianceResult(
                isCompliant = false,
                testsWrittenFirst = true,
                testCoveragePercent = 70f,
                untestableFunctions = listOf("Renderer.render")
            ),
            placeholderCodeCount = 3,
            expectActualPairs = ExpectActualValidation(
                totalExpects = 5,
                matchedPairs = 4,
                unmatchedExpects = 1
            ),
            codeSmells = listOf(
                CodeSmell(
                    type = "LongMethod",
                    file = "Renderer.kt",
                    line = 120,
                    description = "Method too long",
                    severity = "MEDIUM"
                )
            )
        )
        val security = SecurityValidationResult(
            status = ValidationStatus.PASSED,
            score = 1.0f,
            message = "Security clean",
            vulnerabilities = emptyList(),
            securityPatternViolations = emptyList(),
            dependencyVulnerabilities = emptyList(),
            codeIssues = emptyList()
        )
        val fallback = StubResult(
            status = ValidationStatus.PASSED,
            score = 1.0f,
            message = "All good",
            details = emptyMap()
        )

        val compilationCriteria =
            criteriaExtractor.extractCriteria(compilation, "CompilationValidator")
        val testCriteria = criteriaExtractor.extractCriteria(tests, "TestCoverageValidator")
        val performanceCriteria =
            criteriaExtractor.extractCriteria(performance, "PerformanceValidator")
        val constitutionalCriteria =
            criteriaExtractor.extractCriteria(constitutional, "ConstitutionalValidator")
        val securityCriteria = criteriaExtractor.extractCriteria(security, "SecurityValidator")
        val fallbackCriteria = criteriaExtractor.extractCriteria(fallback, "CustomValidator")

        assertEquals(1, compilationCriteria.size)
        assertEquals(2, testCriteria.size)
        assertEquals(1, performanceCriteria.size)
        assertEquals(2, constitutionalCriteria.size)
        assertEquals(1, securityCriteria.size)
        assertEquals(1, fallbackCriteria.size)
        assertEquals("CustomValidator-general", fallbackCriteria.first().id)
    }

    @Test
    fun `aggregator builds report with remediation and recommendations`() {
        val executionResult = ValidationExecutionResult(
            results = listOf(
                ValidatorResult(
                    name = "CompilationValidator",
                    result = CompilationResult(
                        status = ValidationStatus.FAILED,
                        score = 0.6f,
                        message = "Compilation failed on JVM",
                        platformResults = mapOf(
                            "jvm" to PlatformCompilationStatus(
                                "jvm",
                                success = false,
                                errorMessages = listOf("Missing symbol")
                            ),
                            "js" to PlatformCompilationStatus("js", success = true)
                        ),
                        errors = listOf(
                            CompilationError(
                                file = "Main.kt",
                                line = 12,
                                column = 4,
                                message = "Unresolved reference"
                            )
                        )
                    ),
                    error = null
                ),
                ValidatorResult(
                    name = "TestCoverageValidator",
                    result = TestResults(
                        status = ValidationStatus.FAILED,
                        score = 0.5f,
                        message = "Coverage below threshold",
                        totalTests = 120,
                        passedTests = 100,
                        failedTests = 20,
                        skippedTests = 0,
                        lineCoverage = 70f,
                        branchCoverage = 55f,
                        executionTime = 18_000L
                    ),
                    error = null
                ),
                ValidatorResult(
                    name = "SecurityValidator",
                    result = null,
                    error = IllegalStateException("Timeout")
                )
            ),
            executionTime = 2.seconds
        )

        val configuration = ValidationConfiguration.strict()
        val context = ValidationContext(
            projectPath = "/project",
            configuration = mapOf(
                "branchName" to "main",
                "commitHash" to "abc1234"
            )
        )

        val report = aggregator.aggregate(executionResult, configuration, context)

        assertEquals("main", report.branchName)
        assertEquals(ValidationStatus.FAILED, report.overallStatus)
        assertTrue(report.categories.any { it.name == ValidationCategory.COMPILATION })
        assertTrue(report.remediationActions.isNotEmpty())

        val recommendations = recommendationEngine.generateRecommendations(report)
        assertTrue(recommendations.isNotEmpty())
        // Recommendations are sorted by priority (CRITICAL first)
        assertEquals(Priority.CRITICAL, recommendations.first().priority)
    }

    private data class StubResult(
        override val status: ValidationStatus,
        override val score: Float,
        override val message: String,
        override val details: Map<String, Any>
    ) : ValidationResult
}
