package io.materia.core.math

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * 4D vector implementation
 * T028 - Vector4 class
 */
@Serializable
data class Vector4(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var w: Float = 1f
) {
    constructor(scalar: Float) : this(scalar, scalar, scalar, scalar)
    constructor(other: Vector4) : this(other.x, other.y, other.z, other.w)
    constructor(v3: Vector3, w: Float = 1f) : this(v3.x, v3.y, v3.z, w)

    // Basic operations
    fun set(x: Float, y: Float, z: Float, w: Float): Vector4 {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
        return this
    }

    fun set(scalar: Float): Vector4 = set(scalar, scalar, scalar, scalar)
    fun set(other: Vector4): Vector4 = set(other.x, other.y, other.z, other.w)

    fun copy(other: Vector4): Vector4 = set(other)
    fun clone(): Vector4 = Vector4(x, y, z, w)

    // Arithmetic operations
    fun add(other: Vector4): Vector4 {
        x = x + other.x
        y = y + other.y
        z = z + other.z
        w = w + other.w
        return this
    }

    fun add(scalar: Float): Vector4 {
        x = x + scalar
        y = y + scalar
        z = z + scalar
        w = w + scalar
        return this
    }

    fun subtract(other: Vector4): Vector4 {
        x = x - other.x
        y = y - other.y
        z = z - other.z
        w = w - other.w
        return this
    }

    fun subtract(scalar: Float): Vector4 {
        x = x - scalar
        y = y - scalar
        z = z - scalar
        w = w - scalar
        return this
    }

    fun multiply(other: Vector4): Vector4 {
        x = x * other.x
        y = y * other.y
        z = z * other.z
        w = w * other.w
        return this
    }

    fun multiply(scalar: Float): Vector4 {
        x = x * scalar
        y = y * scalar
        z = z * scalar
        w = w * scalar
        return this
    }

    fun divide(other: Vector4): Vector4 {
        x /= other.x
        y /= other.y
        z /= other.z
        w /= other.w
        return this
    }

    fun divide(scalar: Float): Vector4 {
        x /= scalar
        y /= scalar
        z /= scalar
        w /= scalar
        return this
    }

    // Operator overloading for arithmetic
    operator fun plus(other: Vector4): Vector4 =
        Vector4(x + other.x, y + other.y, z + other.z, w + other.w)

    operator fun plus(scalar: Float): Vector4 =
        Vector4(x + scalar, y + scalar, z + scalar, w + scalar)

    operator fun minus(other: Vector4): Vector4 =
        Vector4(x - other.x, y - other.y, z - other.z, w - other.w)

    operator fun minus(scalar: Float): Vector4 =
        Vector4(x - scalar, y - scalar, z - scalar, w - scalar)

    operator fun times(other: Vector4): Vector4 =
        Vector4(x * other.x, y * other.y, z * other.z, w * other.w)

    operator fun times(scalar: Float): Vector4 =
        Vector4((x * scalar), (y * scalar), (z * scalar), (w * scalar))

    operator fun div(other: Vector4): Vector4 =
        Vector4(x / other.x, y / other.y, z / other.z, w / other.w)

    operator fun div(scalar: Float): Vector4 =
        Vector4(x / scalar, y / scalar, z / scalar, w / scalar)

    operator fun unaryMinus(): Vector4 = Vector4(-x, -y, -z, -w)

    // Extension property for normalized vector
    val normalized: Vector4
        get() = clone().normalize()

    // Mutable operators
    operator fun plusAssign(other: Vector4) {
        x = x + other.x
        y = y + other.y
        z = z + other.z
        w = w + other.w
    }

    operator fun minusAssign(other: Vector4) {
        x = x - other.x
        y = y - other.y
        z = z - other.z
        w = w - other.w
    }

    operator fun timesAssign(scalar: Float) {
        x = x * scalar
        y = y * scalar
        z = z * scalar
        w = w * scalar
    }

    // Vector operations
    fun dot(other: Vector4): Float = x * other.x + y * other.y + z * other.z + w * other.w

    fun length(): Float = sqrt(x * x + y * y + z * z + (w * w))
    fun lengthSquared(): Float = x * x + y * y + z * z + w * w

    fun normalize(): Vector4 {
        val len = length()
        return if (len > 0) divide(len) else this
    }

    fun distance(other: Vector4): Float = sqrt(distanceSquared(other))
    fun distanceSquared(other: Vector4): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        val dw = w - other.w
        return dx * dx + dy * dy + dz * dz + (dw * dw)
    }

    fun lerp(other: Vector4, t: Float): Vector4 {
        x += (other.x - x) * t
        y += (other.y - y) * t
        z += (other.z - z) * t
        w += (other.w - w) * t
        return this
    }

    fun negate(): Vector4 {
        x = -x
        y = -y
        z = -z
        w = -w
        return this
    }

    fun floor(): Vector4 {
        x = floor(x)
        y = floor(y)
        z = floor(z)
        w = floor(w)
        return this
    }

    fun ceil(): Vector4 {
        x = ceil(x)
        y = ceil(y)
        z = ceil(z)
        w = ceil(w)
        return this
    }

    fun round(): Vector4 {
        x = round(x)
        y = round(y)
        z = round(z)
        w = round(w)
        return this
    }

    // Matrix transformations
    fun applyMatrix4(matrix: Matrix4): Vector4 {
        val e = matrix.elements
        val oldX = x
        val oldY = y
        val oldZ = z
        val oldW = w
        x = e[0] * oldX + e[4] * oldY + e[8] * oldZ + e[12] * oldW
        y = e[1] * oldX + e[5] * oldY + e[9] * oldZ + e[13] * oldW
        z = e[2] * oldX + e[6] * oldY + e[10] * oldZ + e[14] * oldW
        w = e[3] * oldX + e[7] * oldY + e[11] * oldZ + e[15] * oldW
        return this
    }

    // Utility
    fun equals(other: Vector4, epsilon: Float = 0.000001f): Boolean {
        return abs(x - other.x) < epsilon &&
                abs(y - other.y) < epsilon &&
                abs(z - other.z) < epsilon &&
                abs(w - other.w) < epsilon
    }

    fun isZero(epsilon: Float = 0.000001f): Boolean =
        abs(x) < epsilon && abs(y) < epsilon && abs(z) < epsilon && abs(w) < epsilon

    override fun toString(): String = "Vector4($x, $y, $z, $w)"

    companion object {
        val ZERO = Vector4(0f, 0f, 0f, 0f)
        val ONE = Vector4(1f, 1f, 1f, 1f)
        val UNIT_X = Vector4(1f, 0f, 0f, 0f)
        val UNIT_Y = Vector4(0f, 1f, 0f, 0f)
        val UNIT_Z = Vector4(0f, 0f, 1f, 0f)
        val UNIT_W = Vector4(0f, 0f, 0f, 1f)
    }
}