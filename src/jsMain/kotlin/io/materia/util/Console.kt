package io.materia.util

actual object console {
    actual fun log(message: String) {
        kotlin.js.console.log(message)
    }

    actual fun warn(message: String) {
        kotlin.js.console.warn(message)
    }

    actual fun error(message: String) {
        kotlin.js.console.error(message)
    }
}