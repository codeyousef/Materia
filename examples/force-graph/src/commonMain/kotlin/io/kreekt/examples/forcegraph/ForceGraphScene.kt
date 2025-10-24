package io.kreekt.examples.forcegraph

import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.material.BlendMode
import io.kreekt.engine.material.RenderState
import io.kreekt.engine.material.UnlitLineMaterial
import io.kreekt.engine.material.UnlitPointsMaterial
import io.kreekt.engine.math.Color
import io.kreekt.engine.math.vec3
import io.kreekt.engine.scene.InstancedPoints
import io.kreekt.engine.scene.Mesh
import io.kreekt.engine.scene.Node
import io.kreekt.engine.scene.Scene
import io.kreekt.engine.scene.VertexBuffer
import io.kreekt.engine.geometry.AttributeSemantic
import io.kreekt.engine.geometry.AttributeType
import io.kreekt.engine.geometry.Geometry
import io.kreekt.engine.geometry.GeometryAttribute
import io.kreekt.engine.geometry.GeometryLayout
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class ForceGraphScene(
    layout: ForceGraphLayout
) {
    @Serializable
    data class Config(
        val nodeCount: Int = 2_500,
        val clusterCount: Int = 6,
        val edgeCount: Int = 7_500,
        val transitionSeconds: Float = 0.5f,
        val seed: Long = 13L
    )

    enum class Mode { TfIdf, Semantic }

    data class Metrics(
        val frameTimeMs: Double,
        val nodeCount: Int,
        val edgeCount: Int,
        val mode: Mode
    )

    private val config = layout.config
    private val dataset = ForceGraphDataset(layout)
    private val root = Node("force-graph-root")
    private var nodePoints: InstancedPoints
    private var edgeMesh: Mesh

    private var currentMode: Mode = Mode.TfIdf
    private var targetMode: Mode = Mode.TfIdf
    private var transitionElapsed = 0f
    private var lastFrameTimeMs: Double = 0.0
    private var orbitAngle = 0f

    val scene: Scene = Scene("force-graph").apply {
        backgroundColor = floatArrayOf(0.02f, 0.02f, 0.035f, 1f)
        add(root)
    }

    val camera: PerspectiveCamera = PerspectiveCamera(
        fovDegrees = 55f,
        aspect = 16f / 9f,
        near = 0.1f,
        far = 150f
    ).apply {
        transform.position.set(0f, 12f, 28f)
        lookAt(vec3())
    }

    init {
        nodePoints = buildNodePoints(0f)
        edgeMesh = buildEdgeMesh(0f)
        root.add(edgeMesh)
        root.add(nodePoints)
    }

    fun update(deltaSeconds: Float) {
        orbitAngle += deltaSeconds * 0.2f
        val radius = 28f
        camera.transform.position.set(
            cos(orbitAngle) * radius,
            12f + sin(orbitAngle * 0.3f) * 2f,
            sin(orbitAngle) * radius
        )
        camera.lookAt(vec3())

        if (targetMode != currentMode) {
            transitionElapsed += deltaSeconds
            val progress = min(transitionElapsed / config.transitionSeconds, 1f)
            val mix = when (targetMode) {
                Mode.Semantic -> progress
                Mode.TfIdf -> 1f - progress
            }
            rebuildScene(mix)
            if (progress >= 1f) {
                currentMode = targetMode
                transitionElapsed = 0f
            }
        }
    }

    fun triggerToggle() {
        targetMode = if (targetMode == Mode.TfIdf) Mode.Semantic else Mode.TfIdf
        transitionElapsed = 0f
    }

    fun setMode(mode: Mode) {
        targetMode = mode
        transitionElapsed = 0f
        currentMode = mode
        val mix = if (mode == Mode.Semantic) 1f else 0f
        rebuildScene(mix)
    }

    fun metrics(): Metrics = Metrics(
        frameTimeMs = lastFrameTimeMs,
        nodeCount = config.nodeCount,
        edgeCount = config.edgeCount,
        mode = currentMode
    )

    fun recordFrameTime(ms: Double) {
        lastFrameTimeMs = ms
    }

    fun resize(width: Int, height: Int) {
        camera.aspect = max(0.1f, width.toFloat() / max(1, height).toFloat())
        camera.updateProjection()
    }

    private fun rebuildScene(mix: Float) {
        root.remove(edgeMesh)
        root.remove(nodePoints)
        nodePoints = buildNodePoints(mix)
        edgeMesh = buildEdgeMesh(mix)
        root.add(edgeMesh)
        root.add(nodePoints)
    }

    private fun buildNodePoints(mix: Float): InstancedPoints {
        val positions = dataset.blendNodePositions(mix)
        val colors = dataset.blendNodeColors(mix)
        val sizes = dataset.blendNodeSizes(mix)
        val extras = dataset.buildNodeExtras(mix)
        return InstancedPoints.create(
            name = "force-graph-points",
            positions = positions,
            colors = colors,
            sizes = sizes,
            extras = extras,
            material = UnlitPointsMaterial(
                label = "force-graph-points",
                baseColor = Color.fromFloats(0.9f, 0.95f, 1f),
                size = 1f,
                renderState = RenderState(
                    blendMode = BlendMode.Additive,
                    depthTest = true,
                    depthWrite = false
                )
            )
        )
    }

    private fun buildEdgeMesh(mix: Float): Mesh {
        val positions = dataset.blendNodePositions(mix)
        val edgeData = dataset.buildEdgeVertices(positions, mix)
        val geometry = Geometry(
            vertexBuffer = VertexBuffer(edgeData, Float.SIZE_BYTES * 6),
            layout = GeometryLayout(
                stride = Float.SIZE_BYTES * 6,
                attributes = mapOf(
                    AttributeSemantic.POSITION to GeometryAttribute(0, 3, AttributeType.FLOAT32),
                    AttributeSemantic.COLOR to GeometryAttribute(Float.SIZE_BYTES * 3, 3, AttributeType.FLOAT32)
                )
            )
        )
        val material = UnlitLineMaterial(
            label = "force-graph-edges",
            color = Color.White,
            renderState = RenderState(
                blendMode = BlendMode.Alpha,
                depthTest = true,
                depthWrite = false,
                cullMode = io.kreekt.engine.material.CullMode.NONE
            )
        )
        return Mesh("force-graph-edges", geometry, material)
    }

    private class ForceGraphDataset(layout: ForceGraphLayout) {
        private val config = layout.config
        private val nodeCount = config.nodeCount
        private val edgeCount = config.edgeCount
        private val clusterCount = config.clusterCount

        private val clusterAssignments = layout.clusterAssignments.toIntArray()
        private val tfidfPositions = layout.tfidfPositions.toFloatArray()
        private val semanticPositions = layout.semanticPositions.toFloatArray()
        private val tfidfColors = layout.tfidfColors.toFloatArray()
        private val semanticColors = layout.semanticColors.toFloatArray()
        private val tfidfSizes = layout.tfidfSizes.toFloatArray()
        private val semanticSizes = layout.semanticSizes.toFloatArray()
        private val nodeEnergy = layout.nodeEnergy.toFloatArray()

        private val edges = layout.edges.toIntArray()
        private val tfidfEdgeStrength = layout.tfidfEdgeStrength.toFloatArray()
        private val semanticEdgeStrength = layout.semanticEdgeStrength.toFloatArray()

        private val blendPositions = FloatArray(nodeCount * 3)
        private val blendColors = FloatArray(nodeCount * 3)
        private val blendSizes = FloatArray(nodeCount)
        private val blendExtras = FloatArray(nodeCount * 4)

        private val tempEdgeData = FloatArray(edgeCount * 12)

        fun blendNodePositions(mix: Float): FloatArray {
            for (i in blendPositions.indices) {
                blendPositions[i] = lerp(tfidfPositions[i], semanticPositions[i], mix)
            }
            return blendPositions
        }

        fun blendNodeColors(mix: Float): FloatArray {
            for (i in blendColors.indices) {
                blendColors[i] = lerp(tfidfColors[i], semanticColors[i], mix)
            }
            return blendColors
        }

        fun blendNodeSizes(mix: Float): FloatArray {
            for (i in blendSizes.indices) {
                blendSizes[i] = lerp(tfidfSizes[i], semanticSizes[i], mix)
            }
            return blendSizes
        }

        fun buildNodeExtras(mix: Float): FloatArray {
            val highlightScale = lerp(0.4f, 0.9f, mix)
            repeat(nodeCount) { index ->
                val base = index * 4
                val energy = nodeEnergy[index]
                blendExtras[base] = energy * highlightScale
                blendExtras[base + 1] = clusterAssignments[index] / clusterCount.toFloat()
                blendExtras[base + 2] = mix
                blendExtras[base + 3] = 1f
            }
            return blendExtras
        }

        fun buildEdgeVertices(nodePositions: FloatArray, mix: Float): FloatArray {
            val baseColor = floatArrayOf(0.45f, 0.58f, 0.9f)
            repeat(edgeCount) { edgeIndex ->
                val src = edges[edgeIndex * 2]
                val dst = edges[edgeIndex * 2 + 1]
                val strength = lerp(tfidfEdgeStrength[edgeIndex], semanticEdgeStrength[edgeIndex], mix)
                val intensity = 0.2f + strength * 0.8f
                val colorR = baseColor[0] * intensity
                val colorG = baseColor[1] * intensity
                val colorB = baseColor[2] * intensity

                val srcIndex = src * 3
                val dstIndex = dst * 3
                val edgeBase = edgeIndex * 12

                tempEdgeData[edgeBase] = nodePositions[srcIndex]
                tempEdgeData[edgeBase + 1] = nodePositions[srcIndex + 1]
                tempEdgeData[edgeBase + 2] = nodePositions[srcIndex + 2]
                tempEdgeData[edgeBase + 3] = colorR
                tempEdgeData[edgeBase + 4] = colorG
                tempEdgeData[edgeBase + 5] = colorB

                tempEdgeData[edgeBase + 6] = nodePositions[dstIndex]
                tempEdgeData[edgeBase + 7] = nodePositions[dstIndex + 1]
                tempEdgeData[edgeBase + 8] = nodePositions[dstIndex + 2]
                tempEdgeData[edgeBase + 9] = colorR
                tempEdgeData[edgeBase + 10] = colorG
                tempEdgeData[edgeBase + 11] = colorB
            }
            return tempEdgeData
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    }
}
