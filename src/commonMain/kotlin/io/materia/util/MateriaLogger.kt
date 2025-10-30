package io.materia.util

/**
 * Centralized logging system for Materia library.
 * Provides configurable log levels and consistent formatting across all modules.
 */
object MateriaLogger {

    enum class LogLevel(val priority: Int) {
        DEBUG(0),
        INFO(1),
        WARN(2),
        ERROR(3),
        NONE(4)
    }

    var level: LogLevel = LogLevel.INFO
    var enabled: Boolean = true

    // Optional callback for custom log handling
    var customLogHandler: ((level: LogLevel, tag: String, message: String, throwable: Throwable?) -> Unit)? =
        null

    fun debug(tag: String, message: String) {
        if (enabled && level.priority <= LogLevel.DEBUG.priority) {
            log(LogLevel.DEBUG, tag, message, null)
        }
    }

    fun info(tag: String, message: String) {
        if (enabled && level.priority <= LogLevel.INFO.priority) {
            log(LogLevel.INFO, tag, message, null)
        }
    }

    fun warn(tag: String, message: String) {
        if (enabled && level.priority <= LogLevel.WARN.priority) {
            log(LogLevel.WARN, tag, message, null)
        }
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        if (enabled && level.priority <= LogLevel.ERROR.priority) {
            log(LogLevel.ERROR, tag, message, throwable)
        }
    }

    fun verbose(tag: String, message: String) {
        debug(tag, message) // Verbose is treated as debug
    }

    fun trace(tag: String, message: String) {
        debug(tag, message) // Trace is treated as debug
    }

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        // First try custom handler
        customLogHandler?.invoke(level, tag, message, throwable) ?: run {
            // Default implementation using println
            val levelStr = when (level) {
                LogLevel.DEBUG -> "DEBUG"
                LogLevel.INFO -> "INFO"
                LogLevel.WARN -> "WARN"
                LogLevel.ERROR -> "ERROR"
                LogLevel.NONE -> ""
            }

            println("[$levelStr][$tag] $message")
            throwable?.printStackTrace()
        }
    }

    /**
     * Convenience function for conditional debug logging
     */
    inline fun debugIf(condition: Boolean, tag: String, message: () -> String) {
        if (condition) {
            debug(tag, message())
        }
    }

    /**
     * Performance-aware logging that only evaluates message if needed
     */
    inline fun lazyDebug(tag: String, message: () -> String) {
        if (enabled && level.priority <= LogLevel.DEBUG.priority) {
            debug(tag, message())
        }
    }

    inline fun lazyInfo(tag: String, message: () -> String) {
        if (enabled && level.priority <= LogLevel.INFO.priority) {
            info(tag, message())
        }
    }
}