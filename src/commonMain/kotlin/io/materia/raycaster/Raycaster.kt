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
 * GPU-accelerated raycaster for mouse picking and collision detection.
 *
 * Projects a ray from screen coordinates through the scene to detect
 * intersections with 3D objects. Supports both perspective and orthographic
 * cameras.
 *
 * Typical usage:
 * ```kotlin
 * val raycaster = Raycaster()
 * raycaster.setFromCamera(mouseNDC, camera)
 * val hits = raycaster.intersectObjects(scene.children)
 * if (hits.isNotEmpty()) {
 *     val closest = hits.first()
 *     println("Hit ${closest.`object`.name} at ${closest.point}")
 * }
 * ```
 *
 * @param origin Ray origin point (default: world origin).
 * @param direction Ray direction (default: -Z axis).
 * @param near Minimum intersection distance (default: 0).
 * @param far Maximum intersection distance (default: infinity).
 */
class Raycaster(
    origin: Vector3 = Vector3(),
    direction: Vector3 = Vector3(0f, 0f, -1f),
    val near: Float = 0f,
    val far: Float = Float.POSITIVE_INFINITY
) {
    val ray = Ray(origin, direction)

    /**
     * Type-specific raycasting parameters.
     *
     * Customize thresholds for different geometry types (lines, points)
     * to control intersection sensitivity.
     */
    val params = Params()

    /**
     * Configures the ray from camera and screen coordinates.
     *
     * Transforms normalized device coordinates (NDC) into a world-space ray
     * suitable for intersection testing. Handles both perspective and
     * orthographic projection correctly.
     *
     * @param coords Normalized device coordinates (-1 to +1 on both axes).
     * @param camera The camera defining the projection.
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
     * Sets the ray origin and direction directly.
     *
     * @param origin The ray origin point.
     * @param direction The ray direction (should be normalized).
     */
    fun set(origin: Vector3, direction: Vector3) {
        ray.set(origin, direction)
    }

    /**
     * Tests for ray intersections with an object and optionally its descendants.
     *
     * Returns intersections sorted by distance (closest first).
     *
     * @param object3D The root object to test.
     * @param recursive If true, also tests all descendants.
     * @param optionalTarget Reusable list to avoid allocation.
     * @return Sorted list of intersections.
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
     * Tests for ray intersections with multiple objects.
     *
     * Returns all intersections sorted by distance (closest first).
     *
     * @param objects The objects to test.
     * @param recursive If true, also tests all descendants of each object.
     * @param optionalTarget Reusable list to avoid allocation.
     * @return Sorted list of intersections.
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
     * Type-specific raycasting configuration.
     *
     * Different geometry types may need different intersection thresholds.
     * For example, lines and points have no surface area, so a threshold
     * defines how close the ray must pass to count as a hit.
     */
    class Params {
        /** Parameters for mesh intersection. */
        val mesh = MeshParams()
        /** Parameters for line intersection. */
        val line = LineParams()
        /** Parameters for LOD object intersection. */
        val lod = LODParams()
        /** Parameters for point cloud intersection. */
        val points = PointsParams()

        /** Mesh-specific parameters. */
        class MeshParams

        /** Line-specific parameters. */
        class LineParams {
            /** Distance threshold for line hits (world units). */
            var threshold = 1f
        }

        /** LOD-specific parameters. */
        class LODParams

        /** Point cloud-specific parameters. */
        class PointsParams {
            /** Distance threshold for point hits (world units). */
            var threshold = 1f
        }
    }
}

/**
 * Result of a ray-object intersection test.
 *
 * Contains information about where the ray hit the object and
 * which part of the geometry was intersected.
 *
 * @property distance Distance from ray origin to intersection point.
 * @property point World-space intersection point (null for some geometry types).
 * @property object The intersected Object3D.
 * @property face Face data for mesh intersections.
 * @property faceIndex Index of the intersected face in the geometry.
 * @property index Vertex index for point/line intersections.
 * @property instanceId Instance ID for instanced geometry.
 * @property uv Texture coordinates at the intersection point.
 * @property uv2 Secondary texture coordinates.
 * @property normal Surface normal at the intersection point.
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
 * Triangle face data from a mesh intersection.
 *
 * @property a Index of the first vertex.
 * @property b Index of the second vertex.
 * @property c Index of the third vertex.
 * @property normal Face normal vector.
 * @property materialIndex Index of the material used by this face.
 */
data class Face(
    val a: Int,
    val b: Int,
    val c: Int,
    val normal: Vector3? = null,
    val materialIndex: Int = 0
)

/**
 * Performs raycasting against this object.
 *
 * Override in subclasses (Mesh, Line, Points) to implement
 * geometry-specific intersection testing.
 *
 * @param raycaster The raycaster to test against.
 * @param intersections Output list to append any hits.
 */
fun Object3D.raycast(raycaster: Raycaster, intersections: MutableList<Intersection>) {
    // Default no-op - overridden in Mesh, Line, Points with geometry intersection logic
}