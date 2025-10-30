package io.materia.morph

import io.materia.core.math.Vector3

/**
 * Morph target (blend shape) data
 */
data class MorphTarget(
    val name: String,
    val vertices: List<Vector3>,
    val normals: List<Vector3>? = null
)

/**
 * Morph target influences
 */
class MorphInfluences(size: Int) {
    val values: FloatArray = FloatArray(size)

    operator fun get(index: Int): Float = values[index]
    operator fun set(index: Int, value: Float) {
        values[index] = value.coerceIn(0f, 1f)
    }
}