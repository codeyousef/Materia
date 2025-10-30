package io.materia.texture

import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLVideoElement
import kotlin.js.Promise

/**
 * JS implementation of ImageLoader using native browser APIs
 */
actual class ImageLoader {
    private var crossOrigin: String = ""
    private var requestHeaders = mutableMapOf<String, String>()
    private var basePath: String = ""

    actual fun load(
        url: String,
        onLoad: ((ImageElement) -> Unit)?,
        onProgress: ((ProgressEvent) -> Unit)?,
        onError: ((ErrorEvent) -> Unit)?
    ): ImageElement {
        val fullUrl = if (basePath.isNotEmpty()) "$basePath/$url" else url
        val img = js("new Image()") as? HTMLImageElement
            ?: throw IllegalStateException("Failed to create HTMLImageElement")
        val element = ImageElement(img)

        if (crossOrigin.isNotEmpty()) {
            img.crossOrigin = crossOrigin
        }

        img.onload = {
            onLoad?.invoke(element)
        }

        img.onerror = { event, _, _, _, _ ->
            onError?.invoke(ErrorEvent("Failed to load image: $fullUrl"))
        }

        img.src = fullUrl
        return element
    }

    actual fun setCrossOrigin(crossOrigin: String): ImageLoader {
        this.crossOrigin = crossOrigin
        return this
    }

    actual fun setRequestHeader(headers: Map<String, String>): ImageLoader {
        this.requestHeaders.putAll(headers)
        return this
    }

    actual fun setPath(path: String): ImageLoader {
        this.basePath = path
        return this
    }
}

/**
 * JS implementation of ImageElement using HTMLImageElement
 */
actual class ImageElement internal constructor(internal val element: HTMLImageElement) {
    constructor() : this(
        js("new Image()") as? HTMLImageElement
            ?: throw IllegalStateException("Failed to create HTMLImageElement")
    )

    actual var src: String
        get() = element.src
        set(value) {
            element.src = value
        }

    actual var width: Int
        get() = element.width
        set(value) {
            element.width = value
        }

    actual var height: Int
        get() = element.height
        set(value) {
            element.height = value
        }

    actual val complete: Boolean
        get() = element.complete

    actual val naturalWidth: Int
        get() = element.naturalWidth

    actual val naturalHeight: Int
        get() = element.naturalHeight

    actual fun addEventListener(event: String, callback: () -> Unit) {
        element.addEventListener(event, { callback() })
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        element.removeEventListener(event, { callback() })
    }
}

/**
 * JS implementation of CanvasElement using HTMLCanvasElement
 */
actual class CanvasElement internal constructor(internal val element: HTMLCanvasElement) {
    constructor() : this(
        js("document.createElement('canvas')") as? HTMLCanvasElement
            ?: throw IllegalStateException("Failed to create HTMLCanvasElement")
    )

    actual var width: Int
        get() = element.width
        set(value) {
            element.width = value
        }

    actual var height: Int
        get() = element.height
        set(value) {
            element.height = value
        }

    actual fun toDataURL(type: String, quality: Float): String {
        return element.toDataURL(type, quality.toDouble())
    }

    actual fun getContext(contextId: String): Any? {
        return element.getContext(contextId)
    }
}

/**
 * JS implementation of VideoElement using HTMLVideoElement
 */
actual class VideoElement internal constructor(internal val element: HTMLVideoElement) {
    constructor() : this(
        js("document.createElement('video')") as? HTMLVideoElement
            ?: throw IllegalStateException("Failed to create HTMLVideoElement")
    )

    actual var src: String
        get() = element.src
        set(value) {
            element.src = value
        }

    actual var width: Int
        get() = element.width
        set(value) {
            element.width = value
        }

    actual var height: Int
        get() = element.height
        set(value) {
            element.height = value
        }

    actual var currentTime: Float
        get() = element.currentTime.toFloat()
        set(value) {
            element.currentTime = value.toDouble()
        }

    actual var duration: Float
        get() = element.duration.toFloat()
        set(value) { /* readonly in HTML */ }

    actual val paused: Boolean
        get() = element.paused

    actual val ended: Boolean
        get() = element.ended

    actual fun play() {
        element.play()
    }

    actual fun pause() {
        element.pause()
    }

    actual fun addEventListener(event: String, callback: () -> Unit) {
        element.addEventListener(event, { callback() })
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        element.removeEventListener(event, { callback() })
    }
}
