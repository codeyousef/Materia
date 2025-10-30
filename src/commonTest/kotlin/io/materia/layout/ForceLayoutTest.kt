package io.materia.layout

import io.materia.io.EdgeType
import io.materia.io.GraphData
import io.materia.io.GraphEdge
import io.materia.io.GraphMetadata
import io.materia.io.GraphNode
import io.materia.io.GraphStatistics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceLayoutTest {
    @Test
    fun layoutIsDeterministicForFixedSeed() {
        val graph = sampleGraph(6)
        val layoutA = ForceLayout(seed = 42L).bake(graph, iterations = 50, timeStep = 0.016f)
        val layoutB = ForceLayout(seed = 42L).bake(graph, iterations = 50, timeStep = 0.016f)

        layoutA.layout.positions.zip(layoutB.layout.positions).forEach { (a, b) ->
            assertEquals(a.x, b.x, 1e-4f)
            assertEquals(a.y, b.y, 1e-4f)
            assertEquals(a.z, b.z, 1e-4f)
        }
    }

    @Test
    fun layoutKeepsNodesWithinRadius() {
        val graph = sampleGraph(8)
        val result = ForceLayout(seed = 7L, boundingRadius = 50f).bake(
            graph,
            iterations = 100,
            timeStep = 0.02f
        )
        result.layout.positions.forEach { point ->
            val radiusSq = point.x * point.x + point.y * point.y + point.z * point.z
            assertTrue(radiusSq <= 50f * 50f + 1e-3f)
        }
    }

    private fun sampleGraph(nodeCount: Int): GraphData {
        val nodes = (0 until nodeCount).map { index ->
            GraphNode(id = "n$index", label = "Node $index", weight = 1f)
        }
        val edges = buildList {
            for (i in 0 until nodeCount - 1) {
                add(
                    GraphEdge(
                        source = "n$i",
                        target = "n${i + 1}",
                        weight = 1f,
                        type = EdgeType.UNDIRECTED
                    )
                )
            }
            add(
                GraphEdge(
                    source = "n0",
                    target = "n${nodeCount - 1}",
                    weight = 0.5f,
                    type = EdgeType.UNDIRECTED
                )
            )
        }

        val stats = GraphStatistics(
            nodeCount = nodes.size,
            edgeCount = edges.size,
            weighted = true,
            averageDegree = (edges.size * 2f) / nodes.size
        )

        return GraphData(
            metadata = GraphMetadata(description = "Test graph"),
            statistics = stats,
            nodes = nodes,
            edges = edges
        )
    }
}
