/**
 * RenderLoop - Platform-Agnostic Animation Loop
 *
 * Provides a unified render loop abstraction that works correctly on:
 * - JS/Wasm: Uses requestAnimationFrame to avoid blocking the main thread
 * - JVM/Native: Uses a blocking loop with optional vsync
 *
 * This is critical because a standard `while(true)` loop will freeze the browser.
 */
package io.materia.engine.core

/**
 * Platform-specific render loop abstraction.
 *
 * ## Usage
 *
 * ```kotlin
 * val loop = RenderLoop()
 *
 * loop.start { deltaTime ->
 *     // Update scene
 *     scene.update(deltaTime)
 *
 *     // Render
 *     renderer.render(scene, camera)
 * }
 *
 * // Later...
 * loop.stop()
 * ```
 *
 * ## Platform Behavior
 *
 * **JS/Wasm (Browser):**
 * - Uses `window.requestAnimationFrame()` for browser-synchronized rendering
 * - Automatically pauses when tab is not visible
 * - Respects browser's refresh rate (typically 60Hz or higher)
 *
 * **JVM/Native (Desktop):**
 * - Uses a blocking loop on a dedicated thread
 * - Optionally integrates with GLFW's vsync for smooth frame pacing
 * - Can be configured for fixed or variable timestep
 *
 * ## Thread Safety
 *
 * The callback is always invoked on the main/render thread appropriate for
 * the platform. On JVM, this may be a dedicated render thread.
 */
expect class RenderLoop() {
    /**
     * Whether the render loop is currently running.
     */
    val isRunning: Boolean

    /**
     * Target frames per second (0 = uncapped/vsync).
     *
     * - On JS, this is advisory; the browser controls actual frame rate
     * - On JVM/Native, this controls the loop timing when vsync is disabled
     */
    var targetFps: Int

    /**
     * Starts the render loop, invoking the callback each frame.
     *
     * @param callback Function called each frame with delta time in seconds
     *
     * The callback receives:
     * - `deltaTime`: Time since last frame in seconds (typically ~0.016 for 60fps)
     *
     * On JS, this immediately returns and the loop runs asynchronously.
     * On JVM/Native with blocking mode, this blocks the current thread.
     */
    fun start(callback: (deltaTime: Float) -> Unit)

    /**
     * Stops the render loop.
     *
     * After calling stop(), [isRunning] will be false and the callback
     * will no longer be invoked.
     *
     * Safe to call multiple times or when already stopped.
     */
    fun stop()
}

/**
 * Configuration for render loop behavior.
 */
data class RenderLoopConfig(
    /**
     * Target frames per second. 0 means uncapped (use vsync or run as fast as possible).
     */
    val targetFps: Int = 0,

    /**
     * Whether to use fixed timestep updates.
     *
     * When true, the callback receives a fixed deltaTime regardless of
     * actual frame time. Useful for physics simulations.
     */
    val fixedTimestep: Boolean = false,

    /**
     * Fixed timestep value in seconds (used when [fixedTimestep] is true).
     */
    val fixedDeltaTime: Float = 1f / 60f,

    /**
     * Maximum number of fixed-step updates per frame.
     *
     * Prevents "spiral of death" when updates take longer than the timestep.
     */
    val maxUpdatesPerFrame: Int = 5
)

/**
 * Advanced render loop with fixed and variable timestep support.
 *
 * Provides separate callbacks for fixed-rate updates (physics) and
 * variable-rate rendering.
 */
expect class AdvancedRenderLoop(config: RenderLoopConfig = RenderLoopConfig()) {
    val isRunning: Boolean
    val config: RenderLoopConfig

    /**
     * Starts the loop with separate update and render callbacks.
     *
     * @param update Called at fixed intervals for physics/logic (when fixedTimestep = true)
     * @param render Called each frame for rendering
     */
    fun start(
        update: (fixedDeltaTime: Float) -> Unit,
        render: (deltaTime: Float, interpolation: Float) -> Unit
    )

    fun stop()
}
