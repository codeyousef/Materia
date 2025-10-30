/**
 * Performance tier system for adaptive quality rendering
 * Based on research findings for multi-tier performance targets
 */
package io.materia.performance

import io.materia.renderer.Texture

import kotlinx.serialization.Serializable
import io.materia.core.platform.currentTimeMillis

/**
 * Quality tiers for adaptive performance system
 * Automatically detected based on hardware capabilities
 */
@Serializable
enum class QualityTier {
    /**
     * Mobile/integrated graphics: 60 FPS with 50k triangles + basic effects
     * Limited memory budget (256MB GPU, 512MB total)
     */
    MOBILE,

    /**
     * Mid-range dedicated GPU: 60 FPS with 100k triangles + advanced effects
     * Standard memory budget (1GB GPU)
     */
    STANDARD,

    /**
     * High-end hardware: 60 FPS with 500k+ triangles + full effect stack
     * High memory budget (2GB+ GPU)
     */
    HIGH,

    /**
     * Extreme hardware: 120+ FPS with unlimited triangles + experimental features
     * Unlimited memory budget (4GB+ GPU)
     */
    ULTRA
}

/**
 * Performance budgets for each quality tier
 */
data class PerformanceBudget(
    val targetFPS: Float,
    val maxTriangles: Int,
    val gpuMemoryMB: Int,
    val totalMemoryMB: Int,
    val maxLights: Int,
    val shadowMapSize: Int,
    val enableAdvancedEffects: Boolean,
    val enablePostProcessing: Boolean,
    val maxLODLevels: Int
) {
    companion object {
        fun forTier(tier: QualityTier): PerformanceBudget = when (tier) {
            QualityTier.MOBILE -> PerformanceBudget(
                targetFPS = 60f,
                maxTriangles = 50_000,
                gpuMemoryMB = 256,
                totalMemoryMB = 512,
                maxLights = 4,
                shadowMapSize = 512,
                enableAdvancedEffects = false,
                enablePostProcessing = false,
                maxLODLevels = 3
            )

            QualityTier.STANDARD -> PerformanceBudget(
                targetFPS = 60f,
                maxTriangles = 100_000,
                gpuMemoryMB = 1024,
                totalMemoryMB = 2048,
                maxLights = 8,
                shadowMapSize = 1024,
                enableAdvancedEffects = true,
                enablePostProcessing = true,
                maxLODLevels = 4
            )

            QualityTier.HIGH -> PerformanceBudget(
                targetFPS = 60f,
                maxTriangles = 500_000,
                gpuMemoryMB = 2048,
                totalMemoryMB = 4096,
                maxLights = 16,
                shadowMapSize = 2048,
                enableAdvancedEffects = true,
                enablePostProcessing = true,
                maxLODLevels = 5
            )

            QualityTier.ULTRA -> PerformanceBudget(
                targetFPS = 120f,
                maxTriangles = Int.MAX_VALUE,
                gpuMemoryMB = 4096,
                totalMemoryMB = 8192,
                maxLights = 32,
                shadowMapSize = 4096,
                enableAdvancedEffects = true,
                enablePostProcessing = true,
                maxLODLevels = 6
            )
        }
    }
}

/**
 * Hardware capability detection for automatic tier assignment
 */
data class HardwareCapabilities(
    val gpuMemoryMB: Int,
    val totalMemoryMB: Int,
    val computeShaders: Boolean,
    val geometryShaders: Boolean,
    val tessellationShaders: Boolean,
    val multiDrawIndirect: Boolean,
    val maxTextureSize: Int,
    val maxSamples: Int,
    val platform: Platform
) {
    enum class Platform {
        WEB, MOBILE, DESKTOP, XR
    }

    /**
     * Automatically determine optimal quality tier based on hardware
     */
    fun getOptimalTier(): QualityTier {
        return when {
            // Ultra tier: High-end desktop with 4GB+ VRAM
            platform == Platform.DESKTOP && gpuMemoryMB >= 4096 && computeShaders -> QualityTier.ULTRA

            // High tier: Mid-high end desktop with 2GB+ VRAM
            platform == Platform.DESKTOP && gpuMemoryMB >= 2048 -> QualityTier.HIGH

            // Standard tier: Entry desktop or high-end mobile
            gpuMemoryMB >= 1024 || (platform == Platform.MOBILE && totalMemoryMB >= 4096) -> QualityTier.STANDARD

            // Mobile tier: Everything else
            else -> QualityTier.MOBILE
        }
    }
}

/**
 * Performance metrics tracking
 */
data class PerformanceMetrics(
    val fps: Float,
    val frameTime: Float,
    val cpuTime: Float,
    val gpuTime: Float,
    val drawCalls: Int,
    val triangles: Int,
    val memoryUsage: Float,
    val vramUsage: Float,
    val temperature: Float,
    val powerUsage: Float,
    val timestamp: Long = currentTimeMillis()
) {
    /**
     * Check if performance is meeting the target budget
     */
    fun meetsTarget(budget: PerformanceBudget): Boolean {
        return fps >= budget.targetFPS * 0.9f && // Allow 10% tolerance
                triangles <= budget.maxTriangles &&
                vramUsage <= budget.gpuMemoryMB
    }

    /**
     * Suggest tier adjustment based on current performance
     */
    fun suggestTierAdjustment(currentTier: QualityTier): QualityTier? {
        val currentBudget = PerformanceBudget.forTier(currentTier)

        return when {
            // Performance is significantly above target - can increase quality
            fps > currentBudget.targetFPS * 1.3f && currentTier != QualityTier.ULTRA -> {
                when (currentTier) {
                    QualityTier.MOBILE -> QualityTier.STANDARD
                    QualityTier.STANDARD -> QualityTier.HIGH
                    QualityTier.HIGH -> QualityTier.ULTRA
                    QualityTier.ULTRA -> null
                }
            }

            // Performance is below target - should decrease quality
            !meetsTarget(currentBudget) && currentTier != QualityTier.MOBILE -> {
                when (currentTier) {
                    QualityTier.ULTRA -> QualityTier.HIGH
                    QualityTier.HIGH -> QualityTier.STANDARD
                    QualityTier.STANDARD -> QualityTier.MOBILE
                    QualityTier.MOBILE -> null
                }
            }

            else -> null // No change needed
        }
    }
}