/**
 * RenderLoop - JavaScript/WebAssembly Implementation
 *
 * Uses window.requestAnimationFrame for browser-synchronized rendering.
 */
package io.materia.engine.core

import kotlinx.browser.window

actual class RenderLoop actual constructor() {
    private var running = false
    private var lastTime: Double = 0.0
    private var animationFrameId: Int = 0
    private var frameCallback: ((Float) -> Unit)? = null

    actual val isRunning: Boolean get() = running

    actual var targetFps: Int = 0 // Ignored on JS - browser controls frame rate

    actual fun start(callback: (deltaTime: Float) -> Unit) {
        if (running) return

        running = true
        frameCallback = callback
        lastTime = window.performance.now()

        // Start the recursive animation frame loop
        requestNextFrame()
    }

    private fun requestNextFrame() {
        if (!running) return

        animationFrameId = window.requestAnimationFrame { currentTime ->
            if (!running) return@requestAnimationFrame

            val deltaTime = ((currentTime - lastTime) / 1000.0).toFloat()
            lastTime = currentTime

            // Clamp delta time to prevent huge jumps (e.g., after tab switch)
            val clampedDelta = deltaTime.coerceIn(0.0001f, 0.25f)

            try {
                frameCallback?.invoke(clampedDelta)
            } catch (e: Exception) {
                console.error("RenderLoop callback error: ${e.message}")
            }

            // Schedule next frame
            requestNextFrame()
        }
    }

    actual fun stop() {
        if (!running) return

        running = false
        window.cancelAnimationFrame(animationFrameId)
        animationFrameId = 0
        frameCallback = null
    }
}

actual class AdvancedRenderLoop actual constructor(
    actual val config: RenderLoopConfig
) {
    private var running = false
    private var lastTime: Double = 0.0
    private var accumulator: Float = 0f
    private var animationFrameId: Int = 0
    private var updateCallback: ((Float) -> Unit)? = null
    private var renderCallback: ((Float, Float) -> Unit)? = null

    actual val isRunning: Boolean get() = running

    actual fun start(
        update: (fixedDeltaTime: Float) -> Unit,
        render: (deltaTime: Float, interpolation: Float) -> Unit
    ) {
        if (running) return

        running = true
        updateCallback = update
        renderCallback = render
        lastTime = window.performance.now()
        accumulator = 0f

        requestNextFrame()
    }

    private fun requestNextFrame() {
        if (!running) return

        animationFrameId = window.requestAnimationFrame { currentTime ->
            if (!running) return@requestAnimationFrame

            val deltaTime = ((currentTime - lastTime) / 1000.0).toFloat()
            lastTime = currentTime

            val clampedDelta = deltaTime.coerceIn(0.0001f, 0.25f)

            try {
                if (config.fixedTimestep) {
                    accumulator += clampedDelta

                    var updates = 0
                    while (accumulator >= config.fixedDeltaTime && updates < config.maxUpdatesPerFrame) {
                        updateCallback?.invoke(config.fixedDeltaTime)
                        accumulator -= config.fixedDeltaTime
                        updates++
                    }

                    val interpolation = accumulator / config.fixedDeltaTime
                    renderCallback?.invoke(clampedDelta, interpolation)
                } else {
                    updateCallback?.invoke(clampedDelta)
                    renderCallback?.invoke(clampedDelta, 1f)
                }
            } catch (e: Exception) {
                console.error("AdvancedRenderLoop callback error: ${e.message}")
            }

            requestNextFrame()
        }
    }

    actual fun stop() {
        if (!running) return

        running = false
        window.cancelAnimationFrame(animationFrameId)
        animationFrameId = 0
        updateCallback = null
        renderCallback = null
    }
}
