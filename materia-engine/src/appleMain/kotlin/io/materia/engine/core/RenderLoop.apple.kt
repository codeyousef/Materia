package io.materia.engine.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import platform.Foundation.NSProcessInfo

actual class RenderLoop actual constructor() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null

    actual val isRunning: Boolean
        get() = loopJob?.isActive == true

    actual var targetFps: Int = 0

    actual fun start(callback: (deltaTime: Float) -> Unit) {
        if (loopJob?.isActive == true) return

        loopJob = scope.launch {
            var lastTime = currentSeconds()

            while (true) {
                val now = currentSeconds()
                val clampedDelta = (now - lastTime).toFloat().coerceIn(0.0001f, 0.25f)
                lastTime = now
                callback(clampedDelta)

                if (targetFps > 0) {
                    delay(1000L / targetFps)
                } else {
                    yield()
                }
            }
        }
    }

    actual fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}

actual class AdvancedRenderLoop actual constructor(
    actual val config: RenderLoopConfig
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null

    actual val isRunning: Boolean
        get() = loopJob?.isActive == true

    actual fun start(
        update: (fixedDeltaTime: Float) -> Unit,
        render: (deltaTime: Float, interpolation: Float) -> Unit
    ) {
        if (loopJob?.isActive == true) return

        loopJob = scope.launch {
            var lastTime = currentSeconds()
            var accumulator = 0f

            while (true) {
                val now = currentSeconds()
                val clampedDelta = (now - lastTime).toFloat().coerceIn(0.0001f, 0.25f)
                lastTime = now

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

                if (config.targetFps > 0) {
                    delay(1000L / config.targetFps)
                } else {
                    yield()
                }
            }
        }
    }

    actual fun stop() {
        loopJob?.cancel()
        loopJob = null
    }
}

private fun currentSeconds(): Double = NSProcessInfo.processInfo.systemUptime