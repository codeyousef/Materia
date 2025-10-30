package io.materia.verification

import io.materia.verification.impl.DefaultImplementationVerifier
import io.materia.verification.impl.DefaultPlaceholderDetector
import io.materia.verification.model.ImplementationArtifact
import io.materia.verification.model.ModuleType
import io.materia.verification.model.Priority

/**
 * Utility for running verification scans on the Materia codebase
 * This provides the main entry point for placeholder detection and constitutional compliance validation
 */
object VerificationRunner {

    /**
     * Runs a comprehensive verification scan of the codebase
     * @param rootPath Root directory to scan (default: current project)
     * @return Verification results with detailed reports
     */
    suspend fun runFullVerification(rootPath: String = "src/commonMain/kotlin"): VerificationResults {
        val verifier = DefaultImplementationVerifier()
        val detector = DefaultPlaceholderDetector()

        // Configure detector with comprehensive patterns
        detector.configureDetection(
            patterns = StandardDetectionPatterns.ALL,
            exclusions = listOf(
                "build/",
                ".gradle/",
                ".git/",
                "node_modules/",
                "*.class",
                "*.jar",
                "**/test/**",
                "**/Test.kt"
            )
        )

        // Scan codebase for artifacts
        val artifactsResult = verifier.scanCodebase(rootPath)
        val artifacts = when (artifactsResult) {
            is VerificationResult.Success -> artifactsResult.data
            is VerificationResult.Failure -> {
                return VerificationResults(
                    success = false,
                    error = "Failed to scan codebase: ${artifactsResult.error.message}",
                    totalFiles = 0,
                    totalPlaceholders = 0,
                    artifacts = emptyList<ImplementationArtifact>(),
                    complianceReport = null,
                    progressReport = null
                )
            }
        }

        // Validate constitutional compliance
        val complianceResult = verifier.validateConstitutionalCompliance(artifacts)
        val complianceReport = when (complianceResult) {
            is VerificationResult.Success -> complianceResult.data
            is VerificationResult.Failure -> null
        }

        // Get implementation progress
        val progressResult = verifier.getImplementationProgress()
        val progressReport = when (progressResult) {
            is VerificationResult.Success -> progressResult.data
            is VerificationResult.Failure -> null
        }

        // Calculate statistics
        val totalFiles = artifacts.size
        val totalPlaceholders = artifacts.sumOf { it.placeholderCount }

        return VerificationResults(
            success = true,
            error = null,
            totalFiles = totalFiles,
            totalPlaceholders = totalPlaceholders,
            artifacts = artifacts,
            complianceReport = complianceReport,
            progressReport = progressReport
        )
    }

    /**
     * Runs a focused scan on critical modules only
     */
    suspend fun runCriticalModulesScan(): VerificationResults {
        val results = runFullVerification()

        if (!results.success) return results

        // Filter to only critical modules
        val criticalModules = setOf(
            ModuleType.RENDERER,
            ModuleType.ANIMATION,
            ModuleType.PHYSICS,
            ModuleType.LIGHTING
        )

        val criticalArtifacts = results.artifacts.filter {
            it.moduleType in criticalModules
        }

        return results.copy(
            artifacts = criticalArtifacts,
            totalFiles = criticalArtifacts.size,
            totalPlaceholders = criticalArtifacts.sumOf { it.placeholderCount }
        )
    }

    /**
     * Validates quality gates for production readiness
     */
    suspend fun validateProductionReadiness(): QualityGateResults {
        val verifier = DefaultImplementationVerifier()
        val results = runFullVerification()

        if (!results.success) {
            return QualityGateResults(
                allGatesPassed = false,
                passedGates = 0,
                totalGates = 0,
                failures = emptyList(),
                error = results.error
            )
        }

        val gateResult =
            verifier.validateQualityGates(QualityGates.CONSTITUTIONAL_GATES, results.artifacts)

        return when (gateResult) {
            is VerificationResult.Success -> {
                val report = gateResult.data
                QualityGateResults(
                    allGatesPassed = report.allGatesPassed,
                    passedGates = report.passedGates,
                    totalGates = report.totalGates,
                    failures = report.failedGates,
                    error = null
                )
            }

            is VerificationResult.Failure -> {
                QualityGateResults(
                    allGatesPassed = false,
                    passedGates = 0,
                    totalGates = QualityGates.CONSTITUTIONAL_GATES.size,
                    failures = emptyList<GateFailure>(),
                    error = gateResult.error.message
                )
            }
        }
    }

    /**
     * Prints a summary report of verification results
     */
    fun printSummaryReport(results: VerificationResults) {
        println("=".repeat(80))
        println("Materia Library Implementation Verification Report")
        println("=".repeat(80))

        if (!results.success) {
            println("❌ VERIFICATION FAILED: ${results.error}")
            return
        }

        println("📊 OVERALL STATISTICS:")
        println("   Files Scanned: ${results.totalFiles}")
        println("   Total Placeholders Found: ${results.totalPlaceholders}")
        println("   Constitutional Compliance: ${if (results.complianceReport?.overallCompliance == true) "✅ COMPLIANT" else "❌ VIOLATIONS FOUND"}")

        if (results.progressReport != null) {
            val progress = results.progressReport
            println("   Overall Progress: ${(progress.overallProgress * 100).toInt()}%")
            println("   Complete Artifacts: ${progress.completeArtifacts}/${progress.totalArtifacts}")
            println("   Remaining Placeholders: ${progress.remainingPlaceholders}")
        }

        println("\n📋 BY MODULE:")
        val byModule = results.artifacts.groupBy { it.moduleType }
        val sortedModules = byModule.keys.sortedBy { it.toString() }
        for (module in sortedModules) {
            val artifacts = byModule[module] ?: continue
            val totalPlaceholders = artifacts.sumOf { it.placeholderCount }
            val compliantFiles = artifacts.count { it.constitutionalCompliance }
            val status = if (totalPlaceholders == 0) "✅" else "❌"

            println("   $status ${module.toString()}: ${artifacts.size} files, $totalPlaceholders placeholders, $compliantFiles compliant")
        }

        if (results.complianceReport != null && results.complianceReport.violations.isNotEmpty()) {
            println("\n⚠️  CONSTITUTIONAL VIOLATIONS:")
            for (violation in results.complianceReport.violations.take(10)) {
                println("   - ${violation.principle}: ${violation.violation}")
            }
            if (results.complianceReport.violations.size > 10) {
                println("   ... and ${results.complianceReport.violations.size - 10} more violations")
            }
        }

        println("\n🎯 PRIORITY ACTIONS:")
        val criticalArtifacts =
            results.artifacts.filter { it.priority == Priority.CRITICAL && it.placeholderCount > 0 }
        if (criticalArtifacts.isNotEmpty()) {
            println("   CRITICAL (${criticalArtifacts.size} files need immediate attention):")
            for (artifact in criticalArtifacts.take(5)) {
                println("     - ${artifact.filePath}: ${artifact.placeholderCount} placeholders")
            }
        }

        val highArtifacts =
            results.artifacts.filter { it.priority == Priority.HIGH && it.placeholderCount > 0 }
        if (highArtifacts.isNotEmpty()) {
            println("   HIGH PRIORITY (${highArtifacts.size} files):")
            for (artifact in highArtifacts.take(3)) {
                println("     - ${artifact.filePath}: ${artifact.placeholderCount} placeholders")
            }
        }

        println("=".repeat(80))
    }
}

/**
 * Results of a verification scan
 */
data class VerificationResults(
    val success: Boolean,
    val error: String?,
    val totalFiles: Int,
    val totalPlaceholders: Int,
    val artifacts: List<ImplementationArtifact>,
    val complianceReport: ComplianceReport?,
    val progressReport: ProgressReport?
)

/**
 * Results of quality gate validation
 */
data class QualityGateResults(
    val allGatesPassed: Boolean,
    val passedGates: Int,
    val totalGates: Int,
    val failures: List<GateFailure>,
    val error: String?
)