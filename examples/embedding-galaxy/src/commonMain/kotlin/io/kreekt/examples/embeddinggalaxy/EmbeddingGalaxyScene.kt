package io.kreekt.examples.embeddinggalaxy

/**
 * Placeholder implementation that mimics the knobs the real Embedding Galaxy scene will expose.
 *
 * This keeps the example module compiling without pulling the unfinished renderer stack into the build.
 */
class EmbeddingGalaxyScene(
    private val config: Config = Config()
) {
    data class Config(
        val pointCount: Int = 20_000,
        val clusterCount: Int = 6,
        val seed: Long = 7L
    )

    data class Metrics(
        val frameTimeMs: Double,
        val averageClusterSeparation: Double
    )

    private var elapsedSeconds = 0f
    private var quality: Quality = Quality.Balanced

    enum class Quality { Performance, Balanced, Fidelity }

    fun setQuality(newQuality: Quality) {
        quality = newQuality
    }

    fun update(deltaTimeSeconds: Float) {
        elapsedSeconds += deltaTimeSeconds
    }

    fun snapshotMetrics(): Metrics = Metrics(
        frameTimeMs = when (quality) {
            Quality.Performance -> 8.0
            Quality.Balanced -> 14.0
            Quality.Fidelity -> 22.0
        },
        averageClusterSeparation = config.clusterCount * 1.5
    )

    fun status(): String = buildString {
        append("Embedding Galaxy placeholder\n")
        append("Points: ${config.pointCount}\n")
        append("Clusters: ${config.clusterCount}\n")
        append("Quality: $quality\n")
        val secondsRounded = kotlin.math.round(elapsedSeconds * 100f) / 100f
        append("Elapsed: ${secondsRounded}s")
    }
}
