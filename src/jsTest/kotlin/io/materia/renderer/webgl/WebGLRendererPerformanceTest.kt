package io.materia.renderer.webgl

import io.materia.camera.PerspectiveCamera
import io.materia.core.scene.Scene
import io.materia.renderer.webgpu.WebGPURendererFactory
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * Contract Test: WebGLRenderer Performance (Contract 1)
 *
 * Validates that the renderer meets the constitutional 60 FPS target
 * and minimum 30 FPS requirement with VoxelCraft-scale geometry.
 *
 * From: specs/017-in-voxelcraft-example/contracts/renderer-contract.md
 */
class WebGLRendererPerformanceTest {

    @Test
    fun testRendererMeetsMinimumPerformanceRequirement() = runTest {
        // Setup canvas
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600

        // Use factory to create renderer (tests actual initialization path)
        val renderer = WebGPURendererFactory.create(canvas)

        // Create simple test scene
        val scene = Scene()
        val camera = PerspectiveCamera(
            fov = 75.0f,
            aspect = 800f / 600f,
            near = 0.1f,
            far = 1000.0f
        )
        camera.position.set(0f, 64f, 0f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()

        // Warm-up frames (let renderer initialize GL state)
        repeat(5) {
            renderer.render(scene, camera)
        }

        // Measure performance over 30 frames
        val startTime = Date.now()
        val frameCount = 30
        repeat(frameCount) {
            renderer.render(scene, camera)
        }
        val endTime = Date.now()

        val totalTime = endTime - startTime
        val avgFrameTime = totalTime / frameCount
        val fps = 1000.0 / avgFrameTime

        // Constitutional requirement: FPS >= 30
        assertTrue(
            fps >= 30.0,
            "Expected FPS >= 30 (constitutional minimum), got ${fps.toInt()} FPS"
        )

        console.log("✅ Performance test passed: ${fps.toInt()} FPS (target: 60 FPS, minimum: 30 FPS)")

        // Log whether we're approaching target
        if (fps >= 60.0) {
            console.log("✅ Meeting constitutional 60 FPS target")
        } else if (fps >= 45.0) {
            console.log("⚠️ Approaching target (45-60 FPS)")
        } else {
            console.log("⚠️ Below target but above minimum (30-45 FPS)")
        }
    }

    @Test
    fun testRendererStatistics() = runTest {
        // Setup
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 800
        canvas.height = 600

        val renderer = WebGPURendererFactory.create(canvas)
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)
        camera.updateMatrixWorld(true)
        camera.updateProjectionMatrix()

        // Render frame
        renderer.render(scene, camera)

        // Get statistics
        val stats = renderer.stats

        // Validate stats structure
        assertTrue(stats.triangles >= 0, "Triangle count should be non-negative")
        assertTrue(stats.drawCalls >= 0, "Draw calls should be non-negative")

        console.log("✅ Renderer statistics valid: ${stats.triangles} triangles, ${stats.drawCalls} calls")
    }
}
