package io.materia.renderer.metrics

/**
 * Web performance monitor using high-precision Performance API and WebGPU timestamp queries.
 */
class WebPerformanceMonitor : AbstractPerformanceMonitor() {

    override fun getCurrentTimeMs(): Long {
        return getPerformanceNow().toLong()
    }

    private fun getPerformanceNow(): Double {
        return js("performance.now()") as Double
    }

    /**
     * Use WebGPU timestamp queries for GPU timing (when available).
     */
    fun queryGPUTimestamp(): Double {
        // WebGPU timestamp queries require "timestamp-query" feature
        // For now, use performance.now() as fallback
        return getPerformanceNow()
    }
}

/**
 * Factory function for creating Web performance monitor.
 */
actual fun createPerformanceMonitor(): PerformanceMonitor {
    return WebPerformanceMonitor()
}
