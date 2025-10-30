package io.materia.validation.performance

import io.materia.validation.*
import io.materia.validation.checker.DefaultProductionReadinessChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive performance validation tests ensuring constitutional 60 FPS requirement
 * and memory usage limits are maintained across all platforms.
 *
 * Constitutional Requirements Validated:
 * - 60 FPS target performance across all platforms
 * - <5MB base library size constraint
 * - Memory usage within acceptable limits
 * - Performance consistency across platform implementations
 */
class PerformanceValidationTest {

    private lateinit var checker: DefaultProductionReadinessChecker
    private val projectRoot = "/mnt/d/Projects/KMP/Materia"

    @BeforeTest
    fun setup() {
        checker = DefaultProductionReadinessChecker()
    }

    @Test
    fun testFrameRateRequirementValidation() = runTest {
        // Validate constitutional 60 FPS requirement
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        // Check frame rate meets constitutional requirement
        assertTrue(
            performanceResult.averageFrameRate >= 60.0f,
            "Average frame rate ${performanceResult.averageFrameRate} must meet constitutional 60 FPS requirement"
        )

        // Validate frame rate requirement is met
        assertTrue(
            performanceResult.meetsFrameRateRequirement == true,
            "Constitutional frame rate requirement must be satisfied"
        )

        // Check per-platform frame rates
        performanceResult.frameRateResults.forEach { (platform, frameRate) ->
            assertTrue(
                frameRate >= 60.0f,
                "Platform $platform frame rate $frameRate must meet 60 FPS constitutional requirement"
            )
        }

        println("✅ Frame rate validation passed - all platforms meet 60 FPS requirement")
    }

    @Test
    fun testLibrarySizeConstraintValidation() = runTest {
        // Validate constitutional <5MB library size constraint
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        val fiveMegabytes = 5 * 1024 * 1024L // 5MB in bytes

        assertTrue(
            performanceResult.librarySize <= fiveMegabytes,
            "Library size ${performanceResult.librarySize} bytes must be under constitutional 5MB limit ($fiveMegabytes bytes)"
        )

        // Validate size requirement is met
        assertTrue(
            performanceResult.meetsSizeRequirement == true,
            "Constitutional size requirement must be satisfied"
        )

        val sizeMB = performanceResult.librarySize / (1024.0 * 1024.0)
        val sizeMBFormatted = sizeMB.format(2)
        println("✅ Library size validation passed - ${sizeMBFormatted}MB is under 5MB constitutional limit")
    }

    @Test
    fun testMemoryUsageValidation() = runTest {
        // Validate memory usage stays within acceptable constitutional limits
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        // Check memory size results
        performanceResult.memorySizeResults.forEach { (component, memoryUsage) ->
            // Ensure no component uses excessive memory (>100MB as reasonable limit)
            val maxMemoryLimit = 100 * 1024 * 1024L // 100MB
            assertTrue(
                memoryUsage <= maxMemoryLimit,
                "Component $component memory usage $memoryUsage must be under $maxMemoryLimit bytes"
            )
        }

        println("✅ Memory usage validation passed - all components within acceptable limits")
    }

    @Test
    fun testPerformanceConsistencyAcrossPlatforms() = runTest {
        // Ensure performance is consistent across all supported platforms
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        val frameRates = performanceResult.frameRateResults.values.toList()

        if (frameRates.size > 1) {
            val minFrameRate = frameRates.minOrNull() ?: 0f
            val maxFrameRate = frameRates.maxOrNull() ?: 0f
            val frameRateVariance = maxFrameRate - minFrameRate

            // Ensure frame rate variance is reasonable (within 20% of average)
            val averageFrameRate = performanceResult.averageFrameRate
            val acceptableVariance = averageFrameRate * 0.2f

            assertTrue(
                frameRateVariance <= acceptableVariance,
                "Frame rate variance $frameRateVariance should be within 20% of average ($acceptableVariance)"
            )
        }

        println("✅ Cross-platform performance consistency validated")
    }

    @Test
    fun testPerformanceIssuesDetection() = runTest {
        // Validate that performance issues are properly detected and reported
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        // Check that performance issues list is available
        assertNotNull(
            performanceResult.performanceIssues,
            "Performance issues list should not be null"
        )

        // If there are performance issues, they should be documented
        performanceResult.performanceIssues.forEach { issue ->
            assertTrue(
                issue.isNotBlank(),
                "Performance issue descriptions should not be blank"
            )
        }

        println("✅ Performance issues detection validated - ${performanceResult.performanceIssues.size} issues found")
    }

    @Test
    fun testPerformanceValidationIntegration() = runTest {
        // Test complete performance validation integration
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        assertNotNull(validationResult, "Overall validation result should not be null")
        assertNotNull(
            validationResult.performanceValidation,
            "Performance validation should be included"
        )

        val performance = validationResult.performanceValidation

        // Validate integration with overall validation
        assertTrue(
            validationResult.componentScores.containsKey("performance"),
            "Performance component score should be included in overall validation"
        )

        val performanceScore = validationResult.componentScores["performance"] ?: 0f
        assertTrue(
            performanceScore >= 0f && performanceScore <= 1f,
            "Performance score $performanceScore should be between 0 and 1"
        )

        // If performance meets constitutional requirements, score should be high
        if (performance.meetsFrameRateRequirement == true && performance.meetsSizeRequirement == true) {
            assertTrue(
                performanceScore >= 0.8f,
                "Performance score should be high when constitutional requirements are met"
            )
        }

        println("✅ Performance validation integration test passed")
    }

    @Test
    fun testConstitutionalPerformanceCompliance() = runTest {
        // Comprehensive constitutional compliance test for performance requirements
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val complianceResult = checker.checkConstitutionalCompliance(validationResult)

        // Check performance-related constitutional requirements
        val performanceCompliance = mapOf(
            "60_fps_requirement" to (validationResult.performanceValidation.meetsFrameRateRequirement == true),
            "5mb_size_limit" to (validationResult.performanceValidation.meetsSizeRequirement == true),
            "cross_platform_consistency" to (validationResult.performanceValidation.frameRateResults.size >= 3)
        )

        performanceCompliance.forEach { (requirement, isMet) ->
            assertTrue(
                isMet,
                "Constitutional performance requirement '$requirement' must be satisfied"
            )
        }

        // Overall compliance should include performance requirements
        assertTrue(
            complianceResult.constitutionalRequirements.any {
                it.key.contains(
                    "performance",
                    ignoreCase = true
                )
            },
            "Constitutional compliance should include performance requirements"
        )

        println("✅ Constitutional performance compliance validated")
    }

    @Test
    fun testRealWorldPerformanceScenarios() = runTest {
        // Test performance under realistic usage scenarios
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        // Validate that performance testing covers realistic scenarios
        assertTrue(
            performanceResult.frameRateResults.isNotEmpty(),
            "Performance validation should test actual frame rate scenarios"
        )

        // Check that performance validation tests multiple platforms
        val platformsTestedCount = performanceResult.frameRateResults.size
        assertTrue(
            platformsTestedCount >= 2,
            "Performance validation should test multiple platforms (tested: $platformsTestedCount)"
        )

        println("✅ Real-world performance scenarios validated")
    }

    @Test
    fun testPerformanceRecommendations() = runTest {
        // Test that performance validation provides actionable recommendations
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val recommendations = checker.generateRecommendations(validationResult)

        // If performance issues exist, there should be performance-related recommendations
        val performanceIssues = validationResult.performanceValidation.performanceIssues
        if (performanceIssues.isNotEmpty()) {
            assertTrue(
                recommendations.any { it.contains("performance", ignoreCase = true) },
                "Should provide performance-related recommendations when issues exist"
            )
        }

        // All recommendations should be actionable
        recommendations.forEach { recommendation ->
            assertTrue(
                recommendation.isNotBlank(),
                "Performance recommendations should not be blank"
            )
            assertTrue(
                recommendation.length > 10,
                "Performance recommendations should be meaningful"
            )
        }

        println("✅ Performance recommendations validation passed")
    }

    @Test
    fun testPerformanceValidationCompleteness() = runTest {
        // Ensure performance validation covers all required aspects
        val performanceResult = checker.validatePerformance(projectRoot)

        assertNotNull(performanceResult, "Performance validation should not be null")

        // Validate all required performance metrics are present
        assertNotNull(performanceResult.frameRateResults, "Frame rate results should be present")
        assertNotNull(performanceResult.memorySizeResults, "Memory size results should be present")
        assertNotNull(performanceResult.performanceIssues, "Performance issues should be present")

        // Validate boolean requirements are properly evaluated
        assertNotNull(
            performanceResult.meetsFrameRateRequirement,
            "Frame rate requirement evaluation should be present"
        )
        assertNotNull(
            performanceResult.meetsSizeRequirement,
            "Size requirement evaluation should be present"
        )

        // Validate numeric metrics are reasonable
        assertTrue(
            performanceResult.averageFrameRate >= 0f,
            "Average frame rate should be non-negative"
        )
        assertTrue(
            performanceResult.librarySize >= 0L,
            "Library size should be non-negative"
        )

        println("✅ Performance validation completeness verified")
    }
}