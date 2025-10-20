package io.kreekt.examples.embeddinggalaxy

import io.kreekt.camera.PerspectiveCamera
import io.kreekt.core.math.Color
import io.kreekt.core.math.Vector3
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Scene
import io.kreekt.examples.common.CameraRails
import io.kreekt.examples.common.Colors
import io.kreekt.examples.common.Hud
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.InstancedPointsGeometry
import io.kreekt.io.EmbeddingData
import io.kreekt.io.EmbeddingPoint
import io.kreekt.io.EmbeddingPosition
import io.kreekt.io.EmbeddingStatistics
import io.kreekt.io.NormalizedVector
import io.kreekt.render.PointsBatch
import io.kreekt.points.PointsMaterial
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

class EmbeddingGalaxyScene(
    private val config: Config = Config()
) {
    data class Config(
        val topK: Int = 128,
        val clusterCount: Int = 6,
        val seed: Long = 1337L,
        val enableBloom: Boolean = true,
        val similarityThreshold: Float = 0.85f,
        val shockwaveIntervalSeconds: Float = 4f,
        val shockwaveDurationSeconds: Float = 0.9f,
        val pointCount: Int = 20_000,
        val dimension: Int = 64
    )

    val scene = Scene()
    val camera = PerspectiveCamera(70f, 1f, 0.1f, 500f)
    val points: PointsBatch

    private val geometry = InstancedPointsGeometry(config.pointCount)
    private val material = PointsMaterial(
        color = Color(0xffffff),
        size = 1.4f,
        sizeAttenuation = true,
        vertexColors = true,
        transparent = true
    )

    private val baseColors = FloatArray(config.pointCount * 3)
    private val baseSizes = FloatArray(config.pointCount)
    private val positions = FloatArray(config.pointCount * 3)
    private val normalizedVectors = Array(config.pointCount) { NormalizedVector(FloatArray(config.dimension)) }
    private val similarities = FloatArray(config.pointCount)
    private val highlightIndices = IntArray(config.topK)
    private var highlightCount = 0

    private val random = Random(config.seed)
    private val projection = Array(3) { FloatArray(config.dimension) }
    private val cameraRail: CameraRails
    private val lookAt = Vector3(0f, 0f, 0f)

    private var elapsed = 0f
    private var lastShockwave = -config.shockwaveIntervalSeconds
    private var shockwaveStart = -config.shockwaveDurationSeconds
    private var shockwaveActive = false
    private var shockwaveQueryIndex = -1
    private var paused = false

    private val queryOrigin = Vector3()
    private var maxDistance = 1f

    private var hud: Hud? = null

    val data: EmbeddingData

    init {
        scene.background = Background.Color(Colors.deepNavy.clone())
        camera.position.set(0f, 35f, 140f)
        scene.add(camera)

        prepareProjection()
        data = generateSyntheticData()

        points = PointsBatch(geometry, material)
        scene.add(points)

        cameraRail = CameraRails(
            listOf(
                Vector3(0f, 70f, 160f),
                Vector3(-80f, 45f, 140f),
                Vector3(0f, 30f, 120f),
                Vector3(80f, 55f, 150f)
            )
        )
    }

    fun attachHud(hud: Hud) {
        this.hud = hud
    }

    fun resize(aspect: Float) {
        camera.aspect = aspect
        camera.updateProjectionMatrix()
    }

    fun togglePause() {
        paused = !paused
    }

    fun resetSequence() {
        paused = false
        shockwaveActive = false
        elapsed = 0f
        lastShockwave = -config.shockwaveIntervalSeconds
        shockwaveQueryIndex = -1
        restoreBaseAppearance()
    }

    fun triggerQuery() {
        startShockwave()
    }

    fun update(deltaSeconds: Float) {
        if (paused) {
            hud?.setLine(0, "Paused | points=${config.pointCount}")
            return
        }
        elapsed += deltaSeconds

        if (!shockwaveActive && elapsed - lastShockwave >= config.shockwaveIntervalSeconds) {
            startShockwave()
        }

        updateShockwave(elapsed)
        cameraRail.attach(camera, (elapsed % CAMERA_LOOP_SECONDS) / CAMERA_LOOP_SECONDS, lookAt)

        hud?.setLine(
            0,
            buildString {
                append("points=${config.pointCount}")
                append(" | active=")
                append(if (shockwaveActive) "Q${shockwaveQueryIndex}" else "idle")
                append(" | highlights=$highlightCount")
            }
        )
    }

    private fun startShockwave() {
        shockwaveActive = true
        shockwaveStart = elapsed
        lastShockwave = elapsed
        shockwaveQueryIndex = random.nextInt(config.pointCount)
        computeSimilarities(shockwaveQueryIndex)
        queryOrigin.set(
            positions[shockwaveQueryIndex * 3],
            positions[shockwaveQueryIndex * 3 + 1],
            positions[shockwaveQueryIndex * 3 + 2]
        )
        restoreBaseAppearance()
    }

    private fun updateShockwave(time: Float) {
        if (!shockwaveActive) return
        val progress = (time - shockwaveStart) / config.shockwaveDurationSeconds
        if (progress >= 1f) {
            shockwaveActive = false
            highlightCount = 0
            restoreBaseAppearance()
            return
        }

        val radius = progress * maxDistance
        val fade = 1f - progress
        val threshold = config.similarityThreshold
        if (highlightCount == 0 && thresholdsAreTooHigh(threshold)) {
            // ensure we have something to show even if no point crosses the similarity bound
            highlightCount = min(config.topK, config.pointCount)
            for (i in 0 until highlightCount) {
                highlightIndices[i] = i
            }
        }
        var highlighted = 0
        for (index in 0 until highlightCount) {
            val pointIndex = highlightIndices[index]
            val similarity = similarities[pointIndex]
            if (similarity < threshold) continue
            val px = positions[pointIndex * 3]
            val py = positions[pointIndex * 3 + 1]
            val pz = positions[pointIndex * 3 + 2]
            val distance = distance(queryOrigin.x, queryOrigin.y, queryOrigin.z, px, py, pz)
            val band = max(0f, 1f - abs(distance - radius) / (radius + 0.0001f))
            val similarityBoost = ((similarity - threshold) / max(0.0001f, 1f - threshold)).coerceIn(0f, 1f)
            val intensity = (band * similarityBoost * fade).pow(0.7f)
            applyHighlight(pointIndex, intensity)
            highlighted++
        }

        if (highlighted == 0) {
            shockwaveActive = false
            highlightCount = 0
            restoreBaseAppearance()
        }
    }

    private fun thresholdsAreTooHigh(threshold: Float): Boolean {
        // if almost all samples are below the threshold, treat the shockwave as a global pulse
        val satisfied = similarities.count { it >= threshold }
        return satisfied < config.topK / 4
    }

    private fun applyHighlight(index: Int, intensity: Float) {
        val baseColorIndex = index * 3
        val r = (baseColors[baseColorIndex] * (1f + intensity * 0.8f)).coerceAtMost(1f)
        val g = (baseColors[baseColorIndex + 1] * (1f + intensity * 0.9f)).coerceAtMost(1f)
        val b = (baseColors[baseColorIndex + 2] * (1f + intensity * 1.1f)).coerceAtMost(1f)
        points.setInstanceColor(index, r, g, b)
        val sizeBoost = 1f + intensity * 2.2f
        points.setInstanceSize(index, baseSizes[index] * sizeBoost)
    }

    private fun restoreBaseAppearance() {
        for (i in 0 until config.pointCount) {
            val baseIdx = i * 3
            points.setInstanceColor(i, baseColors[baseIdx], baseColors[baseIdx + 1], baseColors[baseIdx + 2])
            points.setInstanceSize(i, baseSizes[i])
        }
    }

    private fun computeSimilarities(queryIndex: Int) {
        val queryVector = normalizedVectors[queryIndex].components
        var maxDist = 0f
        for (i in 0 until config.pointCount) {
            val pointVector = normalizedVectors[i].components
            var dot = 0f
            for (d in 0 until config.dimension) {
                dot += queryVector[d] * pointVector[d]
            }
            similarities[i] = dot
            val px = positions[i * 3] - queryOrigin.x
            val py = positions[i * 3 + 1] - queryOrigin.y
            val pz = positions[i * 3 + 2] - queryOrigin.z
            val dist = sqrt(px * px + py * py + pz * pz)
            if (dist > maxDist) maxDist = dist
        }
        maxDistance = maxDist.coerceAtLeast(1f)

        val sorted = similarities.withIndex()
            .filter { it.value >= config.similarityThreshold }
            .sortedByDescending { it.value }
            .take(config.topK)
        highlightCount = sorted.size
        sorted.forEachIndexed { idx, value ->
            highlightIndices[idx] = value.index
        }
    }

    private fun prepareProjection() {
        for (axis in 0 until 3) {
            for (d in 0 until config.dimension) {
                projection[axis][d] = random.nextGaussianFloat() * 0.5f
            }
        }
    }

    private fun generateSyntheticData(): EmbeddingData {
        val centres = Array(config.clusterCount) { FloatArray(config.dimension) }
        val pointVectors = Array(config.pointCount) { FloatArray(config.dimension) }

        for (cluster in centres.indices) {
            fillGaussian(centres[cluster])
            normalize(centres[cluster])
        }

        val colors = Colors.brandCycle().map { it.clone() }

        var meanNorm = 0f
        var maxNorm = 0f
        var minNorm = Float.MAX_VALUE

        val basePositionArray = FloatArray(config.pointCount * 3)
        val baseColorArray = FloatArray(config.pointCount * 3)
        val baseSizeArray = FloatArray(config.pointCount)
        val serializablePoints = mutableListOf<EmbeddingPoint>()
        for (i in 0 until config.pointCount) {
            val cluster = i % config.clusterCount
            val vector = pointVectors[i]
            vector.indices.forEach { d ->
                vector[d] = centres[cluster][d] + random.nextGaussianFloat() * 0.35f
            }
            normalize(vector)
            normalizedVectors[i] = NormalizedVector(vector.copyOf())
            val norm = 1f
            meanNorm += norm
            maxNorm = max(maxNorm, norm)
            minNorm = min(minNorm, norm)

            val pos = project(vector)
            positions[i * 3] = pos.x
            positions[i * 3 + 1] = pos.y
            positions[i * 3 + 2] = pos.z
            geometry.updatePosition(i, pos.x, pos.y, pos.z)
            basePositionArray[i * 3] = pos.x
            basePositionArray[i * 3 + 1] = pos.y
            basePositionArray[i * 3 + 2] = pos.z

            val color = colors[cluster % colors.size]
            val baseIndex = i * 3
            baseColors[baseIndex] = color.r
            baseColors[baseIndex + 1] = color.g
            baseColors[baseIndex + 2] = color.b
            geometry.updateColor(i, color.r, color.g, color.b)
            baseColorArray[baseIndex] = color.r
            baseColorArray[baseIndex + 1] = color.g
            baseColorArray[baseIndex + 2] = color.b

            val size = 0.5f + random.nextFloat() * 1.2f
            baseSizes[i] = size
            geometry.updateSize(i, size)
            baseSizeArray[i] = size

            serializablePoints += EmbeddingPoint(
                id = "p$i",
                vector = vector.toList(),
                cluster = cluster,
                position = EmbeddingPosition(pos.x, pos.y, pos.z)
            )
        }

        geometry.setAttribute("position", BufferAttribute(basePositionArray, 3))
        geometry.setAttribute("color", BufferAttribute(baseColorArray, 3))
        geometry.setAttribute("size", BufferAttribute(baseSizeArray, 1))

        val stats = EmbeddingStatistics(
            pointCount = config.pointCount,
            dimension = config.dimension,
            clusterCount = config.clusterCount,
            meanNorm = meanNorm / config.pointCount,
            maxNorm = maxNorm,
            minNorm = minNorm
        )

        return EmbeddingData(
            statistics = stats,
            points = serializablePoints
        )
    }

    private fun project(vector: FloatArray): Vector3 {
        val x = dot(vector, projection[0]) * 90f
        val y = dot(vector, projection[1]) * 90f
        val z = dot(vector, projection[2]) * 90f
        return Vector3(x, y, z)
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    private fun normalize(target: FloatArray) {
        var lengthSq = 0f
        for (component in target) {
            lengthSq += component * component
        }
        val inv = if (lengthSq <= 0f) 1f else 1f / sqrt(lengthSq)
        for (i in target.indices) {
            target[i] *= inv
        }
    }

    private fun fillGaussian(target: FloatArray) {
        for (i in target.indices) {
            target[i] = random.nextGaussianFloat()
        }
    }

    private fun Random.nextGaussianFloat(): Float {
        val u1 = nextFloat().coerceIn(1e-6f, 1f)
        val u2 = nextFloat()
        val radius = sqrt((-2f * ln(u1)))
        val theta = (2f * kotlin.math.PI * u2).toFloat()
        return radius * kotlin.math.cos(theta)
    }

    private fun distance(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        val dz = az - bz
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        private const val CAMERA_LOOP_SECONDS = 8f
    }
}
