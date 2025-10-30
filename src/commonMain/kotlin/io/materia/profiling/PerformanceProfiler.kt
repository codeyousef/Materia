package io.materia.profiling

import io.materia.core.platform.Platform
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update

/**
 * Core performance profiling system for Materia.
 * Provides zero-cost profiling when disabled and comprehensive metrics when enabled.
 *
 * Usage:
 * ```kotlin
 * PerformanceProfiler.configure(ProfilerConfig(enabled = true))
 * PerformanceProfiler.measure("render") {
 *     renderer.render(scene, camera)
 * }
 * ```
 */
object PerformanceProfiler {

    internal val enabled = atomic(false)
    internal val config = atomic(ProfilerConfig())
    private val currentFrame = atomic<FrameProfileData?>(null)
    private val frameHistory = atomic<List<FrameProfileData>>(emptyList())
    private val hotspots = atomic<Map<String, HotspotData>>(emptyMap())

    // Frame timing
    private val frameStartTime = atomic(0L)
    private val frameNumber = atomic(0)

    // Memory tracking
    private val memorySnapshots = atomic<List<PerformanceMemorySnapshot>>(emptyList())

    /**
     * Configure the profiler
     */
    fun configure(newConfig: ProfilerConfig) {
        config.value = newConfig
        enabled.value = newConfig.enabled

        if (!newConfig.enabled) {
            reset()
        }
    }

    /**
     * Check if profiling is enabled
     */
    fun isEnabled(): Boolean = enabled.value

    /**
     * Check if memory tracking is enabled
     */
    fun isMemoryTrackingEnabled(): Boolean = config.value.trackMemory

    /**
     * Start a new frame
     */
    fun startFrame() {
        if (!enabled.value) return

        val now = Platform.currentTimeNanos()
        frameStartTime.value = now
        frameNumber.update { it + 1 }

        currentFrame.value = FrameProfileData(
            frameNumber = frameNumber.value,
            startTime = now,
            measurements = mutableListOf(),
            memoryUsage = 0L
        )
    }

    /**
     * End the current frame and store statistics
     */
    fun endFrame() {
        if (!enabled.value) return

        val frame = currentFrame.value ?: return
        val now = Platform.currentTimeNanos()

        frame.endTime = now
        frame.duration = now - frame.startTime

        // Update frame history
        val cfg = config.value
        frameHistory.update { history ->
            (history + frame).takeLast(cfg.frameHistorySize)
        }

        // Update hotspots
        updateHotspots(frame)

        // Take memory snapshot if enabled
        if (cfg.trackMemory && frameNumber.value % cfg.memorySnapshotInterval == 0) {
            captureMemorySnapshot()
        }

        currentFrame.value = null
    }

    /**
     * Measure execution time of a code block
     */
    inline fun <T> measure(
        name: String,
        category: ProfileCategory = ProfileCategory.OTHER,
        block: () -> T
    ): T {
        if (!isEnabled()) {
            return block()
        }

        val startTime = Platform.currentTimeNanos()
        val trackMemory = isMemoryTrackingEnabled()
        val startMemory = if (trackMemory) Platform.getUsedMemory() else 0L

        return try {
            block()
        } finally {
            val endTime = Platform.currentTimeNanos()
            val duration = endTime - startTime
            val memoryDelta = if (trackMemory) {
                Platform.getUsedMemory() - startMemory
            } else 0L

            recordMeasurement(name, category, duration, memoryDelta)
        }
    }

    /**
     * Measure execution time with custom timing
     */
    inline fun <T> measureWithTiming(
        name: String,
        category: ProfileCategory = ProfileCategory.OTHER,
        timing: (T, Long) -> Unit,
        block: () -> T
    ): T {
        if (!isEnabled()) {
            return block()
        }

        val startTime = Platform.currentTimeNanos()
        val result = block()
        val duration = Platform.currentTimeNanos() - startTime

        recordMeasurement(name, category, duration, 0L)
        timing(result, duration)

        return result
    }

    /**
     * Start a profiling scope (for manual control)
     */
    fun startScope(name: String, category: ProfileCategory = ProfileCategory.OTHER): ProfileScope {
        return ProfileScope(name, category, Platform.currentTimeNanos())
    }

    /**
     * Record a measurement
     */
    fun recordMeasurement(
        name: String,
        category: ProfileCategory,
        durationNanos: Long,
        memoryDelta: Long = 0L
    ) {
        if (!enabled.value) return

        val frame = currentFrame.value ?: return

        frame.measurements.add(
            Measurement(
                name = name,
                category = category,
                durationNanos = durationNanos,
                memoryDelta = memoryDelta,
                timestamp = Platform.currentTimeNanos()
            )
        )
    }

    /**
     * Record a counter value
     */
    fun recordCounter(name: String, value: Long) {
        if (!enabled.value) return

        val frame = currentFrame.value ?: return
        frame.counters[name] = value
    }

    /**
     * Increment a counter
     */
    fun incrementCounter(name: String, delta: Long = 1) {
        if (!enabled.value) return

        val frame = currentFrame.value ?: return
        frame.counters[name] = (frame.counters[name] ?: 0L) + delta
    }

    /**
     * Get frame statistics
     */
    fun getFrameStats(): FrameStats {
        val history = frameHistory.value
        if (history.isEmpty()) {
            return FrameStats.EMPTY
        }

        val recentFrames = history.takeLast(config.value.frameStatsWindow)
        val frameTimes = recentFrames.map { it.duration }

        return FrameStats(
            averageFrameTime = frameTimes.average().toLong(),
            minFrameTime = frameTimes.minOrNull() ?: 0L,
            maxFrameTime = frameTimes.maxOrNull() ?: 0L,
            frameCount = frameNumber.value,
            averageFps = calculateFps(frameTimes.average()),
            percentile95 = calculatePercentile(frameTimes, 0.95),
            percentile99 = calculatePercentile(frameTimes, 0.99),
            droppedFrames = countDroppedFrames(recentFrames)
        )
    }

    /**
     * Get hotspot analysis
     */
    fun getHotspots(): List<Hotspot> {
        return hotspots.value
            .entries
            .map { (name, data) ->
                Hotspot(
                    name = name,
                    category = data.category,
                    totalTime = data.totalTime,
                    callCount = data.callCount,
                    averageTime = if (data.callCount > 0) data.totalTime / data.callCount else 0L,
                    percentage = calculatePercentage(data.totalTime)
                )
            }
            .sortedByDescending { it.totalTime }
    }

    /**
     * Get memory statistics
     */
    fun getMemoryStats(): MemoryStats? {
        val snapshots = memorySnapshots.value
        if (snapshots.isEmpty()) return null

        val latest = snapshots.lastOrNull() ?: return null
        val trend = if (snapshots.size >= 2) {
            val previous = snapshots[snapshots.size - 2]
            latest.usedMemory - previous.usedMemory
        } else 0L

        return MemoryStats(
            current = latest.usedMemory,
            peak = snapshots.maxOfOrNull { it.usedMemory } ?: 0L,
            trend = trend,
            allocations = latest.allocationRate,
            gcPressure = latest.gcPressure
        )
    }

    /**
     * Get detailed frame data
     */
    fun getFrameData(frameNumber: Int): FrameProfileData? {
        return frameHistory.value.find { it.frameNumber == frameNumber }
    }

    /**
     * Get recent frame data
     */
    fun getRecentFrames(count: Int = 60): List<FrameProfileData> {
        return frameHistory.value.takeLast(count)
    }

    /**
     * Export profiling data
     */
    fun export(format: ExportFormat = ExportFormat.JSON): String {
        return when (format) {
            ExportFormat.JSON -> exportToJson()
            ExportFormat.CSV -> exportToCsv()
            ExportFormat.CHROME_TRACE -> exportToChromeTrace()
        }
    }

    /**
     * Reset all profiling data
     */
    fun reset() {
        frameHistory.value = emptyList()
        hotspots.value = emptyMap()
        memorySnapshots.value = emptyList()
        frameNumber.value = 0
        currentFrame.value = null
    }

    // Private helper methods

    private fun updateHotspots(frame: FrameProfileData) {
        val updates = mutableMapOf<String, HotspotData>()

        frame.measurements.forEach { measurement ->
            val key = measurement.name
            val existing = hotspots.value[key] ?: HotspotData(measurement.category, 0L, 0)

            updates[key] = HotspotData(
                category = measurement.category,
                totalTime = existing.totalTime + measurement.durationNanos,
                callCount = existing.callCount + 1
            )
        }

        hotspots.update { current ->
            val merged = current.toMutableMap()
            updates.forEach { (key, value) ->
                merged[key] = value
            }
            merged
        }
    }

    private fun captureMemorySnapshot() {
        val snapshot = PerformanceMemorySnapshot(
            timestamp = Platform.currentTimeNanos(),
            usedMemory = Platform.getUsedMemory(),
            totalMemory = Platform.getTotalMemory(),
            allocationRate = estimateAllocationRate(),
            gcPressure = estimateGcPressure()
        )

        memorySnapshots.update { snapshots ->
            (snapshots + snapshot).takeLast(config.value.memoryHistorySize)
        }
    }

    private fun estimateAllocationRate(): Long {
        val snapshots = memorySnapshots.value
        if (snapshots.size < 2) return 0L

        val recent = snapshots.lastOrNull() ?: return 0L
        val previous = snapshots.getOrNull(snapshots.size - 2) ?: return 0L
        val timeDelta = recent.timestamp - previous.timestamp

        if (timeDelta == 0L) return 0L

        val memoryDelta = recent.usedMemory - previous.usedMemory
        return (memoryDelta * 1_000_000_000L) / timeDelta // bytes per second
    }

    private fun estimateGcPressure(): Float {
        // Simplified GC pressure estimation
        val snapshots = memorySnapshots.value
        if (snapshots.size < 3) return 0f

        val recentDrops = snapshots.takeLast(10)
            .zipWithNext()
            .count { (prev, curr) -> curr.usedMemory < prev.usedMemory * 0.9f }

        return recentDrops / 10f
    }

    private fun calculateFps(averageFrameTimeNanos: Double): Double {
        if (averageFrameTimeNanos < 0.001) return 0.0
        return 1_000_000_000.0 / averageFrameTimeNanos
    }

    private fun calculatePercentile(values: List<Long>, percentile: Double): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        val index = (sorted.size * percentile).toInt().coerceAtMost(sorted.size - 1)
        return sorted[index]
    }

    private fun countDroppedFrames(frames: List<FrameProfileData>): Int {
        val targetFrameTime = 16_666_667L // 60 FPS in nanoseconds
        return frames.count { it.duration > targetFrameTime }
    }

    private fun calculatePercentage(time: Long): Float {
        val recentFrames = frameHistory.value.takeLast(config.value.frameStatsWindow)
        if (recentFrames.isEmpty()) return 0f

        val totalTime = recentFrames.sumOf { it.duration }
        if (totalTime == 0L) return 0f

        return (time.toFloat() / totalTime.toFloat()) * 100f
    }

    private fun exportToJson(): String {
        val frames = frameHistory.value
        val hotspots = getHotspots()
        val stats = getFrameStats()

        return buildString {
            appendLine("{")
            appendLine("  \"stats\": {")
            appendLine("    \"averageFrameTime\": ${stats.averageFrameTime},")
            appendLine("    \"averageFps\": ${stats.averageFps},")
            appendLine("    \"frameCount\": ${stats.frameCount},")
            appendLine("    \"droppedFrames\": ${stats.droppedFrames}")
            appendLine("  },")
            appendLine("  \"hotspots\": [")
            hotspots.forEachIndexed { index, hotspot ->
                appendLine("    {")
                appendLine("      \"name\": \"${hotspot.name}\",")
                appendLine("      \"totalTime\": ${hotspot.totalTime},")
                appendLine("      \"callCount\": ${hotspot.callCount},")
                appendLine("      \"averageTime\": ${hotspot.averageTime},")
                appendLine("      \"percentage\": ${hotspot.percentage}")
                append("    }")
                if (index < hotspots.size - 1) appendLine(",")
                else appendLine()
            }
            appendLine("  ],")
            appendLine("  \"frameCount\": ${frames.size}")
            appendLine("}")
        }
    }

    private fun exportToCsv(): String {
        return buildString {
            appendLine("frame,duration,measurements,counters")
            frameHistory.value.forEach { frame ->
                appendLine("${frame.frameNumber},${frame.duration},${frame.measurements.size},${frame.counters.size}")
            }
        }
    }

    private fun exportToChromeTrace(): String {
        // Chrome trace event format
        return buildString {
            appendLine("[")
            val events = mutableListOf<String>()

            frameHistory.value.forEach { frame ->
                frame.measurements.forEach { measurement ->
                    events.add(
                        """
                        {
                          "name": "${measurement.name}",
                          "cat": "${measurement.category}",
                          "ph": "X",
                          "ts": ${measurement.timestamp / 1000},
                          "dur": ${measurement.durationNanos / 1000},
                          "pid": 1,
                          "tid": 1
                        }
                    """.trimIndent()
                    )
                }
            }

            append(events.joinToString(",\n"))
            appendLine()
            appendLine("]")
        }
    }
}

/**
 * Profiling scope for manual control
 */
class ProfileScope(
    private val name: String,
    private val category: ProfileCategory,
    private val startTime: Long
) {
    private var ended = false

    fun end() {
        if (ended) return
        ended = true

        val duration = Platform.currentTimeNanos() - startTime
        PerformanceProfiler.recordMeasurement(name, category, duration)
    }
}

/**
 * Profiler configuration
 */
data class ProfilerConfig(
    val enabled: Boolean = false,
    val trackMemory: Boolean = true,
    val frameHistorySize: Int = 300,
    val memoryHistorySize: Int = 60,
    val frameStatsWindow: Int = 60,
    val memorySnapshotInterval: Int = 10,
    val verbosity: ProfileVerbosity = ProfileVerbosity.NORMAL
)

/**
 * Profiling verbosity levels
 */
enum class ProfileVerbosity {
    MINIMAL,  // Only frame stats
    NORMAL,   // Frame stats + hotspots
    DETAILED, // Everything including individual measurements
    TRACE     // Maximum detail with memory tracking
}

/**
 * Export formats
 */
enum class ExportFormat {
    JSON,
    CSV,
    CHROME_TRACE
}

/**
 * Profile categories for grouping measurements
 */
enum class ProfileCategory {
    RENDERING,
    SCENE_GRAPH,
    GEOMETRY,
    PHYSICS,
    ANIMATION,
    MEMORY,
    SHADER,
    BUFFER,
    TEXTURE,
    CULLING,
    MATRIX,
    OTHER
}

/**
 * Frame profiling data
 */
data class FrameProfileData(
    val frameNumber: Int,
    val startTime: Long,
    var endTime: Long = 0L,
    var duration: Long = 0L,
    val measurements: MutableList<Measurement> = mutableListOf(),
    val counters: MutableMap<String, Long> = mutableMapOf(),
    var memoryUsage: Long = 0L
)

/**
 * Individual measurement
 */
data class Measurement(
    val name: String,
    val category: ProfileCategory,
    val durationNanos: Long,
    val memoryDelta: Long,
    val timestamp: Long
)

/**
 * Frame statistics
 */
data class FrameStats(
    val averageFrameTime: Long,
    val minFrameTime: Long,
    val maxFrameTime: Long,
    val frameCount: Int,
    val averageFps: Double,
    val percentile95: Long,
    val percentile99: Long,
    val droppedFrames: Int
) {
    companion object {
        val EMPTY = FrameStats(0, 0, 0, 0, 0.0, 0, 0, 0)
    }

    fun meetsTargetFps(targetFps: Int = 60): Boolean {
        val targetFrameTime = 1_000_000_000L / targetFps
        return percentile95 <= targetFrameTime
    }
}

/**
 * Hotspot analysis data
 */
data class Hotspot(
    val name: String,
    val category: ProfileCategory,
    val totalTime: Long,
    val callCount: Int,
    val averageTime: Long,
    val percentage: Float
)

/**
 * Internal hotspot tracking
 */
private data class HotspotData(
    val category: ProfileCategory,
    val totalTime: Long,
    val callCount: Int
)

/**
 * Performance memory snapshot (renamed to avoid conflict with MemoryProfiler's MemorySnapshot)
 */
data class PerformanceMemorySnapshot(
    val timestamp: Long,
    val usedMemory: Long,
    val totalMemory: Long,
    val allocationRate: Long,
    val gcPressure: Float
)

/**
 * Memory statistics
 */
data class MemoryStats(
    val current: Long,
    val peak: Long,
    val trend: Long,
    val allocations: Long,
    val gcPressure: Float
)
