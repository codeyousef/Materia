package io.materia.validation.models

import io.materia.validation.Platform
import kotlinx.serialization.Serializable

/**
 * Represents the size metrics for a platform-specific build artifact.
 *
 * Tracks artifact sizes to ensure compliance with the constitutional 5MB size limit
 * and provides optimization suggestions when the limit is exceeded.
 */
@Serializable
data class BuildArtifactSize(
    /**
     * Target platform for this artifact.
     */
    val platform: Platform,

    /**
     * Path to the generated artifact file.
     */
    val artifactPath: String,

    /**
     * Size of the artifact in bytes.
     */
    val sizeBytes: Long,

    /**
     * Human-readable formatted size string (e.g., "2.3 MB").
     */
    val sizeFormatted: String,

    /**
     * Whether artifact size meets the 5MB constitutional requirement.
     */
    val meetsRequirement: Boolean,

    /**
     * Optimization suggestions to reduce artifact size.
     */
    val optimizationSuggestions: List<String> = emptyList()
) {

    /**
     * Gets size in megabytes.
     * @return size in MB
     */
    fun getSizeMB(): Double {
        return sizeBytes / (1024.0 * 1024.0)
    }

    /**
     * Gets size in kilobytes.
     * @return size in KB
     */
    fun getSizeKB(): Double {
        return sizeBytes / 1024.0
    }

    /**
     * Calculates how much over/under the 5MB limit this artifact is.
     * @return difference in bytes (negative if under limit)
     */
    fun getSizeDifference(): Long {
        val limitBytes = 5L * 1024 * 1024 // 5MB in bytes
        return sizeBytes - limitBytes
    }

    /**
     * Gets percentage of the 5MB limit used.
     * @return percentage (e.g., 120.5 means 20.5% over limit)
     */
    fun getPercentageOfLimit(): Double {
        val limitBytes = 5L * 1024 * 1024
        return (sizeBytes.toDouble() / limitBytes) * 100
    }

    /**
     * Generates a status summary for this artifact.
     * @return human-readable status string
     */
    fun getStatusSummary(): String {
        val percentage = getPercentageOfLimit()
        val status = when {
            meetsRequirement -> "✅ Within limit"
            percentage < 120 -> "⚠️ Slightly over limit"
            percentage < 150 -> "⚠️ Significantly over limit"
            else -> "❌ Far exceeds limit"
        }

        return "$platform artifact: $sizeFormatted $status (${percentage.format(1)}% of 5MB limit)"
    }

    /**
     * Gets prioritized optimization suggestions based on size excess.
     * @return list of suggestions ordered by potential impact
     */
    fun getPrioritizedSuggestions(): List<OptimizationSuggestion> {
        val sizeMB = getSizeMB()
        val suggestions = mutableListOf<OptimizationSuggestion>()

        if (sizeMB > 5) {
            val excessMB = sizeMB - 5

            // High impact suggestions for significant overages
            if (excessMB > 2) {
                suggestions.add(
                    OptimizationSuggestion(
                        action = "Enable ProGuard/R8 minification",
                        impact = OptimizationImpact.HIGH,
                        estimatedReduction = "30-50%",
                        complexity = "Medium"
                    )
                )

                suggestions.add(
                    OptimizationSuggestion(
                        action = "Remove unused dependencies",
                        impact = OptimizationImpact.HIGH,
                        estimatedReduction = "10-30%",
                        complexity = "Low"
                    )
                )
            }

            // Medium impact suggestions
            suggestions.add(
                OptimizationSuggestion(
                    action = "Enable tree shaking for unused code",
                    impact = OptimizationImpact.MEDIUM,
                    estimatedReduction = "10-20%",
                    complexity = "Low"
                )
            )

            suggestions.add(
                OptimizationSuggestion(
                    action = "Optimize resource compression",
                    impact = OptimizationImpact.MEDIUM,
                    estimatedReduction = "5-15%",
                    complexity = "Low"
                )
            )

            // Platform-specific suggestions
            when (platform) {
                Platform.JS -> {
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Use webpack production mode with optimization",
                            impact = OptimizationImpact.HIGH,
                            estimatedReduction = "20-40%",
                            complexity = "Low"
                        )
                    )
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Enable gzip/brotli compression",
                            impact = OptimizationImpact.MEDIUM,
                            estimatedReduction = "60-70% (transfer size)",
                            complexity = "Low"
                        )
                    )
                }

                Platform.JVM -> {
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Use jlink to create custom runtime image",
                            impact = OptimizationImpact.HIGH,
                            estimatedReduction = "30-50%",
                            complexity = "Medium"
                        )
                    )
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Exclude unnecessary JVM modules",
                            impact = OptimizationImpact.MEDIUM,
                            estimatedReduction = "10-20%",
                            complexity = "Medium"
                        )
                    )
                }

                Platform.ANDROID -> {
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Enable App Bundle with dynamic delivery",
                            impact = OptimizationImpact.HIGH,
                            estimatedReduction = "20-35%",
                            complexity = "Medium"
                        )
                    )
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Use vector drawables instead of raster images",
                            impact = OptimizationImpact.MEDIUM,
                            estimatedReduction = "5-15%",
                            complexity = "Low"
                        )
                    )
                }

                Platform.IOS -> {
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Enable Swift optimization and stripping",
                            impact = OptimizationImpact.HIGH,
                            estimatedReduction = "15-25%",
                            complexity = "Low"
                        )
                    )
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Use asset catalogs with app thinning",
                            impact = OptimizationImpact.MEDIUM,
                            estimatedReduction = "10-20%",
                            complexity = "Low"
                        )
                    )
                }

                Platform.NATIVE -> {
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Enable link-time optimization (LTO)",
                            impact = OptimizationImpact.HIGH,
                            estimatedReduction = "10-20%",
                            complexity = "Low"
                        )
                    )
                    suggestions.add(
                        OptimizationSuggestion(
                            action = "Strip debug symbols for release builds",
                            impact = OptimizationImpact.MEDIUM,
                            estimatedReduction = "20-30%",
                            complexity = "Low"
                        )
                    )
                }

                else -> {
                    // Generic suggestions already added above
                }
            }

            // Low impact but easy suggestions
            suggestions.add(
                OptimizationSuggestion(
                    action = "Remove development-only code and logging",
                    impact = OptimizationImpact.LOW,
                    estimatedReduction = "1-5%",
                    complexity = "Low"
                )
            )
        }

        return suggestions.sortedByDescending { it.impact.priority }
    }

    /**
     * Estimates time to implement all optimization suggestions.
     * @return estimated hours
     */
    fun estimateOptimizationEffort(): Int {
        val suggestions = getPrioritizedSuggestions()
        return suggestions.sumOf { suggestion ->
            when (suggestion.complexity) {
                "Low" -> 1
                "Medium" -> 4
                "High" -> 8
                else -> 2
            }
        }
    }
}

/**
 * Optimization suggestion for reducing artifact size.
 */
@Serializable
data class OptimizationSuggestion(
    /**
     * Action to take.
     */
    val action: String,

    /**
     * Expected impact level.
     */
    val impact: OptimizationImpact,

    /**
     * Estimated size reduction.
     */
    val estimatedReduction: String,

    /**
     * Implementation complexity.
     */
    val complexity: String
)

/**
 * Impact level of optimization suggestions.
 */
@Serializable
enum class OptimizationImpact(val priority: Int) {
    /** Major size reduction expected */
    HIGH(3),

    /** Moderate size reduction expected */
    MEDIUM(2),

    /** Minor size reduction expected */
    LOW(1)
}

/**
 * Extension function to format double values.
 * Multiplatform-compatible implementation without String.format().
 */
private fun Double.format(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> {
            var m = 1.0
            repeat(decimals) { m *= 10.0 }
            m
        }
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier

    // Convert to string and ensure proper decimal places
    val str = rounded.toString()
    val parts = str.split('.')
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else ""

    return if (decimals == 0) {
        intPart
    } else {
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        "$intPart.$paddedDec"
    }
}