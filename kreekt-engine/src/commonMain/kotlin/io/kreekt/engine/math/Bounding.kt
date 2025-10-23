package io.kreekt.engine.math

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Mutable axis-aligned bounding box represented by its minimum and maximum corners.
 */
class Aabb(
    val min: Vec3 = vec3(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    val max: Vec3 = vec3(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
) {
    fun reset(): Aabb = apply {
        min.set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        max.set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
    }

    fun isEmpty(): Boolean = min.x > max.x || min.y > max.y || min.z > max.z

    fun include(point: Vec3): Aabb = apply {
        min.x = min(min.x, point.x)
        min.y = min(min.y, point.y)
        min.z = min(min.z, point.z)

        max.x = max(max.x, point.x)
        max.y = max(max.y, point.y)
        max.z = max(max.z, point.z)
    }

    fun include(box: Aabb): Aabb = apply {
        if (box.isEmpty()) return@apply
        include(box.min)
        include(box.max)
    }

    fun set(minPoint: Vec3, maxPoint: Vec3): Aabb = apply {
        min.set(minPoint)
        max.set(maxPoint)
    }

    fun copy(out: Aabb = Aabb()): Aabb = out.set(min, max)

    fun center(out: Vec3 = vec3()): Vec3 =
        out.set(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        )

    fun size(out: Vec3 = vec3()): Vec3 =
        out.set(
            max.x - min.x,
            max.y - min.y,
            max.z - min.z
        )

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
 * Half space plane represented by its normal and distance to origin.
 * The plane equation is n·p + d >= 0 for inside points.
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

    fun set(nx: Float, ny: Float, nz: Float, d: Float): Plane = apply {
        coefficients.set(nx, ny, nz, d)
        normal.set(nx, ny, nz)
        normalize()
    }

    fun setFrom(plane: Plane): Plane = set(plane.normal.x, plane.normal.y, plane.normal.z, plane.distance)

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
 * View frustum represented by six planes.
 */
class Frustum internal constructor(
    private val planes: Array<Plane> = Array(6) { Plane() }
) {
    fun plane(index: Int): Plane = planes[index]

    fun setPlane(index: Int, nx: Float, ny: Float, nz: Float, distance: Float): Frustum = apply {
        planes[index].set(nx, ny, nz, distance)
    }

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
        fun fromMatrix(matrix: Mat4, out: Frustum = Frustum()): Frustum {
            val m = matrix.data
            val m00 = m[0]; val m01 = m[4]; val m02 = m[8];  val m03 = m[12]
            val m10 = m[1]; val m11 = m[5]; val m12 = m[9];  val m13 = m[13]
            val m20 = m[2]; val m21 = m[6]; val m22 = m[10]; val m23 = m[14]
            val m30 = m[3]; val m31 = m[7]; val m32 = m[11]; val m33 = m[15]

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
