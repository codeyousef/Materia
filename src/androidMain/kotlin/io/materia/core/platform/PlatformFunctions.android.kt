package io.materia.core.platform

actual fun platformArrayCopy(
    src: FloatArray,
    srcPos: Int,
    dest: FloatArray,
    destPos: Int,
    length: Int
) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

actual fun platformArrayCopy(
    src: IntArray,
    srcPos: Int,
    dest: IntArray,
    destPos: Int,
    length: Int
) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

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
        else -> obj
    }
}
