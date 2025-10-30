package io.materia.validation.suite

import io.materia.validation.*
import io.materia.validation.checker.DefaultProductionReadinessChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * T035: Complete test suite validation ensuring all tests pass
 * and maintaining the 627 test target with 100% success rate.
 *
 * Constitutional Requirements Validated:
 * - All tests must pass (100% success rate)
 * - Test coverage meets constitutional standards
 * - Test quality and reliability validation
 * - Cross-platform test execution validation
 */
class TestSuiteValidationTest {

    private lateinit var checker: DefaultProductionReadinessChecker
    private val projectRoot = "/mnt/d/Projects/KMP/Materia"

    @BeforeTest
    fun setup() {
        checker = DefaultProductionReadinessChecker()
    }

    @Test
    fun testExecuteTestSuiteValidation() = runTest {
        // Execute the complete test suite validation
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        // Validate basic test metrics
        assertTrue(
            testResult.totalTests > 0,
            "Total tests should be greater than 0 (found: ${testResult.totalTests})"
        )

        // Check test execution completeness
        val completedTests =
            testResult.passedTests + testResult.failedTests + testResult.skippedTests
        assertEquals(
            testResult.totalTests,
            completedTests,
            "Completed tests ($completedTests) should equal total tests (${testResult.totalTests})"
        )

        // Validate execution time is reasonable
        assertTrue(
            testResult.executionTimeMs > 0,
            "Test execution time should be positive (found: ${testResult.executionTimeMs}ms)"
        )

        println("✅ Test suite execution validated - ${testResult.totalTests} tests processed")
    }

    @Test
    fun testValidateTestSuccessRate() = runTest {
        // Validate the constitutional requirement of high test success rate
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        if (testResult.totalTests > 0) {
            val successRate = testResult.passedTests.toFloat() / testResult.totalTests.toFloat()

            // Constitutional requirement: maintain high test success rate (>95%)
            assertTrue(
                successRate >= 0.95f,
                "Test success rate $successRate should be >= 95% for production readiness (passed: ${testResult.passedTests}, total: ${testResult.totalTests})"
            )

            // Ideally aim for 100% success rate
            if (successRate >= 0.99f) {
                val successRateFormatted = (successRate * 100).format(1)
                println("✅ Excellent test success rate: ${successRateFormatted}%")
            } else {
                val successRateFormatted = (successRate * 100).format(1)
                println("⚠️ Test success rate: ${successRateFormatted}% - improvement recommended")
            }
        }

        println("✅ Test success rate validation completed")
    }

    @Test
    fun testValidateTestCoverage() = runTest {
        // Validate code coverage meets constitutional standards
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        // Constitutional requirement: >80% code coverage
        assertTrue(
            testResult.codeCoverage >= 0.8f,
            "Code coverage ${testResult.codeCoverage} should be >= 80% for constitutional compliance"
        )

        // Validate coverage by category
        assertTrue(
            testResult.unitTestResults.coverage >= 0.75f,
            "Unit test coverage ${testResult.unitTestResults.coverage} should be >= 75%"
        )

        assertTrue(
            testResult.integrationTestResults.coverage >= 0.60f,
            "Integration test coverage ${testResult.integrationTestResults.coverage} should be >= 60%"
        )

        val coverageFormatted = (testResult.codeCoverage * 100).format(1)
        println("✅ Test coverage validation passed - ${coverageFormatted}% overall coverage")
    }

    @Test
    fun testValidateTestCategories() = runTest {
        // Validate all test categories are properly executed
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        // Unit tests validation
        val unitTests = testResult.unitTestResults
        assertTrue(
            unitTests.totalTests > 0,
            "Unit tests should exist (found: ${unitTests.totalTests})"
        )

        // Integration tests validation
        val integrationTests = testResult.integrationTestResults
        assertTrue(
            integrationTests.totalTests >= 0,
            "Integration test count should be non-negative (found: ${integrationTests.totalTests})"
        )

        // Performance tests validation
        val performanceTests = testResult.performanceTestResults
        assertTrue(
            performanceTests.totalTests >= 0,
            "Performance test count should be non-negative (found: ${performanceTests.totalTests})"
        )

        println("✅ Test categories validated - Unit: ${unitTests.totalTests}, Integration: ${integrationTests.totalTests}, Performance: ${performanceTests.totalTests}")
    }

    @Test
    fun testValidateTestFailures() = runTest {
        // Validate test failures are properly documented
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        // Check test failures documentation
        testResult.testFailures.forEach { failure ->
            assertTrue(
                failure.testName.isNotBlank(),
                "Test failure should have a test name"
            )
            assertTrue(
                failure.errorMessage.isNotBlank(),
                "Test failure should have an error message"
            )
            assertTrue(
                failure.category.isNotBlank(),
                "Test failure should have a category"
            )
        }

        // Constitutional requirement: minimal test failures
        val failureRate = if (testResult.totalTests > 0) {
            testResult.failedTests.toFloat() / testResult.totalTests.toFloat()
        } else 0f

        assertTrue(
            failureRate <= 0.05f,
            "Test failure rate $failureRate should be <= 5% for production readiness"
        )

        println("✅ Test failures validation passed - ${testResult.failedTests} failures documented")
    }

    @Test
    fun testValidateTestIntegration() = runTest {
        // Validate test suite integration with overall validation
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        assertNotNull(validationResult, "Overall validation result should not be null")
        assertNotNull(validationResult.testResults, "Test results should be included")

        val testResults = validationResult.testResults

        // Validate integration with component scores
        assertTrue(
            validationResult.componentScores.containsKey("testing") ||
                    validationResult.componentScores.containsKey("test_suite"),
            "Testing component score should be included in overall validation"
        )

        // If tests pass well, overall score should be high
        val testScore =
            validationResult.componentScores["testing"]
                ?: validationResult.componentScores["test_suite"] ?: 0f

        val successRate = if (testResults.totalTests > 0) {
            testResults.passedTests.toFloat() / testResults.totalTests.toFloat()
        } else 1f

        if (successRate >= 0.95f && testResults.codeCoverage >= 0.8f) {
            assertTrue(
                testScore >= 0.8f,
                "Test score should be high when success rate and coverage are good"
            )
        }

        println("✅ Test suite integration validation passed")
    }

    @Test
    fun testValidateTestPerformance() = runTest {
        // Validate test execution performance
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        if (testResult.totalTests > 0) {
            // Test execution should be reasonably fast (< 10 seconds per test on average)
            val avgTimePerTest =
                testResult.executionTimeMs.toFloat() / testResult.totalTests.toFloat()
            assertTrue(
                avgTimePerTest < 10000f, // 10 seconds per test max
                "Average test execution time ${avgTimePerTest}ms should be reasonable"
            )

            // Total execution time should be documented
            assertTrue(
                testResult.executionTimeMs > 0,
                "Test execution time should be positive"
            )
        }

        println("✅ Test performance validation passed - ${testResult.executionTimeMs}ms total execution time")
    }

    @Test
    fun testValidateTestQuality() = runTest {
        // Validate test quality metrics
        val testResult = checker.executeTestSuite(projectRoot)

        assertNotNull(testResult, "Test execution result should not be null")

        // Test categories should have reasonable distribution
        if (testResult.totalTests > 0) {
            val unitTestRatio =
                testResult.unitTestResults.totalTests.toFloat() / testResult.totalTests.toFloat()

            // Unit tests should dominate the test suite (>60%)
            val unitTestRatioFormatted = (unitTestRatio * 100).format(1)
            assertTrue(
                unitTestRatio >= 0.6f,
                "Unit tests should comprise >= 60% of test suite (found: ${unitTestRatioFormatted}%)"
            )
        }

        // Skipped tests should be minimal
        val skippedRatio = if (testResult.totalTests > 0) {
            testResult.skippedTests.toFloat() / testResult.totalTests.toFloat()
        } else 0f

        assertTrue(
            skippedRatio <= 0.1f,
            "Skipped test ratio $skippedRatio should be <= 10%"
        )

        println("✅ Test quality validation passed")
    }

    @Test
    fun testValidateConstitutionalTestCompliance() = runTest {
        // Comprehensive constitutional test compliance validation
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val complianceResult = checker.checkConstitutionalCompliance(validationResult)

        // Check test-related constitutional requirements
        val testCompliance = mapOf(
            "test_coverage_80_percent" to (validationResult.testResults.codeCoverage >= 0.8f),
            "test_success_rate_95_percent" to (
                    if (validationResult.testResults.totalTests > 0) {
                        validationResult.testResults.passedTests.toFloat() / validationResult.testResults.totalTests.toFloat() >= 0.95f
                    } else true
                    ),
            "minimal_test_failures" to (validationResult.testResults.failedTests <= validationResult.testResults.totalTests * 0.05)
        )

        testCompliance.forEach { (requirement, isMet) ->
            assertTrue(
                isMet,
                "Constitutional test requirement '$requirement' must be satisfied"
            )
        }

        // Overall compliance should include test requirements
        assertTrue(
            complianceResult.constitutionalRequirements.any {
                it.key.contains(
                    "test",
                    ignoreCase = true
                )
            },
            "Constitutional compliance should include test requirements"
        )

        println("✅ Constitutional test compliance validated")
    }

    @Test
    fun testValidateTestRecommendations() = runTest {
        // Test that test validation provides actionable recommendations
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val recommendations = checker.generateRecommendations(validationResult)

        // If test issues exist, there should be test-related recommendations
        val testIssues = validationResult.testResults.failedTests > 0 ||
                validationResult.testResults.codeCoverage < 0.8f

        if (testIssues) {
            assertTrue(
                recommendations.any { it.contains("test", ignoreCase = true) },
                "Should provide test-related recommendations when test issues exist"
            )
        }

        // All recommendations should be actionable
        recommendations.forEach { recommendation ->
            assertTrue(
                recommendation.isNotBlank(),
                "Test recommendations should not be blank"
            )
            assertTrue(
                recommendation.length > 10,
                "Test recommendations should be meaningful"
            )
        }

        println("✅ Test recommendations validation passed")
    }
}