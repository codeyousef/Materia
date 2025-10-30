package io.materia.core.math

/**
 * High-performance object pool for frequently allocated math objects
 * Reduces GC pressure by reusing Vector3, Matrix4, Quaternion instances
 */
class ObjectPool<T>(
    private val factory: () -> T,
    private val reset: (T) -> Unit = {},
    private val maxSize: Int = 100
) {
    private val pool = ArrayDeque<T>(maxSize)
    private var borrowed = 0

    /**
     * Borrow an object from the pool
     */
    fun borrow(): T {
        borrowed++
        return if (pool.isNotEmpty()) {
            pool.removeFirst()
        } else {
            factory()
        }
    }

    /**
     * Return an object to the pool
     */
    fun returnObject(obj: T) {
        if (borrowed > 0) borrowed--
        if (pool.size < maxSize) {
            reset(obj)
            pool.addLast(obj)
        }
    }

    /**
     * Clear the pool
     */
    fun clear() {
        pool.clear()
        borrowed = 0
    }

    /**
     * Get pool statistics
     */
    fun getStats(): PoolStatsInternal = PoolStatsInternal(
        available = pool.size,
        borrowed = borrowed,
        capacity = maxSize
    )
}

/**
 * Internal pool statistics data class
 */
data class PoolStatsInternal(
    val available: Int,
    val borrowed: Int,
    val capacity: Int
)
