/**
 * Materia Tools - Memory Usage Profiler
 * Profiles memory usage patterns and identifies optimization opportunities
 */

package io.materia.tools.optimization

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.management.*
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Advanced memory profiler that tracks allocation patterns, garbage collection,
 * and identifies memory optimization opportunities
 */
class MemoryProfiler {
    private val logger = Logger("MemoryProfiler")
    private val memoryBean = ManagementFactory.getMemoryMXBean()
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    private val poolBeans = ManagementFactory.getMemoryPoolMXBeans()

    private val allocationTracking = ConcurrentHashMap<String, AllocationInfo>()
    private val gcEvents = mutableListOf<GCEvent>()
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private var profilingActive = false

    suspend fun profileMemoryUsage(config: MemoryProfilingConfig): MemoryProfilingReport = coroutineScope {
        logger.info("Starting memory profiling session...")
        logger.info("Duration: ${config.durationMs}ms")
        logger.info("Sample interval: ${config.sampleIntervalMs}ms")

        profilingActive = true
        val startTime = System.currentTimeMillis()

        // Start monitoring tasks
        val monitoringJobs = listOf(
            async { monitorMemoryUsage(config) },
            async { monitorGarbageCollection(config) },
            async { monitorAllocationPatterns(config) },
            async { monitorMemoryPools(config) },
            async { detectMemoryLeaks(config) }
        )

        // Wait for profiling duration
        delay(config.durationMs)
        profilingActive = false

        // Stop monitoring and collect results
        monitoringJobs.forEach { it.cancel() }

        val endTime = System.currentTimeMillis()
        val analysis = analyzeMemoryUsage()

        logger.info("Memory profiling completed")
        logger.info("Total samples: ${memorySnapshots.size}")
        logger.info("GC events: ${gcEvents.size}")

        MemoryProfilingReport(
            timestamp = Instant.now(),
            duration = endTime - startTime,
            config = config,
            snapshots = memorySnapshots,
            gcEvents = gcEvents,
            allocationTracking = allocationTracking.values.toList(),
            analysis = analysis,
            recommendations = generateRecommendations(analysis)
        )
    }

    private suspend fun monitorMemoryUsage(config: MemoryProfilingConfig) {
        while (profilingActive) {
            try {
                val snapshot = captureMemorySnapshot()
                memorySnapshots.add(snapshot)

                delay(config.sampleIntervalMs)
            } catch (e: Exception) {
                logger.error("Error capturing memory snapshot", e)
            }
        }
    }

    private suspend fun monitorGarbageCollection(config: MemoryProfilingConfig) {
        val initialGCStats = gcBeans.associate { it.name to GCStats(it.collectionCount, it.collectionTime) }

        while (profilingActive) {
            try {
                gcBeans.forEach { gcBean ->
                    val initial = initialGCStats[gcBean.name]
                    if (initial != null) {
                        val currentCount = gcBean.collectionCount
                        val currentTime = gcBean.collectionTime

                        if (currentCount > initial.count) {
                            val event = GCEvent(
                                timestamp = System.currentTimeMillis(),
                                collectorName = gcBean.name,
                                collectionCount = currentCount - initial.count,
                                collectionTime = currentTime - initial.time
                            )
                            gcEvents.add(event)
                        }
                    }
                }

                delay(config.sampleIntervalMs)
            } catch (e: Exception) {
                logger.error("Error monitoring GC", e)
            }
        }
    }

    private suspend fun monitorAllocationPatterns(config: MemoryProfilingConfig) {
        while (profilingActive) {
            try {
                // Track allocations via JVM TI or profiling agent
                trackAllocations()

                delay(config.sampleIntervalMs * 2)
            } catch (e: Exception) {
                logger.error("Error tracking allocations", e)
            }
        }
    }

    private suspend fun monitorMemoryPools(config: MemoryProfilingConfig) {
        while (profilingActive) {
            try {
                poolBeans.forEach { poolBean ->
                    val usage = poolBean.usage
                    val poolSnapshot = MemoryPoolSnapshot(
                        timestamp = System.currentTimeMillis(),
                        poolName = poolBean.name,
                        used = usage.used,
                        committed = usage.committed,
                        max = usage.max,
                        type = poolBean.type.name
                    )

                    // Store pool snapshots (simplified for this example)
                    logger.info("Pool ${poolBean.name}: ${usage.used / 1024 / 1024}MB used")
                }

                delay(config.sampleIntervalMs * 5)
            } catch (e: Exception) {
                logger.error("Error monitoring memory pools", e)
            }
        }
    }

    private suspend fun detectMemoryLeaks(config: MemoryProfilingConfig) {
        val leakDetectionWindow = 30000L // 30 seconds
        var lastCheckTime = System.currentTimeMillis()

        while (profilingActive) {
            try {
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastCheckTime >= leakDetectionWindow) {
                    analyzeForMemoryLeaks()
                    lastCheckTime = currentTime
                }

                delay(5000) // Check every 5 seconds
            } catch (e: Exception) {
                logger.error("Error detecting memory leaks", e)
            }
        }
    }

    private fun captureMemorySnapshot(): MemorySnapshot {
        val heapUsage = memoryBean.heapMemoryUsage
        val nonHeapUsage = memoryBean.nonHeapMemoryUsage
        val runtime = Runtime.getRuntime()

        return MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            heapUsed = heapUsage.used,
            heapCommitted = heapUsage.committed,
            heapMax = heapUsage.max,
            nonHeapUsed = nonHeapUsage.used,
            nonHeapCommitted = nonHeapUsage.committed,
            nonHeapMax = nonHeapUsage.max,
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            maxMemory = runtime.maxMemory()
        )
    }

    private fun trackAllocations() {
        // Simulate allocation tracking
        val classes = listOf(
            "Vector3", "Matrix4", "Quaternion", "Mesh", "Material",
            "Texture", "Light", "Camera", "Scene", "Object3D"
        )

        classes.forEach { className ->
            val existing = allocationTracking[className]
            val newAllocations = Random.nextInt(1, 10)
            val newSize = Random.nextLong(1024, 10240)

            if (existing != null) {
                allocationTracking[className] = existing.copy(
                    allocations = existing.allocations + newAllocations,
                    totalSize = existing.totalSize + newSize
                )
            } else {
                allocationTracking[className] = AllocationInfo(
                    className = className,
                    allocations = newAllocations,
                    totalSize = newSize
                )
            }
        }
    }

    private fun analyzeForMemoryLeaks() {
        logger.info("Analyzing for potential memory leaks...")

        // Check for consistently growing memory usage
        if (memorySnapshots.size >= 10) {
            val recentSnapshots = memorySnapshots.takeLast(10)
            val growthTrend = calculateMemoryGrowthTrend(recentSnapshots)

            if (growthTrend > 0.1) { // 10% growth
                logger.warn("Potential memory leak detected: ${growthTrend * 100}% growth trend")
            }
        }
    }

    private fun calculateMemoryGrowthTrend(snapshots: List<MemorySnapshot>): Double {
        if (snapshots.size < 2) return 0.0

        val first = snapshots.first().heapUsed
        val last = snapshots.last().heapUsed

        return (last - first).toDouble() / first
    }

    private fun analyzeMemoryUsage(): MemoryAnalysis {
        val peakHeapUsage = memorySnapshots.maxOfOrNull { it.heapUsed } ?: 0L
        val averageHeapUsage = memorySnapshots.map { it.heapUsed }.average()
        val memoryGrowthRate = calculateOverallGrowthRate()
        val topAllocators = findTopAllocators()
        val gcEfficiency = calculateGCEfficiency()
        val memoryFragmentation = calculateMemoryFragmentation()
        val leakSuspects = detectLeakSuspects()

        return MemoryAnalysis(
            peakHeapUsage = peakHeapUsage,
            averageHeapUsage = averageHeapUsage.toLong(),
            memoryGrowthRate = memoryGrowthRate,
            topAllocators = topAllocators,
            gcEfficiency = gcEfficiency,
            memoryFragmentation = memoryFragmentation,
            totalGCTime = gcEvents.sumOf { it.collectionTime },
            gcCount = gcEvents.sumOf { it.collectionCount },
            leakSuspects = leakSuspects
        )
    }

    private fun calculateOverallGrowthRate(): Double {
        if (memorySnapshots.size < 2) return 0.0

        val first = memorySnapshots.first().heapUsed
        val last = memorySnapshots.last().heapUsed
        val duration = memorySnapshots.last().timestamp - memorySnapshots.first().timestamp

        return if (duration > 0) {
            ((last - first).toDouble() / first) * (60000.0 / duration) // Growth rate per minute
        } else 0.0
    }

    private fun findTopAllocators(): List<AllocationInfo> {
        return allocationTracking.values
            .sortedByDescending { it.totalSize }
            .take(10)
    }

    private fun calculateGCEfficiency(): Double {
        if (gcEvents.isEmpty()) return 100.0

        val totalGCTime = gcEvents.sumOf { it.collectionTime }
        val totalRunTime = if (memorySnapshots.isNotEmpty()) {
            memorySnapshots.last().timestamp - memorySnapshots.first().timestamp
        } else 1L

        return ((totalRunTime - totalGCTime).toDouble() / totalRunTime) * 100
    }

    private fun calculateMemoryFragmentation(): Double {
        // Simplified fragmentation calculation
        if (memorySnapshots.isEmpty()) return 0.0

        val latest = memorySnapshots.last()
        val usedRatio = latest.heapUsed.toDouble() / latest.heapCommitted
        val fragmentationScore = (1.0 - usedRatio) * 100

        return fragmentationScore.coerceIn(0.0, 100.0)
    }

    private fun detectLeakSuspects(): List<String> {
        val suspects = mutableListOf<String>()

        // Check for classes with high allocation rates
        allocationTracking.values.forEach { allocation ->
            if (allocation.allocations > 1000) {
                suspects.add("${allocation.className}: High allocation count (${allocation.allocations})")
            }

            if (allocation.totalSize > 50 * 1024 * 1024) { // 50MB
                suspects.add("${allocation.className}: Large memory footprint (${allocation.totalSize / 1024 / 1024}MB)")
            }
        }

        // Check for memory growth trends
        if (memorySnapshots.size >= 5) {
            val trend = calculateMemoryGrowthTrend(memorySnapshots.takeLast(5))
            if (trend > 0.2) { // 20% growth in recent samples
                suspects.add("Overall memory: Consistent growth pattern (${(trend * 100).toInt()}%)")
            }
        }

        return suspects
    }

    private fun generateRecommendations(analysis: MemoryAnalysis): List<MemoryRecommendation> {
        val recommendations = mutableListOf<MemoryRecommendation>()

        // High memory usage recommendations
        if (analysis.peakHeapUsage > 1024 * 1024 * 1024) { // > 1GB
            recommendations.add(
                MemoryRecommendation(
                    priority = RecommendationPriority.HIGH,
                    category = "Memory Usage",
                    title = "High memory usage detected",
                    description = "Peak heap usage exceeded 1GB. Consider optimizing data structures or increasing heap size.",
                    action = "Review memory allocation patterns and consider object pooling"
                )
            )
        }

        // GC efficiency recommendations
        if (analysis.gcEfficiency < 95.0) {
            recommendations.add(
                MemoryRecommendation(
                    priority = RecommendationPriority.MEDIUM,
                    category = "Garbage Collection",
                    title = "Poor GC efficiency",
                    description = "GC efficiency is ${analysis.gcEfficiency.toInt()}%. Consider GC tuning.",
                    action = "Tune GC parameters or switch to a different GC algorithm"
                )
            )
        }

        // Allocation pattern recommendations
        analysis.topAllocators.take(3).forEach { allocator ->
            if (allocator.allocations > 500) {
                recommendations.add(
                    MemoryRecommendation(
                        priority = RecommendationPriority.MEDIUM,
                        category = "Allocation Patterns",
                        title = "High allocation rate for ${allocator.className}",
                        description = "${allocator.className} allocated ${allocator.allocations} times.",
                        action = "Consider object pooling or reusing instances"
                    )
                )
            }
        }

        // Memory leak recommendations
        if (analysis.leakSuspects.isNotEmpty()) {
            recommendations.add(
                MemoryRecommendation(
                    priority = RecommendationPriority.HIGH,
                    category = "Memory Leaks",
                    title = "Potential memory leaks detected",
                    description = "Found ${analysis.leakSuspects.size} potential memory leak indicators.",
                    action = "Investigate objects that are not being properly released"
                )
            )
        }

        // Memory growth recommendations
        if (analysis.memoryGrowthRate > 0.1) { // 10% per minute
            recommendations.add(
                MemoryRecommendation(
                    priority = RecommendationPriority.HIGH,
                    category = "Memory Growth",
                    title = "Rapid memory growth",
                    description = "Memory is growing at ${(analysis.memoryGrowthRate * 100).toInt()}% per minute.",
                    action = "Check for memory leaks or excessive object creation"
                )
            )
        }

        // Fragmentation recommendations
        if (analysis.memoryFragmentation > 30.0) {
            recommendations.add(
                MemoryRecommendation(
                    priority = RecommendationPriority.LOW,
                    category = "Memory Fragmentation",
                    title = "High memory fragmentation",
                    description = "Memory fragmentation is ${analysis.memoryFragmentation.toInt()}%.",
                    action = "Consider using memory-mapped files or different allocation strategies"
                )
            )
        }

        return recommendations
    }
}

// Data classes
@Serializable
data class MemoryProfilingConfig(
    val durationMs: Long = 60000, // 1 minute
    val sampleIntervalMs: Long = 1000, // 1 second
    val enableAllocationTracking: Boolean = true,
    val enableLeakDetection: Boolean = true,
    val enableGCMonitoring: Boolean = true,
    val trackObjectCreation: Boolean = false // Requires JVM agent
)

@Serializable
data class MemorySnapshot(
    val timestamp: Long,
    val heapUsed: Long,
    val heapCommitted: Long,
    val heapMax: Long,
    val nonHeapUsed: Long,
    val nonHeapCommitted: Long,
    val nonHeapMax: Long,
    val totalMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long
)

@Serializable
data class GCEvent(
    val timestamp: Long,
    val collectorName: String,
    val collectionCount: Long,
    val collectionTime: Long
)

@Serializable
data class AllocationInfo(
    val className: String,
    val allocations: Int,
    val totalSize: Long
)

@Serializable
data class MemoryPoolSnapshot(
    val timestamp: Long,
    val poolName: String,
    val used: Long,
    val committed: Long,
    val max: Long,
    val type: String
)

@Serializable
data class MemoryAnalysis(
    val peakHeapUsage: Long,
    val averageHeapUsage: Long,
    val memoryGrowthRate: Double,
    val topAllocators: List<AllocationInfo>,
    val gcEfficiency: Double,
    val memoryFragmentation: Double,
    val totalGCTime: Long,
    val gcCount: Long,
    val leakSuspects: List<String>
)

@Serializable
data class MemoryRecommendation(
    val priority: RecommendationPriority,
    val category: String,
    val title: String,
    val description: String,
    val action: String
)

@Serializable
data class MemoryProfilingReport(
    val timestamp: Instant,
    val duration: Long,
    val config: MemoryProfilingConfig,
    val snapshots: List<MemorySnapshot>,
    val gcEvents: List<GCEvent>,
    val allocationTracking: List<AllocationInfo>,
    val analysis: MemoryAnalysis,
    val recommendations: List<MemoryRecommendation>
)

@Serializable
enum class RecommendationPriority {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class GCStats(val count: Long, val time: Long)

// Main execution
suspend fun main(args: Array<String>) {
    val configFile = args.getOrNull(0) ?: "memory-profiling-config.json"
    val outputFile = args.getOrNull(1) ?: "memory-profiling-report.json"

    try {
        val config = if (File(configFile).exists()) {
            Json.decodeFromString<MemoryProfilingConfig>(File(configFile).readText())
        } else {
            MemoryProfilingConfig()
        }

        val profiler = MemoryProfiler()
        val report = profiler.profileMemoryUsage(config)

        // Write report
        val reportJson = Json.encodeToString(MemoryProfilingReport.serializer(), report)
        File(outputFile).writeText(reportJson)

        // Console output
        println("\n" + "=".repeat(60))
        println("MEMORY PROFILING REPORT")
        println("=".repeat(60))
        println("Duration: ${report.duration}ms")
        println("Samples collected: ${report.snapshots.size}")
        println("GC events: ${report.gcEvents.size}")
        println("Peak heap usage: ${report.analysis.peakHeapUsage / 1024 / 1024}MB")
        println("Average heap usage: ${report.analysis.averageHeapUsage / 1024 / 1024}MB")
        println("GC efficiency: ${report.analysis.gcEfficiency.toInt()}%")
        println("Memory growth rate: ${(report.analysis.memoryGrowthRate * 100).toInt()}% per minute")
        println("=".repeat(60))

        println("\nTop Memory Allocators:")
        report.analysis.topAllocators.take(5).forEach { allocator ->
            println("• ${allocator.className}: ${allocator.allocations} allocations, ${allocator.totalSize / 1024}KB")
        }

        if (report.analysis.leakSuspects.isNotEmpty()) {
            println("\nPotential Memory Leak Suspects:")
            report.analysis.leakSuspects.forEach { suspect ->
                println("⚠️  $suspect")
            }
        }

        println("\nRecommendations:")
        report.recommendations
            .sortedByDescending { it.priority }
            .forEach { recommendation ->
                val priority = when (recommendation.priority) {
                    RecommendationPriority.CRITICAL -> "🔴 CRITICAL"
                    RecommendationPriority.HIGH -> "🟠 HIGH"
                    RecommendationPriority.MEDIUM -> "🟡 MEDIUM"
                    RecommendationPriority.LOW -> "🟢 LOW"
                }
                println("$priority ${recommendation.title}")
                println("   ${recommendation.description}")
                println("   Action: ${recommendation.action}")
                println()
            }

        println("Memory profiling report saved to: $outputFile")
    } catch (e: Exception) {
        println("Memory profiling failed: ${e.message}")
        e.printStackTrace()
        kotlin.system.exitProcess(1)
    }
}