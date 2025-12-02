/**
 * KmpWindow - JVM Implementation
 *
 * Wraps GLFW window for Vulkan rendering on desktop.
 */
package io.materia.engine.window

import io.materia.renderer.RenderSurface
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JVM RenderSurface wrapping GLFW window handle.
 */
class JvmRenderSurface(
    private val windowHandle: Long
) : RenderSurface {
    private var _width: Int = 0
    private var _height: Int = 0

    init {
        updateSize()
    }

    fun updateSize() {
        MemoryStack.stackPush().use { stack ->
            val pWidth = stack.mallocInt(1)
            val pHeight = stack.mallocInt(1)
            glfwGetFramebufferSize(windowHandle, pWidth, pHeight)
            _width = pWidth[0]
            _height = pHeight[0]
        }
    }

    override val width: Int get() = _width
    override val height: Int get() = _height

    override fun getHandle(): Any = windowHandle
}

/**
 * JVM window implementation using GLFW.
 */
class JvmKmpWindow(
    private val handle: Long,
    private val config: WindowConfig
) : KmpWindow {
    private val _disposed = AtomicBoolean(false)
    private val listeners = mutableListOf<WindowEventListener>()
    private var _title = config.title
    private var cachedWidth = config.width
    private var cachedHeight = config.height

    override val isDisposed: Boolean get() = _disposed.get()

    override val width: Int
        get() {
            updateCachedSize()
            return cachedWidth
        }

    override val height: Int
        get() {
            updateCachedSize()
            return cachedHeight
        }

    override val pixelRatio: Float
        get() {
            MemoryStack.stackPush().use { stack ->
                val xScale = stack.mallocFloat(1)
                val yScale = stack.mallocFloat(1)
                glfwGetWindowContentScale(handle, xScale, yScale)
                return xScale[0]
            }
        }

    override val physicalWidth: Int get() = (width * pixelRatio).toInt()
    override val physicalHeight: Int get() = (height * pixelRatio).toInt()

    override var title: String
        get() = _title
        set(value) {
            _title = value
            glfwSetWindowTitle(handle, value)
        }

    override val shouldClose: Boolean
        get() = glfwWindowShouldClose(handle)

    override val isFocused: Boolean
        get() = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE

    override val isVisible: Boolean
        get() = glfwGetWindowAttrib(handle, GLFW_VISIBLE) == GLFW_TRUE

    init {
        setupCallbacks()
    }

    private fun setupCallbacks() {
        glfwSetFramebufferSizeCallback(handle) { _, width, height ->
            cachedWidth = width
            cachedHeight = height
            listeners.forEach { it.onResize(width, height) }
        }

        glfwSetWindowFocusCallback(handle) { _, focused ->
            listeners.forEach { it.onFocusChanged(focused) }
        }

        glfwSetWindowCloseCallback(handle) { _ ->
            val allowClose = listeners.all { it.onCloseRequested() }
            if (!allowClose) {
                glfwSetWindowShouldClose(handle, false)
            }
        }
    }

    private fun updateCachedSize() {
        MemoryStack.stackPush().use { stack ->
            val pWidth = stack.mallocInt(1)
            val pHeight = stack.mallocInt(1)
            glfwGetFramebufferSize(handle, pWidth, pHeight)
            cachedWidth = pWidth[0]
            cachedHeight = pHeight[0]
        }
    }

    override fun getNativeHandle(): Any = handle

    override fun createRenderSurface(): RenderSurface = JvmRenderSurface(handle)

    override fun pollEvents() {
        glfwPollEvents()
    }

    override fun requestClose() {
        glfwSetWindowShouldClose(handle, true)
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
        glfwDestroyWindow(handle)
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
    private var initialized = false

    actual fun create(config: WindowConfig): KmpWindow {
        ensureGlfwInitialized()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API) // No OpenGL - using Vulkan
        glfwWindowHint(GLFW_RESIZABLE, if (config.resizable) GLFW_TRUE else GLFW_FALSE)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Start hidden, show after setup
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, if (config.transparent) GLFW_TRUE else GLFW_FALSE)

        if (config.highDpi) {
            glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE)
        }

        val monitor = if (config.fullscreen) glfwGetPrimaryMonitor() else NULL
        val handle = glfwCreateWindow(
            config.width,
            config.height,
            config.title,
            monitor,
            NULL
        )

        if (handle == NULL) {
            throw RuntimeException("Failed to create GLFW window")
        }

        // Center window on screen
        if (!config.fullscreen) {
            val vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor())
            if (vidMode != null) {
                glfwSetWindowPos(
                    handle,
                    (vidMode.width() - config.width) / 2,
                    (vidMode.height() - config.height) / 2
                )
            }
        }

        glfwShowWindow(handle)

        return JvmKmpWindow(handle, config)
    }

    private fun ensureGlfwInitialized() {
        if (initialized) return

        GLFWErrorCallback.createPrint(System.err).set()

        if (!glfwInit()) {
            throw RuntimeException("Failed to initialize GLFW")
        }

        initialized = true

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            glfwTerminate()
            glfwSetErrorCallback(null)?.free()
        })
    }
}
