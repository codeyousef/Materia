package io.kreekt.engine.math

import kotlin.math.sqrt

data class Vector3f(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    fun set(nx: Float, ny: Float, nz: Float): Vector3f {
        x = nx
        y = ny
        z = nz
        return this
    }

    fun copyFrom(other: Vector3f): Vector3f = set(other.x, other.y, other.z)

    fun add(other: Vector3f): Vector3f = set(x + other.x, y + other.y, z + other.z)

    fun subtract(other: Vector3f): Vector3f = set(x - other.x, y - other.y, z - other.z)

    fun scale(scale: Float): Vector3f = set(x * scale, y * scale, z * scale)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vector3f {
        val len = length()
        return if (len > 0f) {
            val inv = 1f / len
            set(x * inv, y * inv, z * inv)
        } else {
            this
        }
    }

    fun toFloatArray(): FloatArray = floatArrayOf(x, y, z)

    companion object {
        val Zero = Vector3f(0f, 0f, 0f)
        val One = Vector3f(1f, 1f, 1f)
        val Up = Vector3f(0f, 1f, 0f)
        val Forward = Vector3f(0f, 0f, -1f)
    }
}
