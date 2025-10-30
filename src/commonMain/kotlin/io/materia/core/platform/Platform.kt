package io.materia.core.platform

/**
 * Platform abstraction for time measurement
 */
expect fun currentTimeMillis(): Long

/**
 * Platform abstraction for high-resolution time measurement (nanoseconds)
 */
expect fun nanoTime(): Long

/**
 * Platform abstraction for performance measurement
 */
expect fun performanceNow(): Double

/**
 * Platform abstraction for array cloning
 */
expect fun FloatArray.platformClone(): FloatArray
expect fun IntArray.platformClone(): IntArray
expect fun DoubleArray.platformClone(): DoubleArray

/**
 * Platform abstraction for generic array cloning
 */
expect fun <T> Array<T>.platformClone(): Array<T>

/**
 * Platform utilities for profiling
 */
object Platform {
    /**
     * Get current time in nanoseconds
     */
    fun currentTimeNanos(): Long = nanoTime()

    /**
     * Get used memory in bytes
     */
    fun getUsedMemory(): Long = getMemoryUsage().used

    /**
     * Get total memory in bytes
     */
    fun getTotalMemory(): Long = getMemoryUsage().total
}

/**
 * Memory usage information
 */
data class MemoryUsage(
    val used: Long,
    val total: Long,
    val free: Long
)

/**
 * Get memory usage for current platform
 */
expect fun getMemoryUsage(): MemoryUsage