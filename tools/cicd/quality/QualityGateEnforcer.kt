/**
 * Materia Tools - Quality Gate Enforcement
 * Enforces quality standards and blocks releases if criteria are not met
 */

package io.materia.tools.cicd.quality

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import kotlin.system.exitProcess

/**
 * Quality gate enforcer that validates multiple quality metrics
 */
class QualityGateEnforcer(
    private val config: QualityGateConfig,
    private val projectRoot: String
) {
    private val logger = Logger("QualityGate")

    suspend fun enforceQualityGates(): QualityGateReport = coroutineScope {
        logger.info("Starting quality gate enforcement...")
        logger.info("Project: ${config.projectName}")
        logger.info("Strictness: ${config.strictness}")

        val checks = mutableListOf<Deferred<QualityCheck>>()

        // Code coverage check
        if (config.coverage.enabled) {
            checks.add(async { checkCodeCoverage() })
        }

        // Code quality checks
        if (config.codeQuality.enabled) {
            checks.add(async { checkCodeQuality() })
        }

        // Security vulnerability checks
        if (config.security.enabled) {
            checks.add(async { checkSecurityVulnerabilities() })
        }

        // Performance regression checks
        if (config.performance.enabled) {
            checks.add(async { checkPerformanceRegression() })
        }

        // Test quality checks
        if (config.testQuality.enabled) {
            checks.add(async { checkTestQuality() })
        }

        // Documentation completeness
        if (config.documentation.enabled) {
            checks.add(async { checkDocumentationCompleteness() })
        }

        // License compliance
        if (config.license.enabled) {
            checks.add(async { checkLicenseCompliance() })
        }

        // API compatibility
        if (config.compatibility.enabled) {
            checks.add(async { checkApiCompatibility() })
        }

        val results = checks.awaitAll()
        val report = generateReport(results)

        logger.info("Quality gate enforcement completed")
        logger.info("Overall status: ${if (report.passed) "PASSED" else "FAILED"}")

        return@coroutineScope report
    }

    private suspend fun checkCodeCoverage(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking code coverage...")

            try {
                val coverageReport = findCoverageReport()
                if (coverageReport == null) {
                    return@withContext QualityCheck(
                        name = "Code Coverage",
                        passed = false,
                        required = config.coverage.required,
                        message = "No coverage report found",
                        details = mapOf("expected_file" to "build/reports/kover/report.xml")
                    )
                }

                val coverage = parseCoverageReport(coverageReport)
                val threshold = config.coverage.minimumPercentage
                val passed = coverage >= threshold

                QualityCheck(
                    name = "Code Coverage",
                    passed = passed,
                    required = config.coverage.required,
                    message = if (passed)
                        "Coverage ${coverage}% meets threshold ${threshold}%"
                    else
                        "Coverage ${coverage}% below threshold ${threshold}%",
                    details = mapOf(
                        "actual_coverage" to "${coverage}%",
                        "required_coverage" to "${threshold}%",
                        "report_file" to coverageReport.absolutePath
                    )
                )
            } catch (e: Exception) {
                logger.error("Code coverage check failed", e)
                QualityCheck(
                    name = "Code Coverage",
                    passed = false,
                    required = config.coverage.required,
                    message = "Coverage check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkCodeQuality(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking code quality...")

            try {
                val detektReport = File(projectRoot, "build/reports/detekt/detekt.xml")
                val ktlintReport = File(projectRoot, "build/reports/ktlint/ktlintMainSourceSetCheck.txt")

                var totalIssues = 0
                var criticalIssues = 0

                // Parse Detekt report
                if (detektReport.exists()) {
                    val detektResults = parseDetektReport(detektReport)
                    totalIssues += detektResults.totalIssues
                    criticalIssues += detektResults.criticalIssues
                }

                // Parse KtLint report
                if (ktlintReport.exists()) {
                    val ktlintIssues = parseKtLintReport(ktlintReport)
                    totalIssues += ktlintIssues
                }

                val maxIssues = config.codeQuality.maxIssues
                val maxCritical = config.codeQuality.maxCriticalIssues
                val passed = totalIssues <= maxIssues && criticalIssues <= maxCritical

                QualityCheck(
                    name = "Code Quality",
                    passed = passed,
                    required = config.codeQuality.required,
                    message = if (passed)
                        "Code quality acceptable: $totalIssues issues ($criticalIssues critical)"
                    else
                        "Code quality issues: $totalIssues issues ($criticalIssues critical) exceed limits",
                    details = mapOf(
                        "total_issues" to totalIssues.toString(),
                        "critical_issues" to criticalIssues.toString(),
                        "max_allowed_issues" to maxIssues.toString(),
                        "max_allowed_critical" to maxCritical.toString()
                    )
                )
            } catch (e: Exception) {
                logger.error("Code quality check failed", e)
                QualityCheck(
                    name = "Code Quality",
                    passed = false,
                    required = config.codeQuality.required,
                    message = "Code quality check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkSecurityVulnerabilities(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking security vulnerabilities...")

            try {
                val dependencyCheckReport = File(projectRoot, "build/reports/dependency-check-report.xml")

                if (!dependencyCheckReport.exists()) {
                    return@withContext QualityCheck(
                        name = "Security Vulnerabilities",
                        passed = false,
                        required = config.security.required,
                        message = "No security scan report found",
                        details = mapOf("expected_file" to dependencyCheckReport.absolutePath)
                    )
                }

                val vulnerabilities = parseDependencyCheckReport(dependencyCheckReport)
                val highSeverity = vulnerabilities.count { it.severity in listOf("HIGH", "CRITICAL") }
                val maxHigh = config.security.maxHighSeverityVulnerabilities
                val passed = highSeverity <= maxHigh

                QualityCheck(
                    name = "Security Vulnerabilities",
                    passed = passed,
                    required = config.security.required,
                    message = if (passed)
                        "Security acceptable: $highSeverity high/critical vulnerabilities"
                    else
                        "Security issues: $highSeverity high/critical vulnerabilities exceed limit of $maxHigh",
                    details = mapOf(
                        "total_vulnerabilities" to vulnerabilities.size.toString(),
                        "high_critical_vulnerabilities" to highSeverity.toString(),
                        "max_allowed_high_critical" to maxHigh.toString(),
                        "report_file" to dependencyCheckReport.absolutePath
                    )
                )
            } catch (e: Exception) {
                logger.error("Security vulnerability check failed", e)
                QualityCheck(
                    name = "Security Vulnerabilities",
                    passed = false,
                    required = config.security.required,
                    message = "Security check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkPerformanceRegression(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking performance regression...")

            try {
                val performanceReport = File(projectRoot, "build/reports/performance/benchmark-results.json")

                if (!performanceReport.exists()) {
                    return@withContext QualityCheck(
                        name = "Performance Regression",
                        passed = !config.performance.required,
                        required = config.performance.required,
                        message = "No performance benchmark report found",
                        details = mapOf("expected_file" to performanceReport.absolutePath)
                    )
                }

                val regressions = parsePerformanceReport(performanceReport)
                val maxRegression = config.performance.maxRegressionPercent
                val significantRegressions = regressions.count { it.regressionPercent > maxRegression }
                val passed = significantRegressions == 0

                QualityCheck(
                    name = "Performance Regression",
                    passed = passed,
                    required = config.performance.required,
                    message = if (passed)
                        "No significant performance regressions detected"
                    else
                        "$significantRegressions benchmarks regressed more than ${maxRegression}%",
                    details = mapOf(
                        "total_benchmarks" to regressions.size.toString(),
                        "regressed_benchmarks" to significantRegressions.toString(),
                        "max_allowed_regression" to "${maxRegression}%",
                        "report_file" to performanceReport.absolutePath
                    )
                )
            } catch (e: Exception) {
                logger.error("Performance regression check failed", e)
                QualityCheck(
                    name = "Performance Regression",
                    passed = false,
                    required = config.performance.required,
                    message = "Performance check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkTestQuality(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking test quality...")

            try {
                val testReports = findTestReports()
                if (testReports.isEmpty()) {
                    return@withContext QualityCheck(
                        name = "Test Quality",
                        passed = false,
                        required = config.testQuality.required,
                        message = "No test reports found",
                        details = mapOf("expected_location" to "build/test-results/**/TEST-*.xml")
                    )
                }

                val testResults = testReports.map { parseTestReport(it) }
                val totalTests = testResults.sumOf { it.totalTests }
                val failedTests = testResults.sumOf { it.failedTests }
                val successRate = if (totalTests > 0) ((totalTests - failedTests) * 100.0 / totalTests) else 0.0

                val minTests = config.testQuality.minimumTestCount
                val minSuccessRate = config.testQuality.minimumSuccessRate
                val passed = totalTests >= minTests && successRate >= minSuccessRate

                QualityCheck(
                    name = "Test Quality",
                    passed = passed,
                    required = config.testQuality.required,
                    message = if (passed)
                        "Test quality acceptable: $totalTests tests, ${successRate.toInt()}% success rate"
                    else
                        "Test quality issues: $totalTests tests (need >= $minTests), ${successRate.toInt()}% success rate (need >= ${minSuccessRate}%)",
                    details = mapOf(
                        "total_tests" to totalTests.toString(),
                        "failed_tests" to failedTests.toString(),
                        "success_rate" to "${successRate.toInt()}%",
                        "minimum_tests" to minTests.toString(),
                        "minimum_success_rate" to "${minSuccessRate}%"
                    )
                )
            } catch (e: Exception) {
                logger.error("Test quality check failed", e)
                QualityCheck(
                    name = "Test Quality",
                    passed = false,
                    required = config.testQuality.required,
                    message = "Test quality check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkDocumentationCompleteness(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking documentation completeness...")

            try {
                val publicApiElements = countPublicApiElements()
                val documentedElements = countDocumentedElements()
                val documentationRate = if (publicApiElements > 0)
                    (documentedElements * 100.0 / publicApiElements) else 100.0

                val minRate = config.documentation.minimumCoverage
                val passed = documentationRate >= minRate

                QualityCheck(
                    name = "Documentation Completeness",
                    passed = passed,
                    required = config.documentation.required,
                    message = if (passed)
                        "Documentation coverage ${documentationRate.toInt()}% meets requirement"
                    else
                        "Documentation coverage ${documentationRate.toInt()}% below requirement ${minRate}%",
                    details = mapOf(
                        "public_api_elements" to publicApiElements.toString(),
                        "documented_elements" to documentedElements.toString(),
                        "documentation_rate" to "${documentationRate.toInt()}%",
                        "minimum_rate" to "${minRate}%"
                    )
                )
            } catch (e: Exception) {
                logger.error("Documentation completeness check failed", e)
                QualityCheck(
                    name = "Documentation Completeness",
                    passed = false,
                    required = config.documentation.required,
                    message = "Documentation check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkLicenseCompliance(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking license compliance...")

            try {
                val dependencyLicenses = analyzeDependencyLicenses()
                val blockedLicenses = dependencyLicenses.filter {
                    it.license in config.license.blockedLicenses
                }

                val passed = blockedLicenses.isEmpty()

                QualityCheck(
                    name = "License Compliance",
                    passed = passed,
                    required = config.license.required,
                    message = if (passed)
                        "All dependencies have acceptable licenses"
                    else
                        "${blockedLicenses.size} dependencies have blocked licenses",
                    details = mapOf(
                        "total_dependencies" to dependencyLicenses.size.toString(),
                        "blocked_dependencies" to blockedLicenses.size.toString(),
                        "blocked_licenses" to blockedLicenses.map { "${it.dependency}: ${it.license}" }.toString()
                    )
                )
            } catch (e: Exception) {
                logger.error("License compliance check failed", e)
                QualityCheck(
                    name = "License Compliance",
                    passed = false,
                    required = config.license.required,
                    message = "License compliance check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private suspend fun checkApiCompatibility(): QualityCheck {
        return withContext(Dispatchers.IO) {
            logger.info("Checking API compatibility...")

            try {
                // This would typically use a tool like JApiCmp or similar
                val compatibilityReport = File(projectRoot, "build/reports/api-compatibility/report.txt")

                if (!compatibilityReport.exists()) {
                    return@withContext QualityCheck(
                        name = "API Compatibility",
                        passed = !config.compatibility.required,
                        required = config.compatibility.required,
                        message = "No API compatibility report found",
                        details = mapOf("expected_file" to compatibilityReport.absolutePath)
                    )
                }

                val breakingChanges = parseApiCompatibilityReport(compatibilityReport)
                val allowBreaking = config.compatibility.allowBreakingChanges
                val passed = breakingChanges.isEmpty() || allowBreaking

                QualityCheck(
                    name = "API Compatibility",
                    passed = passed,
                    required = config.compatibility.required,
                    message = if (passed)
                        if (breakingChanges.isEmpty()) "No breaking API changes" else "Breaking changes allowed"
                    else
                        "${breakingChanges.size} breaking API changes detected",
                    details = mapOf(
                        "breaking_changes" to breakingChanges.size.toString(),
                        "allow_breaking_changes" to allowBreaking.toString(),
                        "changes" to breakingChanges.toString()
                    )
                )
            } catch (e: Exception) {
                logger.error("API compatibility check failed", e)
                QualityCheck(
                    name = "API Compatibility",
                    passed = false,
                    required = config.compatibility.required,
                    message = "API compatibility check failed: ${e.message}",
                    details = mapOf("error" to e.toString())
                )
            }
        }
    }

    private fun generateReport(checks: List<QualityCheck>): QualityGateReport {
        val passedChecks = checks.count { it.passed }
        val totalChecks = checks.size
        val requiredChecks = checks.filter { it.required }
        val passedRequiredChecks = requiredChecks.count { it.passed }
        val totalRequiredChecks = requiredChecks.size

        val overallPassed = when (config.strictness) {
            QualityStrictness.STRICT -> passedChecks == totalChecks
            QualityStrictness.MODERATE -> passedRequiredChecks == totalRequiredChecks
            QualityStrictness.LENIENT -> passedRequiredChecks >= (totalRequiredChecks * 0.8).toInt()
        }

        return QualityGateReport(
            timestamp = Instant.now(),
            projectName = config.projectName,
            strictness = config.strictness,
            passed = overallPassed,
            checks = checks,
            summary = QualityGateSummary(
                totalChecks = totalChecks,
                passedChecks = passedChecks,
                failedChecks = totalChecks - passedChecks,
                requiredChecks = totalRequiredChecks,
                passedRequiredChecks = passedRequiredChecks
            )
        )
    }

    // Helper methods for parsing reports (simplified implementations)
    private fun findCoverageReport(): File? {
        val possibleLocations = listOf(
            "build/reports/kover/report.xml",
            "build/reports/jacoco/test/jacocoTestReport.xml"
        )
        return possibleLocations.map { File(projectRoot, it) }.firstOrNull { it.exists() }
    }

    private fun parseCoverageReport(file: File): Double {
        // Simplified XML parsing - in real implementation, use proper XML parser
        val content = file.readText()
        val regex = """line-rate="([0-9.]+)"""".toRegex()
        val match = regex.find(content)
        return match?.groupValues?.get(1)?.toDouble()?.times(100) ?: 0.0
    }

    // Additional helper methods would be implemented here...
    private fun parseDetektReport(file: File): DetektResults = DetektResults(0, 0)
    private fun parseKtLintReport(file: File): Int = 0
    private fun parseDependencyCheckReport(file: File): List<Vulnerability> = emptyList()
    private fun parsePerformanceReport(file: File): List<PerformanceRegression> = emptyList()
    private fun findTestReports(): List<File> = emptyList()
    private fun parseTestReport(file: File): TestResults = TestResults(0, 0)
    private fun countPublicApiElements(): Int = 100
    private fun countDocumentedElements(): Int = 80
    private fun analyzeDependencyLicenses(): List<DependencyLicense> = emptyList()
    private fun parseApiCompatibilityReport(file: File): List<String> = emptyList()
}

// Data classes
@Serializable
data class QualityGateConfig(
    val projectName: String,
    val strictness: QualityStrictness,
    val coverage: CoverageConfig,
    val codeQuality: CodeQualityConfig,
    val security: SecurityConfig,
    val performance: PerformanceConfig,
    val testQuality: TestQualityConfig,
    val documentation: DocumentationConfig,
    val license: LicenseConfig,
    val compatibility: CompatibilityConfig
)

@Serializable
enum class QualityStrictness {
    STRICT,    // All checks must pass
    MODERATE,  // All required checks must pass
    LENIENT    // 80% of required checks must pass
}

@Serializable
data class CoverageConfig(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val minimumPercentage: Double = 80.0
)

@Serializable
data class CodeQualityConfig(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val maxIssues: Int = 100,
    val maxCriticalIssues: Int = 0
)

@Serializable
data class SecurityConfig(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val maxHighSeverityVulnerabilities: Int = 0
)

@Serializable
data class PerformanceConfig(
    val enabled: Boolean = true,
    val required: Boolean = false,
    val maxRegressionPercent: Double = 10.0
)

@Serializable
data class TestQualityConfig(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val minimumTestCount: Int = 100,
    val minimumSuccessRate: Double = 100.0
)

@Serializable
data class DocumentationConfig(
    val enabled: Boolean = true,
    val required: Boolean = false,
    val minimumCoverage: Double = 80.0
)

@Serializable
data class LicenseConfig(
    val enabled: Boolean = true,
    val required: Boolean = true,
    val blockedLicenses: List<String> = listOf("GPL-3.0", "AGPL-3.0")
)

@Serializable
data class CompatibilityConfig(
    val enabled: Boolean = true,
    val required: Boolean = false,
    val allowBreakingChanges: Boolean = false
)

@Serializable
data class QualityCheck(
    val name: String,
    val passed: Boolean,
    val required: Boolean,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class QualityGateReport(
    val timestamp: Instant,
    val projectName: String,
    val strictness: QualityStrictness,
    val passed: Boolean,
    val checks: List<QualityCheck>,
    val summary: QualityGateSummary
)

@Serializable
data class QualityGateSummary(
    val totalChecks: Int,
    val passedChecks: Int,
    val failedChecks: Int,
    val requiredChecks: Int,
    val passedRequiredChecks: Int
)

// Helper data classes
data class DetektResults(val totalIssues: Int, val criticalIssues: Int)
data class Vulnerability(val severity: String, val description: String)
data class PerformanceRegression(val benchmark: String, val regressionPercent: Double)
data class TestResults(val totalTests: Int, val failedTests: Int)
data class DependencyLicense(val dependency: String, val license: String)

class Logger(private val name: String) {
    fun info(message: String) = println("[$name] INFO: $message")
    fun error(message: String, throwable: Throwable? = null) {
        println("[$name] ERROR: $message")
        throwable?.printStackTrace()
    }
}

// Main execution
suspend fun main(args: Array<String>) {
    val configFile = args.getOrNull(0) ?: "quality-gate-config.json"
    val projectRoot = args.getOrNull(1) ?: "."

    try {
        val configContent = File(configFile).readText()
        val config = Json.decodeFromString<QualityGateConfig>(configContent)

        val enforcer = QualityGateEnforcer(config, projectRoot)
        val report = enforcer.enforceQualityGates()

        // Write report
        val reportJson = Json.encodeToString(QualityGateReport.serializer(), report)
        File("quality-gate-report.json").writeText(reportJson)

        // Console output
        println("\n" + "=".repeat(50))
        println("QUALITY GATE REPORT")
        println("=".repeat(50))
        println("Project: ${report.projectName}")
        println("Status: ${if (report.passed) "PASSED" else "FAILED"}")
        println("Strictness: ${report.strictness}")
        println("Checks: ${report.summary.passedChecks}/${report.summary.totalChecks} passed")
        println("Required: ${report.summary.passedRequiredChecks}/${report.summary.requiredChecks} passed")
        println("=".repeat(50))

        report.checks.forEach { check ->
            val status = if (check.passed) "✓" else "✗"
            val required = if (check.required) "[REQUIRED]" else "[OPTIONAL]"
            println("$status ${check.name} $required - ${check.message}")
        }

        println("=".repeat(50))

        if (!report.passed) {
            exitProcess(1)
        }
    } catch (e: Exception) {
        println("Quality gate enforcement failed: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}