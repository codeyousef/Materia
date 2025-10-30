package io.materia.validation.model

import kotlinx.serialization.Serializable

/**
 * Represents overall production readiness validation outcome.
 *
 * This is the top-level result that aggregates all validation checks including
 * placeholder detection, implementation gap analysis, and renderer audits.
 */
@Serializable
data class ProductionReadinessResult(
    /**
     * When validation was performed (epoch milliseconds).
     */
    val timestamp: Long,

    /**
     * Pass/Fail/Warning status.
     */
    val overallStatus: ValidationStatus,

    /**
     * Total placeholders detected across the codebase.
     */
    val placeholderCount: Int,

    /**
     * Total implementation gaps found.
     */
    val gapCount: Int,

    /**
     * Percentage of tests passing (0.0-1.0).
     */
    val testSuccessRate: Float,

    /**
     * Measured performance data.
     */
    val performanceMetrics: PerformanceData,

    /**
     * Suggested improvements.
     */
    val recommendations: List<String>,

    /**
     * Constitutional compliance score (0.0-1.0).
     */
    val complianceLevel: Float,

    /**
     * Detected placeholder instances.
     */
    val placeholders: List<PlaceholderInstance> = emptyList(),

    /**
     * Implementation gaps found.
     */
    val gaps: List<ImplementationGap> = emptyList(),

    /**
     * Renderer component status.
     */
    val rendererComponents: List<RendererComponent> = emptyList()
) {

    /**
     * Validates that this validation result has consistent data.
     * @return simple ValidationResult indicating success or failure
     */
    fun validate(): SimpleValidationResult {
        val errors = mutableListOf<String>()

        if (placeholderCount < 0) {
            errors.add("Placeholder count cannot be negative, got: $placeholderCount")
        }

        if (gapCount < 0) {
            errors.add("Gap count cannot be negative, got: $gapCount")
        }

        if (testSuccessRate < 0.0f || testSuccessRate > 1.0f) {
            errors.add("Test success rate must be between 0.0 and 1.0, got: $testSuccessRate")
        }

        if (complianceLevel < 0.0f || complianceLevel > 1.0f) {
            errors.add("Compliance level must be between 0.0 and 1.0, got: $complianceLevel")
        }

        // Validate consistency between counts and actual collections
        if (placeholderCount != placeholders.size) {
            errors.add("Placeholder count ($placeholderCount) doesn't match placeholders list size (${placeholders.size})")
        }

        if (gapCount != gaps.size) {
            errors.add("Gap count ($gapCount) doesn't match gaps list size (${gaps.size})")
        }

        // Validate performance metrics
        val metricsValidation = performanceMetrics.validate()
        if (!metricsValidation.isValid) {
            errors.addAll(metricsValidation.errors.map { "Performance metrics: $it" })
        }

        // Validate all placeholders
        placeholders.forEach { placeholder ->
            val placeholderValidation = placeholder.validate()
            if (!placeholderValidation.isValid) {
                errors.addAll(placeholderValidation.errors.map { "Placeholder ${placeholder.filePath}:${placeholder.lineNumber}: $it" })
            }
        }

        // Validate all gaps
        gaps.forEach { gap ->
            val gapValidation = gap.validate()
            if (!gapValidation.isValid) {
                errors.addAll(gapValidation.errors.map { "Gap ${gap.expectDeclaration}: $it" })
            }
        }

        // Validate all renderer components
        rendererComponents.forEach { component ->
            val componentValidation = component.validate()
            if (!componentValidation.isValid) {
                errors.addAll(componentValidation.errors.map { "Component ${component.componentName}: $it" })
            }
        }

        return if (errors.isEmpty()) {
            SimpleValidationResult.success()
        } else {
            SimpleValidationResult.failure(errors)
        }
    }

    /**
     * Checks if the codebase is ready for production deployment.
     * Production readiness requires:
     * - No critical placeholders
     * - No missing implementations for core platforms
     * - Test success rate >= 95%
     * - Constitutional compliance >= 90%
     * - All renderer components production ready
     *
     * @return true if ready for production deployment
     */
    fun isProductionReady(): Boolean {
        val noCriticalPlaceholders = !hasCriticalPlaceholders()
        val noCorePlatformGaps = !hasCorePlatformGaps()
        val highTestSuccessRate = testSuccessRate >= 0.95f
        val highCompliance = complianceLevel >= 0.9f
        val renderersReady = rendererComponents.all { it.isProductionReady() }

        return noCriticalPlaceholders &&
                noCorePlatformGaps &&
                highTestSuccessRate &&
                highCompliance &&
                renderersReady
    }

    /**
     * Checks if there are critical placeholders that block production.
     * @return true if any placeholders have CRITICAL or HIGH criticality
     */
    fun hasCriticalPlaceholders(): Boolean {
        return placeholders.any { it.blocksProduction() }
    }

    /**
     * Checks if there are missing implementations for core platforms.
     * Core platforms are JVM and JAVASCRIPT.
     *
     * @return true if any gaps affect core platforms
     */
    fun hasCorePlatformGaps(): Boolean {
        return gaps.any { gap ->
            gap.getCriticalPlatforms().isNotEmpty()
        }
    }

    /**
     * Gets the overall health score for the codebase.
     * Combines multiple factors into a single score.
     *
     * @return health score (0.0-1.0)
     */
    fun getHealthScore(): Float {
        val placeholderScore = if (placeholderCount == 0) 1.0f else {
            val criticalCount = placeholders.count { it.blocksProduction() }
            (1.0f - (criticalCount.toFloat() / placeholderCount)).coerceAtLeast(0.0f)
        }

        val gapScore = if (gapCount == 0) 1.0f else {
            val criticalGaps = gaps.count { it.isCritical() }
            (1.0f - (criticalGaps.toFloat() / gapCount)).coerceAtLeast(0.0f)
        }

        val rendererScore = if (rendererComponents.isEmpty()) 0.5f else {
            rendererComponents.map { it.getHealthScore() }.average().toFloat()
        }

        return (placeholderScore * 0.25f) +
                (gapScore * 0.25f) +
                (testSuccessRate * 0.2f) +
                (complianceLevel * 0.15f) +
                (rendererScore * 0.15f)
    }

    /**
     * Gets a summary of critical issues that need immediate attention.
     * @return list of critical issue descriptions
     */
    fun getCriticalIssues(): List<String> {
        val issues = mutableListOf<String>()

        // Critical placeholders
        val criticalPlaceholders = placeholders.filter { it.blocksProduction() }
        if (criticalPlaceholders.isNotEmpty()) {
            issues.add("${criticalPlaceholders.size} critical placeholders block production")
        }

        // Core platform gaps
        val corePlatformGaps = gaps.filter { it.getCriticalPlatforms().isNotEmpty() }
        if (corePlatformGaps.isNotEmpty()) {
            issues.add("${corePlatformGaps.size} implementation gaps affect core platforms")
        }

        // Low test success rate
        if (testSuccessRate < 0.95f) {
            val failureRate = ((1.0f - testSuccessRate) * 100).toInt()
            issues.add("$failureRate% test failure rate (target: <5%)")
        }

        // Low constitutional compliance
        if (complianceLevel < 0.9f) {
            val compliancePercent = (complianceLevel * 100).toInt()
            issues.add("$compliancePercent% constitutional compliance (target: ≥90%)")
        }

        // Non-production-ready renderers
        val notReadyRenderers = rendererComponents.filter { !it.isProductionReady() }
        if (notReadyRenderers.isNotEmpty()) {
            issues.add("${notReadyRenderers.size} renderer components not production ready")
        }

        return issues
    }

    /**
     * Creates a comprehensive improvement plan.
     * @return ImprovementPlan with prioritized actions
     */
    fun createImprovementPlan(): ComprehensiveImprovementPlan {
        val actions = mutableListOf<PrioritizedAction>()

        // High priority: Critical placeholders
        placeholders.filter { it.blocksProduction() }.forEach { placeholder ->
            actions.add(
                PrioritizedAction(
                    description = "Replace critical placeholder: ${placeholder.getDescription()}",
                    priority = placeholder.getPriorityScore(),
                    estimatedEffort = when (placeholder.criticality) {
                        CriticalityLevel.CRITICAL -> EffortLevel.LARGE
                        CriticalityLevel.HIGH -> EffortLevel.MEDIUM
                        else -> EffortLevel.SMALL
                    },
                    category = "Placeholders"
                )
            )
        }

        // High priority: Core platform gaps
        gaps.filter { it.getCriticalPlatforms().isNotEmpty() }.forEach { gap ->
            actions.add(
                PrioritizedAction(
                    description = "Implement missing functionality: ${gap.getDescription()}",
                    priority = gap.getPriorityScore(),
                    estimatedEffort = gap.estimatedEffort,
                    category = "Implementation Gaps"
                )
            )
        }

        // Medium priority: Renderer improvements
        rendererComponents.filter { !it.isProductionReady() }.forEach { component ->
            val plan = component.createImprovementPlan()
            actions.add(
                PrioritizedAction(
                    description = "Improve renderer: ${component.getDescription()}",
                    priority = if (component.isProductionReady()) 30 else 70,
                    estimatedEffort = plan.estimatedEffort,
                    category = "Renderer Components"
                )
            )
        }

        // Sort by priority (highest first)
        actions.sortByDescending { it.priority }

        return ComprehensiveImprovementPlan(
            validationResult = this,
            actions = actions,
            estimatedTotalEffort = calculateTotalEffort(actions)
        )
    }

    /**
     * Gets a breakdown of issues by module.
     * @return map of module names to issue counts
     */
    fun getIssuesByModule(): Map<String, Int> {
        val moduleIssues = mutableMapOf<String, Int>()

        placeholders.forEach { placeholder ->
            moduleIssues[placeholder.module] = (moduleIssues[placeholder.module] ?: 0) + 1
        }

        return moduleIssues
    }

    /**
     * Gets platform-specific statistics.
     * @return map of platform names to their status summaries
     */
    fun getPlatformStatistics(): Map<String, PlatformStatus> {
        val platformStats = mutableMapOf<String, PlatformStatus>()

        Platform.values().forEach { platform ->
            val platformName = platform.name
            val gaps = gaps.count { it.platforms.contains(platformName) }
            val rendererComponent = rendererComponents.find { it.platform == platform }

            platformStats[platformName] = PlatformStatus(
                platform = platform,
                implementationGaps = gaps,
                rendererStatus = rendererComponent?.implementationStatus
                    ?: ImplementationStatus.NOT_STARTED,
                isProductionReady = gaps == 0 && (rendererComponent?.isProductionReady() ?: false)
            )
        }

        return platformStats
    }

    private fun calculateTotalEffort(actions: List<PrioritizedAction>): EffortLevel {
        val totalHours = actions.sumOf { action ->
            when (action.estimatedEffort) {
                EffortLevel.TRIVIAL -> 1
                EffortLevel.SMALL -> 4
                EffortLevel.MEDIUM -> 16
                EffortLevel.LARGE -> 64
            }
        }

        return when {
            totalHours <= 1 -> EffortLevel.TRIVIAL
            totalHours <= 4 -> EffortLevel.SMALL
            totalHours <= 16 -> EffortLevel.MEDIUM
            else -> EffortLevel.LARGE
        }
    }
}

/**
 * Overall validation status.
 */
@Serializable
enum class ValidationStatus {
    /** All checks successful */
    PASS,

    /** Critical issues found */
    FAIL,

    /** Non-critical issues found */
    WARNING
}


/**
 * Prioritized action for improvement plans.
 */
@Serializable
data class PrioritizedAction(
    val description: String,
    val priority: Int,
    val estimatedEffort: EffortLevel,
    val category: String
)

/**
 * Comprehensive improvement plan for the entire codebase.
 */
@Serializable
data class ComprehensiveImprovementPlan(
    val validationResult: ProductionReadinessResult,
    val actions: List<PrioritizedAction>,
    val estimatedTotalEffort: EffortLevel
) {
    /**
     * Gets actions in a specific category.
     * @param category the category to filter by
     * @return list of actions in the specified category
     */
    fun getActionsByCategory(category: String): List<PrioritizedAction> {
        return actions.filter { it.category == category }
    }

    /**
     * Gets the estimated timeline in days for all improvements.
     * @return estimated days to complete all actions
     */
    fun getEstimatedDays(): Double {
        val totalHours = actions.sumOf { action ->
            when (action.estimatedEffort) {
                EffortLevel.TRIVIAL -> 1
                EffortLevel.SMALL -> 4
                EffortLevel.MEDIUM -> 16
                EffortLevel.LARGE -> 64
            }
        }
        return totalHours / 8.0 // 8 hours per day
    }

    /**
     * Gets the top priority actions.
     * @param count number of top actions to return
     * @return list of highest priority actions
     */
    fun getTopPriorityActions(count: Int): List<PrioritizedAction> {
        return actions.take(count)
    }
}

/**
 * Platform-specific status summary.
 */
@Serializable
data class PlatformStatus(
    val platform: Platform,
    val implementationGaps: Int,
    val rendererStatus: ImplementationStatus,
    val isProductionReady: Boolean
) {
    /**
     * Gets a human-readable status description.
     * @return formatted status string
     */
    fun getStatusDescription(): String {
        val gapText = if (implementationGaps > 0) " ($implementationGaps gaps)" else ""
        val readyText = if (isProductionReady) "Production Ready" else "Not Ready"
        return "${platform.name}: ${rendererStatus.name}$gapText - $readyText"
    }
}