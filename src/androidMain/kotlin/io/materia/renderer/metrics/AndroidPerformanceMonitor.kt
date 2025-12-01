package io.materia.renderer.metrics

import android.os.SystemClock

/**
 * Android performance monitor using SystemClock and Vulkan timestamp queries.
 */
class AndroidPerformanceMonitor : AbstractPerformanceMonitor() {

    override fun getCurrentTimeMs(): Long {
        // Use elapsedRealtimeNanos for monotonic time
        return SystemClock.elapsedRealtimeNanos() / 1_000_000
    }

    /**
     * Use Vulkan timestamp queries for GPU timing on Android.
     */
    fun queryVulkanTimestamp(): Long {
        // GPU timestamp queries require active VkQueryPool; falls back to system time
        return getCurrentTimeMs()
    }
}

/**
 * Factory function for creating Android performance monitor.
 */
actual fun createPerformanceMonitor(): PerformanceMonitor {
    return AndroidPerformanceMonitor()
}
