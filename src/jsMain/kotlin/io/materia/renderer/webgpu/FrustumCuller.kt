package io.materia.renderer.webgpu

import io.materia.camera.Camera
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.core.scene.Mesh
import io.materia.geometry.BufferGeometry

/**
 * Frustum culling optimization for skipping off-screen objects.
 * T038: +15 FPS improvement by avoiding unnecessary draw calls.
 *
 * Implements view frustum culling using camera's projection-view matrix
 * to determine which objects are visible and should be rendered.
 */
class FrustumCuller {
    // Frustum planes (left, right, bottom, top, near, far)
    private val planes = Array(6) { Plane() }

    // Statistics
    private var totalObjects = 0
    private var culledObjects = 0
    private var visibleObjects = 0

    private val tempCenter = Vector3()
    private val tempLocalCenter = Vector3()
    private val tempScale = Vector3()

    /**
     * Extracts frustum planes from camera's projection-view matrix.
     * @param camera Camera with projection and view matrices
     */
    fun extractPlanesFromCamera(camera: Camera) {
        val matrix = Matrix4().multiplyMatrices(
            camera.projectionMatrix,
            camera.matrixWorldInverse
        )

        val me = matrix.elements

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

    /**
     * Checks if an object is visible within the frustum.
     * @param obj Object to test
     * @return true if object is visible, false if culled
     */
    fun isObjectVisible(obj: Object3D): Boolean {
        totalObjects++

        val center = computeWorldBoundingCenter(obj, tempCenter)
        val radius = computeBoundingRadius(obj)
        if (radius <= 0f) {
            // Treat objects with no size as visible
            visibleObjects++
            return true
        }

        // Test against all 6 frustum planes
        for (plane in planes) {
            val distance = plane.distanceToPoint(center)
            if (distance < -radius) {
                // Object is completely outside this plane
                culledObjects++
                return false
            }
        }

        // Object is at least partially visible
        visibleObjects++
        return true
    }

    private fun computeWorldBoundingCenter(obj: Object3D, target: Vector3): Vector3 {
        return when (obj) {
            is Mesh -> {
                val geometry = obj.geometry
                val sphere = geometry.getOrComputeBoundingSphere()
                if (sphere.radius > 0f) {
                    tempLocalCenter.copy(sphere.center)
                    obj.localToWorld(tempLocalCenter)
                    target.copy(tempLocalCenter)
                } else {
                    obj.getWorldPosition(target)
                }
            }

            else -> obj.getWorldPosition(target)
        }
    }

    private fun computeBoundingRadius(obj: Object3D): Float {
        val worldScale = obj.getWorldScale(tempScale)
        val maxScale = maxOf(worldScale.x, maxOf(worldScale.y, worldScale.z))
        if (maxScale <= 0f) {
            return 0f
        }

        return if (obj is Mesh) {
            val sphere = obj.geometry.getOrComputeBoundingSphere()
            when {
                sphere.radius > 0f -> sphere.radius * maxScale
                else -> maxScale
            }
        } else {
            maxScale
        }
    }

    private fun BufferGeometry.getOrComputeBoundingSphere(): io.materia.core.math.Sphere {
        return boundingSphere ?: computeBoundingSphere()
    }

    /**
     * Culls a list of objects, returning only visible ones.
     * @param objects List of objects to cull
     * @param camera Camera defining the frustum
     * @return List of visible objects
     */
    fun cullObjects(objects: List<Object3D>, camera: Camera): List<Object3D> {
        resetStats()
        extractPlanesFromCamera(camera)

        return objects.filter { isObjectVisible(it) }
    }

    /**
     * Culls objects during scene traversal.
     * @param root Root object to traverse
     * @param camera Camera defining the frustum
     * @param onVisible Callback for each visible object
     */
    fun cullScene(root: Object3D, camera: Camera, onVisible: (Object3D) -> Unit) {
        resetStats()
        extractPlanesFromCamera(camera)

        root.traverse { obj ->
            if (obj is Mesh && isObjectVisible(obj)) {
                onVisible(obj)
            }
        }
    }

    /**
     * Gets culling statistics.
     */
    fun getStats(): CullingStats {
        return CullingStats(
            total = totalObjects,
            culled = culledObjects,
            visible = visibleObjects,
            cullRate = if (totalObjects > 0) culledObjects.toFloat() / totalObjects else 0f
        )
    }

    /**
     * Resets statistics counters.
     */
    private fun resetStats() {
        totalObjects = 0
        culledObjects = 0
        visibleObjects = 0
    }

    /**
     * Represents a plane in 3D space (ax + by + cz + d = 0).
     */
    private class Plane {
        var a = 0f
        var b = 0f
        var c = 0f
        var d = 0f

        fun set(a: Float, b: Float, c: Float, d: Float): Plane {
            this.a = a
            this.b = b
            this.c = c
            this.d = d
            return this
        }

        fun normalize(): Plane {
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
}

/**
 * Culling statistics.
 */
data class CullingStats(
    val total: Int,
    val culled: Int,
    val visible: Int,
    val cullRate: Float
)
