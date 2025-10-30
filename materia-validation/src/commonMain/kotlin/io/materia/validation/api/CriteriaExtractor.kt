package io.materia.validation.api

import io.materia.validation.models.*

/**
 * Extracts validation criteria from validation results.
 */
internal class CriteriaExtractor {

    fun extractCriteria(
        result: ValidationResult,
        validatorName: String
    ): List<ValidationCriterion> {
        val criteria = mutableListOf<ValidationCriterion>()

        when (result) {
            is CompilationResult -> extractCompilationCriteria(criteria, result, validatorName)
            is TestResults -> extractTestCriteria(criteria, result, validatorName)
            is PerformanceMetrics -> extractPerformanceCriteria(criteria, result, validatorName)
            is ConstitutionalCompliance -> extractConstitutionalCriteria(
                criteria,
                result,
                validatorName
            )

            is SecurityValidationResult -> extractSecurityCriteria(criteria, result, validatorName)
        }

        // Generic criterion if none were added
        if (criteria.isEmpty()) {
            criteria.add(
                ValidationCriterion(
                    id = "$validatorName-general",
                    name = validatorName,
                    description = result.message,
                    severity = Severity.MEDIUM,
                    status = result.status,
                    requirement = "Passed",
                    actual = result.status.name,
                    details = emptyMap()
                )
            )
        }

        return criteria
    }

    private fun extractCompilationCriteria(
        criteria: MutableList<ValidationCriterion>,
        result: CompilationResult,
        validatorName: String
    ) {
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-compilation",
                name = "Compilation Success",
                description = "All platforms compile successfully",
                severity = Severity.CRITICAL,
                status = result.status,
                requirement = "All platforms pass",
                actual = "${result.platformResults.count { it.value.success }}/${result.platformResults.size} platforms pass",
                details = emptyMap()
            )
        )
    }

    private fun extractTestCriteria(
        criteria: MutableList<ValidationCriterion>,
        result: TestResults,
        validatorName: String
    ) {
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-tests",
                name = "Test Success Rate",
                description = "All tests pass",
                severity = Severity.HIGH,
                status = result.status,
                requirement = "100% pass rate",
                actual = "${result.passedTests}/${result.totalTests} tests pass",
                details = emptyMap()
            )
        )
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-coverage",
                name = "Test Coverage",
                description = "Adequate test coverage",
                severity = Severity.MEDIUM,
                status = if (result.lineCoverage >= 80f) ValidationStatus.PASSED else ValidationStatus.FAILED,
                requirement = "≥ 80%",
                actual = "${result.lineCoverage}%",
                details = emptyMap()
            )
        )
    }

    private fun extractPerformanceCriteria(
        criteria: MutableList<ValidationCriterion>,
        result: PerformanceMetrics,
        validatorName: String
    ) {
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-fps",
                name = "Frame Rate",
                description = "Meets 60 FPS requirement",
                severity = Severity.CRITICAL,
                status = if (result.minFps >= 60f) ValidationStatus.PASSED else ValidationStatus.FAILED,
                requirement = "≥ 60 FPS",
                actual = "${result.minFps} FPS",
                details = emptyMap()
            )
        )
    }

    private fun extractConstitutionalCriteria(
        criteria: MutableList<ValidationCriterion>,
        result: ConstitutionalCompliance,
        validatorName: String
    ) {
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-placeholders",
                name = "No Placeholder Code",
                description = "All code is implemented",
                severity = Severity.HIGH,
                status = if (result.placeholderCodeCount == 0) ValidationStatus.PASSED else ValidationStatus.FAILED,
                requirement = "0 placeholders",
                actual = "${result.placeholderCodeCount} placeholders",
                details = emptyMap()
            )
        )
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-tdd",
                name = "TDD Compliance",
                description = "Follows TDD practices",
                severity = Severity.MEDIUM,
                status = if (result.tddCompliance.isCompliant) ValidationStatus.PASSED else ValidationStatus.WARNING,
                requirement = "TDD compliant",
                actual = if (result.tddCompliance.isCompliant) "Compliant" else "Non-compliant",
                details = emptyMap()
            )
        )
    }

    private fun extractSecurityCriteria(
        criteria: MutableList<ValidationCriterion>,
        result: SecurityValidationResult,
        validatorName: String
    ) {
        criteria.add(
            ValidationCriterion(
                id = "$validatorName-vulnerabilities",
                name = "No Security Vulnerabilities",
                description = "No security issues found",
                severity = Severity.CRITICAL,
                status = if (result.vulnerabilities.isEmpty()) ValidationStatus.PASSED else ValidationStatus.FAILED,
                requirement = "0 vulnerabilities",
                actual = "${result.vulnerabilities.size} vulnerabilities",
                details = emptyMap()
            )
        )
    }
}
