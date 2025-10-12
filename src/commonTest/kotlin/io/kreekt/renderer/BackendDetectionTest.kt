/**
 * T004: Contract Test - Backend Detection (FR-004)
 * Feature: 019-we-should-not
 *
 * Tests that RendererFactory.detectAvailableBackends() returns correct backends
 * for the current platform.
 *
 * Requirements Tested:
 * - FR-004: Automatic backend detection
 *
 * EXPECTED: These tests MUST FAIL until RendererFactory is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackendDetectionTest {

    @Test
    fun `detectAvailableBackends returns non-empty list`() {
        // This will fail: RendererFactory not yet implemented
        val backends = RendererFactory.detectAvailableBackends()

        assertTrue(
            backends.isNotEmpty(),
            "Platform must have at least one available backend"
        )
    }

    @Test
    fun `detectAvailableBackends returns valid BackendType values`() {
        // This will fail: BackendType enum not yet defined
        val backends = RendererFactory.detectAvailableBackends()

        backends.forEach { backend ->
            assertTrue(
                backend is BackendType,
                "All returned backends must be valid BackendType values"
            )
        }
    }

    @Test
    fun `JS platform has at least WebGL fallback`() {
        // Platform-specific test - will be implemented in platform source sets
        val platform = getPlatform()
        if (platform == Platform.JS) {
            val backends = RendererFactory.detectAvailableBackends()

            assertTrue(
                backends.contains(BackendType.WEBGPU) || backends.contains(BackendType.WEBGL),
                "JS platform must support WebGPU or WebGL"
            )
        }
    }

    @Test
    fun `JVM platform has Vulkan support`() {
        // Platform-specific test
        val platform = getPlatform()
        if (platform == Platform.JVM) {
            val backends = RendererFactory.detectAvailableBackends()

            assertTrue(
                backends.contains(BackendType.VULKAN),
                "JVM platform must support Vulkan (FR-002)"
            )
        }
    }

    @Test
    fun `detectAvailableBackends is deterministic`() {
        // Detection should return same results on repeated calls
        val backends1 = RendererFactory.detectAvailableBackends()
        val backends2 = RendererFactory.detectAvailableBackends()

        assertEquals(
            backends1.size,
            backends2.size,
            "Backend detection must be deterministic"
        )

        assertTrue(
            backends1.containsAll(backends2),
            "Backend detection must return consistent results"
        )
    }
}
