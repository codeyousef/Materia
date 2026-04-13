package io.materia.datetime

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

/**
 * Native actual for currentTimeMillis function.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long {
    return time(null).toLong() * 1000L
}

/**
 * Native actual for currentTimeString function.
 */
actual fun currentTimeString(): String {
    return currentTimeMillis().toString()
}
