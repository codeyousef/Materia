package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * Actionable step to fix a validation failure.
 *
 * Provides detailed guidance on how to resolve issues identified during validation.
 * Each action is linked to a specific criterion that failed and includes step-by-step
 * instructions, effort estimates, and optional automation commands.
 *
 * @property criterionId The ID of the validation criterion that triggered this action.
 * @property title Brief, actionable title of what needs to be done.
 * @property description Detailed explanation of the issue and why it needs fixing.
 * @property steps Ordered list of specific steps to resolve the issue.
 * @property estimatedEffort Human-readable effort estimate (e.g., "5 minutes", "1 hour", "2 days").
 * @property priority Priority level where 1 is highest priority.
 * @property automatable Whether this action can be automated.
 * @property command Optional shell command that can automatically fix the issue.
 */
@Serializable
data class RemediationAction(
    val criterionId: String,
    val title: String,
    val description: String,
    val steps: List<String>,
    val estimatedEffort: String,
    val priority: Int,
    val automatable: Boolean,
    val command: String? = null
) {
    init {
        require(priority > 0) {
            "Priority must be positive, got $priority"
        }
        require(steps.isNotEmpty()) {
            "Remediation action must have at least one step"
        }
        if (automatable) {
            require(command != null) {
                "Automatable actions must provide a command"
            }
        }
    }

    /**
     * Priority levels for remediation actions.
     */
    companion object {
        const val PRIORITY_CRITICAL = 1
        const val PRIORITY_HIGH = 2
        const val PRIORITY_MEDIUM = 3
        const val PRIORITY_LOW = 4

        /**
         * Common effort estimates.
         */
        object EffortEstimates {
            const val MINUTES_5 = "5 minutes"
            const val MINUTES_15 = "15 minutes"
            const val MINUTES_30 = "30 minutes"
            const val HOUR_1 = "1 hour"
            const val HOURS_2 = "2 hours"
            const val HOURS_4 = "4 hours"
            const val DAY_1 = "1 day"
            const val DAYS_2 = "2 days"
            const val WEEK_1 = "1 week"
        }

        /**
         * Creates a critical priority action.
         */
        fun critical(
            criterionId: String,
            title: String,
            description: String,
            steps: List<String>,
            estimatedEffort: String = EffortEstimates.MINUTES_30,
            automatable: Boolean = false,
            command: String? = null
        ) = RemediationAction(
            criterionId = criterionId,
            title = title,
            description = description,
            steps = steps,
            estimatedEffort = estimatedEffort,
            priority = PRIORITY_CRITICAL,
            automatable = automatable,
            command = command
        )

        /**
         * Creates an automated fix action.
         */
        fun automated(
            criterionId: String,
            title: String,
            description: String,
            command: String,
            priority: Int = PRIORITY_MEDIUM
        ) = RemediationAction(
            criterionId = criterionId,
            title = title,
            description = description,
            steps = listOf("Run command: $command"),
            estimatedEffort = EffortEstimates.MINUTES_5,
            priority = priority,
            automatable = true,
            command = command
        )
    }

    /**
     * Determines if this is a high-priority action (priority 1 or 2).
     */
    val isHighPriority: Boolean
        get() = priority <= PRIORITY_HIGH

    /**
     * Determines if this is a quick fix (15 minutes or less).
     */
    val isQuickFix: Boolean
        get() = estimatedEffort in listOf(
            EffortEstimates.MINUTES_5,
            EffortEstimates.MINUTES_15
        )

    /**
     * Gets the priority as a human-readable string.
     */
    val priorityLabel: String
        get() = when (priority) {
            PRIORITY_CRITICAL -> "Critical"
            PRIORITY_HIGH -> "High"
            PRIORITY_MEDIUM -> "Medium"
            PRIORITY_LOW -> "Low"
            else -> "Priority $priority"
        }

    /**
     * Generates a concise summary of this action.
     */
    fun generateSummary(): String = buildString {
        append("[$priorityLabel] $title")
        if (automatable) {
            append(" [Automatable]")
        }
        append(" (~$estimatedEffort)")
    }

    /**
     * Generates detailed instructions for this remediation.
     */
    fun generateInstructions(): String = buildString {
        appendLine("Remediation: $title")
        appendLine("Priority: $priorityLabel")
        appendLine("Estimated Effort: $estimatedEffort")
        appendLine()
        appendLine("Description:")
        appendLine(description)
        appendLine()
        appendLine("Steps:")
        steps.forEachIndexed { index, step ->
            appendLine("  ${index + 1}. $step")
        }
        if (automatable && command != null) {
            appendLine()
            appendLine("Automated Fix:")
            appendLine("  $command")
        }
    }

    /**
     * Generates a shell script snippet for automated fixes.
     */
    fun generateScript(): String? = if (automatable && command != null) {
        """
        |#!/bin/bash
        |# Remediation: $title
        |# Priority: $priorityLabel
        |# Criterion: $criterionId
        |
        |echo "Applying fix: $title"
        |$command
        |
        |if [ $? -eq 0 ]; then
        |    echo "✓ Fix applied successfully"
        |else
        |    echo "✗ Fix failed, manual intervention required"
        |    exit 1
        |fi
        """.trimMargin()
    } else null

    /**
     * Creates a copy with updated priority.
     */
    fun withPriority(newPriority: Int): RemediationAction =
        copy(priority = newPriority)

    /**
     * Creates a copy with automation enabled.
     */
    fun withAutomation(command: String): RemediationAction =
        copy(automatable = true, command = command)
}