package io.materia.engine.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mutable axis-aligned bounding box (AABB) defined by minimum and maximum corners.
 *
 * An AABB is the tightest box with faces parallel to the coordinate axes that
 * fully contains a set of points or geometry. Use [include] to expand the box
 * to encompass additional points, and [transform] to compute a new AABB after
 * applying a matrix transformation.
 *
 * By default, the box is "empty" (inverted bounds) until points are included.
 *
 * @property min The corner with the smallest X, Y, Z values.
 * @property max The corner with the largest X, Y, Z values.
 */
class Aabb(
    val min: Vec3 = vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    val max: Vec3 = vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
) {
    /**
     * Resets this box to an empty (inverted) state.
     *
     * @return This box for chaining.
     */
    fun reset(): Aabb = apply {
        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
    }

    /**
     * Checks whether this box is empty (has no valid extent).
     *
     * @return True if min exceeds max on any axis.
     */
    fun isEmpty(): Boolean = min.x > max.x || min.y > max.y || min.z > max.z

    /**
     * Expands this box to include the given point.
     *
     * @param point The point to include.
     * @return This box for chaining.
     */
    fun include(point: Vec3): Aabb = apply {
        min.x = min(min.x, point.x)
        min.y = min(min.y, point.y)
        min.z = min(min.z, point.z)

        max.x = max(max.x, point.x)
        max.y = max(max.y, point.y)
        max.z = max(max.z, point.z)
    }

    /**
     * Expands this box to include another bounding box.
     *
     * @param box The box to include.
     * @return This box for chaining.
     */
    fun include(box: Aabb): Aabb = apply {
        if (box.isEmpty()) return@apply
        include(box.min)
        include(box.max)
    }

    /**
     * Sets this box from explicit min/max corners.
     *
     * @param minPoint The minimum corner.
     * @param maxPoint The maximum corner.
     * @return This box for chaining.
     */
    fun set(minPoint: Vec3, maxPoint: Vec3): Aabb = apply {
        min.set(minPoint)
        max.set(maxPoint)
    }

    /**
     * Creates a copy of this bounding box.
     *
     * @param out Optional pre-allocated box to write into.
     * @return A copy of this box.
     */
    fun copy(out: Aabb = Aabb()): Aabb = out.set(min, max)

    /**
     * Computes the center point of this box.
     *
     * @param out Optional pre-allocated vector to write into.
     * @return The center of the box.
     */
    fun center(out: Vec3 = vec3()): Vec3 =
        out.set(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        )

    /**
     * Computes the size (extent) of this box along each axis.
     *
     * @param out Optional pre-allocated vector to write into.
     * @return The width, height, and depth of the box.
     */
    fun size(out: Vec3 = vec3()): Vec3 =
        out.set(
            max.x - min.x,
            max.y - min.y,
            max.z - min.z
        )

    /**
     * Computes a new AABB that bounds this box after transformation.
     *
     * The result is the smallest axis-aligned box containing all transformed corners.
     *
     * @param matrix The transformation matrix to apply.
     * @param out Optional pre-allocated box to write into.
     * @return The transformed bounding box.
     */
    fun transform(matrix: Mat4, out: Aabb = Aabb()): Aabb {
        if (isEmpty()) {
            return out.reset()
        }
        val center = center(tmpCenter)
        val extent = size(tmpExtent).scale(0.5f)
        val m = matrix.data

        val cx = m[0] * center.x + m[4] * center.y + m[8] * center.z + m[12]
        val cy = m[1] * center.x + m[5] * center.y + m[9] * center.z + m[13]
        val cz = m[2] * center.x + m[6] * center.y + m[10] * center.z + m[14]

        val ex = abs(m[0]) * extent.x + abs(m[4]) * extent.y + abs(m[8]) * extent.z
        val ey = abs(m[1]) * extent.x + abs(m[5]) * extent.y + abs(m[9]) * extent.z
        val ez = abs(m[2]) * extent.x + abs(m[6]) * extent.y + abs(m[10]) * extent.z

        out.min.set(cx - ex, cy - ey, cz - ez)
        out.max.set(cx + ex, cy + ey, cz + ez)
        return out
    }

    companion object {
        private val tmpCenter = vec3()
        private val tmpExtent = vec3()

        fun fromCenterSize(center: Vec3, size: Vec3, out: Aabb = Aabb()): Aabb {
            val hx = size.x * 0.5f
            val hy = size.y * 0.5f
            val hz = size.z * 0.5f
            out.min.set(center.x - hx, center.y - hy, center.z - hz)
            out.max.set(center.x + hx, center.y + hy, center.z + hz)
            return out
        }
    }
}

/**
 * Half-space plane defined by a unit normal and signed distance to origin.
 *
 * The plane equation is `n · p + d = 0` for points on the plane.
 * Points satisfy `n · p + d > 0` when on the positive (front) side.
 *
 * Used primarily for frustum culling where each frustum face is a plane.
 */
class Plane {
    private val coefficients = vec4()

    var normal: Vec3 = vec3()
        private set

    var distance: Float
        get() = coefficients.w
        private set(value) {
            coefficients.w = value
        }

    init {
        normal = vec3(
            coefficients.x,
            coefficients.y,
            coefficients.z
        )
    }

    /**
     * Sets the plane coefficients and normalizes the result.
     *
     * @param nx Normal X component.
     * @param ny Normal Y component.
     * @param nz Normal Z component.
     * @param d Distance coefficient.
     * @return This plane for chaining.
     */
    fun set(nx: Float, ny: Float, nz: Float, d: Float): Plane = apply {
        coefficients.set(nx, ny, nz, d)
        normal.set(nx, ny, nz)
        normalize()
    }

    /**
     * Copies values from another plane.
     *
     * @param plane The source plane.
     * @return This plane for chaining.
     */
    fun setFrom(plane: Plane): Plane =
        set(plane.normal.x, plane.normal.y, plane.normal.z, plane.distance)

    /**
     * Computes the signed distance from a point to this plane.
     *
     * @param point The point to test.
     * @return Positive if on the front side, negative if behind, zero if on the plane.
     */
    fun distanceTo(point: Vec3): Float =
        normal.x * point.x + normal.y * point.y + normal.z * point.z + distance

    private fun normalize() {
        val length = sqrt(
            coefficients.x * coefficients.x +
                    coefficients.y * coefficients.y +
                    coefficients.z * coefficients.z
        )
        if (length == 0f) return
        val inv = 1f / length
        coefficients.x *= inv
        coefficients.y *= inv
        coefficients.z *= inv
        coefficients.w *= inv
        normal.set(coefficients.x, coefficients.y, coefficients.z)
    }
}

/**
 * View frustum represented by six clipping planes for culling tests.
 *
 * Planes are ordered as: left, right, bottom, top, near, far.
 * Use [fromMatrix] to extract frustum planes from a view-projection matrix,
 * then [intersects] to test AABB visibility.
 */
class Frustum internal constructor(
    private val planes: Array<Plane> = Array(6) { Plane() }
) {
    /**
     * Returns the plane at the specified index.
     *
     * @param index Plane index (0=left, 1=right, 2=bottom, 3=top, 4=near, 5=far).
     * @return The requested plane.
     */
    fun plane(index: Int): Plane = planes[index]

    /**
     * Sets a frustum plane by index.
     *
     * @param index Plane index.
     * @param nx Normal X component.
     * @param ny Normal Y component.
     * @param nz Normal Z component.
     * @param distance Distance coefficient.
     * @return This frustum for chaining.
     */
    fun setPlane(index: Int, nx: Float, ny: Float, nz: Float, distance: Float): Frustum = apply {
        planes[index].set(nx, ny, nz, distance)
    }

    /**
     * Tests whether an AABB intersects or is inside this frustum.
     *
     * Uses the "p-vertex" optimization for efficient culling.
     *
     * @param aabb The bounding box to test.
     * @return True if the box is at least partially visible.
     */
    fun intersects(aabb: Aabb): Boolean {
        if (aabb.isEmpty()) return false
        val min = aabb.min
        val max = aabb.max
        planes.forEach { plane ->
            val nx = plane.normal.x
            val ny = plane.normal.y
            val nz = plane.normal.z

            val px = if (nx >= 0f) max.x else min.x
            val py = if (ny >= 0f) max.y else min.y
            val pz = if (nz >= 0f) max.z else min.z

            if (nx * px + ny * py + nz * pz + plane.distance < 0f) {
                return false
            }
        }
        return true
    }

    companion object {
        /**
         * Extracts frustum planes from a view-projection matrix.
         *
         * Uses the Gribb/Hartmann method to derive planes from the combined
         * view-projection matrix rows.
         *
         * @param matrix The view-projection matrix.
         * @param out Optional pre-allocated frustum to write into.
         * @return A frustum with planes extracted from the matrix.
         */
        fun fromMatrix(matrix: Mat4, out: Frustum = Frustum()): Frustum {
            val m = matrix.data
            val m00 = m[0];
            val m01 = m[4];
            val m02 = m[8];
            val m03 = m[12]
            val m10 = m[1];
            val m11 = m[5];
            val m12 = m[9];
            val m13 = m[13]
            val m20 = m[2];
            val m21 = m[6];
            val m22 = m[10];
            val m23 = m[14]
            val m30 = m[3];
            val m31 = m[7];
            val m32 = m[11];
            val m33 = m[15]

            out.setPlane(0, m30 + m00, m31 + m01, m32 + m02, m33 + m03) // Left
            out.setPlane(1, m30 - m00, m31 - m01, m32 - m02, m33 - m03) // Right
            out.setPlane(2, m30 + m10, m31 + m11, m32 + m12, m33 + m13) // Bottom
            out.setPlane(3, m30 - m10, m31 - m11, m32 - m12, m33 - m13) // Top
            out.setPlane(4, m30 + m20, m31 + m21, m32 + m22, m33 + m23) // Near
            out.setPlane(5, m30 - m20, m31 - m21, m32 - m22, m33 - m23) // Far
            return out
        }
    }
}
