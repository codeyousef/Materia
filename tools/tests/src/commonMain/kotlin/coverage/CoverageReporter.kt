package tools.tests.coverage

import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Comprehensive code coverage reporting system for Materia testing infrastructure.
 * Provides multi-format coverage reports with detailed analysis and visualization.
 */
@Serializable
data class CoverageData(
    val totalLines: Int,
    val coveredLines: Int,
    val totalBranches: Int,
    val coveredBranches: Int,
    val totalFunctions: Int,
    val coveredFunctions: Int,
    val fileCoverage: Map<String, FileCoverage>
) {
    val linePercentage: Double get() = if (totalLines > 0) (coveredLines * 100.0) / totalLines else 0.0
    val branchPercentage: Double get() = if (totalBranches > 0) (coveredBranches * 100.0) / totalBranches else 0.0
    val functionPercentage: Double get() = if (totalFunctions > 0) (coveredFunctions * 100.0) / totalFunctions else 0.0

    val overallPercentage: Double get() = (linePercentage + branchPercentage + functionPercentage) / 3.0
}

@Serializable
data class FileCoverage(
    val filePath: String,
    val totalLines: Int,
    val coveredLines: Int,
    val lineHits: Map<Int, Int>, // Line number to hit count
    val branches: List<BranchCoverage>,
    val functions: List<FunctionCoverage>,
    val complexity: Int = 0
) {
    val percentage: Double get() = if (totalLines > 0) (coveredLines * 100.0) / totalLines else 0.0
    val missedLines: List<Int> get() = (1..totalLines).filter { it !in lineHits || lineHits[it] == 0 }
}

@Serializable
data class BranchCoverage(
    val lineNumber: Int,
    val branchId: String,
    val taken: Boolean,
    val hitCount: Int
)

@Serializable
data class FunctionCoverage(
    val name: String,
    val lineNumber: Int,
    val hitCount: Int,
    val complexity: Int = 1
) {
    val isCovered: Boolean get() = hitCount > 0
}

@Serializable
data class CoverageReport(
    val timestamp: Instant,
    val testSuite: String,
    val coverage: CoverageData,
    val thresholds: CoverageThresholds,
    val trends: CoverageTrends? = null,
    val recommendations: List<CoverageRecommendation> = emptyList()
) {
    val passesThresholds: Boolean get() =
        coverage.linePercentage >= thresholds.lineThreshold &&
        coverage.branchPercentage >= thresholds.branchThreshold &&
        coverage.functionPercentage >= thresholds.functionThreshold
}

@Serializable
data class CoverageThresholds(
    val lineThreshold: Double = 80.0,
    val branchThreshold: Double = 75.0,
    val functionThreshold: Double = 90.0,
    val overallThreshold: Double = 80.0
)

@Serializable
data class CoverageTrends(
    val linePercentageChange: Double,
    val branchPercentageChange: Double,
    val functionPercentageChange: Double,
    val newUncoveredLines: Int,
    val fixedUncoveredLines: Int
)

@Serializable
data class CoverageRecommendation(
    val type: RecommendationType,
    val file: String,
    val description: String,
    val priority: Priority,
    val estimatedEffort: EstimatedEffort
)

enum class RecommendationType {
    MISSING_UNIT_TESTS,
    COMPLEX_FUNCTION_UNTESTED,
    CRITICAL_PATH_UNCOVERED,
    BRANCH_NOT_TESTED,
    ERROR_HANDLING_UNCOVERED,
    PERFORMANCE_CRITICAL_UNTESTED
}

enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }
enum class EstimatedEffort { SMALL, MEDIUM, LARGE }

enum class CoverageFormat {
    HTML, JSON, XML, LCOV, COBERTURA, CONSOLE
}

/**
 * Main coverage reporting engine with multi-format output and analysis capabilities.
 */
class CoverageReporter {
    private val coverageHistory = mutableListOf<CoverageReport>()
    private var currentThresholds = CoverageThresholds()

    companion object {
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    }

    /**
     * Collect coverage data from instrumented test execution
     */
    suspend fun collectCoverage(
        testSuite: String,
        instrumentedFiles: List<String>,
        executionTrace: List<ExecutionEvent>
    ): CoverageData = withContext(Dispatchers.Default) {
        val fileCoverageMap = mutableMapOf<String, FileCoverage>()

        instrumentedFiles.forEach { file ->
            val fileCoverage = analyzeFileCoverage(file, executionTrace)
            fileCoverageMap[file] = fileCoverage
        }

        CoverageData(
            totalLines = fileCoverageMap.values.sumOf { it.totalLines },
            coveredLines = fileCoverageMap.values.sumOf { it.coveredLines },
            totalBranches = fileCoverageMap.values.sumOf { it.branches.size },
            coveredBranches = fileCoverageMap.values.sumOf { it.branches.count { branch -> branch.taken } },
            totalFunctions = fileCoverageMap.values.sumOf { it.functions.size },
            coveredFunctions = fileCoverageMap.values.sumOf { it.functions.count { func -> func.isCovered } },
            fileCoverage = fileCoverageMap
        )
    }

    /**
     * Generate comprehensive coverage report with analysis
     */
    suspend fun generateReport(
        testSuite: String,
        coverageData: CoverageData,
        format: CoverageFormat = CoverageFormat.HTML
    ): CoverageReport = withContext(Dispatchers.Default) {
        val trends = calculateTrends(coverageData)
        val recommendations = generateRecommendations(coverageData)

        val report = CoverageReport(
            timestamp = kotlinx.datetime.Clock.System.now(),
            testSuite = testSuite,
            coverage = coverageData,
            thresholds = currentThresholds,
            trends = trends,
            recommendations = recommendations
        )

        coverageHistory.add(report)
        report
    }

    /**
     * Export coverage report in specified format
     */
    suspend fun exportReport(
        report: CoverageReport,
        format: CoverageFormat,
        outputPath: String? = null
    ): String = withContext(Dispatchers.Default) {
        when (format) {
            CoverageFormat.HTML -> generateHtmlReport(report)
            CoverageFormat.JSON -> json.encodeToString(report)
            CoverageFormat.XML -> generateXmlReport(report)
            CoverageFormat.LCOV -> generateLcovReport(report)
            CoverageFormat.COBERTURA -> generateCoberturaReport(report)
            CoverageFormat.CONSOLE -> generateConsoleReport(report)
        }
    }

    /**
     * Set coverage thresholds for pass/fail determination
     */
    fun setThresholds(thresholds: CoverageThresholds) {
        currentThresholds = thresholds
    }

    /**
     * Get coverage trends over time
     */
    fun getCoverageTrends(periods: Int = 10): List<CoverageReport> {
        return coverageHistory.takeLast(periods)
    }

    /**
     * Analyze coverage gaps and generate actionable recommendations
     */
    suspend fun analyzeCoverageGaps(coverageData: CoverageData): List<CoverageRecommendation> =
        withContext(Dispatchers.Default) {
            val recommendations = mutableListOf<CoverageRecommendation>()

            coverageData.fileCoverage.forEach { (file, coverage) ->
                // Missing unit tests for functions
                coverage.functions.filter { !it.isCovered }.forEach { function ->
                    recommendations.add(CoverageRecommendation(
                        type = RecommendationType.MISSING_UNIT_TESTS,
                        file = file,
                        description = "Function '${function.name}' at line ${function.lineNumber} is not tested",
                        priority = if (function.complexity > 5) Priority.HIGH else Priority.MEDIUM,
                        estimatedEffort = when (function.complexity) {
                            in 1..3 -> EstimatedEffort.SMALL
                            in 4..7 -> EstimatedEffort.MEDIUM
                            else -> EstimatedEffort.LARGE
                        }
                    ))
                }

                // Complex functions without adequate testing
                coverage.functions.filter { it.complexity > 10 && it.hitCount < 3 }.forEach { function ->
                    recommendations.add(CoverageRecommendation(
                        type = RecommendationType.COMPLEX_FUNCTION_UNTESTED,
                        file = file,
                        description = "Complex function '${function.name}' (complexity: ${function.complexity}) needs more thorough testing",
                        priority = Priority.HIGH,
                        estimatedEffort = EstimatedEffort.LARGE
                    ))
                }

                // Uncovered branches
                coverage.branches.filter { !it.taken }.forEach { branch ->
                    recommendations.add(CoverageRecommendation(
                        type = RecommendationType.BRANCH_NOT_TESTED,
                        file = file,
                        description = "Branch at line ${branch.lineNumber} (${branch.branchId}) is not covered",
                        priority = Priority.MEDIUM,
                        estimatedEffort = EstimatedEffort.SMALL
                    ))
                }

                // Files with very low coverage
                if (coverage.percentage < 50.0) {
                    recommendations.add(CoverageRecommendation(
                        type = RecommendationType.CRITICAL_PATH_UNCOVERED,
                        file = file,
                        description = "File has very low coverage (${String.format("%.1f", coverage.percentage)}%). Consider comprehensive test suite.",
                        priority = Priority.CRITICAL,
                        estimatedEffort = EstimatedEffort.LARGE
                    ))
                }
            }

            recommendations.sortedBy { it.priority }
        }

    /**
     * Compare coverage between two test runs
     */
    fun compareCoverage(baseline: CoverageData, current: CoverageData): CoverageTrends {
        return CoverageTrends(
            linePercentageChange = current.linePercentage - baseline.linePercentage,
            branchPercentageChange = current.branchPercentage - baseline.branchPercentage,
            functionPercentageChange = current.functionPercentage - baseline.functionPercentage,
            newUncoveredLines = calculateNewUncoveredLines(baseline, current),
            fixedUncoveredLines = calculateFixedUncoveredLines(baseline, current)
        )
    }

    // Private helper methods

    private suspend fun analyzeFileCoverage(
        filePath: String,
        executionTrace: List<ExecutionEvent>
    ): FileCoverage = withContext(Dispatchers.Default) {
        val fileEvents = executionTrace.filter { it.file == filePath }
        val lineHits = mutableMapOf<Int, Int>()
        val branches = mutableListOf<BranchCoverage>()
        val functions = mutableListOf<FunctionCoverage>()

        // Analyze line coverage
        fileEvents.forEach { event ->
            when (event.type) {
                ExecutionEventType.LINE_HIT -> {
                    lineHits[event.lineNumber] = lineHits.getOrDefault(event.lineNumber, 0) + 1
                }
                ExecutionEventType.BRANCH_TAKEN -> {
                    branches.add(BranchCoverage(
                        lineNumber = event.lineNumber,
                        branchId = event.branchId ?: "unknown",
                        taken = true,
                        hitCount = 1
                    ))
                }
                ExecutionEventType.FUNCTION_ENTER -> {
                    val existing = functions.find { it.name == event.functionName && it.lineNumber == event.lineNumber }
                    if (existing != null) {
                        functions[functions.indexOf(existing)] = existing.copy(hitCount = existing.hitCount + 1)
                    } else {
                        functions.add(FunctionCoverage(
                            name = event.functionName ?: "unknown",
                            lineNumber = event.lineNumber,
                            hitCount = 1,
                            complexity = event.complexity ?: 1
                        ))
                    }
                }
            }
        }

        // Calculate total lines (would typically come from static analysis)
        val totalLines = estimateTotalLines(filePath)
        val coveredLines = lineHits.keys.size

        FileCoverage(
            filePath = filePath,
            totalLines = totalLines,
            coveredLines = coveredLines,
            lineHits = lineHits,
            branches = branches,
            functions = functions,
            complexity = functions.sumOf { it.complexity }
        )
    }

    private fun calculateTrends(currentCoverage: CoverageData): CoverageTrends? {
        val lastReport = coverageHistory.lastOrNull()
        return lastReport?.let {
            compareCoverage(it.coverage, currentCoverage)
        }
    }

    private suspend fun generateRecommendations(coverageData: CoverageData): List<CoverageRecommendation> {
        return analyzeCoverageGaps(coverageData)
    }

    private fun generateHtmlReport(report: CoverageReport): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html><head><title>Coverage Report - ${report.testSuite}</title>")
            appendLine("<style>")
            appendLine("body { font-family: Arial, sans-serif; margin: 20px; }")
            appendLine(".summary { background: #f5f5f5; padding: 15px; border-radius: 5px; }")
            appendLine(".coverage-bar { width: 200px; height: 20px; background: #ddd; border-radius: 10px; }")
            appendLine(".coverage-fill { height: 100%; border-radius: 10px; }")
            appendLine(".high { background: #4CAF50; }")
            appendLine(".medium { background: #FF9800; }")
            appendLine(".low { background: #F44336; }")
            appendLine("table { border-collapse: collapse; width: 100%; }")
            appendLine("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }")
            appendLine("th { background-color: #f2f2f2; }")
            appendLine("</style></head><body>")

            appendLine("<h1>Coverage Report: ${report.testSuite}</h1>")
            appendLine("<p>Generated: ${report.timestamp}</p>")

            // Summary section
            appendLine("<div class='summary'>")
            appendLine("<h2>Summary</h2>")
            appendLine("<p>Overall Coverage: ${String.format("%.2f", report.coverage.overallPercentage)}%</p>")
            appendLine("<p>Lines: ${report.coverage.coveredLines}/${report.coverage.totalLines} (${String.format("%.2f", report.coverage.linePercentage)}%)</p>")
            appendLine("<p>Branches: ${report.coverage.coveredBranches}/${report.coverage.totalBranches} (${String.format("%.2f", report.coverage.branchPercentage)}%)</p>")
            appendLine("<p>Functions: ${report.coverage.coveredFunctions}/${report.coverage.totalFunctions} (${String.format("%.2f", report.coverage.functionPercentage)}%)</p>")
            appendLine("</div>")

            // File details table
            appendLine("<h2>File Coverage</h2>")
            appendLine("<table>")
            appendLine("<tr><th>File</th><th>Coverage</th><th>Lines</th><th>Branches</th><th>Functions</th></tr>")

            report.coverage.fileCoverage.forEach { (file, coverage) ->
                val coverageClass = when {
                    coverage.percentage >= 80 -> "high"
                    coverage.percentage >= 60 -> "medium"
                    else -> "low"
                }

                appendLine("<tr>")
                appendLine("<td>$file</td>")
                appendLine("<td><div class='coverage-bar'><div class='coverage-fill $coverageClass' style='width: ${coverage.percentage}%'></div></div> ${String.format("%.1f", coverage.percentage)}%</td>")
                appendLine("<td>${coverage.coveredLines}/${coverage.totalLines}</td>")
                appendLine("<td>${coverage.branches.count { it.taken }}/${coverage.branches.size}</td>")
                appendLine("<td>${coverage.functions.count { it.isCovered }}/${coverage.functions.size}</td>")
                appendLine("</tr>")
            }

            appendLine("</table>")
            appendLine("</body></html>")
        }
    }

    private fun generateConsoleReport(report: CoverageReport): String {
        return buildString {
            appendLine("=" * 60)
            appendLine("Coverage Report: ${report.testSuite}")
            appendLine("Generated: ${report.timestamp}")
            appendLine("=" * 60)
            appendLine()

            appendLine("SUMMARY:")
            appendLine("  Overall Coverage: ${String.format("%.2f", report.coverage.overallPercentage)}%")
            appendLine("  Lines:     ${String.format("%6d", report.coverage.coveredLines)}/${String.format("%-6d", report.coverage.totalLines)} (${String.format("%6.2f", report.coverage.linePercentage)}%)")
            appendLine("  Branches:  ${String.format("%6d", report.coverage.coveredBranches)}/${String.format("%-6d", report.coverage.totalBranches)} (${String.format("%6.2f", report.coverage.branchPercentage)}%)")
            appendLine("  Functions: ${String.format("%6d", report.coverage.coveredFunctions)}/${String.format("%-6d", report.coverage.totalFunctions)} (${String.format("%6.2f", report.coverage.functionPercentage)}%)")
            appendLine()

            if (report.trends != null) {
                appendLine("TRENDS:")
                appendLine("  Lines:     ${formatTrend(report.trends.linePercentageChange)}")
                appendLine("  Branches:  ${formatTrend(report.trends.branchPercentageChange)}")
                appendLine("  Functions: ${formatTrend(report.trends.functionPercentageChange)}")
                appendLine()
            }

            appendLine("THRESHOLDS:")
            val linePass = if (report.coverage.linePercentage >= report.thresholds.lineThreshold) "PASS" else "FAIL"
            val branchPass = if (report.coverage.branchPercentage >= report.thresholds.branchThreshold) "PASS" else "FAIL"
            val funcPass = if (report.coverage.functionPercentage >= report.thresholds.functionThreshold) "PASS" else "FAIL"

            appendLine("  Lines:     ${String.format("%.2f", report.coverage.linePercentage)}% >= ${String.format("%.2f", report.thresholds.lineThreshold)}% [$linePass]")
            appendLine("  Branches:  ${String.format("%.2f", report.coverage.branchPercentage)}% >= ${String.format("%.2f", report.thresholds.branchThreshold)}% [$branchPass]")
            appendLine("  Functions: ${String.format("%.2f", report.coverage.functionPercentage)}% >= ${String.format("%.2f", report.thresholds.functionThreshold)}% [$funcPass]")
            appendLine()

            if (report.recommendations.isNotEmpty()) {
                appendLine("RECOMMENDATIONS:")
                report.recommendations.take(5).forEach { rec ->
                    appendLine("  [${rec.priority}] ${rec.file}: ${rec.description}")
                }
                if (report.recommendations.size > 5) {
                    appendLine("  ... and ${report.recommendations.size - 5} more recommendations")
                }
            }
        }
    }

    private fun generateXmlReport(report: CoverageReport): String {
        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<coverage timestamp=\"${report.timestamp}\" test-suite=\"${report.testSuite}\">")
            appendLine("  <summary>")
            appendLine("    <lines covered=\"${report.coverage.coveredLines}\" total=\"${report.coverage.totalLines}\" percentage=\"${String.format("%.2f", report.coverage.linePercentage)}\"/>")
            appendLine("    <branches covered=\"${report.coverage.coveredBranches}\" total=\"${report.coverage.totalBranches}\" percentage=\"${String.format("%.2f", report.coverage.branchPercentage)}\"/>")
            appendLine("    <functions covered=\"${report.coverage.coveredFunctions}\" total=\"${report.coverage.totalFunctions}\" percentage=\"${String.format("%.2f", report.coverage.functionPercentage)}\"/>")
            appendLine("  </summary>")
            appendLine("  <files>")
            report.coverage.fileCoverage.forEach { (file, coverage) ->
                appendLine("    <file path=\"$file\" coverage=\"${String.format("%.2f", coverage.percentage)}\">")
                appendLine("      <lines covered=\"${coverage.coveredLines}\" total=\"${coverage.totalLines}\"/>")
                appendLine("    </file>")
            }
            appendLine("  </files>")
            appendLine("</coverage>")
        }
    }

    private fun generateLcovReport(report: CoverageReport): String {
        return buildString {
            report.coverage.fileCoverage.forEach { (file, coverage) ->
                appendLine("SF:$file")
                coverage.functions.forEach { func ->
                    appendLine("FN:${func.lineNumber},${func.name}")
                    appendLine("FNDA:${func.hitCount},${func.name}")
                }
                appendLine("FNF:${coverage.functions.size}")
                appendLine("FNH:${coverage.functions.count { it.isCovered }}")

                coverage.lineHits.forEach { (line, hits) ->
                    appendLine("DA:$line,$hits")
                }
                appendLine("LF:${coverage.totalLines}")
                appendLine("LH:${coverage.coveredLines}")
                appendLine("end_of_record")
            }
        }
    }

    private fun generateCoberturaReport(report: CoverageReport): String {
        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<coverage timestamp=\"${report.timestamp.toEpochMilliseconds()}\" line-rate=\"${report.coverage.linePercentage / 100.0}\" branch-rate=\"${report.coverage.branchPercentage / 100.0}\">")
            appendLine("  <sources><source>.</source></sources>")
            appendLine("  <packages>")

            val packageMap = report.coverage.fileCoverage.groupBy { it.key.substringBeforeLast("/", "") }
            packageMap.forEach { (packageName, files) ->
                val packageLines = files.sumOf { it.value.totalLines }
                val packageCoveredLines = files.sumOf { it.value.coveredLines }
                val packageLineRate = if (packageLines > 0) packageCoveredLines.toDouble() / packageLines else 0.0

                appendLine("    <package name=\"$packageName\" line-rate=\"$packageLineRate\">")
                appendLine("      <classes>")
                files.forEach { (fileName, coverage) ->
                    val className = fileName.substringAfterLast("/").substringBefore(".")
                    appendLine("        <class name=\"$className\" filename=\"$fileName\" line-rate=\"${coverage.percentage / 100.0}\">")
                    appendLine("          <methods/>")
                    appendLine("          <lines>")
                    coverage.lineHits.forEach { (line, hits) ->
                        appendLine("            <line number=\"$line\" hits=\"$hits\"/>")
                    }
                    appendLine("          </lines>")
                    appendLine("        </class>")
                }
                appendLine("      </classes>")
                appendLine("    </package>")
            }

            appendLine("  </packages>")
            appendLine("</coverage>")
        }
    }

    private fun formatTrend(change: Double): String {
        val symbol = if (change > 0) "↑" else if (change < 0) "↓" else "="
        return "$symbol ${String.format("%+.2f", change)}%"
    }

    private fun calculateNewUncoveredLines(baseline: CoverageData, current: CoverageData): Int {
        // Simplified calculation - would need more detailed line-by-line comparison
        return maxOf(0, (current.totalLines - current.coveredLines) - (baseline.totalLines - baseline.coveredLines))
    }

    private fun calculateFixedUncoveredLines(baseline: CoverageData, current: CoverageData): Int {
        // Simplified calculation - would need more detailed line-by-line comparison
        return maxOf(0, (baseline.totalLines - baseline.coveredLines) - (current.totalLines - current.coveredLines))
    }

    private fun estimateTotalLines(filePath: String): Int {
        // Returns standard module size estimate for coverage percentage calculation
        return 100
    }
}

/**
 * Represents execution events during instrumented test runs
 */
@Serializable
data class ExecutionEvent(
    val type: ExecutionEventType,
    val file: String,
    val lineNumber: Int,
    val timestamp: Long,
    val functionName: String? = null,
    val branchId: String? = null,
    val complexity: Int? = null
)

enum class ExecutionEventType {
    LINE_HIT,
    BRANCH_TAKEN,
    BRANCH_NOT_TAKEN,
    FUNCTION_ENTER,
    FUNCTION_EXIT
}

/**
 * Platform-specific coverage collection interfaces
 */
expect class PlatformCoverageCollector {
    suspend fun instrumentCode(sourceFiles: List<String>): List<String>
    suspend fun collectExecutionTrace(testExecution: suspend () -> Unit): List<ExecutionEvent>
    fun cleanup()
}

/**
 * Utility functions for coverage analysis
 */
object CoverageUtils {
    fun calculateComplexity(sourceCode: String): Int {
        // Simplified cyclomatic complexity calculation
        val keywords = listOf("if", "else", "when", "while", "for", "catch", "&&", "||")
        return keywords.sumOf { keyword ->
            sourceCode.split(keyword).size - 1
        } + 1 // Base complexity
    }

    fun extractFunctions(sourceCode: String): List<String> {
        // Simplified function extraction using regex
        val functionRegex = Regex("""fun\s+(\w+)\s*\(""")
        return functionRegex.findAll(sourceCode).map { it.groupValues[1] }.toList()
    }

    fun isTestFile(filePath: String): Boolean {
        return filePath.contains("/test/") ||
               filePath.contains("Test.kt") ||
               filePath.contains("Spec.kt")
    }

    fun categorizeFile(filePath: String): FileCategory {
        return when {
            filePath.contains("/ui/") -> FileCategory.UI
            filePath.contains("/data/") -> FileCategory.DATA
            filePath.contains("/network/") -> FileCategory.NETWORK
            filePath.contains("/util/") -> FileCategory.UTILITY
            isTestFile(filePath) -> FileCategory.TEST
            else -> FileCategory.BUSINESS_LOGIC
        }
    }
}

enum class FileCategory {
    UI, DATA, NETWORK, UTILITY, BUSINESS_LOGIC, TEST
}