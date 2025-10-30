package io.materia.validation.reporting

import io.materia.validation.models.*

/**
 * Generates Markdown reports from validation results.
 */
internal class MarkdownReportGenerator(
    private val formatters: ReportFormatters
) {

    fun generate(report: ProductionReadinessReport): String {
        return buildString {
            appendHeader(report)
            appendExecutiveSummary(report)
            appendConstitutionalRequirements(report)
            appendCategoryScores(report)
            appendPlatformSupport(report)
            appendIssuesSummary(report)
            appendRecommendations(report)
            appendFooter()
        }
    }

    private fun StringBuilder.appendHeader(report: ProductionReadinessReport) {
        appendLine("# Materia Production Readiness Report")
        appendLine()
        appendLine("**Generated:** ${formatters.formatTimestamp(report.timestamp.toEpochMilliseconds())}")
        appendLine("**Branch:** `${report.branchName}` **Commit:** `${report.commitHash.take(8)}`")
        appendLine()
    }

    private fun StringBuilder.appendExecutiveSummary(report: ProductionReadinessReport) {
        appendLine("## Executive Summary")
        appendLine()
        if (report.isProductionReady) {
            appendLine("✅ **PRODUCTION READY**")
        } else {
            appendLine("❌ **NOT PRODUCTION READY**")
        }
        appendLine()
        appendLine("**Overall Score:** ${formatters.formatPercentage(report.overallScore)}")
        appendLine()
    }

    private fun StringBuilder.appendConstitutionalRequirements(report: ProductionReadinessReport) {
        val constitutionalCategory =
            report.categories.find { it.name == ValidationCategory.CONSTITUTIONAL }
        if (constitutionalCategory != null) {
            appendLine("## Constitutional Requirements")
            appendLine()
            appendLine("| Requirement | Status |")
            appendLine("|------------|--------|")
            constitutionalCategory.criteria.forEach { criterion ->
                val met = criterion.status == ValidationStatus.PASSED
                appendLine("| ${criterion.name} | ${if (met) "✅ Met" else "❌ Not Met"} |")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendCategoryScores(report: ProductionReadinessReport) {
        appendLine("## Validation Category Scores")
        appendLine()
        appendLine("| Category | Score | Status |")
        appendLine("|----------|-------|--------|")
        report.categories.forEach { category ->
            val status = formatters.getScoreStatus(category.score)
            val emoji = formatters.getStatusEmoji(status)
            appendLine("| ${category.name} | ${formatters.formatPercentage(category.score)} | $emoji $status |")
        }
        appendLine()
    }

    private fun StringBuilder.appendPlatformSupport(report: ProductionReadinessReport) {
        val platformCriteria =
            report.categories.flatMap { it.criteria }.filter { it.platform != null }
        if (platformCriteria.isNotEmpty()) {
            appendLine("## Platform Support")
            appendLine()
            appendLine("| Platform | Status | Issues |")
            appendLine("|----------|--------|--------|")
            platformCriteria.groupBy { it.platform }.forEach { (platform, criteria) ->
                val failedCount = criteria.count { it.status == ValidationStatus.FAILED }
                val statusEmoji = if (failedCount == 0) "✅" else "❌"
                val statusText = if (failedCount == 0) "PASSED" else "FAILED"
                appendLine("| $platform | $statusEmoji $statusText | $failedCount |")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendIssuesSummary(report: ProductionReadinessReport) {
        val allFailedCriteria = report.categories.flatMap { it.criteria }.filter {
            it.status in listOf(ValidationStatus.FAILED, ValidationStatus.ERROR)
        }
        if (allFailedCriteria.isNotEmpty()) {
            appendLine("## Issues Summary")
            appendLine()

            val issuesBySeverity = allFailedCriteria.groupBy { it.severity }

            Severity.values().forEach { severity ->
                val severityIssues = issuesBySeverity[severity] ?: emptyList()
                if (severityIssues.isNotEmpty()) {
                    appendLine("### ${severity.name} Issues (${severityIssues.size})")
                    appendLine()
                    severityIssues.take(10).forEach { criterion ->
                        appendLine("- **${criterion.name}**: ${criterion.description}")
                        appendLine("  - Required: ${criterion.requirement}")
                        appendLine("  - Actual: ${criterion.actual}")
                    }
                    if (severityIssues.size > 10) {
                        appendLine("- ...and ${severityIssues.size - 10} more")
                    }
                    appendLine()
                }
            }
        }
    }

    private fun StringBuilder.appendRecommendations(report: ProductionReadinessReport) {
        appendLine("## Recommendations")
        appendLine()
        appendLine("Based on the validation results, here are the top priorities:")
        appendLine()

        val allFailedCriteria = report.categories.flatMap { it.criteria }.filter {
            it.status in listOf(ValidationStatus.FAILED, ValidationStatus.ERROR)
        }

        val topIssues = allFailedCriteria
            .sortedBy { it.severity.ordinal }
            .take(5)

        if (topIssues.isNotEmpty()) {
            appendLine("1. **Fix Critical Issues**")
            topIssues.forEach { criterion ->
                appendLine("   - ${criterion.description}")
            }
        }

        if (report.overallScore < 0.8f) {
            appendLine("2. **Improve Overall Score**")
            appendLine("   - Current: ${formatters.formatPercentage(report.overallScore)}")
            appendLine("   - Target: 80% or higher")
        }

        val constitutionalCat =
            report.categories.find { it.name == ValidationCategory.CONSTITUTIONAL }
        val failedRequirements = constitutionalCat?.criteria?.filter {
            it.status != ValidationStatus.PASSED
        } ?: emptyList()

        if (failedRequirements.isNotEmpty()) {
            appendLine("3. **Meet Constitutional Requirements**")
            failedRequirements.forEach { criterion ->
                appendLine("   - ${criterion.name}")
            }
        }

        appendLine()
    }

    private fun StringBuilder.appendFooter() {
        appendLine("---")
        appendLine("*Generated by Materia Validation System*")
    }
}
