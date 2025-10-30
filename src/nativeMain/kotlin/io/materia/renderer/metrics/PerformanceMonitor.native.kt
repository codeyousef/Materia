/**
 * Native stub for PerformanceMonitor.
 * Native platforms are not primary targets for Materia.
 */

package io.materia.renderer.metrics

import kotlinx.datetime.Clock

/**
 * Native actual for createPerformanceMonitor function.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual fun createPerformanceMonitor(): PerformanceMonitor {
    return object : AbstractPerformanceMonitor() {
        override fun getCurrentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
