package io.materia.io

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable graph representation shared by examples, tools, and validation tasks.
 * Mirrors the structure used by search/document index payloads: metadata block, statistics,
 * and explicit typed collections rather than loosely typed maps.
 */
@Serializable
data class GraphData(
    val metadata: GraphMetadata = GraphMetadata(),
    val statistics: GraphStatistics,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val layout: GraphLayout? = null
) {
    init {
        require(nodes.isNotEmpty()) { "GraphData requires at least one node" }
        require(statistics.nodeCount == nodes.size) {
            "Graph statistics nodeCount ${statistics.nodeCount} must match nodes.size ${nodes.size}"
        }
        require(statistics.edgeCount == edges.size) {
            "Graph statistics edgeCount ${statistics.edgeCount} must match edges.size ${edges.size}"
        }
    }
}

@Serializable
data class GraphMetadata(
    val source: String = "synthetic",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val generator: String = "materia-examples",
    @SerialName("created_at")
    val createdAtEpochMillis: Long = 0L
)

@Serializable
data class GraphStatistics(
    @SerialName("node_count")
    val nodeCount: Int,
    @SerialName("edge_count")
    val edgeCount: Int,
    @SerialName("weighted")
    val weighted: Boolean,
    @SerialName("average_degree")
    val averageDegree: Float
) {
    init {
        require(nodeCount > 0) { "Graph statistics require positive node count" }
        require(edgeCount >= 0) { "Graph statistics require non-negative edge count" }
    }
}

@Serializable
data class GraphNode(
    val id: String,
    val label: String? = null,
    val group: String? = null,
    val weight: Float = 1f,
    val embedding: List<Float>? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(id.isNotBlank()) { "Graph node id cannot be blank" }
        require(weight > 0f) { "Graph node weight must be positive" }
    }
}

@Serializable
data class GraphEdge(
    val source: String,
    val target: String,
    val weight: Float = 1f,
    val type: EdgeType = EdgeType.UNDIRECTED,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(source.isNotBlank()) { "Graph edge source cannot be blank" }
        require(target.isNotBlank()) { "Graph edge target cannot be blank" }
        require(weight > 0f) { "Graph edge weight must be positive" }
    }
}

@Serializable
data class GraphLayout(
    val positions: List<GraphLayoutPoint>,
    @SerialName("seed")
    val randomSeed: Long,
    val iterations: Int
) {
    init {
        require(positions.isNotEmpty()) { "Graph layout requires at least one position" }
    }
}

@Serializable
data class GraphLayoutPoint(
    val id: String,
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

@Serializable
data class GraphLayoutBundle(
    val tfidf: GraphLayout,
    val semantic: GraphLayout
)

@Serializable
enum class EdgeType {
    @SerialName("undirected")
    UNDIRECTED,

    @SerialName("directed")
    DIRECTED
}
