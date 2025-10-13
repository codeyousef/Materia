package io.kreekt.controls

import kotlinx.browser.document
import org.w3c.dom.Element
import io.kreekt.util.console

actual object PointerLock {

    actual fun request(target: Any?, onRejected: ((String) -> Unit)?) {
        val element = when (target) {
            is Element -> target
            else -> target.asDynamic()
        }

        if (element == null) {
            val message = "Pointer lock target is null."
            console.warn(message)
            onRejected?.invoke(message)
            return
        }

        val requestFn = element.requestPointerLock
            ?: element.mozRequestPointerLock
            ?: element.webkitRequestPointerLock

        if (requestFn == null) {
            val message = "Pointer lock API is not available on this element."
            console.warn(message)
            onRejected?.invoke(message)
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
            val result = requestFn.call(element)
            val dynamicResult: dynamic = result
            if (dynamicResult != null) {
                val catchFn = dynamicResult.catch
                if (catchFn != null) {
                    dynamicResult.catch { error: dynamic ->
                        val message = "Pointer lock request rejected: ${toMessage(error)}"
                        console.warn(message)
                        onRejected?.invoke(message)
                        null
                    }
                }
            }
        } catch (error: dynamic) {
            val message = "Pointer lock request failed: ${toMessage(error)}"
            console.warn(message)
            onRejected?.invoke(message)
        }
    }

    actual fun exit() {
        val exitFn = document.asDynamic().exitPointerLock
            ?: document.asDynamic().mozExitPointerLock
            ?: document.asDynamic().webkitExitPointerLock

        exitFn?.call(document)
    }

    actual fun isSupported(): Boolean {
        val doc = document.asDynamic()
        return doc.exitPointerLock != null ||
            doc.mozExitPointerLock != null ||
            doc.webkitExitPointerLock != null
    }
}
