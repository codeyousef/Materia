/**
 * RenderLoop - JVM Implementation
 *
 * Uses a blocking loop with optional frame pacing for desktop rendering.
 */
package io.materia.engine.core

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

actual class RenderLoop actual constructor() {
    private val running = AtomicBoolean(false)
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    actual val isRunning: Boolean get() = running.get()

    actual var targetFps: Int = 0

    actual fun start(callback: (deltaTime: Float) -> Unit) {
        if (!running.compareAndSet(false, true)) return

        loopJob = scope.launch {
            var lastTime = System.nanoTime()

            while (running.get()) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime

                // Clamp delta time to prevent huge jumps
                val clampedDelta = deltaTime.coerceIn(0.0001f, 0.25f)

                try {
                    // Switch to main dispatcher for callback if needed
                    withContext(Dispatchers.Main.immediate) {
                        callback(clampedDelta)
                    }
                } catch (e: Exception) {
                    System.err.println("RenderLoop callback error: ${e.message}")
                    e.printStackTrace()
                }

                // Frame pacing if target FPS is set
                if (targetFps > 0) {
                    val targetFrameTime = 1_000_000_000L / targetFps
                    val elapsedTime = System.nanoTime() - currentTime
                    val sleepTime = targetFrameTime - elapsedTime

                    if (sleepTime > 0) {
                        delay(sleepTime / 1_000_000) // Convert to milliseconds
                    }
                } else {
                    // Yield to allow other coroutines to run
                    yield()
                }
            }
        }
    }

    actual fun stop() {
        if (!running.compareAndSet(true, false)) return
        loopJob?.cancel()
        loopJob = null
    }
}

actual class AdvancedRenderLoop actual constructor(
    actual val config: RenderLoopConfig
) {
    private val running = AtomicBoolean(false)
    private var loopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    actual val isRunning: Boolean get() = running.get()

    actual fun start(
        update: (fixedDeltaTime: Float) -> Unit,
        render: (deltaTime: Float, interpolation: Float) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return

        loopJob = scope.launch {
            var lastTime = System.nanoTime()
            var accumulator = 0f

            while (running.get()) {
                val currentTime = System.nanoTime()
                val deltaTime = (currentTime - lastTime) / 1_000_000_000f
                lastTime = currentTime

                val clampedDelta = deltaTime.coerceIn(0.0001f, 0.25f)

                try {
                    withContext(Dispatchers.Main.immediate) {
                        if (config.fixedTimestep) {
                            accumulator += clampedDelta

                            var updates = 0
                            while (accumulator >= config.fixedDeltaTime && updates < config.maxUpdatesPerFrame) {
                                update(config.fixedDeltaTime)
                                accumulator -= config.fixedDeltaTime
                                updates++
                            }

                            val interpolation = accumulator / config.fixedDeltaTime
                            render(clampedDelta, interpolation)
                        } else {
                            update(clampedDelta)
                            render(clampedDelta, 1f)
                        }
                    }
                } catch (e: Exception) {
                    System.err.println("AdvancedRenderLoop callback error: ${e.message}")
                    e.printStackTrace()
                }

                // Frame pacing
                if (config.targetFps > 0) {
                    val targetFrameTime = 1_000_000_000L / config.targetFps
                    val elapsedTime = System.nanoTime() - currentTime
                    val sleepTime = targetFrameTime - elapsedTime

                    if (sleepTime > 0) {
                        delay(sleepTime / 1_000_000)
                    }
                } else {
                    yield()
                }
            }
        }
    }

    actual fun stop() {
        if (!running.compareAndSet(true, false)) return
        loopJob?.cancel()
        loopJob = null
    }
}
