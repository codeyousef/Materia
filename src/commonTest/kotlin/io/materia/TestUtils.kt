package io.materia

import kotlin.math.pow
import kotlin.random.Random

/**
 * Test utilities for cross-platform compatibility
 */

/**
 * Math utilities object for test compatibility
 * Maps JavaScript-style Math to Kotlin math
 */
object Math {
    const val PI: Double = kotlin.math.PI
    const val E: Double = kotlin.math.E

    fun random(): Double = Random.nextDouble()
    fun abs(x: Double): Double = kotlin.math.abs(x)
    fun abs(x: Float): Float = kotlin.math.abs(x)
    fun abs(x: Int): Int = kotlin.math.abs(x)
    fun sin(x: Double): Double = kotlin.math.sin(x)
    fun cos(x: Double): Double = kotlin.math.cos(x)
    fun tan(x: Double): Double = kotlin.math.tan(x)
    fun asin(x: Double): Double = kotlin.math.asin(x)
    fun acos(x: Double): Double = kotlin.math.acos(x)
    fun atan(x: Double): Double = kotlin.math.atan(x)
    fun atan2(y: Double, x: Double): Double = kotlin.math.atan2(y, x)
    fun sqrt(x: Double): Double = kotlin.math.sqrt(x)
    fun sqrt(x: Float): Float = kotlin.math.sqrt(x)
    fun pow(x: Double, y: Double): Double = x.pow(y)
    fun pow(x: Float, y: Float): Float = x.toDouble().pow(y.toDouble()).toFloat()
    fun floor(x: Double): Double = kotlin.math.floor(x)
    fun ceil(x: Double): Double = kotlin.math.ceil(x)
    fun round(x: Double): Double = kotlin.math.round(x)
    fun min(a: Double, b: Double): Double = kotlin.math.min(a, b)
    fun max(a: Double, b: Double): Double = kotlin.math.max(a, b)
    fun min(a: Float, b: Float): Float = kotlin.math.min(a, b)
    fun max(a: Float, b: Float): Float = kotlin.math.max(a, b)
    fun min(a: Int, b: Int): Int = kotlin.math.min(a, b)
    fun max(a: Int, b: Int): Int = kotlin.math.max(a, b)
}

/**
 * System utilities for cross-platform timing
 */
object System {
    fun currentTimeMillis(): Long = io.materia.core.platform.currentTimeMillis()
    fun nanoTime(): Long = io.materia.core.platform.currentTimeMillis() * 1_000_000L
}
