package io.materia.validation.models

import io.materia.validation.utils.format
import kotlinx.serialization.Serializable

/**
 * Configuration settings for validation execution.
 *
 * Controls which validations are performed, their thresholds, and output options.
 * This configuration allows customizing the validation process for different
 * environments (development, CI/CD, production) and use cases.
 *
 * @property enabledCategories Set of category names to validate (empty = all categories).
 * @property platforms Set of platforms to validate (empty = all available platforms).
 * @property performanceRequirements Performance thresholds and requirements.
 * @property coverageThreshold Minimum required test coverage percentage (default: 95.0%).
 * @property maxArtifactSize Maximum allowed artifact size in bytes (default: 2MB).
 * @property failFast Whether to stop validation on first critical failure (default: false).
 * @property generateHtmlReport Whether to generate an HTML report (default: true).
 * @property outputDirectory Directory for validation reports (default: "build/validation-reports").
 */
@Serializable
data class ValidationConfiguration(
    val enabledCategories: Set<String> = emptySet(),
    val platforms: Set<Platform> = emptySet(),
    val performanceRequirements: PerformanceRequirements = PerformanceRequirements(),
    val coverageThreshold: Float = 95.0f,
    val maxArtifactSize: Long = 2_097_152L, // 2 MB in bytes
    val failFast: Boolean = false,
    val generateHtmlReport: Boolean = true,
    val outputDirectory: String = "build/validation-reports"
) {
    init {
        require(coverageThreshold in 0.0f..100.0f) {
            "Coverage threshold must be between 0 and 100, got $coverageThreshold"
        }
        require(maxArtifactSize > 0) {
            "Max artifact size must be positive, got $maxArtifactSize"
        }
    }

    /**
     * Performance-specific validation requirements.
     *
     * @property minFps Minimum required frames per second (default: 120.0).
     * @property maxInitTime Maximum allowed initialization time in milliseconds (default: 2000ms).
     * @property maxMemoryUsage Optional maximum memory usage in bytes (null = no limit).
     */
    @Serializable
    data class PerformanceRequirements(
        val minFps: Float = 120.0f,
        val maxInitTime: Long = 2000L,
        val maxMemoryUsage: Long? = null
    ) {
        init {
            require(minFps > 0) {
                "Minimum FPS must be positive, got $minFps"
            }
            require(maxInitTime > 0) {
                "Max init time must be positive, got $maxInitTime"
            }
            maxMemoryUsage?.let {
                require(it > 0) {
                    "Max memory usage must be positive, got $it"
                }
            }
        }

        /**
         * Checks if a given FPS value meets the requirement.
         */
        fun meetsFpsRequirement(fps: Float): Boolean = fps >= minFps

        /**
         * Checks if initialization time meets the requirement.
         */
        fun meetsInitTimeRequirement(initTimeMs: Long): Boolean = initTimeMs <= maxInitTime

        /**
         * Checks if memory usage meets the requirement.
         */
        fun meetsMemoryRequirement(memoryBytes: Long): Boolean =
            maxMemoryUsage?.let { memoryBytes <= it } ?: true

        /**
         * Generates a summary of performance requirements.
         */
        fun generateSummary(): String = buildString {
            appendLine("Performance Requirements:")
            appendLine("  Min FPS: $minFps")
            appendLine("  Max Init Time: ${maxInitTime}ms")
            maxMemoryUsage?.let {
                appendLine("  Max Memory: ${formatBytes(it)}")
            }
        }

        private fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    companion object {
        /**
         * Default category names that are always validated.
         */
        val DEFAULT_CATEGORIES = setOf(
            ValidationCategory.COMPILATION,
            ValidationCategory.TESTING,
            ValidationCategory.PERFORMANCE,
            ValidationCategory.SECURITY,
            ValidationCategory.CONSTITUTIONAL
        )

        /**
         * Creates a strict configuration for production validation.
         * Enforces all constitutional requirements with no compromises.
         */
        fun strict() = ValidationConfiguration(
            enabledCategories = DEFAULT_CATEGORIES + setOf(
                ValidationCategory.DOCUMENTATION,
                ValidationCategory.DEPENDENCIES,
                ValidationCategory.CODE_QUALITY
            ),
            platforms = emptySet(), // Validate all available platforms
            performanceRequirements = PerformanceRequirements(
                minFps = 120.0f,  // Constitutional requirement
                maxInitTime = 2000L,
                maxMemoryUsage = 512_000_000L // 512 MB
            ),
            coverageThreshold = 95.0f,
            maxArtifactSize = 2_097_152L, // 2 MB constitutional limit
            failFast = false, // Run all validations to get complete report
            generateHtmlReport = true
        )

        /**
         * Creates a permissive configuration for development.
         * Allows flexibility during active development.
         */
        fun permissive() = ValidationConfiguration(
            enabledCategories = setOf(
                ValidationCategory.COMPILATION,
                ValidationCategory.TESTING
            ),
            platforms = setOf(Platform.JVM, Platform.JS), // Quick platforms only
            performanceRequirements = PerformanceRequirements(
                minFps = 30.0f, // Lower threshold for development
                maxInitTime = 5000L,
                maxMemoryUsage = null // No memory limit
            ),
            coverageThreshold = 70.0f, // Lower coverage acceptable
            maxArtifactSize = 10_485_760L, // 10 MB for development
            failFast = true, // Stop on first error for faster feedback
            generateHtmlReport = false
        )

        /**
         * Creates an incremental configuration for CI/CD.
         * Validates only changed components for faster builds.
         */
        fun incremental() = ValidationConfiguration(
            enabledCategories = DEFAULT_CATEGORIES,
            platforms = emptySet(), // Will be determined based on changes
            performanceRequirements = PerformanceRequirements(
                minFps = 60.0f, // Moderate requirement
                maxInitTime = 3000L,
                maxMemoryUsage = null
            ),
            coverageThreshold = 80.0f,
            maxArtifactSize = 5_242_880L, // 5 MB
            failFast = false,
            generateHtmlReport = true
        )

        /**
         * Creates a configuration for quick smoke tests.
         */
        fun smoke() = ValidationConfiguration(
            enabledCategories = setOf(ValidationCategory.COMPILATION),
            platforms = setOf(Platform.JVM), // Fastest platform
            performanceRequirements = PerformanceRequirements(
                minFps = 1.0f, // Just check it runs
                maxInitTime = 30000L, // 30 seconds
                maxMemoryUsage = null
            ),
            coverageThreshold = 0.0f, // No coverage requirement
            maxArtifactSize = Long.MAX_VALUE, // No size limit
            failFast = true,
            generateHtmlReport = false,
            outputDirectory = "build/smoke-test"
        )
    }

    /**
     * Determines if a specific category is enabled.
     */
    fun isCategoryEnabled(category: String): Boolean =
        enabledCategories.isEmpty() || category in enabledCategories

    /**
     * Determines if a specific platform should be validated.
     */
    fun shouldValidatePlatform(platform: Platform): Boolean =
        platforms.isEmpty() || platform in platforms

    /**
     * Gets the maximum artifact size in MB for display.
     */
    val maxArtifactSizeMB: Float
        get() = maxArtifactSize / (1024.0f * 1024.0f)

    /**
     * Generates a summary of the configuration.
     */
    fun generateSummary(): String = buildString {
        appendLine("Validation Configuration")
        appendLine("========================")
        appendLine(
            "Categories: ${
                if (enabledCategories.isEmpty()) "All"
                else enabledCategories.joinToString(", ")
            }"
        )
        appendLine(
            "Platforms: ${
                if (platforms.isEmpty()) "All available"
                else platforms.joinToString(", ")
            }"
        )
        appendLine("Coverage Threshold: $coverageThreshold%")
        appendLine("Max Artifact Size: ${maxArtifactSizeMB.format(2)} MB")
        appendLine("Fail Fast: $failFast")
        appendLine("HTML Report: $generateHtmlReport")
        appendLine("Output: $outputDirectory")
        appendLine()
        append(performanceRequirements.generateSummary())
    }

    /**
     * Creates a copy with specific categories enabled.
     */
    fun withCategories(vararg categories: String): ValidationConfiguration =
        copy(enabledCategories = categories.toSet())

    /**
     * Creates a copy with specific platforms enabled.
     */
    fun withPlatforms(vararg platforms: Platform): ValidationConfiguration =
        copy(platforms = platforms.toSet())

    /**
     * Creates a copy with updated performance requirements.
     */
    fun withPerformanceRequirements(
        minFps: Float? = null,
        maxInitTime: Long? = null,
        maxMemoryUsage: Long? = null
    ): ValidationConfiguration = copy(
        performanceRequirements = PerformanceRequirements(
            minFps = minFps ?: performanceRequirements.minFps,
            maxInitTime = maxInitTime ?: performanceRequirements.maxInitTime,
            maxMemoryUsage = maxMemoryUsage ?: performanceRequirements.maxMemoryUsage
        )
    )
}