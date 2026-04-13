@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.materia.engine.window

import cnames.structs.GLFWwindow
import io.materia.renderer.AppleSurfaceHandle
import io.materia.renderer.RenderSurface
import io.materia.renderer.SurfaceFactory
import glfw.GLFW_CLIENT_API
import glfw.GLFW_FALSE
import glfw.GLFW_FOCUSED
import glfw.GLFW_NO_API
import glfw.GLFW_RESIZABLE
import glfw.GLFW_TRUE
import glfw.GLFW_VISIBLE
import glfw.glfwCreateWindow
import glfw.glfwDefaultWindowHints
import glfw.glfwDestroyWindow
import glfw.glfwGetFramebufferSize
import glfw.glfwGetPrimaryMonitor
import glfw.glfwGetWindowAttrib
import glfw.glfwGetWindowSize
import glfw.glfwInit
import glfw.glfwPollEvents
import glfw.glfwSetWindowShouldClose
import glfw.glfwSetWindowTitle
import glfw.glfwShowWindow
import glfw.glfwWindowHint
import glfw.glfwWindowShouldClose
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value

private class MacosKmpWindow(
    private val handle: CPointer<GLFWwindow>,
    config: WindowConfig
) : KmpWindow {
    private val listeners = mutableListOf<WindowEventListener>()
    private var disposed = false
    private var titleValue = config.title
    private var lastFramebufferWidth = framebufferSize().first
    private var lastFramebufferHeight = framebufferSize().second
    private var lastFocused = isFocused
    private var closeApproved = false

    override val isDisposed: Boolean
        get() = disposed

    override val width: Int
        get() = windowSize().first

    override val height: Int
        get() = windowSize().second

    override val pixelRatio: Float
        get() {
            val logicalWidth = width.coerceAtLeast(1)
            return physicalWidth.toFloat() / logicalWidth.toFloat()
        }

    override val physicalWidth: Int
        get() = framebufferSize().first.coerceAtLeast(1)

    override val physicalHeight: Int
        get() = framebufferSize().second.coerceAtLeast(1)

    override var title: String
        get() = titleValue
        set(value) {
            titleValue = value
            glfwSetWindowTitle(handle, value)
        }

    override val shouldClose: Boolean
        get() = glfwWindowShouldClose(handle) == GLFW_TRUE

    override val isFocused: Boolean
        get() = glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE

    override val isVisible: Boolean
        get() = glfwGetWindowAttrib(handle, GLFW_VISIBLE) == GLFW_TRUE

    override fun getNativeHandle(): Any = handle

    override fun createRenderSurface(): RenderSurface {
        return SurfaceFactory.create(
            AppleSurfaceHandle(
                handle = handle,
                width = physicalWidth,
                height = physicalHeight,
                label = titleValue
            )
        )
    }

    override fun pollEvents() {
        glfwPollEvents()

        val (framebufferWidth, framebufferHeight) = framebufferSize()
        if (framebufferWidth != lastFramebufferWidth || framebufferHeight != lastFramebufferHeight) {
            lastFramebufferWidth = framebufferWidth
            lastFramebufferHeight = framebufferHeight
            listeners.forEach { it.onResize(framebufferWidth, framebufferHeight) }
        }

        val focused = isFocused
        if (focused != lastFocused) {
            lastFocused = focused
            listeners.forEach { it.onFocusChanged(focused) }
        }

        if (!closeApproved && glfwWindowShouldClose(handle) == GLFW_TRUE) {
            val allowClose = listeners.all { it.onCloseRequested() }
            if (!allowClose) {
                glfwSetWindowShouldClose(handle, GLFW_FALSE)
            } else {
                closeApproved = true
            }
        }
    }

    override fun requestClose() {
        glfwSetWindowShouldClose(handle, GLFW_TRUE)
    }

    override fun addEventListener(listener: WindowEventListener) {
        listeners += listener
    }

    override fun removeEventListener(listener: WindowEventListener) {
        listeners -= listener
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        listeners.clear()
        glfwDestroyWindow(handle)
    }

    private fun windowSize(): Pair<Int, Int> = memScoped {
        val width = alloc<IntVar>()
        val height = alloc<IntVar>()
        glfwGetWindowSize(handle, width.ptr, height.ptr)
        width.value to height.value
    }

    private fun framebufferSize(): Pair<Int, Int> = memScoped {
        val width = alloc<IntVar>()
        val height = alloc<IntVar>()
        glfwGetFramebufferSize(handle, width.ptr, height.ptr)
        width.value to height.value
    }
}

actual object WindowFactory {
    private var initialized = false

    actual fun create(config: WindowConfig): KmpWindow {
        ensureGlfwInitialized()

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, if (config.resizable) GLFW_TRUE else GLFW_FALSE)
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)

        val handle = glfwCreateWindow(
            config.width.coerceAtLeast(1),
            config.height.coerceAtLeast(1),
            config.title,
            if (config.fullscreen) glfwGetPrimaryMonitor() else null,
            null
        ) ?: error("Failed to create GLFW window for macOS")

        glfwShowWindow(handle)
        return MacosKmpWindow(handle, config)
    }

    private fun ensureGlfwInitialized() {
        if (initialized) return
        if (glfwInit() == GLFW_FALSE) {
            error("Failed to initialize GLFW for macOS window creation")
        }
        initialized = true
    }
}