package io.materia.core.platform

import kotlin.time.TimeSource

/**
 * Native implementation of platform abstractions
 */
private val timeSource = TimeSource.Monotonic

actual fun currentTimeMillis(): Long = timeSource.markNow().elapsedNow().inWholeMilliseconds

actual fun nanoTime(): Long = timeSource.markNow().elapsedNow().inWholeNanoseconds

actual fun performanceNow(): Double =
    timeSource.markNow().elapsedNow().inWholeNanoseconds / 1_000_000.0

actual fun FloatArray.platformClone(): FloatArray {
    val result = FloatArray(this.size)
    for (i in this.indices) {
        result[i] = this[i]
    }
    return result
}

actual fun IntArray.platformClone(): IntArray {
    val result = IntArray(this.size)
    for (i in this.indices) {
        result[i] = this[i]
    }
    return result
}

actual fun DoubleArray.platformClone(): DoubleArray {
    val result = DoubleArray(this.size)
    for (i in this.indices) {
        result[i] = this[i]
    }
    return result
}

actual fun <T> Array<T>.platformClone(): Array<T> {
    return this.copyOf()
}

/**
 * Native implementation of memory usage tracking
 * Note: Limited memory tracking on native platforms
 */
actual fun getMemoryUsage(): MemoryUsage {
    // Native platforms don't have easy access to memory stats
    // Would need platform-specific APIs (e.g., mallinfo on Linux)
    return MemoryUsage(
        used = 0L,
        total = 0L,
        free = 0L
    )
}