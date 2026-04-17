package io.materia.examples.benchmarks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkMathTest {
    @Test
    fun percentileInterpolatesAcrossSortedValues() {
        val result = BenchmarkMath.percentile(listOf(10.0, 20.0, 30.0, 40.0), 95.0)
        assertEquals(38.5, result, absoluteTolerance = 0.001)
    }

    @Test
    fun aggregateCombinesRepeatsIntoSingleMatrixEntry() {
        val config = BenchmarkRunConfig(width = 1920, height = 1080, warmupFrames = 2, measuredFrames = 3, repeats = 2)
        val workload = BenchmarkDefaults.triangleWorkload(config)
        val environment = BenchmarkEnvironment(
            platform = "JVM",
            backend = "VULKAN",
            environmentLabel = "Linux / Java 22 / VULKAN / Test GPU",
            deviceName = "Test GPU",
            driverVersion = "test-driver",
            osName = "Linux",
            runtimeVersion = "22.0.2",
            notes = listOf("Hidden GLFW surface")
        )
        val captures = listOf(
            BenchmarkCapture(
                workload = workload,
                environment = environment,
                sample = BenchmarkSample(
                    repeatIndex = 1,
                    bootToFirstFrameMs = 40.0,
                    avgFps = 60.0,
                    p95FrameMs = 18.0,
                    peakHeapDeltaMb = 120.0,
                    memoryMetricKind = BenchmarkMemoryMetricKind.JVM_HEAP_DELTA_MB,
                    measuredFrameTimesMs = listOf(16.0, 17.0, 18.0),
                    notes = listOf("repeat-1")
                )
            ),
            BenchmarkCapture(
                workload = workload,
                environment = environment,
                sample = BenchmarkSample(
                    repeatIndex = 2,
                    bootToFirstFrameMs = 44.0,
                    avgFps = 55.0,
                    p95FrameMs = 20.0,
                    peakHeapDeltaMb = 128.0,
                    memoryMetricKind = BenchmarkMemoryMetricKind.JVM_HEAP_DELTA_MB,
                    measuredFrameTimesMs = listOf(17.0, 18.0, 20.0),
                    notes = listOf("repeat-2")
                )
            )
        )

        val aggregate = BenchmarkMath.aggregate(captures)

        assertEquals(42.0, aggregate.bootToFirstFrameMs, absoluteTolerance = 0.001)
        assertEquals(57.5, aggregate.avgFps, absoluteTolerance = 0.001)
        assertEquals(19.5, aggregate.p95FrameMs, absoluteTolerance = 0.001)
        assertEquals(128.0, aggregate.peakHeapDeltaMb ?: error("Expected peak heap delta"), absoluteTolerance = 0.001)
        assertEquals(BenchmarkMemoryMetricKind.JVM_HEAP_DELTA_MB, aggregate.memoryMetricKind)
        assertEquals(2, aggregate.samples.size)
        assertTrue(aggregate.notes.contains("Hidden GLFW surface"))
        assertTrue(aggregate.notes.contains("repeat-1"))
        assertTrue(aggregate.notes.contains("repeat-2"))
    }
}
