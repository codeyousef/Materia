/**
 * KmpWindow - Android Implementation
 *
 * Wraps Android SurfaceView/TextureView for Vulkan rendering.
 * Android windows are typically managed by the Activity lifecycle,
 * so this provides a bridge between the Android view system and Materia's rendering.
 */
package io.materia.engine.window

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.materia.renderer.RenderSurface
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android RenderSurface wrapping a native Surface.
 */
class AndroidRenderSurface(
    private val surface: Surface,
    private var _width: Int,
    private var _height: Int
) : RenderSurface {
    override val width: Int get() = _width
    override val height: Int get() = _height

    override fun getHandle(): Any = surface

    fun updateSize(width: Int, height: Int) {
        _width = width
        _height = height
    }
}

/**
 * Android window implementation using SurfaceView.
 */
class AndroidKmpWindow(
    private val surfaceView: SurfaceView
) : KmpWindow {
    private val _disposed = AtomicBoolean(false)
    private val listeners = mutableListOf<WindowEventListener>()
    private var _title = "Materia"
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var currentSurface: AndroidRenderSurface? = null

    override val isDisposed: Boolean get() = _disposed.get()

    override val width: Int get() = cachedWidth.takeIf { it > 0 } ?: surfaceView.width
    override val height: Int get() = cachedHeight.takeIf { it > 0 } ?: surfaceView.height

    override val pixelRatio: Float
        get() = surfaceView.context.resources.displayMetrics.density

    override val physicalWidth: Int get() = width
    override val physicalHeight: Int get() = height

    override var title: String
        get() = _title
        set(value) { _title = value }

    override val shouldClose: Boolean get() = false // Android manages lifecycle differently

    override val isFocused: Boolean get() = surfaceView.isFocused

    override val isVisible: Boolean get() = surfaceView.isShown

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Surface ready for rendering
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                cachedWidth = width
                cachedHeight = height
                currentSurface?.updateSize(width, height)
                listeners.forEach { it.onResize(width, height) }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Surface no longer available
            }
        })
    }

    override fun getNativeHandle(): Any = surfaceView.holder.surface

    override fun createRenderSurface(): RenderSurface {
        val surface = surfaceView.holder.surface
        val renderSurface = AndroidRenderSurface(
            surface,
            surfaceView.width.takeIf { it > 0 } ?: 1,
            surfaceView.height.takeIf { it > 0 } ?: 1
        )
        currentSurface = renderSurface
        return renderSurface
    }

    override fun pollEvents() {
        // Android handles events through the main looper - no-op here
    }

    override fun requestClose() {
        // Android windows are closed by finishing the Activity
        // This is handled at the Activity level
    }

    override fun addEventListener(listener: WindowEventListener) {
        listeners.add(listener)
    }

    override fun removeEventListener(listener: WindowEventListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        if (!_disposed.compareAndSet(false, true)) return
        listeners.clear()
        currentSurface = null
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
    private var defaultSurfaceView: SurfaceView? = null

    /**
     * Set the SurfaceView to use for window creation.
     * This must be called from an Activity before creating windows.
     */
    fun setSurfaceView(surfaceView: SurfaceView) {
        defaultSurfaceView = surfaceView
    }

    actual fun create(config: WindowConfig): KmpWindow {
        val surfaceView = defaultSurfaceView
            ?: throw IllegalStateException(
                "WindowFactory.setSurfaceView() must be called before creating windows on Android"
            )
        return AndroidKmpWindow(surfaceView)
    }
}
