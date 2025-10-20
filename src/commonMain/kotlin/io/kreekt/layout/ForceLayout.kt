package io.kreekt.layout

import io.kreekt.core.math.Vector3
import io.kreekt.io.GraphData
import io.kreekt.io.GraphLayout
import io.kreekt.io.GraphLayoutPoint
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Deterministic force-directed layout for medium sized graphs.
 */
class ForceLayout(
    private val seed: Long = 0L,
    private val maxNodes: Int = 10_000,
    private val repulsionStrength: Float = 750f,
    private val springStrength: Float = 0.02f,
    private val damping: Float = 0.85f,
    private val maxVelocity: Float = 3.5f,
    private val boundingRadius: Float = 120f
) {
    init {
        require(maxNodes > 0) { "ForceLayout maxNodes must be positive" }
        require(repulsionStrength > 0f) { "ForceLayout repulsionStrength must be positive" }
        require(springStrength > 0f) { "ForceLayout springStrength must be positive" }
    }

    data class Result(
        val layout: GraphLayout,
        val positions: Map<String, Vector3>
    )

    fun bake(
        graph: GraphData,
        iterations: Int,
        timeStep: Float
    ): Result {
        require(iterations > 0) { "Iterations must be positive" }
        require(timeStep > 0f) { "Time step must be positive" }
        require(graph.nodes.size <= maxNodes) {
            "Graph node count ${graph.nodes.size} exceeds limit of $maxNodes"
        }

        val random = Random(seed)

        val nodeCount = graph.nodes.size
        val positions = Array(nodeCount) { Vector3() }
        val velocities = Array(nodeCount) { Vector3() }
        val forces = Array(nodeCount) { Vector3() }

        graph.nodes.forEachIndexed { index, _ ->
            val theta = random.nextFloat() * (2f * PI)
            val phi = (random.nextFloat() - 0.5f) * PI
            val radius = boundingRadius * 0.5f * random.nextFloat().coerceAtLeast(0.25f)
            val cosPhi = kotlin.math.cos(phi.toDouble()).toFloat()
            val sinPhi = kotlin.math.sin(phi.toDouble()).toFloat()
            val cosTheta = kotlin.math.cos(theta.toDouble()).toFloat()
            val sinTheta = kotlin.math.sin(theta.toDouble()).toFloat()
            positions[index].set(
                radius * cosPhi * cosTheta,
                radius * sinPhi,
                radius * cosPhi * sinTheta
            )
        }

        val nodeIndex = graph.nodes.mapIndexed { index, node -> node.id to index }.toMap()

        repeat(iterations) {
            for (i in 0 until nodeCount) {
                forces[i].set(0f, 0f, 0f)
            }

            for (i in 0 until nodeCount) {
                val pi = positions[i]
                for (j in (i + 1) until nodeCount) {
                    val pj = positions[j]
                    val delta = pi.clone().sub(pj)
                    val distanceSq = max(delta.lengthSq(), 0.01f)
                    val force = repulsionStrength / distanceSq
                    delta.normalize().multiplyScalar(force)
                    forces[i].add(delta)
                    forces[j].sub(delta)
                }
            }

            graph.edges.forEach { edge ->
                val sourceIndex = nodeIndex[edge.source] ?: return@forEach
                val targetIndex = nodeIndex[edge.target] ?: return@forEach
                val sourcePos = positions[sourceIndex]
                val targetPos = positions[targetIndex]
                val delta = targetPos.clone().sub(sourcePos)
                val length = max(sqrt(delta.lengthSq().toDouble()).toFloat(), 0.0001f)
                val restLength = 45f / edge.weight.coerceAtLeast(0.25f)
                val displacement = length - restLength
                delta.normalize().multiplyScalar(displacement * springStrength)
                forces[sourceIndex].add(delta)
                forces[targetIndex].sub(delta)
            }

            for (i in 0 until nodeCount) {
                val velocity = velocities[i]
                val force = forces[i]
                velocity.add(force.multiplyScalar(timeStep))
                velocity.multiplyScalar(damping)
                clampMagnitude(velocity, maxVelocity)
                positions[i].add(velocity.clone().multiplyScalar(timeStep))
                clampMagnitude(positions[i], boundingRadius)
            }
        }

        val layoutPoints = graph.nodes.mapIndexed { index, node ->
            GraphLayoutPoint(
                id = node.id,
                x = positions[index].x,
                y = positions[index].y,
                z = positions[index].z
            )
        }
        val layout = GraphLayout(
            positions = layoutPoints,
            randomSeed = seed,
            iterations = iterations
        )

        val positionMap = graph.nodes.associate { node ->
            val idx = nodeIndex[node.id] ?: error("Missing index for node ${node.id}")
            node.id to positions[idx].clone()
        }

        return Result(layout, positionMap)
    }

    private fun clampMagnitude(vector: Vector3, radius: Float) {
        val lengthSq = vector.lengthSq()
        if (lengthSq > radius * radius) {
            vector.normalize().multiplyScalar(radius)
        }
    }

    companion object {
        private const val PI = kotlin.math.PI.toFloat()
    }
}
