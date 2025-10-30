package io.materia.validation.reporting

import io.materia.validation.models.*

/**
 * Generates console-friendly reports from validation results.
 */
internal class ConsoleReportGenerator(
    private val formatters: ReportFormatters
) {

    fun generate(report: ProductionReadinessReport): String {
        return buildString {
            appendHeader()
            appendStatus(report)
            appendConstitutionalRequirements(report)
            appendCategoryScores(report)
            appendIssuesCounts(report)
            appendWarning(report)
        }
    }

    private fun StringBuilder.appendHeader() {
        appendLine("╔══════════════════════════════════════════════════════════╗")
        appendLine("║         Materia Production Readiness Summary             ║")
        appendLine("╚══════════════════════════════════════════════════════════╝")
        appendLine()
    }

    private fun StringBuilder.appendStatus(report: ProductionReadinessReport) {
        val status = if (report.isProductionReady) "✅ READY" else "❌ NOT READY"
        appendLine("Status: $status")
        appendLine("Score:  ${formatters.formatPercentage(report.overallScore)}")
        appendLine()
    }

    private fun StringBuilder.appendConstitutionalRequirements(report: ProductionReadinessReport) {
        val constitutionalCategory =
            report.categories.find { it.name == ValidationCategory.CONSTITUTIONAL }
        if (constitutionalCategory != null) {
            appendLine("Constitutional Requirements:")
            constitutionalCategory.criteria.forEach { criterion ->
                val symbol = if (criterion.status == ValidationStatus.PASSED) "✓" else "✗"
                appendLine("  $symbol ${criterion.name}")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendCategoryScores(report: ProductionReadinessReport) {
        appendLine("Category Scores:")
        report.categories.forEach { category ->
            val bar = formatters.generateProgressBar(category.score, 20)
            appendLine("  ${category.name.padEnd(15)} $bar ${formatters.formatPercentage(category.score)}")
        }
        appendLine()
    }

    private fun StringBuilder.appendIssuesCounts(report: ProductionReadinessReport) {
        val allCriteria = report.categories.flatMap { it.criteria }
        val criticalIssues =
            allCriteria.count { it.severity == Severity.CRITICAL && it.status != ValidationStatus.PASSED }
        val highIssues =
            allCriteria.count { it.severity == Severity.HIGH && it.status != ValidationStatus.PASSED }
        val mediumIssues =
            allCriteria.count { it.severity == Severity.MEDIUM && it.status != ValidationStatus.PASSED }
        val lowIssues =
            allCriteria.count { it.severity == Severity.LOW && it.status != ValidationStatus.PASSED }

        appendLine("Issues: Critical: $criticalIssues | High: $highIssues | Medium: $mediumIssues | Low: $lowIssues")
    }

    private fun StringBuilder.appendWarning(report: ProductionReadinessReport) {
        if (!report.isProductionReady) {
            appendLine()
            appendLine("⚠️  Fix the above issues before deploying to production")
        }
    }
}
