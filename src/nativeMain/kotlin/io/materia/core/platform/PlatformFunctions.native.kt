package io.materia.core.platform

/**
 * Native implementation of platform functions
 */

actual fun platformArrayCopy(
    src: FloatArray,
    srcPos: Int,
    dest: FloatArray,
    destPos: Int,
    length: Int
) {
    for (i in 0 until length) {
        dest[destPos + i] = src[srcPos + i]
    }
}

actual fun platformArrayCopy(
    src: IntArray,
    srcPos: Int,
    dest: IntArray,
    destPos: Int,
    length: Int
) {
    for (i in 0 until length) {
        dest[destPos + i] = src[srcPos + i]
    }
}

@Suppress("UNCHECKED_CAST")
actual fun <T> platformClone(obj: T): T {
    return when (obj) {
        is FloatArray -> obj.copyOf() as T
        is IntArray -> obj.copyOf() as T
        is DoubleArray -> obj.copyOf() as T
        is Array<*> -> obj.copyOf() as T
        is MutableList<*> -> (obj as MutableList<*>).toMutableList() as T
        is MutableMap<*, *> -> (obj as MutableMap<*, *>).toMutableMap() as T
        is MutableSet<*> -> (obj as MutableSet<*>).toMutableSet() as T
        else -> obj // For immutable objects, return as-is
    }
}