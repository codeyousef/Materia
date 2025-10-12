/**
 * T005: Contract Test - Primary Backend Selection (FR-001, FR-002)
 * Feature: 019-we-should-not
 *
 * Tests that RendererFactory.create() selects primary backend by default.
 *
 * Requirements Tested:
 * - FR-001: WebGPU primary for JS
 * - FR-002: Vulkan primary for JVM
 * - FR-023: Log selected backend
 *
 * EXPECTED: These tests MUST FAIL until RendererFactory is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PrimaryBackendTest {

    @Test
    fun `RendererFactory create returns success on supported platform`() = runTest {
        val surface = createTestSurface(width = 800, height = 600)

        val result = RendererFactory.create(surface)

        assertTrue(
            result.isSuccess,
            "Renderer creation should succeed on supported platform"
        )
    }

    @Test
    fun `JS platform uses WebGPU if available`() = runTest {
        val platform = getPlatform()
        if (platform == Platform.JS) {
            val surface = createTestSurface(800, 600)
            val renderer = RendererFactory.create(surface).getOrThrow()

            // Should prefer WebGPU if available, otherwise WebGL
            assertTrue(
                renderer.backend == BackendType.WEBGPU || renderer.backend == BackendType.WEBGL,
                "JS must use WebGPU or WebGL (FR-001)"
            )

            // If WebGPU is available, it should be preferred
            val availableBackends = RendererFactory.detectAvailableBackends()
            if (availableBackends.contains(BackendType.WEBGPU)) {
                assertEquals(
                    BackendType.WEBGPU,
                    renderer.backend,
                    "WebGPU should be preferred when available (FR-001)"
                )
            }

            renderer.dispose()
        }
    }

    @Test
    fun `JVM platform uses Vulkan`() = runTest {
        val platform = getPlatform()
        if (platform == Platform.JVM) {
            val surface = createTestSurface(800, 600)
            val renderer = RendererFactory.create(surface).getOrThrow()

            assertEquals(
                BackendType.VULKAN,
                renderer.backend,
                "JVM must use Vulkan as primary backend (FR-002)"
            )

            renderer.dispose()
        }
    }

    @Test
    fun `backend selection is logged`() = runTest {
        val surface = createTestSurface(800, 600)

        // Capture logs (platform-specific implementation)
        val logCapture = captureLog {
            val renderer = RendererFactory.create(surface).getOrThrow()
            renderer.dispose()
        }

        assertTrue(
            logCapture.contains("backend", ignoreCase = true) ||
                    logCapture.contains("selected", ignoreCase = true) ||
                    logCapture.contains("vulkan", ignoreCase = true) ||
                    logCapture.contains("webgpu", ignoreCase = true),
            "Backend selection must be logged (FR-023). Got: $logCapture"
        )
    }

    @Test
    fun `create with default config uses primary backend`() = runTest {
        val surface = createTestSurface(800, 600)

        // Default config should select primary backend automatically
        val renderer = RendererFactory.create(surface).getOrThrow()

        assertTrue(
            renderer.backend.isPrimary,
            "Default config should select primary backend (WebGPU/Vulkan, not WebGL)"
        )

        renderer.dispose()
    }

    @Test
    fun `WebGL is only used as fallback on JS`() = runTest {
        val platform = getPlatform()
        if (platform == Platform.JS) {
            val surface = createTestSurface(800, 600)
            val renderer = RendererFactory.create(surface).getOrThrow()

            // If WebGL is used, WebGPU must not be available
            if (renderer.backend == BackendType.WEBGL) {
                val availableBackends = RendererFactory.detectAvailableBackends()
                assertFalse(
                    availableBackends.contains(BackendType.WEBGPU),
                    "WebGL should only be used when WebGPU is unavailable (FR-005)"
                )
            }

            renderer.dispose()
        }
    }
}
