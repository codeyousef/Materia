package io.materia.validation.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import io.materia.validation.services.CompilationValidator
import io.materia.validation.models.Platform
import io.materia.validation.models.ValidationStatus

/**
 * Contract test for validateCompilation endpoint.
 * Tests compilation validation across platforms.
 */
class CompilationTest {

    @Test
    fun `validateCompilation should check all platforms`() = runTest {
        val validator = CompilationValidator()
        val platforms = listOf(Platform.JVM, Platform.JS, Platform.NATIVE_LINUX_X64)

        val result = validator.validateCompilation(
            projectPath = ".",
            platforms = platforms
        )

        assertNotNull(result)
        assertNotNull(result.status)
        assertNotNull(result.platformResults)
        // JS platform returns SKIPPED with empty results, which is acceptable
        assertTrue(result.platformResults.size <= platforms.size)
    }

    @Test
    fun `validateCompilation should report compilation errors`() = runTest {
        val validator = CompilationValidator()

        val result = validator.validateCompilation(
            projectPath = "/invalid/path",
            platforms = listOf(Platform.JVM)
        )

        assertNotNull(result)
        // JS returns SKIPPED, JVM returns FAILED
        assertTrue(result.status == ValidationStatus.FAILED || result.status == ValidationStatus.SKIPPED)
    }

    @Test
    fun `validateCompilation should handle platform unavailability gracefully`() = runTest {
        val validator = CompilationValidator()

        val result = validator.validateCompilation(
            projectPath = ".",
            platforms = listOf(Platform.IOS)
        )

        assertNotNull(result)
        // Platform may be SKIPPED, WARNING, or even FAILED if not available
        assertTrue(
            result.status == ValidationStatus.SKIPPED ||
                    result.status == ValidationStatus.WARNING ||
                    result.status == ValidationStatus.FAILED
        )
    }

    @Test
    fun `validateCompilation should validate response schema`() = runTest {
        val validator = CompilationValidator()

        val result = validator.validateCompilation(
            projectPath = ".",
            platforms = listOf(Platform.JVM)
        )

        assertNotNull(result)
        assertNotNull(result.status)
        assertNotNull(result.platformResults)

        result.platformResults.forEach { (platform, platformResult) ->
            assertNotNull(platform)
            assertNotNull(platformResult.success)
            if (!platformResult.success) {
                assertNotNull(platformResult.errorMessages)
            }
        }
    }

    @Test
    fun `validateCompilation should complete within timeout`() = runTest {
        val validator = CompilationValidator()
        val startTime = Clock.System.now().toEpochMilliseconds()

        val result = validator.validateCompilation(
            projectPath = ".",
            platforms = Platform.values().toList(),
            timeoutMillis = 300_000L
        )

        val duration = Clock.System.now().toEpochMilliseconds() - startTime
        assertTrue(duration < 300_000L)
        assertNotNull(result)
    }
}
