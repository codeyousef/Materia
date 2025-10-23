package io.kreekt.io

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic synthetic data generators used by the MVP demos and tests.
 * Provides clustered embedding clouds as well as a small utility graph.
 */
object SyntheticDataFactory {

    /**
     * Generate a deterministic clustered embedding dataset.
     * Each cluster shares a random centre and Gaussian spread around it.
     */
    fun clusteredEmbeddings(
        dimension: Int,
        clusterCount: Int,
        pointsPerCluster: Int,
        seed: Long = 1337L,
        spread: Float = 0.35f
    ): EmbeddingData {
        require(dimension > 0) { "Embedding dimension must be positive" }
        require(clusterCount > 0) { "Cluster count must be positive" }
        require(pointsPerCluster > 0) { "Points per cluster must be positive" }
        require(spread > 0f) { "Spread must be positive" }

        val rng = Random(seed)
        val totalPoints = clusterCount * pointsPerCluster
        val centres = List(clusterCount) {
            FloatArray(dimension) { rng.nextFloat() * 2f - 1f }
        }

        val points = ArrayList<EmbeddingPoint>(totalPoints)
        var normSum = 0f
        var maxNorm = Float.MIN_VALUE
        var minNorm = Float.MAX_VALUE

        centres.forEachIndexed { clusterIndex, centre ->
            repeat(pointsPerCluster) { pointIndex ->
                val vector = FloatArray(dimension) { dim ->
                    centre[dim] + nextGaussian(rng) * spread
                }
                val norm = vector.fold(0f) { acc, value -> acc + value * value }.let { sqrt(it) }
                normSum += norm
                maxNorm = maxOf(maxNorm, norm)
                minNorm = min(norm, norm)

                val position = EmbeddingPosition(
                    x = vector.getOrElse(0) { 0f },
                    y = vector.getOrElse(1) { 0f },
                    z = vector.getOrElse(2) { 0f }
                )

                val id = "cluster-${clusterIndex}_point-$pointIndex"
                points += EmbeddingPoint(
                    id = id,
                    vector = vector.toList(),
                    cluster = clusterIndex,
                    position = position,
                    metadata = mapOf("cluster" to clusterIndex.toString())
                )
            }
        }

        val statistics = EmbeddingStatistics(
            pointCount = totalPoints,
            dimension = dimension,
            clusterCount = clusterCount,
            meanNorm = normSum / totalPoints,
            maxNorm = maxNorm,
            minNorm = minNorm
        )

        return EmbeddingData(
            metadata = EmbeddingMetadata(
                source = "synthetic",
                generator = "SyntheticDataFactory.clusteredEmbeddings",
                tags = listOf("clustered", "synthetic"),
                createdAtEpochMillis = 0L
            ),
            statistics = statistics,
            points = points
        )
    }

    /**
     * Generate a small random graph with optional weights and a circular layout.
     */
    fun smallGraph(
        nodeCount: Int,
        connectionProbability: Float = 0.25f,
        weighted: Boolean = true,
        seed: Long = 4242L
    ): GraphData {
        require(nodeCount > 0) { "Graph requires positive node count" }
        require(connectionProbability in 0f..1f) { "Connection probability must be in [0, 1]" }

        val rng = Random(seed)

        val nodes = List(nodeCount) { index ->
            GraphNode(
                id = "node-$index",
                label = "Node $index",
                group = "group-${index % maxOf(1, nodeCount / 3)}",
                weight = if (weighted) rng.nextFloat().coerceIn(0.5f, 1.5f) else 1f,
                metadata = mapOf("index" to index.toString())
            )
        }

        val edges = ArrayList<GraphEdge>()
        for (i in 0 until nodeCount) {
            for (j in i + 1 until nodeCount) {
                if (rng.nextFloat() <= connectionProbability) {
                    val weight = if (weighted) (0.5f + rng.nextFloat()) else 1f
                    edges += GraphEdge(
                        source = nodes[i].id,
                        target = nodes[j].id,
                        weight = weight
                    )
                }
            }
        }

        val layoutPositions = nodes.mapIndexed { index, node ->
            val angle = (index.toFloat() / nodeCount.toFloat()) * 2f * PI.toFloat()
            GraphLayoutPoint(
                id = node.id,
                x = cos(angle.toDouble()).toFloat(),
                y = sin(angle.toDouble()).toFloat(),
                z = 0f
            )
        }

        val statistics = GraphStatistics(
            nodeCount = nodeCount,
            edgeCount = edges.size,
            weighted = weighted,
            averageDegree = if (nodeCount > 0) (edges.size * 2f) / nodeCount else 0f
        )

        return GraphData(
            metadata = GraphMetadata(
                source = "synthetic",
                generator = "SyntheticDataFactory.smallGraph",
                tags = listOf("graph", "synthetic"),
                createdAtEpochMillis = 0L
            ),
            statistics = statistics,
            nodes = nodes,
            edges = edges,
            layout = GraphLayout(
                positions = layoutPositions,
                randomSeed = seed,
                iterations = 0
            )
        )
    }

    private fun nextGaussian(rng: Random): Float {
        val u1 = rng.nextFloat().coerceIn(1e-6f, 0.999999f)
        val u2 = rng.nextFloat().coerceIn(1e-6f, 0.999999f)
        val radius = sqrt(-2f * ln(u1.toDouble()).toFloat())
        val theta = 2f * PI.toFloat() * u2
        return radius * cos(theta.toDouble()).toFloat()
    }
}
