/**
 * KmpWindow - Platform Window Abstraction
 *
 * Provides a unified interface for windowing across all platforms.
 * This is the crucial abstraction that bridges platform-specific window
 * systems to Materia's rendering infrastructure.
 *
 * ## Platform Implementations
 *
 * - **JS/Wasm**: Wraps HTMLCanvasElement with WebGPU context
 * - **JVM**: Wraps GLFW window with Vulkan surface
 * - **Native**: Wraps platform-specific window handles
 */
package io.materia.engine.window

import io.materia.engine.core.Disposable

/**
 * Window event listener interface.
 */
interface WindowEventListener {
    /**
     * Called when window is resized.
     *
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun onResize(width: Int, height: Int) {}

    /**
     * Called when window focus changes.
     */
    fun onFocusChanged(focused: Boolean) {}

    /**
     * Called when window close is requested.
     *
     * @return true to allow close, false to prevent
     */
    fun onCloseRequested(): Boolean = true
}

/**
 * Platform-agnostic window abstraction.
 *
 * This interface provides the bridge between platform windowing systems
 * and Materia's GPU rendering infrastructure.
 *
 * ## Usage
 *
 * ```kotlin
 * // Create window (platform-specific factory)
 * val window = WindowFactory.create(WindowConfig(
 *     title = "My App",
 *     width = 1280,
 *     height = 720
 * ))
 *
 * // Initialize GPU surface
 * val surface = gpuSurfaceFactory.create(device, window.renderSurface)
 *
 * // Main loop
 * renderLoop.start { deltaTime ->
 *     if (!window.shouldClose) {
 *         window.pollEvents()
 *         renderer.render(scene, camera)
 *     }
 * }
 *
 * window.dispose()
 * ```
 *
 * ## Platform Specifics
 *
 * **JS (Browser):**
 * - Creates/wraps an HTMLCanvasElement
 * - Uses ResizeObserver for automatic resize handling
 * - WebGPU context obtained via canvas.getContext("webgpu")
 *
 * **JVM (Desktop):**
 * - Creates a GLFW window
 * - Provides raw window handle for Vulkan surface creation
 * - Supports fullscreen, borderless, and windowed modes
 *
 * **Native (iOS/macOS):**
 * - Wraps platform-specific view/layer
 * - Provides Metal/Vulkan compatible surface
 */
expect interface KmpWindow : Disposable {
    /**
     * Current window width in pixels.
     */
    val width: Int

    /**
     * Current window height in pixels.
     */
    val height: Int

    /**
     * Device pixel ratio (for HiDPI displays).
     *
     * Physical pixels = logical pixels * pixelRatio
     */
    val pixelRatio: Float

    /**
     * Physical width in pixels (width * pixelRatio).
     */
    val physicalWidth: Int

    /**
     * Physical height in pixels (height * pixelRatio).
     */
    val physicalHeight: Int

    /**
     * Window title (if applicable).
     */
    var title: String

    /**
     * Whether the window close has been requested.
     *
     * On JS, this is always false (browser handles closing).
     * On desktop, this reflects the window's close button state.
     */
    val shouldClose: Boolean

    /**
     * Whether the window currently has focus.
     */
    val isFocused: Boolean

    /**
     * Whether the window is currently visible.
     */
    val isVisible: Boolean

    /**
     * Gets the platform-specific window handle.
     *
     * Returns:
     * - JS: HTMLCanvasElement
     * - JVM: GLFW window handle (Long)
     * - Native: Platform-specific pointer
     */
    fun getNativeHandle(): Any

    /**
     * Creates a RenderSurface for GPU rendering.
     *
     * This is the primary method for connecting the window to the GPU.
     */
    fun createRenderSurface(): io.materia.renderer.RenderSurface

    /**
     * Polls for window events (input, resize, etc.).
     *
     * On JS, this is typically a no-op (events are async).
     * On desktop, this processes the window event queue.
     */
    fun pollEvents()

    /**
     * Requests the window to close.
     */
    fun requestClose()

    /**
     * Adds an event listener.
     */
    fun addEventListener(listener: WindowEventListener)

    /**
     * Removes an event listener.
     */
    fun removeEventListener(listener: WindowEventListener)
}

/**
 * Configuration for window creation.
 */
data class WindowConfig(
    /**
     * Window title.
     */
    val title: String = "Materia",

    /**
     * Initial width in logical pixels.
     */
    val width: Int = 1280,

    /**
     * Initial height in logical pixels.
     */
    val height: Int = 720,

    /**
     * Whether the window should be resizable.
     */
    val resizable: Boolean = true,

    /**
     * Whether to enable fullscreen mode.
     */
    val fullscreen: Boolean = false,

    /**
     * Whether to use vsync.
     */
    val vsync: Boolean = true,

    /**
     * Target canvas element ID (JS only).
     *
     * If null, a new canvas is created.
     */
    val canvasId: String? = null,

    /**
     * Whether to request high-DPI rendering.
     */
    val highDpi: Boolean = true,

    /**
     * Transparent window background (desktop only).
     */
    val transparent: Boolean = false
)

/**
 * Factory for creating platform-specific windows.
 */
expect object WindowFactory {
    /**
     * Creates a new window with the given configuration.
     */
    fun create(config: WindowConfig = WindowConfig()): KmpWindow
}
