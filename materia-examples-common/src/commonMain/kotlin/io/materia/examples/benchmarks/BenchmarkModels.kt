package io.materia.examples.benchmarks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkRunConfig(
    @SerialName("width")
    val width: Int = 1920,
    @SerialName("height")
    val height: Int = 1080,
    @SerialName("warmup_frames")
    val warmupFrames: Int = 120,
    @SerialName("measured_frames")
    val measuredFrames: Int = 600,
    @SerialName("repeats")
    val repeats: Int = 3
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(warmupFrames >= 0) { "warmupFrames must be non-negative" }
        require(measuredFrames > 0) { "measuredFrames must be positive" }
        require(repeats > 0) { "repeats must be positive" }
    }
}

@Serializable
data class BenchmarkWorkload(
    @SerialName("scene")
    val scene: String,
    @SerialName("workload_label")
    val workloadLabel: String,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("warmup_frames")
    val warmupFrames: Int,
    @SerialName("measured_frames")
    val measuredFrames: Int,
    @SerialName("repeats")
    val repeats: Int,
    @SerialName("notes")
    val notes: List<String> = emptyList()
)

@Serializable
data class BenchmarkEnvironment(
    @SerialName("platform")
    val platform: String,
    @SerialName("backend")
    val backend: String,
    @SerialName("environment_label")
    val environmentLabel: String,
    @SerialName("device_name")
    val deviceName: String,
    @SerialName("driver_version")
    val driverVersion: String? = null,
    @SerialName("os_name")
    val osName: String? = null,
    @SerialName("runtime_version")
    val runtimeVersion: String? = null,
    @SerialName("browser_version")
    val browserVersion: String? = null,
    @SerialName("emulator_name")
    val emulatorName: String? = null,
    @SerialName("notes")
    val notes: List<String> = emptyList()
)

@Serializable
enum class BenchmarkMemoryMetricKind {
    JVM_HEAP_DELTA_MB,
    JS_HEAP_DELTA_MB,
    ANDROID_APP_HEAP_ESTIMATE_DELTA_MB,
    UNAVAILABLE
}

@Serializable
data class BenchmarkSample(
    @SerialName("repeat_index")
    val repeatIndex: Int,
    @SerialName("boot_to_first_frame_ms")
    val bootToFirstFrameMs: Double,
    @SerialName("avg_fps")
    val avgFps: Double,
    @SerialName("p95_frame_ms")
    val p95FrameMs: Double,
    @SerialName("peak_heap_delta_mb")
    val peakHeapDeltaMb: Double? = null,
    @SerialName("memory_metric_kind")
    val memoryMetricKind: BenchmarkMemoryMetricKind,
    @SerialName("measured_frame_times_ms")
    val measuredFrameTimesMs: List<Double>,
    @SerialName("notes")
    val notes: List<String> = emptyList()
)

@Serializable
data class BenchmarkCapture(
    @SerialName("workload")
    val workload: BenchmarkWorkload,
    @SerialName("environment")
    val environment: BenchmarkEnvironment,
    @SerialName("sample")
    val sample: BenchmarkSample
)

@Serializable
data class BenchmarkAggregate(
    @SerialName("workload")
    val workload: BenchmarkWorkload,
    @SerialName("environment")
    val environment: BenchmarkEnvironment,
    @SerialName("boot_to_first_frame_ms")
    val bootToFirstFrameMs: Double,
    @SerialName("avg_fps")
    val avgFps: Double,
    @SerialName("p95_frame_ms")
    val p95FrameMs: Double,
    @SerialName("peak_heap_delta_mb")
    val peakHeapDeltaMb: Double? = null,
    @SerialName("memory_metric_kind")
    val memoryMetricKind: BenchmarkMemoryMetricKind,
    @SerialName("samples")
    val samples: List<BenchmarkSample>,
    @SerialName("notes")
    val notes: List<String> = emptyList()
)

@Serializable
data class BenchmarkSnapshot(
    @SerialName("generated_at_iso_utc")
    val generatedAtIsoUtc: String,
    @SerialName("benchmark_version")
    val benchmarkVersion: Int = 1,
    @SerialName("methodology_summary")
    val methodologySummary: String,
    @SerialName("results")
    val results: List<BenchmarkAggregate>
)
