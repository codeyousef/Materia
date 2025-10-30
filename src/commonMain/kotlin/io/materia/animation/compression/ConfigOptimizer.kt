package io.materia.animation.compression

import io.materia.animation.AnimationCompressor
import io.materia.animation.AnimationCompressor.AnimationTrack
import io.materia.animation.AnimationCompressor.CompressionConfig
import io.materia.animation.AnimationCompressor.CompressionResult

/**
 * Configuration optimizer for compression settings
 */
object ConfigOptimizer {

    /**
     * Optimize compression config for target quality/size ratio
     */
    fun optimize(
        sampleTracks: List<AnimationTrack>,
        targetCompressionRatio: Float,
        maxQualityLoss: Float
    ): CompressionConfig {
        var config = CompressionConfig(targetCompressionRatio = targetCompressionRatio)

        // Iteratively adjust parameters to meet targets
        var attempts = 0
        while (attempts < 10) {
            val (_, result) = AnimationCompressor.compressAnimation(sampleTracks, config)

            if (result.compressionRatio <= targetCompressionRatio &&
                result.qualityLoss <= maxQualityLoss
            ) {
                break
            }

            // Adjust config based on results
            config = adjust(config, result, targetCompressionRatio, maxQualityLoss)
            attempts++
        }

        return config
    }

    /**
     * Adjust configuration based on compression results
     */
    fun adjust(
        config: CompressionConfig,
        result: CompressionResult,
        targetRatio: Float,
        maxQualityLoss: Float
    ): CompressionConfig {
        val ratioError = result.compressionRatio - targetRatio
        val qualityError = result.qualityLoss - maxQualityLoss

        return config.copy(
            positionTolerance = if (ratioError > 0) config.positionTolerance * 1.1f else config.positionTolerance * 0.9f,
            rotationTolerance = if (ratioError > 0) config.rotationTolerance * 1.1f else config.rotationTolerance * 0.9f,
            scaleTolerance = if (ratioError > 0) config.scaleTolerance * 1.1f else config.scaleTolerance * 0.9f,
            simplificationRatio = if (qualityError > 0) config.simplificationRatio * 1.1f else config.simplificationRatio * 0.9f
        )
    }
}
