package io.kreekt.engine.math

import kotlin.math.sqrt

@PublishedApi
internal fun vec2Array(x: Float = 0f, y: Float = 0f): FloatArray = floatArrayOf(x, y)

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
        val Zero: Vec2
            get() = vec2()

        val One: Vec2
            get() = vec2(1f, 1f)
    }
}

fun vec2(x: Float = 0f, y: Float = 0f): Vec2 = Vec2(vec2Array(x, y))

@PublishedApi
internal fun vec3Array(x: Float = 0f, y: Float = 0f, z: Float = 0f): FloatArray = floatArrayOf(x, y, z)

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
        val Zero: Vec3
            get() = vec3()

        val One: Vec3
            get() = vec3(1f, 1f, 1f)

        val Up: Vec3
            get() = vec3(0f, 1f, 0f)

        val Forward: Vec3
            get() = vec3(0f, 0f, -1f)

        val Right: Vec3
            get() = vec3(1f, 0f, 0f)
    }
}

fun vec3(x: Float = 0f, y: Float = 0f, z: Float = 0f): Vec3 = Vec3(vec3Array(x, y, z))

@PublishedApi
internal fun vec4Array(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): FloatArray =
    floatArrayOf(x, y, z, w)

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
        val Zero: Vec4
            get() = vec4()
    }
}

fun vec4(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 0f): Vec4 = Vec4(vec4Array(x, y, z, w))

typealias Vector3f = Vec3
