package io.materia.validation.services

import io.materia.validation.api.ValidationContext
import io.materia.validation.api.Validator
import io.materia.validation.models.*

/**
 * JS-specific implementation of the CompilationValidator.
 *
 * Note: Browser-based compilation validation is not supported due to environment
 * limitations. Validation requires Node.js environment with file system access
 * and Gradle tooling. Current implementation returns SKIPPED status as validation
 * is performed on JVM platforms.
 */
actual class CompilationValidator actual constructor() : Validator<CompilationResult> {

    actual override val name: String = "JS Compilation Validator"

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
        // Browser environment validation skipped - use JVM validator for compilation checks
        // Full implementation would require Node.js with file system and Gradle access
        return CompilationResult(
            status = ValidationStatus.SKIPPED,
            score = 1.0f,
            message = "Compilation validation deferred to JVM platform (browser limitation)",
            platformResults = emptyMap(),
            errors = emptyList(),
            warnings = emptyList(),
            compilationTime = 0L
        )
    }
}