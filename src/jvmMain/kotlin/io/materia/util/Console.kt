package io.materia.util

actual object console {
    actual fun log(message: String) {
        println(message)
    }

    actual fun warn(message: String) {
        System.err.println("WARN: $message")
    }

    actual fun error(message: String) {
        System.err.println("ERROR: $message")
    }
}