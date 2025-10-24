package io.kreekt.examples.forcegraph

import io.kreekt.engine.math.Color
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.collections.HashSet

@Serializable
data class ForceGraphLayout(
    val config: ForceGraphScene.Config,
    val clusterAssignments: List<Int>,
    val tfidfPositions: List<Float>,
    val semanticPositions: List<Float>,
    val tfidfColors: List<Float>,
    val semanticColors: List<Float>,
    val tfidfSizes: List<Float>,
    val semanticSizes: List<Float>,
    val nodeEnergy: List<Float>,
    val edges: List<Int>,
    val tfidfEdgeStrength: List<Float>,
    val semanticEdgeStrength: List<Float>
)

object ForceGraphLayoutGenerator {
    fun generate(config: ForceGraphScene.Config): ForceGraphLayout {
        val random = Random(config.seed)
        val nodeCount = config.nodeCount
        val edgeCount = config.edgeCount
        val clusterCount = config.clusterCount

        val clusterAssignments = IntArray(nodeCount)
        val tfidfPositions = FloatArray(nodeCount * 3)
        val semanticPositions = FloatArray(nodeCount * 3)
        val tfidfColors = FloatArray(nodeCount * 3)
        val semanticColors = FloatArray(nodeCount * 3)
        val tfidfSizes = FloatArray(nodeCount)
        val semanticSizes = FloatArray(nodeCount)
        val nodeEnergy = FloatArray(nodeCount)

        val edges = IntArray(edgeCount * 2)
        val tfidfEdgeStrength = FloatArray(edgeCount)
        val semanticEdgeStrength = FloatArray(edgeCount)

        val palette = arrayOf(
            Color.fromFloats(0.88f, 0.46f, 0.92f),
            Color.fromFloats(0.42f, 0.82f, 0.92f),
            Color.fromFloats(0.98f, 0.65f, 0.32f),
            Color.fromFloats(0.52f, 0.95f, 0.70f),
            Color.fromFloats(0.98f, 0.82f, 0.36f),
            Color.fromFloats(0.68f, 0.72f, 1f)
        )

        repeat(nodeCount) { index ->
            val cluster = random.nextInt(clusterCount)
            clusterAssignments[index] = cluster
            val baseAngle = ((cluster.toFloat() + random.nextFloat() * 0.35f) / clusterCount.toFloat()) * (PI.toFloat() * 2f)
            val radius = 9f + random.nextFloat() * 5f
            val height = (random.nextFloat() - 0.5f) * 6f

            val tfidfBase = index * 3
            tfidfPositions[tfidfBase] = cos(baseAngle) * radius + random.gaussian(0.8f)
            tfidfPositions[tfidfBase + 1] = height + random.gaussian(0.6f)
            tfidfPositions[tfidfBase + 2] = sin(baseAngle) * radius + random.gaussian(0.8f)

            val semanticRadius = radius * lerp(0.55f, 0.85f, random.nextFloat())
            semanticPositions[tfidfBase] = cos(baseAngle + random.gaussian(0.25f)) * semanticRadius
            semanticPositions[tfidfBase + 1] = height * 0.35f + random.gaussian(0.4f)
            semanticPositions[tfidfBase + 2] = sin(baseAngle + random.gaussian(0.25f)) * semanticRadius

            val clusterColor = palette[cluster % palette.size]
            val colorBase = tfidfBase
            val chroma = 0.7f + random.nextFloat() * 0.3f
            tfidfColors[colorBase] = clusterColor.r * chroma
            tfidfColors[colorBase + 1] = clusterColor.g * chroma
            tfidfColors[colorBase + 2] = clusterColor.b * chroma

            val semanticChroma = 0.5f + random.nextFloat() * 0.5f
            semanticColors[colorBase] = clusterColor.r * semanticChroma
            semanticColors[colorBase + 1] = clusterColor.g * semanticChroma
            semanticColors[colorBase + 2] = clusterColor.b * semanticChroma

            tfidfSizes[index] = 0.6f + random.nextFloat() * 0.8f
            semanticSizes[index] = tfidfSizes[index] * (1.1f + random.nextFloat() * 0.4f)

            nodeEnergy[index] = random.nextFloat()
        }

        val used = HashSet<Long>(edgeCount * 2)
        repeat(edgeCount) { edgeIndex ->
            var src: Int
            var dst: Int
            do {
                src = random.nextInt(nodeCount)
                dst = random.nextInt(nodeCount)
            } while (src == dst || !used.add(src.toLong() shl 32 or dst.toLong()))

            edges[edgeIndex * 2] = src
            edges[edgeIndex * 2 + 1] = dst

            val clusterSim = if (clusterAssignments[src] == clusterAssignments[dst]) 1f else 0.35f
            tfidfEdgeStrength[edgeIndex] = 0.3f + random.nextFloat() * 0.6f * clusterSim
            semanticEdgeStrength[edgeIndex] = 0.5f + random.nextFloat() * 0.5f * (1f - clusterSim * 0.4f)
        }

        return ForceGraphLayout(
            config = config,
            clusterAssignments = clusterAssignments.toList(),
            tfidfPositions = tfidfPositions.toList(),
            semanticPositions = semanticPositions.toList(),
            tfidfColors = tfidfColors.toList(),
            semanticColors = semanticColors.toList(),
            tfidfSizes = tfidfSizes.toList(),
            semanticSizes = semanticSizes.toList(),
            nodeEnergy = nodeEnergy.toList(),
            edges = edges.toList(),
            tfidfEdgeStrength = tfidfEdgeStrength.toList(),
            semanticEdgeStrength = semanticEdgeStrength.toList()
        )
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun Random.gaussian(scale: Float): Float {
    val u1 = nextFloat().coerceAtLeast(Float.MIN_VALUE)
    val u2 = nextFloat().coerceAtLeast(Float.MIN_VALUE)
    val r = sqrt(-2.0f * ln(u1))
    val theta = 2.0f * PI.toFloat() * u2
    return r * cos(theta) * scale
}
