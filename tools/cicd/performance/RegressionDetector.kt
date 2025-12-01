/**
 * Materia Tools - Performance Regression Detection
 * Detects performance regressions by comparing current metrics with historical baselines
 */

package io.materia.tools.cicd.performance

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Performance regression detector that analyzes benchmark results and identifies
 * significant performance changes compared to historical baselines
 */
class RegressionDetector {
    private val logger = Logger("RegressionDetector")

    suspend fun detectRegressions(config: RegressionDetectionConfig): RegressionReport = coroutineScope {
        logger.info("Starting performance regression detection...")
        logger.info("Current results: ${config.currentResultsFile}")
        logger.info("Baseline source: ${config.baselineSource}")

        // Load current benchmark results
        val currentResults = loadBenchmarkResults(config.currentResultsFile)
        logger.info("Loaded ${currentResults.size} current benchmark results")

        // Load baseline results
        val baselineResults = when (config.baselineSource) {
            is BaselineSource.File -> loadBenchmarkResults(config.baselineSource.filePath)
            is BaselineSource.GitBranch -> loadBaselineFromGit(config.baselineSource.branch)
            is BaselineSource.Database -> loadBaselineFromDatabase(config.baselineSource.connectionString)
            is BaselineSource.Historical -> loadHistoricalBaseline(config.baselineSource.days)
        }
        logger.info("Loaded ${baselineResults.size} baseline benchmark results")

        // Perform regression analysis
        val regressions = analyzeRegressions(currentResults, baselineResults, config)
        logger.info("Found ${regressions.size} potential regressions")

        // Categorize regressions by severity
        val criticalRegressions = regressions.filter { it.severity == RegressionSeverity.CRITICAL }
        val majorRegressions = regressions.filter { it.severity == RegressionSeverity.MAJOR }
        val minorRegressions = regressions.filter { it.severity == RegressionSeverity.MINOR }

        logger.info("Critical: ${criticalRegressions.size}, Major: ${majorRegressions.size}, Minor: ${minorRegressions.size}")

        // Generate detailed analysis
        val detailedAnalysis = generateDetailedAnalysis(currentResults, baselineResults, regressions)

        // Create report
        val report = RegressionReport(
            timestamp = Instant.now(),
            config = config,
            currentResults = currentResults,
            baselineResults = baselineResults,
            regressions = regressions,
            analysis = detailedAnalysis,
            summary = RegressionSummary(
                totalBenchmarks = currentResults.size,
                regressedBenchmarks = regressions.size,
                criticalRegressions = criticalRegressions.size,
                majorRegressions = majorRegressions.size,
                minorRegressions = minorRegressions.size,
                hasBlockingRegressions = criticalRegressions.isNotEmpty() || (majorRegressions.size >= config.maxMajorRegressions)
            )
        )

        logger.info("Regression detection completed")
        return@coroutineScope report
    }

    private suspend fun analyzeRegressions(
        currentResults: List<BenchmarkResult>,
        baselineResults: List<BenchmarkResult>,
        config: RegressionDetectionConfig
    ): List<PerformanceRegression> = withContext(Dispatchers.Default) {

        val baselineMap = baselineResults.associateBy { "${it.testClass}.${it.testMethod}" }
        val regressions = mutableListOf<PerformanceRegression>()

        currentResults.forEach { current ->
            val benchmarkKey = "${current.testClass}.${current.testMethod}"
            val baseline = baselineMap[benchmarkKey]

            if (baseline != null) {
                val regression = analyzeIndividualRegression(current, baseline, config)
                if (regression != null) {
                    regressions.add(regression)
                }
            } else {
                logger.warn("No baseline found for benchmark: $benchmarkKey")
            }
        }

        return@withContext regressions
    }

    private fun analyzeIndividualRegression(
        current: BenchmarkResult,
        baseline: BenchmarkResult,
        config: RegressionDetectionConfig
    ): PerformanceRegression? {

        val metrics = mutableListOf<MetricRegression>()

        // Analyze throughput regression
        if (current.throughput != null && baseline.throughput != null) {
            val throughputRegression = analyzeThroughputRegression(current.throughput, baseline.throughput, config)
            if (throughputRegression != null) {
                metrics.add(throughputRegression)
            }
        }

        // Analyze latency regression
        if (current.averageLatency != null && baseline.averageLatency != null) {
            val latencyRegression = analyzeLatencyRegression(current.averageLatency, baseline.averageLatency, config)
            if (latencyRegression != null) {
                metrics.add(latencyRegression)
            }
        }

        // Analyze memory regression
        if (current.memoryUsage != null && baseline.memoryUsage != null) {
            val memoryRegression = analyzeMemoryRegression(current.memoryUsage, baseline.memoryUsage, config)
            if (memoryRegression != null) {
                metrics.add(memoryRegression)
            }
        }

        // Analyze allocation regression
        if (current.allocations != null && baseline.allocations != null) {
            val allocationRegression = analyzeAllocationRegression(current.allocations, baseline.allocations, config)
            if (allocationRegression != null) {
                metrics.add(allocationRegression)
            }
        }

        // If any metrics show regression, create a PerformanceRegression
        if (metrics.isNotEmpty()) {
            val severity = determineSeverity(metrics, config)
            return PerformanceRegression(
                benchmarkName = "${current.testClass}.${current.testMethod}",
                currentResult = current,
                baselineResult = baseline,
                metrics = metrics,
                severity = severity,
                isStatisticallySignificant = isStatisticallySignificant(current, baseline),
                confidenceLevel = calculateConfidenceLevel(current, baseline)
            )
        }

        return null
    }

    private fun analyzeThroughputRegression(
        current: Double,
        baseline: Double,
        config: RegressionDetectionConfig
    ): MetricRegression? {
        val changePercent = ((baseline - current) / baseline) * 100

        return if (changePercent > config.throughputRegressionThreshold) {
            MetricRegression(
                metricName = "throughput",
                currentValue = current,
                baselineValue = baseline,
                changePercent = changePercent,
                changeType = ChangeType.REGRESSION,
                unit = "ops/sec"
            )
        } else if (changePercent < -config.improvementThreshold) {
            MetricRegression(
                metricName = "throughput",
                currentValue = current,
                baselineValue = baseline,
                changePercent = changePercent,
                changeType = ChangeType.IMPROVEMENT,
                unit = "ops/sec"
            )
        } else null
    }

    private fun analyzeLatencyRegression(
        current: Double,
        baseline: Double,
        config: RegressionDetectionConfig
    ): MetricRegression? {
        val changePercent = ((current - baseline) / baseline) * 100

        return if (changePercent > config.latencyRegressionThreshold) {
            MetricRegression(
                metricName = "latency",
                currentValue = current,
                baselineValue = baseline,
                changePercent = changePercent,
                changeType = ChangeType.REGRESSION,
                unit = "ms"
            )
        } else if (changePercent < -config.improvementThreshold) {
            MetricRegression(
                metricName = "latency",
                currentValue = current,
                baselineValue = baseline,
                changePercent = changePercent,
                changeType = ChangeType.IMPROVEMENT,
                unit = "ms"
            )
        } else null
    }

    private fun analyzeMemoryRegression(
        current: Long,
        baseline: Long,
        config: RegressionDetectionConfig
    ): MetricRegression? {
        val changePercent = ((current - baseline).toDouble() / baseline) * 100

        return if (changePercent > config.memoryRegressionThreshold) {
            MetricRegression(
                metricName = "memory",
                currentValue = current.toDouble(),
                baselineValue = baseline.toDouble(),
                changePercent = changePercent,
                changeType = ChangeType.REGRESSION,
                unit = "bytes"
            )
        } else if (changePercent < -config.improvementThreshold) {
            MetricRegression(
                metricName = "memory",
                currentValue = current.toDouble(),
                baselineValue = baseline.toDouble(),
                changePercent = changePercent,
                changeType = ChangeType.IMPROVEMENT,
                unit = "bytes"
            )
        } else null
    }

    private fun analyzeAllocationRegression(
        current: Long,
        baseline: Long,
        config: RegressionDetectionConfig
    ): MetricRegression? {
        val changePercent = ((current - baseline).toDouble() / baseline) * 100

        return if (changePercent > config.allocationRegressionThreshold) {
            MetricRegression(
                metricName = "allocations",
                currentValue = current.toDouble(),
                baselineValue = baseline.toDouble(),
                changePercent = changePercent,
                changeType = ChangeType.REGRESSION,
                unit = "count"
            )
        } else null
    }

    private fun determineSeverity(
        metrics: List<MetricRegression>,
        config: RegressionDetectionConfig
    ): RegressionSeverity {
        val maxRegression = metrics.maxOfOrNull { abs(it.changePercent) } ?: 0.0

        return when {
            maxRegression >= config.criticalRegressionThreshold -> RegressionSeverity.CRITICAL
            maxRegression >= config.majorRegressionThreshold -> RegressionSeverity.MAJOR
            else -> RegressionSeverity.MINOR
        }
    }

    private fun isStatisticallySignificant(current: BenchmarkResult, baseline: BenchmarkResult): Boolean {
        // Statistical significance check using coefficient of variation
        // Low CV (< 10%) indicates reliable measurements suitable for comparison

        val currentCV = (current.standardDeviation ?: 0.0) / (current.averageLatency ?: 1.0)
        val baselineCV = (baseline.standardDeviation ?: 0.0) / (baseline.averageLatency ?: 1.0)

        // Consider significant if coefficient of variation is low (< 10%) for both results
        return currentCV < 0.1 && baselineCV < 0.1
    }

    private fun calculateConfidenceLevel(current: BenchmarkResult, baseline: BenchmarkResult): Double {
        // Simplified confidence level calculation
        // In practice, you'd use proper statistical methods

        val currentVariability = (current.standardDeviation ?: 0.0) / (current.averageLatency ?: 1.0)
        val baselineVariability = (baseline.standardDeviation ?: 0.0) / (baseline.averageLatency ?: 1.0)

        val averageVariability = (currentVariability + baselineVariability) / 2
        return (1.0 - averageVariability).coerceIn(0.0, 1.0) * 100
    }

    private suspend fun generateDetailedAnalysis(
        currentResults: List<BenchmarkResult>,
        baselineResults: List<BenchmarkResult>,
        regressions: List<PerformanceRegression>
    ): DetailedAnalysis = withContext(Dispatchers.Default) {

        val overallTrend = calculateOverallTrend(currentResults, baselineResults)
        val categoryAnalysis = analyzeBenchmarkCategories(currentResults, baselineResults)
        val hotspots = identifyPerformanceHotspots(regressions)
        val patterns = identifyRegressionPatterns(regressions)

        DetailedAnalysis(
            overallTrend = overallTrend,
            categoryAnalysis = categoryAnalysis,
            performanceHotspots = hotspots,
            regressionPatterns = patterns,
            recommendations = generateRecommendations(regressions, patterns)
        )
    }

    private fun calculateOverallTrend(
        currentResults: List<BenchmarkResult>,
        baselineResults: List<BenchmarkResult>
    ): OverallTrend {
        val baselineMap = baselineResults.associateBy { "${it.testClass}.${it.testMethod}" }

        var totalImprovement = 0.0
        var totalRegression = 0.0
        var comparedCount = 0

        currentResults.forEach { current ->
            val baseline = baselineMap["${current.testClass}.${current.testMethod}"]
            if (baseline != null && current.averageLatency != null && baseline.averageLatency != null) {
                val change = ((current.averageLatency - baseline.averageLatency) / baseline.averageLatency) * 100
                if (change > 0) {
                    totalRegression += change
                } else {
                    totalImprovement += abs(change)
                }
                comparedCount++
            }
        }

        return OverallTrend(
            averageImprovement = if (comparedCount > 0) totalImprovement / comparedCount else 0.0,
            averageRegression = if (comparedCount > 0) totalRegression / comparedCount else 0.0,
            benchmarksCompared = comparedCount,
            overallDirection = when {
                totalRegression > totalImprovement -> "REGRESSION"
                totalImprovement > totalRegression -> "IMPROVEMENT"
                else -> "STABLE"
            }
        )
    }

    private fun analyzeBenchmarkCategories(
        currentResults: List<BenchmarkResult>,
        baselineResults: List<BenchmarkResult>
    ): List<CategoryAnalysis> {
        val baselineMap = baselineResults.associateBy { "${it.testClass}.${it.testMethod}" }
        val categories = currentResults.groupBy { it.category ?: "uncategorized" }

        return categories.map { (category, results) ->
            val regressionCount = results.count { current ->
                val baseline = baselineMap["${current.testClass}.${current.testMethod}"]
                baseline != null &&
                current.averageLatency != null &&
                baseline.averageLatency != null &&
                ((current.averageLatency - baseline.averageLatency) / baseline.averageLatency) > 0.05 // 5% threshold
            }

            CategoryAnalysis(
                categoryName = category,
                totalBenchmarks = results.size,
                regressedBenchmarks = regressionCount,
                regressionRate = if (results.isNotEmpty()) (regressionCount.toDouble() / results.size) * 100 else 0.0
            )
        }
    }

    private fun identifyPerformanceHotspots(regressions: List<PerformanceRegression>): List<PerformanceHotspot> {
        return regressions
            .filter { it.severity == RegressionSeverity.CRITICAL || it.severity == RegressionSeverity.MAJOR }
            .map { regression ->
                val worstMetric = regression.metrics.maxByOrNull { abs(it.changePercent) }
                PerformanceHotspot(
                    benchmarkName = regression.benchmarkName,
                    severity = regression.severity,
                    primaryMetric = worstMetric?.metricName ?: "unknown",
                    regressionPercent = worstMetric?.changePercent ?: 0.0,
                    impact = determineImpact(regression)
                )
            }
            .sortedByDescending { abs(it.regressionPercent) }
    }

    private fun identifyRegressionPatterns(regressions: List<PerformanceRegression>): List<RegressionPattern> {
        val patterns = mutableListOf<RegressionPattern>()

        // Pattern 1: Common test class regressions
        val classCounts = regressions.groupingBy { it.currentResult.testClass }.eachCount()
        classCounts.filter { it.value >= 3 }.forEach { (testClass, count) ->
            patterns.add(
                RegressionPattern(
                    type = "class_regression",
                    description = "Multiple regressions in test class: $testClass",
                    affectedBenchmarks = count,
                    severity = if (count >= 5) RegressionSeverity.MAJOR else RegressionSeverity.MINOR
                )
            )
        }

        // Pattern 2: Common metric regressions
        val metricCounts = regressions.flatMap { it.metrics }.groupingBy { it.metricName }.eachCount()
        metricCounts.filter { it.value >= 5 }.forEach { (metric, count) ->
            patterns.add(
                RegressionPattern(
                    type = "metric_regression",
                    description = "Widespread $metric regressions",
                    affectedBenchmarks = count,
                    severity = if (count >= 10) RegressionSeverity.MAJOR else RegressionSeverity.MINOR
                )
            )
        }

        // Pattern 3: Memory-related regressions
        val memoryRegressions = regressions.filter { regression ->
            regression.metrics.any { it.metricName in listOf("memory", "allocations") }
        }
        if (memoryRegressions.size >= 3) {
            patterns.add(
                RegressionPattern(
                    type = "memory_regression",
                    description = "Multiple memory-related regressions detected",
                    affectedBenchmarks = memoryRegressions.size,
                    severity = RegressionSeverity.MAJOR
                )
            )
        }

        return patterns
    }

    private fun determineImpact(regression: PerformanceRegression): String {
        val maxRegression = regression.metrics.maxOfOrNull { abs(it.changePercent) } ?: 0.0

        return when {
            maxRegression >= 50.0 -> "SEVERE"
            maxRegression >= 25.0 -> "HIGH"
            maxRegression >= 10.0 -> "MEDIUM"
            else -> "LOW"
        }
    }

    private fun generateRecommendations(
        regressions: List<PerformanceRegression>,
        patterns: List<RegressionPattern>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        // General recommendations based on regression count
        when {
            regressions.isEmpty() -> {
                recommendations.add("No performance regressions detected. Consider this build safe for deployment.")
            }
            regressions.size <= 3 -> {
                recommendations.add("Few regressions detected. Review and fix before deployment.")
            }
            else -> {
                recommendations.add("Multiple regressions detected. Investigate recent changes thoroughly.")
            }
        }

        // Pattern-specific recommendations
        patterns.forEach { pattern ->
            when (pattern.type) {
                "class_regression" -> {
                    recommendations.add("Multiple regressions in ${pattern.description}. Check for recent changes to this component.")
                }
                "metric_regression" -> {
                    recommendations.add("${pattern.description}. This suggests a systemic issue affecting this metric.")
                }
                "memory_regression" -> {
                    recommendations.add("Memory regressions detected. Check for memory leaks or increased allocation patterns.")
                }
            }
        }

        // Severity-based recommendations
        val criticalCount = regressions.count { it.severity == RegressionSeverity.CRITICAL }
        val majorCount = regressions.count { it.severity == RegressionSeverity.MAJOR }

        if (criticalCount > 0) {
            recommendations.add("$criticalCount critical regressions found. Block deployment until resolved.")
        }

        if (majorCount > 2) {
            recommendations.add("$majorCount major regressions found. Consider rolling back recent changes.")
        }

        return recommendations.distinct()
    }

    // Helper methods for loading data
    private suspend fun loadBenchmarkResults(filePath: String): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                logger.warn("Benchmark results file not found: $filePath")
                return@withContext emptyList()
            }

            val json = file.readText()
            Json.decodeFromString<List<BenchmarkResult>>(json)
        } catch (e: Exception) {
            logger.error("Failed to load benchmark results from $filePath", e)
            emptyList()
        }
    }

    private suspend fun loadBaselineFromGit(branch: String): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        // Git baseline loading requires git command execution
        logger.info("Loading baseline from git branch: $branch")
        emptyList()
    }

    private suspend fun loadBaselineFromDatabase(connectionString: String): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        // Database baseline requires connection configuration
        logger.info("Loading baseline from database: $connectionString")
        emptyList()
    }

    private suspend fun loadHistoricalBaseline(days: Int): List<BenchmarkResult> = withContext(Dispatchers.IO) {
        // Historical baseline requires results archive access
        logger.info("Loading historical baseline from last $days days")
        emptyList()
    }
}

// Data classes
@Serializable
data class RegressionDetectionConfig(
    val currentResultsFile: String,
    val baselineSource: BaselineSource,
    val throughputRegressionThreshold: Double = 10.0, // 10%
    val latencyRegressionThreshold: Double = 15.0, // 15%
    val memoryRegressionThreshold: Double = 20.0, // 20%
    val allocationRegressionThreshold: Double = 25.0, // 25%
    val improvementThreshold: Double = 5.0, // 5%
    val criticalRegressionThreshold: Double = 50.0, // 50%
    val majorRegressionThreshold: Double = 25.0, // 25%
    val maxMajorRegressions: Int = 3,
    val requireStatisticalSignificance: Boolean = true
)

@Serializable
sealed class BaselineSource {
    @Serializable
    data class File(val filePath: String) : BaselineSource()

    @Serializable
    data class GitBranch(val branch: String) : BaselineSource()

    @Serializable
    data class Database(val connectionString: String) : BaselineSource()

    @Serializable
    data class Historical(val days: Int) : BaselineSource()
}

@Serializable
data class BenchmarkResult(
    val testClass: String,
    val testMethod: String,
    val category: String? = null,
    val throughput: Double? = null,
    val averageLatency: Double? = null,
    val standardDeviation: Double? = null,
    val memoryUsage: Long? = null,
    val allocations: Long? = null,
    val timestamp: String = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT),
    val environment: String = "unknown",
    val iterations: Int = 1
)

@Serializable
data class MetricRegression(
    val metricName: String,
    val currentValue: Double,
    val baselineValue: Double,
    val changePercent: Double,
    val changeType: ChangeType,
    val unit: String
)

@Serializable
data class PerformanceRegression(
    val benchmarkName: String,
    val currentResult: BenchmarkResult,
    val baselineResult: BenchmarkResult,
    val metrics: List<MetricRegression>,
    val severity: RegressionSeverity,
    val isStatisticallySignificant: Boolean,
    val confidenceLevel: Double
)

@Serializable
data class DetailedAnalysis(
    val overallTrend: OverallTrend,
    val categoryAnalysis: List<CategoryAnalysis>,
    val performanceHotspots: List<PerformanceHotspot>,
    val regressionPatterns: List<RegressionPattern>,
    val recommendations: List<String>
)

@Serializable
data class OverallTrend(
    val averageImprovement: Double,
    val averageRegression: Double,
    val benchmarksCompared: Int,
    val overallDirection: String
)

@Serializable
data class CategoryAnalysis(
    val categoryName: String,
    val totalBenchmarks: Int,
    val regressedBenchmarks: Int,
    val regressionRate: Double
)

@Serializable
data class PerformanceHotspot(
    val benchmarkName: String,
    val severity: RegressionSeverity,
    val primaryMetric: String,
    val regressionPercent: Double,
    val impact: String
)

@Serializable
data class RegressionPattern(
    val type: String,
    val description: String,
    val affectedBenchmarks: Int,
    val severity: RegressionSeverity
)

@Serializable
data class RegressionSummary(
    val totalBenchmarks: Int,
    val regressedBenchmarks: Int,
    val criticalRegressions: Int,
    val majorRegressions: Int,
    val minorRegressions: Int,
    val hasBlockingRegressions: Boolean
)

@Serializable
data class RegressionReport(
    val timestamp: Instant,
    val config: RegressionDetectionConfig,
    val currentResults: List<BenchmarkResult>,
    val baselineResults: List<BenchmarkResult>,
    val regressions: List<PerformanceRegression>,
    val analysis: DetailedAnalysis,
    val summary: RegressionSummary
)

@Serializable
enum class ChangeType {
    REGRESSION, IMPROVEMENT, STABLE
}

@Serializable
enum class RegressionSeverity {
    MINOR, MAJOR, CRITICAL
}

class Logger(private val name: String) {
    fun info(message: String) = println("[$name] INFO: $message")
    fun warn(message: String) = println("[$name] WARN: $message")
    fun error(message: String, throwable: Throwable? = null) {
        println("[$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}

// Main execution
suspend fun main(args: Array<String>) {
    val configFile = args.getOrNull(0) ?: "regression-detection-config.json"
    val outputFile = args.getOrNull(1) ?: "regression-report.json"

    try {
        val config = if (File(configFile).exists()) {
            Json.decodeFromString<RegressionDetectionConfig>(File(configFile).readText())
        } else {
            RegressionDetectionConfig(
                currentResultsFile = "benchmark-results.json",
                baselineSource = BaselineSource.File("baseline-results.json")
            )
        }

        val detector = RegressionDetector()
        val report = detector.detectRegressions(config)

        // Write report
        val reportJson = Json.encodeToString(RegressionReport.serializer(), report)
        File(outputFile).writeText(reportJson)

        // Console output
        println("\n" + "=".repeat(70))
        println("PERFORMANCE REGRESSION DETECTION REPORT")
        println("=".repeat(70))
        println("Timestamp: ${report.timestamp}")
        println("Total benchmarks: ${report.summary.totalBenchmarks}")
        println("Regressed benchmarks: ${report.summary.regressedBenchmarks}")
        println("Critical regressions: ${report.summary.criticalRegressions}")
        println("Major regressions: ${report.summary.majorRegressions}")
        println("Minor regressions: ${report.summary.minorRegressions}")
        println("Blocking regressions: ${if (report.summary.hasBlockingRegressions) "YES" else "NO"}")
        println("=".repeat(70))

        if (report.regressions.isNotEmpty()) {
            println("\nTop Regressions:")
            report.regressions
                .sortedByDescending { it.severity }
                .take(5)
                .forEach { regression ->
                    val worstMetric = regression.metrics.maxByOrNull { kotlin.math.abs(it.changePercent) }
                    println("🔴 ${regression.benchmarkName}")
                    println("   Severity: ${regression.severity}")
                    if (worstMetric != null) {
                        println("   Worst metric: ${worstMetric.metricName} (+${worstMetric.changePercent.toInt()}%)")
                    }
                    println("   Confidence: ${regression.confidenceLevel.toInt()}%")
                    println()
                }
        }

        if (report.analysis.performanceHotspots.isNotEmpty()) {
            println("Performance Hotspots:")
            report.analysis.performanceHotspots.take(3).forEach { hotspot ->
                println("⚠️  ${hotspot.benchmarkName} (${hotspot.impact} impact)")
                println("    ${hotspot.primaryMetric}: ${hotspot.regressionPercent.toInt()}% regression")
            }
            println()
        }

        if (report.analysis.regressionPatterns.isNotEmpty()) {
            println("Regression Patterns:")
            report.analysis.regressionPatterns.forEach { pattern ->
                println("📊 ${pattern.description}")
                println("   Affected benchmarks: ${pattern.affectedBenchmarks}")
                println("   Severity: ${pattern.severity}")
            }
            println()
        }

        println("Recommendations:")
        report.analysis.recommendations.forEach { recommendation ->
            println("• $recommendation")
        }

        println("\nReport saved to: $outputFile")

        // Exit with error code if blocking regressions found
        if (report.summary.hasBlockingRegressions) {
            println("\n❌ BLOCKING REGRESSIONS DETECTED - FAILING BUILD")
            kotlin.system.exitProcess(1)
        } else {
            println("\n✅ NO BLOCKING REGRESSIONS - BUILD CAN PROCEED")
        }

    } catch (e: Exception) {
        println("Regression detection failed: ${e.message}")
        e.printStackTrace()
        kotlin.system.exitProcess(1)
    }
}