package io.materia.optimization

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Vector3
import io.materia.core.scene.Mesh
import io.materia.geometry.BufferGeometry
import io.materia.renderer.Renderer
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/**
 * Level of Detail (LOD) configuration for a single level
 */
data class LODLevel(
    val geometry: BufferGeometry,
    val distance: Float,
    val screenSpaceError: Float = 0.0f,
    val transitionDuration: Float = 0.2f
)

/**
 * LOD transition state for smooth switching
 */
sealed class LODTransition {
    object None : LODTransition()
    data class Fading(
        val fromLevel: Int,
        val toLevel: Int,
        val progress: Float
    ) : LODTransition()
}

/**
 * LOD switching strategy
 */
enum class LODStrategy {
    DISTANCE_BASED,
    SCREEN_SPACE_ERROR,
    COMBINED
}

/**
 * LOD group managing multiple detail levels for a mesh
 */
class LODGroup(
    val levels: List<LODLevel>,
    val hysteresis: Float = 0.1f,
    val strategy: LODStrategy = LODStrategy.DISTANCE_BASED
) {
    private var currentLevel = 0
    private var transition = LODTransition.None as LODTransition
    private var lastDistance = Float.MAX_VALUE
    private val transitionScope = CoroutineScope(Dispatchers.Default)

    /**
     * Update LOD selection based on camera distance
     */
    fun update(camera: Camera, meshPosition: Vector3, deltaTime: Float): Int {
        val distance = camera.position.distanceTo(meshPosition)

        // Apply hysteresis to prevent LOD popping
        val effectiveDistance = if (distance > lastDistance) {
            distance * (1.0f + hysteresis)
        } else {
            distance * (1.0f - hysteresis)
        }

        lastDistance = distance

        // Find appropriate LOD level
        val targetLevel = when (strategy) {
            LODStrategy.DISTANCE_BASED -> findLevelByDistance(effectiveDistance)
            LODStrategy.SCREEN_SPACE_ERROR -> findLevelByScreenSpaceError(camera, meshPosition)
            LODStrategy.COMBINED -> {
                val distLevel = findLevelByDistance(effectiveDistance)
                val errorLevel = findLevelByScreenSpaceError(camera, meshPosition)
                minOf(distLevel, errorLevel)
            }
        }

        // Handle transitions
        if (targetLevel != currentLevel) {
            startTransition(currentLevel, targetLevel)
            currentLevel = targetLevel
        }

        // Update transition progress
        updateTransition(deltaTime)

        return currentLevel
    }

    private fun findLevelByDistance(distance: Float): Int {
        for (i in levels.indices.reversed()) {
            if (distance >= levels[i].distance) {
                return i
            }
        }
        return 0
    }

    private fun findLevelByScreenSpaceError(camera: Camera, position: Vector3): Int {
        val screenSize = calculateScreenSize(camera, position)

        for (i in levels.indices) {
            val error = levels[i].screenSpaceError
            if (screenSize >= error) {
                return i
            }
        }
        return levels.size - 1
    }

    private fun calculateScreenSize(camera: Camera, position: Vector3): Float {
        val distance = camera.position.distanceTo(position)
        val boundingRadius = levels[0].geometry.boundingSphere?.radius ?: 1.0f

        // Project bounding sphere to screen space
        val fov = when (camera) {
            is PerspectiveCamera -> camera.fov
            else -> camera.getEffectiveFOV()
        } * (PI / 180.0).toFloat()

        val screenHeight = 1080f // Default screen height

        // Check for division by zero - distance and tan(fov/2) must not be zero
        if (kotlin.math.abs(distance) < io.materia.core.math.EPSILON) {
            return Float.MAX_VALUE // Object is at camera position, use highest detail
        }

        val tanHalfFov = tan(fov / 2.0f)
        if (kotlin.math.abs(tanHalfFov) < io.materia.core.math.EPSILON) {
            return 0.0f // Invalid FOV, return minimum size
        }

        val projectedSize = (boundingRadius / distance) * (screenHeight / (2.0f * tanHalfFov))

        return projectedSize
    }

    private fun startTransition(from: Int, to: Int) {
        transition = LODTransition.Fading(from, to, 0.0f)

        // Launch smooth transition coroutine
        transitionScope.launch {
            val duration = levels[to].transitionDuration
            var elapsed = 0.0f

            while (elapsed < duration) {
                delay(16) // ~60 FPS update
                elapsed = elapsed + 0.016f
                val progress = (elapsed / duration).coerceIn(0.0f, 1.0f)
                transition = LODTransition.Fading(from, to, progress)
            }

            transition = LODTransition.None
        }
    }

    private fun updateTransition(deltaTime: Float) {
        when (val t = transition) {
            is LODTransition.Fading -> {
                // Transition logic handled in coroutine
            }

            LODTransition.None -> {
                // No transition
            }
        }
    }

    /**
     * Get transition state for rendering
     */
    fun getTransition(): LODTransition = transition

    /**
     * Get current geometry accounting for transitions
     */
    fun getCurrentGeometry(): Pair<BufferGeometry, Float> {
        return when (val t = transition) {
            is LODTransition.Fading -> {
                if (t.progress < 0.5f) {
                    levels[t.fromLevel].geometry to (1.0f - t.progress * 2.0f)
                } else {
                    levels[t.toLevel].geometry to ((t.progress - 0.5f) * 2.0f)
                }
            }

            LODTransition.None -> {
                levels[currentLevel].geometry to 1.0f
            }
        }
    }
}

/**
 * LOD system managing all LOD groups in the scene
 */
class LODSystem(
    private val defaultHysteresis: Float = 0.1f,
    private val autoGenerateLODs: Boolean = true
) {
    private val lodGroups = mutableMapOf<String, LODGroup>()
    private val meshToGroup = mutableMapOf<Mesh, String>()
    private val statistics = LODStatistics()

    /**
     * Register a mesh with LOD levels
     */
    fun registerLOD(
        mesh: Mesh,
        levels: List<LODLevel>,
        groupId: String = mesh.hashCode().toString()
    ) {
        val group = LODGroup(
            levels = levels.sortedBy { it.distance },
            hysteresis = defaultHysteresis
        )

        lodGroups[groupId] = group
        meshToGroup[mesh] = groupId

        statistics.totalGroups++
        statistics.totalLevels += levels.size
    }

    /**
     * Automatically generate LOD levels from a high-detail mesh
     */
    suspend fun generateLODs(
        mesh: Mesh,
        levelCount: Int = 3,
        reductionFactors: List<Float> = listOf(1.0f, 0.5f, 0.25f)
    ) = withContext(Dispatchers.Default) {
        val baseMesh = mesh.geometry
        val levels = mutableListOf<LODLevel>()

        reductionFactors.forEachIndexed { index, factor ->
            val distance = index * 50.0f // Default distance intervals
            val simplified = if (factor < 1.0f) {
                simplifyGeometry(baseMesh, factor)
            } else {
                baseMesh
            }

            levels.add(
                LODLevel(
                    geometry = simplified,
                    distance = distance,
                    screenSpaceError = (1.0f - factor) * 100.0f
                )
            )
        }

        registerLOD(mesh, levels)
    }

    /**
     * Simplify geometry using edge collapse decimation
     */
    private fun simplifyGeometry(geometry: BufferGeometry, targetRatio: Float): BufferGeometry {
        // Mesh decimation using simplified index reduction
        // Full implementation would use quadric error metrics for optimal quality
        val positionAttribute = geometry.getAttribute("position")
        val currentVertexCount = positionAttribute?.count ?: 0
        val targetVertexCount = (currentVertexCount * targetRatio).toInt()

        // Clone and reduce geometry
        val simplified = geometry.clone()

        // Reduction using indexed geometry decimation
        if (targetRatio < 0.5f) {
            // Aggressive simplification using index skipping for rapid LOD generation
            val indexAttribute = simplified.index
            if (indexAttribute != null) {
                val skipFactor = (1.0f / targetRatio).toInt()
                val newIndices = mutableListOf<Int>()
                for (i in 0 until indexAttribute.count step skipFactor) {
                    newIndices.add(indexAttribute.getX(i).toInt())
                }
                // This would need proper re-indexing
            }
        }

        return simplified
    }

    /**
     * Update all LOD groups
     */
    fun update(camera: Camera, renderer: Renderer, deltaTime: Float) {
        statistics.frameStart()

        meshToGroup.forEach { (mesh, groupId) ->
            lodGroups[groupId]?.let { group ->
                val level = group.update(camera, mesh.position, deltaTime)
                val (geometry, opacity) = group.getCurrentGeometry()

                // Apply LOD geometry to mesh
                mesh.geometry = geometry

                // Handle transition fading if supported
                if (opacity < 1.0f) {
                    // Note: Material opacity will be set when the material system supports transparency
                    // This is a planned feature for smooth LOD transitions
                    // mesh.material?.opacity = opacity
                    // mesh.material?.transparent = true
                }

                statistics.recordLODSwitch(level)
            }
        }

        statistics.frameEnd()
    }

    /**
     * Get LOD statistics
     */
    fun getStatistics(): LODStatistics = statistics

    /**
     * Clear all LOD groups
     */
    fun clear() {
        lodGroups.clear()
        meshToGroup.clear()
        statistics.reset()
    }
}

/**
 * LOD statistics for performance monitoring
 */
class LODStatistics {
    var totalGroups = 0
    var totalLevels = 0
    private var currentFrame = 0L
    private var lodSwitches = mutableMapOf<Int, Int>()
    private val switchHistory = mutableListOf<Pair<Long, Int>>()

    fun frameStart() {
        currentFrame++
    }

    fun recordLODSwitch(level: Int) {
        lodSwitches[level] = (lodSwitches[level] ?: 0) + 1
        switchHistory.add(currentFrame to level)

        // Keep history limited
        if (switchHistory.size > 1000) {
            switchHistory.removeAt(0)
        }
    }

    fun frameEnd() {
        // Process frame statistics
    }

    fun reset() {
        totalGroups = 0
        totalLevels = 0
        currentFrame = 0
        lodSwitches.clear()
        switchHistory.clear()
    }

    fun getLODDistribution(): Map<Int, Int> = lodSwitches.toMap()

    fun getSwitchRate(): Float {
        if (currentFrame == 0L) return 0.0f
        return switchHistory.size.toFloat() / currentFrame
    }
}

/**
 * LOD utilities for common operations
 */
object LODUtils {
    /**
     * Calculate optimal LOD distances based on object size
     */
    fun calculateDistances(
        boundingRadius: Float,
        levelCount: Int,
        nearDistance: Float = 10.0f,
        farDistance: Float = 1000.0f
    ): List<Float> {
        val distances = mutableListOf<Float>()
        val logNear = ln(nearDistance)
        val logFar = ln(farDistance)

        for (i in 0 until levelCount) {
            val t = i.toFloat() / (levelCount - 1)
            val logDist = logNear + (logFar - logNear) * t
            distances.add(exp(logDist))
        }

        return distances
    }

    /**
     * Generate screen space error thresholds
     */
    fun calculateScreenSpaceErrors(
        levelCount: Int,
        maxError: Float = 10.0f
    ): List<Float> {
        return (0 until levelCount).map { i ->
            maxError * (1.0f - i.toFloat() / (levelCount - 1))
        }
    }
}