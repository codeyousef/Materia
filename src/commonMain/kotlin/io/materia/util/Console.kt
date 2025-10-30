package io.materia.util

/**
 * Multiplatform console facade so common code can log without depending on JS globals.
 */
expect object console {
    fun log(message: String)
    fun warn(message: String)
    fun error(message: String)
}