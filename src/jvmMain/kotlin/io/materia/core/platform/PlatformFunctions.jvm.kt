/**
 * JVM implementations for platform-specific functions
 */
package io.materia.core.platform

/**
 * JVM implementation of platform array copy for FloatArray
 */
actual fun platformArrayCopy(
    src: FloatArray,
    srcPos: Int,
    dest: FloatArray,
    destPos: Int,
    length: Int
) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

/**
 * JVM implementation of platform array copy for IntArray
 */
actual fun platformArrayCopy(
    src: IntArray,
    srcPos: Int,
    dest: IntArray,
    destPos: Int,
    length: Int
) {
    System.arraycopy(src, srcPos, dest, destPos, length)
}

