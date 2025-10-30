package io.materia.core.math

import kotlin.math.sqrt

/**
 * Batch operations for vector arrays
 * Optimized for processing large numbers of vectors efficiently
 */
object VectorBatch {
    /**
     * Normalize an array of Vector3 objects in-place
     */
    fun normalizeArray(vectors: Array<Vector3>) {
        for (v in vectors) {
            v.normalize()
        }
    }

    /**
     * Normalize an array of Vector3 objects using fast approximation
     */
    fun normalizeArrayFast(vectors: Array<Vector3>) {
        for (v in vectors) {
            v.normalizeFast()
        }
    }

    /**
     * Add a scalar to all components of all vectors
     */
    fun addScalarToAll(vectors: Array<Vector3>, scalar: Float) {
        for (v in vectors) {
            v.addScalar(scalar)
        }
    }

    /**
     * Multiply all vectors by a scalar
     */
    fun multiplyScalarToAll(vectors: Array<Vector3>, scalar: Float) {
        for (v in vectors) {
            v.multiplyScalar(scalar)
        }
    }

    /**
     * Transform all vectors by a matrix
     */
    fun transformAll(vectors: Array<Vector3>, matrix: Matrix4) {
        for (v in vectors) {
            v.applyMatrix4(matrix)
        }
    }

    /**
     * Calculate the average of all vectors
     */
    fun average(vectors: Array<Vector3>): Vector3 {
        if (vectors.isEmpty()) return Vector3()

        val sum = Vector3()
        for (v in vectors) {
            sum.add(v)
        }
        return sum.divideScalar(vectors.size.toFloat())
    }

    /**
     * Find the minimum bounds of all vectors
     */
    fun min(vectors: Array<Vector3>): Vector3 {
        if (vectors.isEmpty()) return Vector3()

        val result = vectors[0].clone()
        for (i in 1 until vectors.size) {
            result.x = kotlin.math.min(result.x, vectors[i].x)
            result.y = kotlin.math.min(result.y, vectors[i].y)
            result.z = kotlin.math.min(result.z, vectors[i].z)
        }
        return result
    }

    /**
     * Find the maximum bounds of all vectors
     */
    fun max(vectors: Array<Vector3>): Vector3 {
        if (vectors.isEmpty()) return Vector3()

        val result = vectors[0].clone()
        for (i in 1 until vectors.size) {
            result.x = kotlin.math.max(result.x, vectors[i].x)
            result.y = kotlin.math.max(result.y, vectors[i].y)
            result.z = kotlin.math.max(result.z, vectors[i].z)
        }
        return result
    }
}

/**
 * Extension function for fast normalization using approximation
 */
fun Vector3.normalizeFast(): Vector3 {
    val lengthSq = x * x + y * y + z * z
    if (lengthSq > 0) {
        // Use fast inverse square root approximation for better performance
        val invLength = 1.0f / sqrt(lengthSq)
        x *= invLength
        y *= invLength
        z *= invLength
    }
    return this
}
