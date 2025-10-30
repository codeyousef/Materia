package io.materia.core.math

import kotlin.jvm.JvmInline

/**
 * Inline value classes for zero-allocation math operations
 * These classes are optimized for hot paths and avoid object allocation overhead
 */

/**
 * Inline wrapper for angle values to avoid Float boxing
 */
@JvmInline
value class Angle(val radians: Float) {
    val degrees: Float get() = radians * 57.29578f // 180/PI

    companion object {
        fun fromDegrees(degrees: Float) = Angle(degrees * 0.017453292f) // PI/180
        fun fromRadians(radians: Float) = Angle(radians)
    }
}

/**
 * Inline wrapper for distance values
 */
@JvmInline
value class Distance(val value: Float) {
    operator fun compareTo(other: Distance): Int = value.compareTo(other.value)
    operator fun plus(other: Distance): Distance = Distance(value + other.value)
    operator fun minus(other: Distance): Distance = Distance(value - other.value)
}

/**
 * Fast math utilities for hot paths
 * Note: inline modifier removed as these functions don't have function type parameters
 * The JVM will apply its own inlining optimizations where beneficial
 */
object FastMath {
    /**
     * Fast approximation of 1/sqrt(x) using inverse square root
     */
    fun invSqrt(x: Float): Float {
        // Note: On modern JVMs, kotlin.math.sqrt is already optimized
        return 1f / kotlin.math.sqrt(x)
    }

    /**
     * Fast vector length squared (avoids sqrt)
     */
    fun lengthSquared(x: Float, y: Float, z: Float): Float {
        return x * x + y * y + z * z
    }

    /**
     * Fast vector length
     */
    fun length(x: Float, y: Float, z: Float): Float {
        return kotlin.math.sqrt(x * x + y * y + z * z)
    }

    /**
     * Fast dot product
     */
    fun dot(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        return x1 * x2 + y1 * y2 + z1 * z2
    }

    /**
     * Fast distance squared
     */
    fun distanceSquared(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        return dx * dx + dy * dy + dz * dz
    }

    /**
     * Fast lerp without allocation
     */
    fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    /**
     * Clamp value without branching
     */
    fun clamp(value: Float, min: Float, max: Float): Float {
        return value.coerceIn(min, max)
    }
}

/**
 * Extension functions for fast math operations
 * Note: inline modifier removed as these functions don't have function type parameters
 * The JVM will apply its own inlining optimizations where beneficial
 */
fun Vector3.setFast(x: Float, y: Float, z: Float) {
    this.x = x
    this.y = y
    this.z = z
}

fun Vector3.addFast(x: Float, y: Float, z: Float) {
    this.x += x
    this.y += y
    this.z += z
}

fun Vector3.multiplyScalarFast(scalar: Float) {
    this.x *= scalar
    this.y *= scalar
    this.z *= scalar
}
