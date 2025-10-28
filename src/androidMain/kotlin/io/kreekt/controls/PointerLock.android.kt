package io.kreekt.controls

actual object PointerLock {
    actual fun request(target: Any?, onRejected: ((String) -> Unit)?) {
        onRejected?.invoke("Pointer lock is not supported on Android.")
    }

    actual fun exit() {
        // No-op on Android
    }

    actual fun isSupported(): Boolean = false
}
