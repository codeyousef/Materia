package io.materia.validation.api

import io.materia.validation.models.*
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * Aggregates individual validation results into a comprehensive report.
 */
internal class ResultAggregator(
    private val criteriaExtractor: CriteriaExtractor,
    private val remediationGenerator: RemediationActionGenerator
) {

    fun aggregate(
        executionResult: ValidationExecutionResult,
        configuration: ValidationConfiguration,
        context: ValidationContext
    ): ProductionReadinessReport {

        val categories = mutableListOf<io.materia.validation.models.ValidationCategory>()
        val remediationActions = mutableListOf<io.materia.validation.models.RemediationAction>()

        // Get git information from context or use defaults
        val branchName = context.configuration["branchName"] as? String ?: "unknown"
        val commitHash = context.configuration["commitHash"] as? String ?: "unknown"

        // Process each validation result
        executionResult.results.forEach { validatorResult ->
            val result = validatorResult.result

            if (result != null) {
                // Convert to ValidationCategory
                val categoryName = getCategoryNameForValidator(validatorResult.name)
                val criteria = criteriaExtractor.extractCriteria(result, validatorResult.name)
                val categoryScore = result.score
                val categoryStatus = result.status

                categories.add(
                    io.materia.validation.models.ValidationCategory(
                        name = categoryName,
                        status = categoryStatus,
                        score = categoryScore,
                        weight = getCategoryWeight(categoryName),
                        criteria = criteria
                    )
                )

                // Extract remediation actions from failures
                if (result.status == ValidationStatus.FAILED) {
                    remediationActions.addAll(remediationGenerator.generate(result, categoryName))
                }
            } else if (validatorResult.error != null) {
                // Handle validation errors
                val errorCategory = io.materia.validation.models.ValidationCategory(
                    name = validatorResult.name,
                    status = ValidationStatus.ERROR,
                    score = 0.0f,
                    weight = 0.1f,
                    criteria = listOf(
                        ValidationCriterion(
                            id = "${validatorResult.name}-error",
                            name = "Validator Execution",
                            description = "Validator failed to execute: ${validatorResult.error.message}",
                            requirement = "Success",
                            actual = "Error: ${validatorResult.error.message}",
                            status = ValidationStatus.ERROR,
                            severity = Severity.HIGH,
                            details = emptyMap()
                        )
                    )
                )
                categories.add(errorCategory)
            }
        }

        // Calculate overall score
        val overallScore = calculateOverallScore(categories)

        // Determine overall status
        val overallStatus = determineOverallStatus(categories)

        return ProductionReadinessReport(
            timestamp = Clock.System.now(),
            branchName = branchName,
            commitHash = commitHash,
            overallStatus = overallStatus,
            overallScore = overallScore,
            categories = categories,
            remediationActions = remediationActions,
            executionTime = executionResult.executionTime
        )
    }

    private fun getCategoryNameForValidator(validatorName: String): String {
        return when {
            validatorName.contains("Compilation", ignoreCase = true) ->
                io.materia.validation.models.ValidationCategory.COMPILATION

            validatorName.contains("Test", ignoreCase = true) ->
                io.materia.validation.models.ValidationCategory.TESTING

            validatorName.contains("Performance", ignoreCase = true) ->
                io.materia.validation.models.ValidationCategory.PERFORMANCE

            validatorName.contains("Constitutional", ignoreCase = true) ->
                io.materia.validation.models.ValidationCategory.CONSTITUTIONAL

            validatorName.contains("Security", ignoreCase = true) ->
                io.materia.validation.models.ValidationCategory.SECURITY

            else -> "Infrastructure"
        }
    }

    private fun getCategoryWeight(categoryName: String): Float {
        return when (categoryName) {
            io.materia.validation.models.ValidationCategory.COMPILATION -> 0.25f
            io.materia.validation.models.ValidationCategory.TESTING -> 0.20f
            io.materia.validation.models.ValidationCategory.PERFORMANCE -> 0.20f
            io.materia.validation.models.ValidationCategory.CONSTITUTIONAL -> 0.15f
            io.materia.validation.models.ValidationCategory.SECURITY -> 0.15f
            else -> 0.05f
        }
    }

    private fun calculateOverallScore(categories: List<io.materia.validation.models.ValidationCategory>): Float {
        if (categories.isEmpty()) return 0.0f

        var totalWeight = 0.0f
        var weightedSum = 0.0f

        categories.forEach { category ->
            weightedSum += category.score * category.weight
            totalWeight += category.weight
        }

        return if (totalWeight > 0) (weightedSum / totalWeight) else 0.0f
    }

    private fun determineOverallStatus(categories: List<io.materia.validation.models.ValidationCategory>): ValidationStatus {
        if (categories.isEmpty()) return ValidationStatus.SKIPPED

        val hasCriticalFailures = categories.any { it.hasCriticalFailures }
        if (hasCriticalFailures) return ValidationStatus.FAILED

        val hasFailures = categories.any { it.status == ValidationStatus.FAILED }
        if (hasFailures) return ValidationStatus.FAILED

        val hasErrors = categories.any { it.status == ValidationStatus.ERROR }
        if (hasErrors) return ValidationStatus.ERROR

        val hasWarnings = categories.any { it.status == ValidationStatus.WARNING }
        if (hasWarnings) return ValidationStatus.WARNING

        return ValidationStatus.PASSED
    }
}
