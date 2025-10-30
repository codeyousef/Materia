package io.materia.tools.tests.performance

import io.materia.tools.tests.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * PerformanceBenchmark - Comprehensive performance benchmarking and regression detection
 *
 * Provides robust performance testing capabilities including:
 * - CPU, GPU, and memory performance benchmarking
 * - Statistical analysis with confidence intervals
 * - Performance regression detection and alerting
 * - Cross-platform benchmark comparison
 * - Automated baseline management and updates
 * - Performance trend analysis over time
 * - Real-time performance monitoring during tests
 * - Integration with CI/CD for performance gates
 */
class PerformanceBenchmark {

    // Core state flows
    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    private val _benchmarkProgress = MutableStateFlow(BenchmarkProgress.empty())
    val benchmarkProgress: StateFlow<BenchmarkProgress> = _benchmarkProgress.asStateFlow()

    private val _benchmarkResults = MutableStateFlow<List<BenchmarkResult>>(emptyList())
    val benchmarkResults: StateFlow<List<BenchmarkResult>> = _benchmarkResults.asStateFlow()

    // Configuration
    private val _benchmarkConfig = MutableStateFlow(BenchmarkConfig.default())
    val benchmarkConfig: StateFlow<BenchmarkConfig> = _benchmarkConfig.asStateFlow()

    // Baseline management
    private val baselineManager = PerformanceBaselineManager()
    private val statisticalAnalyzer = StatisticalAnalyzer()
    private val regressionDetector = RegressionDetector()

    // Benchmark suites registry
    private val benchmarkSuites = mutableMapOf<String, BenchmarkSuite>()
    private val resultHistory = mutableListOf<BenchmarkResult>()

    init {
        registerDefaultBenchmarks()
    }

    // === BENCHMARK EXECUTION ===

    /**
     * Runs a complete benchmark suite
     */
    suspend fun runBenchmarkSuite(suiteId: String): BenchmarkSuiteResult {
        val suite = benchmarkSuites[suiteId]
            ?: throw IllegalArgumentException("Unknown benchmark suite: $suiteId")

        _isBenchmarking.value = true

        try {
            val startTime = Clock.System.now()
            val results = mutableListOf<BenchmarkResult>()

            updateProgress(BenchmarkProgress(
                totalBenchmarks = suite.benchmarks.size,
                completedBenchmarks = 0,
                currentBenchmark = null,
                startTime = startTime
            ))

            for ((index, benchmark) in suite.benchmarks.withIndex()) {
                updateProgress(_benchmarkProgress.value.copy(
                    completedBenchmarks = index,
                    currentBenchmark = benchmark.name
                ))

                val result = runBenchmark(benchmark)
                results.add(result)

                // Add to history
                resultHistory.add(result)
                updateBenchmarkResults(result)
            }

            val endTime = Clock.System.now()

            return BenchmarkSuiteResult(
                suite = suite,
                results = results,
                startTime = startTime,
                endTime = endTime,
                duration = endTime - startTime,
                overallScore = calculateOverallScore(results),
                regressions = detectRegressions(results)
            )

        } finally {
            _isBenchmarking.value = false
            updateProgress(BenchmarkProgress.empty())
        }
    }

    /**
     * Runs a single benchmark
     */
    suspend fun runBenchmark(benchmark: Benchmark): BenchmarkResult {
        val config = _benchmarkConfig.value
        val startTime = Clock.System.now()

        try {
            // Setup benchmark environment
            setupBenchmarkEnvironment(benchmark)

            // Warm up
            if (config.warmupIterations > 0) {
                repeat(config.warmupIterations) {
                    benchmark.execute()
                }
            }

            // Collect baseline for comparison
            val baseline = baselineManager.getBaseline(benchmark.id)

            // Execute benchmark iterations
            val measurements = mutableListOf<BenchmarkMeasurement>()

            repeat(config.iterations) { iteration ->
                val measurement = executeBenchmarkIteration(benchmark, iteration)
                measurements.add(measurement)

                // Check for early termination if confidence is high enough
                if (iteration >= config.minIterations &&
                    statisticalAnalyzer.hasConverged(measurements, config.confidenceLevel)) {
                    break
                }
            }

            // Analyze results
            val analysis = statisticalAnalyzer.analyze(measurements)

            // Compare with baseline
            val comparison = baseline?.let {
                compareWithBaseline(analysis, it)
            }

            // Detect regression
            val regression = regressionDetector.detect(benchmark.id, analysis, resultHistory)

            val result = BenchmarkResult(
                benchmark = benchmark,
                measurements = measurements,
                analysis = analysis,
                baseline = baseline,
                comparison = comparison,
                regression = regression,
                passed = regression?.severity != RegressionSeverity.CRITICAL,
                startTime = startTime,
                endTime = Clock.System.now(),
                duration = Clock.System.now() - startTime
            )

            // Update baseline if this is a new best or significantly better
            if (shouldUpdateBaseline(result)) {
                baselineManager.updateBaseline(benchmark.id, analysis)
            }

            return result

        } catch (e: Exception) {
            return BenchmarkResult.error(benchmark, e, startTime)
        } finally {
            cleanupBenchmarkEnvironment(benchmark)
        }
    }

    /**
     * Runs a custom performance test
     */
    suspend fun runCustomBenchmark(
        name: String,
        operation: suspend () -> Unit,
        iterations: Int = 100
    ): BenchmarkResult {
        val benchmark = Benchmark(
            id = "custom_$name",
            name = name,
            description = "Custom benchmark: $name",
            category = BenchmarkCategory.CUSTOM,
            execute = operation
        )

        return runBenchmark(benchmark)
    }

    // === BENCHMARK REGISTRATION ===

    /**
     * Registers a benchmark suite
     */
    fun registerBenchmarkSuite(suite: BenchmarkSuite) {
        benchmarkSuites[suite.id] = suite
    }

    /**
     * Registers a single benchmark
     */
    fun registerBenchmark(benchmark: Benchmark) {
        val suiteId = "custom_${benchmark.category.name.lowercase()}"
        val existingSuite = benchmarkSuites[suiteId]

        if (existingSuite != null) {
            benchmarkSuites[suiteId] = existingSuite.copy(
                benchmarks = existingSuite.benchmarks + benchmark
            )
        } else {
            benchmarkSuites[suiteId] = BenchmarkSuite(
                id = suiteId,
                name = "Custom ${benchmark.category.name} Benchmarks",
                description = "Custom benchmarks for ${benchmark.category.name}",
                benchmarks = listOf(benchmark)
            )
        }
    }

    /**
     * Lists all available benchmark suites
     */
    fun getBenchmarkSuites(): List<BenchmarkSuite> {
        return benchmarkSuites.values.toList()
    }

    /**
     * Gets a specific benchmark suite
     */
    fun getBenchmarkSuite(suiteId: String): BenchmarkSuite? {
        return benchmarkSuites[suiteId]
    }

    // === BASELINE MANAGEMENT ===

    /**
     * Creates baseline from current results
     */
    suspend fun createBaseline(benchmarkId: String): Boolean {
        val recentResults = resultHistory.filter { it.benchmark.id == benchmarkId }
            .takeLast(10) // Use last 10 runs for baseline

        if (recentResults.isEmpty()) return false

        val aggregatedAnalysis = statisticalAnalyzer.aggregate(recentResults.map { it.analysis })
        baselineManager.setBaseline(benchmarkId, aggregatedAnalysis)

        return true
    }

    /**
     * Gets baseline for a benchmark
     */
    suspend fun getBaseline(benchmarkId: String): StatisticalAnalysis? {
        return baselineManager.getBaseline(benchmarkId)
    }

    /**
     * Lists all baselines
     */
    suspend fun getBaselines(): Map<String, StatisticalAnalysis> {
        return baselineManager.getAllBaselines()
    }

    // === ANALYSIS AND REPORTING ===

    /**
     * Generates performance report
     */
    fun generatePerformanceReport(
        suiteResults: List<BenchmarkSuiteResult>
    ): PerformanceReport {
        val allResults = suiteResults.flatMap { it.results }
        val regressions = allResults.mapNotNull { it.regression }
        val criticalRegressions = regressions.filter { it.severity == RegressionSeverity.CRITICAL }

        return PerformanceReport(
            timestamp = Clock.System.now(),
            suiteResults = suiteResults,
            totalBenchmarks = allResults.size,
            passedBenchmarks = allResults.count { it.passed },
            failedBenchmarks = allResults.count { !it.passed },
            regressions = regressions,
            criticalRegressions = criticalRegressions,
            overallScore = calculateOverallScore(allResults),
            recommendations = generateRecommendations(allResults)
        )
    }

    /**
     * Exports benchmark data
     */
    fun exportBenchmarkData(format: BenchmarkExportFormat): String {
        return when (format) {
            BenchmarkExportFormat.JSON -> exportToJSON()
            BenchmarkExportFormat.CSV -> exportToCSV()
            BenchmarkExportFormat.XML -> exportToXML()
            BenchmarkExportFormat.MARKDOWN -> exportToMarkdown()
        }
    }

    /**
     * Gets performance trends over time
     */
    fun getPerformanceTrends(
        benchmarkId: String,
        timeRange: Duration = Duration.parse("P30D")
    ): PerformanceTrend {
        val cutoffTime = Clock.System.now() - timeRange
        val relevantResults = resultHistory.filter {
            it.benchmark.id == benchmarkId && it.startTime >= cutoffTime
        }.sortedBy { it.startTime }

        if (relevantResults.size < 2) {
            return PerformanceTrend.stable(benchmarkId)
        }

        val values = relevantResults.map { it.analysis.mean }
        val trend = calculateTrend(values)
        val regression = calculateRegression(values)

        return PerformanceTrend(
            benchmarkId = benchmarkId,
            timeRange = timeRange,
            dataPoints = relevantResults.size,
            trend = trend,
            regression = regression,
            improvement = values.last() - values.first(),
            improvementPercentage = ((values.last() - values.first()) / values.first()) * 100,
            stability = calculateStability(values)
        )
    }

    // === CONFIGURATION ===

    /**
     * Updates benchmark configuration
     */
    fun updateConfig(config: BenchmarkConfig) {
        _benchmarkConfig.value = config
    }

    // === PRIVATE METHODS ===

    private suspend fun executeBenchmarkIteration(
        benchmark: Benchmark,
        iteration: Int
    ): BenchmarkMeasurement {
        val startTime = Clock.System.now()
        val memoryBefore = getCurrentMemoryUsage()

        val executionTime = measureTime {
            benchmark.execute()
        }

        val endTime = Clock.System.now()
        val memoryAfter = getCurrentMemoryUsage()

        return BenchmarkMeasurement(
            iteration = iteration,
            executionTime = executionTime,
            memoryUsage = memoryAfter - memoryBefore,
            cpuUsage = getCurrentCPUUsage(),
            timestamp = startTime,
            duration = endTime - startTime
        )
    }

    private fun setupBenchmarkEnvironment(benchmark: Benchmark) {
        // Setup benchmark-specific environment
        System.gc() // Force garbage collection before benchmark

        when (benchmark.category) {
            BenchmarkCategory.CPU -> setupCPUBenchmarkEnvironment()
            BenchmarkCategory.MEMORY -> setupMemoryBenchmarkEnvironment()
            BenchmarkCategory.GPU -> setupGPUBenchmarkEnvironment()
            BenchmarkCategory.IO -> setupIOBenchmarkEnvironment()
            BenchmarkCategory.NETWORK -> setupNetworkBenchmarkEnvironment()
            BenchmarkCategory.RENDERING -> setupRenderingBenchmarkEnvironment()
            BenchmarkCategory.CUSTOM -> setupCustomBenchmarkEnvironment()
        }
    }

    private fun cleanupBenchmarkEnvironment(benchmark: Benchmark) {
        // Cleanup benchmark environment
        System.gc() // Force garbage collection after benchmark
    }

    private fun setupCPUBenchmarkEnvironment() {
        // Setup CPU benchmark environment
    }

    private fun setupMemoryBenchmarkEnvironment() {
        // Setup memory benchmark environment
    }

    private fun setupGPUBenchmarkEnvironment() {
        // Setup GPU benchmark environment
    }

    private fun setupIOBenchmarkEnvironment() {
        // Setup I/O benchmark environment
    }

    private fun setupNetworkBenchmarkEnvironment() {
        // Setup network benchmark environment
    }

    private fun setupRenderingBenchmarkEnvironment() {
        // Setup rendering benchmark environment
    }

    private fun setupCustomBenchmarkEnvironment() {
        // Setup custom benchmark environment
    }

    private fun compareWithBaseline(
        current: StatisticalAnalysis,
        baseline: StatisticalAnalysis
    ): BaselineComparison {
        val percentageChange = ((current.mean - baseline.mean) / baseline.mean) * 100
        val significantChange = abs(percentageChange) > 5.0 // 5% threshold

        return BaselineComparison(
            baselineMean = baseline.mean,
            currentMean = current.mean,
            percentageChange = percentageChange,
            significantChange = significantChange,
            improvement = percentageChange < 0, // Lower is better for execution time
            confidenceInterval = current.confidenceInterval
        )
    }

    private fun shouldUpdateBaseline(result: BenchmarkResult): Boolean {
        val comparison = result.comparison ?: return false
        return comparison.improvement && comparison.significantChange
    }

    private fun detectRegressions(results: List<BenchmarkResult>): List<PerformanceRegression> {
        return results.mapNotNull { it.regression }
    }

    private fun calculateOverallScore(results: List<BenchmarkResult>): Float {
        if (results.isEmpty()) return 0.0f

        val scores = results.map { result ->
            when {
                result.regression?.severity == RegressionSeverity.CRITICAL -> 0.0f
                result.regression?.severity == RegressionSeverity.MAJOR -> 25.0f
                result.regression?.severity == RegressionSeverity.MINOR -> 75.0f
                result.passed -> 100.0f
                else -> 50.0f
            }
        }

        return scores.average().toFloat()
    }

    private fun generateRecommendations(results: List<BenchmarkResult>): List<String> {
        val recommendations = mutableListOf<String>()

        val regressions = results.mapNotNull { it.regression }
        val criticalRegressions = regressions.filter { it.severity == RegressionSeverity.CRITICAL }

        if (criticalRegressions.isNotEmpty()) {
            recommendations.add("Critical performance regressions detected in ${criticalRegressions.size} benchmarks")
            recommendations.add("Review recent changes that may have impacted performance")
        }

        val highVariance = results.filter { it.analysis.standardDeviation > it.analysis.mean * 0.2 }
        if (highVariance.isNotEmpty()) {
            recommendations.add("High variance detected in ${highVariance.size} benchmarks")
            recommendations.add("Consider increasing iteration count or checking for external factors")
        }

        val slowBenchmarks = results.filter { it.analysis.mean > 1000.0 } // 1 second threshold
        if (slowBenchmarks.isNotEmpty()) {
            recommendations.add("${slowBenchmarks.size} benchmarks are taking over 1 second")
            recommendations.add("Consider optimizing slow operations or increasing timeout thresholds")
        }

        return recommendations
    }

    private fun calculateTrend(values: List<Double>): TrendDirection {
        if (values.size < 2) return TrendDirection.STABLE

        val firstHalf = values.take(values.size / 2).average()
        val secondHalf = values.drop(values.size / 2).average()
        val change = (secondHalf - firstHalf) / firstHalf

        return when {
            change > 0.05 -> TrendDirection.DEGRADING  // 5% worse
            change < -0.05 -> TrendDirection.IMPROVING // 5% better
            else -> TrendDirection.STABLE
        }
    }

    private fun calculateRegression(values: List<Double>): LinearRegression {
        if (values.size < 2) return LinearRegression(0.0, 0.0, 0.0)

        val n = values.size
        val x = (0 until n).map { it.toDouble() }
        val y = values

        val meanX = x.average()
        val meanY = y.average()

        val numerator = x.zip(y) { xi, yi -> (xi - meanX) * (yi - meanY) }.sum()
        val denominator = x.map { (it - meanX).pow(2) }.sum()

        val slope = if (denominator != 0.0) numerator / denominator else 0.0
        val intercept = meanY - slope * meanX

        val predicted = x.map { slope * it + intercept }
        val ssRes = y.zip(predicted) { actual, pred -> (actual - pred).pow(2) }.sum()
        val ssTot = y.map { (it - meanY).pow(2) }.sum()
        val rSquared = if (ssTot != 0.0) 1 - (ssRes / ssTot) else 0.0

        return LinearRegression(slope, intercept, rSquared)
    }

    private fun calculateStability(values: List<Double>): Float {
        if (values.size < 2) return 1.0f

        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        val standardDeviation = sqrt(variance)
        val coefficientOfVariation = if (mean != 0.0) standardDeviation / mean else 0.0

        // Convert CV to stability score (lower CV = higher stability)
        return (1.0 / (1.0 + coefficientOfVariation)).toFloat()
    }

    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun getCurrentCPUUsage(): Float {
        // Platform-specific CPU usage collection
        return 0.0f // Placeholder
    }

    private fun updateProgress(progress: BenchmarkProgress) {
        _benchmarkProgress.value = progress
    }

    private fun updateBenchmarkResults(result: BenchmarkResult) {
        val currentResults = _benchmarkResults.value.toMutableList()
        val existingIndex = currentResults.indexOfFirst { it.benchmark.id == result.benchmark.id }

        if (existingIndex >= 0) {
            currentResults[existingIndex] = result
        } else {
            currentResults.add(result)
        }

        _benchmarkResults.value = currentResults
    }

    private fun registerDefaultBenchmarks() {
        // Register default benchmark suites
        registerBenchmarkSuite(createCPUBenchmarkSuite())
        registerBenchmarkSuite(createMemoryBenchmarkSuite())
        registerBenchmarkSuite(createRenderingBenchmarkSuite())
    }

    private fun createCPUBenchmarkSuite(): BenchmarkSuite {
        return BenchmarkSuite(
            id = "cpu_benchmarks",
            name = "CPU Performance Benchmarks",
            description = "Benchmarks for CPU-intensive operations",
            benchmarks = listOf(
                Benchmark(
                    id = "cpu_fibonacci",
                    name = "Fibonacci Calculation",
                    description = "Recursive fibonacci calculation benchmark",
                    category = BenchmarkCategory.CPU,
                    execute = { fibonacci(30) }
                ),
                Benchmark(
                    id = "cpu_prime_generation",
                    name = "Prime Number Generation",
                    description = "Prime number generation benchmark",
                    category = BenchmarkCategory.CPU,
                    execute = { generatePrimes(10000) }
                )
            )
        )
    }

    private fun createMemoryBenchmarkSuite(): BenchmarkSuite {
        return BenchmarkSuite(
            id = "memory_benchmarks",
            name = "Memory Performance Benchmarks",
            description = "Benchmarks for memory allocation and GC performance",
            benchmarks = listOf(
                Benchmark(
                    id = "memory_allocation",
                    name = "Memory Allocation",
                    description = "Large array allocation benchmark",
                    category = BenchmarkCategory.MEMORY,
                    execute = { allocateMemory() }
                )
            )
        )
    }

    private fun createRenderingBenchmarkSuite(): BenchmarkSuite {
        return BenchmarkSuite(
            id = "rendering_benchmarks",
            name = "Rendering Performance Benchmarks",
            description = "Benchmarks for 3D rendering performance",
            benchmarks = listOf(
                Benchmark(
                    id = "render_triangles",
                    name = "Triangle Rendering",
                    description = "Render 1000 triangles benchmark",
                    category = BenchmarkCategory.RENDERING,
                    execute = { renderTriangles(1000) }
                )
            )
        )
    }

    // Benchmark implementations
    private fun fibonacci(n: Int): Int {
        return if (n <= 1) n else fibonacci(n - 1) + fibonacci(n - 2)
    }

    private fun generatePrimes(limit: Int): List<Int> {
        val primes = mutableListOf<Int>()
        for (i in 2..limit) {
            if (isPrime(i)) primes.add(i)
        }
        return primes
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        for (i in 2..sqrt(n.toDouble()).toInt()) {
            if (n % i == 0) return false
        }
        return true
    }

    private fun allocateMemory() {
        val arrays = mutableListOf<IntArray>()
        repeat(100) {
            arrays.add(IntArray(10000) { it })
        }
    }

    private fun renderTriangles(count: Int) {
        // Simulate triangle rendering
        repeat(count) {
            // Simulate vertex processing
            val vertices = floatArrayOf(0f, 1f, 0f, -1f, -1f, 0f, 1f, -1f, 0f)
            // Simulate some computation
            vertices.map { it * 2.0f }.sum()
        }
    }

    private fun exportToJSON(): String {
        // Export benchmark results to JSON
        return "{}"
    }

    private fun exportToCSV(): String {
        // Export benchmark results to CSV
        return ""
    }

    private fun exportToXML(): String {
        // Export benchmark results to XML
        return ""
    }

    private fun exportToMarkdown(): String {
        // Export benchmark results to Markdown
        return ""
    }
}

// === ENUMS ===

enum class BenchmarkCategory {
    CPU, MEMORY, GPU, IO, NETWORK, RENDERING, CUSTOM
}

enum class RegressionSeverity {
    NONE, MINOR, MAJOR, CRITICAL
}

enum class TrendDirection {
    IMPROVING, STABLE, DEGRADING
}

enum class BenchmarkExportFormat {
    JSON, CSV, XML, MARKDOWN
}

// === DATA CLASSES ===

data class BenchmarkConfig(
    val iterations: Int,
    val minIterations: Int,
    val warmupIterations: Int,
    val confidenceLevel: Float,
    val regressionThreshold: Float,
    val timeoutSeconds: Int
) {
    companion object {
        fun default() = BenchmarkConfig(
            iterations = 100,
            minIterations = 10,
            warmupIterations = 5,
            confidenceLevel = 0.95f,
            regressionThreshold = 10.0f, // 10% threshold
            timeoutSeconds = 300
        )
    }
}

data class Benchmark(
    val id: String,
    val name: String,
    val description: String,
    val category: BenchmarkCategory,
    val execute: suspend () -> Unit
)

data class BenchmarkSuite(
    val id: String,
    val name: String,
    val description: String,
    val benchmarks: List<Benchmark>
)

data class BenchmarkMeasurement(
    val iteration: Int,
    val executionTime: Duration,
    val memoryUsage: Long,
    val cpuUsage: Float,
    val timestamp: Instant,
    val duration: Duration
)

data class StatisticalAnalysis(
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val min: Double,
    val max: Double,
    val percentile95: Double,
    val percentile99: Double,
    val sampleSize: Int,
    val confidenceInterval: ConfidenceInterval
)

data class ConfidenceInterval(
    val level: Float,
    val lowerBound: Double,
    val upperBound: Double
)

data class BaselineComparison(
    val baselineMean: Double,
    val currentMean: Double,
    val percentageChange: Double,
    val significantChange: Boolean,
    val improvement: Boolean,
    val confidenceInterval: ConfidenceInterval
)

data class PerformanceRegression(
    val benchmarkId: String,
    val severity: RegressionSeverity,
    val percentageIncrease: Double,
    val description: String,
    val detectedAt: Instant
)

data class BenchmarkResult(
    val benchmark: Benchmark,
    val measurements: List<BenchmarkMeasurement>,
    val analysis: StatisticalAnalysis,
    val baseline: StatisticalAnalysis?,
    val comparison: BaselineComparison?,
    val regression: PerformanceRegression?,
    val passed: Boolean,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration
) {
    companion object {
        fun error(benchmark: Benchmark, exception: Throwable, startTime: Instant): BenchmarkResult {
            return BenchmarkResult(
                benchmark = benchmark,
                measurements = emptyList(),
                analysis = StatisticalAnalysis(
                    mean = 0.0, median = 0.0, standardDeviation = 0.0,
                    min = 0.0, max = 0.0, percentile95 = 0.0, percentile99 = 0.0,
                    sampleSize = 0,
                    confidenceInterval = ConfidenceInterval(0.95f, 0.0, 0.0)
                ),
                baseline = null,
                comparison = null,
                regression = PerformanceRegression(
                    benchmarkId = benchmark.id,
                    severity = RegressionSeverity.CRITICAL,
                    percentageIncrease = 100.0,
                    description = "Benchmark failed: ${exception.message}",
                    detectedAt = startTime
                ),
                passed = false,
                startTime = startTime,
                endTime = Clock.System.now(),
                duration = Clock.System.now() - startTime
            )
        }
    }
}

data class BenchmarkSuiteResult(
    val suite: BenchmarkSuite,
    val results: List<BenchmarkResult>,
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration,
    val overallScore: Float,
    val regressions: List<PerformanceRegression>
)

data class BenchmarkProgress(
    val totalBenchmarks: Int,
    val completedBenchmarks: Int,
    val currentBenchmark: String?,
    val startTime: Instant,
    val estimatedTimeRemaining: Duration? = null
) {
    val progressPercentage: Float
        get() = if (totalBenchmarks > 0) completedBenchmarks.toFloat() / totalBenchmarks else 0.0f

    companion object {
        fun empty() = BenchmarkProgress(
            totalBenchmarks = 0,
            completedBenchmarks = 0,
            currentBenchmark = null,
            startTime = Clock.System.now()
        )
    }
}

data class PerformanceReport(
    val timestamp: Instant,
    val suiteResults: List<BenchmarkSuiteResult>,
    val totalBenchmarks: Int,
    val passedBenchmarks: Int,
    val failedBenchmarks: Int,
    val regressions: List<PerformanceRegression>,
    val criticalRegressions: List<PerformanceRegression>,
    val overallScore: Float,
    val recommendations: List<String>
)

data class PerformanceTrend(
    val benchmarkId: String,
    val timeRange: Duration,
    val dataPoints: Int,
    val trend: TrendDirection,
    val regression: LinearRegression,
    val improvement: Double,
    val improvementPercentage: Double,
    val stability: Float
) {
    companion object {
        fun stable(benchmarkId: String) = PerformanceTrend(
            benchmarkId = benchmarkId,
            timeRange = Duration.ZERO,
            dataPoints = 0,
            trend = TrendDirection.STABLE,
            regression = LinearRegression(0.0, 0.0, 0.0),
            improvement = 0.0,
            improvementPercentage = 0.0,
            stability = 1.0f
        )
    }
}

data class LinearRegression(
    val slope: Double,
    val intercept: Double,
    val rSquared: Double
)

// === HELPER CLASSES ===

class PerformanceBaselineManager {
    private val baselines = mutableMapOf<String, StatisticalAnalysis>()

    fun setBaseline(benchmarkId: String, analysis: StatisticalAnalysis) {
        baselines[benchmarkId] = analysis
    }

    fun getBaseline(benchmarkId: String): StatisticalAnalysis? {
        return baselines[benchmarkId]
    }

    fun getAllBaselines(): Map<String, StatisticalAnalysis> {
        return baselines.toMap()
    }

    fun updateBaseline(benchmarkId: String, analysis: StatisticalAnalysis) {
        baselines[benchmarkId] = analysis
    }
}

class StatisticalAnalyzer {
    fun analyze(measurements: List<BenchmarkMeasurement>): StatisticalAnalysis {
        val values = measurements.map { it.executionTime.inWholeNanoseconds.toDouble() }
        val sorted = values.sorted()

        val mean = values.average()
        val median = sorted[sorted.size / 2]
        val variance = values.map { (it - mean).pow(2) }.average()
        val standardDeviation = sqrt(variance)

        val confidenceInterval = calculateConfidenceInterval(values, 0.95f)

        return StatisticalAnalysis(
            mean = mean,
            median = median,
            standardDeviation = standardDeviation,
            min = sorted.first(),
            max = sorted.last(),
            percentile95 = sorted[(sorted.size * 0.95).toInt()],
            percentile99 = sorted[(sorted.size * 0.99).toInt()],
            sampleSize = values.size,
            confidenceInterval = confidenceInterval
        )
    }

    fun hasConverged(measurements: List<BenchmarkMeasurement>, confidenceLevel: Float): Boolean {
        if (measurements.size < 10) return false

        val analysis = analyze(measurements)
        val marginOfError = analysis.confidenceInterval.upperBound - analysis.mean
        val relativeError = marginOfError / analysis.mean

        return relativeError < 0.05 // 5% relative error threshold
    }

    fun aggregate(analyses: List<StatisticalAnalysis>): StatisticalAnalysis {
        val means = analyses.map { it.mean }
        val overallMean = means.average()

        return StatisticalAnalysis(
            mean = overallMean,
            median = means.sorted()[means.size / 2],
            standardDeviation = sqrt(means.map { (it - overallMean).pow(2) }.average()),
            min = means.minOrNull() ?: 0.0,
            max = means.maxOrNull() ?: 0.0,
            percentile95 = means.sorted()[(means.size * 0.95).toInt()],
            percentile99 = means.sorted()[(means.size * 0.99).toInt()],
            sampleSize = analyses.sumOf { it.sampleSize },
            confidenceInterval = calculateConfidenceInterval(means, 0.95f)
        )
    }

    private fun calculateConfidenceInterval(values: List<Double>, level: Float): ConfidenceInterval {
        val mean = values.average()
        val standardError = sqrt(values.map { (it - mean).pow(2) }.average() / values.size)
        val margin = 1.96 * standardError // 95% confidence interval

        return ConfidenceInterval(
            level = level,
            lowerBound = mean - margin,
            upperBound = mean + margin
        )
    }
}

class RegressionDetector {
    fun detect(
        benchmarkId: String,
        current: StatisticalAnalysis,
        history: List<BenchmarkResult>
    ): PerformanceRegression? {
        val recentResults = history.filter { it.benchmark.id == benchmarkId }
            .takeLast(10) // Look at last 10 results

        if (recentResults.isEmpty()) return null

        val recentMean = recentResults.map { it.analysis.mean }.average()
        val percentageIncrease = ((current.mean - recentMean) / recentMean) * 100

        val severity = when {
            percentageIncrease > 50.0 -> RegressionSeverity.CRITICAL
            percentageIncrease > 25.0 -> RegressionSeverity.MAJOR
            percentageIncrease > 10.0 -> RegressionSeverity.MINOR
            else -> RegressionSeverity.NONE
        }

        return if (severity != RegressionSeverity.NONE) {
            PerformanceRegression(
                benchmarkId = benchmarkId,
                severity = severity,
                percentageIncrease = percentageIncrease,
                description = "Performance degraded by ${percentageIncrease.format(1)}%",
                detectedAt = Clock.System.now()
            )
        } else null
    }

    private fun Double.format(decimals: Int): String {
        return "%.${decimals}f".format(this)
    }
}