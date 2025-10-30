package io.materia.validation.api

import io.materia.validation.models.*

/**
 * Generates actionable recommendations from validation reports.
 */
internal class RecommendationEngine {

    fun generateRecommendations(report: ProductionReadinessReport): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        // Generate recommendations from failed categories
        report.categories.forEach { category ->
            if (category.status == ValidationStatus.FAILED) {
                category.failedCriteria.forEach { criterion ->
                    recommendations.add(
                        Recommendation(
                            priority = when (criterion.severity) {
                                Severity.CRITICAL -> Priority.CRITICAL
                                Severity.HIGH -> Priority.HIGH
                                Severity.MEDIUM -> Priority.MEDIUM
                                Severity.LOW -> Priority.LOW
                            },
                            category = category.name,
                            issue = criterion.description,
                            action = "Fix ${criterion.name}",
                            estimatedEffort = EffortLevel.MEDIUM
                        )
                    )
                }
            }
        }

        // Generate recommendations from remediation actions
        report.remediationActions.forEach { action ->
            recommendations.add(
                Recommendation(
                    priority = when (action.priority) {
                        1 -> Priority.CRITICAL
                        2 -> Priority.HIGH
                        3 -> Priority.MEDIUM
                        else -> Priority.LOW
                    },
                    category = action.criterionId,
                    issue = action.description,
                    action = action.title,
                    estimatedEffort = estimateEffortFromTime(action.estimatedEffort)
                )
            )
        }

        // Sort by priority
        return recommendations.sortedBy { it.priority.ordinal }
    }

    private fun estimateEffortFromTime(timeStr: String): EffortLevel {
        return when {
            "hour" in timeStr.lowercase() -> {
                val hours = timeStr.filter { it.isDigit() }.toIntOrNull() ?: 1
                if (hours <= 1) EffortLevel.LOW else if (hours <= 4) EffortLevel.MEDIUM else EffortLevel.HIGH
            }

            "day" in timeStr.lowercase() || "week" in timeStr.lowercase() -> EffortLevel.HIGH
            "minute" in timeStr.lowercase() -> EffortLevel.LOW
            else -> EffortLevel.MEDIUM
        }
    }
}

/**
 * Recommendation for improving production readiness.
 */
data class Recommendation(
    val priority: Priority,
    val category: String,
    val issue: String,
    val action: String,
    val estimatedEffort: EffortLevel
)

enum class Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
}

enum class EffortLevel {
    LOW,
    MEDIUM,
    HIGH
}
