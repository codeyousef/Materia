package io.materia.performance

import io.materia.renderer.RenderStats
import kotlin.math.roundToInt

/** Stable frame metrics suitable for an in-engine HUD and quality decisions. */
data class SmoothedFrameStats(
    val fps: Double = 0.0,
    val frameTimeMs: Double = 0.0,
    val drawCalls: Int = 0,
    val triangles: Int = 0,
    val sampleCount: Int = 0
)

/**
 * Fixed-size moving average that avoids exposing single-frame requestAnimationFrame
 * spikes to game UI or adaptive quality logic.
 */
class FrameStatsSmoother(windowSize: Int = 60) {
    private val capacity = windowSize.coerceAtLeast(1)
    private val frameTimesMs = ArrayDeque<Double>(capacity)
    private var frameTimeTotal = 0.0
    private var latestRenderStats: RenderStats? = null

    val snapshot: SmoothedFrameStats
        get() {
            val averageFrameTime = if (frameTimesMs.isEmpty()) {
                0.0
            } else {
                frameTimeTotal / frameTimesMs.size
            }
            val latest = latestRenderStats
            return SmoothedFrameStats(
                fps = if (averageFrameTime > 0.0) 1000.0 / averageFrameTime else 0.0,
                frameTimeMs = averageFrameTime,
                drawCalls = latest?.drawCalls ?: 0,
                triangles = latest?.triangles ?: 0,
                sampleCount = frameTimesMs.size
            )
        }

    fun recordFrame(deltaSeconds: Float, renderStats: RenderStats? = null): SmoothedFrameStats {
        val frameTime = deltaSeconds.coerceAtLeast(0f) * 1000.0
        if (frameTime > 0.0) {
            if (frameTimesMs.size == capacity) {
                frameTimeTotal -= frameTimesMs.removeFirst()
            }
            frameTimesMs.addLast(frameTime)
            frameTimeTotal += frameTime
        }
        if (renderStats != null) latestRenderStats = renderStats
        return snapshot
    }

    fun reset() {
        frameTimesMs.clear()
        frameTimeTotal = 0.0
        latestRenderStats = null
    }
}

data class AdaptiveResolutionConfig(
    val targetFps: Double = 55.0,
    val minimumScale: Float = 0.75f,
    val maximumScale: Float = 1.25f,
    val scaleStep: Float = 0.1f,
    val lowSampleCount: Int = 2,
    val highSampleCount: Int = 4,
    val lowFpsRatio: Double = 0.82,
    val highFpsRatio: Double = 1.04
) {
    init {
        require(targetFps > 0.0)
        require(minimumScale > 0f)
        require(maximumScale >= minimumScale)
        require(scaleStep > 0f)
        require(lowSampleCount > 0)
        require(highSampleCount > 0)
    }
}

/**
 * Hysteresis-based resolution policy. Callers own the platform resize operation;
 * this class only returns a new scale when a stable performance trend warrants it.
 */
class AdaptiveResolutionController(
    val config: AdaptiveResolutionConfig = AdaptiveResolutionConfig(),
    initialScale: Float = config.maximumScale
) {
    var scale: Float = initialScale.coerceIn(config.minimumScale, config.maximumScale)
        private set

    private var lowSamples = 0
    private var highSamples = 0

    fun record(smoothedFps: Double): Float? {
        if (smoothedFps <= 0.0) return null

        when {
            smoothedFps < config.targetFps * config.lowFpsRatio -> {
                lowSamples++
                highSamples = 0
                if (lowSamples >= config.lowSampleCount) {
                    lowSamples = 0
                    return changeScale(-config.scaleStep)
                }
            }

            smoothedFps > config.targetFps * config.highFpsRatio -> {
                highSamples++
                lowSamples = 0
                if (highSamples >= config.highSampleCount) {
                    highSamples = 0
                    return changeScale(config.scaleStep)
                }
            }

            else -> {
                lowSamples = 0
                highSamples = 0
            }
        }
        return null
    }

    fun reset(newScale: Float = config.maximumScale) {
        scale = newScale.coerceIn(config.minimumScale, config.maximumScale)
        lowSamples = 0
        highSamples = 0
    }

    private fun changeScale(delta: Float): Float? {
        val next = ((scale + delta) * 100f).roundToInt() / 100f
        val clamped = next.coerceIn(config.minimumScale, config.maximumScale)
        if (clamped == scale) return null
        scale = clamped
        return scale
    }
}
