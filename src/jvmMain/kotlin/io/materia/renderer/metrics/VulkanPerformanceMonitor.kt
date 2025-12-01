package io.materia.renderer.metrics

/**
 * Vulkan performance monitor using System.nanoTime() and Vulkan timestamp queries.
 */
class VulkanPerformanceMonitor : AbstractPerformanceMonitor() {

    override fun getCurrentTimeMs(): Long {
        return System.nanoTime() / 1_000_000
    }

    /**
     * Use Vulkan timestamp queries for precise GPU timing (when available).
     * Would query VkQueryPool with VK_QUERY_TYPE_TIMESTAMP.
     */
    fun queryVulkanTimestamp(): Long {
        // GPU timestamp queries require active VkQueryPool; falls back to system time
        return getCurrentTimeMs()
    }
}

/**
 * Factory function for creating Vulkan performance monitor.
 */
actual fun createPerformanceMonitor(): PerformanceMonitor {
    return VulkanPerformanceMonitor()
}
