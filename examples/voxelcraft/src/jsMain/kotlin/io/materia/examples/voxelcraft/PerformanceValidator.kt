package io.materia.examples.voxelcraft

import io.materia.renderer.RenderStats

/**
 * Performance validator for constitutional requirements.
 * T020: Validates 60 FPS WebGPU, 30 FPS WebGL minimum, <100 draw calls.
 */
object PerformanceValidator {

    /**
     * Performance metrics snapshot for validation
     */
    data class PerformanceMetrics(
        val fps: Float,
        val drawCalls: Int,
        val triangles: Int,
        val backendType: String,
        val frameTime: Double = 0.0
    )

    /**
     * Validation result with detailed breakdown
     */
    data class ValidationResult(
        val passed: Boolean,
        val meetsWebGPUTarget: Boolean,
        val meetsWebGLMinimum: Boolean,
        val meetsDrawCallLimit: Boolean,
        val message: String
    )

    /**
     * Validate constitutional performance requirements.
     *
     * Constitutional Requirements:
     * - FR-001: 60 FPS target with WebGPU
     * - FR-004: 30 FPS minimum with WebGL 2.0 fallback
     * - FR-005: <100 draw calls for 81 chunks
     *
     * @param metrics Current performance metrics
     * @return Validation result with detailed breakdown
     */
    fun validate(metrics: PerformanceMetrics): ValidationResult {
        val isWebGPU = metrics.backendType.contains("WebGPU", ignoreCase = true)
        val isWebGL = metrics.backendType.contains("WebGL", ignoreCase = true)

        val meetsWebGPUTarget = isWebGPU && metrics.fps >= 60f
        val meetsWebGLMinimum = isWebGL && metrics.fps >= 30f
        val meetsDrawCallLimit = metrics.drawCalls < 100

        val passed = (meetsWebGPUTarget || meetsWebGLMinimum) && meetsDrawCallLimit

        val message = buildString {
            appendLine("🎯 Performance Validation:")
            appendLine("  Backend: ${metrics.backendType}")
            appendLine("  FPS: ${metrics.fps.toInt()} (Target: ${if (isWebGPU) "60" else "30"})")
            appendLine("  Draw Calls: ${metrics.drawCalls} (Limit: <100)")
            appendLine("  Triangles: ${metrics.triangles}")

            if (!passed) {
                appendLine()
                appendLine("❌ VALIDATION FAILED:")
                if (isWebGPU && !meetsWebGPUTarget) {
                    appendLine("  • WebGPU FPS ${metrics.fps.toInt()} < 60 (FR-001 requirement)")
                }
                if (isWebGL && !meetsWebGLMinimum) {
                    appendLine("  • WebGL FPS ${metrics.fps.toInt()} < 30 (FR-004 requirement)")
                }
                if (!meetsDrawCallLimit) {
                    appendLine("  • Draw calls ${metrics.drawCalls} >= 100 (FR-005 requirement)")
                }
            } else {
                appendLine()
                appendLine("✅ All constitutional requirements met!")
            }
        }

        return ValidationResult(
            passed = passed,
            meetsWebGPUTarget = meetsWebGPUTarget,
            meetsWebGLMinimum = meetsWebGLMinimum,
            meetsDrawCallLimit = meetsDrawCallLimit,
            message = message
        )
    }

    /**
     * Validate from RenderStats and additional context
     *
     * @param stats Renderer statistics
     * @param fps Current FPS
     * @param backendType Backend type string
     * @return Validation result
     */
    fun validateFromStats(stats: RenderStats, fps: Float, backendType: String): ValidationResult {
        val metrics = PerformanceMetrics(
            fps = fps,
            drawCalls = stats.drawCalls,
            triangles = stats.triangles,
            backendType = backendType
        )
        return validate(metrics)
    }

    /**
     * Log validation result to console with appropriate styling
     */
    fun logResult(result: ValidationResult) {
        if (result.passed) {
            console.log(result.message)
        } else {
            console.warn(result.message)
        }
    }

    /**
     * Perform warmup validation after initial frames stabilize.
     *
     * @param frameCount Current frame count
     * @param metrics Current performance metrics
     * @return Validation result, or null if warmup not complete
     */
    fun validateAfterWarmup(frameCount: Int, metrics: PerformanceMetrics): ValidationResult? {
        // Wait for 120 frames (~2 seconds at 60 FPS) for warmup
        if (frameCount < 120) return null

        // Validate on frame 120 only
        if (frameCount != 120) return null

        return validate(metrics)
    }
}
