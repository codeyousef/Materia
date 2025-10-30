/**
 * Platform-specific functions for Kotlin Multiplatform
 */
package io.materia.core.platform

// currentTimeMillis is already declared in Platform.kt

/**
 * Platform-specific array copy function
 * Replaces System.arraycopy for multiplatform compatibility
 */
expect fun platformArrayCopy(
    src: FloatArray,
    srcPos: Int,
    dest: FloatArray,
    destPos: Int,
    length: Int
)

/**
 * Platform-specific array copy function for IntArray
 */
expect fun platformArrayCopy(
    src: IntArray,
    srcPos: Int,
    dest: IntArray,
    destPos: Int,
    length: Int
)

/**
 * Platform-specific clone function
 */
expect fun <T> platformClone(obj: T): T

/**
 * Simple cross-platform number formatting
 * Formats a number to specified decimal places
 */
fun formatFloat(value: Float, decimalPlaces: Int = 1): String {
    val factor = when (decimalPlaces) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1000.0
    }
    val rounded = kotlin.math.round(value * factor) / factor
    return rounded.toString()
}

fun formatDouble(value: Double, decimalPlaces: Int = 1): String {
    val factor = when (decimalPlaces) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        else -> 1000.0
    }
    val rounded = kotlin.math.round(value * factor) / factor
    return rounded.toString()
}