package io.materia.optimization

import io.materia.core.math.*
import io.materia.core.platform.currentTimeMillis
import io.materia.camera.Camera
import io.materia.core.scene.Mesh
import io.materia.renderer.Renderer
import kotlinx.coroutines.*
import kotlin.math.*

// Type alias for compatibility
typealias BoundingBox = Box3

/**
 * Represents a plane in 3D space (ax + by + cz + d = 0).
 */
private class FrustumPlane {
    var a = 0f
    var b = 0f
    var c = 0f
    var d = 0f

    fun set(a: Float, b: Float, c: Float, d: Float): FrustumPlane {
        this.a = a
        this.b = b
        this.c = c
        this.d = d
        return this
    }

    fun normalize(): FrustumPlane {
        val length = kotlin.math.sqrt(a * a + b * b + c * c)
        if (length > 0) {
            val invLength = 1f / length
            a *= invLength
            b *= invLength
            c *= invLength
            d *= invLength
        }
        return this
    }

    fun distanceToPoint(point: Vector3): Float {
        return a * point.x.toFloat() + b * point.y.toFloat() + c * point.z.toFloat() + d
    }
}

/**
 * View frustum for visibility culling
 */
class Frustum {
    // Frustum planes (left, right, bottom, top, near, far)
    private val planes = Array(6) { FrustumPlane() }

    /**
     * Extracts frustum planes from camera's projection-view matrix.
     * @param camera Camera with projection and view matrices
     */
    fun setFromMatrix(matrix: Matrix4) {
        val me = matrix.toArray()

        // Extract frustum planes using Gribb-Hartmann method
        // Left plane
        planes[0].set(
            me[3] + me[0],
            me[7] + me[4],
            me[11] + me[8],
            me[15] + me[12]
        ).normalize()

        // Right plane
        planes[1].set(
            me[3] - me[0],
            me[7] - me[4],
            me[11] - me[8],
            me[15] - me[12]
        ).normalize()

        // Bottom plane
        planes[2].set(
            me[3] + me[1],
            me[7] + me[5],
            me[11] + me[9],
            me[15] + me[13]
        ).normalize()

        // Top plane
        planes[3].set(
            me[3] - me[1],
            me[7] - me[5],
            me[11] - me[9],
            me[15] - me[13]
        ).normalize()

        // Near plane
        planes[4].set(
            me[3] + me[2],
            me[7] + me[6],
            me[11] + me[10],
            me[15] + me[14]
        ).normalize()

        // Far plane
        planes[5].set(
            me[3] - me[2],
            me[7] - me[6],
            me[11] - me[10],
            me[15] - me[14]
        ).normalize()
    }

    fun intersectsBox(box: BoundingBox): Boolean {
        for (plane in planes) {
            // Find the positive vertex (furthest along the normal)
            val positive = Vector3(
                if (plane.a >= 0) box.max.x else box.min.x,
                if (plane.b >= 0) box.max.y else box.min.y,
                if (plane.c >= 0) box.max.z else box.min.z
            )
            if (plane.distanceToPoint(positive) < 0) {
                return false
            }
        }
        return true
    }
}

/**
 * Spatial data structure for efficient culling
 */
sealed class SpatialNode {
    abstract val bounds: BoundingBox
    abstract val objects: MutableList<Mesh>
}

/**
 * Octree node for hierarchical culling
 */
class OctreeNode(
    override val bounds: BoundingBox,
    val depth: Int = 0,
    val maxDepth: Int = 8,
    val maxObjects: Int = 8
) : SpatialNode() {
    override val objects = mutableListOf<Mesh>()
    private var children: Array<OctreeNode>? = null

    /**
     * Insert object into octree
     */
    fun insert(mesh: Mesh): Boolean {
        val meshBounds = mesh.geometry.boundingBox ?: return false

        if (!bounds.containsBox(meshBounds)) {
            return false
        }

        val childNodes = children
        if (childNodes != null) {
            // Insert into children
            childNodes.forEach { child ->
                if (child.insert(mesh)) {
                    return true
                }
            }
            // Object spans multiple children, store in this node
            objects.add(mesh)
            return true
        }

        objects.add(mesh)

        // Split if necessary
        if (objects.size > maxObjects && depth < maxDepth) {
            split()
        }

        return true
    }

    /**
     * Split node into 8 children
     */
    private fun split() {
        val halfSize = (bounds.max - bounds.min) * 0.5f
        val center = (bounds.min + bounds.max) * 0.5f

        children = Array(8) { index ->
            val childMin = Vector3(
                if (index and 1 != 0) center.x else bounds.min.x,
                if (index and 2 != 0) center.y else bounds.min.y,
                if (index and 4 != 0) center.z else bounds.min.z
            )
            val childMax = childMin + halfSize

            OctreeNode(
                BoundingBox(childMin, childMax),
                depth + 1,
                maxDepth,
                maxObjects
            )
        }

        // Redistribute objects
        val objectsToRedistribute = objects.toList()
        objects.clear()

        objectsToRedistribute.forEach { mesh ->
            var inserted = false
            val childNodes = children ?: return
            childNodes.forEach { child ->
                if (child.insert(mesh)) {
                    inserted = true
                    return@forEach
                }
            }
            if (!inserted) {
                objects.add(mesh) // Keep in parent if spans children
            }
        }
    }

    /**
     * Query visible objects using frustum culling
     */
    fun query(frustum: Frustum, results: MutableList<Mesh>) {
        if (!frustum.intersectsBox(bounds)) {
            return
        }

        results.addAll(objects)

        children?.forEach { child ->
            child.query(frustum, results)
        }
    }

    /**
     * Clear the octree
     */
    fun clear() {
        objects.clear()
        children?.forEach { it.clear() }
        children = null
    }
}

/**
 * Hierarchical Z-Buffer for occlusion culling
 */
class HierarchicalZBuffer(
    private val width: Int = 256,
    private val height: Int = 256,
    private val levels: Int = 8
) {
    private val buffers = Array(levels) { level ->
        val levelWidth = width shr level
        val levelHeight = height shr level
        FloatArray((levelWidth * levelHeight)) { Float.MAX_VALUE }
    }

    /**
     * Update Z-buffer with rendered depth
     */
    fun update(depthBuffer: FloatArray) {
        // Copy base level
        depthBuffer.copyInto(buffers[0])

        // Build hierarchy
        for (level in 1 until levels) {
            buildLevel(level)
        }
    }

    /**
     * Build mip level from previous level
     */
    private fun buildLevel(level: Int) {
        val prevLevel = level - 1
        val prevWidth = width shr prevLevel
        val currWidth = width shr level
        val currHeight = height shr level

        for (y in 0 until currHeight) {
            for (x in 0 until currWidth) {
                val px = x * 2
                val py = y * 2

                // Take maximum depth of 2x2 block
                val z00 = getDepth(prevLevel, px, py)
                val z10 = getDepth(prevLevel, px + 1, py)
                val z01 = getDepth(prevLevel, px, py + 1)
                val z11 = getDepth(prevLevel, px + 1, py + 1)

                val maxDepth = maxOf(z00, z10, z01, z11)
                setDepth(level, x, y, maxDepth)
            }
        }
    }

    /**
     * Test if bounding box is occluded
     */
    fun isOccluded(box: BoundingBox, viewProjection: Matrix4): Boolean {
        // Project box to screen space
        val screenBox = projectToScreen(box, viewProjection)

        // Find appropriate mip level
        val level = selectMipLevel(screenBox)

        // Sample Z-buffer
        val minX = (screenBox.min.x * (width shr level)).toInt().coerceIn(0, (width shr level) - 1)
        val maxX = (screenBox.max.x * (width shr level)).toInt().coerceIn(0, (width shr level) - 1)
        val minY =
            (screenBox.min.y * (height shr level)).toInt().coerceIn(0, (height shr level) - 1)
        val maxY =
            (screenBox.max.y * (height shr level)).toInt().coerceIn(0, (height shr level) - 1)

        val boxDepth = screenBox.min.z

        // Check if all samples are behind stored depth
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                if (getDepth(level, x, y) > boxDepth) {
                    return false // Not occluded
                }
            }
        }

        return true // Fully occluded
    }

    private fun projectToScreen(box: BoundingBox, viewProjection: Matrix4): BoundingBox {
        // Simplified screen projection
        val corners = box.getCorners()
        var minScreen = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var maxScreen = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        corners.forEach { corner ->
            val projected = viewProjection.multiplyPoint3(corner)
            // Convert to screen space [0, 1]
            val screen = Vector3(
                (projected.x + 1f) * 0.5f,
                (projected.y + 1f) * 0.5f,
                projected.z
            )
            minScreen = minScreen.min(screen)
            maxScreen = maxScreen.max(screen)
        }

        return BoundingBox(minScreen, maxScreen)
    }

    private fun selectMipLevel(screenBox: BoundingBox): Int {
        val size = maxOf(
            screenBox.max.x - screenBox.min.x,
            screenBox.max.y - screenBox.min.y
        )
        return (log2(1f / size).toInt()).coerceIn(0, levels - 1)
    }

    private fun getDepth(level: Int, x: Int, y: Int): Float {
        val width = width shr level
        return buffers[level][y * width + x]
    }

    private fun setDepth(level: Int, x: Int, y: Int, depth: Float) {
        val width = width shr level
        buffers[level][y * width + x] = depth
    }

    fun clear() {
        buffers.forEach { it.fill(Float.MAX_VALUE) }
    }
}

/**
 * Comprehensive culling system
 */
class CullingSystem(
    private val renderer: Renderer,
    private val enableOcclusion: Boolean = true,
    private val enableDistance: Boolean = true,
    private val enableSmallObject: Boolean = true
) {
    private val frustum = Frustum()
    private val octree = OctreeNode(
        BoundingBox(
            Vector3(-1000f, -1000f, -1000f),
            Vector3(1000f, 1000f, 1000f)
        )
    )
    private val hierarchicalZBuffer = if (enableOcclusion) {
        HierarchicalZBuffer()
    } else null
    private val statistics = CullingStatistics()

    // Culling configuration
    var maxRenderDistance = 1000f
    var minObjectScreenSize = 2f // pixels

    /**
     * Add object to culling system
     */
    fun addObject(mesh: Mesh) {
        octree.insert(mesh)
        statistics.totalObjects++
    }

    /**
     * Remove object from culling system
     */
    fun removeObject(mesh: Mesh) {
        // Would need to implement removal from octree
        statistics.totalObjects--
    }

    /**
     * Perform all culling operations
     */
    fun cull(camera: Camera): List<Mesh> {
        statistics.frameStart()

        // Update frustum from camera
        val viewProjection = camera.projectionMatrix.clone().multiply(camera.viewMatrix)
        frustum.setFromMatrix(viewProjection)

        // Frustum culling via octree
        val frustumCulled = mutableListOf<Mesh>()
        octree.query(frustum, frustumCulled)
        statistics.frustumCulled = statistics.totalObjects - frustumCulled.size

        // Distance culling
        val distanceCulled = if (enableDistance) {
            frustumCulled.filter { mesh ->
                val distance = camera.position.distanceTo(mesh.position)
                distance <= maxRenderDistance
            }
        } else frustumCulled
        statistics.distanceCulled = frustumCulled.size - distanceCulled.size

        // Small object culling
        val smallObjectCulled = if (enableSmallObject) {
            distanceCulled.filter { mesh ->
                val screenSize = calculateScreenSize(mesh, camera, viewProjection)
                screenSize >= minObjectScreenSize
            }
        } else distanceCulled
        statistics.smallObjectCulled = distanceCulled.size - smallObjectCulled.size

        // Occlusion culling
        val finalVisible = if (enableOcclusion && hierarchicalZBuffer != null) {
            smallObjectCulled.filter { mesh ->
                val box = mesh.geometry.boundingBox ?: return@filter true
                !hierarchicalZBuffer.isOccluded(box, viewProjection)
            }
        } else smallObjectCulled
        statistics.occlusionCulled = smallObjectCulled.size - finalVisible.size

        statistics.visibleObjects = finalVisible.size
        statistics.frameEnd()

        return finalVisible
    }

    /**
     * Calculate screen size of object
     */
    private fun calculateScreenSize(mesh: Mesh, camera: Camera, viewProjection: Matrix4): Float {
        val bounds = mesh.geometry.boundingBox ?: return Float.MAX_VALUE
        val sphere = mesh.geometry.boundingSphere ?: return Float.MAX_VALUE

        val distance = camera.position.distanceTo(mesh.position)
        if (distance <= 0) return Float.MAX_VALUE

        // Project bounding sphere to screen
        val angularSize = 2f * atan(sphere.radius / distance)
        val screenHeight = 1080f // Default screen height, should be passed from renderer
        val fov = (camera as? io.materia.camera.PerspectiveCamera)?.fov ?: 50f
        val fovRad = fov * (PI / 180f).toFloat()

        return (angularSize / fovRad) * screenHeight
    }

    /**
     * Update occlusion buffer with rendered depth
     */
    fun updateOcclusionBuffer(depthBuffer: FloatArray) {
        hierarchicalZBuffer?.update(depthBuffer)
    }

    /**
     * Get culling statistics
     */
    fun getStatistics(): CullingStatistics = statistics

    /**
     * Clear culling system
     */
    fun clear() {
        octree.clear()
        hierarchicalZBuffer?.clear()
        statistics.reset()
    }
}

/**
 * Culling statistics
 */
class CullingStatistics {
    var totalObjects = 0
    var visibleObjects = 0
    var frustumCulled = 0
    var occlusionCulled = 0
    var distanceCulled = 0
    var smallObjectCulled = 0
    private var frameCount = 0L
    private var cullTime = 0L

    fun frameStart() {
        frameCount++
        cullTime = currentTimeMillis()
    }

    fun frameEnd() {
        cullTime = currentTimeMillis() - cullTime
    }

    fun reset() {
        totalObjects = 0
        visibleObjects = 0
        frustumCulled = 0
        occlusionCulled = 0
        distanceCulled = 0
        smallObjectCulled = 0
        frameCount = 0
        cullTime = 0
    }

    fun getCullingRatio(): Float {
        return if (totalObjects > 0) {
            1f - (visibleObjects.toFloat() / totalObjects)
        } else 0f
    }

    fun getAverageCullTime(): Float {
        return if (frameCount > 0) {
            cullTime.toFloat() / frameCount
        } else 0f
    }
}

/**
 * Bounding volume extensions
 */
fun BoundingBox.containsBox(other: BoundingBox): Boolean {
    return other.min.x >= min.x && other.max.x <= max.x &&
            other.min.y >= min.y && other.max.y <= max.y &&
            other.min.z >= min.z && other.max.z <= max.z
}

fun BoundingBox.getCorners(): List<Vector3> {
    return listOf(
        Vector3(min.x, min.y, min.z),
        Vector3(max.x, min.y, min.z),
        Vector3(min.x, max.y, min.z),
        Vector3(max.x, max.y, min.z),
        Vector3(min.x, min.y, max.z),
        Vector3(max.x, min.y, max.z),
        Vector3(min.x, max.y, max.z),
        Vector3(max.x, max.y, max.z)
    )
}

fun BoundingBox.transform(matrix: Matrix4): BoundingBox {
    val corners = getCorners().map { matrix.multiplyPoint3(it) }
    var newMin = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    var newMax = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    corners.forEach { corner ->
        newMin = newMin.min(corner)
        newMax = newMax.max(corner)
    }

    return BoundingBox(newMin, newMax)
}