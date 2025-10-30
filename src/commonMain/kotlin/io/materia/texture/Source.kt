package io.materia.texture

import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * Texture data source abstraction
 * Represents the actual image/data source for textures
 * Handles image loading, caching, and data management
 *
 * Based on Three.js Source class
 */
class Source(
    var data: Any? = null,
    var needsUpdate: Boolean = false,
    var uuid: String = generateUUID()
) {

    /**
     * Check if source data is ready
     */
    val isDataReady: Boolean
        get() = data != null

    /**
     * Convert source to JSON representation
     */
    fun toJSON(meta: Any? = null): Map<String, Any> {
        return mapOf(
            "uuid" to uuid,
            "url" to (getDataURL() ?: "")
        )
    }

    /**
     * Get data URL if available
     */
    private fun getDataURL(): String? {
        return when (val d = data) {
            is ImageElement -> d.src
            is CanvasElement -> d.toDataURL()
            is VideoElement -> d.src
            else -> null
        }
    }

    companion object {
        private val sourceId: AtomicInt = atomic(0)

        fun generateUUID(): String {
            return "source-${sourceId.getAndIncrement()}"
        }
    }
}

/**
 * Image loader for loading texture sources from URLs
 */
expect class ImageLoader {
    /**
     * Load image from URL
     */
    fun load(
        url: String,
        onLoad: ((ImageElement) -> Unit)? = null,
        onProgress: ((ProgressEvent) -> Unit)? = null,
        onError: ((ErrorEvent) -> Unit)? = null
    ): ImageElement

    /**
     * Set cross origin for CORS
     */
    fun setCrossOrigin(crossOrigin: String): ImageLoader

    /**
     * Set request header
     */
    fun setRequestHeader(headers: Map<String, String>): ImageLoader

    /**
     * Set base path for relative URLs
     */
    fun setPath(path: String): ImageLoader
}

/**
 * Platform-agnostic image element
 */
expect class ImageElement {
    var src: String
    var width: Int
    var height: Int
    val complete: Boolean
    val naturalWidth: Int
    val naturalHeight: Int

    fun addEventListener(event: String, callback: () -> Unit)
    fun removeEventListener(event: String, callback: () -> Unit)
}

/**
 * Platform-agnostic canvas element
 */
expect class CanvasElement {
    var width: Int
    var height: Int

    fun toDataURL(type: String = "image/png", quality: Float = 1.0f): String
    fun getContext(contextId: String): Any?
}

/**
 * Platform-agnostic video element
 */
expect class VideoElement {
    var src: String
    var width: Int
    var height: Int
    var currentTime: Float
    var duration: Float
    val paused: Boolean
    val ended: Boolean

    fun play()
    fun pause()
    fun addEventListener(event: String, callback: () -> Unit)
    fun removeEventListener(event: String, callback: () -> Unit)
}

/**
 * Progress event for loading
 */
data class ProgressEvent(
    val loaded: Long,
    val total: Long,
    val lengthComputable: Boolean = total > 0
) {
    val progress: Float
        get() = if (lengthComputable && total > 0) {
            loaded.toFloat() / total.toFloat()
        } else {
            0f
        }
}

/**
 * Error event for loading failures
 */
data class ErrorEvent(
    val message: String,
    val error: Throwable? = null
)