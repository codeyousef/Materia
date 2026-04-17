package io.materia.examples.benchmarks

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BenchmarkSerializationTest {
    @Test
    fun snapshotRoundTripsThroughJsonWithPublishedMetricNames() {
        val snapshot = BenchmarkSnapshot(
            generatedAtIsoUtc = "2026-04-17T00:00:00Z",
            methodologySummary = BenchmarkDefaults.methodologySummary,
            results = listOf(
                BenchmarkAggregate(
                    workload = BenchmarkDefaults.forceGraphWorkload(),
                    environment = BenchmarkEnvironment(
                        platform = "Android",
                        backend = "VULKAN",
                        environmentLabel = "Pixel_9_Pro / VULKAN / emulator",
                        deviceName = "sdk_gphone64_x86_64",
                        emulatorName = "Pixel_9_Pro"
                    ),
                    bootToFirstFrameMs = 250.0,
                    avgFps = 58.0,
                    p95FrameMs = 19.2,
                    peakHeapDeltaMb = 96.5,
                    memoryMetricKind = BenchmarkMemoryMetricKind.ANDROID_APP_HEAP_ESTIMATE_DELTA_MB,
                    samples = listOf(
                        BenchmarkSample(
                            repeatIndex = 1,
                            bootToFirstFrameMs = 250.0,
                            avgFps = 58.0,
                            p95FrameMs = 19.2,
                            peakHeapDeltaMb = 96.5,
                            memoryMetricKind = BenchmarkMemoryMetricKind.ANDROID_APP_HEAP_ESTIMATE_DELTA_MB,
                            measuredFrameTimesMs = listOf(16.5, 17.0, 19.2)
                        )
                    )
                )
            )
        )

        val payload = BenchmarkJson.codec.encodeToString(snapshot)
        val decoded = BenchmarkJson.codec.decodeFromString<BenchmarkSnapshot>(payload)

        assertTrue(payload.contains("\"boot_to_first_frame_ms\""))
        assertTrue(payload.contains("\"avg_fps\""))
        assertTrue(payload.contains("\"p95_frame_ms\""))
        assertTrue(payload.contains("\"peak_heap_delta_mb\""))
        assertTrue(payload.contains("\"workload_label\""))
        assertTrue(payload.contains("\"environment_label\""))
        assertEquals(snapshot, decoded)
    }

    @Test
    fun markdownRendersExpectedTableRowsFromSnapshot() {
        val snapshot = BenchmarkSnapshot(
            generatedAtIsoUtc = "2026-04-17T00:00:00Z",
            methodologySummary = BenchmarkDefaults.methodologySummary,
            results = listOf(
                BenchmarkAggregate(
                    workload = BenchmarkDefaults.triangleWorkload(),
                    environment = BenchmarkEnvironment(
                        platform = "JVM",
                        backend = "VULKAN",
                        environmentLabel = "Linux / Java 22 / VULKAN / Test GPU",
                        deviceName = "Test GPU",
                        osName = "Linux",
                        runtimeVersion = "22.0.2"
                    ),
                    bootToFirstFrameMs = 42.5,
                    avgFps = 120.0,
                    p95FrameMs = 9.2,
                    peakHeapDeltaMb = 32.1,
                    memoryMetricKind = BenchmarkMemoryMetricKind.JVM_HEAP_DELTA_MB,
                    samples = emptyList(),
                    notes = listOf("Hidden GLFW surface")
                )
            )
        )

        val markdown = BenchmarkMarkdown.renderReadmeSection(snapshot)

        assertTrue(markdown.contains("| Triangle | JVM | Default triangle scene |"))
        assertTrue(markdown.contains("42.5 ms"))
        assertTrue(markdown.contains("32.1 MB"))
        assertTrue(markdown.contains("Synthetic contract tests"))
    }
}
