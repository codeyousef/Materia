package io.materia.validation.platform

import io.materia.validation.GapSeverity
import io.materia.validation.GapType
import io.materia.validation.Platform
import io.materia.validation.ValidationConfiguration
import io.materia.validation.checker.DefaultProductionReadinessChecker
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Comprehensive cross-platform validation tests ensuring consistency and functionality
 * across JVM, JS, Native, Android, and iOS platforms.
 *
 * Constitutional Requirements Validated:
 * - Type safety across all platforms
 * - Consistent API behavior between platforms
 * - Platform-specific renderer implementations
 * - Multiplatform compatibility and feature parity
 */
class CrossPlatformValidationTest {

    private lateinit var checker: DefaultProductionReadinessChecker
    private val projectRoot = "/mnt/d/Projects/KMP/Materia"

    @BeforeTest
    fun setup() {
        checker = DefaultProductionReadinessChecker()
    }

    @Test
    fun testAllPlatformSupport() = runTest {
        // Validate that all constitutional platforms are supported
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val rendererAudit = validationResult.rendererAudit

        // Check that all required platforms are tested
        val supportedPlatforms = rendererAudit.rendererComponents.keys
        val requiredPlatforms =
            setOf(Platform.JVM, Platform.JS, Platform.ANDROID, Platform.IOS, Platform.NATIVE)

        requiredPlatforms.forEach { platform ->
            assertTrue(
                supportedPlatforms.contains(platform) || rendererAudit.missingPlatforms.contains(
                    platform
                ),
                "Platform $platform should be either supported or documented as missing"
            )
        }

        // At least core platforms should be supported
        val corePlatforms = setOf(Platform.JVM, Platform.JS)
        corePlatforms.forEach { platform ->
            assertTrue(
                supportedPlatforms.contains(platform),
                "Core platform $platform must be supported"
            )
        }

        println("✅ Platform support validation passed - ${supportedPlatforms.size} platforms supported")
    }

    @Test
    fun testRendererConsistencyAcrossPlatforms() = runTest {
        // Validate renderer implementations are consistent across platforms
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val rendererComponents = validationResult.rendererAudit.rendererComponents

        if (rendererComponents.size > 1) {
            val featureCompletnessValues = rendererComponents.values.map { it.featureCompleteness }
            val performanceScores = rendererComponents.values.map { it.performanceScore }

            // Check feature completeness consistency (within 20% variance)
            val avgFeatureCompleteness = featureCompletnessValues.average().toFloat()
            featureCompletnessValues.forEach { completeness ->
                val variance = kotlin.math.abs(completeness - avgFeatureCompleteness)
                assertTrue(
                    variance <= 0.2f,
                    "Feature completeness variance $variance should be within 20% across platforms"
                )
            }

            // Check performance score consistency (within reasonable bounds)
            val avgPerformanceScore = performanceScores.average().toFloat()
            performanceScores.forEach { score ->
                val variance = kotlin.math.abs(score - avgPerformanceScore)
                assertTrue(
                    variance <= 0.3f,
                    "Performance score variance $variance should be within 30% across platforms"
                )
            }
        }

        println("✅ Cross-platform renderer consistency validated")
    }

    @Test
    fun testMultiplatformAPIConsistency() = runTest {
        // Validate that API consistency is maintained across platforms
        val gapAnalysis = checker.analyzeImplementationGaps(projectRoot)

        // Check for critical gaps that would break API consistency
        val criticalGaps = gapAnalysis.gaps.filter { it.severity == GapSeverity.CRITICAL }

        criticalGaps.forEach { gap ->
            // Critical gaps should not exist in core API modules
            assertFalse(
                gap.module.contains("core", ignoreCase = true) || gap.module.contains(
                    "api",
                    ignoreCase = true
                ),
                "Critical implementation gap found in core module: ${gap.module} - ${gap.expectedSignature}"
            )
        }

        // Platforms should have reasonable coverage
        val platformsCovered = gapAnalysis.platformsCovered
        assertTrue(
            platformsCovered.size >= 2,
            "Implementation gap analysis should cover at least 2 platforms (covered: ${platformsCovered.size})"
        )

        println("✅ Multiplatform API consistency validated - ${criticalGaps.size} critical gaps found")
    }

    @Test
    fun testPlatformSpecificImplementations() = runTest {
        // Validate platform-specific implementations exist and are correct
        val rendererAudit = checker.auditRendererImplementations(projectRoot)

        rendererAudit.rendererComponents.forEach { (platform, component) ->
            // Each platform should have a renderer component
            assertNotNull(component, "Platform $platform should have a renderer component")

            // Platform-specific implementations should meet minimum quality
            assertTrue(
                component.performanceScore >= 0.5f,
                "Platform $platform performance score ${component.performanceScore} should meet minimum threshold"
            )

            assertTrue(
                component.featureCompleteness >= 0.7f,
                "Platform $platform feature completeness ${component.featureCompleteness} should meet minimum threshold"
            )

            // Constitutional compliance should be checked
            assertTrue(
                component.constitutionalCompliance.isNotEmpty(),
                "Platform $platform should have constitutional compliance checks"
            )
        }

        println("✅ Platform-specific implementations validated")
    }

    @Test
    fun testCrossPlatformTypesSafety() = runTest {
        // Validate type safety is maintained across all platforms
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        val implementationGaps = validationResult.implementationGaps

        // Check for type safety violations
        val typeSafetyIssues = implementationGaps.gaps.filter { gap ->
            gap.gapType == GapType.MISSING_ACTUAL ||
                    gap.expectedSignature.contains("expect", ignoreCase = true)
        }

        // Type safety should be maintained - no missing actual implementations for expect declarations
        val criticalTypeSafetyIssues =
            typeSafetyIssues.filter { it.severity == GapSeverity.CRITICAL }

        assertTrue(
            criticalTypeSafetyIssues.isEmpty(),
            "Critical type safety issues found: ${criticalTypeSafetyIssues.map { "${it.platform}:${it.expectedSignature}" }}"
        )

        println("✅ Cross-platform type safety validated - ${typeSafetyIssues.size} type issues found")
    }

    @Test
    fun testJavaScriptRendererValidation() = runTest {
        // Specific validation for JavaScript renderer (addressing the black screen issue)
        val rendererAudit = checker.auditRendererImplementations(projectRoot)

        val jsRenderer = rendererAudit.rendererComponents[Platform.JS]
        if (jsRenderer != null) {
            // JS renderer should be production ready
            assertTrue(
                jsRenderer.isProductionReady == true,
                "JavaScript renderer should be production ready (black screen issue should be fixed)"
            )

            // JS renderer should have acceptable performance
            assertTrue(
                jsRenderer.performanceScore >= 0.8f,
                "JavaScript renderer performance score ${jsRenderer.performanceScore} should be high"
            )

            // JS renderer should have minimal issues
            assertTrue(
                jsRenderer.issues.size <= 2,
                "JavaScript renderer should have minimal issues (found: ${jsRenderer.issues.size})"
            )

            // Validate WebGPU-specific requirements
            val webGpuValidation = jsRenderer.validationResults["webgpu_support"] ?: false
            assertTrue(
                webGpuValidation,
                "JavaScript renderer should have WebGPU support validation"
            )
        }

        println("✅ JavaScript renderer validation passed")
    }

    @Test
    fun testPlatformFeatureParity() = runTest {
        // Ensure feature parity across supported platforms
        val rendererAudit = checker.auditRendererImplementations(projectRoot)

        val rendererComponents = rendererAudit.rendererComponents
        if (rendererComponents.size > 1) {
            val featureCompletnessThreshold = 0.8f

            rendererComponents.forEach { (platform, component) ->
                assertTrue(
                    component.featureCompleteness >= featureCompletnessThreshold,
                    "Platform $platform feature completeness ${component.featureCompleteness} should meet parity threshold"
                )

                // Core validation results should be consistent
                val coreValidations = setOf("basic_rendering", "scene_graph", "camera_system")
                coreValidations.forEach { validation ->
                    val result = component.validationResults[validation]
                    if (result != null) {
                        assertTrue(
                            result,
                            "Platform $platform should pass core validation: $validation"
                        )
                    }
                }
            }
        }

        println("✅ Platform feature parity validated")
    }

    @Test
    fun testCrossPlatformPerformanceConsistency() = runTest {
        // Validate performance consistency across platforms
        val performanceResult = checker.validatePerformance(projectRoot)

        val frameRates = performanceResult.frameRateResults

        if (frameRates.size > 1) {
            // All platforms should meet constitutional 60 FPS requirement
            frameRates.forEach { (platform, frameRate) ->
                assertTrue(
                    frameRate >= 60.0f,
                    "Platform $platform frame rate $frameRate should meet 60 FPS constitutional requirement"
                )
            }

            // Performance should be reasonably consistent
            val frameRateValues = frameRates.values.toList()
            val minFrameRate = frameRateValues.minOrNull() ?: 0f
            val maxFrameRate = frameRateValues.maxOrNull() ?: 0f
            val frameRateRange = maxFrameRate - minFrameRate

            // Frame rate range should not exceed 40% of average
            val avgFrameRate = frameRateValues.average().toFloat()
            val acceptableRange = avgFrameRate * 0.4f

            assertTrue(
                frameRateRange <= acceptableRange,
                "Cross-platform frame rate range $frameRateRange should be within acceptable bounds ($acceptableRange)"
            )
        }

        println("✅ Cross-platform performance consistency validated")
    }

    @Test
    fun testPlatformSpecificOptimizations() = runTest {
        // Validate platform-specific optimizations are in place
        val rendererAudit = checker.auditRendererImplementations(projectRoot)

        rendererAudit.rendererComponents.forEach { (platform, component) ->
            // Each platform should have optimization-related recommendations
            val hasOptimizationRecommendations = component.recommendations.any { recommendation ->
                recommendation.contains("optimization", ignoreCase = true) ||
                        recommendation.contains("performance", ignoreCase = true)
            }

            // If performance score is below optimal, there should be optimization recommendations
            if (component.performanceScore < 0.9f) {
                assertTrue(
                    hasOptimizationRecommendations,
                    "Platform $platform with performance score ${component.performanceScore} should have optimization recommendations"
                )
            }

            // Platform should not have unresolved critical issues
            val criticalIssues =
                component.issues.filter { it.contains("critical", ignoreCase = true) }
            assertTrue(
                criticalIssues.isEmpty(),
                "Platform $platform should not have critical issues: $criticalIssues"
            )
        }

        println("✅ Platform-specific optimizations validated")
    }

    @Test
    fun testCrossPlatformIntegrationComplete() = runTest {
        // Comprehensive cross-platform integration test
        val validationResult = checker.validateProductionReadiness(
            projectRoot,
            ValidationConfiguration.strict()
        )

        // Overall renderer score should be acceptable
        assertTrue(
            validationResult.rendererAudit.overallRendererScore >= 0.8f,
            "Overall renderer score ${validationResult.rendererAudit.overallRendererScore} should be high for production readiness"
        )

        // Missing platforms should be documented if any
        val missingPlatforms = validationResult.rendererAudit.missingPlatforms
        if (missingPlatforms.isNotEmpty()) {
            println("ℹ️ Missing platforms documented: $missingPlatforms")
        }

        // Performance issues should be manageable
        val performanceIssues = validationResult.rendererAudit.performanceIssues
        assertTrue(
            performanceIssues.size <= 3,
            "Performance issues should be manageable (found: ${performanceIssues.size})"
        )

        // Cross-platform component score should be included
        assertTrue(
            validationResult.componentScores.containsKey("cross_platform") ||
                    validationResult.componentScores.containsKey("renderer"),
            "Cross-platform or renderer component score should be included"
        )

        println("✅ Cross-platform integration validation completed successfully")
    }

    @Test
    fun testPlatformSpecificFeatureDetection() = runTest {
        // Test detection of platform-specific features and capabilities
        val rendererAudit = checker.auditRendererImplementations(projectRoot)

        rendererAudit.rendererComponents.forEach { (platform, component) ->
            // Platform-specific validations should exist
            assertTrue(
                component.validationResults.isNotEmpty(),
                "Platform $platform should have validation results"
            )

            // Constitutional compliance should be platform-aware
            assertTrue(
                component.constitutionalCompliance.isNotEmpty(),
                "Platform $platform should have constitutional compliance checks"
            )

            // Platform should report production readiness status
            assertNotNull(
                component.isProductionReady,
                "Platform $platform should report production readiness status"
            )
        }

        println("✅ Platform-specific feature detection validated")
    }
}