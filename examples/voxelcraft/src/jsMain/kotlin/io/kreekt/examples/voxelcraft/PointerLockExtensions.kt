package io.kreekt.examples.voxelcraft

import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement

/**
 * Request pointer lock with vendor fallbacks and guard against rejected promises.
 *
 * Browsers may reject pointer lock requests asynchronously (e.g., if the user
 * exits pointer lock before the request resolves). We capture those rejections
 * to avoid unhandled promise errors that would otherwise surface in the console.
 */
@Suppress("UnsafeCastFromDynamic", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
fun HTMLCanvasElement.requestPointerLockSafe() {
    val dynamicCanvas = this.asDynamic()
    val requestFn = dynamicCanvas.requestPointerLock
        ?: dynamicCanvas.mozRequestPointerLock
        ?: dynamicCanvas.webkitRequestPointerLock

    if (requestFn == null) {
        logPointerLockWarning("Pointer lock API is not available on this browser.")
        return
    }

    val toMessage = { error: dynamic ->
        try {
            error?.message?.toString()
        } catch (_: Throwable) {
            null
        } ?: error?.toString() ?: "Unknown pointer lock error"
    }

    try {
        val result = requestFn.call(this)
        val dynamicResult: dynamic = result
        if (dynamicResult != null) {
            val catchFn = dynamicResult.catch
            if (catchFn != null) {
                dynamicResult.catch { error: dynamic ->
                    val message = toMessage(error)
                    logPointerLockWarning("Pointer lock request rejected: $message")
                    null
                }
            }
        }
    } catch (error: dynamic) {
        val message = toMessage(error)
        logPointerLockWarning("Pointer lock request failed: $message")
    }
}

private fun logPointerLockWarning(message: String) {
    val console = window.asDynamic().console ?: return
    console.warn(message)
}
