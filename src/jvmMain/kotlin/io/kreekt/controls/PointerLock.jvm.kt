package io.kreekt.controls

actual object PointerLock {
    actual fun request(target: Any?, onRejected: ((String) -> Unit)?) {
        onRejected?.invoke("Pointer lock is not supported on the JVM platform.")
    }

    actual fun exit() {
        // No-op on JVM
    }

    actual fun isSupported(): Boolean = false
}
