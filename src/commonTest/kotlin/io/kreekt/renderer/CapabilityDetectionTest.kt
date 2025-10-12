/**
 * T010: Contract Test - Capability Detection (FR-024)
 * Feature: 019-we-should-not
 *
 * Tests renderer detects and validates backend capabilities.
 *
 * Requirements Tested:
 * - FR-024: Detect and report backend capability mismatches before rendering
 *
 * EXPECTED: These tests MUST FAIL until RendererCapabilities is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CapabilityDetectionTest {

    @Test
    fun `renderer capabilities are available after initialization`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        assertNotNull(
            renderer.capabilities,
            "Capabilities must be available after renderer initialization"
        )

        renderer.dispose()
    }

    @Test
    fun `capabilities include required minimum texture size`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities

        assertTrue(
            caps.maxTextureSize >= 2048,
            "maxTextureSize must be at least 2048 (FR-024), got: ${caps.maxTextureSize}"
        )

        renderer.dispose()
    }

    @Test
    fun `capabilities include required vertex attributes`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities

        assertTrue(
            caps.maxVertexAttributes >= 16,
            "maxVertexAttributes must be at least 16 (FR-024), got: ${caps.maxVertexAttributes}"
        )

        renderer.dispose()
    }

    @Test
    fun `capabilities report device name`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities

        assertNotNull(
            caps.deviceName,
            "Device name must be reported (FR-024)"
        )

        assertTrue(
            caps.deviceName.isNotBlank(),
            "Device name must not be blank, got: '${caps.deviceName}'"
        )

        renderer.dispose()
    }

    @Test
    fun `capabilities report driver version`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities

        assertNotNull(
            caps.driverVersion,
            "Driver version must be reported (FR-024)"
        )

        assertTrue(
            caps.driverVersion.isNotBlank(),
            "Driver version must not be blank, got: '${caps.driverVersion}'"
        )

        renderer.dispose()
    }

    @Test
    fun `capabilities backend matches renderer backend`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities

        assertEquals(
            renderer.backend,
            caps.backend,
            "Capabilities backend must match renderer backend"
        )

        renderer.dispose()
    }

    @Test
    fun `WebGPU supports compute on capable hardware`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        if (renderer.backend == BackendType.WEBGPU) {
            val caps = renderer.capabilities

            // WebGPU typically supports compute shaders
            // (Note: This may vary by hardware, so we just log it)
            println("WebGPU compute support: ${caps.supportsCompute}")
        }

        renderer.dispose()
    }

    @Test
    fun `Vulkan reports accurate capabilities`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        if (renderer.backend == BackendType.VULKAN) {
            val caps = renderer.capabilities

            // Vulkan should report detailed capabilities
            assertTrue(
                caps.maxTextureSize >= 4096,
                "Vulkan typically supports at least 4K textures, got: ${caps.maxTextureSize}"
            )

            println("Vulkan capabilities:")
            println("  Device: ${caps.deviceName}")
            println("  Driver: ${caps.driverVersion}")
            println("  Max Texture: ${caps.maxTextureSize}")
            println("  Max Vertex Attributes: ${caps.maxVertexAttributes}")
            println("  Compute: ${caps.supportsCompute}")
            println("  Ray Tracing: ${caps.supportsRayTracing}")
            println("  Multisampling: ${caps.supportsMultisampling}")
        }

        renderer.dispose()
    }

    @Test
    fun `capabilities are deterministic`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        val caps1 = renderer.capabilities
        val caps2 = renderer.capabilities

        // Capabilities should be same object or have same values
        assertEquals(caps1.backend, caps2.backend)
        assertEquals(caps1.maxTextureSize, caps2.maxTextureSize)
        assertEquals(caps1.maxVertexAttributes, caps2.maxVertexAttributes)
        assertEquals(caps1.deviceName, caps2.deviceName)

        renderer.dispose()
    }

    @Test
    fun `WebGL fallback has lower capabilities`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()

        if (renderer.backend == BackendType.WEBGL) {
            val caps = renderer.capabilities

            // WebGL typically has lower limits than WebGPU/Vulkan
            assertFalse(
                caps.supportsCompute,
                "WebGL should not support compute shaders"
            )

            assertFalse(
                caps.supportsRayTracing,
                "WebGL should not support ray tracing"
            )

            // But should still meet minimum requirements
            assertTrue(
                caps.maxTextureSize >= 2048,
                "WebGL must still meet minimum texture size (2048)"
            )

            println("WebGL fallback capabilities:")
            println("  Max Texture: ${caps.maxTextureSize}")
            println("  Max Vertex Attributes: ${caps.maxVertexAttributes}")
        }

        renderer.dispose()
    }

    @Test
    fun `capability mismatch is detected before rendering`() = runTest {
        // This test verifies FR-024: detect mismatches BEFORE rendering attempts
        val surface = createTestSurface(800, 600)

        // Renderer creation either succeeds with valid capabilities
        // or fails immediately with capability mismatch error
        val result = RendererFactory.create(surface)

        if (result.isSuccess) {
            val renderer = result.getOrThrow()
            val caps = renderer.capabilities

            // If creation succeeded, capabilities must meet minimums
            assertTrue(caps.maxTextureSize >= 2048)
            assertTrue(caps.maxVertexAttributes >= 16)

            renderer.dispose()
        } else {
            // If creation failed, must be due to capability mismatch
            val exception = result.getOrNull()
            assertTrue(
                exception is RendererInitializationException,
                "Capability failures must throw RendererInitializationException"
            )
        }
    }
}
