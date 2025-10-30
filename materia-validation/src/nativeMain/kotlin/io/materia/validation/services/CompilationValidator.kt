package io.materia.validation.services

import io.materia.validation.api.ValidationContext
import io.materia.validation.api.Validator
import io.materia.validation.models.*

/**
 * Native-specific implementation of the CompilationValidator.
 *
 * Note: Full compilation validation on Native platforms requires platform-specific
 * process execution (execvp on POSIX, CreateProcess on Windows) which varies across
 * native targets. Current implementation returns SKIPPED status as validation is
 * primarily performed on JVM platforms where Gradle tooling is available.
 */
actual class CompilationValidator actual constructor() : Validator<CompilationResult> {

    actual override val name: String = "Native Compilation Validator"

    /**
     * Convenience method to validate compilation for a given project path.
     *
     * @param projectPath The path to the project to validate
     * @param platforms The list of platforms to validate (null means all)
     * @param timeoutMillis Optional timeout in milliseconds
     * @return CompilationResult containing the validation results
     */
    suspend fun validateCompilation(
        projectPath: String,
        platforms: List<io.materia.validation.models.Platform>? = null,
        timeoutMillis: Long? = null
    ): CompilationResult {
        val platformStrings = platforms?.map { it.name.lowercase() }
        val config = if (timeoutMillis != null) {
            mapOf("timeoutMillis" to timeoutMillis)
        } else {
            emptyMap()
        }

        val context = ValidationContext(
            projectPath = projectPath,
            platforms = platformStrings,
            configuration = config
        )
        return validate(context)
    }

    actual override suspend fun validate(context: ValidationContext): CompilationResult {
        // Native platform validation skipped - use JVM validator for compilation checks
        // Full implementation would require platform-specific process execution APIs
        return CompilationResult(
            status = ValidationStatus.SKIPPED,
            score = 1.0f,
            message = "Compilation validation deferred to JVM platform (Gradle tooling)",
            platformResults = emptyMap(),
            errors = emptyList(),
            warnings = emptyList(),
            compilationTime = 0L
        )
    }
}