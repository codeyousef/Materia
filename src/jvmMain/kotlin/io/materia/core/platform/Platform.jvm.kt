package io.materia.core.platform

/**
 * JVM implementation of platform abstractions
 */
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun nanoTime(): Long = System.nanoTime()

actual fun performanceNow(): Double = System.nanoTime() / 1_000_000.0

actual fun FloatArray.platformClone(): FloatArray = this.clone()
actual fun IntArray.platformClone(): IntArray = this.clone()
actual fun DoubleArray.platformClone(): DoubleArray = this.clone()

@Suppress("UNCHECKED_CAST")
actual fun <T> Array<T>.platformClone(): Array<T> = this.clone() as Array<T>

@Suppress("UNCHECKED_CAST")
actual fun <T> platformClone(obj: T): T {
    return when (obj) {
        is FloatArray -> obj.clone() as T
        is IntArray -> obj.clone() as T
        is DoubleArray -> obj.clone() as T
        is Array<*> -> obj.clone() as T
        is MutableList<*> -> (obj as MutableList<*>).toMutableList() as T
        is MutableMap<*, *> -> (obj as MutableMap<*, *>).toMutableMap() as T
        is MutableSet<*> -> (obj as MutableSet<*>).toMutableSet() as T
        else -> obj // For immutable objects, return as-is
    }
}

/**
 * JVM implementation of memory usage tracking
 */
actual fun getMemoryUsage(): MemoryUsage {
    val runtime = Runtime.getRuntime()
    val total = runtime.totalMemory()
    val free = runtime.freeMemory()
    val used = total - free

    return MemoryUsage(
        used = used,
        total = total,
        free = free
    )
}