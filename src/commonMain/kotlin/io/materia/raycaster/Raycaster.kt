package io.materia.raycaster

import io.materia.core.math.Vector3
import io.materia.core.math.Vector2
import io.materia.core.math.Ray
import io.materia.core.math.Matrix4
import io.materia.core.scene.Object3D
import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.camera.OrthographicCamera

/**
 * Raycaster implementation - T062
 * Performs raycasting for mouse picking and collision detection
 */
class Raycaster(
    origin: Vector3 = Vector3(),
    direction: Vector3 = Vector3(0f, 0f, -1f),
    val near: Float = 0f,
    val far: Float = Float.POSITIVE_INFINITY
) {
    val ray = Ray(origin, direction)

    /**
     * Parameters for different object types
     */
    val params = Params()

    /**
     * Set ray from camera and normalized device coordinates
     */
    fun setFromCamera(coords: Vector2, camera: Camera) {
        when (camera) {
            is PerspectiveCamera -> {
                ray.origin.setFromMatrixColumn(camera.matrixWorld, 3)
                ray.direction.set(coords.x, coords.y, 0.5f)
                    .unproject(camera)
                    .sub(ray.origin)
                    .normalize()
            }

            is OrthographicCamera -> {
                val denominator = camera.near - camera.far
                val z =
                    if (kotlin.math.abs(denominator) < 0.00001f) 0f else (camera.near + camera.far) / denominator
                ray.origin.set(
                    coords.x,
                    coords.y,
                    z
                ).unproject(camera)

                ray.direction.set(0f, 0f, -1f)
                    .transformDirection(camera.matrixWorld) // Apply rotation only
            }

            else -> {
                // Default behavior
                ray.origin.copy(camera.position)
                ray.direction.set(coords.x, coords.y, -1f)
                    .transformDirection(camera.matrixWorld) // Apply rotation only
                    .normalize()
            }
        }
    }

    /**
     * Set ray directly
     */
    fun set(origin: Vector3, direction: Vector3) {
        ray.set(origin, direction)
    }

    /**
     * Check intersections with an object and its descendants
     */
    fun intersectObject(
        object3D: Object3D,
        recursive: Boolean = true,
        optionalTarget: MutableList<Intersection> = mutableListOf()
    ): List<Intersection> {
        val intersections = optionalTarget
        intersections.clear()

        intersectObjectInternal(object3D, this, intersections, recursive)

        intersections.sortBy { it.distance }

        return intersections
    }

    /**
     * Check intersections with multiple objects
     */
    fun intersectObjects(
        objects: List<Object3D>,
        recursive: Boolean = true,
        optionalTarget: MutableList<Intersection> = mutableListOf()
    ): List<Intersection> {
        val intersections = optionalTarget
        intersections.clear()

        for (obj in objects) {
            intersectObjectInternal(obj, this, intersections, recursive)
        }

        intersections.sortBy { it.distance }

        return intersections
    }

    private fun intersectObjectInternal(
        object3D: Object3D,
        raycaster: Raycaster,
        intersections: MutableList<Intersection>,
        recursive: Boolean
    ) {
        // Check if object is visible and within layer mask
        if (!object3D.visible) return

        // Perform raycast on the object
        object3D.raycast(raycaster, intersections)

        // Check children if recursive
        if (recursive) {
            for (child in object3D.children) {
                intersectObjectInternal(child, raycaster, intersections, true)
            }
        }
    }

    /**
     * Parameters for raycasting different object types
     */
    class Params {
        val mesh = MeshParams()
        val line = LineParams()
        val lod = LODParams()
        val points = PointsParams()

        class MeshParams

        class LineParams {
            var threshold = 1f
        }

        class LODParams

        class PointsParams {
            var threshold = 1f
        }
    }
}

/**
 * Intersection result from raycasting
 */
data class Intersection(
    val distance: Float,
    val point: Vector3? = null,
    val `object`: Object3D,
    val face: Face? = null,
    val faceIndex: Int? = null,
    val index: Int? = null,
    val instanceId: Int? = null,
    val uv: Vector2? = null,
    val uv2: Vector2? = null,
    val normal: Vector3? = null
)

/**
 * Face structure for mesh intersections
 */
data class Face(
    val a: Int,
    val b: Int,
    val c: Int,
    val normal: Vector3? = null,
    val materialIndex: Int = 0
)

/**
 * Extension function for Object3D to support raycasting
 */
fun Object3D.raycast(raycaster: Raycaster, intersections: MutableList<Intersection>) {
    // Default implementation - override in specific object types
    // This will be implemented in Mesh, Line, Points, etc.
}