package io.materia.renderer.metrics

import platform.Foundation.NSDate

/**
 * iOS performance monitor using NSDate and Metal timestamp queries (via MoltenVK).
 */
class IosPerformanceMonitor : AbstractPerformanceMonitor() {

    override fun getCurrentTimeMs(): Long {
        // NSDate.timeIntervalSinceNow returns time in seconds
        return (NSDate().timeIntervalSince1970 * 1000).toLong()
    }

    /**
     * Use Metal timestamp queries via MoltenVK for precise GPU timing.
     */
    fun queryMetalTimestamp(): Long {
        // Placeholder - would use Metal/MoltenVK timestamp queries
        return getCurrentTimeMs()
    }
}

/**
 * Factory function for creating iOS performance monitor.
 */
actual fun createPerformanceMonitor(): PerformanceMonitor {
    return IosPerformanceMonitor()
}
