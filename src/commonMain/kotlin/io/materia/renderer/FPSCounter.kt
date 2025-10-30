package io.materia.renderer

/**
 * FPS counter with rolling average for accurate frame rate measurement.
 * T014: Performance metrics system.
 *
 * Uses a sliding window to smooth FPS readings and reduce jitter.
 *
 * @param windowSize Number of frames to average (default: 60 frames = 1 second at 60 FPS)
 */
class FPSCounter(private val windowSize: Int = 60) {
    private val frameTimes = mutableListOf<Double>()
    private var lastTime = 0.0

    /**
     * Update FPS counter with current time.
     *
     * @param currentTime Current time in milliseconds (typically from performance.now())
     * @return Current FPS as a rolling average, or 0.0f if insufficient data
     */
    fun update(currentTime: Double): Float {
        if (lastTime > 0) {
            val deltaTime = currentTime - lastTime
            frameTimes.add(deltaTime)
            if (frameTimes.size > windowSize) {
                frameTimes.removeAt(0)
            }
        }
        lastTime = currentTime

        if (frameTimes.isEmpty()) return 0f
        val avgFrameTime = frameTimes.average()
        return (1000.0 / avgFrameTime).toFloat()
    }

    /**
     * Reset FPS counter state.
     * Useful when pausing/resuming or changing scenes.
     */
    fun reset() {
        frameTimes.clear()
        lastTime = 0.0
    }

    /**
     * Get current average frame time in milliseconds.
     * @return Average frame time, or 0.0 if insufficient data
     */
    fun getAverageFrameTime(): Double {
        if (frameTimes.isEmpty()) return 0.0
        return frameTimes.average()
    }

    /**
     * Get minimum FPS in the current window.
     * @return Minimum FPS, or 0.0f if insufficient data
     */
    fun getMinFPS(): Float {
        if (frameTimes.isEmpty()) return 0f
        val maxFrameTime = frameTimes.maxOrNull() ?: return 0f
        return (1000.0 / maxFrameTime).toFloat()
    }

    /**
     * Get maximum FPS in the current window.
     * @return Maximum FPS, or 0.0f if insufficient data
     */
    fun getMaxFPS(): Float {
        if (frameTimes.isEmpty()) return 0f
        val minFrameTime = frameTimes.minOrNull() ?: return 0f
        return (1000.0 / minFrameTime).toFloat()
    }
}
