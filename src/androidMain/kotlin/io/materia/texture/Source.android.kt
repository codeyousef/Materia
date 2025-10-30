package io.materia.texture

import java.util.concurrent.ConcurrentHashMap

actual class ImageLoader {
    private var basePath: String = ""
    private var crossOrigin: String = ""
    private var headers: Map<String, String> = emptyMap()

    actual fun load(
        url: String,
        onLoad: ((ImageElement) -> Unit)?,
        onProgress: ((ProgressEvent) -> Unit)?,
        onError: ((ErrorEvent) -> Unit)?
    ): ImageElement {
        val element = ImageElement().apply {
            src = if (basePath.isNotEmpty() && !url.startsWith("http")) "$basePath$url" else url
            updateDimensions(0, 0)
            markComplete()
        }
        onProgress?.invoke(ProgressEvent(loaded = 0, total = 0, lengthComputable = false))
        onLoad?.invoke(element)
        return element
    }

    actual fun setCrossOrigin(crossOrigin: String): ImageLoader {
        this.crossOrigin = crossOrigin
        return this
    }

    actual fun setRequestHeader(headers: Map<String, String>): ImageLoader {
        this.headers = headers
        return this
    }

    actual fun setPath(path: String): ImageLoader {
        this.basePath = path
        return this
    }
}

actual class ImageElement {
    actual var src: String = ""
    actual var width: Int = 0
    actual var height: Int = 0

    private var _complete: Boolean = false
    actual val complete: Boolean
        get() = _complete

    private var _naturalWidth: Int = 0
    actual val naturalWidth: Int
        get() = _naturalWidth

    private var _naturalHeight: Int = 0
    actual val naturalHeight: Int
        get() = _naturalHeight

    private val listeners = ConcurrentHashMap<String, MutableList<() -> Unit>>()

    actual fun addEventListener(event: String, callback: () -> Unit) {
        listeners.computeIfAbsent(event) { mutableListOf() }.add(callback)
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        listeners[event]?.remove(callback)
    }

    fun updateDimensions(width: Int, height: Int) {
        this.width = width
        this.height = height
        this._naturalWidth = width
        this._naturalHeight = height
    }

    fun markComplete() {
        _complete = true
        dispatch("load")
    }

    fun dispatch(event: String) {
        listeners[event]?.forEach { it.invoke() }
    }
}

actual class CanvasElement {
    actual var width: Int = 0
    actual var height: Int = 0

    actual fun toDataURL(type: String, quality: Float): String = "data:$type;base64,"

    actual fun getContext(contextId: String): Any? = null
}

actual class VideoElement {
    actual var src: String = ""
    actual var width: Int = 0
    actual var height: Int = 0
    actual var currentTime: Float = 0f
    actual var duration: Float = 0f
    actual val paused: Boolean = true
    actual val ended: Boolean = false

    private val listeners = ConcurrentHashMap<String, MutableList<() -> Unit>>()

    actual fun play() {}

    actual fun pause() {}

    actual fun addEventListener(event: String, callback: () -> Unit) {
        listeners.computeIfAbsent(event) { mutableListOf() }.add(callback)
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        listeners[event]?.remove(callback)
    }
}
