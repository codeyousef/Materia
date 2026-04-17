package io.materia.examples.benchmarks

object BenchmarkDefaults {
    val runConfig = BenchmarkRunConfig()
    const val methodologySummary: String =
        "1920x1080 output, 120 warmup frames, 600 measured frames, and 3 repeats per scene/platform. " +
            "Frame metrics use wall-clock timings around each render iteration. Heap values are deltas from the " +
            "platform-reported heap baseline captured before scene boot."

    fun triangleWorkload(config: BenchmarkRunConfig = runConfig): BenchmarkWorkload =
        BenchmarkWorkload(
            scene = "Triangle",
            workloadLabel = "Default triangle scene",
            width = config.width,
            height = config.height,
            warmupFrames = config.warmupFrames,
            measuredFrames = config.measuredFrames,
            repeats = config.repeats,
            notes = listOf("Single-scene baseline")
        )

    fun embeddingGalaxyWorkload(config: BenchmarkRunConfig = runConfig): BenchmarkWorkload =
        BenchmarkWorkload(
            scene = "Embedding Galaxy",
            workloadLabel = "20,000 points / Balanced",
            width = config.width,
            height = config.height,
            warmupFrames = config.warmupFrames,
            measuredFrames = config.measuredFrames,
            repeats = config.repeats,
            notes = listOf("Instanced point cloud", "Balanced quality")
        )

    fun forceGraphWorkload(config: BenchmarkRunConfig = runConfig): BenchmarkWorkload =
        BenchmarkWorkload(
            scene = "Force Graph",
            workloadLabel = "2,500 nodes / 7,500 edges / default mode",
            width = config.width,
            height = config.height,
            warmupFrames = config.warmupFrames,
            measuredFrames = config.measuredFrames,
            repeats = config.repeats,
            notes = listOf("Default baked dataset", "Default mode is TF-IDF")
        )
}
