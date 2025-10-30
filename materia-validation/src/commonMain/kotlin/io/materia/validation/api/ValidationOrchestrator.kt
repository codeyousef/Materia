package io.materia.validation.api

import io.materia.validation.models.*
import io.materia.validation.services.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlin.time.Duration

/**
 * Orchestrates parallel execution of validators.
 */
internal class ValidationOrchestrator(
    private val validators: List<Validator<*>>
) {

    suspend fun executeValidation(
        context: ValidationContext
    ): ValidationExecutionResult = coroutineScope {

        val startTime = Clock.System.now()

        // Execute validators in parallel where possible
        val validationJobs = validators.mapNotNull { validator ->
            if (validator.isApplicable(context)) {
                async {
                    try {
                        ValidatorResult(
                            name = validator.name,
                            result = validator.validate(context),
                            error = null
                        )
                    } catch (e: Exception) {
                        ValidatorResult(
                            name = validator.name,
                            result = null,
                            error = e
                        )
                    }
                }
            } else {
                null
            }
        }

        val results = validationJobs.awaitAll()
        val endTime = Clock.System.now()
        val executionTime = endTime - startTime

        ValidationExecutionResult(
            results = results,
            executionTime = executionTime
        )
    }
}

/**
 * Result of validation execution.
 */
internal data class ValidationExecutionResult(
    val results: List<ValidatorResult>,
    val executionTime: Duration
)

/**
 * Internal class to hold validator results.
 */
internal data class ValidatorResult(
    val name: String,
    val result: ValidationResult?,
    val error: Throwable?
)
