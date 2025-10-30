package io.materia.animation.compression

import io.materia.animation.AnimationCompressor.AnimationTrack
import io.materia.animation.AnimationCompressor.CompressionConfig
import io.materia.animation.AnimationCompressor.Keyframe
import kotlin.math.abs

/**
 * Curve fitting optimization for animation tracks
 */
object CurveFitter {

    /**
     * Apply curve fitting optimization
     */
    fun fitCurve(
        track: AnimationTrack,
        config: CompressionConfig
    ): AnimationTrack {
        val targetKeyframeCount = (track.keyframes.size * config.simplificationRatio).toInt()
        if (targetKeyframeCount >= track.keyframes.size) return track

        // Use curve fitting to reduce keyframe count while maintaining shape
        val importantIndices =
            findImportantKeyframes(track, targetKeyframeCount, config.maxCurveError)
        val reducedKeyframes = importantIndices.map { track.keyframes[it] }

        return track.copy(keyframes = reducedKeyframes.toMutableList())
    }

    /**
     * Find most important keyframes to preserve curve shape
     */
    fun findImportantKeyframes(
        track: AnimationTrack,
        targetCount: Int,
        maxError: Float
    ): List<Int> {
        if (targetCount >= track.keyframes.size) {
            return track.keyframes.indices.toList()
        }

        val importantIndices = mutableSetOf<Int>()

        // Always include first and last
        importantIndices.add(0)
        importantIndices.add(track.keyframes.size - 1)

        // Find keyframes with highest curvature
        val curvatures = calculateCurvatures(track.keyframes)
        val sortedByCurvature = curvatures.indices.sortedByDescending { curvatures[it] }

        for (index in sortedByCurvature) {
            if (importantIndices.size >= targetCount) break
            importantIndices.add(index)
        }

        return importantIndices.sorted()
    }

    /**
     * Calculate curvature at each keyframe
     */
    fun calculateCurvatures(keyframes: List<Keyframe>): FloatArray {
        val curvatures = FloatArray(keyframes.size)

        for (i in 1 until keyframes.size - 1) {
            val prev = keyframes[i - 1]
            val curr = keyframes[i]
            val next = keyframes[i + 1]

            // Simplified curvature calculation
            val v1 = curr.distanceTo(prev)
            val v2 = next.distanceTo(curr)
            val directDistance = next.distanceTo(prev)

            curvatures[i] = abs(v1 + v2 - directDistance)
        }

        return curvatures
    }
}
