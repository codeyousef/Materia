package io.materia.performance

import io.materia.renderer.RenderStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FramePacingTest {

    @Test
    fun smootherUsesFrameTimeAverageAndLatestRenderCounts() {
        val smoother = FrameStatsSmoother(windowSize = 3)
        smoother.recordFrame(0.010f)
        smoother.recordFrame(0.020f)
        val snapshot = smoother.recordFrame(
            0.030f,
            RenderStats(fps = 30.0, frameTime = 30.0, triangles = 120, drawCalls = 8)
        )

        assertEquals(50.0, snapshot.fps, 0.01)
        assertEquals(20.0, snapshot.frameTimeMs, 0.01)
        assertEquals(120, snapshot.triangles)
        assertEquals(8, snapshot.drawCalls)
        assertEquals(3, snapshot.sampleCount)
    }

    @Test
    fun smootherDropsOldestSamples() {
        val smoother = FrameStatsSmoother(windowSize = 2)
        smoother.recordFrame(0.010f)
        smoother.recordFrame(0.020f)
        val snapshot = smoother.recordFrame(0.040f)

        assertEquals(1000.0 / 30.0, snapshot.fps, 0.01)
    }

    @Test
    fun adaptiveResolutionUsesHysteresisAndBounds() {
        val controller = AdaptiveResolutionController(
            AdaptiveResolutionConfig(
                targetFps = 55.0,
                minimumScale = 0.75f,
                maximumScale = 1.25f,
                scaleStep = 0.1f,
                lowSampleCount = 2,
                highSampleCount = 3
            ),
            initialScale = 1.0f
        )

        assertNull(controller.record(30.0))
        assertEquals(0.9f, controller.record(30.0))
        repeat(20) {
            controller.record(30.0)
            controller.record(30.0)
        }
        assertEquals(0.75f, controller.scale)

        assertNull(controller.record(60.0))
        assertNull(controller.record(60.0))
        assertEquals(0.85f, controller.record(60.0))
    }

    @Test
    fun neutralSamplesResetPendingTrend() {
        val controller = AdaptiveResolutionController(initialScale = 1f)

        controller.record(30.0)
        controller.record(55.0)
        assertNull(controller.record(30.0))
        assertEquals(0.9f, controller.record(30.0))
    }
}
