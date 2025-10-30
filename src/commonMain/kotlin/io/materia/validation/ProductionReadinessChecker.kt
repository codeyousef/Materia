package io.materia.validation

/**
 * Contract interface for overall production readiness validation.
 *
 * This interface defines the contract for comprehensive production
 * readiness checking of the Materia codebase, coordinating placeholder
 * detection, implementation validation, and renderer verification.
 */
interface ProductionReadinessChecker {

    /**
     * Performs comprehensive production readiness validation.
     *
     * @param projectRoot Root directory of the Materia project
     * @param validationConfig Configuration for validation checks
     * @return ValidationResult with overall readiness assessment
     */
    suspend fun validateProductionReadiness(
        projectRoot: String,
        validationConfig: ValidationConfiguration = ValidationConfiguration.strict()
    ): ValidationResult

    /**
     * Runs placeholder detection across the entire codebase.
     *
     * @param projectRoot Root directory to scan
     * @return ScanResult containing all detected placeholders
     */
    suspend fun scanForPlaceholders(projectRoot: String): ScanResult

    /**
     * Analyzes implementation completeness across all platforms.
     *
     * @param projectRoot Root directory to analyze
     * @return GapAnalysisResult containing implementation gaps
     */
    suspend fun analyzeImplementationGaps(projectRoot: String): GapAnalysisResult

    /**
     * Validates renderer implementations for all platforms.
     *
     * @param projectRoot Root directory containing renderer code
     * @return RendererAuditResult with renderer validation status
     */
    suspend fun auditRendererImplementations(projectRoot: String): RendererAuditResult

    /**
     * Executes the full test suite and validates results.
     *
     * @param projectRoot Root directory of project
     * @return TestExecutionResult with test outcomes
     */
    suspend fun executeTestSuite(projectRoot: String): TestExecutionResult

    /**
     * Validates example applications functionality.
     *
     * @param projectRoot Root directory containing examples
     * @return ExampleValidationResult with example status
     */
    suspend fun validateExamples(projectRoot: String): ExampleValidationResult

    /**
     * Measures performance against constitutional standards.
     *
     * @param projectRoot Root directory of project
     * @return PerformanceValidationResult with benchmark results
     */
    suspend fun validatePerformance(projectRoot: String): PerformanceValidationResult

    /**
     * Checks constitutional compliance across all validation areas.
     *
     * @param validationResult Overall validation result to check
     * @return ComplianceResult with constitutional adherence status
     */
    fun checkConstitutionalCompliance(validationResult: ValidationResult): ComplianceResult

    /**
     * Generates recommendations for addressing validation failures.
     *
     * @param validationResult Validation result to analyze
     * @return List of actionable recommendations for improvement
     */
    fun generateRecommendations(validationResult: ValidationResult): List<String>

    /**
     * Creates a summary report of production readiness status.
     *
     * @param validationResult Complete validation results
     * @return ProductionReadinessReport formatted for stakeholders
     */
    fun generateReadinessReport(validationResult: ValidationResult): ProductionReadinessReport
}