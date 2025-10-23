package io.kreekt.io

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntheticDataFactoryTest {

    @Test
    fun clusteredEmbeddingsProduceExpectedCounts() {
        val data = SyntheticDataFactory.clusteredEmbeddings(
            dimension = 3,
            clusterCount = 4,
            pointsPerCluster = 10,
            seed = 1234L
        )

        assertEquals(40, data.statistics.pointCount)
        assertEquals(3, data.statistics.dimension)
        assertEquals(4, data.statistics.clusterCount)
        assertTrue(data.points.all { it.vector.size == 3 })
        assertTrue(data.points.groupBy { it.cluster }.keys.size == 4)
        assertTrue(data.statistics.maxNorm >= data.statistics.minNorm)
    }

    @Test
    fun smallGraphGeneratesEdgesWithinBounds() {
        val graph = SyntheticDataFactory.smallGraph(
            nodeCount = 8,
            connectionProbability = 0.5f,
            weighted = true,
            seed = 2024L
        )

        assertEquals(8, graph.statistics.nodeCount)
        assertEquals(graph.nodes.size, graph.statistics.nodeCount)
        assertEquals(graph.edges.size, graph.statistics.edgeCount)
        assertTrue(graph.statistics.averageDegree >= 0f)
        assertTrue(graph.layout?.positions?.size == graph.nodes.size)
    }
}
