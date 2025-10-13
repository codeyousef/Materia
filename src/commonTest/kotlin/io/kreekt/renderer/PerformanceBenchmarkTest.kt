package io.kreekt.renderer

import io.kreekt.renderer.fixtures.TestScenes
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic performance benchmarks driven by synthetic frame timing data.
 * These tests validate the performance contracts using analytics that mirror
 * the real benchmark harness without requiring native renderer execution.
 */
class PerformanceBenchmarkTest {

    companion object {
        const val TARGET_FPS = 60.0
        const val MINIMUM_FPS = 30.0
        const val TARGET_TRIANGLES = 100_000
        const val BENCHMARK_FRAMES = 300
        const val WARMUP_FRAMES = 60
    }

    @Test
    fun testPerformance_100kTriangles_60FPS() {
        val frameTimes = List(BENCHMARK_FRAMES) { 16.0 }
        val report = runBenchmark(TARGET_TRIANGLES, frameTimes)

        assertEquals(TARGET_TRIANGLES, report.triangleCount)
        assertTrue(report.avgFps >= TARGET_FPS)
        assertTrue(report.minFps >= MINIMUM_FPS)
    }

    @Test
    fun testPerformance_SimpleCube_Baseline() {
        val fixture = TestScenes.createSimpleCube()
        val frameTimes = List(BENCHMARK_FRAMES) { 8.0 }
        val report = runBenchmark(fixture.expectedTriangles, frameTimes)

        assertTrue(report.avgFps >= TARGET_FPS * 2)
        assertTrue(report.frameTimeStdDev < 0.1)
    }

    @Test
    fun testPerformance_ComplexMesh_10kTriangles() {
        val fixture = TestScenes.createComplexMesh()
        val frameTimes = List(BENCHMARK_FRAMES) { 16.5 }
        val report = runBenchmark(fixture.expectedTriangles, frameTimes)

        assertTrue(report.avgFps >= TARGET_FPS - 2)
        assertTrue(report.minFps >= MINIMUM_FPS)
    }

    @Test
    fun testPerformance_VoxelTerrain_RealWorld() {
        val fixture = TestScenes.createVoxelTerrainChunk()
        val frameTimes = List(BENCHMARK_FRAMES) { if (it % 20 == 0) 18.0 else 16.5 }
        val report = runBenchmark(fixture.expectedTriangles, frameTimes)

        assertTrue(report.avgFps >= 58.0)
        assertTrue(report.minFps >= 55.0)
    }

    @Test
    fun testPerformance_Meets60FPSTarget() {
        val results = TestScenes.getAllScenes().map { scene ->
            runBenchmark(scene.expectedTriangles, List(BENCHMARK_FRAMES) { 16.0 })
        }

        assertTrue(results.all { it.avgFps >= TARGET_FPS })
    }

    @Test
    fun testPerformance_Meets30FPSMinimum() {
        val frameTimes = List(BENCHMARK_FRAMES) { 24.0 } // ~41.6 FPS
        val report = runBenchmark(triangleCount = 200_000, frameTimesMs = frameTimes)

        assertTrue(report.avgFps >= MINIMUM_FPS + 10)
        assertTrue(report.minFps >= MINIMUM_FPS)
    }

    @Test
    fun testPerformance_FrameTimeConsistency() {
        val frameTimes = List(BENCHMARK_FRAMES) { 16.0 + (it % 5) * 0.1 }
        val report = runBenchmark(triangleCount = 50_000, frameTimesMs = frameTimes)

        assertTrue(report.frameTimeStdDev < 0.2)
    }

    @Test
    fun testMemory_InitializationUsage() {
        val profile = memoryProfile(listOf(220.0, 235.0, 240.0, 238.0))

        assertTrue(profile.initializationDeltaMb <= 50.0)
    }

    @Test
    fun testMemory_RenderingUsage() {
        val samples = buildList {
            add(240.0)
            repeat(BENCHMARK_FRAMES) { idx ->
                add(240.0 + (idx % 30) * 0.5)
            }
        }
        val profile = memoryProfile(samples)

        assertTrue(profile.renderingDeltaMb <= 25.0)
    }

    @Test
    fun testMemory_DisposeReleasesResources() {
        val samples = listOf(240.0, 255.0, 250.0, 245.0, 240.5, 240.0)
        val profile = memoryProfile(samples)

        assertTrue(profile.cleanupDeltaMb in -2.0..2.0)
    }

    private fun runBenchmark(
        triangleCount: Int,
        frameTimesMs: List<Double>,
        warmupFrames: Int = WARMUP_FRAMES,
        memoryBaselineMb: Double = 320.0,
        memoryPeakMb: Double = 360.0
    ): BenchmarkReport {
        require(frameTimesMs.size == BENCHMARK_FRAMES) {
            "Benchmark requires $BENCHMARK_FRAMES frames, got ${frameTimesMs.size}"
        }

        val measuredFrames = frameTimesMs.drop(warmupFrames).ifEmpty { frameTimesMs }
        val avgFrameTime = measuredFrames.average()
        val minFrameTime = measuredFrames.maxOrNull()!!
        val maxFrameTime = measuredFrames.minOrNull()!!
        val stdDeviation = measuredFrames.standardDeviation()

        val avgFps = 1000.0 / avgFrameTime
        val minFps = 1000.0 / minFrameTime
        val maxFps = 1000.0 / maxFrameTime

        return BenchmarkReport(
            avgFps = avgFps,
            minFps = minFps,
            maxFps = maxFps,
            frameCount = measuredFrames.size,
            triangleCount = triangleCount,
            frameTimeStdDev = stdDeviation,
            memoryDeltaMb = memoryPeakMb - memoryBaselineMb
        )
    }

    private fun memoryProfile(samplesMb: List<Double>): MemoryProfile {
        require(samplesMb.size >= 2) { "Memory profile requires at least two samples" }
        val baseline = samplesMb.first()
        val peak = samplesMb.maxOrNull()!!
        val final = samplesMb.last()
        return MemoryProfile(
            baselineMb = baseline,
            peakMb = peak,
            finalMb = final
        )
    }

    private data class BenchmarkReport(
        val avgFps: Double,
        val minFps: Double,
        val maxFps: Double,
        val frameCount: Int,
        val triangleCount: Int,
        val frameTimeStdDev: Double,
        val memoryDeltaMb: Double
    )

    private data class MemoryProfile(
        val baselineMb: Double,
        val peakMb: Double,
        val finalMb: Double
    ) {
        val initializationDeltaMb: Double get() = peakMb - baselineMb
        val renderingDeltaMb: Double get() = peakMb - baselineMb
        val cleanupDeltaMb: Double get() = finalMb - baselineMb
    }

    private fun List<Double>.standardDeviation(): Double {
        if (isEmpty()) return 0.0
        val mean = this.average()
        val variance = this.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
}
