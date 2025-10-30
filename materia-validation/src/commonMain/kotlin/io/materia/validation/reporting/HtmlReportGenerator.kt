package io.materia.validation.reporting

import io.materia.validation.models.*

/**
 * Generates HTML reports from validation results.
 */
internal class HtmlReportGenerator(
    private val styles: ReportStyles,
    private val chartGenerator: ChartGenerator,
    private val formatters: ReportFormatters
) {

    fun generate(report: ProductionReadinessReport, includeCharts: Boolean = true): String {
        return buildString {
            appendHtmlHeader(includeCharts)
            appendBody(report, includeCharts)
            appendLine("</html>")
        }
    }

    private fun StringBuilder.appendHtmlHeader(includeCharts: Boolean) {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang='en'>")
        appendLine("<head>")
        appendLine("    <meta charset='UTF-8'>")
        appendLine("    <meta name='viewport' content='width=device-width, initial-scale=1.0'>")
        appendLine("    <title>Materia Production Readiness Report</title>")
        appendLine(styles.getHtmlStyles())
        if (includeCharts) {
            appendLine(styles.getChartScripts())
        }
        appendLine("</head>")
        appendLine("<body>")
    }

    private fun StringBuilder.appendBody(
        report: ProductionReadinessReport,
        includeCharts: Boolean
    ) {
        appendHeader(report)
        appendExecutiveSummary(report)
        appendConstitutionalRequirements(report)
        appendCategoryScores(report, includeCharts)
        appendPlatformStatus(report)
        appendIssuesSummary(report)
        appendMetadata(report)
        appendFooter()
        if (includeCharts) {
            appendLine(chartGenerator.generateScript(report))
        }
        appendLine("</body>")
    }

    private fun StringBuilder.appendHeader(report: ProductionReadinessReport) {
        appendLine("<div class='header'>")
        appendLine("    <h1>Materia Production Readiness Report</h1>")
        appendLine("    <div class='timestamp'>Generated: ${formatters.formatTimestamp(report.timestamp.toEpochMilliseconds())}</div>")
        appendLine(
            "    <div class='project-path'>Branch: ${report.branchName} (${
                report.commitHash.take(
                    8
                )
            })</div>"
        )
        appendLine("</div>")
    }

    private fun StringBuilder.appendExecutiveSummary(report: ProductionReadinessReport) {
        appendLine("<div class='section executive-summary'>")
        appendLine("    <h2>Executive Summary</h2>")
        appendLine("    <div class='summary-card ${if (report.isProductionReady) "ready" else "not-ready"}'>")
        appendLine("        <div class='status-indicator'></div>")
        appendLine("        <div class='status-text'>")
        appendLine("            ${if (report.isProductionReady) "✓ Production Ready" else "✗ Not Production Ready"}")
        appendLine("        </div>")
        appendLine(
            "        <div class='overall-score'>Overall Score: ${
                formatters.formatPercentage(
                    report.overallScore
                )
            }</div>"
        )
        appendLine("    </div>")
        appendLine("</div>")
    }

    private fun StringBuilder.appendConstitutionalRequirements(report: ProductionReadinessReport) {
        val constitutionalCategory =
            report.categories.find { it.name == ValidationCategory.CONSTITUTIONAL }
        if (constitutionalCategory != null) {
            appendLine("<div class='section constitutional'>")
            appendLine("    <h2>Constitutional Requirements</h2>")
            appendLine("    <table class='requirements-table'>")
            appendLine("        <thead>")
            appendLine("            <tr>")
            appendLine("                <th>Requirement</th>")
            appendLine("                <th>Status</th>")
            appendLine("            </tr>")
            appendLine("        </thead>")
            appendLine("        <tbody>")
            constitutionalCategory.criteria.forEach { criterion ->
                val met = criterion.status == ValidationStatus.PASSED
                appendLine("            <tr class='${if (met) "passed" else "failed"}'>")
                appendLine("                <td>${criterion.name}</td>")
                appendLine("                <td>${if (met) "✓ Met" else "✗ Not Met"}</td>")
                appendLine("            </tr>")
            }
            appendLine("        </tbody>")
            appendLine("    </table>")
            appendLine("</div>")
        }
    }

    private fun StringBuilder.appendCategoryScores(
        report: ProductionReadinessReport,
        includeCharts: Boolean
    ) {
        appendLine("<div class='section scores'>")
        appendLine("    <h2>Validation Category Scores</h2>")
        if (includeCharts) {
            appendLine("    <canvas id='scoresChart' width='400' height='200'></canvas>")
        }
        appendLine("    <table class='scores-table'>")
        appendLine("        <thead>")
        appendLine("            <tr>")
        appendLine("                <th>Category</th>")
        appendLine("                <th>Score</th>")
        appendLine("                <th>Status</th>")
        appendLine("            </tr>")
        appendLine("        </thead>")
        appendLine("        <tbody>")
        report.categories.forEach { category ->
            val status = formatters.getScoreStatus(category.score)
            appendLine("            <tr class='$status'>")
            appendLine("                <td>${category.name}</td>")
            appendLine("                <td>${formatters.formatPercentage(category.score)}</td>")
            appendLine("                <td>$status</td>")
            appendLine("            </tr>")
        }
        appendLine("        </tbody>")
        appendLine("    </table>")
        appendLine("</div>")
    }

    private fun StringBuilder.appendPlatformStatus(report: ProductionReadinessReport) {
        val platformCriteria =
            report.categories.flatMap { it.criteria }.filter { it.platform != null }
        if (platformCriteria.isNotEmpty()) {
            appendLine("<div class='section platforms'>")
            appendLine("    <h2>Platform Support</h2>")
            appendLine("    <div class='platform-grid'>")
            platformCriteria.groupBy { it.platform }.forEach { (platform, criteria) ->
                val failedCount = criteria.count { it.status == ValidationStatus.FAILED }
                val statusClass = if (failedCount == 0) "passed" else "failed"
                appendLine("        <div class='platform-card $statusClass'>")
                appendLine("            <h3>$platform</h3>")
                appendLine("            <div class='platform-status'>${if (failedCount == 0) "✓ Supported" else "✗ Issues Found"}</div>")
                if (failedCount > 0) {
                    appendLine("            <div class='platform-issues'>")
                    appendLine("                <strong>Issues ($failedCount):</strong>")
                    appendLine("                <ul>")
                    criteria.filter { it.status == ValidationStatus.FAILED }.take(3)
                        .forEach { criterion ->
                            appendLine("                    <li>${criterion.name}</li>")
                        }
                    if (failedCount > 3) {
                        appendLine("                    <li>...and ${failedCount - 3} more</li>")
                    }
                    appendLine("                </ul>")
                    appendLine("            </div>")
                }
                appendLine("        </div>")
            }
            appendLine("    </div>")
            appendLine("</div>")
        }
    }

    private fun StringBuilder.appendIssuesSummary(report: ProductionReadinessReport) {
        val failedCriteria = report.categories.flatMap { it.criteria }.filter {
            it.status in listOf(ValidationStatus.FAILED, ValidationStatus.ERROR)
        }
        if (failedCriteria.isNotEmpty()) {
            appendLine("<div class='section issues'>")
            appendLine("    <h2>Issues Summary</h2>")

            val issuesBySeverity = failedCriteria.groupBy { it.severity }

            Severity.values().forEach { severity ->
                val severityIssues = issuesBySeverity[severity] ?: emptyList()
                if (severityIssues.isNotEmpty()) {
                    appendLine("    <div class='severity-group ${severity.name.lowercase()}'>")
                    appendLine("        <h3>${severity.name} (${severityIssues.size})</h3>")
                    appendLine("        <div class='issues-list'>")
                    severityIssues.take(5).forEach { criterion ->
                        appendLine("            <div class='issue'>")
                        appendLine("                <div class='issue-header'>")
                        appendLine("                    <strong>${criterion.name}</strong>")
                        appendLine("                </div>")
                        appendLine("                <div class='issue-description'>${criterion.description}</div>")
                        appendLine("                <div>Required: ${criterion.requirement}</div>")
                        appendLine("                <div>Actual: ${criterion.actual}</div>")
                        appendLine("            </div>")
                    }
                    if (severityIssues.size > 5) {
                        appendLine("            <div class='more-issues'>...and ${severityIssues.size - 5} more</div>")
                    }
                    appendLine("        </div>")
                    appendLine("    </div>")
                }
            }
            appendLine("</div>")
        }
    }

    private fun StringBuilder.appendMetadata(report: ProductionReadinessReport) {
        appendLine("<div class='section metadata'>")
        appendLine("    <h2>Validation Metadata</h2>")
        appendLine("    <dl class='metadata-list'>")
        appendLine("        <dt>Branch:</dt>")
        appendLine("        <dd>${report.branchName}</dd>")
        appendLine("        <dt>Commit:</dt>")
        appendLine("        <dd>${report.commitHash.take(8)}</dd>")
        appendLine("        <dt>Execution Time:</dt>")
        appendLine("        <dd>${report.executionTime}</dd>")
        appendLine("    </dl>")
        appendLine("</div>")
    }

    private fun StringBuilder.appendFooter() {
        appendLine("<div class='footer'>")
        appendLine("    <p>Generated by Materia Validation System</p>")
        appendLine("</div>")
    }
}
