package io.materia.validation.utils

/**
 * Multiplatform extension functions for formatting numbers.
 *
 * These provide consistent formatting across JVM, JS, and Native platforms.
 */

/**
 * Formats a Float to a string with the specified number of decimal places.
 *
 * @param decimals Number of decimal places (default: 2)
 * @return Formatted string
 */
fun Float.format(decimals: Int = 2): String {
    // Simple rounding and formatting
    val multiplier = when (decimals) {
        0 -> 1
        1 -> 10
        2 -> 100
        3 -> 1000
        else -> 10000
    }

    val rounded = (this * multiplier).toInt().toFloat() / multiplier
    return when (decimals) {
        0 -> rounded.toInt().toString()
        1 -> {
            val intPart = rounded.toInt()
            val fracPart = ((rounded - intPart) * 10).toInt()
            "$intPart.$fracPart"
        }

        2 -> {
            val intPart = rounded.toInt()
            val fracPart = ((rounded - intPart) * 100).toInt()
            val fracStr = fracPart.toString().padStart(2, '0')
            "$intPart.$fracStr"
        }

        else -> {
            // For higher precision, use a more general approach
            val intPart = rounded.toInt()
            var fracPart = ((rounded - intPart) * multiplier).toInt()
            val fracStr = fracPart.toString().padStart(decimals, '0')
            "$intPart.$fracStr"
        }
    }
}

/**
 * Formats a Double to a string with the specified number of decimal places.
 *
 * @param decimals Number of decimal places (default: 2)
 * @return Formatted string
 */
fun Double.format(decimals: Int = 2): String {
    return this.toFloat().format(decimals)
}