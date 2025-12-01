package io.materia.optimization

import io.materia.camera.Camera
import io.materia.core.math.*
import io.materia.core.platform.currentTimeMillis
import io.materia.core.scene.Material
import io.materia.core.scene.Mesh
import io.materia.core.scene.SpriteMaterial
import io.materia.geometry.BufferGeometry
import io.materia.renderer.Renderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.PI
import kotlin.random.Random

// Type alias for compatibility
typealias Geometry = BufferGeometry

/**
 * Instance data for a single instanced object
 */
data class InstanceData(
    val id: String,
    val transform: Matrix4,
    val color: Color = Color.WHITE,
    val customAttributes: Map<String, Any> = emptyMap(),
    var visible: Boolean = true,
    var culled: Boolean = false
)

/**
 * Instanced mesh batch for efficient GPU rendering
 */
class InstancedBatch(
    val geometry: Geometry,
    val material: Material,
    val maxInstances: Int = 10000
) {
    private val instances = mutableListOf<InstanceData>()
    private val instanceMatrices = FloatArray((maxInstances * 16))
    private val instanceColors = FloatArray((maxInstances * 4))
    private var dirty = true
    private var sortingEnabled = false
    private var sortingStrategy: SortingStrategy = SortingStrategy.NONE

    /**
     * Sorting strategies for instances
     */
    enum class SortingStrategy {
        NONE,
        FRONT_TO_BACK,
        BACK_TO_FRONT,
        BY_MATERIAL_STATE
    }

    /**
     * Add an instance to the batch
     */
    fun addInstance(instance: InstanceData): Boolean {
        if (instances.size >= maxInstances) {
            return false
        }

        instances.add(instance)
        dirty = true
        return true
    }

    /**
     * Remove an instance from the batch
     */
    fun removeInstance(id: String): Boolean {
        val toRemove = instances.find { it.id == id }
        val removed = if (toRemove != null) {
            instances.remove(toRemove)
        } else {
            false
        }
        if (removed) {
            dirty = true
        }
        return removed
    }

    /**
     * Update instance transform
     */
    fun updateInstance(id: String, transform: Matrix4) {
        instances.find { it.id == id }?.let {
            val index = instances.indexOf(it)
            instances[index] = it.copy(transform = transform)
            dirty = true
        }
    }

    /**
     * Update instance data buffers for GPU
     */
    fun updateBuffers() {
        if (!dirty) return

        instances.forEachIndexed { index, instance ->
            if (!instance.culled && instance.visible) {
                // Copy transform matrix
                val matrixOffset = index * 16
                instance.transform.toArray().forEachIndexed { i, value ->
                    instanceMatrices[matrixOffset + i] = value
                }

                // Copy color
                val colorOffset = index * 4
                instanceColors[colorOffset] = instance.color.r
                instanceColors[colorOffset + 1] = instance.color.g
                instanceColors[colorOffset + 2] = instance.color.b
                instanceColors[colorOffset + 3] = 1.0f // Alpha always 1
            }
        }

        dirty = false
    }

    /**
     * Sort instances based on strategy
     */
    fun sortInstances(cameraPosition: Vector3) {
        if (!sortingEnabled) return

        when (sortingStrategy) {
            SortingStrategy.FRONT_TO_BACK -> {
                instances.sortBy { instance ->
                    val position = instance.transform.extractTranslation()
                    position.distanceTo(cameraPosition)
                }
            }

            SortingStrategy.BACK_TO_FRONT -> {
                instances.sortByDescending { instance ->
                    val position = instance.transform.extractTranslation()
                    position.distanceTo(cameraPosition)
                }
            }

            SortingStrategy.BY_MATERIAL_STATE -> {
                // Sort by material properties to minimize state changes
                instances.sortBy { it.customAttributes["materialOrder"] as? Int ?: 0 }
            }

            SortingStrategy.NONE -> {
                // No sorting
            }
        }

        dirty = true
    }

    /**
     * Perform frustum culling on instances
     */
    fun cullInstances(frustum: Frustum, boundingBox: BoundingBox) {
        var culledCount = 0

        instances.forEach { instance ->
            val worldBounds = boundingBox.transform(instance.transform)
            instance.culled = !frustum.intersectsBox(worldBounds)
            if (instance.culled) culledCount++
        }

        if (culledCount > 0) {
            dirty = true
        }
    }

    /**
     * Get active instance count (visible and not culled)
     */
    fun getActiveCount(): Int {
        return instances.count { it.visible && !it.culled }
    }

    /**
     * Get instance data arrays for rendering
     */
    fun getInstanceData(): Pair<FloatArray, FloatArray> {
        updateBuffers()
        return instanceMatrices to instanceColors
    }

    /**
     * Clear all instances
     */
    fun clear() {
        instances.clear()
        dirty = true
    }

    /**
     * Check if batch can accept more instances
     */
    fun hasCapacity(count: Int = 1): Boolean {
        return instances.size + count <= maxInstances
    }
}

/**
 * Instance manager for efficient GPU instancing
 */
class InstanceManager(
    private val renderer: Renderer,
    private val autoBatchThreshold: Int = 100,
    private val maxInstancesPerBatch: Int = 10000
) {
    private val batches = mutableMapOf<String, InstancedBatch>()
    private val meshToBatch = mutableMapOf<Mesh, String>()
    private val statistics = InstanceStatistics()
    private val batchingScope = CoroutineScope(Dispatchers.Default)

    /**
     * Register mesh for instancing
     */
    fun registerMesh(mesh: Mesh): String {
        val material = mesh.material ?: return "" // Skip meshes without materials
        val batchKey = generateBatchKey(mesh.geometry, material)

        // Find or create batch
        val batch = batches.getOrPut(batchKey) {
            InstancedBatch(
                geometry = mesh.geometry,
                material = material,
                maxInstances = maxInstancesPerBatch
            )
        }

        meshToBatch[mesh] = batchKey
        statistics.totalMeshes++

        return batchKey
    }

    /**
     * Add instance of a mesh
     */
    fun addInstance(
        mesh: Mesh,
        transform: Matrix4,
        color: Color = Color.WHITE,
        customAttributes: Map<String, Any> = emptyMap()
    ): String? {
        val batchKey = meshToBatch[mesh] ?: registerMesh(mesh)
        val batch = batches[batchKey] ?: return null

        val instanceId = generateInstanceId()
        val instance = InstanceData(
            id = instanceId,
            transform = transform,
            color = color,
            customAttributes = customAttributes
        )

        if (batch.addInstance(instance)) {
            statistics.totalInstances++
            checkAutoBatching(batchKey)
            return instanceId
        }

        // Batch full, try to create new batch
        val newBatch = createOverflowBatch(mesh)
        if (newBatch?.addInstance(instance) == true) {
            statistics.totalInstances++
            return instanceId
        }

        return null
    }

    /**
     * Create overflow batch when primary is full
     */
    private fun createOverflowBatch(mesh: Mesh): InstancedBatch? {
        val baseBatchKey = meshToBatch[mesh] ?: return null
        var overflowIndex = 1

        // Use a reasonable maximum to prevent infinite loops
        val maxOverflowBatches = 1000

        while (overflowIndex <= maxOverflowBatches) {
            val overflowKey = "${baseBatchKey}_overflow_$overflowIndex"
            if (!batches.containsKey(overflowKey)) {
                val batch = InstancedBatch(
                    geometry = mesh.geometry,
                    material = mesh.material ?: SpriteMaterial(), // Default material if null
                    maxInstances = maxInstancesPerBatch
                )
                batches[overflowKey] = batch
                statistics.totalBatches++
                return batch
            }

            val existingBatch = batches[overflowKey]
            if (existingBatch?.hasCapacity() == true) {
                return existingBatch
            }

            overflowIndex++
        }

        return null
    }

    /**
     * Remove instance
     */
    fun removeInstance(instanceId: String): Boolean {
        batches.values.forEach { batch ->
            if (batch.removeInstance(instanceId)) {
                statistics.totalInstances--
                return true
            }
        }
        return false
    }

    /**
     * Update instance transform
     */
    fun updateInstanceTransform(instanceId: String, transform: Matrix4) {
        batches.values.forEach { batch ->
            batch.updateInstance(instanceId, transform)
        }
    }

    /**
     * Stream instance data for dynamic updates
     */
    fun streamInstanceData(): Flow<List<InstanceData>> = flow {
        while (currentCoroutineContext().isActive) {
            val allInstances = batches.values.flatMap { batch ->
                // Get instances from batch (would need to expose this)
                emptyList<InstanceData>()
            }
            emit(allInstances)
            delay(16) // ~60 FPS
        }
    }

    /**
     * Perform frustum culling on all batches
     */
    fun performCulling(frustum: Frustum) {
        statistics.cullingStart()

        batches.values.forEach { batch ->
            val boundingBox = batch.geometry.boundingBox ?: return@forEach
            batch.cullInstances(frustum, boundingBox)

            val activeCount = batch.getActiveCount()
            statistics.recordCulling(activeCount)
        }

        statistics.cullingEnd()
    }

    /**
     * Check and perform automatic batching
     */
    private fun checkAutoBatching(batchKey: String) {
        val batch = batches[batchKey] ?: return
        val activeCount = batch.getActiveCount()

        if (activeCount >= autoBatchThreshold) {
            // Trigger automatic batching optimization
            batchingScope.launch {
                optimizeBatch(batch)
            }
        }
    }

    /**
     * Optimize batch for better performance
     */
    private suspend fun optimizeBatch(batch: InstancedBatch) = withContext(Dispatchers.Default) {
        // Sort instances for better GPU cache coherency
        batch.sortInstances(Vector3(0f, 0f, 0f)) // Use camera position in real scenario

        // Update buffers
        batch.updateBuffers()

        statistics.recordOptimization()
    }

    /**
     * Render all instance batches
     */
    fun render(renderer: Renderer, camera: Camera) {
        statistics.frameStart()

        batches.values.forEach { batch ->
            val activeCount = batch.getActiveCount()
            if (activeCount > 0) {
                // Sort if needed
                batch.sortInstances(camera.position)

                // Get instance data
                val (matrices, colors) = batch.getInstanceData()

                // Render instanced geometry via GPU instancing
                // GPU instancing support requires WebGPU/Vulkan renderer integration
                // renderer.renderInstanced(
                //     geometry = batch.geometry,
                //     material = batch.material,
                //     instanceMatrices = matrices,
                //     instanceColors = colors,
                //     instanceCount = activeCount
                // )

                statistics.recordDrawCall(activeCount)
            }
        }

        statistics.frameEnd()
    }

    /**
     * Generate batch key from geometry and material
     */
    private fun generateBatchKey(geometry: Geometry, material: Material): String {
        return "${geometry.hashCode()}_${material.hashCode()}"
    }

    /**
     * Generate unique instance ID
     */
    private fun generateInstanceId(): String {
        return "instance_${currentTimeMillis()}_${(Random.nextFloat() * 10000).toInt()}"
    }

    /**
     * Get statistics
     */
    fun getStatistics(): InstanceStatistics = statistics

    /**
     * Clear all batches
     */
    fun clear() {
        batches.values.forEach { it.clear() }
        batches.clear()
        meshToBatch.clear()
        statistics.reset()
    }
}

/**
 * Instance rendering statistics
 */
class InstanceStatistics {
    var totalMeshes = 0
    var totalBatches = 0
    var totalInstances = 0
    private var currentFrame = 0L
    private var drawCalls = 0
    private var instancesDrawn = 0
    private var culledInstances = 0
    private var optimizationCount = 0

    fun frameStart() {
        currentFrame++
        drawCalls = 0
        instancesDrawn = 0
    }

    fun recordDrawCall(instanceCount: Int) {
        drawCalls++
        instancesDrawn = instancesDrawn + instanceCount
    }

    fun cullingStart() {
        culledInstances = 0
    }

    fun recordCulling(activeCount: Int) {
        // Track culling effectiveness
    }

    fun cullingEnd() {
        // Process culling statistics
    }

    fun recordOptimization() {
        optimizationCount++
    }

    fun frameEnd() {
        // Process frame statistics
    }

    fun reset() {
        totalMeshes = 0
        totalBatches = 0
        totalInstances = 0
        currentFrame = 0
        drawCalls = 0
        instancesDrawn = 0
        culledInstances = 0
        optimizationCount = 0
    }

    fun getAverageInstancesPerDraw(): Float {
        return if (drawCalls > 0) instancesDrawn.toFloat() / drawCalls else 0f
    }

    fun getBatchingEfficiency(): Float {
        return if (totalInstances > 0) {
            1.0f - (drawCalls.toFloat() / totalInstances)
        } else 0f
    }
}

/**
 * Utilities for instance management
 */
object InstanceUtils {
    /**
     * Create grid of instances
     */
    fun createGrid(
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        spacing: Float = 1.0f
    ): List<Matrix4> {
        val transforms = mutableListOf<Matrix4>()

        for (x in 0 until sizeX) {
            for (y in 0 until sizeY) {
                for (z in 0 until sizeZ) {
                    val position = Vector3(
                        x * spacing - ((sizeX * spacing)) / 2f,
                        y * spacing - ((sizeY * spacing)) / 2f,
                        z * spacing - ((sizeZ * spacing)) / 2f
                    )
                    val matrix = Matrix4()
                    matrix.makeTranslation(position.x, position.y, position.z)
                    transforms.add(matrix)
                }
            }
        }

        return transforms
    }

    /**
     * Create random distribution of instances
     */
    fun createRandomDistribution(
        count: Int,
        bounds: BoundingBox,
        minScale: Float = 0.5f,
        maxScale: Float = 2.0f
    ): List<Matrix4> {
        return (0 until count).map {
            val position = Vector3(
                bounds.min.x + Random.nextFloat() * (bounds.max.x - bounds.min.x),
                bounds.min.y + Random.nextFloat() * (bounds.max.y - bounds.min.y),
                bounds.min.z + Random.nextFloat() * (bounds.max.z - bounds.min.z)
            )

            val scale = minScale + Random.nextFloat() * (maxScale - minScale)
            val euler = Euler(
                Random.nextFloat() * PI.toFloat() * 2,
                Random.nextFloat() * PI.toFloat() * 2,
                Random.nextFloat() * PI.toFloat() * 2
            )
            val rotation = Quaternion.fromEuler(euler)

            val matrix = Matrix4()
            matrix.makeRotationFromQuaternion(rotation)
            matrix.makeScale(scale, scale, scale)
            matrix.setPosition(position)
            matrix
        }
    }
}