package io.materia.controls

/**
 * Multiplatform pointer lock utility.
 *
 * On platforms that support the browser Pointer Lock API, [request] will attempt
 * to lock the pointer to the supplied element. Platforms without pointer lock
 * support simply invoke [onRejected].
 */
expect object PointerLock {
    /**
     * Request pointer lock for the given render surface. The supplied [target]
     * is platform-specific (e.g., `HTMLCanvasElement` on JS). When pointer lock
     * is unavailable or rejected, the optional [onRejected] callback receives the
     * formatted error message.
     */
    fun request(target: Any?, onRejected: ((String) -> Unit)? = null)

    /**
     * Release pointer lock if the platform supports it.
     */
    fun exit()

    /**
     * Indicates whether pointer lock requests are supported on the current
     * platform/runtime.
     */
    fun isSupported(): Boolean
}
