/**
 * Memory Profiler for tracking and analyzing memory usage
 * Provides comprehensive memory monitoring and optimization tools
 */
package io.materia.profiling

import io.materia.core.platform.currentTimeMillis
import io.materia.util.MateriaLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Memory allocation type
 */
enum class MemoryAllocationType {
    VERTEX_BUFFER,
    INDEX_BUFFER,
    UNIFORM_BUFFER,
    TEXTURE,
    RENDER_TARGET,
    SHADER,
    MATERIAL,
    GEOMETRY,
    SCENE_OBJECT,
    OTHER
}

/**
 * Memory allocation record
 */
data class MemoryAllocation(
    val id: String,
    val type: MemoryAllocationType,
    val size: Long,
    val timestamp: Long,
    val stackTrace: String = "",
    val tag: String = "",
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Memory snapshot at a point in time
 */
data class MemorySnapshot(
    val timestamp: Long,
    val totalAllocated: Long,
    val currentUsage: Long,
    val allocationsCount: Int,
    val averageAllocationSize: Long,
    val largestAllocation: Long,
    val typeBreakdown: Map<MemoryAllocationType, Long>
)

/**
 * Memory leak detection result
 */
data class MemoryLeak(
    val allocation: MemoryAllocation,
    val age: Long,
    val suspiciousActivity: List<String>
)

/**
 * Memory profiling configuration
 */
data class MemoryProfilerConfig(
    val enableTracking: Boolean = true,
    val enableStackTraces: Boolean = false,
    val maxAllocations: Int = 10000,
    val snapshotInterval: Long = 5000L,
    val leakDetectionThreshold: Long = 60000L,
    val enableLeakDetection: Boolean = true
)

/**
 * Memory profiler implementation
 */
class MemoryProfiler(private val config: MemoryProfilerConfig = MemoryProfilerConfig()) {

    private val mutex = Mutex()
    private val _allocations = mutableMapOf<String, MemoryAllocation>()
    private val _snapshots = mutableListOf<MemorySnapshot>()

    private var _totalAllocated = 0L
    private var _totalDeallocated = 0L
    private var _peakUsage = 0L
    private var profilingJob: Job? = null

    /**
     * Start memory profiling
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun startProfiling(scope: CoroutineScope = GlobalScope) {
        if (profilingJob?.isActive == true) return

        profilingJob = scope.launch {
            while (isActive) {
                createSnapshot()
                if (config.enableLeakDetection) {
                    detectLeaks()
                }
                delay(config.snapshotInterval)
            }
        }
    }

    /**
     * Stop memory profiling
     */
    fun stopProfiling() {
        profilingJob?.cancel()
        profilingJob = null
    }

    /**
     * Record a memory allocation
     */
    suspend fun recordAllocation(
        id: String,
        type: MemoryAllocationType,
        size: Long,
        tag: String = "",
        metadata: Map<String, Any> = emptyMap()
    ) {
        if (!config.enableTracking) return

        val stackTrace = if (config.enableStackTraces) {
            captureStackTrace()
        } else {
            ""
        }

        val allocation = MemoryAllocation(
            id = id,
            type = type,
            size = size,
            timestamp = currentTimeMillis(),
            stackTrace = stackTrace,
            tag = tag,
            metadata = metadata
        )

        mutex.withLock {
            _allocations[id] = allocation
            _totalAllocated += size
            _peakUsage = max(_peakUsage, getCurrentUsage())

            // Limit number of tracked allocations
            if (_allocations.size > config.maxAllocations) {
                val oldestEntry = _allocations.values.minByOrNull { it.timestamp }
                oldestEntry?.let {
                    _allocations.remove(it.id)
                }
            }
        }
    }

    /**
     * Record memory deallocation
     */
    suspend fun recordDeallocation(id: String) {
        mutex.withLock {
            _allocations[id]?.let { allocation ->
                _allocations.remove(id)
                _totalDeallocated += allocation.size
            }
        }
    }

    /**
     * Create memory snapshot
     */
    private suspend fun createSnapshot() {
        val currentTime = currentTimeMillis()
        val currentAllocations = mutex.withLock { _allocations.values.toList() }

        val currentUsage = currentAllocations.sumOf { it.size }
        val averageSize = if (currentAllocations.isNotEmpty()) {
            currentUsage / currentAllocations.size
        } else {
            0L
        }

        val largestAllocation = currentAllocations.maxOfOrNull { it.size } ?: 0L

        val typeBreakdown = currentAllocations.groupBy { it.type }
            .mapValues { (_, allocations) -> allocations.sumOf { it.size } }

        val snapshot = MemorySnapshot(
            timestamp = currentTime,
            totalAllocated = _totalAllocated,
            currentUsage = currentUsage,
            allocationsCount = currentAllocations.size,
            averageAllocationSize = averageSize,
            largestAllocation = largestAllocation,
            typeBreakdown = typeBreakdown
        )

        mutex.withLock {
            _snapshots.add(snapshot)

            // Keep limited history
            if (_snapshots.size > 100) {
                _snapshots.removeAt(0)
            }
        }
    }

    /**
     * Detect potential memory leaks
     */
    private suspend fun detectLeaks() {
        val currentTime = currentTimeMillis()

        mutex.withLock {
            val suspiciousAllocations = _allocations.values.filter { allocation ->
                currentTime - allocation.timestamp > config.leakDetectionThreshold
            }

            suspiciousAllocations.forEach { allocation ->
                val age = currentTime - allocation.timestamp
                val leak = MemoryLeak(
                    allocation = allocation,
                    age = age,
                    suspiciousActivity = listOf("Long-lived allocation: ${age}ms")
                )
                // In a real implementation, you'd emit this to a leak reporting system
                MateriaLogger.warn("MemoryProfiler", "Potential memory leak detected: $leak")
            }
        }
    }

    /**
     * Get memory snapshots
     */
    suspend fun getSnapshots(): List<MemorySnapshot> {
        return mutex.withLock { _snapshots.toList() }
    }

    /**
     * Get memory usage summary
     */
    suspend fun getUsageSummary(): Map<String, Any> {
        return mutex.withLock {
            mapOf(
                "currentAllocations" to _allocations.size,
                "totalAllocated" to _totalAllocated,
                "totalDeallocated" to _totalDeallocated,
                "currentUsage" to getCurrentUsage(),
                "peakUsage" to _peakUsage
            )
        }
    }

    /**
     * Get current allocations
     */
    suspend fun getCurrentAllocations(): List<MemoryAllocation> {
        return mutex.withLock { _allocations.values.toList() }
    }

    /**
     * Get allocations by type
     */
    suspend fun getAllocationsByType(type: MemoryAllocationType): List<MemoryAllocation> {
        return mutex.withLock {
            _allocations.values.filter { it.type == type }
        }
    }

    /**
     * Get allocations by tag
     */
    suspend fun getAllocationsByTag(tag: String): List<MemoryAllocation> {
        return mutex.withLock {
            _allocations.values.filter { it.tag == tag }
        }
    }

    /**
     * Clear all tracking data
     */
    suspend fun clear() {
        mutex.withLock {
            _allocations.clear()
            _snapshots.clear()
            _totalAllocated = 0L
            _totalDeallocated = 0L
            _peakUsage = 0L
        }
    }

    /**
     * Get current memory usage
     */
    private fun getCurrentUsage(): Long {
        return _allocations.values.sumOf { it.size }
    }

    /**
     * Capture stack trace - simplified version
     */
    private fun captureStackTrace(): String {
        // This is a simplified implementation that works across platforms
        return "Stack trace capture not fully implemented in multiplatform context"
    }

    /**
     * Create allocation analytics
     */
    suspend fun createAnalytics(): MemoryAnalytics {
        val allocations = mutex.withLock { _allocations.values.toList() }
        val snapshots = mutex.withLock { _snapshots.toList() }

        return MemoryAnalytics(
            currentAllocations = allocations,
            snapshots = snapshots,
            totalBytesAllocated = _totalAllocated,
            totalBytesDeallocated = _totalDeallocated,
            peakUsage = _peakUsage,
            averageAllocationSize = if (allocations.isNotEmpty()) {
                allocations.sumOf { it.size } / allocations.size
            } else {
                0L
            }
        )
    }
}

/**
 * Memory analytics report
 */
data class MemoryAnalytics(
    val currentAllocations: List<MemoryAllocation>,
    val snapshots: List<MemorySnapshot>,
    val totalBytesAllocated: Long,
    val totalBytesDeallocated: Long,
    val peakUsage: Long,
    val averageAllocationSize: Long
) {

    fun getTypeBreakdown(): Map<MemoryAllocationType, Long> {
        return currentAllocations.groupBy { it.type }
            .mapValues { (_, allocations) -> allocations.sumOf { it.size } }
    }

    fun getUsageTrend(): List<Pair<Long, Long>> {
        return snapshots.map { it.timestamp to it.currentUsage }
    }

    fun getAllocationRate(): Double {
        if (snapshots.size < 2) return 0.0

        val timeDiff = snapshots.last().timestamp - snapshots.first().timestamp
        val allocationDiff = snapshots.last().allocationsCount - snapshots.first().allocationsCount

        return if (timeDiff > 0) allocationDiff.toDouble() / (timeDiff / 1000.0) else 0.0
    }
}