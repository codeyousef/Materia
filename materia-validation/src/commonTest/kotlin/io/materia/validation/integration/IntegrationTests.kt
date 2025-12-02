package io.materia.validation.integration

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import io.materia.validation.api.ProductionReadinessChecker
import io.materia.validation.models.*

/**
 * Integration tests for validation scenarios.
 * 
 * Note: Tests that spawn Gradle processes are disabled in CI due to timeout issues.
 * These tests validate the API surface and configuration handling only.
 */
class PreReleaseTest {
    @Test
    fun `pre-release validation configuration should be valid`() {
        val config = ValidationConfiguration(
            enabledCategories = setOf(
                "compilation",
                "testing",
                "performance",
                "security",
                "constitutional"
            ),
            coverageThreshold = 95.0f,
            failFast = false
        )

        assertNotNull(config)
        assertTrue(config.enabledCategories.size == 5)
        assertTrue(config.coverageThreshold == 95.0f)
    }
}

class PullRequestTest {
    @Test
    fun `PR validation configuration should support fail fast`() {
        val config = ValidationConfiguration(
            enabledCategories = setOf("compilation", "testing"),
            failFast = true
        )

        assertNotNull(config)
        assertTrue(config.failFast)
    }
}

class DailyBuildTest {
    @Test
    fun `daily build configuration should support HTML reports`() {
        val config = ValidationConfiguration(generateHtmlReport = true)
        
        assertNotNull(config)
        assertTrue(config.generateHtmlReport)
    }
}

class CrossPlatformTest {
    @Test
    fun `cross-platform configuration should accept platform set`() {
        val config = ValidationConfiguration(
            platforms = setOf(Platform.JVM, Platform.JS, Platform.NATIVE_LINUX_X64)
        )

        assertNotNull(config)
        assertTrue(config.platforms.size == 3)
        assertTrue(config.platforms.contains(Platform.JVM))
    }
}

class ProductionReadinessCheckerTest {
    @Test
    fun `checker should be instantiable`() {
        val checker = ProductionReadinessChecker()
        assertNotNull(checker)
    }
}
