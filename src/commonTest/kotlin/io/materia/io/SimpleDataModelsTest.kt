package io.materia.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SimpleDataModelsTest {

    @Test
    fun embeddingConversionRoundTrip() {
        val simple = EmbeddingDataSimple(
            dims = 3,
            vectors = floatArrayOf(
                0.1f, 0.2f, 0.3f,
                0.4f, 0.5f, 0.6f,
                0.7f, 0.8f, 0.9f
            ),
            labels = listOf("a", "b", "c"),
            clusters = intArrayOf(0, 1, 1)
        )

        val rich = simple.toEmbeddingData()
        assertEquals(simple.dims, rich.statistics.dimension)
        assertEquals(simple.labels?.size ?: 0, rich.points.size)
        assertEquals(simple.clusters?.distinct()?.size ?: 1, rich.statistics.clusterCount)

        val roundTrip = rich.toSimple()
        assertEquals(simple.dims, roundTrip.dims)
        assertContentEquals(simple.vectors, roundTrip.vectors)
        assertEquals(simple.labels, roundTrip.labels)
        assertContentEquals(simple.clusters!!, roundTrip.clusters!!)
    }

    @Test
    fun graphConversionRoundTrip() {
        val simple = GraphDataSimple(
            nodes = listOf(NodeInfo("A"), NodeInfo("B")),
            edges = listOf(EdgeInfo("A", "B")),
            weights = floatArrayOf(1.5f)
        )

        val rich = simple.toGraphData()
        assertEquals(2, rich.statistics.nodeCount)
        assertEquals(1, rich.statistics.edgeCount)
        assertEquals(true, rich.statistics.weighted)

        val roundTrip = rich.toSimple()
        assertEquals(simple.nodes.size, roundTrip.nodes.size)
        assertEquals(simple.edges.size, roundTrip.edges.size)
        assertContentEquals(simple.weights!!, roundTrip.weights!!)
    }
}
