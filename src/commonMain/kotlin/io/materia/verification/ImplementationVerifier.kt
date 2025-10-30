package io.materia.verification

import io.materia.verification.model.*

/**
 * Contract interface for implementation verification system
 * Validates that all library features are complete without placeholders
 */
interface ImplementationVerifier {

    /**
     * Scans the entire codebase for placeholder patterns
     * @param rootPath Root directory to scan
     * @param excludePatterns File patterns to exclude from scanning
     * @return List of all implementation artifacts with their status
     */
    suspend fun scanCodebase(
        rootPath: String,
        excludePatterns: List<String> = emptyList()
    ): VerificationResult<List<ImplementationArtifact>>

    /**
     * Analyzes a specific source file for completeness
     * @param filePath Path to the source file
     * @return Detailed analysis of the file's implementation status
     */
    suspend fun analyzeFile(filePath: String): VerificationResult<ImplementationArtifact>

    /**
     * Validates constitutional compliance for implementation artifacts
     * @param artifacts List of artifacts to validate
     * @return Compliance report with violations and recommendations
     */
    suspend fun validateConstitutionalCompliance(
        artifacts: List<ImplementationArtifact>
    ): VerificationResult<ComplianceReport>

    /**
     * Generates implementation tasks for completing placeholders
     * @param artifacts Artifacts containing placeholders
     * @param priority Minimum priority level to include
     * @return Ordered list of implementation tasks
     */
    suspend fun generateImplementationTasks(
        artifacts: List<ImplementationArtifact>,
        priority: Priority = Priority.MEDIUM
    ): VerificationResult<List<ImplementationTask>>

    /**
     * Verifies that an implementation is complete and production-ready
     * @param artifact Implementation artifact to verify
     * @return Verification status with any remaining issues
     */
    suspend fun verifyImplementationComplete(
        artifact: ImplementationArtifact
    ): VerificationResult<VerificationStatus>

    /**
     * Tracks progress of implementation completion
     * @return Current progress statistics
     */
    suspend fun getImplementationProgress(): VerificationResult<ProgressReport>

    /**
     * Validates that quality gates are met
     * @param gates List of quality gates to check
     * @param artifacts Artifacts to validate against gates
     * @return Gate validation results
     */
    suspend fun validateQualityGates(
        gates: List<QualityGate>,
        artifacts: List<ImplementationArtifact>
    ): VerificationResult<GateValidationReport>
}

/**
 * Result wrapper for verification operations
 */
sealed class VerificationResult<out T> {
    data class Success<T>(val data: T) : VerificationResult<T>()
    data class Failure(val error: VerificationError) : VerificationResult<Nothing>()
}

/**
 * Error types for verification operations
 */
sealed class VerificationError(open val message: String, open val cause: Throwable? = null) {
    data class FileNotFound(override val message: String) : VerificationError(message)
    data class ScanningError(override val message: String, override val cause: Throwable?) :
        VerificationError(message, cause)

    data class ConstitutionalViolation(override val message: String, val violations: List<String>) :
        VerificationError(message)

    data class CompilationError(override val message: String, val errors: List<String>) :
        VerificationError(message)

    data class PermissionError(override val message: String) : VerificationError(message)
}

/**
 * Reports and status data classes
 */
data class ComplianceReport(
    val totalArtifacts: Int,
    val compliantArtifacts: Int,
    val violations: List<ConstitutionalViolation>,
    val recommendations: List<String>,
    val overallCompliance: Boolean
)

data class ConstitutionalViolation(
    val principle: String,
    val artifact: ImplementationArtifact,
    val violation: String,
    val severity: Severity,
    val recommendation: String
)

data class ImplementationTask(
    val id: String,
    val title: String,
    val description: String,
    val artifact: ImplementationArtifact,
    val placeholders: List<PlaceholderPattern>,
    val priority: Priority,
    val estimatedEffort: io.materia.verification.model.Duration,
    val dependencies: List<String>,
    val testRequirements: List<String>
)

data class VerificationStatus(
    val isComplete: Boolean,
    val remainingIssues: List<PlaceholderPattern>,
    val testCoverage: Float,
    val constitutionalCompliance: Boolean,
    val recommendations: List<String>
)

data class ProgressReport(
    val totalArtifacts: Int,
    val completeArtifacts: Int,
    val inProgressArtifacts: Int,
    val remainingPlaceholders: Int,
    val overallProgress: Float,
    val moduleProgress: Map<ModuleType, Float>,
    val estimatedCompletion: io.materia.verification.model.Duration?
)

data class GateValidationReport(
    val totalGates: Int,
    val passedGates: Int,
    val failedGates: List<GateFailure>,
    val allGatesPassed: Boolean
)

data class GateFailure(
    val gate: QualityGate,
    val reason: String,
    val affectedArtifacts: List<ImplementationArtifact>,
    val recommendation: String
)

