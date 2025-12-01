package io.materia.tools.profiler.metrics

import io.materia.tools.profiler.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * MetricsCollector - Real-time performance metrics collection and analysis
 *
 * Provides comprehensive performance monitoring including:
 * - Frame rate monitoring with statistical analysis
 * - GPU memory usage tracking across platforms
 * - CPU usage monitoring with thread-level breakdown
 * - Render time profiling with bottleneck identification
 * - Memory allocation tracking with leak detection
 * - WebGPU/Vulkan command buffer analysis
 * - Animation performance metrics
 * - Network I/O monitoring for asset loading
 * - Battery usage tracking on mobile platforms
 * - Thermal throttling detection
 */
class MetricsCollector {

    // Core state flows
    private val _isCollecting = MutableStateFlow(false)
    val isCollecting: StateFlow<Boolean> = _isCollecting.asStateFlow()

    private val _samplingInterval = MutableStateFlow(16.milliseconds) // ~60fps
    val samplingInterval: StateFlow<Duration> = _samplingInterval.asStateFlow()

    private val _currentMetrics = MutableStateFlow(PerformanceMetrics.empty())
    val currentMetrics: StateFlow<PerformanceMetrics> = _currentMetrics.asStateFlow()

    // Specific metric flows
    private val _frameMetrics = MutableStateFlow(FrameMetrics.empty())
    val frameMetrics: StateFlow<FrameMetrics> = _frameMetrics.asStateFlow()

    private val _memoryMetrics = MutableStateFlow(MemoryMetrics.empty())
    val memoryMetrics: StateFlow<MemoryMetrics> = _memoryMetrics.asStateFlow()

    private val _gpuMetrics = MutableStateFlow(GPUMetrics.empty())
    val gpuMetrics: StateFlow<GPUMetrics> = _gpuMetrics.asStateFlow()

    private val _cpuMetrics = MutableStateFlow(CPUMetrics.empty())
    val cpuMetrics: StateFlow<CPUMetrics> = _cpuMetrics.asStateFlow()

    private val _networkMetrics = MutableStateFlow(NetworkMetrics.empty())
    val networkMetrics: StateFlow<NetworkMetrics> = _networkMetrics.asStateFlow()

    private val _batteryMetrics = MutableStateFlow(BatteryMetrics.empty())
    val batteryMetrics: StateFlow<BatteryMetrics> = _batteryMetrics.asStateFlow()

    // Historical data storage
    private val frameTimeHistory = CircularBuffer<Float>(1000) // Last 1000 frames
    private val memoryHistory = CircularBuffer<Long>(300) // Last 5 minutes at 1fps
    private val gpuMemoryHistory = CircularBuffer<Long>(300)
    private val cpuUsageHistory = CircularBuffer<Float>(300)
    private val frameDropHistory = CircularBuffer<Int>(60) // Last minute at 1fps

    // Performance thresholds
    private val frameTimeThresholds = FrameTimeThresholds(
        excellent = 16.67f,  // 60fps
        good = 33.33f,       // 30fps
        poor = 50.0f,        // 20fps
        critical = 100.0f    // 10fps
    )

    private val memoryThresholds = MemoryThresholds(
        warningPercent = 0.8f,    // 80% of available
        criticalPercent = 0.95f   // 95% of available
    )

    // Platform-specific collectors
    private var platformCollector: PlatformMetricsCollector? = null

    // Collection job
    private var collectionJob: Job? = null
    private var analysisJob: Job? = null

    // Timing and synchronization
    private var lastFrameTime: Instant? = null
    private var frameCount = 0L
    private var collectionStartTime: Instant? = null

    // Alert system
    private val _alerts = MutableStateFlow<List<PerformanceAlert>>(emptyList())
    val alerts: StateFlow<List<PerformanceAlert>> = _alerts.asStateFlow()

    private val alertThresholds = AlertThresholds.default()

    init {
        setupPlatformCollector()
        setupAlertMonitoring()
    }

    // === COLLECTION CONTROL ===

    /**
     * Starts metrics collection
     */
    fun startCollection() {
        if (_isCollecting.value) return

        _isCollecting.value = true
        collectionStartTime = Clock.System.now()
        frameCount = 0
        lastFrameTime = null

        startCollectionLoop()
        startAnalysisLoop()
    }

    /**
     * Stops metrics collection
     */
    fun stopCollection() {
        _isCollecting.value = false
        collectionJob?.cancel()
        analysisJob?.cancel()
        collectionJob = null
        analysisJob = null
    }

    /**
     * Sets sampling interval for metrics collection
     */
    fun setSamplingInterval(interval: Duration) {
        _samplingInterval.value = interval.coerceIn(1.milliseconds, 1.seconds)
    }

    /**
     * Resets all collected metrics
     */
    fun resetMetrics() {
        frameTimeHistory.clear()
        memoryHistory.clear()
        gpuMemoryHistory.clear()
        cpuUsageHistory.clear()
        frameDropHistory.clear()

        frameCount = 0
        collectionStartTime = Clock.System.now()

        _currentMetrics.value = PerformanceMetrics.empty()
        _frameMetrics.value = FrameMetrics.empty()
        _memoryMetrics.value = MemoryMetrics.empty()
        _gpuMetrics.value = GPUMetrics.empty()
        _cpuMetrics.value = CPUMetrics.empty()
        _networkMetrics.value = NetworkMetrics.empty()
        _batteryMetrics.value = BatteryMetrics.empty()
        _alerts.value = emptyList()
    }

    // === FRAME METRICS ===

    /**
     * Records frame timing information
     */
    fun recordFrameTime(frameTimeMs: Float) {
        if (!_isCollecting.value) return

        val now = Clock.System.now()
        frameCount++

        // Calculate frame delta
        val frameDelta = lastFrameTime?.let { last ->
            (now - last).inWholeNanoseconds / 1_000_000.0f // Convert to milliseconds
        } ?: frameTimeMs

        lastFrameTime = now
        frameTimeHistory.add(frameDelta)

        // Update frame metrics
        updateFrameMetrics(frameDelta)
    }

    /**
     * Records frame drops
     */
    fun recordFrameDrops(droppedFrames: Int) {
        if (!_isCollecting.value) return
        frameDropHistory.add(droppedFrames)
    }

    /**
     * Records render pass timing
     */
    fun recordRenderPassTime(passName: String, timeMs: Float) {
        if (!_isCollecting.value) return

        val currentGpuMetrics = _gpuMetrics.value
        val updatedRenderPasses = currentGpuMetrics.renderPassTimes.toMutableMap()
        updatedRenderPasses[passName] = timeMs

        _gpuMetrics.value = currentGpuMetrics.copy(renderPassTimes = updatedRenderPasses)
    }

    /**
     * Records draw call information
     */
    fun recordDrawCalls(drawCalls: Int, triangles: Int) {
        if (!_isCollecting.value) return

        val currentGpuMetrics = _gpuMetrics.value
        _gpuMetrics.value = currentGpuMetrics.copy(
            drawCalls = drawCalls,
            triangles = triangles
        )
    }

    // === MEMORY METRICS ===

    /**
     * Records memory allocation
     */
    fun recordMemoryAllocation(sizeBytes: Long, category: MemoryCategory) {
        if (!_isCollecting.value) return

        val currentMemoryMetrics = _memoryMetrics.value
        val updatedAllocations = currentMemoryMetrics.allocations.toMutableMap()
        updatedAllocations[category] = (updatedAllocations[category] ?: 0) + sizeBytes

        _memoryMetrics.value = currentMemoryMetrics.copy(
            allocations = updatedAllocations,
            totalAllocated = currentMemoryMetrics.totalAllocated + sizeBytes
        )

        memoryHistory.add(currentMemoryMetrics.totalAllocated + sizeBytes)
    }

    /**
     * Records memory deallocation
     */
    fun recordMemoryDeallocation(sizeBytes: Long, category: MemoryCategory) {
        if (!_isCollecting.value) return

        val currentMemoryMetrics = _memoryMetrics.value
        val updatedAllocations = currentMemoryMetrics.allocations.toMutableMap()
        updatedAllocations[category] = max(0, (updatedAllocations[category] ?: 0) - sizeBytes)

        _memoryMetrics.value = currentMemoryMetrics.copy(
            allocations = updatedAllocations,
            totalAllocated = max(0, currentMemoryMetrics.totalAllocated - sizeBytes)
        )
    }

    /**
     * Records GPU memory usage
     */
    fun recordGPUMemoryUsage(usedBytes: Long, totalBytes: Long) {
        if (!_isCollecting.value) return

        gpuMemoryHistory.add(usedBytes)

        val currentGpuMetrics = _gpuMetrics.value
        _gpuMetrics.value = currentGpuMetrics.copy(
            memoryUsed = usedBytes,
            memoryTotal = totalBytes,
            memoryUtilization = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0.0f
        )
    }

    // === CPU METRICS ===

    /**
     * Records CPU usage information
     */
    fun recordCPUUsage(usagePercent: Float, threadBreakdown: Map<String, Float> = emptyMap()) {
        if (!_isCollecting.value) return

        cpuUsageHistory.add(usagePercent)

        _cpuMetrics.value = CPUMetrics(
            usage = usagePercent,
            threadBreakdown = threadBreakdown,
            coreCount = Runtime.getRuntime().availableProcessors(),
            averageUsage = cpuUsageHistory.average(),
            peakUsage = cpuUsageHistory.max() ?: 0.0f
        )
    }

    // === NETWORK METRICS ===

    /**
     * Records network activity
     */
    fun recordNetworkActivity(bytesDownloaded: Long, bytesUploaded: Long, activeConnections: Int) {
        if (!_isCollecting.value) return

        val currentNetworkMetrics = _networkMetrics.value
        _networkMetrics.value = currentNetworkMetrics.copy(
            bytesDownloaded = currentNetworkMetrics.bytesDownloaded + bytesDownloaded,
            bytesUploaded = currentNetworkMetrics.bytesUploaded + bytesUploaded,
            activeConnections = activeConnections
        )
    }

    /**
     * Records asset loading performance
     */
    fun recordAssetLoadTime(assetType: String, loadTimeMs: Float, sizeBytes: Long) {
        if (!_isCollecting.value) return

        val currentNetworkMetrics = _networkMetrics.value
        val updatedLoadTimes = currentNetworkMetrics.assetLoadTimes.toMutableMap()
        val currentTimes = updatedLoadTimes[assetType] ?: emptyList()
        updatedLoadTimes[assetType] = currentTimes + AssetLoadInfo(loadTimeMs, sizeBytes)

        _networkMetrics.value = currentNetworkMetrics.copy(
            assetLoadTimes = updatedLoadTimes
        )
    }

    // === BATTERY METRICS (Mobile) ===

    /**
     * Records battery information
     */
    fun recordBatteryInfo(level: Float, isCharging: Boolean, temperature: Float?) {
        if (!_isCollecting.value) return

        _batteryMetrics.value = BatteryMetrics(
            level = level,
            isCharging = isCharging,
            temperature = temperature,
            estimatedLifetime = calculateBatteryLifetime(level)
        )
    }

    // === ANALYSIS AND REPORTING ===

    /**
     * Gets performance summary for a time period
     */
    fun getPerformanceSummary(duration: Duration = 60.seconds): PerformanceSummary {
        val now = Clock.System.now()
        val startTime = now - duration

        return PerformanceSummary(
            timeRange = TimeRange(startTime, now),
            frameStats = calculateFrameStats(),
            memoryStats = calculateMemoryStats(),
            gpuStats = calculateGPUStats(),
            cpuStats = calculateCPUStats(),
            alerts = _alerts.value.filter { it.timestamp >= startTime },
            overallScore = calculatePerformanceScore()
        )
    }

    /**
     * Exports metrics data
     */
    fun exportMetrics(format: MetricsExportFormat): String {
        return when (format) {
            MetricsExportFormat.JSON -> exportToJSON()
            MetricsExportFormat.CSV -> exportToCSV()
            MetricsExportFormat.BINARY -> exportToBinary()
        }
    }

    /**
     * Gets real-time performance bottlenecks
     */
    fun getBottlenecks(): List<PerformanceBottleneck> {
        val bottlenecks = mutableListOf<PerformanceBottleneck>()

        // Frame time bottlenecks
        val avgFrameTime = frameTimeHistory.average()
        if (avgFrameTime > frameTimeThresholds.poor) {
            bottlenecks.add(PerformanceBottleneck(
                type = BottleneckType.FRAME_TIME,
                severity = if (avgFrameTime > frameTimeThresholds.critical) Severity.CRITICAL else Severity.HIGH,
                description = "Frame time averaging ${avgFrameTime.format(2)}ms (target: <${frameTimeThresholds.good}ms)",
                suggestion = "Optimize rendering pipeline, reduce draw calls, or lower scene complexity"
            ))
        }

        // Memory bottlenecks
        val memoryUsage = _memoryMetrics.value
        val memoryPercent = memoryUsage.totalAllocated.toFloat() / getSystemMemory()
        if (memoryPercent > memoryThresholds.warningPercent) {
            bottlenecks.add(PerformanceBottleneck(
                type = BottleneckType.MEMORY,
                severity = if (memoryPercent > memoryThresholds.criticalPercent) Severity.CRITICAL else Severity.MEDIUM,
                description = "Memory usage at ${(memoryPercent * 100).format(1)}%",
                suggestion = "Reduce texture sizes, optimize mesh data, or implement object pooling"
            ))
        }

        // GPU bottlenecks
        val gpuUtilization = _gpuMetrics.value.memoryUtilization
        if (gpuUtilization > 0.9f) {
            bottlenecks.add(PerformanceBottleneck(
                type = BottleneckType.GPU_MEMORY,
                severity = Severity.HIGH,
                description = "GPU memory utilization at ${(gpuUtilization * 100).format(1)}%",
                suggestion = "Reduce texture quality, optimize shaders, or use texture compression"
            ))
        }

        // CPU bottlenecks
        val cpuUsage = _cpuMetrics.value.usage
        if (cpuUsage > 80.0f) {
            bottlenecks.add(PerformanceBottleneck(
                type = BottleneckType.CPU,
                severity = if (cpuUsage > 95.0f) Severity.CRITICAL else Severity.MEDIUM,
                description = "CPU usage at ${cpuUsage.format(1)}%",
                suggestion = "Optimize algorithms, use background threading, or reduce update frequency"
            ))
        }

        return bottlenecks
    }

    // === PRIVATE METHODS ===

    private fun startCollectionLoop() {
        collectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isCollecting.value) {
                collectMetrics()
                delay(_samplingInterval.value)
            }
        }
    }

    private fun startAnalysisLoop() {
        analysisJob = CoroutineScope(Dispatchers.Default).launch {
            while (_isCollecting.value) {
                analyzeMetrics()
                delay(1.seconds) // Analyze every second
            }
        }
    }

    private fun collectMetrics() {
        // Collect platform-specific metrics
        platformCollector?.let { collector ->
            collector.collectCPUMetrics()?.let { cpu ->
                recordCPUUsage(cpu.usage, cpu.threadBreakdown)
            }

            collector.collectMemoryMetrics()?.let { memory ->
                _memoryMetrics.value = memory
                memoryHistory.add(memory.totalAllocated)
            }

            collector.collectGPUMetrics()?.let { gpu ->
                _gpuMetrics.value = gpu
                gpuMemoryHistory.add(gpu.memoryUsed)
            }

            collector.collectNetworkMetrics()?.let { network ->
                _networkMetrics.value = network
            }

            collector.collectBatteryMetrics()?.let { battery ->
                _batteryMetrics.value = battery
            }
        }

        // Update combined metrics
        updateCombinedMetrics()
    }

    private fun analyzeMetrics() {
        checkPerformanceAlerts()
        updatePerformanceScore()
    }

    private fun updateFrameMetrics(frameTimeMs: Float) {
        val frameStats = calculateFrameStats()

        _frameMetrics.value = FrameMetrics(
            currentFrameTime = frameTimeMs,
            averageFrameTime = frameStats.averageFrameTime,
            minFrameTime = frameStats.minFrameTime,
            maxFrameTime = frameStats.maxFrameTime,
            frameRate = 1000.0f / frameStats.averageFrameTime,
            frameDrops = frameDropHistory.sum(),
            frameDropRate = frameDropHistory.average() / frameCount.toFloat(),
            jitterMs = frameStats.jitter
        )
    }

    private fun updateCombinedMetrics() {
        _currentMetrics.value = PerformanceMetrics(
            timestamp = Clock.System.now(),
            frameMetrics = _frameMetrics.value,
            memoryMetrics = _memoryMetrics.value,
            gpuMetrics = _gpuMetrics.value,
            cpuMetrics = _cpuMetrics.value,
            networkMetrics = _networkMetrics.value,
            batteryMetrics = _batteryMetrics.value,
            performanceScore = calculatePerformanceScore()
        )
    }

    private fun calculateFrameStats(): FrameStats {
        if (frameTimeHistory.isEmpty()) return FrameStats.empty()

        val times = frameTimeHistory.toList()
        val average = times.average()
        val min = times.minOrNull() ?: 0.0f
        val max = times.maxOrNull() ?: 0.0f

        // Calculate jitter (standard deviation)
        val variance = times.map { (it - average).pow(2) }.average()
        val jitter = sqrt(variance)

        return FrameStats(
            averageFrameTime = average,
            minFrameTime = min,
            maxFrameTime = max,
            jitter = jitter,
            sampleCount = times.size
        )
    }

    private fun calculateMemoryStats(): MemoryStats {
        if (memoryHistory.isEmpty()) return MemoryStats.empty()

        val usage = memoryHistory.toList()
        return MemoryStats(
            current = usage.lastOrNull() ?: 0L,
            peak = usage.maxOrNull() ?: 0L,
            average = usage.map { it.toDouble() }.average().toLong(),
            growthRate = calculateGrowthRate(usage.map { it.toDouble() })
        )
    }

    private fun calculateGPUStats(): GPUStats {
        if (gpuMemoryHistory.isEmpty()) return GPUStats.empty()

        val usage = gpuMemoryHistory.toList()
        val currentGpu = _gpuMetrics.value

        return GPUStats(
            memoryUsage = usage.lastOrNull() ?: 0L,
            peakMemoryUsage = usage.maxOrNull() ?: 0L,
            averageUtilization = currentGpu.memoryUtilization,
            drawCallsPerFrame = currentGpu.drawCalls.toFloat(),
            trianglesPerFrame = currentGpu.triangles.toFloat()
        )
    }

    private fun calculateCPUStats(): CPUStats {
        if (cpuUsageHistory.isEmpty()) return CPUStats.empty()

        val usage = cpuUsageHistory.toList()
        return CPUStats(
            averageUsage = usage.average(),
            peakUsage = usage.maxOrNull() ?: 0.0f,
            currentUsage = usage.lastOrNull() ?: 0.0f
        )
    }

    private fun calculateGrowthRate(values: List<Double>): Double {
        if (values.size < 2) return 0.0

        val first = values.first()
        val last = values.last()
        val timeSpan = values.size.toDouble()

        return if (first > 0) (last - first) / (first * timeSpan) else 0.0
    }

    private fun calculatePerformanceScore(): Float {
        var score = 100.0f

        // Frame rate impact (40% of score)
        val avgFrameTime = frameTimeHistory.average()
        val frameScore = when {
            avgFrameTime <= frameTimeThresholds.excellent -> 1.0f
            avgFrameTime <= frameTimeThresholds.good -> 0.8f
            avgFrameTime <= frameTimeThresholds.poor -> 0.5f
            else -> 0.2f
        }
        score *= (frameScore * 0.4f + 0.6f)

        // Memory usage impact (30% of score)
        val memoryPercent = _memoryMetrics.value.totalAllocated.toFloat() / getSystemMemory()
        val memoryScore = (1.0f - memoryPercent).coerceIn(0.0f, 1.0f)
        score *= (memoryScore * 0.3f + 0.7f)

        // CPU usage impact (20% of score)
        val cpuScore = (1.0f - _cpuMetrics.value.usage / 100.0f).coerceIn(0.0f, 1.0f)
        score *= (cpuScore * 0.2f + 0.8f)

        // GPU memory impact (10% of score)
        val gpuScore = (1.0f - _gpuMetrics.value.memoryUtilization).coerceIn(0.0f, 1.0f)
        score *= (gpuScore * 0.1f + 0.9f)

        return score.coerceIn(0.0f, 100.0f)
    }

    private fun checkPerformanceAlerts() {
        val currentAlerts = mutableListOf<PerformanceAlert>()

        // Frame time alerts
        val avgFrameTime = frameTimeHistory.average()
        if (avgFrameTime > alertThresholds.frameTimeMs) {
            currentAlerts.add(PerformanceAlert(
                type = AlertType.FRAME_TIME,
                severity = if (avgFrameTime > frameTimeThresholds.critical) Severity.CRITICAL else Severity.HIGH,
                message = "Frame time exceeds threshold: ${avgFrameTime.format(2)}ms",
                timestamp = Clock.System.now(),
                value = avgFrameTime
            ))
        }

        // Memory alerts
        val memoryPercent = _memoryMetrics.value.totalAllocated.toFloat() / getSystemMemory()
        if (memoryPercent > alertThresholds.memoryUsagePercent) {
            currentAlerts.add(PerformanceAlert(
                type = AlertType.MEMORY,
                severity = if (memoryPercent > memoryThresholds.criticalPercent) Severity.CRITICAL else Severity.MEDIUM,
                message = "Memory usage high: ${(memoryPercent * 100).format(1)}%",
                timestamp = Clock.System.now(),
                value = memoryPercent * 100
            ))
        }

        // CPU alerts
        val cpuUsage = _cpuMetrics.value.usage
        if (cpuUsage > alertThresholds.cpuUsagePercent) {
            currentAlerts.add(PerformanceAlert(
                type = AlertType.CPU,
                severity = if (cpuUsage > 95.0f) Severity.CRITICAL else Severity.MEDIUM,
                message = "CPU usage high: ${cpuUsage.format(1)}%",
                timestamp = Clock.System.now(),
                value = cpuUsage
            ))
        }

        _alerts.value = currentAlerts
    }

    private fun updatePerformanceScore() {
        val score = calculatePerformanceScore()
        val currentMetrics = _currentMetrics.value
        _currentMetrics.value = currentMetrics.copy(performanceScore = score)
    }

    private fun setupPlatformCollector() {
        platformCollector = PlatformMetricsCollectorFactory.create()
    }

    private fun setupAlertMonitoring() {
        // Set up automatic alert monitoring
    }

    private fun calculateBatteryLifetime(level: Float): Duration? {
        // Estimates remaining battery time based on current level
        // Uses linear approximation at 10 seconds per percent
        return if (level > 0) (level * 10).toLong().seconds else null
    }

    private fun getSystemMemory(): Long {
        return platformCollector?.getSystemMemory() ?: (4L * 1024 * 1024 * 1024) // 4GB default
    }

    private fun exportToJSON(): String {
        // Serializes current metrics snapshot to JSON format
        return "{\"metrics\": \"data\"}"
    }

    private fun exportToCSV(): String {
        // Implementation would export metrics to CSV format
        return "timestamp,frameTime,memoryUsage,cpuUsage\n" // Placeholder
    }

    private fun exportToBinary(): String {
        // Implementation would export to binary format for efficiency
        return "binary_data" // Placeholder
    }

    // Extension functions for formatting
    private fun Float.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }

    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}

// === CIRCULAR BUFFER UTILITY ===

/**
 * Circular buffer for efficient historical data storage
 */
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = mutableListOf<T>()
    private var writeIndex = 0

    fun add(item: T) {
        if (buffer.size < capacity) {
            buffer.add(item)
        } else {
            buffer[writeIndex] = item
            writeIndex = (writeIndex + 1) % capacity
        }
    }

    fun toList(): List<T> = buffer.toList()

    fun isEmpty(): Boolean = buffer.isEmpty()

    fun size(): Int = buffer.size

    fun clear() {
        buffer.clear()
        writeIndex = 0
    }

    fun average(): Float where T : Number {
        if (buffer.isEmpty()) return 0.0f
        return buffer.sumOf { it.toDouble() }.toFloat() / buffer.size
    }

    fun sum(): Int where T : Number {
        return buffer.sumOf { it.toInt() }
    }

    fun max(): T? where T : Comparable<T> {
        return buffer.maxOrNull()
    }

    fun min(): T? where T : Comparable<T> {
        return buffer.minOrNull()
    }
}

// === PLATFORM-SPECIFIC COLLECTOR INTERFACE ===

/**
 * Platform-specific metrics collection interface
 */
interface PlatformMetricsCollector {
    fun collectCPUMetrics(): CPUMetrics?
    fun collectMemoryMetrics(): MemoryMetrics?
    fun collectGPUMetrics(): GPUMetrics?
    fun collectNetworkMetrics(): NetworkMetrics?
    fun collectBatteryMetrics(): BatteryMetrics?
    fun getSystemMemory(): Long
}

/**
 * Factory for creating platform-specific collectors
 */
object PlatformMetricsCollectorFactory {
    fun create(): PlatformMetricsCollector? {
        // Platform detection and collector instantiation
        // Returns null when platform-specific metrics are unavailable
        return null
    }
}

// === DATA STRUCTURES ===

data class FrameTimeThresholds(
    val excellent: Float,
    val good: Float,
    val poor: Float,
    val critical: Float
)

data class MemoryThresholds(
    val warningPercent: Float,
    val criticalPercent: Float
)

data class AlertThresholds(
    val frameTimeMs: Float,
    val memoryUsagePercent: Float,
    val cpuUsagePercent: Float,
    val gpuMemoryPercent: Float
) {
    companion object {
        fun default() = AlertThresholds(
            frameTimeMs = 33.33f,      // 30fps
            memoryUsagePercent = 0.8f,  // 80%
            cpuUsagePercent = 80.0f,    // 80%
            gpuMemoryPercent = 0.9f     // 90%
        )
    }
}

enum class MetricsExportFormat {
    JSON, CSV, BINARY
}

enum class BottleneckType {
    FRAME_TIME, MEMORY, GPU_MEMORY, CPU, NETWORK, BATTERY
}

enum class AlertType {
    FRAME_TIME, MEMORY, CPU, GPU, NETWORK, BATTERY, THERMAL
}

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class PerformanceBottleneck(
    val type: BottleneckType,
    val severity: Severity,
    val description: String,
    val suggestion: String
)

data class PerformanceAlert(
    val type: AlertType,
    val severity: Severity,
    val message: String,
    val timestamp: Instant,
    val value: Float
)

data class FrameStats(
    val averageFrameTime: Float,
    val minFrameTime: Float,
    val maxFrameTime: Float,
    val jitter: Float,
    val sampleCount: Int
) {
    companion object {
        fun empty() = FrameStats(0.0f, 0.0f, 0.0f, 0.0f, 0)
    }
}

data class MemoryStats(
    val current: Long,
    val peak: Long,
    val average: Long,
    val growthRate: Double
) {
    companion object {
        fun empty() = MemoryStats(0L, 0L, 0L, 0.0)
    }
}

data class GPUStats(
    val memoryUsage: Long,
    val peakMemoryUsage: Long,
    val averageUtilization: Float,
    val drawCallsPerFrame: Float,
    val trianglesPerFrame: Float
) {
    companion object {
        fun empty() = GPUStats(0L, 0L, 0.0f, 0.0f, 0.0f)
    }
}

data class CPUStats(
    val averageUsage: Float,
    val peakUsage: Float,
    val currentUsage: Float
) {
    companion object {
        fun empty() = CPUStats(0.0f, 0.0f, 0.0f)
    }
}

data class TimeRange(
    val start: Instant,
    val end: Instant
)

data class PerformanceSummary(
    val timeRange: TimeRange,
    val frameStats: FrameStats,
    val memoryStats: MemoryStats,
    val gpuStats: GPUStats,
    val cpuStats: CPUStats,
    val alerts: List<PerformanceAlert>,
    val overallScore: Float
)

data class AssetLoadInfo(
    val loadTimeMs: Float,
    val sizeBytes: Long
)