package io.materia.validation.services

import io.materia.validation.api.ValidationContext
import io.materia.validation.api.ValidationException
import io.materia.validation.api.Validator
import io.materia.validation.models.*

/**
 * Validates compilation across all specified platforms.
 *
 * This validator performs platform-specific compilation checks to ensure
 * the codebase compiles successfully on all target platforms. It uses
 * the expect/actual pattern to delegate to platform-specific implementations.
 *
 * ## Responsibilities
 * - Compile code for each specified platform
 * - Collect compilation errors and warnings
 * - Measure compilation time
 * - Report platform-specific compilation status
 *
 * ## Platform Support
 * - JVM: Uses Gradle/Kotlin compiler
 * - JS: Uses Kotlin/JS compiler
 * - Native: Uses Kotlin/Native compiler
 * - Android: Uses Android Gradle Plugin
 * - iOS: Uses Kotlin/Native with iOS targets
 *
 * @see CompilationResult for the structure of returned results
 */
expect class CompilationValidator() : Validator<CompilationResult> {
    override val name: String

    /**
     * Validates compilation for the specified platforms.
     *
     * This method will attempt to compile the project for each platform
     * specified in the context. If no platforms are specified, it will
     * compile for all available platforms.
     *
     * @param context The validation context containing project path and platforms
     * @return CompilationResult with platform-specific compilation statuses
     * @throws ValidationException if compilation cannot be initiated
     */
    override suspend fun validate(context: ValidationContext): CompilationResult
}

/**
 * Common implementation helper for CompilationValidator.
 *
 * This class provides shared logic that can be used by platform-specific
 * implementations to reduce code duplication.
 */
internal class CompilationValidatorHelper {

    /**
     * Calculates the overall score based on platform results.
     *
     * @param platformResults Map of platform to compilation status
     * @return Score from 0.0 to 1.0 based on success rate
     */
    fun calculateScore(platformResults: Map<String, PlatformCompilationStatus>): Float {
        if (platformResults.isEmpty()) return 0f
        val successCount = platformResults.count { it.value.success }
        return successCount.toFloat() / platformResults.size
    }

    /**
     * Determines overall status based on platform results.
     *
     * @param platformResults Map of platform to compilation status
     * @return Overall validation status
     */
    fun determineStatus(platformResults: Map<String, PlatformCompilationStatus>): ValidationStatus {
        return when {
            platformResults.isEmpty() -> ValidationStatus.ERROR
            platformResults.all { it.value.success } -> ValidationStatus.PASSED
            platformResults.any { it.value.success } -> ValidationStatus.WARNING
            else -> ValidationStatus.FAILED
        }
    }

    /**
     * Generates a summary message based on compilation results.
     *
     * @param platformResults Map of platform to compilation status
     * @return Human-readable summary message
     */
    fun generateMessage(platformResults: Map<String, PlatformCompilationStatus>): String {
        val successCount = platformResults.count { it.value.success }
        val totalCount = platformResults.size

        return when {
            totalCount == 0 -> "No platforms validated"
            successCount == totalCount -> "All $totalCount platforms compiled successfully"
            successCount == 0 -> "Compilation failed on all $totalCount platforms"
            else -> "Compilation succeeded on $successCount of $totalCount platforms"
        }
    }

    /**
     * Collects all errors from platform results.
     *
     * @param platformResults Map of platform to compilation status
     * @return List of compilation errors across all platforms
     */
    fun collectErrors(platformResults: Map<String, PlatformCompilationStatus>): List<CompilationError> {
        return platformResults.flatMap { (platform, status) ->
            status.errorMessages.mapIndexed { index, message ->
                CompilationError(
                    file = extractFileFromMessage(message) ?: "unknown",
                    line = extractLineFromMessage(message) ?: 0,
                    column = extractColumnFromMessage(message) ?: 0,
                    message = "$platform: $message",
                    severity = "ERROR"
                )
            }
        }
    }

    /**
     * Collects all warnings from platform results.
     *
     * @param platformResults Map of platform to compilation status
     * @return List of compilation warnings across all platforms
     */
    fun collectWarnings(platformResults: Map<String, PlatformCompilationStatus>): List<CompilationWarning> {
        return platformResults.flatMap { (platform, status) ->
            status.warningMessages.mapIndexed { index, message ->
                CompilationWarning(
                    file = extractFileFromMessage(message) ?: "unknown",
                    line = extractLineFromMessage(message) ?: 0,
                    column = extractColumnFromMessage(message) ?: 0,
                    message = "$platform: $message",
                    severity = "WARNING"
                )
            }
        }
    }

    /**
     * Extracts file path from compiler message if present.
     */
    private fun extractFileFromMessage(message: String): String? {
        // Common patterns: "file.kt:12:5: error" or "file.kt: error"
        val regex = Regex("^([^:]+\\.kt[sx]?)[::]")
        return regex.find(message)?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts line number from compiler message if present.
     */
    private fun extractLineFromMessage(message: String): Int? {
        // Pattern: "file.kt:12:5: error"
        val regex = Regex(":\\s*(\\d+)\\s*:")
        return regex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Extracts column number from compiler message if present.
     */
    private fun extractColumnFromMessage(message: String): Int? {
        // Pattern: "file.kt:12:5: error"
        val regex = Regex(":\\s*\\d+\\s*:\\s*(\\d+)")
        return regex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Gets the list of default platforms to compile if none specified.
     */
    fun getDefaultPlatforms(): List<String> {
        return listOf("jvm", "js", "native")
    }

    /**
     * Validates and filters the requested platforms.
     *
     * @param requested List of requested platforms (null means all)
     * @return List of valid platforms to compile
     */
    fun validatePlatforms(requested: List<String>?): List<String> {
        val validPlatforms = setOf(
            "jvm", "js", "native", "android", "ios",
            "linuxX64", "linuxArm64",
            "mingwX64",
            "macosX64", "macosArm64",
            "iosArm64", "iosX64", "iosSimulatorArm64",
            "watchosArm64", "watchosX64",
            "tvosArm64", "tvosX64"
        )

        return requested?.filter { it in validPlatforms }
            ?: getDefaultPlatforms()
    }
}