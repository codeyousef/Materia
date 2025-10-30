package io.materia.profiling

/**
 * Real-time profiling dashboard for monitoring performance during development.
 * Provides a comprehensive view of all profiling metrics.
 */
class ProfilingDashboard {

    private var enabled = false
    private val dashboardConfig = DashboardConfig()

    /**
     * Enable the profiling dashboard
     */
    fun enable(config: DashboardConfig = DashboardConfig()) {
        enabled = true
        dashboardConfig.apply {
            updateIntervalMs = config.updateIntervalMs
            showHotspots = config.showHotspots
            showMemory = config.showMemory
            showFrameGraph = config.showFrameGraph
            showRecommendations = config.showRecommendations
        }

        PerformanceProfiler.configure(
            ProfilerConfig(
                enabled = true,
                trackMemory = config.showMemory,
                verbosity = config.verbosity
            )
        )
    }

    /**
     * Disable the profiling dashboard
     */
    fun disable() {
        enabled = false
        PerformanceProfiler.configure(ProfilerConfig(enabled = false))
    }

    /**
     * Get current dashboard state
     */
    fun getCurrentState(): DashboardState {
        if (!enabled) {
            return DashboardState(
                enabled = false,
                frameStats = FrameStats.EMPTY,
                hotspots = emptyList(),
                memoryStats = null,
                recommendations = emptyList()
            )
        }

        val frameStats = PerformanceProfiler.getFrameStats()
        val hotspots = if (dashboardConfig.showHotspots) {
            PerformanceProfiler.getHotspots().take(dashboardConfig.maxHotspots)
        } else emptyList()

        val memoryStats = if (dashboardConfig.showMemory) {
            PerformanceProfiler.getMemoryStats()
        } else null

        val recommendations = if (dashboardConfig.showRecommendations) {
            ProfilingReport.generateReport().recommendations.take(dashboardConfig.maxRecommendations)
        } else emptyList()

        return DashboardState(
            enabled = true,
            frameStats = frameStats,
            hotspots = hotspots,
            memoryStats = memoryStats,
            recommendations = recommendations
        )
    }

    /**
     * Get formatted dashboard text
     */
    fun getFormattedText(): String {
        val state = getCurrentState()
        if (!state.enabled) return "Dashboard disabled"

        return buildString {
            appendLine("═══════════════════════════════════════════════════════════════════════════════")
            appendLine("  Materia Performance Dashboard")
            appendLine("═══════════════════════════════════════════════════════════════════════════════")
            appendLine()

            // Performance indicator
            val fpsStatus = when {
                state.frameStats.averageFps >= 58 -> "✓ EXCELLENT"
                state.frameStats.averageFps >= 50 -> "⚠ GOOD"
                state.frameStats.averageFps >= 40 -> "⚠ FAIR"
                else -> "✗ POOR"
            }

            appendLine(
                "  FPS: ${
                    io.materia.core.platform.formatDouble(
                        state.frameStats.averageFps,
                        1
                    )
                } $fpsStatus"
            )
            appendLine("  Frame Time: ${formatMs(state.frameStats.averageFrameTime / 1_000_000)} (Target: 16.67ms)")
            appendLine("  Dropped: ${state.frameStats.droppedFrames} frames")
            appendLine()

            // Memory
            state.memoryStats?.let { memory ->
                appendLine("  Memory: ${formatMB(memory.current)} / Peak: ${formatMB(memory.peak)}")
                appendLine("  Allocation Rate: ${formatMB(memory.allocations)}/s")
                appendLine()
            }

            // Top hotspots
            if (state.hotspots.isNotEmpty()) {
                appendLine("  Top Hotspots:")
                state.hotspots.take(5).forEach { hotspot ->
                    appendLine(
                        "    • ${hotspot.name}: ${
                            io.materia.core.platform.formatFloat(
                                hotspot.percentage,
                                1
                            )
                        }% (${hotspot.callCount} calls)"
                    )
                }
                appendLine()
            }

            // Critical recommendations
            val critical = state.recommendations.filter { it.severity == Severity.HIGH }
            if (critical.isNotEmpty()) {
                appendLine("  ⚠ Critical Issues:")
                critical.forEach { rec ->
                    appendLine("    • ${rec.message}")
                }
                appendLine()
            }

            appendLine("═══════════════════════════════════════════════════════════════════════════════")
        }
    }

    /**
     * Check if performance is acceptable
     */
    fun isPerformanceAcceptable(targetFps: Int = 60): Boolean {
        val state = getCurrentState()
        return state.frameStats.meetsTargetFps(targetFps) &&
                state.recommendations.none { it.severity == Severity.HIGH }
    }

    /**
     * Get performance grade (A-F)
     */
    fun getPerformanceGrade(): Grade {
        val state = getCurrentState()

        val fpsScore = when {
            state.frameStats.averageFps >= 58 -> 10
            state.frameStats.averageFps >= 50 -> 8
            state.frameStats.averageFps >= 40 -> 6
            state.frameStats.averageFps >= 30 -> 4
            else -> 2
        }

        val memoryScore = state.memoryStats?.let { memory ->
            when {
                memory.gcPressure < 0.2f -> 10
                memory.gcPressure < 0.4f -> 8
                memory.gcPressure < 0.6f -> 6
                memory.gcPressure < 0.8f -> 4
                else -> 2
            }
        } ?: 10

        val issueScore = when (state.recommendations.count { it.severity == Severity.HIGH }) {
            0 -> 10
            1 -> 8
            2 -> 6
            3 -> 4
            else -> 2
        }

        val totalScore = (fpsScore + memoryScore + issueScore) / 3

        return when {
            totalScore >= 9 -> Grade.A
            totalScore >= 8 -> Grade.B
            totalScore >= 7 -> Grade.C
            totalScore >= 6 -> Grade.D
            else -> Grade.F
        }
    }

    private fun formatMs(ms: Long): String {
        return "${io.materia.core.platform.formatFloat(ms.toFloat(), 2)}ms"
    }

    private fun formatMB(bytes: Long): String {
        return "${io.materia.core.platform.formatFloat(bytes / (1024f * 1024f), 1)}MB"
    }
}

/**
 * Dashboard configuration
 */
data class DashboardConfig(
    var updateIntervalMs: Long = 1000,
    var showHotspots: Boolean = true,
    var showMemory: Boolean = true,
    var showFrameGraph: Boolean = true,
    var showRecommendations: Boolean = true,
    var maxHotspots: Int = 10,
    var maxRecommendations: Int = 5,
    var verbosity: ProfileVerbosity = ProfileVerbosity.NORMAL
)

/**
 * Dashboard state snapshot
 */
data class DashboardState(
    val enabled: Boolean,
    val frameStats: FrameStats,
    val hotspots: List<Hotspot>,
    val memoryStats: MemoryStats?,
    val recommendations: List<Recommendation>
)

/**
 * Performance grade
 */
enum class Grade {
    A, B, C, D, F
}

/**
 * Quick profiling helpers for common scenarios
 */
object ProfilingHelpers {

    /**
     * Enable profiling for development
     */
    fun enableDevelopmentProfiling() {
        PerformanceProfiler.configure(
            ProfilerConfig(
                enabled = true,
                trackMemory = true,
                verbosity = ProfileVerbosity.DETAILED
            )
        )
    }

    /**
     * Enable lightweight profiling for production
     */
    fun enableProductionProfiling() {
        PerformanceProfiler.configure(
            ProfilerConfig(
                enabled = true,
                trackMemory = false,
                verbosity = ProfileVerbosity.MINIMAL,
                frameHistorySize = 60 // Only keep 1 second of history
            )
        )
    }

    /**
     * Profile a game loop iteration
     */
    inline fun profileGameLoop(
        deltaTime: Float,
        updatePhase: () -> Unit,
        renderPhase: () -> Unit
    ) {
        PerformanceProfiler.startFrame()

        PerformanceProfiler.measure("gameLoop.update", ProfileCategory.OTHER) {
            updatePhase()
        }

        PerformanceProfiler.measure("gameLoop.render", ProfileCategory.RENDERING) {
            renderPhase()
        }

        PerformanceProfiler.recordCounter("gameLoop.deltaTimeMs", (deltaTime * 1000).toLong())
        PerformanceProfiler.endFrame()
    }

    /**
     * Profile scene rendering with detailed breakdowns
     */
    inline fun profileSceneRender(
        sceneName: String,
        culling: () -> Unit,
        sorting: () -> Unit,
        drawCalls: () -> Unit
    ) {
        PerformanceProfiler.measure("scene.$sceneName.render", ProfileCategory.RENDERING) {
            PerformanceProfiler.measure("scene.$sceneName.culling", ProfileCategory.CULLING) {
                culling()
            }

            PerformanceProfiler.measure("scene.$sceneName.sorting", ProfileCategory.RENDERING) {
                sorting()
            }

            PerformanceProfiler.measure("scene.$sceneName.drawCalls", ProfileCategory.RENDERING) {
                drawCalls()
            }
        }
    }

    /**
     * Create a profiling session
     */
    fun createSession(name: String): ProfilingSession {
        return ProfilingSession(name)
    }
}

/**
 * Profiling session for focused performance analysis
 */
class ProfilingSession(val name: String) {
    private val startTime = io.materia.core.platform.Platform.currentTimeNanos()
    private val startFrame = PerformanceProfiler.getFrameStats().frameCount
    private var endTime: Long? = null
    private var endFrame: Int? = null

    /**
     * End the profiling session
     */
    fun end(): SessionSummary {
        endTime = io.materia.core.platform.Platform.currentTimeNanos()
        endFrame = PerformanceProfiler.getFrameStats().frameCount

        val finalEndTime = endTime ?: io.materia.core.platform.Platform.currentTimeNanos()
        val finalEndFrame = endFrame ?: PerformanceProfiler.getFrameStats().frameCount

        val duration = (finalEndTime - startTime) / 1_000_000_000.0 // seconds
        val frameCount = finalEndFrame - startFrame
        val averageFps = frameCount / duration

        val report = ProfilingReport.generateReport()

        return SessionSummary(
            name = name,
            durationSeconds = duration,
            frameCount = frameCount,
            averageFps = averageFps,
            hotspots = report.hotspots.take(10),
            recommendations = report.recommendations
        )
    }
}

/**
 * Profiling session summary
 */
data class SessionSummary(
    val name: String,
    val durationSeconds: Double,
    val frameCount: Int,
    val averageFps: Double,
    val hotspots: List<Hotspot>,
    val recommendations: List<Recommendation>
) {
    fun printSummary() {
        println()
        println("═══════════════════════════════════════════════════════════════")
        println("  Profiling Session: $name")
        println("═══════════════════════════════════════════════════════════════")
        println("  Duration: ${io.materia.core.platform.formatDouble(durationSeconds, 2)}s")
        println("  Frames: $frameCount")
        println("  Average FPS: ${io.materia.core.platform.formatDouble(averageFps, 2)}")
        println()
        println("  Top Hotspots:")
        hotspots.take(5).forEach { hotspot ->
            println(
                "    • ${hotspot.name}: ${
                    io.materia.core.platform.formatFloat(
                        hotspot.percentage,
                        1
                    )
                }%"
            )
        }
        println()
        if (recommendations.isNotEmpty()) {
            println("  Recommendations: ${recommendations.size}")
            recommendations.take(3).forEach { rec ->
                println("    • [${rec.severity}] ${rec.message}")
            }
        }
        println("═══════════════════════════════════════════════════════════════")
        println()
    }
}
