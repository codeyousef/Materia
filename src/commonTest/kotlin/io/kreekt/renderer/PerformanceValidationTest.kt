/**
 * T007: Contract Test - Performance Validation (FR-019)
 * Feature: 019-we-should-not
 *
 * Tests renderer maintains 30 FPS minimum, targets 60 FPS.
 *
 * Requirements Tested:
 * - FR-019: 60 FPS target, 30 FPS minimum
 *
 * EXPECTED: These tests MUST FAIL until Renderer is implemented (TDD Red Phase)
 */

package io.kreekt.renderer

import io.kreekt.core.scene.Scene
import io.kreekt.core.scene.Mesh
import io.kreekt.camera.PerspectiveCamera
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PerformanceValidationTest {

    @Test
    fun `renderer maintains minimum 30 FPS with simple scene`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        // Add simple test geometry
        val testCube = createTestCube()
        scene.add(testCube)

        // Warmup: 120 frames (as per research.md)
        repeat(120) {
            renderer.render(scene, camera)
        }

        // Measure: 60 frames
        val startTime = currentTimeMillis()
        repeat(60) {
            renderer.render(scene, camera)
        }
        val elapsed = currentTimeMillis() - startTime

        val averageFps = 60000.0 / elapsed
        val stats = renderer.stats

        // Assert 30 FPS minimum (FR-019: 30 FPS minimum acceptable)
        assertTrue(
            averageFps >= 30.0,
            "Renderer must maintain at least 30 FPS (got ${formatDouble(averageFps, 1)} FPS)"
        )

        // Log performance info
        println("✅ Performance: ${formatDouble(averageFps, 1)} FPS, ${formatDouble(stats.frameTime, 2)}ms/frame")

        // Target 60 FPS (may not always achieve, but should be close on primary backends)
        if (averageFps < 50.0 && renderer.backend.isPrimary) {
            println(
                "⚠️ Performance warning: ${
                    formatDouble(
                        averageFps,
                        1
                    )
                } FPS on primary backend ${renderer.backend} (target: 60 FPS)"
            )
        }

        renderer.dispose()
    }

    @Test
    fun `frame time stays within acceptable bounds`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        // Warmup
        repeat(120) {
            renderer.render(scene, camera)
        }

        // Measure frame times
        val frameTimes = mutableListOf<Double>()
        repeat(60) {
            val frameStart = currentTimeMillis()
            renderer.render(scene, camera)
            val frameEnd = currentTimeMillis()
            frameTimes.add((frameEnd - frameStart).toDouble())
        }

        val avgFrameTime = frameTimes.average()
        val maxFrameTime = frameTimes.maxOrNull() ?: 0.0

        // 30 FPS = 33.33ms per frame max
        assertTrue(
            avgFrameTime <= 33.33,
            "Average frame time must be ≤33.33ms for 30 FPS (got ${formatDouble(avgFrameTime, 2)}ms)"
        )

        // Allow occasional spikes, but not too many
        val slowFrames = frameTimes.count { it > 33.33 }
        assertTrue(
            slowFrames < 10,
            "Too many slow frames: $slowFrames out of 60 exceeded 33.33ms"
        )

        renderer.dispose()
    }

    @Test
    fun `stats track performance metrics accurately`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        // Warmup
        repeat(120) {
            renderer.render(scene, camera)
        }

        // Render and check stats
        renderer.render(scene, camera)
        val stats = renderer.stats

        assertTrue(stats.fps > 0, "FPS must be tracked")
        assertTrue(stats.frameTime > 0, "Frame time must be tracked")
        assertTrue(stats.timestamp > 0.0, "Timestamp must be set")

        // Stats should be reasonable
        assertTrue(stats.fps <= 1000, "FPS should be realistic (≤1000)")
        assertTrue(stats.frameTime < 1000, "Frame time should be realistic (<1000ms)")
    }

    @Test
    fun `performance is consistent across frames`() = runTest {
        val surface = createTestSurface(800, 600)
        val renderer = RendererFactory.create(surface).getOrThrow()
        val scene = Scene()
        val camera = PerspectiveCamera(75.0f, 800f / 600f, 0.1f, 1000.0f)

        val testCube = createTestCube()
        scene.add(testCube)

        // Warmup
        repeat(120) {
            renderer.render(scene, camera)
        }

        // Measure multiple batches
        val batchFps = mutableListOf<Double>()
        repeat(3) { batchIndex ->
            val startTime = currentTimeMillis()
            repeat(60) {
                renderer.render(scene, camera)
            }
            val elapsed = currentTimeMillis() - startTime
            val fps = 60000.0 / elapsed
            batchFps.add(fps)
        }

        // Calculate variance
        val avgFps = batchFps.average()
        val maxDeviation = batchFps.maxOfOrNull { kotlin.math.abs(it - avgFps) } ?: 0.0

        // Performance should be relatively stable (within 20% variance)
        val allowedDeviation = avgFps * 0.2
        assertTrue(
            maxDeviation <= allowedDeviation,
            "Performance should be consistent: max deviation ${formatDouble(maxDeviation, 1)} FPS " +
                    "exceeds ${formatDouble(allowedDeviation, 1)} FPS (20% of avg ${formatDouble(avgFps, 1)} FPS)"
        )

        renderer.dispose()
    }
}
