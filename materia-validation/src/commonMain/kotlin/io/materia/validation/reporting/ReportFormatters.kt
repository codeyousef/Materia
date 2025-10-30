package io.materia.validation.reporting

import kotlin.math.roundToInt

/**
 * Utility formatters for report generation.
 */
internal class ReportFormatters {

    fun formatTimestamp(timestamp: Long): String {
        // Simple formatting - in real implementation would use proper date formatting
        return "Timestamp: $timestamp"
    }

    fun formatPercentage(score: Float): String {
        return "${(score * 100).roundToInt()}%"
    }

    fun getScoreStatus(score: Float): String {
        return when {
            score >= 0.9f -> "excellent"
            score >= 0.7f -> "good"
            score >= 0.5f -> "warning"
            else -> "poor"
        }
    }

    fun getStatusEmoji(status: String): String {
        return when (status) {
            "excellent" -> "🟢"
            "good" -> "🔵"
            "warning" -> "🟡"
            "poor" -> "🔴"
            else -> "⚪"
        }
    }

    fun generateProgressBar(value: Float, width: Int): String {
        val filled = (value * width).roundToInt()
        val empty = width - filled
        return "[${"█".repeat(filled)}${"░".repeat(empty)}]"
    }

    fun formatMetadataKey(key: String): String {
        // Split camelCase to separate words
        return key.replace(Regex("([A-Z])"), " $1")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
    }
}
