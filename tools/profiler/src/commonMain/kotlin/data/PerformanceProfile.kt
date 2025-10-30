package io.materia.tools.profiler.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.math.*

/**
 * PerformanceProfile - Data model for performance monitoring and analysis
 *
 * This data class represents a complete performance profiling session containing
 * frame data, memory snapshots, performance events, and summary statistics.
 * It's optimized for binary serialization for efficiency and JSON for reports.
 */
@Serializable
data class PerformanceProfile @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val sessionStart: Instant,
    val sessionEnd: Instant,
    val platform: RuntimePlatform,
    val frames: List<FrameData>,
    val memory: List<MemorySnapshot>,
    val events: List<PerformanceEvent>,
    val summary: PerformanceSummary,
    val configuration: ProfilingConfiguration,
    val metadata: ProfilingMetadata
) {
    init {
        require(sessionEnd >= sessionStart) {
            "Session end time must be after or equal to start time"
        }
        require(frames.isNotEmpty()) { "Performance profile must contain at least one frame" }

        // Validate frame data is chronologically ordered
        val sortedFrames = frames.sortedBy { it.timestamp }
        require(frames == sortedFrames) {
            "Frames must be ordered chronologically"
        }

        // Validate frame timestamps are within session bounds
        val sessionStartMs = sessionStart.toEpochMilliseconds() * 1000 // Convert to microseconds
        val sessionEndMs = sessionEnd.toEpochMilliseconds() * 1000
        frames.forEach { frame ->
            require(frame.timestamp >= sessionStartMs && frame.timestamp <= sessionEndMs) {
                "Frame ${frame.number} timestamp ${frame.timestamp} is outside session bounds"
            }
        }

        // Validate memory snapshots are ordered
        val sortedMemory = memory.sortedBy { it.timestamp }
        require(memory == sortedMemory) {
            "Memory snapshots must be ordered chronologically"
        }

        // Validate summary matches actual data
        val actualFrameCount = frames.size
        val actualAverageFPS = calculateAverageFPS(frames)
        require(abs(summary.averageFPS - actualAverageFPS) < 0.1f) {
            "Summary average FPS (${summary.averageFPS}) doesn't match calculated FPS ($actualAverageFPS)"
        }
    }

    private fun calculateAverageFPS(frames: List<FrameData>): Float {
        if (frames.size < 2) return 0f

        val frameIntervals = frames.zipWithNext { current, next ->
            (next.timestamp - current.timestamp) / 1000f // Convert to milliseconds
        }

        val averageFrameTime = frameIntervals.average().toFloat()
        return if (averageFrameTime > 0) 1000f / averageFrameTime else 0f
    }

    /**
     * Session duration in milliseconds
     */
    val sessionDuration: Long
        get() = sessionEnd.toEpochMilliseconds() - sessionStart.toEpochMilliseconds()

    /**
     * Total number of frames captured
     */
    val frameCount: Int
        get() = frames.size

    /**
     * Gets frame data within a specific time range
     */
    fun getFramesInRange(startTime: Long, endTime: Long): List<FrameData> {
        return frames.filter { it.timestamp >= startTime && it.timestamp <= endTime }
    }

    /**
     * Gets performance statistics for a specific time window
     */
    fun getStatisticsForWindow(windowStartMs: Long, windowSizeMs: Long): WindowStatistics {
        val windowEndMs = windowStartMs + windowSizeMs * 1000 // Convert to microseconds
        val windowFrames = frames.filter {
            it.timestamp >= windowStartMs && it.timestamp < windowEndMs
        }

        if (windowFrames.isEmpty()) {
            return WindowStatistics.empty()
        }

        val frameTimes = windowFrames.zipWithNext { current, next ->
            (next.timestamp - current.timestamp) / 1000f // Convert to milliseconds
        }

        val averageFrameTime = frameTimes.average().toFloat()
        val fps = if (averageFrameTime > 0) 1000f / averageFrameTime else 0f

        return WindowStatistics(
            frameCount = windowFrames.size,
            averageFPS = fps,
            minFrameTime = frameTimes.minOrNull() ?: 0f,
            maxFrameTime = frameTimes.maxOrNull() ?: 0f,
            averageDrawCalls = windowFrames.map { it.drawCalls }.average().toFloat(),
            averageTriangles = windowFrames.map { it.triangles }.average().toFloat(),
            averageTextureMemory = windowFrames.map { it.textureMemory }.average().toLong(),
            averageBufferMemory = windowFrames.map { it.bufferMemory }.average().toLong()
        )
    }

    /**
     * Finds performance bottlenecks in the profile
     */
    fun findBottlenecks(): List<PerformanceBottleneck> {
        val bottlenecks = mutableListOf<PerformanceBottleneck>()

        // Analyze frame times for stutters
        val frameTimes = frames.zipWithNext { current, next ->
            (next.timestamp - current.timestamp) / 1000f
        }

        val averageFrameTime = frameTimes.average().toFloat()
        val stutterThreshold = averageFrameTime * 2.0f

        frameTimes.forEachIndexed { index, frameTime ->
            if (frameTime > stutterThreshold) {
                bottlenecks.add(PerformanceBottleneck(
                    type = BottleneckType.FRAME_STUTTER,
                    description = "Frame ${index + 1} took ${frameTime}ms (${stutterThreshold}ms threshold)",
                    severity = if (frameTime > stutterThreshold * 2) BottleneckSeverity.HIGH else BottleneckSeverity.MEDIUM,
                    timestamp = frames[index].timestamp,
                    metrics = mapOf(
                        "frameTime" to frameTime,
                        "threshold" to stutterThreshold
                    )
                ))
            }
        }

        // Analyze memory usage for spikes
        val memoryUsages = memory.map { it.totalUsedMB }
        if (memoryUsages.isNotEmpty()) {
            val averageMemory = memoryUsages.average().toFloat()
            val memoryThreshold = averageMemory * 1.5f

            memory.forEach { snapshot ->
                if (snapshot.totalUsedMB > memoryThreshold) {
                    bottlenecks.add(PerformanceBottleneck(
                        type = BottleneckType.MEMORY_SPIKE,
                        description = "Memory usage spike: ${snapshot.totalUsedMB}MB (${memoryThreshold}MB threshold)",
                        severity = if (snapshot.totalUsedMB > memoryThreshold * 1.5f) BottleneckSeverity.HIGH else BottleneckSeverity.MEDIUM,
                        timestamp = snapshot.timestamp,
                        metrics = mapOf(
                            "memoryUsage" to snapshot.totalUsedMB,
                            "threshold" to memoryThreshold
                        )
                    ))
                }
            }
        }

        // Analyze draw calls for excessive batching issues
        val drawCalls = frames.map { it.drawCalls }
        val averageDrawCalls = drawCalls.average().toFloat()
        val drawCallThreshold = averageDrawCalls * 2.0f

        frames.forEach { frame ->
            if (frame.drawCalls > drawCallThreshold) {
                bottlenecks.add(PerformanceBottleneck(
                    type = BottleneckType.EXCESSIVE_DRAW_CALLS,
                    description = "Excessive draw calls: ${frame.drawCalls} (${drawCallThreshold.toInt()} threshold)",
                    severity = BottleneckSeverity.MEDIUM,
                    timestamp = frame.timestamp,
                    metrics = mapOf(
                        "drawCalls" to frame.drawCalls.toFloat(),
                        "threshold" to drawCallThreshold
                    )
                ))
            }
        }

        return bottlenecks.sortedByDescending { it.severity.ordinal }
    }

    /**
     * Generates optimization recommendations based on the profile data
     */
    fun generateRecommendations(): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()
        val bottlenecks = findBottlenecks()

        // Frame rate recommendations
        if (summary.averageFPS < 30f) {
            recommendations.add(OptimizationRecommendation(
                category = OptimizationCategory.FRAME_RATE,
                priority = RecommendationPriority.HIGH,
                title = "Low Frame Rate Detected",
                description = "Average FPS is ${summary.averageFPS}, which is below the 30 FPS minimum threshold",
                actions = listOf(
                    "Reduce polygon count in complex models",
                    "Optimize shader complexity",
                    "Implement level-of-detail (LOD) systems",
                    "Use occlusion culling to reduce off-screen rendering"
                )
            ))
        }

        // Memory recommendations
        val averageMemoryUsage = memory.map { it.totalUsedMB }.average().toFloat()
        if (averageMemoryUsage > 500f) { // 500MB threshold
            recommendations.add(OptimizationRecommendation(
                category = OptimizationCategory.MEMORY,
                priority = RecommendationPriority.MEDIUM,
                title = "High Memory Usage",
                description = "Average memory usage is ${averageMemoryUsage}MB, consider optimization",
                actions = listOf(
                    "Implement texture compression",
                    "Use object pooling for frequently created objects",
                    "Optimize mesh data structures",
                    "Implement texture streaming for large assets"
                )
            ))
        }

        // Draw call recommendations
        val averageDrawCalls = frames.map { it.drawCalls }.average().toFloat()
        if (averageDrawCalls > 1000f) {
            recommendations.add(OptimizationRecommendation(
                category = OptimizationCategory.RENDERING,
                priority = RecommendationPriority.MEDIUM,
                title = "High Draw Call Count",
                description = "Average draw calls per frame: ${averageDrawCalls.toInt()}",
                actions = listOf(
                    "Implement instanced rendering for similar objects",
                    "Use texture atlases to reduce material switches",
                    "Implement static batching for static geometry",
                    "Consider using compute shaders for particle systems"
                )
            ))
        }

        // Stutter recommendations
        val stutterEvents = bottlenecks.filter { it.type == BottleneckType.FRAME_STUTTER }
        if (stutterEvents.isNotEmpty()) {
            recommendations.add(OptimizationRecommendation(
                category = OptimizationCategory.FRAME_RATE,
                priority = RecommendationPriority.HIGH,
                title = "Frame Stuttering Detected",
                description = "${stutterEvents.size} frame stutter events detected",
                actions = listOf(
                    "Implement frame pacing to maintain consistent timing",
                    "Move heavy operations off the main thread",
                    "Implement progressive loading for large assets",
                    "Use coroutines for long-running calculations"
                )
            ))
        }

        return recommendations.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Exports profile data in a specific format
     */
    fun export(format: ExportFormat): String {
        return when (format) {
            ExportFormat.JSON -> exportAsJSON()
            ExportFormat.CSV -> exportAsCSV()
            ExportFormat.CHROME_TRACING -> exportAsChromeTracing()
        }
    }

    private fun exportAsJSON(): String {
        // Simplified JSON export - would use proper JSON serialization
        return """
        {
            "id": "$id",
            "sessionStart": "$sessionStart",
            "sessionEnd": "$sessionEnd",
            "platform": "$platform",
            "frameCount": $frameCount,
            "sessionDuration": $sessionDuration,
            "summary": {
                "averageFPS": ${summary.averageFPS},
                "percentile95FPS": ${summary.percentile95FPS},
                "percentile99FPS": ${summary.percentile99FPS},
                "maxFrameTime": ${summary.maxFrameTime},
                "totalDrawCalls": ${summary.totalDrawCalls},
                "peakMemoryUsage": ${summary.peakMemoryUsage}
            }
        }
        """.trimIndent()
    }

    private fun exportAsCSV(): String {
        val header = "frame,timestamp,cpuTime,gpuTime,drawCalls,triangles,textureMemory,bufferMemory\n"
        val frameData = frames.joinToString("\n") { frame ->
            "${frame.number},${frame.timestamp},${frame.cpuTime},${frame.gpuTime},${frame.drawCalls},${frame.triangles},${frame.textureMemory},${frame.bufferMemory}"
        }
        return header + frameData
    }

    private fun exportAsChromeTracing(): String {
        // Simplified Chrome Tracing format
        val traceEvents = frames.map { frame ->
            """{"name":"Frame","cat":"rendering","ph":"X","ts":${frame.timestamp},"dur":${frame.cpuTime * 1000},"pid":1,"tid":1}"""
        }.joinToString(",")

        return """{"traceEvents":[$traceEvents]}"""
    }

    companion object {
        /**
         * Creates a performance profile from a profiling session
         */
        fun fromSession(
            sessionStart: Instant,
            sessionEnd: Instant,
            platform: RuntimePlatform,
            frames: List<FrameData>,
            memory: List<MemorySnapshot>,
            events: List<PerformanceEvent>,
            configuration: ProfilingConfiguration
        ): PerformanceProfile {
            val summary = calculateSummary(frames, memory)
            val metadata = ProfilingMetadata.createDefault()

            return PerformanceProfile(
                sessionStart = sessionStart,
                sessionEnd = sessionEnd,
                platform = platform,
                frames = frames,
                memory = memory,
                events = events,
                summary = summary,
                configuration = configuration,
                metadata = metadata
            )
        }

        private fun calculateSummary(frames: List<FrameData>, memory: List<MemorySnapshot>): PerformanceSummary {
            if (frames.isEmpty()) {
                return PerformanceSummary(
                    averageFPS = 0f,
                    percentile95FPS = 0f,
                    percentile99FPS = 0f,
                    maxFrameTime = 0f,
                    totalDrawCalls = 0L,
                    peakMemoryUsage = 0L
                )
            }

            // Calculate frame times
            val frameTimes = frames.zipWithNext { current, next ->
                (next.timestamp - current.timestamp) / 1000f // Convert to milliseconds
            }

            val sortedFrameTimes = frameTimes.sorted()
            val averageFrameTime = frameTimes.average().toFloat()
            val averageFPS = if (averageFrameTime > 0) 1000f / averageFrameTime else 0f

            val percentile95FrameTime = if (sortedFrameTimes.isNotEmpty()) {
                sortedFrameTimes[(sortedFrameTimes.size * 0.95).toInt().coerceAtMost(sortedFrameTimes.size - 1)]
            } else 0f
            val percentile95FPS = if (percentile95FrameTime > 0) 1000f / percentile95FrameTime else 0f

            val percentile99FrameTime = if (sortedFrameTimes.isNotEmpty()) {
                sortedFrameTimes[(sortedFrameTimes.size * 0.99).toInt().coerceAtMost(sortedFrameTimes.size - 1)]
            } else 0f
            val percentile99FPS = if (percentile99FrameTime > 0) 1000f / percentile99FrameTime else 0f

            val maxFrameTime = frameTimes.maxOrNull() ?: 0f
            val totalDrawCalls = frames.sumOf { it.drawCalls.toLong() }
            val peakMemoryUsage = memory.maxOfOrNull { it.totalUsedMB.toLong() } ?: 0L

            return PerformanceSummary(
                averageFPS = averageFPS,
                percentile95FPS = percentile95FPS,
                percentile99FPS = percentile99FPS,
                maxFrameTime = maxFrameTime,
                totalDrawCalls = totalDrawCalls,
                peakMemoryUsage = peakMemoryUsage
            )
        }

        /**
         * Creates an empty performance profile for testing
         */
        fun createEmpty(platform: RuntimePlatform): PerformanceProfile {
            val now = kotlinx.datetime.Clock.System.now()
            val dummyFrame = FrameData(
                number = 1,
                timestamp = now.toEpochMilliseconds() * 1000,
                cpuTime = 16.67f,
                gpuTime = 16.67f,
                drawCalls = 100,
                triangles = 10000,
                textureMemory = 50 * 1024 * 1024,
                bufferMemory = 10 * 1024 * 1024
            )

            return PerformanceProfile(
                sessionStart = now,
                sessionEnd = now,
                platform = platform,
                frames = listOf(dummyFrame),
                memory = emptyList(),
                events = emptyList(),
                summary = PerformanceSummary(
                    averageFPS = 60f,
                    percentile95FPS = 60f,
                    percentile99FPS = 60f,
                    maxFrameTime = 16.67f,
                    totalDrawCalls = 100L,
                    peakMemoryUsage = 60L
                ),
                configuration = ProfilingConfiguration.default(),
                metadata = ProfilingMetadata.createDefault()
            )
        }
    }
}

/**
 * FrameData - Performance data for a single frame
 */
@Serializable
data class FrameData(
    val number: Long,
    val timestamp: Long, // microseconds since epoch
    val cpuTime: Float, // milliseconds
    val gpuTime: Float, // milliseconds
    val drawCalls: Int,
    val triangles: Int,
    val textureMemory: Long, // bytes
    val bufferMemory: Long // bytes
) {
    init {
        require(number >= 0) { "Frame number must be non-negative" }
        require(timestamp >= 0) { "Frame timestamp must be non-negative" }
        require(cpuTime >= 0) { "CPU time must be non-negative" }
        require(gpuTime >= 0) { "GPU time must be non-negative" }
        require(drawCalls >= 0) { "Draw calls must be non-negative" }
        require(triangles >= 0) { "Triangle count must be non-negative" }
        require(textureMemory >= 0) { "Texture memory must be non-negative" }
        require(bufferMemory >= 0) { "Buffer memory must be non-negative" }
    }

    /**
     * Total frame time (CPU + GPU time)
     */
    val totalFrameTime: Float
        get() = cpuTime + gpuTime

    /**
     * Frame rate based on CPU time
     */
    val frameRate: Float
        get() = if (cpuTime > 0) 1000f / cpuTime else 0f

    /**
     * Total memory usage for this frame
     */
    val totalMemory: Long
        get() = textureMemory + bufferMemory

    /**
     * Triangles per draw call ratio
     */
    val trianglesPerDrawCall: Float
        get() = if (drawCalls > 0) triangles.toFloat() / drawCalls.toFloat() else 0f
}

/**
 * MemorySnapshot - Memory usage at a specific point in time
 */
@Serializable
data class MemorySnapshot(
    val timestamp: Long, // microseconds since epoch
    val totalUsedMB: Float,
    val heapUsedMB: Float,
    val nativeUsedMB: Float,
    val gpuUsedMB: Float,
    val textureMemoryMB: Float,
    val bufferMemoryMB: Float,
    val allocationsPerSecond: Int = 0,
    val gcEvents: Int = 0
) {
    init {
        require(timestamp >= 0) { "Memory snapshot timestamp must be non-negative" }
        require(totalUsedMB >= 0) { "Total used memory must be non-negative" }
        require(heapUsedMB >= 0) { "Heap used memory must be non-negative" }
        require(nativeUsedMB >= 0) { "Native used memory must be non-negative" }
        require(gpuUsedMB >= 0) { "GPU used memory must be non-negative" }
        require(textureMemoryMB >= 0) { "Texture memory must be non-negative" }
        require(bufferMemoryMB >= 0) { "Buffer memory must be non-negative" }
        require(allocationsPerSecond >= 0) { "Allocations per second must be non-negative" }
        require(gcEvents >= 0) { "GC events must be non-negative" }
    }

    /**
     * Memory fragmentation ratio (0.0 = no fragmentation, 1.0 = highly fragmented)
     */
    val fragmentationRatio: Float
        get() = if (totalUsedMB > 0) {
            val usedComponents = heapUsedMB + nativeUsedMB + gpuUsedMB
            if (usedComponents > 0) {
                1.0f - (usedComponents / totalUsedMB).coerceAtMost(1.0f)
            } else 0.0f
        } else 0.0f
}

/**
 * PerformanceEvent - Significant performance-related event
 */
@Serializable
data class PerformanceEvent(
    val timestamp: Long,
    val type: EventType,
    val description: String,
    val severity: EventSeverity,
    val data: Map<String, String> = emptyMap()
) {
    init {
        require(timestamp >= 0) { "Event timestamp must be non-negative" }
        require(description.isNotBlank()) { "Event description must be non-empty" }
    }
}

/**
 * PerformanceSummary - Aggregated performance statistics
 */
@Serializable
data class PerformanceSummary(
    val averageFPS: Float,
    val percentile95FPS: Float,
    val percentile99FPS: Float,
    val maxFrameTime: Float,
    val totalDrawCalls: Long,
    val peakMemoryUsage: Long
) {
    init {
        require(averageFPS >= 0) { "Average FPS must be non-negative" }
        require(percentile95FPS >= 0) { "95th percentile FPS must be non-negative" }
        require(percentile99FPS >= 0) { "99th percentile FPS must be non-negative" }
        require(maxFrameTime >= 0) { "Max frame time must be non-negative" }
        require(totalDrawCalls >= 0) { "Total draw calls must be non-negative" }
        require(peakMemoryUsage >= 0) { "Peak memory usage must be non-negative" }
    }

    /**
     * Performance grade based on FPS and frame consistency
     */
    val performanceGrade: PerformanceGrade
        get() = when {
            averageFPS >= 60f && percentile99FPS >= 50f -> PerformanceGrade.EXCELLENT
            averageFPS >= 45f && percentile99FPS >= 30f -> PerformanceGrade.GOOD
            averageFPS >= 30f && percentile99FPS >= 20f -> PerformanceGrade.FAIR
            averageFPS >= 15f -> PerformanceGrade.POOR
            else -> PerformanceGrade.VERY_POOR
        }
}

/**
 * ProfilingConfiguration - Configuration used during profiling
 */
@Serializable
data class ProfilingConfiguration(
    val captureFrameData: Boolean = true,
    val captureMemoryData: Boolean = true,
    val captureGPUData: Boolean = true,
    val samplingRate: Int = 60, // samples per second
    val maxSessionDuration: kotlin.time.Duration = kotlin.time.Duration.parse("PT10M"), // 10 minutes
    val enableDetailedEvents: Boolean = false,
    val memorySnapshotInterval: kotlin.time.Duration = kotlin.time.Duration.parse("PT1S") // 1 second
) {
    companion object {
        fun default(): ProfilingConfiguration = ProfilingConfiguration()

        fun highFrequency(): ProfilingConfiguration = ProfilingConfiguration(
            samplingRate = 120,
            memorySnapshotInterval = kotlin.time.Duration.parse("PT0.5S"),
            enableDetailedEvents = true
        )

        fun lowOverhead(): ProfilingConfiguration = ProfilingConfiguration(
            captureGPUData = false,
            samplingRate = 30,
            memorySnapshotInterval = kotlin.time.Duration.parse("PT5S"),
            enableDetailedEvents = false
        )
    }
}

/**
 * ProfilingMetadata - Metadata about the profiling session
 */
@Serializable
data class ProfilingMetadata(
    val profilerVersion: String,
    val applicationVersion: String,
    val deviceInfo: DeviceInfo,
    val environmentInfo: Map<String, String> = emptyMap()
) {
    companion object {
        fun createDefault(): ProfilingMetadata = ProfilingMetadata(
            profilerVersion = "1.0.0",
            applicationVersion = "1.0.0",
            deviceInfo = DeviceInfo.createDefault()
        )
    }
}

/**
 * DeviceInfo - Information about the device running the application
 */
@Serializable
data class DeviceInfo(
    val deviceModel: String,
    val operatingSystem: String,
    val osVersion: String,
    val cpuModel: String,
    val cpuCores: Int,
    val totalMemoryMB: Long,
    val gpuModel: String,
    val gpuMemoryMB: Long,
    val screenResolution: String,
    val displayRefreshRate: Float
) {
    companion object {
        fun createDefault(): DeviceInfo = DeviceInfo(
            deviceModel = "Unknown",
            operatingSystem = "Unknown",
            osVersion = "Unknown",
            cpuModel = "Unknown",
            cpuCores = 1,
            totalMemoryMB = 1024,
            gpuModel = "Unknown",
            gpuMemoryMB = 256,
            screenResolution = "1920x1080",
            displayRefreshRate = 60.0f
        )
    }
}

// Supporting data classes

data class WindowStatistics(
    val frameCount: Int,
    val averageFPS: Float,
    val minFrameTime: Float,
    val maxFrameTime: Float,
    val averageDrawCalls: Float,
    val averageTriangles: Float,
    val averageTextureMemory: Long,
    val averageBufferMemory: Long
) {
    companion object {
        fun empty(): WindowStatistics = WindowStatistics(
            frameCount = 0,
            averageFPS = 0f,
            minFrameTime = 0f,
            maxFrameTime = 0f,
            averageDrawCalls = 0f,
            averageTriangles = 0f,
            averageTextureMemory = 0L,
            averageBufferMemory = 0L
        )
    }
}

data class PerformanceBottleneck(
    val type: BottleneckType,
    val description: String,
    val severity: BottleneckSeverity,
    val timestamp: Long,
    val metrics: Map<String, Float>
)

data class OptimizationRecommendation(
    val category: OptimizationCategory,
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val actions: List<String>
)

// Enums

@Serializable
enum class RuntimePlatform {
    JVM_DESKTOP, JVM_ANDROID, JAVASCRIPT_BROWSER, JAVASCRIPT_NODE,
    NATIVE_WINDOWS, NATIVE_LINUX, NATIVE_MACOS, NATIVE_IOS,
    WEBGPU, WASM
}

@Serializable
enum class EventType {
    FRAME_STUTTER, MEMORY_ALLOCATION, GARBAGE_COLLECTION,
    TEXTURE_LOAD, SHADER_COMPILATION, SCENE_LOAD,
    USER_INTERACTION, NETWORK_REQUEST, FILE_IO
}

@Serializable
enum class EventSeverity {
    INFO, WARNING, ERROR, CRITICAL
}

enum class PerformanceGrade {
    EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
}

enum class BottleneckType {
    FRAME_STUTTER, MEMORY_SPIKE, EXCESSIVE_DRAW_CALLS,
    HIGH_TRIANGLE_COUNT, SHADER_COMPLEXITY, TEXTURE_THRASHING
}

enum class BottleneckSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class OptimizationCategory {
    FRAME_RATE, MEMORY, RENDERING, CPU, GPU, LOADING
}

enum class RecommendationPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class ExportFormat {
    JSON, CSV, CHROME_TRACING
}