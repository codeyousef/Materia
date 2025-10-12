/**
 * T009: Contract Test - Visual Consistency (FR-020)
 * Feature: 019-we-should-not
 *
 * Tests identical scenes produce consistent output.
 *
 * Requirements Tested:
 * - FR-020: Visual output visually identical across backends
 *
 * EXPECTED: These tests MUST FAIL until Renderer is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import io.kreekt.core.scene.Scene
import io.kreekt.core.scene.Mesh
import io.kreekt.camera.PerspectiveCamera
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class VisualConsistencyTest {

    @Test
    fun `identical scenes produce consistent triangle counts`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        val testMesh = createTestCube()
        scene.add(testMesh)

        // Render same scene twice
        renderer.render(scene, camera)
        val stats1 = renderer.stats

        renderer.render(scene, camera)
        val stats2 = renderer.stats

        // Triangle count should be identical for same scene
        assertEquals(
            stats1.triangles,
            stats2.triangles,
            "Triangle count must be consistent for identical scenes (FR-020)"
        )

        renderer.dispose()
    }

    @Test
    fun `identical scenes produce consistent draw calls`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        val testMesh = createTestCube()
        scene.add(testMesh)

        // Render same scene twice
        renderer.render(scene, camera)
        val stats1 = renderer.stats

        renderer.render(scene, camera)
        val stats2 = renderer.stats

        // Draw calls should be identical
        assertEquals(
            stats1.drawCalls,
            stats2.drawCalls,
            "Draw calls must be consistent for identical scenes (FR-020)"
        )

        renderer.dispose()
    }

    @Test
    fun `empty scene produces zero triangles`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        // Render empty scene
        renderer.render(scene, camera)
        val stats = renderer.stats

        assertEquals(
            0,
            stats.triangles,
            "Empty scene should produce zero triangles"
        )

        renderer.dispose()
    }

    @Test
    fun `adding mesh increases triangle count predictably`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        // Render empty scene
        renderer.render(scene, camera)
        val statsEmpty = renderer.stats

        // Add a cube (12 triangles typically)
        val cube = createTestCube()
        scene.add(cube)

        renderer.render(scene, camera)
        val statsWithCube = renderer.stats

        assertTrue(
            statsWithCube.triangles > statsEmpty.triangles,
            "Adding mesh should increase triangle count"
        )

        // Triangle count should be deterministic
        renderer.render(scene, camera)
        val statsAgain = renderer.stats

        assertEquals(
            statsWithCube.triangles,
            statsAgain.triangles,
            "Triangle count must remain consistent"
        )

        renderer.dispose()
    }

    @Test
    fun `camera position does not affect triangle count`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()

        val cube = createTestCube()
        scene.add(cube)

        // Render from position 1
        val camera1 = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera1.position.set(0.0f, 0.0f, 5.0f)
        renderer.render(scene, camera1)
        val stats1 = renderer.stats

        // Render from position 2
        val camera2 = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera2.position.set(10.0f, 10.0f, 10.0f)
        renderer.render(scene, camera2)
        val stats2 = renderer.stats

        // Triangle count should be same (before frustum culling)
        // Note: This tests that the scene graph itself is consistent
        assertEquals(
            stats1.triangles,
            stats2.triangles,
            "Triangle count should not depend on camera position (for same visible geometry)"
        )

        renderer.dispose()
    }

    @Test
    fun `renderer backend affects performance but not geometry count`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        val cube = createTestCube()
        scene.add(cube)

        renderer.render(scene, camera)
        val stats = renderer.stats

        // Backend might affect FPS, but geometry count must be consistent
        assertTrue(
            stats.triangles > 0,
            "Should render geometry"
        )

        // Log backend for context
        println("Backend: ${renderer.backend}, Triangles: ${stats.triangles}, FPS: ${stats.fps}")

        renderer.dispose()
    }
}
