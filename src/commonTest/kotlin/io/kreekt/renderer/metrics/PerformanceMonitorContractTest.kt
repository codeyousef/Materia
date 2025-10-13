package io.kreekt.renderer.metrics

import io.kreekt.renderer.backend.BackendId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceMonitorContractTest {

    private class TestPerformanceMonitor : AbstractPerformanceMonitor() {
        var now: Long = 0

        override fun getCurrentTimeMs(): Long = now

        fun advance(ms: Long) {
            now += ms
        }

        fun recordFrame(frameTimeMs: Double, backendId: BackendId = BackendId.VULKAN) {
            recordFrameMetrics(
                FrameMetrics(
                    backendId = backendId,
                    frameTimeMs = frameTimeMs,
                    gpuTimeMs = frameTimeMs * 0.6,
                    cpuTimeMs = frameTimeMs * 0.4,
                    timestamp = now
                )
            )
            advance(frameTimeMs.toLong().coerceAtLeast(1))
        }
    }

    @Test
    fun testInitializationBudget_ExceedsThreshold() {
        val monitor = TestPerformanceMonitor()
        monitor.beginInitializationTrace(BackendId.VULKAN)
        monitor.advance(3_250)

        val stats = monitor.endInitializationTrace(BackendId.VULKAN)

        assertEquals(3_250, stats.initTimeMs)
        assertFalse(stats.withinBudget, "Initialization exceeding budget should be flagged")
    }

    @Test
    fun testInitializationBudget_WithinThreshold() {
        val monitor = TestPerformanceMonitor()
        monitor.beginInitializationTrace(BackendId.WEBGPU)
        monitor.advance(1_250)

        val stats = monitor.endInitializationTrace(BackendId.WEBGPU)

        assertEquals(1_250, stats.initTimeMs)
        assertTrue(stats.withinBudget)
    }

    @Test
    fun testFrameWindowAverage_MeetsBudget() {
        val monitor = TestPerformanceMonitor()
        repeat(120) {
            monitor.recordFrame(frameTimeMs = 16.0)
        }

        val assessment = monitor.evaluateBudget(FrameWindow.DEFAULT)

        assertEquals(62.5, assessment.avgFps, 0.1)
        assertEquals(62.5, assessment.minFps, 0.1)
        assertTrue(assessment.withinBudget)
    }

    @Test
    fun testFrameWindowDrop_BelowMinimum() {
        val monitor = TestPerformanceMonitor()
        repeat(119) {
            monitor.recordFrame(frameTimeMs = 16.0)
        }
        monitor.recordFrame(frameTimeMs = 45.0) // ~22 FPS frame

        val assessment = monitor.evaluateBudget(FrameWindow.DEFAULT)

        assertFalse(assessment.withinBudget)
        assertTrue(assessment.minFps < 30.0)
    }

    @Test
    fun testRollingWindow_120Frames() {
        val monitor = TestPerformanceMonitor()

        repeat(30) {
            monitor.recordFrame(frameTimeMs = 33.0)
        }
        repeat(150) {
            monitor.recordFrame(frameTimeMs = 16.0)
        }

        val assessment = monitor.evaluateBudget(FrameWindow.DEFAULT)

        assertEquals(120, assessment.frameCount)
        assertEquals(62.5, assessment.avgFps, 0.1)
    }

    @Test
    fun testGpuTimingQueries_RecordsMetrics() {
        val monitor = TestPerformanceMonitor()
        monitor.recordFrame(frameTimeMs = 18.0)
        monitor.recordFrame(frameTimeMs = 20.0)

        val assessment = monitor.evaluateBudget(FrameWindow(size = 2))

        assertEquals(2, assessment.frameCount)
        assertEquals(52.6, assessment.avgFps, 0.5)
    }

    @Test
    fun testPerformanceDegradation_EmitsEvent() {
        val monitor = TestPerformanceMonitor()
        repeat(60) {
            monitor.recordFrame(frameTimeMs = 20.0) // 50 FPS
        }

        val assessment = monitor.evaluateBudget(FrameWindow(size = 60))

        assertFalse(assessment.withinBudget)
        assertTrue((assessment.notes ?: "").contains("Performance below budget"))
    }

    @Test
    fun testCrossPlatformTimers_ConsistentMeasurements() {
        val monitor = TestPerformanceMonitor()
        repeat(240) {
            monitor.recordFrame(frameTimeMs = 16.0, backendId = BackendId.WEBGPU)
        }

        val assessment = monitor.evaluateBudget(FrameWindow(size = 120))

        assertEquals(120, assessment.frameCount)
        assertEquals(62.5, assessment.avgFps, 0.1)
    }
}
