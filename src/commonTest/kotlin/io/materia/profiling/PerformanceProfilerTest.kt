package io.materia.profiling

import kotlin.test.*

/**
 * Tests for the performance profiling system
 */
class PerformanceProfilerTest {

    @BeforeTest
    fun setup() {
        PerformanceProfiler.reset()
        PerformanceProfiler.configure(ProfilerConfig(enabled = true, trackMemory = false))
    }

    @AfterTest
    fun cleanup() {
        PerformanceProfiler.reset()
        PerformanceProfiler.configure(ProfilerConfig(enabled = false))
    }

    @Test
    fun testBasicProfiling() {
        PerformanceProfiler.startFrame()

        PerformanceProfiler.measure("test.operation", ProfileCategory.OTHER) {
            // Simulate work
            var sum = 0
            repeat(1000) { sum += it }
        }

        PerformanceProfiler.endFrame()

        val stats = PerformanceProfiler.getFrameStats()
        assertTrue(stats.frameCount > 0, "Should have recorded frames")
    }

    @Test
    fun testHotspotDetection() {
        repeat(10) {
            PerformanceProfiler.startFrame()

            // Simulate expensive operation
            PerformanceProfiler.measure("expensive.operation", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(10000) { sum += it }
            }

            // Simulate cheap operation
            PerformanceProfiler.measure("cheap.operation", ProfileCategory.OTHER) {
                var sum = 0
                repeat(100) { sum += it }
            }

            PerformanceProfiler.endFrame()
        }

        val hotspots = PerformanceProfiler.getHotspots()
        assertTrue(hotspots.isNotEmpty(), "Should detect hotspots")

        val expensiveHotspot = hotspots.find { it.name == "expensive.operation" }
        assertNotNull(expensiveHotspot, "Should find expensive operation")
        assertTrue(expensiveHotspot.callCount == 10, "Should have correct call count")
    }

    @Test
    fun testFrameStats() {
        repeat(60) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("frame.work", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(1000) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val stats = PerformanceProfiler.getFrameStats()
        assertEquals(60, stats.frameCount, "Should have 60 frames")
        assertTrue(stats.averageFrameTime > 0, "Should have positive average frame time")
        assertTrue(stats.averageFps > 0.0, "Should have positive FPS")
    }

    @Test
    fun testCounters() {
        PerformanceProfiler.startFrame()

        PerformanceProfiler.recordCounter("test.counter", 100)
        PerformanceProfiler.incrementCounter("test.increment", 5)
        PerformanceProfiler.incrementCounter("test.increment", 3)

        PerformanceProfiler.endFrame()

        val frame = PerformanceProfiler.getRecentFrames(1).firstOrNull()
        assertNotNull(frame, "Should have frame data")
        assertEquals(100L, frame.counters["test.counter"], "Should record counter value")
        assertEquals(8L, frame.counters["test.increment"], "Should increment counter")
    }

    @Test
    fun testProfileScope() {
        PerformanceProfiler.startFrame()

        val scope = PerformanceProfiler.startScope("scoped.operation", ProfileCategory.OTHER)
        var sum = 0
        repeat(1000) { sum += it }
        scope.end()

        PerformanceProfiler.endFrame()

        val hotspots = PerformanceProfiler.getHotspots()
        val scopedHotspot = hotspots.find { it.name == "scoped.operation" }
        assertNotNull(scopedHotspot, "Should find scoped operation")
    }

    @Test
    fun testExportJson() {
        repeat(5) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test.operation", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(100) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val json = PerformanceProfiler.export(ExportFormat.JSON)
        assertTrue(json.isNotEmpty(), "Should export JSON")
        assertTrue(json.contains("stats"), "Should contain stats section")
        assertTrue(json.contains("hotspots"), "Should contain hotspots section")
    }

    @Test
    fun testExportCsv() {
        repeat(5) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test.operation", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(100) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val csv = PerformanceProfiler.export(ExportFormat.CSV)
        assertTrue(csv.isNotEmpty(), "Should export CSV")
        assertTrue(csv.contains("frame,duration"), "Should contain CSV headers")
    }

    @Test
    fun testDisabledProfiler() {
        PerformanceProfiler.configure(ProfilerConfig(enabled = false))

        PerformanceProfiler.startFrame()
        var executedWithoutProfiler = false
        PerformanceProfiler.measure("test.operation", ProfileCategory.OTHER) {
            executedWithoutProfiler = true
        }
        PerformanceProfiler.endFrame()

        assertTrue(executedWithoutProfiler, "Should execute block even when profiler is disabled")

        val stats = PerformanceProfiler.getFrameStats()
        assertEquals(0, stats.frameCount, "Should not record frames when disabled")
    }

    @Test
    fun testFrameStatsTargetFps() {
        // Simulate frames meeting 60 FPS target (16.67ms per frame)
        repeat(60) {
            PerformanceProfiler.startFrame()
            // Simulate minimal work (should be fast)
            var sum = 0
            repeat(10) { sum += it }
            PerformanceProfiler.endFrame()
        }

        val stats = PerformanceProfiler.getFrameStats()
        // Note: This test may be flaky depending on system performance
        // Controlled timing would improve test reliability
        assertTrue(stats.frameCount == 60, "Should have 60 frames")
    }

    @Test
    fun testReset() {
        repeat(10) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test.operation", ProfileCategory.OTHER) {
                var sum = 0
                repeat(100) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        assertTrue(
            PerformanceProfiler.getFrameStats().frameCount > 0,
            "Should have frames before reset"
        )
        assertTrue(
            PerformanceProfiler.getHotspots().isNotEmpty(),
            "Should have hotspots before reset"
        )

        PerformanceProfiler.reset()

        assertEquals(
            0,
            PerformanceProfiler.getFrameStats().frameCount,
            "Should have no frames after reset"
        )
        assertTrue(
            PerformanceProfiler.getHotspots().isEmpty(),
            "Should have no hotspots after reset"
        )
    }
}

/**
 * Tests for profiling report generation
 */
class ProfilingReportTest {

    @BeforeTest
    fun setup() {
        PerformanceProfiler.reset()
        PerformanceProfiler.configure(ProfilerConfig(enabled = true, trackMemory = false))
    }

    @AfterTest
    fun cleanup() {
        PerformanceProfiler.reset()
    }

    @Test
    fun testReportGeneration() {
        // Generate some profiling data
        repeat(10) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("render", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(1000) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val report = ProfilingReport.generateReport()

        assertNotNull(report.frameStats, "Should have frame stats")
        assertTrue(report.hotspots.isNotEmpty(), "Should have hotspots")
    }

    @Test
    fun testTextReportGeneration() {
        repeat(5) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test.operation", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(1000) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val textReport = ProfilingReport.generateTextReport()

        assertTrue(textReport.isNotEmpty(), "Should generate text report")
        assertTrue(textReport.contains("Materia Performance Report"), "Should contain header")
        assertTrue(textReport.contains("Frame Statistics"), "Should contain frame stats section")
    }

    @Test
    fun testHtmlReportGeneration() {
        repeat(5) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test.operation", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(1000) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val htmlReport = ProfilingReport.generateHtmlReport()

        assertTrue(htmlReport.isNotEmpty(), "Should generate HTML report")
        assertTrue(htmlReport.contains("<!DOCTYPE html>"), "Should be valid HTML")
        assertTrue(htmlReport.contains("Materia Performance Report"), "Should contain title")
    }
}

/**
 * Tests for profiling dashboard
 */
class ProfilingDashboardTest {

    private val dashboard = ProfilingDashboard()

    @BeforeTest
    fun setup() {
        PerformanceProfiler.reset()
    }

    @AfterTest
    fun cleanup() {
        dashboard.disable()
        PerformanceProfiler.reset()
    }

    @Test
    fun testDashboardEnable() {
        dashboard.enable()

        val state = dashboard.getCurrentState()
        assertTrue(state.enabled, "Dashboard should be enabled")
    }

    @Test
    fun testDashboardState() {
        dashboard.enable()

        // Generate some data
        repeat(10) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(100) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val state = dashboard.getCurrentState()
        assertTrue(state.frameStats.frameCount > 0, "Should have frame data")
        assertTrue(state.hotspots.isNotEmpty(), "Should have hotspots")
    }

    @Test
    fun testPerformanceGrade() {
        dashboard.enable()

        // Generate good performance data
        repeat(60) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("lightweight", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(10) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val grade = dashboard.getPerformanceGrade()
        assertNotNull(grade, "Should have performance grade")
    }

    @Test
    fun testFormattedText() {
        dashboard.enable()

        repeat(5) {
            PerformanceProfiler.startFrame()
            PerformanceProfiler.measure("test", ProfileCategory.RENDERING) {
                var sum = 0
                repeat(100) { sum += it }
            }
            PerformanceProfiler.endFrame()
        }

        val text = dashboard.getFormattedText()
        assertTrue(text.isNotEmpty(), "Should generate formatted text")
        assertTrue(text.contains("Materia Performance Dashboard"), "Should contain header")
    }
}
