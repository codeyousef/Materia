package io.materia.examples.benchmarks

class BenchmarkRecorder(
    private val repeatIndex: Int,
    private val baselineHeapMb: Double?,
    private val memoryMetricKind: BenchmarkMemoryMetricKind,
    private val notes: List<String> = emptyList()
) {
    private val measuredFrameTimesMs = mutableListOf<Double>()
    private var peakHeapDeltaMb: Double? = null

    fun observeHeapUsage(heapUsageMb: Double?) {
        if (heapUsageMb != null && baselineHeapMb != null) {
            val delta = (heapUsageMb - baselineHeapMb).coerceAtLeast(0.0)
            peakHeapDeltaMb = maxOf(peakHeapDeltaMb ?: 0.0, delta)
        }
    }

    fun recordFrame(frameTimeMs: Double, heapUsageMb: Double?) {
        measuredFrameTimesMs += frameTimeMs
        observeHeapUsage(heapUsageMb)
    }

    fun build(bootToFirstFrameMs: Double): BenchmarkSample {
        require(measuredFrameTimesMs.isNotEmpty()) { "At least one measured frame is required" }

        val averageFrameMs = measuredFrameTimesMs.average()
        return BenchmarkSample(
            repeatIndex = repeatIndex,
            bootToFirstFrameMs = bootToFirstFrameMs,
            avgFps = if (averageFrameMs > 0.0) 1000.0 / averageFrameMs else 0.0,
            p95FrameMs = BenchmarkMath.percentile(measuredFrameTimesMs, 95.0),
            peakHeapDeltaMb = peakHeapDeltaMb,
            memoryMetricKind = memoryMetricKind,
            measuredFrameTimesMs = measuredFrameTimesMs.toList(),
            notes = notes
        )
    }
}
