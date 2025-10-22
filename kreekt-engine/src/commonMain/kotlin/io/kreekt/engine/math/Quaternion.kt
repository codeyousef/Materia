package io.kreekt.engine.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Minimal quaternion implementation sufficient for the MVP math tests.
 *
 * Provides axis-angle construction, normalization, and conversion to a
 * column-major 4x4 rotation matrix compatible with the engine's matrix utilities.
 */
data class Quaternion(
    var x: Float,
    var y: Float,
    var z: Float,
    var w: Float
) {
    fun set(nx: Float, ny: Float, nz: Float, nw: Float): Quaternion {
        x = nx
        y = ny
        z = nz
        w = nw
        return this
    }

    fun copy(): Quaternion = Quaternion(x, y, z, w)

    fun normalize(): Quaternion {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length == 0f) {
            return identity(this)
        }
        val inv = 1f / length
        return set(x * inv, y * inv, z * inv, w * inv)
    }

    fun setFromAxisAngle(axis: Vector3f, angleRadians: Float): Quaternion {
        val normalizedAxis = axis.copy().normalize()
        val halfAngle = angleRadians * 0.5f
        val sinHalf = sin(halfAngle)
        val cosHalf = cos(halfAngle)
        return set(
            normalizedAxis.x * sinHalf,
            normalizedAxis.y * sinHalf,
            normalizedAxis.z * sinHalf,
            cosHalf
        )
    }

    /**
     * Convert the quaternion into a column-major 4x4 rotation matrix.
     */
    fun toRotationMatrix(): FloatArray {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z

        return floatArrayOf(
            1f - 2f * (yy + zz),
            2f * (xy + wz),
            2f * (xz - wy),
            0f,

            2f * (xy - wz),
            1f - 2f * (xx + zz),
            2f * (yz + wx),
            0f,

            2f * (xz + wy),
            2f * (yz - wx),
            1f - 2f * (xx + yy),
            0f,

            0f,
            0f,
            0f,
            1f
        )
    }

    companion object {
        fun identity(): Quaternion = Quaternion(0f, 0f, 0f, 1f)

        fun identity(out: Quaternion): Quaternion = out.set(0f, 0f, 0f, 1f)
    }
}
