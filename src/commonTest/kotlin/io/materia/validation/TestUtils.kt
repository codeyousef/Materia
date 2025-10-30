package io.materia.validation

import kotlinx.datetime.Clock
import kotlin.math.pow
import kotlin.math.round

/**
 * Format a floating-point number with specified decimal places.
 * This is a multiplatform replacement for String.format().
 */
fun Double.format(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> 10.0.pow(decimals.toDouble())
    }
    val rounded = round(this * multiplier) / multiplier
    return rounded.toString()
}

/**
 * Format a floating-point number with specified decimal places.
 * This is a multiplatform replacement for String.format().
 */
fun Float.format(decimals: Int): String {
    return this.toDouble().format(decimals)
}

// Test-only time counter to avoid kotlinx.datetime.Clock.System resolution issues
private var testTimeCounter = 1_000_000_000_000L

/**
 * Get current time in epoch milliseconds.
 * For testing purposes, returns an incrementing counter.
 * This avoids the Clock.System resolution issue in the test environment.
 */
fun currentTimeMillis(): Long {
    return testTimeCounter++
}

/**
 * Get current time in epoch milliseconds.
 * For testing purposes, returns an incrementing counter.
 */
fun getCurrentTimeMillis(): Long {
    return testTimeCounter++
}