/**
 * RenderLoop - Android Implementation
 *
 * Uses Choreographer for frame-synchronized rendering on Android.
 */
package io.materia.engine.core

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean

actual class RenderLoop actual constructor() {
    private val running = AtomicBoolean(false)
    private val choreographer = Choreographer.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFrameTimeNanos = 0L
    private var callback: ((Float) -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running.get()) return

            val deltaTime = if (lastFrameTimeNanos == 0L) {
                1f / 60f // Assume 60fps for first frame
            } else {
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            }
            lastFrameTimeNanos = frameTimeNanos

            // Clamp delta time to prevent huge jumps
            val clampedDelta = deltaTime.coerceIn(0.0001f, 0.25f)

            try {
                callback?.invoke(clampedDelta)
            } catch (e: Exception) {
                System.err.println("RenderLoop callback error: ${e.message}")
                e.printStackTrace()
            }

            // Schedule next frame
            if (running.get()) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    actual val isRunning: Boolean get() = running.get()

    actual var targetFps: Int = 0 // Android uses VSync by default

    actual fun start(callback: (deltaTime: Float) -> Unit) {
        if (!running.compareAndSet(false, true)) return

        this.callback = callback
        lastFrameTimeNanos = 0L

        mainHandler.post {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    actual fun stop() {
        if (!running.compareAndSet(true, false)) return

        mainHandler.post {
            choreographer.removeFrameCallback(frameCallback)
        }
        callback = null
    }
}

actual class AdvancedRenderLoop actual constructor(
    actual val config: RenderLoopConfig
) {
    private val running = AtomicBoolean(false)
    private val choreographer = Choreographer.getInstance()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastFrameTimeNanos = 0L
    private var accumulator = 0f
    private var updateCallback: ((Float) -> Unit)? = null
    private var renderCallback: ((Float, Float) -> Unit)? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running.get()) return

            val deltaTime = if (lastFrameTimeNanos == 0L) {
                1f / 60f
            } else {
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f
            }
            lastFrameTimeNanos = frameTimeNanos

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
                System.err.println("AdvancedRenderLoop callback error: ${e.message}")
                e.printStackTrace()
            }

            if (running.get()) {
                choreographer.postFrameCallback(this)
            }
        }
    }

    actual val isRunning: Boolean get() = running.get()

    actual fun start(
        update: (fixedDeltaTime: Float) -> Unit,
        render: (deltaTime: Float, interpolation: Float) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return

        this.updateCallback = update
        this.renderCallback = render
        lastFrameTimeNanos = 0L
        accumulator = 0f

        mainHandler.post {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    actual fun stop() {
        if (!running.compareAndSet(true, false)) return

        mainHandler.post {
            choreographer.removeFrameCallback(frameCallback)
        }
        updateCallback = null
        renderCallback = null
    }
}
