package io.materia.validation.api

import io.materia.validation.models.*
import io.materia.validation.services.*

/**
 * Main orchestration API for production readiness validation.
 *
 * This checker coordinates all validators and aggregates their results into
 * a comprehensive production readiness report. It implements a flexible
 * validation pipeline that can be configured for different validation modes.
 *
 * ## Usage Example
 * ```kotlin
 * val checker = ProductionReadinessChecker()
 * val report = checker.validateProductionReadiness(
 *     projectPath = "/path/to/project",
 *     configuration = ValidationConfiguration.strict()
 * )
 *
 * println("Production ready: ${report.isProductionReady}")
 * println("Overall score: ${report.overallScore}")
 * ```
 */
class ProductionReadinessChecker {

    private val validators = mutableListOf<Validator<*>>()
    private val orchestrator get() = ValidationOrchestrator(validators)
    private val criteriaExtractor = CriteriaExtractor()
    private val remediationGenerator = RemediationActionGenerator()
    private val aggregator = ResultAggregator(criteriaExtractor, remediationGenerator)
    private val recommendationEngine = RecommendationEngine()

    init {
        // Register default validators
        registerDefaultValidators()
    }

    /**
     * Main entry point for production readiness validation.
     *
     * Executes all applicable validators and returns a comprehensive report.
     *
     * @param projectPath The root path of the project to validate
     * @param configuration The validation configuration to use
     * @param platforms Optional list of platforms to validate (null = all platforms)
     * @return A comprehensive production readiness report
     */
    suspend fun validateProductionReadiness(
        projectPath: String,
        configuration: ValidationConfiguration = ValidationConfiguration.strict(),
        platforms: List<Platform>? = null
    ): ProductionReadinessReport {

        val context = ValidationContext(
            projectPath = projectPath,
            platforms = platforms?.map { it.name },
            configuration = mapOf(
                "coverageThreshold" to configuration.coverageThreshold,
                "maxArtifactSize" to configuration.maxArtifactSize,
                "failFast" to configuration.failFast,
                "performanceRequirements" to configuration.performanceRequirements
            )
        )

        // Execute validators in parallel
        val executionResult = orchestrator.executeValidation(context)

        // Aggregate results
        return aggregator.aggregate(executionResult, configuration, context)
    }

    /**
     * Validates performance metrics specifically.
     *
     * @param projectPath The project path to validate
     * @param configuration Optional configuration overrides
     * @return Performance validation metrics
     */
    suspend fun validatePerformance(
        projectPath: String,
        configuration: Map<String, Any> = emptyMap()
    ): PerformanceMetrics {
        val validator = PerformanceValidator()
        val context = ValidationContext(
            projectPath = projectPath,
            configuration = configuration
        )
        return validator.validate(context)
    }

    /**
     * Validates compilation across platforms.
     *
     * @param projectPath The project path to validate
     * @param platforms Platforms to compile (null = all)
     * @return Compilation validation results
     */
    suspend fun validateCompilation(
        projectPath: String,
        platforms: List<String>? = null
    ): CompilationResult {
        val validator = CompilationValidator()
        val context = ValidationContext(
            projectPath = projectPath,
            platforms = platforms
        )
        return validator.validate(context)
    }

    /**
     * Validates test coverage and test suite health.
     *
     * @param projectPath The project path to validate
     * @return Test coverage validation results
     */
    suspend fun validateTestCoverage(
        projectPath: String
    ): TestResults {
        val validator = TestCoverageValidator()
        val context = ValidationContext(projectPath = projectPath)
        return validator.validate(context)
    }

    /**
     * Validates constitutional compliance (TDD, code quality, etc).
     *
     * @param projectPath The project path to validate
     * @return Constitutional compliance results
     */
    suspend fun validateConstitutionalCompliance(
        projectPath: String
    ): ConstitutionalCompliance {
        val validator = ConstitutionalValidator()
        val context = ValidationContext(projectPath = projectPath)
        return validator.validate(context)
    }

    /**
     * Validates security vulnerabilities and best practices.
     *
     * @param projectPath The project path to validate
     * @return Security validation results
     */
    suspend fun validateSecurity(
        projectPath: String
    ): SecurityValidationResult {
        val validator = SecurityValidator()
        val context = ValidationContext(projectPath = projectPath)
        return validator.validate(context)
    }

    /**
     * Adds a custom validator to the validation pipeline.
     *
     * @param validator The validator to add
     */
    fun registerValidator(validator: Validator<*>) {
        validators.add(validator)
    }

    /**
     * Removes a validator from the validation pipeline.
     *
     * @param validatorName The name of the validator to remove
     * @return true if removed, false if not found
     */
    fun unregisterValidator(validatorName: String): Boolean {
        return validators.removeAll { it.name == validatorName }
    }

    /**
     * Gets the list of registered validator names.
     */
    fun getRegisteredValidators(): List<String> {
        return validators.map { it.name }
    }

    /**
     * Generates actionable recommendations based on validation results.
     *
     * @param report The production readiness report
     * @return List of prioritized recommendations
     */
    fun generateRecommendations(report: ProductionReadinessReport): List<Recommendation> {
        return recommendationEngine.generateRecommendations(report)
    }

    /**
     * Registers the default set of validators.
     */
    private fun registerDefaultValidators() {
        validators.apply {
            add(CompilationValidator())
            add(TestCoverageValidator())
            add(PerformanceValidator())
            add(ConstitutionalValidator())
            add(SecurityValidator())
        }
    }
}
