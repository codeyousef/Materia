package io.materia.renderer.metrics

import io.materia.datetime.currentTimeMillis
import io.materia.renderer.backend.BackendId
import kotlinx.serialization.Serializable

/**
 * Performance monitoring interface for tracking rendering metrics and enforcing budget requirements.
 * Uses expect/actual pattern for platform-specific timer implementations.
 */
interface PerformanceMonitor {
    /**
     * Begin tracking initialization timing for a backend.
     */
    fun beginInitializationTrace(backendId: BackendId)

    /**
     * End initialization tracking and return statistics.
     */
    fun endInitializationTrace(backendId: BackendId): InitializationStats

    /**
     * Record frame timing metrics.
     */
    fun recordFrameMetrics(metrics: FrameMetrics)

    /**
     * Evaluate performance budget over a frame window.
     */
    fun evaluateBudget(window: FrameWindow = FrameWindow.DEFAULT): PerformanceAssessment
}

/**
 * Initialization timing statistics.
 */
@Serializable
data class InitializationStats(
    val backendId: BackendId,
    val initTimeMs: Long,
    val withinBudget: Boolean,
    val budgetMs: Long = 3000
) {
    val budgetUtilization: Double get() = initTimeMs.toDouble() / budgetMs.toDouble()
}

/**
 * Frame timing metrics for a single frame.
 */
@Serializable
data class FrameMetrics(
    val backendId: BackendId,
    val frameTimeMs: Double,
    val gpuTimeMs: Double,
    val cpuTimeMs: Double,
    val timestamp: Long = currentTimeMillis()
) {
    val fps: Double get() = 1000.0 / frameTimeMs
}

/**
 * Frame window specification for rolling average calculations.
 */
@Serializable
data class FrameWindow(
    val size: Int
) {
    init {
        require(size > 0) { "Frame window size must be positive, got $size" }
    }

    companion object {
        val DEFAULT = FrameWindow(120) // 2 seconds at 60 FPS
    }
}

/**
 * Performance budget assessment over a frame window.
 */
@Serializable
data class PerformanceAssessment(
    val backendId: BackendId,
    val avgFps: Double,
    val minFps: Double,
    val maxFps: Double,
    val withinBudget: Boolean,
    val notes: String? = null,
    val frameCount: Int,
    val timestamp: Long = currentTimeMillis()
) {
    val targetFps: Double = 60.0
    val minimumFps: Double = 30.0

    /**
     * Check if performance meets constitutional requirements (60 FPS target, 30 FPS minimum).
     */
    fun meetsRequirements(): Boolean {
        return avgFps >= targetFps && minFps >= minimumFps
    }
}

/**
 * Abstract base implementation providing rolling window logic.
 * Platform-specific implementations extend this with actual timer queries.
 */
abstract class AbstractPerformanceMonitor : PerformanceMonitor {
    private val initTracesMap = mutableMapOf<BackendId, Long>()
    private val frameMetricsHistory = mutableListOf<FrameMetrics>()
    private val maxHistorySize = 240 // Keep up to 4 seconds of history at 60 FPS

    override fun beginInitializationTrace(backendId: BackendId) {
        initTracesMap[backendId] = getCurrentTimeMs()
    }

    override fun endInitializationTrace(backendId: BackendId): InitializationStats {
        val startTime = initTracesMap.remove(backendId)
            ?: throw IllegalStateException("No initialization trace found for backend $backendId")

        val endTime = getCurrentTimeMs()
        val elapsedMs = endTime - startTime
        val budgetMs = 3000L

        return InitializationStats(
            backendId = backendId,
            initTimeMs = elapsedMs,
            withinBudget = elapsedMs < budgetMs,
            budgetMs = budgetMs
        )
    }

    override fun recordFrameMetrics(metrics: FrameMetrics) {
        frameMetricsHistory.add(metrics)

        // Trim history to prevent unbounded growth
        while (frameMetricsHistory.size > maxHistorySize) {
            frameMetricsHistory.removeAt(0)
        }
    }

    override fun evaluateBudget(window: FrameWindow): PerformanceAssessment {
        val recentFrames = frameMetricsHistory.takeLast(window.size)

        if (recentFrames.isEmpty()) {
            return PerformanceAssessment(
                backendId = BackendId.VULKAN, // Default, should be set by caller
                avgFps = 0.0,
                minFps = 0.0,
                maxFps = 0.0,
                withinBudget = false,
                notes = "No frame data available",
                frameCount = 0
            )
        }

        val backendId = recentFrames.first().backendId
        val avgFrameTime = recentFrames.map { it.frameTimeMs }.average()
        val minFrameTime = recentFrames.minOf { it.frameTimeMs }
        val maxFrameTime = recentFrames.maxOf { it.frameTimeMs }

        val avgFps = 1000.0 / avgFrameTime
        val minFps = 1000.0 / maxFrameTime // Min FPS = longest frame time
        val maxFps = 1000.0 / minFrameTime // Max FPS = shortest frame time

        val withinBudget = avgFps >= 60.0 && minFps >= 30.0

        val avgFpsFormatted = ((avgFps * 10).toInt() / 10.0)
        val minFpsFormatted = ((minFps * 10).toInt() / 10.0)

        return PerformanceAssessment(
            backendId = backendId,
            avgFps = avgFps,
            minFps = minFps,
            maxFps = maxFps,
            withinBudget = withinBudget,
            notes = if (!withinBudget) "Performance below budget: avg=$avgFpsFormatted FPS, min=$minFpsFormatted FPS" else null,
            frameCount = recentFrames.size
        )
    }

    /**
     * Get current time in milliseconds.
     * Platform implementations override this with high-precision timers.
     */
    protected abstract fun getCurrentTimeMs(): Long
}

/**
 * Platform factory for creating performance monitors.
 */
expect fun createPerformanceMonitor(): PerformanceMonitor
