package io.materia.controls

actual object PointerLock {
    actual fun request(target: Any?, onRejected: ((String) -> Unit)?) {
        onRejected?.invoke("Pointer lock is not supported on native targets.")
    }

    actual fun exit() {
        // No-op
    }

    actual fun isSupported(): Boolean = false
}
