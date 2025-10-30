/**
 * Native stub for DateTimeUtils.
 * Native platforms are not primary targets for Materia.
 */

package io.materia.datetime

import kotlinx.datetime.Clock

/**
 * Native actual for currentTimeMillis function.
 */
actual fun currentTimeMillis(): Long {
    return Clock.System.now().toEpochMilliseconds()
}

/**
 * Native actual for currentTimeString function.
 */
actual fun currentTimeString(): String {
    return Clock.System.now().toString()
}
