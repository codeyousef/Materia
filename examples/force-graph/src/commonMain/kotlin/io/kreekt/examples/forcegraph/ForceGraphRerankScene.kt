package io.kreekt.examples.forcegraph

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Color
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Scene
import io.kreekt.examples.common.Colors
import io.kreekt.examples.common.Hud
import io.kreekt.geometry.InstancedPointsGeometry
import io.kreekt.io.GraphData
import io.kreekt.io.GraphEdge
import io.kreekt.io.GraphLayoutBundle
import io.kreekt.io.GraphMetadata
import io.kreekt.io.GraphNode
import io.kreekt.io.GraphStatistics
import io.kreekt.io.saveJson
import io.kreekt.layout.ForceLayout
import io.kreekt.render.GPULines
import io.kreekt.render.PointsBatch
import io.kreekt.points.PointsMaterial
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class ForceGraphRerankScene(
    private val config: Config = Config()
) {
    enum class Mode { TfIdf, Semantic }

    data class Config(
        val nodeCount: Int = 2000,
        val minEdgesPerNode: Int = 4,
        val maxEdgesPerNode: Int = 6,
        val layoutIterations: Int = 1_500,
        val layoutTimeStep: Float = 0.016f,
        val seed: Long = 42L
    )

    val scene = Scene()
    val camera = PerspectiveCamera(60f, 1f, 0.1f, 600f)

    private val random = Random(config.seed)
    private val geometry = InstancedPointsGeometry(config.nodeCount)
    private val material =
        PointsMaterial(color = Color(0xffffff), size = 1.5f, sizeAttenuation = true, vertexColors = true)
    private val nodeBatch = PointsBatch(geometry, material)
    private lateinit var lineBatch: GPULines

    private val baseColors = FloatArray(config.nodeCount * 3)
    private val currentPositions = FloatArray(config.nodeCount * 3)
    private val startPositions = FloatArray(config.nodeCount * 3)
    private val targetPositions = FloatArray(config.nodeCount * 3)

    private val tfidfWeights: FloatArray
    private val semanticWeights: FloatArray
    private val edgeEndpoints: IntArray

    private val graphData: GraphData
    private val layouts: GraphLayoutBundle
    private val tfidfLayoutMap: Map<String, Vector3>
    private val semanticLayoutMap: Map<String, Vector3>
    private val nodeIndex: Map<String, Int>

    private var mode: Mode = Mode.TfIdf
    private var transitioning = false
    private var transitionElapsed = 0f
    private val transitionDuration = 0.5f

    private var hud: Hud? = null

    init {
        scene.background = Background.Color(Colors.deepNavy.clone())
        scene.add(camera)

        val generated = generateGraph()
        graphData = generated.data
        tfidfWeights = generated.tfidfWeights
        semanticWeights = generated.semanticWeights
        edgeEndpoints = generated.edgeEndpoints

        nodeIndex = graphData.nodes.mapIndexed { index, node -> node.id to index }.toMap()

        val layoutData = bakeLayouts(graphData)
        layouts = layoutData.bundle
        tfidfLayoutMap = layoutData.tfidfPositions
        semanticLayoutMap = layoutData.semanticPositions

        initialiseNodes(graphData.nodes)
        initialiseLines(graphData.edges)
        applyLayout(tfidfLayoutMap)
        centreCamera()
        scene.add(nodeBatch)
        scene.add(lineBatch)

        persistLayouts(layouts)
    }

    fun attachHud(hud: Hud) {
        this.hud = hud
    }

    fun resize(aspect: Float) {
        camera.aspect = aspect
        camera.updateProjectionMatrix()
    }

    fun setMode(targetMode: Mode) {
        if (mode == targetMode) return
        mode = targetMode
        transitioning = true
        transitionElapsed = 0f
        for (i in currentPositions.indices) {
            startPositions[i] = currentPositions[i]
        }
        val layout = if (mode == Mode.TfIdf) tfidfLayoutMap else semanticLayoutMap
        copyLayout(layout, targetPositions)
    }

    fun toggleMode() {
        setMode(if (mode == Mode.TfIdf) Mode.Semantic else Mode.TfIdf)
    }

    fun update(deltaSeconds: Float) {
        if (transitioning) {
            transitionElapsed += deltaSeconds
            val progress = (transitionElapsed / transitionDuration).coerceIn(0f, 1f)
            val eased = easeInOutCubic(progress)
            interpolatePositions(eased)
            updateLines(eased)
            if (progress >= 1f) {
                transitioning = false
            }
        } else {
            updateLines(if (mode == Mode.Semantic) 1f else 0f)
        }

        hud?.setLine(0, buildString {
            append("mode=${mode.name}")
            append(" | nodes=${graphData.nodes.size}")
            append(" | edges=${graphData.edges.size}")
        })
    }

    private fun interpolatePositions(progress: Float) {
        for (i in 0 until config.nodeCount) {
            val idx = i * 3
            val sx = startPositions[idx]
            val sy = startPositions[idx + 1]
            val sz = startPositions[idx + 2]
            val tx = targetPositions[idx]
            val ty = targetPositions[idx + 1]
            val tz = targetPositions[idx + 2]
            val nx = sx + (tx - sx) * progress
            val ny = sy + (ty - sy) * progress
            val nz = sz + (tz - sz) * progress
            currentPositions[idx] = nx
            currentPositions[idx + 1] = ny
            currentPositions[idx + 2] = nz
            nodeBatch.setInstancePosition(i, nx, ny, nz)
        }
    }

    private fun updateLines(blend: Float) {
        for (edgeIndex in 0 until edgeCount()) {
            val a = edgeEndpoints[edgeIndex * 2]
            val b = edgeEndpoints[edgeIndex * 2 + 1]
            val ax = currentPositions[a * 3]
            val ay = currentPositions[a * 3 + 1]
            val az = currentPositions[a * 3 + 2]
            val bx = currentPositions[b * 3]
            val by = currentPositions[b * 3 + 1]
            val bz = currentPositions[b * 3 + 2]

            val weight = lerp(tfidfWeights[edgeIndex], semanticWeights[edgeIndex], blend)
            val intensity = (0.35f + weight * 0.65f).coerceIn(0f, 1f)
            val color = Colors.teal.clone().multiplyScalar(intensity)
            lineBatch.setSegment(edgeIndex, ax, ay, az, bx, by, bz, color.r, color.g, color.b, intensity)
        }
    }

    private fun copyLayout(layout: Map<String, Vector3>, destination: FloatArray) {
        layout.forEach { (id, vector) ->
            val index = nodeIndex[id] ?: return@forEach
            val offset = index * 3
            destination[offset] = vector.x
            destination[offset + 1] = vector.y
            destination[offset + 2] = vector.z
        }
    }

    private fun applyLayout(layout: Map<String, Vector3>) {
        copyLayout(layout, currentPositions)
        currentPositions.copyInto(targetPositions)
        for (i in 0 until config.nodeCount) {
            val idx = i * 3
            nodeBatch.setInstancePosition(
                i,
                currentPositions[idx],
                currentPositions[idx + 1],
                currentPositions[idx + 2]
            )
        }
        currentPositions.copyInto(startPositions)
        updateLines(if (mode == Mode.Semantic) 1f else 0f)
    }

    private fun centreCamera() {
        var maxDistance = 0f
        for (i in 0 until config.nodeCount) {
            val idx = i * 3
            val x = currentPositions[idx]
            val y = currentPositions[idx + 1]
            val z = currentPositions[idx + 2]
            val distance = sqrt(x * x + y * y + z * z)
            maxDistance = max(maxDistance, distance)
        }
        camera.position.set(0f, maxDistance * 0.65f, maxDistance * 2.2f)
        camera.lookAt(0f, 0f, 0f)
        camera.updateMatrixWorld(true)
    }

    private fun initialiseNodes(nodes: List<GraphNode>) {
        val palette = Colors.brandCycle()
        nodes.forEachIndexed { index, node ->
            val color = palette[index % palette.size]
            val baseIndex = index * 3
            baseColors[baseIndex] = color.r
            baseColors[baseIndex + 1] = color.g
            baseColors[baseIndex + 2] = color.b
            nodeBatch.setInstanceColor(index, color.r, color.g, color.b)
            nodeBatch.setInstanceSize(index, 1.2f + (node.weight * 0.6f))
        }
    }

    private fun initialiseLines(edges: List<GraphEdge>) {
        lineBatch = GPULines(edges.size)
        for (edgeIndex in edges.indices) {
            val a = nodeIndex[edges[edgeIndex].source] ?: continue
            val b = nodeIndex[edges[edgeIndex].target] ?: continue
            edgeEndpoints[edgeIndex * 2] = a
            edgeEndpoints[edgeIndex * 2 + 1] = b
            lineBatch.setSegment(
                edgeIndex,
                0f,
                0f,
                0f,
                0f,
                0f,
                0f,
                Colors.azure.r,
                Colors.azure.g,
                Colors.azure.b,
                0.2f
            )
        }
    }

    private fun persistLayouts(bundle: GraphLayoutBundle) {
        val capturePath = "examples/_captures/force-graph-layout.json"
        runCatching {
            saveJson(capturePath, bundle)
        }.onFailure { /* ignore on JS */ }
    }

    private fun generateGraph(): GeneratedGraph {
        val nodes = mutableListOf<GraphNode>()
        val tfidf = mutableListOf<Float>()
        val semantic = mutableListOf<Float>()
        val endpoints = mutableListOf<Int>()
        val embeddings = Array(config.nodeCount) { FloatArray(8) }
        val groups = Array(config.nodeCount) { random.nextInt(0, 6) }

        for (i in 0 until config.nodeCount) {
            fillGaussian(embeddings[i])
            normalize(embeddings[i])
            nodes += GraphNode(
                id = "n$i",
                label = "Node $i",
                group = "cluster-${groups[i]}",
                weight = 0.8f + random.nextFloat() * 0.6f,
                embedding = embeddings[i].toList()
            )
        }

        val edges = mutableListOf<GraphEdge>()
        val seen = HashSet<Long>()
        for (source in 0 until config.nodeCount) {
            val degree = random.nextInt(config.minEdgesPerNode, config.maxEdgesPerNode + 1)
            repeat(degree) {
                val target = random.nextInt(config.nodeCount)
                if (target == source) return@repeat
                val key = encodeEdge(source, target)
                if (!seen.add(key)) return@repeat

                val tfidfWeight = baseTfidfWeight(groups[source], groups[target])
                val semanticWeight = semanticSimilarity(embeddings[source], embeddings[target])
                tfidf += tfidfWeight
                semantic += semanticWeight
                endpoints += source
                endpoints += target
                edges += GraphEdge(
                    source = nodes[source].id,
                    target = nodes[target].id,
                    weight = tfidfWeight,
                    metadata = mapOf(
                        "tfidf" to tfidfWeight.toString(),
                        "semantic" to semanticWeight.toString()
                    )
                )
            }
        }

        val stats = GraphStatistics(
            nodeCount = nodes.size,
            edgeCount = edges.size,
            weighted = true,
            averageDegree = if (nodes.isEmpty()) 0f else (edges.size * 2f) / nodes.size
        )

        val metadata = GraphMetadata(description = "Synthetic force graph rerank dataset")
        val graph = GraphData(metadata = metadata, statistics = stats, nodes = nodes, edges = edges)

        return GeneratedGraph(graph, tfidf.toFloatArray(), semantic.toFloatArray(), endpoints.toIntArray())
    }

    private fun bakeLayouts(graph: GraphData): LayoutComputation {
        val tfidfResult = ForceLayout(seed = config.seed).bake(graph, config.layoutIterations, config.layoutTimeStep)
        val semanticGraph = graph.copy(
            edges = graph.edges.mapIndexed { index, edge ->
                edge.copy(weight = semanticWeights[index])
            }
        )
        val semanticResult =
            ForceLayout(seed = config.seed).bake(semanticGraph, config.layoutIterations, config.layoutTimeStep)
        val bundle = GraphLayoutBundle(tfidf = tfidfResult.layout, semantic = semanticResult.layout)
        return LayoutComputation(
            bundle = bundle,
            tfidfPositions = tfidfResult.positions,
            semanticPositions = semanticResult.positions
        )
    }

    private fun edgeCount(): Int = edgeEndpoints.size / 2

    private data class GeneratedGraph(
        val data: GraphData,
        val tfidfWeights: FloatArray,
        val semanticWeights: FloatArray,
        val edgeEndpoints: IntArray
    )

    private data class LayoutComputation(
        val bundle: GraphLayoutBundle,
        val tfidfPositions: Map<String, Vector3>,
        val semanticPositions: Map<String, Vector3>
    )

    private fun fillGaussian(target: FloatArray) {
        for (i in target.indices) {
            target[i] = random.nextGaussianFloat()
        }
    }

    private fun normalize(target: FloatArray) {
        var lengthSq = 0f
        for (value in target) {
            lengthSq += value * value
        }
        val inv = if (lengthSq <= 0f) 1f else 1f / sqrt(lengthSq)
        for (i in target.indices) {
            target[i] *= inv
        }
    }

    private fun baseTfidfWeight(groupA: Int, groupB: Int): Float {
        val shared = if (groupA == groupB) 0.35f else 0f
        return 0.4f + shared + random.nextFloat() * 0.6f
    }

    private fun semanticSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return ((dot + 1f) * 0.5f).coerceIn(0f, 1f)
    }

    private fun encodeEdge(a: Int, b: Int): Long {
        val min = min(a, b)
        val max = max(a, b)
        return (min.toLong() shl 32) or max.toLong()
    }

    private fun Random.nextGaussianFloat(): Float {
        val u1 = nextFloat().coerceIn(1e-6f, 1f)
        val u2 = nextFloat()
        val radius = sqrt(-2f * ln(u1))
        val theta = (2f * kotlin.math.PI * u2).toFloat()
        return radius * kotlin.math.cos(theta)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun easeInOutCubic(t: Float): Float = if (t < 0.5f) {
        4f * t * t * t
    } else {
        1f - (-2f * t + 2f).pow(3) / 2f
    }
}
