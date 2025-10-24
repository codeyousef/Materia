package io.kreekt.examples.embeddinggalaxy

import io.kreekt.engine.camera.PerspectiveCamera
import io.kreekt.engine.material.BlendMode
import io.kreekt.engine.material.RenderState
import io.kreekt.engine.material.UnlitPointsMaterial
import io.kreekt.engine.math.Color
import io.kreekt.engine.math.vec3
import io.kreekt.engine.scene.InstancedPoints
import io.kreekt.engine.scene.Node
import io.kreekt.engine.scene.Scene
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates and animates the Embedding Galaxy instanced points scene.
 *
 * The scene produces clustered datasets backed by [InstancedPoints] so it can
 * be consumed directly by [io.kreekt.engine.render.EngineRenderer].
 */
class EmbeddingGalaxyScene(
    private val config: Config = Config()
) {

    data class Config(
        val basePointCount: Int = 20_000,
        val clusterCount: Int = 6,
        val seed: Long = 7L,
        val maxQualityScale: Float = 1.25f,
        val performanceScale: Float = 0.55f,
        val rotationSpeed: Float = 0.25f,
        val orbitRadius: Float = 7.2f,
        val orbitSpeed: Float = 0.18f,
        val orbitHeightScale: Float = 0.3f,
        val shockwaveIntervalSeconds: Float = 4f,
        val shockwaveDurationSeconds: Float = 1.35f,
        val shockwaveGlowBoost: Float = 1.6f,
        val highlightSampleCount: Int = 900
    )

    enum class Quality { Performance, Balanced, Fidelity }

    data class Metrics(
        val frameTimeMs: Double,
        val activePointCount: Int,
        val quality: Quality
    )

    private val dataset: GalaxyDataset
    private val highlightMask: BooleanArray
    private val galaxyRoot = Node("galaxy-root")
    private var currentPoints: InstancedPoints

    private var elapsedSeconds = 0f
    private var shockwaveTimer = config.shockwaveIntervalSeconds
    private var shockwaveElapsed = 0f
    private var shockwaveActive = false
    private var paused = false

    private var lastFrameTimeMs: Double = 0.0

    var quality: Quality = Quality.Balanced
        private set

    var activePointCount: Int = 0
        private set

    val scene: Scene = Scene("embedding-galaxy").apply {
        backgroundColor = floatArrayOf(0.04f, 0.05f, 0.12f, 1f)
        add(galaxyRoot)
    }

    val camera: PerspectiveCamera = PerspectiveCamera(
        fovDegrees = 55f,
        aspect = 16f / 9f,
        near = 0.1f,
        far = 200f
    ).apply {
        transform.position.set(0f, config.orbitRadius * 0.1f, config.orbitRadius)
        lookAt(vec3())
    }

    val clearColor: FloatArray
        get() = scene.backgroundColor

    init {
        val maxPoints = max(
            config.basePointCount,
            (config.basePointCount * config.maxQualityScale).toInt()
        )
        dataset = generateDataset(maxPoints, config.clusterCount, config.seed)
        highlightMask = pickHighlightMask(maxPoints, config.highlightSampleCount, dataset.random)
        currentPoints = buildInstancedPoints(
            instanceCount = pointsForQuality(quality),
            highlight = false
        )
        galaxyRoot.add(currentPoints)
        activePointCount = pointsForQuality(quality)
    }

    fun update(deltaSeconds: Float) {
        if (paused) return
        elapsedSeconds += deltaSeconds

        val orbitAngle = elapsedSeconds * config.orbitSpeed
        val radius = config.orbitRadius
        val height = sin(orbitAngle * 0.5f) * config.orbitRadius * config.orbitHeightScale
        camera.transform.position.set(
            cos(orbitAngle) * radius,
            height,
            sin(orbitAngle) * radius
        )
        camera.lookAt(vec3())

        val rotationAngle = elapsedSeconds * config.rotationSpeed
        galaxyRoot.transform.setRotationEuler(0f, rotationAngle, 0f)

        if (!shockwaveActive) {
            shockwaveTimer -= deltaSeconds
            if (shockwaveTimer <= 0f) {
                activateShockwave()
            }
        } else {
            shockwaveElapsed += deltaSeconds
            if (shockwaveElapsed >= config.shockwaveDurationSeconds) {
                deactivateShockwave()
            }
        }
    }

    fun triggerQuery() {
        activateShockwave(force = true)
    }

    fun resetSequence() {
        elapsedSeconds = 0f
        shockwaveTimer = config.shockwaveIntervalSeconds
        shockwaveElapsed = 0f
        if (shockwaveActive) {
            deactivateShockwave()
        }
        galaxyRoot.transform.setRotationEuler(0f, 0f, 0f)
        camera.transform.position.set(0f, config.orbitRadius * 0.1f, config.orbitRadius)
        camera.lookAt(vec3())
    }

    fun togglePause() {
        paused = !paused
    }

    fun setQuality(newQuality: Quality) {
        if (quality == newQuality) return
        quality = newQuality
        installInstancedPoints(pointsForQuality(newQuality), highlight = shockwaveActive)
    }

    fun resize(aspect: Float) {
        camera.aspect = max(0.1f, aspect)
        camera.updateProjection()
    }

    fun metrics(): Metrics = Metrics(
        frameTimeMs = lastFrameTimeMs,
        activePointCount = activePointCount,
        quality = quality
    )

    internal fun recordFrameTime(frameMs: Double) {
        lastFrameTimeMs = frameMs
    }

    private fun activateShockwave(force: Boolean = false) {
        if (shockwaveActive && !force) return
        shockwaveActive = true
        shockwaveElapsed = 0f
        shockwaveTimer = config.shockwaveIntervalSeconds
        installInstancedPoints(pointsForQuality(quality), highlight = true)
    }

    private fun deactivateShockwave() {
        shockwaveActive = false
        shockwaveElapsed = 0f
        shockwaveTimer = config.shockwaveIntervalSeconds
        installInstancedPoints(pointsForQuality(quality), highlight = false)
    }

    private fun installInstancedPoints(instanceCount: Int, highlight: Boolean) {
        galaxyRoot.remove(currentPoints)
        currentPoints = buildInstancedPoints(instanceCount, highlight)
        galaxyRoot.add(currentPoints)
        activePointCount = instanceCount
    }

    private fun buildInstancedPoints(instanceCount: Int, highlight: Boolean): InstancedPoints {
        val positions = dataset.positions.copyOfRange(0, instanceCount * 3)
        val colors = dataset.colors.copyOfRange(0, instanceCount * 3)
        val sizes = FloatArray(instanceCount) { index ->
            val base = dataset.sizes[index]
            if (highlight && highlightMask[index]) base * config.shockwaveGlowBoost else base
        }
        val extras = FloatArray(instanceCount * 4)
        repeat(instanceCount) { index ->
            val base = index * 4
            val sourceBase = base
            extras[base] = if (highlight && highlightMask[index]) {
                dataset.extras[sourceBase] + config.shockwaveGlowBoost
            } else {
                dataset.extras[sourceBase]
            }
            extras[base + 1] = dataset.extras[sourceBase + 1]
            extras[base + 2] = dataset.extras[sourceBase + 2]
            extras[base + 3] = dataset.extras[sourceBase + 3]
        }

        val material = UnlitPointsMaterial(
            label = "embedding-galaxy-points",
            baseColor = Color.fromFloats(1f, 1f, 1f),
            size = 1f,
            renderState = RenderState(
                blendMode = BlendMode.Additive,
                depthTest = false,
                depthWrite = false
            )
        )

        return InstancedPoints.create(
            name = "embedding-galaxy",
            positions = positions,
            colors = colors,
            sizes = sizes,
            extras = extras,
            material = material
        )
    }

    private fun pointsForQuality(quality: Quality): Int {
        val base = config.basePointCount
        return when (quality) {
            Quality.Performance -> max(1, (base * config.performanceScale).toInt())
            Quality.Balanced -> base
            Quality.Fidelity -> min(
                dataset.maxPoints,
                (base * config.maxQualityScale).toInt()
            )
        }
    }

    private fun generateDataset(
        maxPoints: Int,
        clusterCount: Int,
        seed: Long
    ): GalaxyDataset {
        val random = Random(seed)
        val centers = List(clusterCount) { cluster ->
            val angle = (cluster.toFloat() / clusterCount.toFloat()) * (2f * PI.toFloat())
            val radius = 2.5f + random.nextFloat() * 1.8f
            val height = (random.nextFloat() - 0.5f) * 1.2f
            vec3(
                cos(angle) * radius,
                height,
                sin(angle) * radius
            )
        }

        val palette = listOf(
            Color.fromFloats(0.70f, 0.35f, 1f),
            Color.fromFloats(0.35f, 0.82f, 1f),
            Color.fromFloats(1f, 0.58f, 0.32f),
            Color.fromFloats(0.45f, 1f, 0.74f),
            Color.fromFloats(1f, 0.85f, 0.35f),
            Color.fromFloats(0.98f, 0.46f, 0.68f),
            Color.fromFloats(0.48f, 0.62f, 1f)
        )

        val positions = FloatArray(maxPoints * 3)
        val colors = FloatArray(maxPoints * 3)
        val sizes = FloatArray(maxPoints)
        val extras = FloatArray(maxPoints * 4)
        val clusterIndices = IntArray(maxPoints)

        repeat(maxPoints) { index ->
            val cluster = random.nextInt(clusterCount)
            clusterIndices[index] = cluster
            val center = centers[cluster]

            val spread = 0.35f + random.nextFloat() * 0.75f
            val offset = gaussianVector(random, spread)

            val base = index * 3
            positions[base] = center.x + offset.x
            positions[base + 1] = center.y + offset.y * 0.35f
            positions[base + 2] = center.z + offset.z

            val color = palette[cluster % palette.size]
            colors[base] = color.r * (0.75f + random.nextFloat() * 0.25f)
            colors[base + 1] = color.g * (0.75f + random.nextFloat() * 0.25f)
            colors[base + 2] = color.b * (0.75f + random.nextFloat() * 0.25f)

            sizes[index] = 0.45f + random.nextFloat() * 0.9f

            val extraBase = index * 4
            extras[extraBase] = 0f // glow accumulator
            extras[extraBase + 1] = cluster.toFloat() / clusterCount.toFloat()
            extras[extraBase + 2] = random.nextFloat()
            extras[extraBase + 3] = 1f
        }

        return GalaxyDataset(
            maxPoints = maxPoints,
            positions = positions,
            colors = colors,
            sizes = sizes,
            extras = extras,
            clusterIndices = clusterIndices,
            random = random
        )
    }

    private fun pickHighlightMask(
        maxPoints: Int,
        highlightSampleCount: Int,
        random: Random
    ): BooleanArray {
        val target = min(highlightSampleCount, maxPoints)
        val mask = BooleanArray(maxPoints)
        val indices = mutableSetOf<Int>()
        while (indices.size < target) {
            indices += random.nextInt(maxPoints)
        }
        indices.forEach { mask[it] = true }
        return mask
    }

    private data class GalaxyDataset(
        val maxPoints: Int,
        val positions: FloatArray,
        val colors: FloatArray,
        val sizes: FloatArray,
        val extras: FloatArray,
        val clusterIndices: IntArray,
        val random: Random
    )

    private data class Vector3(val x: Float, val y: Float, val z: Float)

    private fun gaussianVector(random: Random, spread: Float): Vector3 {
        fun gaussian(): Float {
            val u1 = 1.0 - random.nextDouble()
            val u2 = 1.0 - random.nextDouble()
            val r = sqrt(-2.0 * kotlin.math.ln(u1))
            val theta = 2.0 * PI * u2
            return (r * kotlin.math.cos(theta)).toFloat()
        }
        return Vector3(
            gaussian() * spread,
            gaussian() * spread,
            gaussian() * spread
        )
    }
}
