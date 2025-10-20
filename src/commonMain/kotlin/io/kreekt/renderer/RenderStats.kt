/**
 * T013: RenderStats Data Class
 * Feature: 019-we-should-not
 *
 * Performance metrics for monitoring and validation.
 */

package io.kreekt.renderer

import kotlin.collections.buildList
import kotlin.math.roundToInt

/**
 * Performance metrics for a rendered frame.
 *
 * Used for:
 * - Performance monitoring (FR-019: 60 FPS target, 30 FPS minimum)
 * - PerformanceValidator validation
 * - Debug UI (VoxelCraft HUD)
 *
 * @property fps Frames per second
 * @property frameTime Frame time in milliseconds
 * @property triangles Triangle count rendered
 * @property drawCalls Number of draw calls issued
 * @property textureMemory Texture memory usage in bytes
 * @property bufferMemory Buffer memory usage in bytes
 * @property timestamp Timestamp of stats capture (milliseconds since epoch)
 */
data class RenderStats(
    val fps: Double,
    val frameTime: Double,
    val triangles: Int,
    val drawCalls: Int,
    val textureMemory: Long = 0L,
    val bufferMemory: Long = 0L,
    val timestamp: Long = 0L,
    val iblCpuMs: Double = 0.0,
    val iblPrefilterMipCount: Int = 0,
    val iblLastRoughness: Float = 0f,
    val iblUsingFallbackEnvironment: Boolean = false,
    val iblUsingFallbackBrdf: Boolean = false
) {
    init {
        require(fps >= 0) { "FPS must be non-negative, got: $fps" }
        require(frameTime >= 0) { "Frame time must be non-negative, got: $frameTime" }
        require(triangles >= 0) { "Triangles must be non-negative, got: $triangles" }
        require(drawCalls >= 0) { "Draw calls must be non-negative, got: $drawCalls" }
    }

    /**
     * Returns true if performance meets FR-019 minimum requirement (30 FPS).
     */
    val meetsMinimumFps: Boolean
        get() = fps >= 30.0

    /**
     * Returns true if performance meets FR-019 target (60 FPS).
     */
    val meetsTargetFps: Boolean
        get() = fps >= 60.0

    /**
     * Returns human-readable performance summary.
     */
    override fun toString(): String {
        val fpsStr = (fps * 10).toInt() / 10.0
        val frameTimeStr = (frameTime * 100).toInt() / 100.0
        val fallbackTokens = buildList {
            if (iblUsingFallbackEnvironment) add("env")
            if (iblUsingFallbackBrdf) add("brdf")
        }
        val fallbackSummary = when (fallbackTokens.size) {
            0 -> ""
            1 -> " fallback=${fallbackTokens.first()}"
            else -> " fallback=${fallbackTokens.joinToString("+")}"
        }
        val iblSummary = if (iblPrefilterMipCount > 0 || fallbackTokens.isNotEmpty()) {
            val iblMs = (iblCpuMs * 10).roundToInt() / 10.0
            val iblRough = (iblLastRoughness * 100).roundToInt() / 100.0
            " | IBL ${iblMs}ms mips=$iblPrefilterMipCount rough=$iblRough$fallbackSummary"
        } else {
            ""
        }
        return "$fpsStr FPS | $triangles tris | $drawCalls DC | ${frameTimeStr}ms$iblSummary"
    }

    /**
     * Returns detailed performance report.
     */
    fun toDetailedString(): String {
        val fpsStr = (fps * 10).toInt() / 10.0
        val frameTimeStr = (frameTime * 100).toInt() / 100.0
        return buildString {
            appendLine("Performance Stats:")
            appendLine("  FPS: $fpsStr")
            appendLine("  Frame Time: ${frameTimeStr}ms")
            appendLine("  Triangles: $triangles")
            appendLine("  Draw Calls: $drawCalls")
            appendLine("  Texture Memory: ${textureMemory / 1024 / 1024}MB")
            appendLine("  Buffer Memory: ${bufferMemory / 1024 / 1024}MB")
            if (iblPrefilterMipCount > 0 || iblUsingFallbackEnvironment || iblUsingFallbackBrdf) {
                val iblMs = (iblCpuMs * 100).roundToInt() / 100.0
                val iblRough = (iblLastRoughness * 100).roundToInt() / 100.0
                appendLine("  IBL CPU: ${iblMs}ms (mips=$iblPrefilterMipCount, rough=$iblRough)")
                if (iblUsingFallbackEnvironment || iblUsingFallbackBrdf) {
                    appendLine(
                        "  IBL Fallback: env=${iblUsingFallbackEnvironment}, brdf=${iblUsingFallbackBrdf}"
                    )
                }
            }
            appendLine(
                "  Status: ${
                    when {
                        meetsTargetFps -> "Target (60 FPS)"
                        meetsMinimumFps -> "Above minimum (30 FPS)"
                        else -> "Below minimum (<30 FPS)"
                    }
                }"
            )
        }
    }
}
