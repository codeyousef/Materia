package io.kreekt.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.PI
import kotlin.math.tan

@PublishedApi
internal fun mat4Array(): FloatArray = FloatArray(16)

@JvmInline
value class Mat4 @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 16) { "Mat4 requires exactly 16 elements (found ${data.size})" }
    }

    operator fun get(index: Int): Float = data[index]

    operator fun set(index: Int, value: Float) {
        data[index] = value
    }

    fun copy(): Mat4 = mat4().copyFrom(this)

    fun copyFrom(other: Mat4): Mat4 = apply {
        other.data.copyInto(data, 0, 0, 16)
    }

    fun setIdentity(): Mat4 = apply {
        data.fill(0f)
        data[0] = 1f
        data[5] = 1f
        data[10] = 1f
        data[15] = 1f
    }

    fun multiply(left: Mat4, right: Mat4): Mat4 {
        val a = left.data
        val b = right.data
        val out = data
        for (row in 0 until 4) {
            val r0 = a[row]
            val r1 = a[row + 4]
            val r2 = a[row + 8]
            val r3 = a[row + 12]

            out[row] = r0 * b[0] + r1 * b[1] + r2 * b[2] + r3 * b[3]
            out[row + 4] = r0 * b[4] + r1 * b[5] + r2 * b[6] + r3 * b[7]
            out[row + 8] = r0 * b[8] + r1 * b[9] + r2 * b[10] + r3 * b[11]
            out[row + 12] = r0 * b[12] + r1 * b[13] + r2 * b[14] + r3 * b[15]
        }
        return this
    }

    fun setPerspective(fovDegrees: Float, aspect: Float, near: Float, far: Float): Mat4 {
        val halfAngleRadians = (fovDegrees / 180f) * PI.toFloat() * 0.5f
        val f = (1f / tan(halfAngleRadians.toDouble())).toFloat()

        data.fill(0f)
        data[0] = f / aspect
        data[5] = f
        data[10] = (far + near) / (near - far)
        data[11] = -1f
        data[14] = (2f * far * near) / (near - far)
        return this
    }

    fun setLookAt(eye: Vec3, target: Vec3, up: Vec3 = Vec3.Up): Mat4 {
        val forward = vec3(
            target.x - eye.x,
            target.y - eye.y,
            target.z - eye.z
        ).normalize()

        val right = up.cross(forward).normalize()
        val realUp = forward.cross(right)

        data[0] = right.x
        data[4] = right.y
        data[8] = right.z
        data[12] = -right.dot(eye)

        data[1] = realUp.x
        data[5] = realUp.y
        data[9] = realUp.z
        data[13] = -realUp.dot(eye)

        data[2] = -forward.x
        data[6] = -forward.y
        data[10] = -forward.z
        data[14] = forward.dot(eye)

        data[3] = 0f
        data[7] = 0f
        data[11] = 0f
        data[15] = 1f
        return this
    }

    fun translate(offset: Vec3): Mat4 = apply {
        data[12] += offset.x
        data[13] += offset.y
        data[14] += offset.z
    }

    fun toFloatArray(copy: Boolean = false): FloatArray = if (copy) data.copyOf() else data

    companion object {
        fun identity(): Mat4 = mat4().setIdentity()
    }
}

fun mat4(): Mat4 = Mat4(mat4Array())
