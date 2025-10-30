package io.materia.validation.model

import kotlinx.serialization.Serializable

/**
 * Represents platform-specific renderer implementation status.
 *
 * Renderer components track the implementation state, capabilities, and performance
 * of Materia's rendering subsystem across different platforms.
 */
@Serializable
data class RendererComponent(
    /**
     * Target platform for this renderer component.
     */
    val platform: Platform,

    /**
     * Renderer component identifier (e.g., "WebGPURenderer", "VulkanRenderer").
     */
    val componentName: String,

    /**
     * Current implementation completion level.
     */
    val implementationStatus: ImplementationStatus,

    /**
     * Supported rendering features.
     */
    val capabilities: List<String>,

    /**
     * Required but unimplemented features.
     */
    val missingFeatures: List<String>,

    /**
     * Measured performance data (null if not yet measured).
     */
    val performanceMetrics: PerformanceData? = null,

    /**
     * Test coverage percentage (0.0-1.0).
     */
    val testCoverage: Float
) {

    /**
     * Validates that this renderer component has valid data.
     * @return SimpleValidationResult indicating success or failure with details
     */
    fun validate(): SimpleValidationResult {
        val errors = mutableListOf<String>()

        if (componentName.isBlank()) {
            errors.add("Component name cannot be blank")
        }

        if (testCoverage < 0.0f || testCoverage > 1.0f) {
            errors.add("Test coverage must be between 0.0 and 1.0, got: $testCoverage")
        }

        // Validate performance metrics if present
        performanceMetrics?.let { metrics ->
            val metricsValidation = metrics.validate()
            if (!metricsValidation.isValid) {
                errors.addAll(metricsValidation.errors.map { "Performance metrics: $it" })
            }
        }

        return if (errors.isEmpty()) {
            SimpleValidationResult.success()
        } else {
            SimpleValidationResult.failure(errors)
        }
    }

    /**
     * Checks if this component is production ready.
     * Production readiness requires:
     * - COMPLETE or NEEDS_TESTING implementation status
     * - Test coverage >= 90%
     * - No critical missing features
     * - Performance metrics meet constitutional requirements
     *
     * @return true if component meets production readiness criteria
     */
    fun isProductionReady(): Boolean {
        val statusReady = implementationStatus == ImplementationStatus.COMPLETE ||
                implementationStatus == ImplementationStatus.NEEDS_TESTING

        val coverageReady = testCoverage >= 0.9f

        val featuresReady = !hasCriticalMissingFeatures()

        val performanceReady = performanceMetrics?.meetsRequirements() ?: false

        return statusReady && coverageReady && featuresReady && performanceReady
    }

    /**
     * Checks if this component has critical missing features.
     * Critical features are core rendering capabilities required for basic functionality.
     *
     * @return true if any critical features are missing
     */
    fun hasCriticalMissingFeatures(): Boolean {
        val criticalFeatures = setOf(
            "basic_rendering",
            "buffer_management",
            "shader_compilation",
            "texture_loading",
            "draw_commands"
        )

        return missingFeatures.any { feature ->
            criticalFeatures.any { critical ->
                feature.lowercase().contains(critical.lowercase())
            }
        }
    }

    /**
     * Gets the completion percentage for this component.
     * Based on implementation status and missing features ratio.
     *
     * @return completion percentage (0.0-1.0)
     */
    fun getCompletionPercentage(): Float {
        val statusWeight = when (implementationStatus) {
            ImplementationStatus.NOT_STARTED -> 0.0f
            ImplementationStatus.IN_PROGRESS -> 0.5f
            ImplementationStatus.COMPLETE -> 1.0f
            ImplementationStatus.NEEDS_TESTING -> 0.9f
        }

        val totalFeatures = capabilities.size + missingFeatures.size
        val featureWeight = if (totalFeatures > 0) {
            capabilities.size.toFloat() / totalFeatures
        } else {
            1.0f
        }

        return (statusWeight * 0.7f) + (featureWeight * 0.3f)
    }

    /**
     * Gets the health score for this component.
     * Combines completion, test coverage, and performance metrics.
     *
     * @return health score (0.0-1.0)
     */
    fun getHealthScore(): Float {
        val completionScore = getCompletionPercentage()
        val coverageScore = testCoverage
        val performanceScore = performanceMetrics?.getScore() ?: 0.5f

        return (completionScore * 0.5f) + (coverageScore * 0.3f) + (performanceScore * 0.2f)
    }

    /**
     * Gets a human-readable description of this component.
     * @return formatted description including platform, status, and health
     */
    fun getDescription(): String {
        val completion = (getCompletionPercentage() * 100).toInt()
        val coverage = (testCoverage * 100).toInt()
        return "$componentName on ${platform.name}: ${implementationStatus.name} ($completion% complete, $coverage% coverage)"
    }

    /**
     * Creates an improvement plan for this component.
     * @return ImprovementPlan with specific actions to enhance the component
     */
    fun createImprovementPlan(): ImprovementPlan {
        val actions = mutableListOf<String>()

        // Address implementation status
        when (implementationStatus) {
            ImplementationStatus.NOT_STARTED -> {
                actions.add("Begin implementation of $componentName for ${platform.name}")
            }

            ImplementationStatus.IN_PROGRESS -> {
                actions.add("Complete remaining implementation work")
            }

            ImplementationStatus.NEEDS_TESTING -> {
                actions.add("Complete testing and validation")
            }

            ImplementationStatus.COMPLETE -> {
                // Already complete, focus on optimization
            }
        }

        // Address missing features
        if (missingFeatures.isNotEmpty()) {
            val criticalMissing = missingFeatures.take(3)
            actions.add("Implement missing features: ${criticalMissing.joinToString(", ")}")
        }

        // Address test coverage
        if (testCoverage < 0.9f) {
            val targetIncrease = ((0.9f - testCoverage) * 100).toInt()
            actions.add("Increase test coverage by $targetIncrease% to reach 90% minimum")
        }

        // Address performance if metrics exist
        performanceMetrics?.let { metrics ->
            if (!metrics.meetsRequirements()) {
                actions.add("Optimize performance to meet constitutional requirements (60 FPS target)")
            }
        } ?: run {
            actions.add("Implement performance measurement and benchmarking")
        }

        return ImprovementPlan(
            component = this,
            actions = actions,
            estimatedEffort = when {
                implementationStatus == ImplementationStatus.NOT_STARTED -> EffortLevel.LARGE
                missingFeatures.size > 5 -> EffortLevel.LARGE
                testCoverage < 0.5f -> EffortLevel.MEDIUM
                else -> EffortLevel.SMALL
            }
        )
    }

    /**
     * Checks if this component supports a specific capability.
     * @param capability the capability to check for
     * @return true if capability is supported
     */
    fun supportsCapability(capability: String): Boolean {
        return capabilities.any { it.equals(capability, ignoreCase = true) }
    }

    /**
     * Gets the list of capabilities that are partially implemented.
     * @return list of capabilities that exist but may be incomplete
     */
    fun getPartialCapabilities(): List<String> {
        return if (implementationStatus == ImplementationStatus.IN_PROGRESS) {
            capabilities.filter { capability ->
                // Heuristic: if we have missing features that relate to this capability
                missingFeatures.any { missing ->
                    missing.lowercase().contains(capability.lowercase()) ||
                            capability.lowercase().contains(missing.lowercase())
                }
            }
        } else {
            emptyList()
        }
    }
}

/**
 * Implementation status levels for renderer components.
 */
@Serializable
enum class ImplementationStatus {
    /** No implementation exists */
    NOT_STARTED,

    /** Partial implementation */
    IN_PROGRESS,

    /** Full implementation */
    COMPLETE,

    /** Implementation complete, testing required */
    NEEDS_TESTING
}

/**
 * Performance data for renderer components.
 */
@Serializable
data class PerformanceData(
    /**
     * Frames per second measurement.
     */
    val fps: Float,

    /**
     * Frame time in milliseconds.
     */
    val frameTimeMs: Float,

    /**
     * GPU memory usage in MB.
     */
    val gpuMemoryMB: Float,

    /**
     * CPU memory usage in MB.
     */
    val cpuMemoryMB: Float,

    /**
     * Number of draw calls per frame.
     */
    val drawCalls: Int,

    /**
     * Triangle count per frame.
     */
    val triangles: Int
) {

    /**
     * Validates performance data values.
     * @return SimpleValidationResult indicating if data is valid
     */
    fun validate(): SimpleValidationResult {
        val errors = mutableListOf<String>()

        if (fps <= 0) {
            errors.add("FPS must be positive, got: $fps")
        }

        if (frameTimeMs <= 0) {
            errors.add("Frame time must be positive, got: $frameTimeMs")
        }

        if (gpuMemoryMB < 0) {
            errors.add("GPU memory cannot be negative, got: $gpuMemoryMB")
        }

        if (cpuMemoryMB < 0) {
            errors.add("CPU memory cannot be negative, got: $cpuMemoryMB")
        }

        if (drawCalls < 0) {
            errors.add("Draw calls cannot be negative, got: $drawCalls")
        }

        if (triangles < 0) {
            errors.add("Triangle count cannot be negative, got: $triangles")
        }

        return if (errors.isEmpty()) {
            SimpleValidationResult.success()
        } else {
            SimpleValidationResult.failure(errors)
        }
    }

    /**
     * Checks if performance meets constitutional requirements.
     * Materia constitutional target: 60 FPS with 100k triangles.
     *
     * @return true if performance meets minimum requirements
     */
    fun meetsRequirements(): Boolean {
        return fps >= 60.0f && triangles >= 100_000
    }

    /**
     * Gets a normalized performance score (0.0-1.0).
     * Based on FPS relative to 60 FPS target.
     *
     * @return performance score
     */
    fun getScore(): Float {
        val fpsScore = (fps / 60.0f).coerceAtMost(1.0f)
        val triangleScore = (triangles / 100_000.0f).coerceAtMost(1.0f)
        return (fpsScore + triangleScore) / 2.0f
    }

    /**
     * Gets efficiency rating based on triangles per draw call.
     * @return efficiency score (higher is better)
     */
    fun getEfficiencyRating(): Float {
        return if (drawCalls > 0) {
            triangles.toFloat() / drawCalls
        } else {
            0.0f
        }
    }
}

/**
 * Improvement plan for a renderer component.
 */
@Serializable
data class ImprovementPlan(
    val component: RendererComponent,
    val actions: List<String>,
    val estimatedEffort: EffortLevel
) {
    /**
     * Gets the estimated timeline in hours.
     * @return estimated hours to complete improvements
     */
    fun getEstimatedHours(): Int {
        return when (estimatedEffort) {
            EffortLevel.TRIVIAL -> 1
            EffortLevel.SMALL -> 4
            EffortLevel.MEDIUM -> 16
            EffortLevel.LARGE -> 64
        }
    }

    /**
     * Checks if this is a high-priority improvement.
     * @return true if component is not production ready
     */
    fun isHighPriority(): Boolean = !component.isProductionReady()
}