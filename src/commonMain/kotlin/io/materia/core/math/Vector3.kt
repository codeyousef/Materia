package io.materia.core.math

import kotlinx.serialization.Serializable
import kotlin.math.*

/**
 * # Vector3 - Three-Component Vector
 *
 * A three-dimensional vector with x, y, and z components. Vector3 is one of the most
 * fundamental classes in Materia, used for positions, directions, scales, colors, and more.
 *
 * ## Overview
 *
 * Vector3 provides:
 * - **Arithmetic Operations**: Add, subtract, multiply, divide (component-wise and scalar)
 * - **Vector Math**: Dot product, cross product, length, normalization
 * - **Transformations**: Apply matrices and quaternions
 * - **Interpolation**: Linear interpolation between vectors
 * - **Operator Overloading**: Natural mathematical syntax with Kotlin operators
 * - **Three.js Compatibility**: Familiar API matching Three.js patterns
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Create vectors
 * val position = Vector3(10f, 0f, 5f)
 * val direction = Vector3(0f, 1f, 0f) // Up
 *
 * // Arithmetic operations (mutable)
 * position.add(Vector3(1f, 0f, 0f))
 * direction.multiply(2f)
 *
 * // Arithmetic with operators (immutable)
 * val sum = position + direction
 * val scaled = direction * 3f
 * val difference = position - Vector3.ZERO
 *
 * // Vector operations
 * val length = position.length()
 * val normalized = position.normalized()
 * val distance = position.distance(Vector3.ZERO)
 * ```
 *
 * ## Vector Operations
 *
 * ```kotlin
 * val a = Vector3(1f, 0f, 0f)
 * val b = Vector3(0f, 1f, 0f)
 *
 * // Dot product (scalar)
 * val dot = a.dot(b) // 0.0
 *
 * // Cross product (perpendicular vector)
 * val cross = a.clone().cross(b) // Vector3(0, 0, 1)
 *
 * // Normalization
 * val dir = Vector3(3f, 4f, 0f)
 * dir.normalize() // Length becomes 1.0
 * ```
 *
 * ## Transformations
 *
 * ```kotlin
 * // Apply transformation matrix
 * val point = Vector3(1f, 2f, 3f)
 * point.applyMatrix4(transformMatrix)
 *
 * // Apply rotation quaternion
 * val direction = Vector3(1f, 0f, 0f)
 * direction.applyQuaternion(rotation)
 *
 * // Transform direction (no translation)
 * direction.transformDirection(matrix)
 * ```
 *
 * ## Interpolation
 *
 * ```kotlin
 * val start = Vector3(0f, 0f, 0f)
 * val end = Vector3(10f, 10f, 10f)
 *
 * // Linear interpolation
 * val mid = start.clone().lerp(end, 0.5f) // Vector3(5, 5, 5)
 *
 * // Between two vectors
 * val result = Vector3().lerpVectors(start, end, 0.25f)
 * ```
 *
 * ## Mutable vs Immutable Operations
 *
 * Most methods modify the vector in-place for performance:
 * ```kotlin
 * val v = Vector3(1f, 2f, 3f)
 * v.add(Vector3(1f, 1f, 1f)) // v is now (2, 3, 4)
 * ```
 *
 * Use [clone] or operators for immutable operations:
 * ```kotlin
 * val v1 = Vector3(1f, 2f, 3f)
 * val v2 = v1 + Vector3(1f, 1f, 1f) // v1 unchanged, v2 is (2, 3, 4)
 * ```
 *
 * ## Performance Considerations
 *
 * - Reuse vectors with [set] instead of creating new instances
 * - Use in-place operations (add, multiply) for better performance
 * - Consider object pooling for frequently allocated vectors
 * - Operators create new instances - use sparingly in hot code paths
 *
 * ## Coordinate System
 *
 * Materia uses a right-handed coordinate system:
 * - +X: Right
 * - +Y: Up
 * - +Z: Forward (toward viewer)
 *
 * @property x The x-component of the vector
 * @property y The y-component of the vector
 * @property z The z-component of the vector
 *
 * @constructor Creates a vector with the specified components
 *
 * @see Vector2 Two-component vector
 * @see Vector4 Four-component vector
 * @see Matrix4 For transformations
 * @see Quaternion For rotations
 *
 * @since 1.0.0
 * @sample io.materia.samples.Vector3Samples.basicOperations
 * @sample io.materia.samples.Vector3Samples.transformations
 */
@Serializable
data class Vector3(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) {
    constructor(scalar: Float) : this(scalar, scalar, scalar)
    constructor(other: Vector3) : this(other.x, other.y, other.z)
    constructor(v2: Vector2, z: Float = 0f) : this(v2.x, v2.y, z)

    // Basic operations

    /**
     * Sets the components of this vector.
     *
     * @param x The new x-component
     * @param y The new y-component
     * @param z The new z-component
     * @return This vector for method chaining
     * @since 1.0.0
     */
    fun set(x: Float, y: Float, z: Float): Vector3 {
        this.x = x
        this.y = y
        this.z = z
        return this
    }

    /**
     * Sets all components to the same scalar value.
     *
     * @param scalar The value to set for all components
     * @return This vector for method chaining
     * @since 1.0.0
     */
    fun set(scalar: Float): Vector3 = set(scalar, scalar, scalar)

    /**
     * Sets this vector's components from another vector.
     *
     * @param other The vector to copy from
     * @return This vector for method chaining
     * @since 1.0.0
     */
    fun set(other: Vector3): Vector3 = set(other.x, other.y, other.z)

    /**
     * Copies the values from another vector into this vector.
     *
     * @param other The vector to copy from
     * @return This vector for method chaining
     * @since 1.0.0
     */
    fun copy(other: Vector3): Vector3 = set(other)

    /**
     * Creates a new vector with the same component values as this vector.
     *
     * @return A new Vector3 instance with identical values
     * @since 1.0.0
     */
    fun clone(): Vector3 = Vector3(x, y, z)

    // Arithmetic operations
    fun add(other: Vector3): Vector3 {
        x = x + other.x
        y = y + other.y
        z = z + other.z
        return this
    }

    fun add(scalar: Float): Vector3 {
        x = x + scalar
        y = y + scalar
        z = z + scalar
        return this
    }

    fun subtract(other: Vector3): Vector3 {
        x = x - other.x
        y = y - other.y
        z = z - other.z
        return this
    }

    fun subtract(scalar: Float): Vector3 {
        x = x - scalar
        y = y - scalar
        z = z - scalar
        return this
    }

    fun multiply(other: Vector3): Vector3 {
        x = x * other.x
        y = y * other.y
        z = z * other.z
        return this
    }

    fun multiply(scalar: Float): Vector3 {
        x = x * scalar
        y = y * scalar
        z = z * scalar
        return this
    }

    fun divide(other: Vector3): Vector3 {
        val epsilon = 0.00001f
        x /= if (kotlin.math.abs(other.x) < epsilon) 1f else other.x
        y /= if (kotlin.math.abs(other.y) < epsilon) 1f else other.y
        z /= if (kotlin.math.abs(other.z) < epsilon) 1f else other.z
        return this
    }

    fun divide(scalar: Float): Vector3 {
        val epsilon = 0.00001f
        val safeDivisor = if (kotlin.math.abs(scalar) < epsilon) 1f else scalar
        x /= safeDivisor
        y /= safeDivisor
        z /= safeDivisor
        return this
    }

    // Vector operations
    fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 {
        val cx = y * other.z - z * other.y
        val cy = z * other.x - x * other.z
        val cz = x * other.y - y * other.x
        return set(cx, cy, cz)
    }

    fun crossVectors(a: Vector3, b: Vector3): Vector3 {
        val cx = a.y * b.z - a.z * b.y
        val cy = a.z * b.x - a.x * b.z
        val cz = a.x * b.y - a.y * b.x
        return set(cx, cy, cz)
    }

    fun length(): Float = sqrt(x * x + y * y + z * z)
    fun lengthSquared(): Float = x * x + y * y + z * z

    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0) divide(len) else this
    }

    fun distance(other: Vector3): Float = sqrt(distanceSquared(other))
    fun distanceSquared(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    // Three.js compatibility aliases
    fun sub(other: Vector3): Vector3 = subtract(other)
    fun multiplyScalar(scalar: Float): Vector3 = multiply(scalar)
    fun divideScalar(scalar: Float): Vector3 = divide(scalar)
    fun addScalar(scalar: Float): Vector3 = add(scalar)
    fun distanceTo(other: Vector3): Float = distance(other)
    fun distanceToSquared(other: Vector3): Float = distanceSquared(other)
    fun lengthSq(): Float = lengthSquared()

    // Additional Three.js compatibility methods
    fun lerpVectors(a: Vector3, b: Vector3, t: Float): Vector3 {
        x = a.x + (b.x - a.x) * t
        y = a.y + (b.y - a.y) * t
        z = a.z + (b.z - a.z) * t
        return this
    }

    fun subVectors(a: Vector3, b: Vector3): Vector3 {
        x = a.x - b.x
        y = a.y - b.y
        z = a.z - b.z
        return this
    }

    fun addVectors(a: Vector3, b: Vector3): Vector3 {
        x = a.x + b.x
        y = a.y + b.y
        z = a.z + b.z
        return this
    }

    fun setFromMatrixColumn(matrix: Matrix4, index: Int): Vector3 {
        val elements = matrix.elements
        when (index) {
            0 -> set(elements[0], elements[1], elements[2])
            1 -> set(elements[4], elements[5], elements[6])
            2 -> set(elements[8], elements[9], elements[10])
            3 -> set(elements[12], elements[13], elements[14])
            else -> throw IndexOutOfBoundsException("Matrix column index must be 0-3")
        }
        return this
    }

    // Component-wise min/max operations
    fun min(other: Vector3): Vector3 {
        x = minOf(x, other.x)
        y = minOf(y, other.y)
        z = minOf(z, other.z)
        return this
    }

    fun max(other: Vector3): Vector3 {
        x = maxOf(x, other.x)
        y = maxOf(y, other.y)
        z = maxOf(z, other.z)
        return this
    }

    // Clamp operations
    fun clamp(min: Vector3, max: Vector3): Vector3 {
        x = x.coerceIn(min.x, max.x)
        y = y.coerceIn(min.y, max.y)
        z = z.coerceIn(min.z, max.z)
        return this
    }

    fun clampScalar(min: Float, max: Float): Vector3 {
        x = x.coerceIn(min, max)
        y = y.coerceIn(min, max)
        z = z.coerceIn(min, max)
        return this
    }

    fun lerp(other: Vector3, t: Float): Vector3 {
        x += (other.x - x) * t
        y += (other.y - y) * t
        z += (other.z - z) * t
        return this
    }

    fun negate(): Vector3 {
        x = -x
        y = -y
        z = -z
        return this
    }

    // Operator overloading for arithmetic
    operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun plus(scalar: Float): Vector3 = Vector3(x + scalar, y + scalar, z + scalar)
    operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun minus(scalar: Float): Vector3 = Vector3(x - scalar, y - scalar, z - scalar)
    operator fun times(other: Vector3): Vector3 = Vector3(x * other.x, y * other.y, z * other.z)
    operator fun times(scalar: Float): Vector3 = Vector3((x * scalar), (y * scalar), (z * scalar))
    operator fun div(other: Vector3): Vector3 = Vector3(x / other.x, y / other.y, z / other.z)
    operator fun div(scalar: Float): Vector3 = Vector3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

    // Extension property for normalized vector
    val normalized: Vector3
        get() = clone().normalize()

    /**
     * Returns a new normalized vector (doesn't modify this vector)
     */
    fun normalized(): Vector3 = clone().normalize()

    // Mutable operators
    operator fun plusAssign(other: Vector3) {
        x = x + other.x
        y = y + other.y
        z = z + other.z
    }

    operator fun minusAssign(other: Vector3) {
        x = x - other.x
        y = y - other.y
        z = z - other.z
    }

    operator fun timesAssign(scalar: Float) {
        x = x * scalar
        y = y * scalar
        z = z * scalar
    }

    fun floor(): Vector3 {
        x = floor(x)
        y = floor(y)
        z = floor(z)
        return this
    }

    fun ceil(): Vector3 {
        x = ceil(x)
        y = ceil(y)
        z = ceil(z)
        return this
    }

    fun round(): Vector3 {
        x = round(x)
        y = round(y)
        z = round(z)
        return this
    }

    // Matrix transformations
    fun applyMatrix4(matrix: Matrix4): Vector3 {
        val e = matrix.elements
        val oldX = x
        val oldY = y
        val oldZ = z
        val w = 1f / (e[3] * oldX + e[7] * oldY + e[11] * oldZ + e[15])

        x = (e[0] * oldX + e[4] * oldY + e[8] * oldZ + e[12]) * w
        y = (e[1] * oldX + e[5] * oldY + e[9] * oldZ + e[13]) * w
        z = (e[2] * oldX + e[6] * oldY + e[10] * oldZ + e[14]) * w

        return this
    }

    fun applyMatrix3(matrix: Matrix3): Vector3 {
        val e = matrix.elements
        val oldX = x
        val oldY = y
        val oldZ = z

        x = e[0] * oldX + e[3] * oldY + e[6] * oldZ
        y = e[1] * oldX + e[4] * oldY + e[7] * oldZ
        z = e[2] * oldX + e[5] * oldY + e[8] * oldZ

        return this
    }

    /**
     * Transform direction vector by matrix (ignores translation)
     */
    fun transformDirection(matrix: Matrix4): Vector3 {
        val e = matrix.elements
        val oldX = x
        val oldY = y
        val oldZ = z

        // Apply only the rotational/scale part of the matrix (ignore translation)
        x = e[0] * oldX + e[4] * oldY + e[8] * oldZ
        y = e[1] * oldX + e[5] * oldY + e[9] * oldZ
        z = e[2] * oldX + e[6] * oldY + e[10] * oldZ

        return this
    }

    fun applyQuaternion(quaternion: Quaternion): Vector3 {
        val qx = quaternion.x
        val qy = quaternion.y
        val qz = quaternion.z
        val qw = quaternion.w

        // Calculate quat * vector
        val ix = qw * x + qy * z - qz * y
        val iy = qw * y + qz * x - qx * z
        val iz = qw * z + qx * y - qy * x
        val iw = -qx * x - qy * y - qz * z

        // Calculate result * inverse quat
        x = ix * qw + iw * -qx + iy * -qz - iz * -qy
        y = iy * qw + iw * -qy + iz * -qx - ix * -qz
        z = iz * qw + iw * -qz + ix * -qy - iy * -qx

        return this
    }

    // Utility
    fun equals(other: Vector3, epsilon: Float = 0.000001f): Boolean {
        return abs(x - other.x) < epsilon &&
                abs(y - other.y) < epsilon &&
                abs(z - other.z) < epsilon
    }

    fun isZero(epsilon: Float = 0.000001f): Boolean =
        abs(x) < epsilon && abs(y) < epsilon && abs(z) < epsilon

    fun maxComponent(): Float = maxOf(maxOf(abs(x), abs(y)), abs(z))

    fun minComponent(): Float = minOf(minOf(abs(x), abs(y)), abs(z))

    fun componentAt(index: Int): Float = when (index) {
        0 -> x
        1 -> y
        2 -> z
        else -> throw IndexOutOfBoundsException("Vector3 component index must be 0, 1, or 2")
    }

    fun coerceLength(minLength: Float, maxLength: Float): Vector3 {
        val currentLength = length()
        return when {
            currentLength < minLength && currentLength > 0.001f -> this * (minLength / currentLength)
            currentLength > maxLength -> this * (maxLength / currentLength)
            else -> this.clone()
        }
    }

    /**
     * Projects this vector from world coordinates to normalized device coordinates using the camera
     */
    fun project(camera: io.materia.camera.Camera): Vector3 {
        camera.worldToNDC(this, this)
        return this
    }

    /**
     * Unprojects this vector from normalized device coordinates to world coordinates using the camera
     */
    fun unproject(camera: io.materia.camera.Camera): Vector3 {
        camera.ndcToWorld(this, this)
        return this
    }

    /**
     * Set this vector from a BufferAttribute at the given index.
     * Compatible with Three.js BufferAttribute pattern.
     */
    fun fromBufferAttribute(attribute: io.materia.geometry.BufferAttribute, index: Int): Vector3 {
        x = attribute.getX(index)
        y = if (attribute.itemSize >= 2) attribute.getY(index) else 0f
        z = if (attribute.itemSize >= 3) attribute.getZ(index) else 0f
        return this
    }

    override fun toString(): String = "Vector3($x, $y, $z)"

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UNIT_X = Vector3(1f, 0f, 0f)
        val UNIT_Y = Vector3(0f, 1f, 0f)
        val UNIT_Z = Vector3(0f, 0f, 1f)
        val UP = Vector3(0f, 1f, 0f)
        val DOWN = Vector3(0f, -1f, 0f)
        val LEFT = Vector3(-1f, 0f, 0f)
        val RIGHT = Vector3(1f, 0f, 0f)
        val FORWARD = Vector3(0f, 0f, -1f)
        val BACK = Vector3(0f, 0f, 1f)

        // Aliases for compatibility
        val zero get() = ZERO
        val one get() = ONE
    }
}

// Operator extensions for Vector3
operator fun Float.times(v: Vector3): Vector3 = v * this
operator fun Double.times(v: Vector3): Vector3 = v * this.toFloat()
operator fun Int.times(v: Vector3): Vector3 = v * this.toFloat()