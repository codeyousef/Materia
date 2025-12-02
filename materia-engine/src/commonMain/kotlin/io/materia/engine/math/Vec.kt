package io.materia.engine.math

import kotlin.jvm.JvmInline
import kotlin.math.sqrt

@PublishedApi
internal fun vec2Array(x: Float = 0f, y: Float = 0f): FloatArray = floatArrayOf(x, y)

/**
 * Mutable 2D vector backed by a [FloatArray] for zero-allocation math operations.
 *
 * This is an inline value class wrapping a two-element float array, enabling
 * efficient GPU-compatible data layouts while providing convenient vector operations.
 *
 * @see vec2 Factory function for creating instances.
 */
@JvmInline
value class Vec2 @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 2) { "Vec2 requires exactly 2 elements (found ${data.size})" }
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

    fun set(x: Float, y: Float): Vec2 = apply {
        this.x = x
        this.y = y
    }

    fun set(other: Vec2): Vec2 = set(other.x, other.y)

    fun copy(): Vec2 = vec2(x, y)

    fun length(): Float = sqrt(x * x + y * y)

    fun normalize(): Vec2 {
        val len = length()
        if (len == 0f) return this
        val inv = 1f / len
        x *= inv
        y *= inv
        return this
    }

    fun normalized(): Vec2 = copy().normalize()

    operator fun plus(other: Vec2): Vec2 = vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = vec2(x - other.x, y - other.y)

    operator fun times(scalar: Float): Vec2 = vec2(x * scalar, y * scalar)

    operator fun div(scalar: Float): Vec2 = vec2(x / scalar, y / scalar)

    fun dot(other: Vec2): Float = x * other.x + y * other.y

    fun toFloatArray(copy: Boolean = false): FloatArray = if (copy) data.copyOf() else data

    companion object {
        /** A zero vector (0, 0). Returns a new instance on each access. */
        val Zero: Vec2
            get() = vec2()

        /** A unit vector (1, 1). Returns a new instance on each access. */
        val One: Vec2
            get() = vec2(1f, 1f)
    }
}

/**
 * Creates a new [Vec2] with the specified components.
 *
 * @param x The X component, defaults to 0.
 * @param y The Y component, defaults to 0.
 * @return A new mutable 2D vector.
 */
fun vec2(x: Float = 0f, y: Float = 0f): Vec2 = Vec2(vec2Array(x, y))

@PublishedApi
internal fun vec3Array(x: Float = 0f, y: Float = 0f, z: Float = 0f): FloatArray =
    floatArrayOf(x, y, z)

/**
 * Mutable 3D vector backed by a [FloatArray] for zero-allocation math operations.
 *
 * This is the primary vector type for positions, directions, scales, and colors
 * (RGB) throughout the engine. Methods like [normalize], [add], and [scale]
 * mutate in-place and return `this` for chaining.
 *
 * @see vec3 Factory function for creating instances.
 */
@JvmInline
value class Vec3 @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 3) { "Vec3 requires exactly 3 elements (found ${data.size})" }
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

    fun set(x: Float, y: Float, z: Float): Vec3 = apply {
        this.x = x
        this.y = y
        this.z = z
    }

    fun set(other: Vec3): Vec3 = set(other.x, other.y, other.z)

    fun copy(): Vec3 = vec3(x, y, z)

    fun copyFrom(other: Vec3): Vec3 = set(other.x, other.y, other.z)

    fun add(other: Vec3): Vec3 = set(x + other.x, y + other.y, z + other.z)

    fun subtract(other: Vec3): Vec3 = set(x - other.x, y - other.y, z - other.z)

    fun scale(factor: Float): Vec3 = set(x * factor, y * factor, z * factor)

    fun scaled(factor: Float): Vec3 = copy().scale(factor)

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalize(): Vec3 {
        val len = length()
        if (len == 0f) return this
        val inv = 1f / len
        x *= inv
        y *= inv
        z *= inv
        return this
    }

    fun normalized(): Vec3 = copy().normalize()

    operator fun plus(other: Vec3): Vec3 = vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3): Vec3 = vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Float): Vec3 = vec3(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Float): Vec3 = vec3(x / scalar, y / scalar, z / scalar)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun toFloatArray(copy: Boolean = false): FloatArray = if (copy) data.copyOf() else data

    companion object {
        /** A zero vector (0, 0, 0). Returns a new instance on each access. */
        val Zero: Vec3
            get() = vec3()

        /** A unit vector (1, 1, 1). Returns a new instance on each access. */
        val One: Vec3
            get() = vec3(1f, 1f, 1f)

        /** World-space up direction (0, 1, 0) in Y-up coordinate system. */
        val Up: Vec3
            get() = vec3(0f, 1f, 0f)

        /** World-space forward direction (0, 0, -1) in right-handed coordinate system. */
        val Forward: Vec3
            get() = vec3(0f, 0f, -1f)

        /** World-space right direction (1, 0, 0). */
        val Right: Vec3
            get() = vec3(1f, 0f, 0f)
    }
}

/**
 * Creates a new [Vec3] with the specified components.
 *
 * @param x The X component, defaults to 0.
 * @param y The Y component, defaults to 0.
 * @param z The Z component, defaults to 0.
 * @return A new mutable 3D vector.
 */
fun vec3(x: Float = 0f, y: Float = 0f, z: Float = 0f): Vec3 = Vec3(vec3Array(x, y, z))

@PublishedApi
internal fun vec4Array(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): FloatArray =
    floatArrayOf(x, y, z, w)

/**
 * Mutable 4D vector backed by a [FloatArray] for zero-allocation math operations.
 *
 * Commonly used for homogeneous coordinates, RGBA colors, and quaternion storage.
 *
 * @see vec4 Factory function for creating instances.
 */
@JvmInline
value class Vec4 @PublishedApi internal constructor(internal val data: FloatArray) {
    init {
        require(data.size == 4) { "Vec4 requires exactly 4 elements (found ${data.size})" }
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

    fun set(x: Float, y: Float, z: Float, w: Float): Vec4 = apply {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    fun set(other: Vec4): Vec4 = set(other.x, other.y, other.z, other.w)

    fun copy(): Vec4 = vec4(x, y, z, w)

    operator fun plus(other: Vec4): Vec4 = vec4(x + other.x, y + other.y, z + other.z, w + other.w)

    operator fun minus(other: Vec4): Vec4 = vec4(x - other.x, y - other.y, z - other.z, w - other.w)

    operator fun times(scalar: Float): Vec4 = vec4(x * scalar, y * scalar, z * scalar, w * scalar)

    operator fun div(scalar: Float): Vec4 = vec4(x / scalar, y / scalar, z / scalar, w / scalar)

    fun dot(other: Vec4): Float = x * other.x + y * other.y + z * other.z + w * other.w

    fun toFloatArray(copy: Boolean = false): FloatArray = if (copy) data.copyOf() else data

    companion object {
        /** A zero vector (0, 0, 0, 0). Returns a new instance on each access. */
        val Zero: Vec4
            get() = vec4()
    }
}

/**
 * Creates a new [Vec4] with the specified components.
 *
 * @param x The X component, defaults to 0.
 * @param y The Y component, defaults to 0.
 * @param z The Z component, defaults to 0.
 * @param w The W component, defaults to 0.
 * @return A new mutable 4D vector.
 */
fun vec4(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): Vec4 =
    Vec4(vec4Array(x, y, z, w))

/** Alias for [Vec3] for compatibility with APIs expecting Vector3f naming. */
typealias Vector3f = Vec3
