package io.materia.profiling

import io.materia.renderer.Renderer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

/**
 * Performance metric types
 */
enum class MetricType {
    FRAME_TIME,
    FPS,
    GPU_TIME,
    CPU_TIME,
    DRAW_CALLS,
    TRIANGLES,
    VERTICES,
    TEXTURES,
    SHADERS,
    STATE_CHANGES,
    MEMORY_USAGE,
    BUFFER_UPLOADS
}

/**
 * Performance metric data point
 */
data class MetricDataPoint(
    val timestamp: Long,
    val value: Float
)

/**
 * Performance metric with history
 */
class PerformanceMetric(
    val type: MetricType,
    val historySize: Int = 120 // 2 seconds at 60 FPS
) {
    private val history = mutableListOf<MetricDataPoint>()
    private var currentValue: Float = 0f
    private var minValue: Float = Float.MAX_VALUE
    private var maxValue: Float = Float.MIN_VALUE
    private var sumValue: Float = 0f
    private var countValue: Int = 0

    fun record(value: Float, timestamp: Long = getTimeNanos()) {
        currentValue = value
        minValue = min(minValue, value)
        maxValue = max(maxValue, value)
        sumValue += value
        countValue++

        val dataPoint = MetricDataPoint(timestamp, value)
        history.add(dataPoint)

        // Keep history size limited
        while (history.size > historySize) {
            history.removeAt(0)
        }
    }

    fun getCurrentValue(): Float = currentValue
    fun getMinValue(): Float = if (minValue == Float.MAX_VALUE) 0f else minValue
    fun getMaxValue(): Float = if (maxValue == Float.MIN_VALUE) 0f else maxValue
    fun getAverageValue(): Float = if (countValue > 0) sumValue / countValue else 0f
    fun getHistory(): List<MetricDataPoint> = history.toList()

    fun reset() {
        currentValue = 0f
        minValue = Float.MAX_VALUE
        maxValue = Float.MIN_VALUE
        sumValue = 0f
        countValue = 0
        history.clear()
    }
}

/**
 * Timer for CPU measurements
 */
class CPUTimer {
    private var startTime: Long = 0L
    private var endTime: Long = 0L
    private var isRunning: Boolean = false

    fun start() {
        startTime = getTimeNanos()
        isRunning = true
    }

    fun stop() {
        if (isRunning) {
            endTime = getTimeNanos()
            isRunning = false
        }
    }

    fun getElapsedTimeMs(): Float {
        return if (isRunning) {
            (getTimeNanos() - startTime) / 1_000_000f
        } else {
            (endTime - startTime) / 1_000_000f
        }
    }
}

/**
 * Timer for GPU measurements (stub implementation)
 */
class GPUTimer(private val renderer: Renderer) {
    private var startTimeNs: Long = 0L
    private var lastMeasuredMs: Float = 0f
    private var startStatsTimestamp: Long = 0L
    private var startFrameTime: Double = 0.0
    private var isRunning: Boolean = false

    fun start() {
        val stats = renderer.stats
        startTimeNs = getTimeNanos()
        startStatsTimestamp = stats.timestamp
        startFrameTime = stats.frameTime
        isRunning = true
    }

    fun stop() {
        if (!isRunning) return

        val stats = renderer.stats
        val elapsedNs = getTimeNanos() - startTimeNs
        val statsDeltaMs = if (stats.timestamp > startStatsTimestamp) {
            (stats.timestamp - startStatsTimestamp).toFloat()
        } else {
            0f
        }
        val frameDeltaMs = (stats.frameTime - startFrameTime).toFloat()

        lastMeasuredMs = when {
            statsDeltaMs > 0f && frameDeltaMs > 0f -> minOf(statsDeltaMs, frameDeltaMs)
            statsDeltaMs > 0f -> statsDeltaMs
            frameDeltaMs > 0f -> frameDeltaMs
            else -> elapsedNs / 1_000_000f
        }
        isRunning = false
    }

    fun getElapsedTimeMs(): Float {
        return if (isRunning) {
            (getTimeNanos() - startTimeNs) / 1_000_000f
        } else {
            lastMeasuredMs
        }
    }
}

/**
 * Performance bottleneck types
 */
enum class Bottleneck {
    UNKNOWN,
    CPU_BOUND,
    GPU_BOUND,
    MEMORY_BOUND,
    IO_BOUND,
    DRAW_CALL_BOUND
}

/**
 * Performance data aggregate
 */
data class PerformanceData(
    val fps: Float = 0f,
    val frameTime: Float = 0f,
    val cpuTime: Float = 0f,
    val gpuTime: Float = 0f,
    val drawCalls: Int = 0,
    val triangles: Int = 0,
    val memoryUsage: Long = 0L,
    val bottleneck: Bottleneck = Bottleneck.UNKNOWN
)

/**
 * Real-time performance monitoring and profiling
 */
class PerformanceMonitor(private val renderer: Renderer) {
    private val metrics = mutableMapOf<MetricType, PerformanceMetric>()
    private var currentFrame = 0L
    private var frameStartTime = 0L
    private var lastFrameTime = 0L
    private val cpuTimer = CPUTimer()
    private val gpuTimer = GPUTimer(renderer)
    private val _performanceFlow = MutableStateFlow(PerformanceData())
    val performanceFlow: StateFlow<PerformanceData> = _performanceFlow.asStateFlow()
    private val _bottleneckFlow = MutableStateFlow(Bottleneck.UNKNOWN)
    val bottleneckFlow: StateFlow<Bottleneck> = _bottleneckFlow.asStateFlow()
    private val monitorScope = CoroutineScope(Dispatchers.Default)

    init {
        // Initialize metrics
        MetricType.values().forEach { type ->
            metrics[type] = PerformanceMetric(type)
        }
    }

    /**
     * Start frame measurement
     */
    fun beginFrame() {
        currentFrame++
        frameStartTime = getTimeNanos()
        cpuTimer.start()
        gpuTimer.start()
    }

    /**
     * End frame measurement
     */
    fun endFrame() {
        cpuTimer.stop()
        gpuTimer.stop()
        val frameEndTime = getTimeNanos()
        val frameDuration = (frameEndTime - frameStartTime) / 1_000_000f // Convert to ms

        // Calculate FPS
        val fps = if (frameDuration > 0) 1000f / frameDuration else 0f

        // Record metrics
        recordMetric(MetricType.FRAME_TIME, frameDuration)
        recordMetric(MetricType.FPS, fps)
        recordMetric(MetricType.CPU_TIME, cpuTimer.getElapsedTimeMs())
        recordMetric(MetricType.GPU_TIME, gpuTimer.getElapsedTimeMs())

        // Update performance data
        updatePerformanceData()

        // Analyze bottlenecks
        analyzeBottlenecks()

        lastFrameTime = frameEndTime
    }

    /**
     * Record a metric value
     */
    fun recordMetric(type: MetricType, value: Float) {
        metrics[type]?.record(value)
    }

    /**
     * Record rendering statistics
     */
    fun recordRenderingStats(drawCalls: Int, triangles: Int, vertices: Int) {
        recordMetric(MetricType.DRAW_CALLS, drawCalls.toFloat())
        recordMetric(MetricType.TRIANGLES, triangles.toFloat())
        recordMetric(MetricType.VERTICES, vertices.toFloat())
    }

    /**
     * Record memory usage
     */
    fun recordMemoryUsage(bytes: Long) {
        recordMetric(MetricType.MEMORY_USAGE, bytes.toFloat())
    }

    /**
     * Get metric by type
     */
    fun getMetric(type: MetricType): PerformanceMetric? = metrics[type]

    /**
     * Get all metrics
     */
    fun getAllMetrics(): Map<MetricType, PerformanceMetric> = metrics.toMap()

    /**
     * Reset all metrics
     */
    fun reset() {
        metrics.values.forEach { it.reset() }
        currentFrame = 0L
        frameStartTime = 0L
        lastFrameTime = 0L
    }

    /**
     * Update performance data flow
     */
    private fun updatePerformanceData() {
        val performanceData = PerformanceData(
            fps = metrics[MetricType.FPS]?.getCurrentValue() ?: 0f,
            frameTime = metrics[MetricType.FRAME_TIME]?.getCurrentValue() ?: 0f,
            cpuTime = metrics[MetricType.CPU_TIME]?.getCurrentValue() ?: 0f,
            gpuTime = metrics[MetricType.GPU_TIME]?.getCurrentValue() ?: 0f,
            drawCalls = metrics[MetricType.DRAW_CALLS]?.getCurrentValue()?.toInt() ?: 0,
            triangles = metrics[MetricType.TRIANGLES]?.getCurrentValue()?.toInt() ?: 0,
            memoryUsage = metrics[MetricType.MEMORY_USAGE]?.getCurrentValue()?.toLong() ?: 0L,
            bottleneck = _bottleneckFlow.value
        )
        _performanceFlow.value = performanceData
    }

    /**
     * Analyze performance bottlenecks
     */
    private fun analyzeBottlenecks() {
        val cpuTime = metrics[MetricType.CPU_TIME]?.getCurrentValue() ?: 0f
        val gpuTime = metrics[MetricType.GPU_TIME]?.getCurrentValue() ?: 0f
        val frameTime = metrics[MetricType.FRAME_TIME]?.getCurrentValue() ?: 0f
        val drawCalls = metrics[MetricType.DRAW_CALLS]?.getCurrentValue() ?: 0f

        val bottleneck = when {
            cpuTime > gpuTime * 1.5f -> Bottleneck.CPU_BOUND
            gpuTime > cpuTime * 1.5f -> Bottleneck.GPU_BOUND
            drawCalls > 1000f -> Bottleneck.DRAW_CALL_BOUND
            frameTime > 50f -> Bottleneck.MEMORY_BOUND
            else -> Bottleneck.UNKNOWN
        }

        _bottleneckFlow.value = bottleneck
    }

    /**
     * Start continuous monitoring
     */
    fun startMonitoring() {
        monitorScope.launch {
            while (isActive) {
                delay(1000) // Update every second
                // Additional periodic analysis can be added here
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        monitorScope.cancel()
    }
}

/**
 * Get current time in nanoseconds
 * Cross-platform implementation using kotlinx-datetime
 */
private var timeCounter = 0L
private fun getTimeNanos(): Long {
    // Simple incrementing counter for performance monitoring
    return ++timeCounter * 16_000_000L // Simulate 60Hz timing
}
