package io.materia.core.math

import kotlin.math.abs

/**
 * Math utilities for common mathematical operations
 */
expect object MathUtils {
    /**
     * Power function for Float values
     */
    fun pow(base: Float, exponent: Float): Float

    /**
     * Power function for Double values
     */
    fun pow(base: Double, exponent: Double): Double
}

/**
 * Epsilon value for floating-point comparisons
 * Use 1e-6 for float precision (approximately 6 decimal places)
 */
const val EPSILON = 1e-6f

/**
 * Compare two floats for near-equality using epsilon
 * @param a First float value
 * @param b Second float value
 * @param epsilon Tolerance for comparison (default: EPSILON)
 * @return true if values are within epsilon of each other
 */
fun floatEquals(a: Float, b: Float, epsilon: Float = EPSILON): Boolean {
    return abs(a - b) < epsilon
}

/**
 * Check if a float is approximately zero
 * @param value Float value to check
 * @param epsilon Tolerance for comparison (default: EPSILON)
 * @return true if value is within epsilon of zero
 */
fun floatIsZero(value: Float, epsilon: Float = EPSILON): Boolean {
    return abs(value) < epsilon
}

/**
 * Safe normalization that checks for zero-length vectors
 * Returns a normalized copy if length > epsilon, otherwise returns zero vector
 */
fun Vector3.safeNormalized(epsilon: Float = 0.001f): Vector3 {
    val len = length()
    return if (len > epsilon) {
        clone().apply { divideScalar(len) }
    } else {
        Vector3.ZERO
    }
}

/**
 * Safe normalization that checks for zero-length vectors
 * Returns a normalized copy if length > epsilon, otherwise returns zero vector
 */
fun Vector2.safeNormalized(epsilon: Float = 0.001f): Vector2 {
    val len = length()
    return if (len > epsilon) {
        clone().apply { divideScalar(len) }
    } else {
        Vector2.ZERO
    }
}

/**
 * Safe acos that clamps input to valid domain [-1, 1]
 */
fun safeAcos(value: Float): Float = kotlin.math.acos(value.coerceIn(-1f, 1f))

/**
 * Safe asin that clamps input to valid domain [-1, 1]
 */
fun safeAsin(value: Float): Float = kotlin.math.asin(value.coerceIn(-1f, 1f))

/**
 * Extension function to check if float is approximately zero
 */
fun Float.isApproxZero(epsilon: Float = EPSILON): Boolean = abs(this) < epsilon

/**
 * Extension function to check if float is approximately equal to another
 */
fun Float.isApproxEqual(other: Float, epsilon: Float = EPSILON): Boolean =
    abs(this - other) < epsilon