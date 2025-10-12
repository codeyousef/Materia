/**
 * T006: Contract Test - Renderer Lifecycle (FR-011)
 * Feature: 019-we-should-not
 *
 * Tests Renderer interface methods (initialize, render, resize, dispose).
 *
 * Requirements Tested:
 * - FR-011: Unified renderer interface
 *
 * EXPECTED: These tests MUST FAIL until Renderer interface is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import io.kreekt.core.scene.Scene
import io.kreekt.camera.PerspectiveCamera
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RendererLifecycleTest {

    private lateinit var renderer: Renderer
    private lateinit var surface: RenderSurface
    private lateinit var scene: Scene
    private lateinit var camera: PerspectiveCamera

    @BeforeTest
    fun setup() = runTest {
        surface = createTestSurface(width = 800, height = 600)
        scene = Scene()
        camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 800f / 600f,
            near = 0.1f,
            far = 1000.0f
        )
    }

    @AfterTest
    fun teardown() {
        if (::renderer.isInitialized) {
            renderer.dispose()
        }
    }

    @Test
    fun `renderer initializes successfully with valid config`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Should be in READY state after creation
        assertNotNull(
            renderer.capabilities,
            "Capabilities must be available after initialization"
        )

        assertTrue(
            renderer.capabilities.maxTextureSize >= 2048,
            "Max texture size must be at least 2048 (FR-024)"
        )
    }

    @Test
    fun `render accepts Scene and Camera`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Should not throw
        // assertDoesNotThrow removed - not in kotlin.test

        
            renderer.render(scene, camera)
        
    }

    @Test
    fun `render updates stats`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Render one frame
        renderer.render(scene, camera)

        val stats = renderer.stats
        assertNotNull(stats, "Stats must not be null after render")
        assertTrue(stats.fps >= 0, "FPS must be non-negative")
        assertTrue(stats.frameTime >= 0, "Frame time must be non-negative")
        assertTrue(stats.drawCalls >= 0, "Draw calls must be non-negative")
        assertTrue(stats.triangles >= 0, "Triangles must be non-negative")
    }

    @Test
    fun `getStats returns valid RenderStats`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        renderer.render(scene, camera)

        val stats = renderer.stats
        assertNotNull(stats, "Stats must not be null")

        // Stats should have valid values
        assertTrue(stats.fps >= 0, "FPS must be non-negative")
        assertTrue(stats.frameTime >= 0, "Frame time must be non-negative")
        assertTrue(stats.drawCalls >= 0, "Draw calls must be non-negative")
        assertTrue(stats.triangles >= 0, "Triangles must be non-negative")
        assertTrue(stats.timestamp > 0, "Timestamp must be set")
    }

    @Test
    fun `resize updates surface dimensions`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Should not throw
        // assertDoesNotThrow removed - not in kotlin.test

        
            renderer.resize(1024, 768)
        

        // Next render should work
        // assertDoesNotThrow removed - not in kotlin.test

        
            renderer.render(scene, camera)
        
    }

    @Test
    fun `dispose cleans up resources`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Should not throw
        // assertDoesNotThrow removed - not in kotlin.test

        
            renderer.dispose()
        

        // Subsequent operations should fail
        assertFails("Operations after dispose should fail") {
            renderer.render(scene, camera)
        }
    }

    @Test
    fun `renderer can render multiple frames`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        // Render 10 frames
        repeat(10) {
            // assertDoesNotThrow removed - not in kotlin.test

            
                renderer.render(scene, camera)
            
        }

        val stats = renderer.stats
        assertTrue(
            stats.fps > 0,
            "FPS should be positive after multiple frames"
        )
    }

    @Test
    fun `backend property matches detected backend`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        val detectedBackends = RendererFactory.detectAvailableBackends()
        assertTrue(
            detectedBackends.contains(renderer.backend),
            "Renderer backend must be one of the detected backends"
        )
    }

    @Test
    fun `capabilities reflect backend features`() = runTest {
        renderer = RendererFactory.create(surface).getOrThrow()

        val caps = renderer.capabilities
        assertEquals(
            renderer.backend,
            caps.backend,
            "Capabilities backend must match renderer backend"
        )

        assertNotNull(caps.deviceName, "Device name must be reported")
        assertNotNull(caps.driverVersion, "Driver version must be reported")
    }
}
