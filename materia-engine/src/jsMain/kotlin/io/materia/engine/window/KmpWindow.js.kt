/**
 * KmpWindow - JavaScript/WebAssembly Implementation
 *
 * Wraps HTMLCanvasElement for WebGPU rendering in browsers.
 */
package io.materia.engine.window

import io.materia.renderer.RenderSurface
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event

/**
 * JavaScript RenderSurface wrapping HTMLCanvasElement.
 */
class JsRenderSurface(
    private val canvas: HTMLCanvasElement
) : RenderSurface {
    override val width: Int get() = canvas.width
    override val height: Int get() = canvas.height

    override fun getHandle(): Any = canvas
}

/**
 * JavaScript window implementation using HTMLCanvasElement.
 */
class JsKmpWindow(
    private val canvas: HTMLCanvasElement,
    private val config: WindowConfig
) : KmpWindow {
    private var _disposed = false
    private val listeners = mutableListOf<WindowEventListener>()
    private var resizeObserver: dynamic = null
    private var _title = config.title
    private var _focused = true
    private var _visible = true

    override val isDisposed: Boolean get() = _disposed
    override val width: Int get() = canvas.width
    override val height: Int get() = canvas.height
    override val pixelRatio: Float get() = window.devicePixelRatio.toFloat()
    override val physicalWidth: Int get() = (width * pixelRatio).toInt()
    override val physicalHeight: Int get() = (height * pixelRatio).toInt()
    override val shouldClose: Boolean = false // Browser handles this
    override val isFocused: Boolean get() = _focused
    override val isVisible: Boolean get() = _visible

    override var title: String
        get() = _title
        set(value) {
            _title = value
            document.title = value
        }

    init {
        // Set up resize observer
        setupResizeObserver()

        // Set up visibility change listener
        document.addEventListener("visibilitychange", { _: Event ->
            _visible = document.asDynamic().visibilityState == "visible"
        })

        // Set up focus listeners
        window.addEventListener("focus", { _: Event -> _focused = true })
        window.addEventListener("blur", { _: Event -> _focused = false })

        // Apply initial config
        document.title = config.title

        if (config.highDpi) {
            updateCanvasSize()
        }
    }

    private fun setupResizeObserver() {
        val observer = js("new ResizeObserver")
        resizeObserver = observer({ entries: dynamic ->
            val entry = entries[0]
            val contentRect = entry.contentRect
            val newWidth = (contentRect.width * pixelRatio).toInt()
            val newHeight = (contentRect.height * pixelRatio).toInt()

            if (newWidth != canvas.width || newHeight != canvas.height) {
                canvas.width = newWidth
                canvas.height = newHeight
                notifyResize(newWidth, newHeight)
            }
        })
        resizeObserver.observe(canvas)
    }

    private fun updateCanvasSize() {
        val displayWidth = (canvas.clientWidth * pixelRatio).toInt()
        val displayHeight = (canvas.clientHeight * pixelRatio).toInt()

        if (canvas.width != displayWidth || canvas.height != displayHeight) {
            canvas.width = displayWidth
            canvas.height = displayHeight
        }
    }

    private fun notifyResize(width: Int, height: Int) {
        listeners.forEach { it.onResize(width, height) }
    }

    override fun getNativeHandle(): Any = canvas

    override fun createRenderSurface(): RenderSurface = JsRenderSurface(canvas)

    override fun pollEvents() {
        // No-op on JS - events are handled asynchronously
    }

    override fun requestClose() {
        // Can't close the browser tab programmatically
        console.warn("requestClose() is not supported in browser environment")
    }

    override fun addEventListener(listener: WindowEventListener) {
        listeners.add(listener)
    }

    override fun removeEventListener(listener: WindowEventListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        if (_disposed) return
        _disposed = true

        resizeObserver?.disconnect()
        resizeObserver = null
        listeners.clear()
    }
}

actual interface KmpWindow : io.materia.engine.core.Disposable {
    actual val width: Int
    actual val height: Int
    actual val pixelRatio: Float
    actual val physicalWidth: Int
    actual val physicalHeight: Int
    actual var title: String
    actual val shouldClose: Boolean
    actual val isFocused: Boolean
    actual val isVisible: Boolean
    actual fun getNativeHandle(): Any
    actual fun createRenderSurface(): RenderSurface
    actual fun pollEvents()
    actual fun requestClose()
    actual fun addEventListener(listener: WindowEventListener)
    actual fun removeEventListener(listener: WindowEventListener)
}

actual object WindowFactory {
    actual fun create(config: WindowConfig): KmpWindow {
        val canvas = if (config.canvasId != null) {
            document.getElementById(config.canvasId) as? HTMLCanvasElement
                ?: createCanvas(config)
        } else {
            createCanvas(config)
        }

        return JsKmpWindow(canvas, config)
    }

    private fun createCanvas(config: WindowConfig): HTMLCanvasElement {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.id = config.canvasId ?: "materia-canvas"
        canvas.style.width = "${config.width}px"
        canvas.style.height = "${config.height}px"

        val pixelRatio = if (config.highDpi) window.devicePixelRatio else 1.0
        canvas.width = (config.width * pixelRatio).toInt()
        canvas.height = (config.height * pixelRatio).toInt()

        document.body?.appendChild(canvas)
        return canvas
    }
}
