package io.materia.core.math

/**
 * Object pooling for frequently created math objects
 * Reduces allocations and improves performance in hot paths
 */
object MathObjectPools {
    private val vector3Pool = mutableListOf<Vector3>()
    private val vector2Pool = mutableListOf<Vector2>()
    private val vector4Pool = mutableListOf<Vector4>()
    private val matrix4Pool = mutableListOf<Matrix4>()
    private val quaternionPool = mutableListOf<Quaternion>()

    private var vector3Borrowed = 0
    private var vector3Returned = 0
    private var vector2Borrowed = 0
    private var vector2Returned = 0

    /**
     * Get a Vector3 from the pool
     */
    fun getVector3(): Vector3 {
        vector3Borrowed++
        return if (vector3Pool.isNotEmpty()) {
            vector3Pool.removeLast()
        } else {
            Vector3()
        }
    }

    /**
     * Return a Vector3 to the pool
     */
    fun returnVector3(v: Vector3) {
        vector3Returned++
        v.set(0f, 0f, 0f) // Reset to zero
        vector3Pool.add(v)
    }

    /**
     * Use a Vector3 from the pool and automatically return it
     */
    inline fun <R> withVector3(block: (Vector3) -> R): R {
        val v = getVector3()
        try {
            return block(v)
        } finally {
            returnVector3(v)
        }
    }

    /**
     * Get a Vector2 from the pool
     */
    fun getVector2(): Vector2 {
        vector2Borrowed++
        return if (vector2Pool.isNotEmpty()) {
            vector2Pool.removeLast()
        } else {
            Vector2()
        }
    }

    /**
     * Return a Vector2 to the pool
     */
    fun returnVector2(v: Vector2) {
        vector2Returned++
        v.set(0f, 0f)
        vector2Pool.add(v)
    }

    /**
     * Use a Vector2 from the pool and automatically return it
     */
    inline fun <R> withVector2(block: (Vector2) -> R): R {
        val v = getVector2()
        try {
            return block(v)
        } finally {
            returnVector2(v)
        }
    }

    /**
     * Get a Matrix4 from the pool
     */
    fun getMatrix4(): Matrix4 {
        return if (matrix4Pool.isNotEmpty()) {
            matrix4Pool.removeLast()
        } else {
            Matrix4()
        }
    }

    /**
     * Return a Matrix4 to the pool
     */
    fun returnMatrix4(m: Matrix4) {
        m.identity()
        matrix4Pool.add(m)
    }

    /**
     * Use a Matrix4 from the pool and automatically return it
     */
    inline fun <R> withMatrix4(block: (Matrix4) -> R): R {
        val m = getMatrix4()
        try {
            return block(m)
        } finally {
            returnMatrix4(m)
        }
    }

    /**
     * Get a Quaternion from the pool
     */
    fun getQuaternion(): Quaternion {
        return if (quaternionPool.isNotEmpty()) {
            quaternionPool.removeLast()
        } else {
            Quaternion()
        }
    }

    /**
     * Return a Quaternion to the pool
     */
    fun returnQuaternion(q: Quaternion) {
        q.set(0f, 0f, 0f, 1f) // Reset to identity
        quaternionPool.add(q)
    }

    /**
     * Use a Quaternion from the pool and automatically return it
     */
    inline fun <R> withQuaternion(block: (Quaternion) -> R): R {
        val q = getQuaternion()
        try {
            return block(q)
        } finally {
            returnQuaternion(q)
        }
    }

    /**
     * Get pool statistics
     */
    fun getStats(): PoolStats {
        return PoolStats(
            vector3Pooled = vector3Pool.size,
            vector3Borrowed = vector3Borrowed,
            vector3Returned = vector3Returned,
            vector2Pooled = vector2Pool.size,
            vector2Borrowed = vector2Borrowed,
            vector2Returned = vector2Returned,
            matrix4Pooled = matrix4Pool.size
        )
    }

    /**
     * Clear all pools
     */
    fun clear() {
        vector3Pool.clear()
        vector2Pool.clear()
        vector4Pool.clear()
        matrix4Pool.clear()
        quaternionPool.clear()
        vector3Borrowed = 0
        vector3Returned = 0
        vector2Borrowed = 0
        vector2Returned = 0
    }
}

data class PoolStats(
    val vector3Pooled: Int,
    val vector3Borrowed: Int,
    val vector3Returned: Int,
    val vector2Pooled: Int,
    val vector2Borrowed: Int,
    val vector2Returned: Int,
    val matrix4Pooled: Int
)
