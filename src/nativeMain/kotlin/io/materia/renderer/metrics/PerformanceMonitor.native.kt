package io.materia.renderer.metrics

import io.materia.datetime.currentTimeMillis

actual fun createPerformanceMonitor(): PerformanceMonitor {
    return object : AbstractPerformanceMonitor() {
        override fun getCurrentTimeMs(): Long = currentTimeMillis()
    }
}
