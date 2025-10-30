package io.materia.io

import kotlin.math.sqrt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.FloatArraySerializer
import kotlinx.serialization.builtins.IntArraySerializer

private object FloatArrayKSerializer : KSerializer<FloatArray> by FloatArraySerializer()
private object IntArrayKSerializer : KSerializer<IntArray> by IntArraySerializer()

@Serializable
data class NodeInfo(
    val id: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class EdgeInfo(
    val source: String,
    val target: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class GraphDataSimple(
    val nodes: List<NodeInfo>,
    val edges: List<EdgeInfo>,
    @Serializable(with = FloatArrayKSerializer::class)
    val weights: FloatArray? = null
) {
    init {
        require(nodes.isNotEmpty()) { "GraphDataSimple requires at least one node" }
        if (weights != null) {
            require(weights.size == edges.size) {
                "weights length (${weights.size}) must match edges size (${edges.size})"
            }
        }
    }

    fun toGraphData(): GraphData {
        val weighted = weights != null
        val graphNodes = nodes.map { info ->
            GraphNode(
                id = info.id,
                label = info.metadata["label"],
                group = info.metadata["group"],
                weight = info.metadata["weight"]?.toFloatOrNull() ?: 1f,
                metadata = info.metadata
            )
        }

        val graphEdges = edges.mapIndexed { index, edge ->
            GraphEdge(
                source = edge.source,
                target = edge.target,
                weight = weights?.get(index) ?: 1f,
                metadata = edge.metadata
            )
        }

        val statistics = GraphStatistics(
            nodeCount = graphNodes.size,
            edgeCount = graphEdges.size,
            weighted = weighted,
            averageDegree = if (graphNodes.isNotEmpty()) (graphEdges.size * 2f) / graphNodes.size else 0f
        )

        return GraphData(
            metadata = GraphMetadata(
                source = "converted",
                generator = "GraphDataSimple.toGraphData"
            ),
            statistics = statistics,
            nodes = graphNodes,
            edges = graphEdges,
            layout = null
        )
    }
}

fun GraphData.toSimple(): GraphDataSimple {
    val weights = edges.map { it.weight }.toFloatArray()
    return GraphDataSimple(
        nodes = nodes.map { node ->
            NodeInfo(
                id = node.id,
                metadata = mutableMapOf<String, String>().apply {
                    node.label?.let { put("label", it) }
                    node.group?.let { put("group", it) }
                    put("weight", node.weight.toString())
                    putAll(node.metadata)
                }
            )
        },
        edges = edges.map { edge ->
            EdgeInfo(
                source = edge.source,
                target = edge.target,
                metadata = edge.metadata
            )
        },
        weights = weights
    )
}

@Serializable
data class EmbeddingDataSimple(
    val dims: Int,
    @Serializable(with = FloatArrayKSerializer::class)
    val vectors: FloatArray,
    val labels: List<String>? = null,
    @Serializable(with = IntArrayKSerializer::class)
    val clusters: IntArray? = null
) {
    init {
        require(dims > 0) { "dims must be positive" }
        require(vectors.isNotEmpty()) { "vectors may not be empty" }
        require(vectors.size % dims == 0) {
            "vectors size (${vectors.size}) must be divisible by dims ($dims)"
        }
        if (labels != null) {
            require(labels.size == vectors.size / dims) {
                "labels size (${labels.size}) must match point count ${vectors.size / dims}"
            }
        }
        if (clusters != null) {
            require(clusters.size == vectors.size / dims) {
                "clusters size (${clusters.size}) must match point count ${vectors.size / dims}"
            }
        }
    }

    fun toEmbeddingData(): EmbeddingData {
        val pointCount = vectors.size / dims
        val computedLabels = labels ?: List(pointCount) { idx -> "point-$idx" }
        val clusterAssignments = clusters ?: IntArray(pointCount) { 0 }
        val points = ArrayList<EmbeddingPoint>(pointCount)
        var normSum = 0f
        var maxNorm = Float.MIN_VALUE
        var minNorm = Float.MAX_VALUE

        repeat(pointCount) { index ->
            val start = index * dims
            val vector = FloatArray(dims) { offset -> vectors[start + offset] }
            val norm = sqrt(vector.fold(0f) { acc, value -> acc + value * value })
            normSum += norm
            maxNorm = maxOf(maxNorm, norm)
            minNorm = minOf(minNorm, norm)
            points += EmbeddingPoint(
                id = computedLabels[index],
                vector = vector.toList(),
                cluster = clusterAssignments[index],
                position = EmbeddingPosition.EMPTY,
                metadata = emptyMap()
            )
        }

        val clusterCount = clusterAssignments.toSet().size.coerceAtLeast(1)

        val statistics = EmbeddingStatistics(
            pointCount = pointCount,
            dimension = dims,
            clusterCount = clusterCount,
            meanNorm = normSum / pointCount,
            maxNorm = maxNorm,
            minNorm = minNorm
        )

        return EmbeddingData(
            metadata = EmbeddingMetadata(
                source = "converted",
                generator = "EmbeddingDataSimple.toEmbeddingData"
            ),
            statistics = statistics,
            points = points
        )
    }
}

fun EmbeddingData.toSimple(): EmbeddingDataSimple {
    val dims = statistics.dimension
    val vectors = FloatArray(statistics.pointCount * dims)
    val labels = mutableListOf<String>()
    val clusters = IntArray(statistics.pointCount)

    points.forEachIndexed { index, point ->
        point.vector.forEachIndexed { dim, value ->
            vectors[index * dims + dim] = value
        }
        labels += point.id
        clusters[index] = point.cluster
    }

    return EmbeddingDataSimple(
        dims = dims,
        vectors = vectors,
        labels = labels,
        clusters = clusters
    )
}
