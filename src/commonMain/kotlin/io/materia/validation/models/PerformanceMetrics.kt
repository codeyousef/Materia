package io.materia.validation.models

import kotlinx.serialization.Serializable

/**
 * Comprehensive performance metrics for validation and monitoring.
 *
 * Aggregates various performance measurements including frame rate, memory usage,
 * initialization time, and rendering load to assess constitutional compliance.
 */
@Serializable
data class PerformanceMetrics(
    /**
     * Frame rate performance metrics.
     */
    val fps: FpsMetrics,

    /**
     * Memory usage metrics.
     */
    val memory: MemoryMetrics,

    /**
     * Initialization performance metrics.
     */
    val initialization: InitMetrics,

    /**
     * Rendering load and complexity metrics.
     */
    val renderingLoad: RenderingLoad
) {

    /**
     * Checks if all performance metrics meet constitutional requirements.
     * @return true if all metrics meet requirements
     */
    fun meetsAllRequirements(): Boolean {
        return fps.meetsRequirement &&
                memory.withinBudget &&
                initialization.meetsRequirement
    }

    /**
     * Calculates overall performance score (0.0-1.0).
     * @return weighted average of all metric scores
     */
    fun calculateOverallScore(): Float {
        val fpsScore = fps.calculateScore()
        val memoryScore = memory.calculateScore()
        val initScore = initialization.calculateScore()
        val renderScore = renderingLoad.calculateEfficiencyScore()

        return (fpsScore * 0.4f) +  // FPS is most important
                (memoryScore * 0.3f) +
                (renderScore * 0.2f) +
                (initScore * 0.1f)
    }

    /**
     * Gets performance issues that need attention.
     * @return list of performance issue descriptions
     */
    fun getPerformanceIssues(): List<String> {
        val issues = mutableListOf<String>()

        if (!fps.meetsRequirement) {
            issues.add("Frame rate below 60 FPS requirement (avg: ${fps.average} FPS)")
        }

        if (!memory.withinBudget) {
            issues.add("Memory usage exceeds budget (${memory.heapUsed} MB used)")
        }

        if (!initialization.meetsRequirement) {
            issues.add("Initialization time too slow (${initialization.timeMillis} ms)")
        }

        if (renderingLoad.triangleCount > 1_000_000) {
            issues.add("Excessive triangle count: ${renderingLoad.triangleCount}")
        }

        if (renderingLoad.drawCalls > 1000) {
            issues.add("Too many draw calls: ${renderingLoad.drawCalls}")
        }

        return issues
    }
}

/**
 * Frame rate performance metrics.
 */
@Serializable
data class FpsMetrics(
    /**
     * Minimum FPS measured.
     */
    val min: Float,

    /**
     * Average FPS over measurement period.
     */
    val average: Float,

    /**
     * Maximum FPS measured.
     */
    val max: Float,

    /**
     * 95th percentile FPS (95% of frames are at or above this rate).
     */
    val p95: Float,

    /**
     * 99th percentile FPS (99% of frames are at or above this rate).
     */
    val p99: Float,

    /**
     * Whether FPS meets constitutional 60 FPS requirement.
     */
    val meetsRequirement: Boolean
) {

    /**
     * Calculates FPS score (0.0-1.0) based on constitutional target of 60 FPS.
     * @return normalized FPS score
     */
    fun calculateScore(): Float {
        // Use p95 as the primary metric (95% of frames meet this rate)
        val baseScore = (p95 / 60.0f).coerceIn(0.0f, 1.0f)

        // Bonus for consistency (low variance)
        val variance = max - min
        val consistencyBonus = if (variance < 10) 0.1f else 0.0f

        return (baseScore + consistencyBonus).coerceIn(0.0f, 1.0f)
    }

    /**
     * Gets human-readable FPS summary.
     * @return formatted FPS description
     */
    fun getSummary(): String {
        return "FPS: avg=${average.format(1)}, p95=${p95.format(1)}, p99=${p99.format(1)} " +
                "(min=${min.format(1)}, max=${max.format(1)})"
    }
}

/**
 * Memory usage metrics.
 */
@Serializable
data class MemoryMetrics(
    /**
     * Heap memory used in megabytes.
     */
    val heapUsed: Long,

    /**
     * GPU memory used in megabytes, null if not applicable/measurable.
     */
    val gpuMemory: Long? = null,

    /**
     * Whether memory usage is within the 5MB constitutional budget.
     */
    val withinBudget: Boolean
) {

    /**
     * Calculates memory score (0.0-1.0) based on 5MB budget.
     * @return normalized memory score
     */
    fun calculateScore(): Float {
        val totalMemory = heapUsed + (gpuMemory ?: 0)
        val budgetMB = 5L

        return when {
            totalMemory <= budgetMB -> 1.0f
            totalMemory <= budgetMB * 2 -> 0.7f  // Within 2x budget
            totalMemory <= budgetMB * 3 -> 0.4f  // Within 3x budget
            else -> 0.2f
        }
    }

    /**
     * Gets total memory usage.
     * @return combined heap and GPU memory in MB
     */
    fun getTotalMemory(): Long {
        return heapUsed + (gpuMemory ?: 0)
    }

    /**
     * Gets human-readable memory summary.
     * @return formatted memory description
     */
    fun getSummary(): String {
        val gpuText = gpuMemory?.let { ", GPU: $it MB" } ?: ""
        val budgetText = if (withinBudget) "✓" else "✗"
        return "Memory: Heap: $heapUsed MB$gpuText [$budgetText budget]"
    }
}

/**
 * Initialization performance metrics.
 */
@Serializable
data class InitMetrics(
    /**
     * Initialization time in milliseconds.
     */
    val timeMillis: Long,

    /**
     * Whether initialization meets performance requirement (<1000ms).
     */
    val meetsRequirement: Boolean
) {

    /**
     * Calculates initialization score (0.0-1.0) based on 1 second target.
     * @return normalized initialization score
     */
    fun calculateScore(): Float {
        return when {
            timeMillis <= 500 -> 1.0f   // Excellent: <500ms
            timeMillis <= 1000 -> 0.8f  // Good: <1s
            timeMillis <= 2000 -> 0.5f  // Acceptable: <2s
            timeMillis <= 3000 -> 0.3f  // Slow: <3s
            else -> 0.1f                // Very slow: >3s
        }
    }

    /**
     * Gets human-readable initialization summary.
     * @return formatted initialization description
     */
    fun getSummary(): String {
        val status = when {
            timeMillis < 500 -> "excellent"
            timeMillis < 1000 -> "good"
            timeMillis < 2000 -> "acceptable"
            else -> "slow"
        }
        return "Initialization: $timeMillis ms ($status)"
    }
}

/**
 * Rendering load and complexity metrics.
 */
@Serializable
data class RenderingLoad(
    /**
     * Number of triangles rendered per frame.
     */
    val triangleCount: Int,

    /**
     * Number of draw calls per frame.
     */
    val drawCalls: Int,

    /**
     * Texture memory usage in megabytes.
     */
    val textureMemory: Long
) {

    /**
     * Calculates rendering efficiency score (0.0-1.0).
     * Based on triangle-to-draw-call ratio and overall complexity.
     * @return efficiency score
     */
    fun calculateEfficiencyScore(): Float {
        // Target: 100k triangles as per constitutional requirement
        val triangleScore = when {
            triangleCount >= 100_000 -> 1.0f
            triangleCount >= 50_000 -> 0.8f
            triangleCount >= 10_000 -> 0.6f
            triangleCount >= 1_000 -> 0.4f
            else -> 0.2f
        }

        // Efficiency: triangles per draw call (higher is better)
        val efficiency = if (drawCalls > 0) {
            triangleCount.toFloat() / drawCalls
        } else {
            0.0f
        }

        // Target: at least 100 triangles per draw call
        val efficiencyScore = (efficiency / 100.0f).coerceIn(0.0f, 1.0f)

        // Texture memory penalty (should be reasonable)
        val textureScore = when {
            textureMemory <= 50 -> 1.0f   // Excellent: <50MB
            textureMemory <= 100 -> 0.8f  // Good: <100MB
            textureMemory <= 200 -> 0.6f  // Acceptable: <200MB
            else -> 0.4f                  // High
        }

        return (triangleScore * 0.5f) +
                (efficiencyScore * 0.3f) +
                (textureScore * 0.2f)
    }

    /**
     * Gets batching efficiency (triangles per draw call).
     * @return efficiency ratio
     */
    fun getBatchingEfficiency(): Float {
        return if (drawCalls > 0) {
            triangleCount.toFloat() / drawCalls
        } else {
            0.0f
        }
    }

    /**
     * Checks if rendering load meets constitutional requirement (100k triangles).
     * @return true if triangle count is at least 100k
     */
    fun meetsTriangleRequirement(): Boolean {
        return triangleCount >= 100_000
    }

    /**
     * Gets human-readable rendering load summary.
     * @return formatted rendering description
     */
    fun getSummary(): String {
        val efficiency = getBatchingEfficiency()
        return "Rendering: ${triangleCount} triangles, $drawCalls draw calls " +
                "(${efficiency.format(1)} tri/call), $textureMemory MB textures"
    }
}

/**
 * Extension function to format float values.
 * Multiplatform-compatible implementation without String.format().
 */
private fun Float.format(decimals: Int): String {
    val multiplier = when (decimals) {
        0 -> 1.0
        1 -> 10.0
        2 -> 100.0
        3 -> 1000.0
        else -> {
            var m = 1.0
            repeat(decimals) { m *= 10.0 }
            m
        }
    }
    val rounded = kotlin.math.round(this * multiplier) / multiplier

    // Convert to string and ensure proper decimal places
    val str = rounded.toString()
    val parts = str.split('.')
    val intPart = parts[0]
    val decPart = if (parts.size > 1) parts[1] else ""

    return if (decimals == 0) {
        intPart
    } else {
        val paddedDec = decPart.padEnd(decimals, '0').take(decimals)
        "$intPart.$paddedDec"
    }
}