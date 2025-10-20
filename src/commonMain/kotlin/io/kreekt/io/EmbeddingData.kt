package io.kreekt.io

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Serializable representation of an embedding dataset.
 * Mirrors the data style used by search indexing models (see SearchIndexer data classes)
 * with explicit metadata and statistics blocks so examples can stream or persist
 * high-dimensional vectors consistently across platforms.
 */
@Serializable
data class EmbeddingData(
    val metadata: EmbeddingMetadata = EmbeddingMetadata(),
    val statistics: EmbeddingStatistics,
    val points: List<EmbeddingPoint>
) {
    val dimension: Int
        get() = statistics.dimension

    init {
        require(statistics.dimension > 0) { "Embedding dimension must be positive" }
        require(points.isNotEmpty()) { "Embedding dataset must contain at least one point" }
        require(points.all { it.vector.size == statistics.dimension }) {
            "All points must match embedding dimension ${statistics.dimension}"
        }
    }
}

@Serializable
data class EmbeddingMetadata(
    val source: String = "synthetic",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val generator: String = "kreekt-examples",
    @SerialName("created_at")
    val createdAtEpochMillis: Long = 0L
)

@Serializable
data class EmbeddingStatistics(
    @SerialName("point_count")
    val pointCount: Int,
    val dimension: Int,
    @SerialName("cluster_count")
    val clusterCount: Int,
    @SerialName("mean_norm")
    val meanNorm: Float,
    @SerialName("max_norm")
    val maxNorm: Float,
    @SerialName("min_norm")
    val minNorm: Float
) {
    init {
        require(pointCount > 0) { "Embedding statistics require positive point count" }
        require(dimension > 0) { "Embedding statistics require positive dimension" }
        require(clusterCount > 0) { "Embedding statistics require positive cluster count" }
    }
}

@Serializable
data class EmbeddingPoint(
    val id: String,
    val vector: List<Float>,
    val cluster: Int,
    val position: EmbeddingPosition = EmbeddingPosition.EMPTY,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "EmbeddingPoint id cannot be blank" }
        require(cluster >= 0) { "EmbeddingPoint cluster index must be non-negative" }
    }
}

@Serializable
data class EmbeddingPosition(
    val x: Float,
    val y: Float,
    val z: Float
) {
    companion object {
        val EMPTY = EmbeddingPosition(0f, 0f, 0f)
    }
}

/**
 * Convenience payload that pairs a query vector with the ids that matched a threshold.
 * Useful for driving highlight animations without re-running the similarity search each frame.
 */
@Serializable
data class EmbeddingQueryResult(
    val pointId: String,
    val similarityThreshold: Float,
    val similarPointIds: List<String>
)

/**
 * Utility container for normalized vectors to avoid repeated allocations while animating highlights.
 */
data class NormalizedVector(
    val components: FloatArray
) {
    init {
        require(components.isNotEmpty()) { "Normalized vectors must contain data" }
    }

    @Transient
    val dimension: Int = components.size
}
