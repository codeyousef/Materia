package io.materia.validation.reporting

import io.materia.validation.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates validation reports in various formats.
 *
 * The ReportGenerator creates comprehensive reports from production readiness
 * validation results, supporting multiple output formats including HTML, JSON,
 * and Markdown.
 */
class ReportGenerator {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val formatters = ReportFormatters()
    private val styles = ReportStyles()
    private val chartGenerator = ChartGenerator()

    private val htmlGenerator = HtmlReportGenerator(styles, chartGenerator, formatters)
    private val markdownGenerator = MarkdownReportGenerator(formatters)
    private val consoleGenerator = ConsoleReportGenerator(formatters)

    /**
     * Generates an HTML report from validation results.
     *
     * @param report The production readiness report
     * @param includeCharts Whether to include JavaScript charts
     * @return HTML content as a string
     */
    fun generateHtmlReport(
        report: ProductionReadinessReport,
        includeCharts: Boolean = true
    ): String = htmlGenerator.generate(report, includeCharts)

    /**
     * Generates a JSON report from validation results.
     *
     * @param report The production readiness report
     * @return JSON content as a string
     */
    fun generateJsonReport(report: ProductionReadinessReport): String {
        return json.encodeToString(report)
    }

    /**
     * Generates a Markdown report from validation results.
     *
     * @param report The production readiness report
     * @return Markdown content as a string
     */
    fun generateMarkdownReport(report: ProductionReadinessReport): String =
        markdownGenerator.generate(report)

    /**
     * Generates a summary report suitable for CI/CD logs.
     *
     * @param report The production readiness report
     * @return Console-friendly summary
     */
    fun generateConsoleSummary(report: ProductionReadinessReport): String =
        consoleGenerator.generate(report)
}
