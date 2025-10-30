package io.materia.animation.compression

import io.materia.animation.AnimationCompressor.AnimationTrack
import io.materia.animation.AnimationCompressor.Keyframe

/**
 * Quality estimation and sampling for compressed animations
 */
object QualityEstimator {

    /**
     * Estimate quality loss from compression
     */
    fun estimateQualityLoss(
        originalTracks: List<AnimationTrack>,
        compressedTracks: List<AnimationTrack>
    ): Float {
        var totalError = 0f
        var comparisonCount = 0

        for ((original, compressed) in originalTracks.zip(compressedTracks)) {
            val sampleCount = 100 // Sample at 100 points for comparison
            val duration = original.getDuration()

            for (i in 0 until sampleCount) {
                val time = (i.toFloat() / (sampleCount - 1)) * duration
                val originalValue = sampleTrackAtTime(original, time)
                val compressedValue = sampleTrackAtTime(compressed, time)

                if (originalValue != null && compressedValue != null) {
                    totalError = totalError + originalValue.distanceTo(compressedValue)
                    comparisonCount++
                }
            }
        }

        return if (comparisonCount > 0) totalError / comparisonCount else 0f
    }

    /**
     * Sample track value at specific time
     */
    fun sampleTrackAtTime(track: AnimationTrack, time: Float): Keyframe? {
        if (track.keyframes.isEmpty()) return null

        // Find surrounding keyframes
        val beforeIndex = track.keyframes.indexOfLast { it.time <= time }
        val afterIndex = track.keyframes.indexOfFirst { it.time >= time }

        return when {
            beforeIndex == -1 -> track.keyframes.first()
            afterIndex == -1 -> track.keyframes.last()
            beforeIndex == afterIndex -> track.keyframes[beforeIndex]
            else -> {
                val before = track.keyframes[beforeIndex]
                val after = track.keyframes[afterIndex]
                interpolateKeyframes(before, after, time)
            }
        }
    }

    /**
     * Interpolate between two keyframes
     */
    fun interpolateKeyframes(before: Keyframe, after: Keyframe, time: Float): Keyframe {
        val t = (time - before.time) / (after.time - before.time)
        val interpolatedValue = FloatArray(before.value.size) { i ->
            before.value[i] + t * (after.value[i] - before.value[i])
        }
        return Keyframe(time, interpolatedValue)
    }
}
