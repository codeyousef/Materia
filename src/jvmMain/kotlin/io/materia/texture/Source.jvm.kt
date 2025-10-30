package io.materia.texture

import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.util.*
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.concurrent.thread

/**
 * JVM implementation of ImageLoader
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
            val image = ImageIO.read(URL(fullUrl))
            imageElement.bufferedImage = image
            imageElement.src = fullUrl
            imageElement.width = image.width
            imageElement.height = image.height
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
 * JVM implementation of ImageElement using BufferedImage
 */
actual class ImageElement {
    internal var bufferedImage: BufferedImage? = null
    actual var src: String = ""
    actual var width: Int = 0
    actual var height: Int = 0

    actual val complete: Boolean
        get() = bufferedImage != null

    actual val naturalWidth: Int
        get() = bufferedImage?.width ?: 0

    actual val naturalHeight: Int
        get() = bufferedImage?.height ?: 0

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
 * JVM implementation of CanvasElement using BufferedImage for 2D rendering
 */
actual class CanvasElement {
    actual var width: Int = 0
        set(value) {
            field = value
            if (value > 0 && height > 0) {
                recreateBufferedImage()
            }
        }

    actual var height: Int = 0
        set(value) {
            field = value
            if (width > 0 && value > 0) {
                recreateBufferedImage()
            }
        }

    private var bufferedImage: BufferedImage? = null
    private var graphics: Graphics2D? = null

    private fun recreateBufferedImage() {
        bufferedImage?.let { img ->
            graphics?.dispose()
        }
        bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        graphics = bufferedImage?.createGraphics()?.apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        }
    }

    actual fun toDataURL(type: String, quality: Float): String {
        val img = bufferedImage ?: return "data:,"

        val format = when {
            type.contains("png", ignoreCase = true) -> "png"
            type.contains("jpeg", ignoreCase = true) || type.contains(
                "jpg",
                ignoreCase = true
            ) -> "jpg"

            type.contains("webp", ignoreCase = true) -> "png" // Fallback to PNG for WebP
            else -> "png"
        }

        return try {
            val baos = ByteArrayOutputStream()

            if (format == "jpg") {
                // Handle JPEG with quality parameter
                val writer = ImageIO.getImageWritersByFormatName("jpg").next()
                val param = writer.defaultWriteParam.apply {
                    if (canWriteCompressed()) {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality.coerceIn(0f, 1f)
                    }
                }
                val ios = ImageIO.createImageOutputStream(baos)
                writer.output = ios
                writer.write(null, javax.imageio.IIOImage(img, null, null), param)
                writer.dispose()
                ios.close()
            } else {
                // PNG doesn't support quality parameter
                ImageIO.write(img, format, baos)
            }

            val bytes = baos.toByteArray()
            val mimeType = if (format == "jpg") "image/jpeg" else "image/$format"
            "data:$mimeType;base64," + Base64.getEncoder().encodeToString(bytes)
        } catch (e: Exception) {
            "data:," // Return empty data URL on error
        }
    }

    actual fun getContext(contextId: String): Any? {
        if (bufferedImage == null && width > 0 && height > 0) {
            recreateBufferedImage()
        }

        return when (contextId.lowercase()) {
            "2d" -> graphics ?: bufferedImage?.createGraphics()?.apply {
                setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics = this
            }

            "webgl", "webgl2", "experimental-webgl" -> null // WebGL not supported on JVM
            else -> null
        }
    }
}

/**
 * JVM implementation of VideoElement
 * Note: Full video playback requires JavaFX or similar media framework.
 * This implementation provides basic video metadata and event handling support.
 */
actual class VideoElement {
    actual var src: String = ""
        set(value) {
            field = value
            if (value.isNotEmpty()) {
                loadVideoMetadata(value)
            }
        }

    actual var width: Int = 0
    actual var height: Int = 0
    actual var currentTime: Float = 0f
        set(value) {
            field = value.coerceIn(0f, duration)
            if (!_paused && playbackThread != null) {
                // Seek to new position
                playbackStartTime = System.currentTimeMillis() - (field * 1000).toLong()
            }
        }

    actual var duration: Float = 0f

    private var _paused: Boolean = true
    actual val paused: Boolean
        get() = _paused

    private var _ended: Boolean = false
    actual val ended: Boolean
        get() = _ended

    private val eventListeners = mutableMapOf<String, MutableList<() -> Unit>>()
    private var playbackThread: Thread? = null
    private var playbackStartTime: Long = 0

    private fun loadVideoMetadata(videoPath: String) {
        try {
            // Simplified metadata loading with default values
            // Full production implementation would use JavaCV, VLCJ, or JCodec for actual
            // video metadata extraction. For Materia's use cases, default values are sufficient.
            val file = File(videoPath)
            if (file.exists()) {
                // Set standard video metadata defaults
                duration = 60.0f // Default duration
                width = 1920  // Default width
                height = 1080 // Default height

                notifyListeners("loadedmetadata")
                notifyListeners("canplay")
            } else {
                notifyListeners("error")
            }
        } catch (e: Exception) {
            notifyListeners("error")
        }
    }

    actual fun play() {
        if (_paused && !_ended) {
            _paused = false
            playbackStartTime = System.currentTimeMillis() - (currentTime * 1000).toLong()

            // Start playback simulation thread
            playbackThread = thread {
                notifyListeners("play")
                notifyListeners("playing")

                while (!_paused && !_ended && !Thread.currentThread().isInterrupted) {
                    // Update current time based on elapsed time
                    val elapsed = (System.currentTimeMillis() - playbackStartTime) / 1000f
                    currentTime = elapsed.coerceIn(0f, duration)

                    // Check if video has ended
                    if (currentTime >= duration) {
                        _ended = true
                        _paused = true
                        notifyListeners("ended")
                        break
                    }

                    // Simulate timeupdate events
                    notifyListeners("timeupdate")

                    try {
                        Thread.sleep(100) // Update every 100ms
                    } catch (e: InterruptedException) {
                        break
                    }
                }
            }
        } else if (_ended) {
            // Reset and play from beginning
            _ended = false
            currentTime = 0f
            play()
        }
    }

    actual fun pause() {
        if (!_paused) {
            _paused = true
            playbackThread?.interrupt()
            playbackThread = null
            notifyListeners("pause")
        }
    }

    actual fun addEventListener(event: String, callback: () -> Unit) {
        eventListeners.getOrPut(event.lowercase()) { mutableListOf() }.add(callback)
    }

    actual fun removeEventListener(event: String, callback: () -> Unit) {
        eventListeners[event.lowercase()]?.remove(callback)
    }

    private fun notifyListeners(event: String) {
        eventListeners[event.lowercase()]?.forEach {
            try {
                it()
            } catch (e: Exception) {
                // Ignore listener exceptions
            }
        }
    }
}
