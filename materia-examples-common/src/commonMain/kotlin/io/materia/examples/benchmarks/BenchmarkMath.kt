package io.materia.examples.benchmarks

import kotlin.math.floor

object BenchmarkMath {
    fun percentile(values: List<Double>, percentile: Double): Double {
        require(percentile in 0.0..100.0) { "percentile must be within 0..100" }
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values.first()

        val sorted = values.sorted()
        val scaledIndex = (percentile / 100.0) * (sorted.lastIndex)
        val lowerIndex = floor(scaledIndex).toInt()
        val upperIndex = kotlin.math.ceil(scaledIndex).toInt()
        if (lowerIndex == upperIndex) return sorted[lowerIndex]

        val fraction = scaledIndex - lowerIndex
        val lower = sorted[lowerIndex]
        val upper = sorted[upperIndex]
        return lower + ((upper - lower) * fraction)
    }

    fun aggregate(captures: List<BenchmarkCapture>): BenchmarkAggregate {
        require(captures.isNotEmpty()) { "captures must not be empty" }

        val first = captures.first()
        require(captures.all { it.workload == first.workload }) {
            "all captures must use the same workload"
        }
        require(captures.all { it.environment == first.environment }) {
            "all captures must use the same environment"
        }
        val samples = captures.sortedBy { it.sample.repeatIndex }.map { it.sample }
        val flattenedFrameTimes = samples.flatMap { it.measuredFrameTimesMs }
        val peakHeap = samples.mapNotNull { it.peakHeapDeltaMb }.maxOrNull()
        val distinctMemoryKinds = samples.map { it.memoryMetricKind }.distinct()
        val memoryMetricKind = if (distinctMemoryKinds.size == 1) {
            distinctMemoryKinds.first()
        } else {
            BenchmarkMemoryMetricKind.UNAVAILABLE
        }

        return BenchmarkAggregate(
            workload = first.workload,
            environment = first.environment,
            bootToFirstFrameMs = samples.map { it.bootToFirstFrameMs }.average(),
            avgFps = samples.map { it.avgFps }.average(),
            p95FrameMs = percentile(flattenedFrameTimes, 95.0),
            peakHeapDeltaMb = peakHeap,
            memoryMetricKind = memoryMetricKind,
            samples = samples,
            notes = (
                first.workload.notes +
                    first.environment.notes +
                    samples.flatMap { it.notes }
                ).distinct()
        )
    }

    fun matrixKey(scene: String, platform: String): String = "$scene::$platform"
}
