package io.materia.validation.api

import io.materia.validation.models.*

/**
 * Generates remediation actions from validation failures.
 */
internal class RemediationActionGenerator {

    fun generate(
        result: ValidationResult,
        categoryName: String
    ): List<io.materia.validation.models.RemediationAction> {
        val actions = mutableListOf<io.materia.validation.models.RemediationAction>()

        when (result) {
            is CompilationResult -> generateCompilationActions(actions, result)
            is TestResults -> generateTestActions(actions, result)
            is ConstitutionalCompliance -> generateConstitutionalActions(actions, result)
        }

        return actions
    }

    private fun generateCompilationActions(
        actions: MutableList<io.materia.validation.models.RemediationAction>,
        result: CompilationResult
    ) {
        result.errors.forEach { error ->
            actions.add(
                io.materia.validation.models.RemediationAction(
                    criterionId = "compilation-error",
                    title = "Fix compilation error in ${error.file}",
                    description = error.message,
                    steps = listOf(
                        "Review error at ${error.file}:${error.line}",
                        "Fix the compilation issue"
                    ),
                    estimatedEffort = "15 minutes",
                    priority = 1,
                    automatable = false
                )
            )
        }
    }

    private fun generateTestActions(
        actions: MutableList<io.materia.validation.models.RemediationAction>,
        result: TestResults
    ) {
        if (result.failedTests > 0) {
            actions.add(
                io.materia.validation.models.RemediationAction(
                    criterionId = "test-failures",
                    title = "Fix ${result.failedTests} failing tests",
                    description = "Tests are failing and need to be fixed",
                    steps = listOf(
                        "Review failed tests",
                        "Debug and fix issues",
                        "Verify all tests pass"
                    ),
                    estimatedEffort = "${result.failedTests * 20} minutes",
                    priority = 2,
                    automatable = false
                )
            )
        }
        if (result.lineCoverage < 80f) {
            actions.add(
                io.materia.validation.models.RemediationAction(
                    criterionId = "test-coverage",
                    title = "Increase test coverage to 80%",
                    description = "Test coverage is below the required threshold",
                    steps = listOf(
                        "Identify uncovered code",
                        "Write tests for uncovered paths",
                        "Verify coverage threshold"
                    ),
                    estimatedEffort = "2 hours",
                    priority = 3,
                    automatable = false
                )
            )
        }
    }

    private fun generateConstitutionalActions(
        actions: MutableList<io.materia.validation.models.RemediationAction>,
        result: ConstitutionalCompliance
    ) {
        if (result.placeholderCodeCount > 0) {
            actions.add(
                io.materia.validation.models.RemediationAction(
                    criterionId = "placeholder-code",
                    title = "Replace ${result.placeholderCodeCount} placeholders with implementations",
                    description = "Placeholder code needs to be implemented",
                    steps = listOf(
                        "Review each placeholder",
                        "Implement proper functionality",
                        "Remove TODO/FIXME markers"
                    ),
                    estimatedEffort = "${result.placeholderCodeCount * 30} minutes",
                    priority = 2,
                    automatable = false
                )
            )
        }
    }
}
