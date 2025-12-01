package io.materia.datetime

/**
 * Get current time in milliseconds since epoch.
 * Platform-specific implementation provides optimal time resolution.
 */
expect fun currentTimeMillis(): Long

/**
 * Get current instant as ISO-8601 string.
 * Platform-specific implementation returns system time in ISO-8601 format.
 */
expect fun currentTimeString(): String
