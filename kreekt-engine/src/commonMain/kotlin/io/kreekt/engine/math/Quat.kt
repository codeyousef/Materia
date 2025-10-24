package io.kreekt.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@PublishedApi
internal fun quatArray(): FloatArray = floatArrayOf(0f, 0f, 0f, 1f)

@JvmInline
value class Quat @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 4) { "Quat requires exactly 4 elements (found ${data.size})" }
    }

    var x: Float
        get() = data[0]
        set(value) {
            data[0] = value
        }

    var y: Float
        get() = data[1]
        set(value) {
            data[1] = value
        }

    var z: Float
        get() = data[2]
        set(value) {
            data[2] = value
        }

    var w: Float
        get() = data[3]
        set(value) {
            data[3] = value
        }

    fun set(x: Float, y: Float, z: Float, w: Float): Quat = apply {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    fun copy(): Quat = quat().set(this)

    fun set(other: Quat): Quat = set(other.x, other.y, other.z, other.w)

    fun setIdentity(): Quat = set(0f, 0f, 0f, 1f)

    fun normalize(): Quat {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length == 0f) return setIdentity()
        val inv = 1f / length
        x *= inv
        y *= inv
        z *= inv
        w *= inv
        return this
    }

    fun setFromAxisAngle(axis: Vec3, angleRadians: Float): Quat {
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

    fun toRotationMatrix(out: Mat4 = mat4()): Mat4 {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z

        val data = out.data
        out.setIdentity()
        data[0] = 1f - 2f * (yy + zz)
        data[4] = 2f * (xy + wz)
        data[8] = 2f * (xz - wy)

        data[1] = 2f * (xy - wz)
        data[5] = 1f - 2f * (xx + zz)
        data[9] = 2f * (yz + wx)

        data[2] = 2f * (xz + wy)
        data[6] = 2f * (yz - wx)
        data[10] = 1f - 2f * (xx + yy)

        return out
    }

    companion object {
        fun identity(): Quat = quat().setIdentity()
    }
}

fun quat(): Quat = Quat(quatArray())

typealias Quaternion = Quat
