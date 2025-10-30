package io.materia.validation.models

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Comprehensive test execution results.
 *
 * Tracks test execution metrics including pass/fail rates, coverage,
 * duration, and detailed failure information for quality assessment.
 */
@Serializable
data class TestResults(
    /**
     * Total number of tests executed.
     */
    val totalTests: Int,

    /**
     * Number of tests that passed.
     */
    val passedTests: Int,

    /**
     * Number of tests that failed.
     */
    val failedTests: Int,

    /**
     * Number of tests that were skipped.
     */
    val skippedTests: Int,

    /**
     * Code coverage metrics.
     */
    val coverage: CoverageMetrics,

    /**
     * Total test execution duration.
     */
    val duration: Duration,

    /**
     * Detailed information about test failures.
     */
    val failureDetails: List<TestFailure> = emptyList()
) {

    /**
     * Calculates test success rate.
     * @return success rate as percentage (0.0-100.0)
     */
    fun getSuccessRate(): Double {
        return if (totalTests > 0) {
            (passedTests.toDouble() / totalTests) * 100
        } else {
            0.0
        }
    }

    /**
     * Calculates test failure rate.
     * @return failure rate as percentage (0.0-100.0)
     */
    fun getFailureRate(): Double {
        return if (totalTests > 0) {
            (failedTests.toDouble() / totalTests) * 100
        } else {
            0.0
        }
    }

    /**
     * Checks if tests meet constitutional requirement (>95% pass rate).
     * @return true if success rate is 95% or higher
     */
    fun meetsConstitutionalRequirement(): Boolean {
        return getSuccessRate() >= 95.0
    }

    /**
     * Gets executed test count (excluding skipped).
     * @return number of tests that actually ran
     */
    fun getExecutedTests(): Int {
        return passedTests + failedTests
    }

    /**
     * Calculates average test execution time.
     * @return average duration per test
     */
    fun getAverageTestDuration(): Duration {
        val executed = getExecutedTests()
        return if (executed > 0) {
            duration / executed
        } else {
            Duration.ZERO
        }
    }

    /**
     * Gets test health score (0.0-1.0).
     * Based on success rate, coverage, and execution time.
     * @return combined health score
     */
    fun getHealthScore(): Float {
        val successScore = (getSuccessRate().toFloat() / 100.0f).coerceIn(0.0f, 1.0f)
        val coverageScore = coverage.getOverallScore()

        // Execution time score (faster is better, target < 5 minutes)
        val targetMinutes = 5.0
        val actualMinutes = duration.toDouble(DurationUnit.MINUTES)
        val timeScore = if (actualMinutes <= targetMinutes) {
            1.0f
        } else {
            (targetMinutes / actualMinutes).toFloat().coerceIn(0.0f, 1.0f)
        }

        return (successScore * 0.5f) +  // Success rate is most important
                (coverageScore * 0.4f) +  // Coverage is very important
                (timeScore * 0.1f)        // Speed is nice to have
    }

    /**
     * Groups failures by category for analysis.
     * @return map of category to list of failures
     */
    fun getFailuresByCategory(): Map<String, List<TestFailure>> {
        return failureDetails.groupBy { failure ->
            when {
                failure.testName.contains("Unit", ignoreCase = true) -> "Unit Tests"
                failure.testName.contains("Integration", ignoreCase = true) -> "Integration Tests"
                failure.testName.contains("Performance", ignoreCase = true) -> "Performance Tests"
                failure.testName.contains("UI", ignoreCase = true) -> "UI Tests"
                failure.testName.contains("End", ignoreCase = true) -> "E2E Tests"
                else -> "Other Tests"
            }
        }
    }

    /**
     * Gets the most common failure patterns.
     * @param limit maximum number of patterns to return
     * @return list of common error messages with occurrence counts
     */
    fun getCommonFailurePatterns(limit: Int = 5): List<Pair<String, Int>> {
        return failureDetails
            .groupBy { it.getErrorPattern() }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }

    /**
     * Generates test summary report.
     * @return formatted summary string
     */
    fun generateSummary(): String {
        val successRate = getSuccessRate()
        val status = when {
            successRate >= 95 -> "✅ EXCELLENT"
            successRate >= 80 -> "⚠️ GOOD"
            successRate >= 60 -> "⚠️ FAIR"
            else -> "❌ POOR"
        }

        return buildString {
            appendLine("Test Results Summary $status")
            appendLine("═══════════════════════════════════")
            appendLine("Total Tests: $totalTests")
            appendLine("  ✅ Passed: $passedTests (${formatPercent(passedTests, totalTests)})")
            appendLine("  ❌ Failed: $failedTests (${formatPercent(failedTests, totalTests)})")
            appendLine("  ⏭️ Skipped: $skippedTests (${formatPercent(skippedTests, totalTests)})")
            appendLine()
            appendLine("Coverage:")
            appendLine("  Lines: ${coverage.linePercentage.format(1)}%")
            appendLine("  Branches: ${coverage.branchPercentage.format(1)}%")
            appendLine()
            appendLine(
                "Duration: ${
                    duration.toComponents { hours, minutes, seconds, _ ->
                        when {
                            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
                            minutes > 0 -> "${minutes}m ${seconds}s"
                            else -> "${seconds}s"
                        }
                    }
                }"
            )

            if (failureDetails.isNotEmpty()) {
                appendLine()
                appendLine("Top Failures:")
                failureDetails.take(3).forEach { failure ->
                    appendLine("  • ${failure.testName}: ${failure.getShortMessage()}")
                }
            }
        }
    }

    /**
     * Creates action items to improve test results.
     * @return list of prioritized actions
     */
    fun createImprovementActions(): List<String> {
        val actions = mutableListOf<String>()

        // Address failures
        if (failedTests > 0) {
            val categories = getFailuresByCategory()
            categories.forEach { (category, failures) ->
                if (failures.isNotEmpty()) {
                    actions.add("Fix ${failures.size} failing tests in $category")
                }
            }
        }

        // Address coverage
        if (!coverage.meetsRequirement) {
            val lineDiff = 80.0f - coverage.linePercentage
            if (lineDiff > 0) {
                actions.add("Increase line coverage by ${lineDiff.format(1)}% to meet 80% requirement")
            }

            val branchDiff = 70.0f - coverage.branchPercentage
            if (branchDiff > 0) {
                actions.add("Increase branch coverage by ${branchDiff.format(1)}% to meet 70% requirement")
            }
        }

        // Address skipped tests
        if (skippedTests > totalTests * 0.1) { // More than 10% skipped
            actions.add("Investigate and fix ${skippedTests} skipped tests")
        }

        // Address performance
        if (duration > Duration.parse("10m")) {
            actions.add("Optimize test execution time (currently ${duration.inWholeMinutes} minutes)")
        }

        return actions
    }

    private fun formatPercent(value: Int, total: Int): String {
        return if (total > 0) {
            "${((value.toDouble() / total) * 100).format(1)}%"
        } else {
            "0.0%"
        }
    }
}

/**
 * Code coverage metrics.
 */
@Serializable
data class CoverageMetrics(
    /**
     * Percentage of lines covered by tests (0.0-100.0).
     */
    val linePercentage: Float,

    /**
     * Percentage of branches covered by tests (0.0-100.0).
     */
    val branchPercentage: Float,

    /**
     * Whether coverage meets constitutional requirements.
     * Requires 80% line coverage and 70% branch coverage.
     */
    val meetsRequirement: Boolean
) {

    /**
     * Gets overall coverage score (0.0-1.0).
     * @return weighted average of line and branch coverage
     */
    fun getOverallScore(): Float {
        val lineScore = (linePercentage / 100.0f).coerceIn(0.0f, 1.0f)
        val branchScore = (branchPercentage / 100.0f).coerceIn(0.0f, 1.0f)

        // Line coverage is slightly more important
        return (lineScore * 0.6f) + (branchScore * 0.4f)
    }

    /**
     * Gets coverage level classification.
     * @return coverage level description
     */
    fun getCoverageLevel(): String {
        val overall = (linePercentage + branchPercentage) / 2

        return when {
            overall >= 90 -> "Excellent"
            overall >= 80 -> "Good"
            overall >= 70 -> "Acceptable"
            overall >= 60 -> "Fair"
            overall >= 50 -> "Poor"
            else -> "Very Poor"
        }
    }

    /**
     * Checks if coverage is comprehensive (>90% both line and branch).
     * @return true if both metrics exceed 90%
     */
    fun isComprehensive(): Boolean {
        return linePercentage >= 90.0f && branchPercentage >= 90.0f
    }
}

/**
 * Details about a specific test failure.
 */
@Serializable
data class TestFailure(
    /**
     * Name of the failed test.
     */
    val testName: String,

    /**
     * Class containing the test.
     */
    val className: String,

    /**
     * Error or assertion message.
     */
    val message: String,

    /**
     * Stack trace of the failure.
     */
    val stackTrace: String
) {

    /**
     * Gets a short version of the error message.
     * @param maxLength maximum length of message
     * @return truncated message if necessary
     */
    fun getShortMessage(maxLength: Int = 80): String {
        return if (message.length <= maxLength) {
            message
        } else {
            message.take(maxLength - 3) + "..."
        }
    }

    /**
     * Gets the root cause from the stack trace.
     * @return first meaningful line from stack trace
     */
    fun getRootCause(): String {
        val lines = stackTrace.lines()

        // Find first line that's from our code (io.materia)
        val ourCodeLine = lines.find { it.contains("io.materia") }
        if (ourCodeLine != null) {
            return ourCodeLine.trim()
        }

        // Fall back to first non-empty line
        return lines.firstOrNull { it.isNotBlank() }?.trim() ?: "Unknown cause"
    }

    /**
     * Extracts error pattern for grouping similar failures.
     * @return normalized error pattern
     */
    fun getErrorPattern(): String {
        // Extract common patterns from message
        return when {
            message.contains("NullPointerException") -> "Null Pointer"
            message.contains("AssertionError") -> "Assertion Failed"
            message.contains("TimeoutException") -> "Timeout"
            message.contains("IllegalArgumentException") -> "Illegal Argument"
            message.contains("IllegalStateException") -> "Illegal State"
            message.contains("expected") && message.contains("but was") -> "Value Mismatch"
            message.contains("not found") -> "Not Found"
            message.contains("connection") -> "Connection Issue"
            else -> "Other Error"
        }
    }

    /**
     * Gets the test location.
     * @return formatted location string
     */
    fun getLocation(): String {
        return "$className.$testName"
    }

    /**
     * Determines if this is likely a flaky test.
     * @return true if failure appears to be intermittent
     */
    fun isLikelyFlaky(): Boolean {
        val flakyPatterns = listOf(
            "timeout", "connection", "network", "socket",
            "intermittent", "random", "sometimes", "occasionally"
        )

        val lowerMessage = message.lowercase()
        return flakyPatterns.any { pattern -> lowerMessage.contains(pattern) }
    }
}

/**
 * Extension function to format float values.
 * Multiplatform-compatible implementation without String.format().
 */
private fun Float.format(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> {
            var m = 1.0
            repeat(decimals) { m *= 10.0 }
            m
        }
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier

    // Convert to string and ensure proper decimal places
    val str = rounded.toString()
    val parts = str.split('.')
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else ""

    return if (decimals == 0) {
        intPart
    } else {
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        "$intPart.$paddedDec"
    }
}

/**
 * Extension function to format double values.
 * Multiplatform-compatible implementation without String.format().
 */
private fun Double.format(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> {
            var m = 1.0
            repeat(decimals) { m *= 10.0 }
            m
        }
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier

    // Convert to string and ensure proper decimal places
    val str = rounded.toString()
    val parts = str.split('.')
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else ""

    return if (decimals == 0) {
        intPart
    } else {
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        "$intPart.$paddedDec"
    }
}