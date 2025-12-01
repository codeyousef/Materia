package io.materia.loader

import io.materia.util.Base64Compat
import kotlinx.browser.document
import org.khronos.webgl.Uint8ClampedArray
import org.w3c.dom.CanvasRenderingContext2D
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal actual object PlatformImageDecoder {
    actual suspend fun decode(bytes: ByteArray): DecodedImage = suspendCoroutine { continuation ->
        val image = js("new Image()")
        image.asDynamic().crossOrigin = "anonymous"

        image.onload = {
            val canvas = document.createElement("canvas") as org.w3c.dom.HTMLCanvasElement
            canvas.width = (image.asDynamic().width as Number).toInt()
            canvas.height = (image.asDynamic().height as Number).toInt()
            val context = canvas.getContext("2d") as? CanvasRenderingContext2D
            if (context != null) {
                context.asDynamic().drawImage(image, 0, 0)
                val imageData = context.asDynamic().getImageData(0, 0, canvas.width, canvas.height)
                val data = imageData.data as Uint8ClampedArray
                val byteArray =
                    ByteArray(data.length) { index -> (data.asDynamic()[index] as Int).toByte() }
                continuation.resume(DecodedImage(canvas.width, canvas.height, byteArray))
            } else {
                continuation.resumeWithException(IllegalStateException("2D canvas context unavailable"))
            }
        }

        image.onerror = { _: dynamic, _: dynamic, _: dynamic, _: dynamic, error: dynamic ->
            continuation.resumeWithException(IllegalArgumentException("Failed to decode image: ${'$'}error"))
        }

        val base64 = Base64Compat.encode(bytes)
        image.src = "data:application/octet-stream;base64,${'$'}base64"
    }
}
