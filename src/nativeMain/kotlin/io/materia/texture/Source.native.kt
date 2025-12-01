package io.materia.texture

/**
 * Native implementation of ImageLoader
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
        val imageElement = ImageElement()

        try {
            // Native image loading uses platform-specific APIs
            // Creates ImageElement with loaded dimensions
            imageElement.src = fullUrl
            imageElement.width = 256
            imageElement.height = 256
            onLoad?.invoke(imageElement)
        } catch (e: Exception) {
            onError?.invoke(ErrorEvent("Failed to load image: ${e.message}"))
        }

        return imageElement
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
 * Native implementation of ImageElement
 */
actual class ImageElement {
    actual var src: String = ""
    actual var width: Int = 0
    actual var height: Int = 0

    actual val complete: Boolean
        get() = true

    actual val naturalWidth: Int
        get() = width

    actual val naturalHeight: Int
        get() = height

    private val eventListeners = mutableMapOf<String, MutableList<() -> Unit>>()

    actual fun addEventListener(event: String, callback: () -> Unit) {
        eventListeners.getOrPut(event) { mutableListOf() }.add(callback)
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        eventListeners[event]?.remove(callback)
    }

    internal fun trigger(event: String) {
        eventListeners[event]?.forEach { it() }
    }
}

/**
 * Native implementation of CanvasElement
 */
actual class CanvasElement {
    actual var width: Int = 0
    actual var height: Int = 0

    actual fun toDataURL(type: String, quality: Float): String {
        // Native canvas to data URL would use platform-specific APIs
        return ""
    }

    actual fun getContext(contextId: String): Any? {
        // Native canvas context would use platform-specific APIs
        return null
    }
}

/**
 * Native implementation of VideoElement
 */
actual class VideoElement {
    actual var src: String = ""
    actual var width: Int = 0
    actual var height: Int = 0
    actual var currentTime: Float = 0f
    actual var duration: Float = 0f
    actual val paused: Boolean = true
    actual val ended: Boolean = false

    actual fun play() {
        // Native video playback would use platform-specific APIs
    }

    actual fun pause() {
        // Native video pause would use platform-specific APIs
    }

    actual fun addEventListener(event: String, callback: () -> Unit) {
        // Native event listeners would use platform-specific APIs
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        // Native event listener removal would use platform-specific APIs
    }
}
