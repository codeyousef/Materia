package io.materia.datetime

/**
 * Get current time in milliseconds since epoch.
 * This is a workaround for Clock.System.now() resolution issues in commonMain.
 */
expect fun currentTimeMillis(): Long

/**
 * Get current instant as ISO-8601 string.
 * This is a workaround for Clock.System.now() resolution issues in commonMain.
 */
expect fun currentTimeString(): String
