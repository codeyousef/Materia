package io.materia.animation.compression

import io.materia.animation.AnimationCompressor.AnimationTrack
import io.materia.animation.AnimationCompressor.CompressionConfig
import io.materia.animation.AnimationCompressor.Keyframe
import kotlin.math.sqrt

/**
 * Keyframe optimization using Douglas-Peucker and tolerance-based algorithms
 */
object KeyframeOptimizer {

    /**
     * Remove redundant keyframes using Douglas-Peucker algorithm
     */
    fun removeRedundant(
        track: AnimationTrack,
        config: CompressionConfig
    ): AnimationTrack {
        if (track.keyframes.size <= 2) return track

        val tolerance = when (track.type) {
            AnimationTrack.TrackType.POSITION -> config.positionTolerance
            AnimationTrack.TrackType.ROTATION -> config.rotationTolerance
            AnimationTrack.TrackType.SCALE -> config.scaleTolerance
            else -> 0.001f
        }

        val filteredKeyframes = mutableListOf<Keyframe>()

        // Always keep first keyframe
        if (config.preserveFirstLastKeys && track.keyframes.isNotEmpty()) {
            filteredKeyframes.add(track.keyframes.first())
        }

        // Douglas-Peucker algorithm for keyframe reduction
        val indices = douglasPeucker(track.keyframes, tolerance, 0, track.keyframes.size - 1)

        for (i in 1 until indices.size - 1) {
            filteredKeyframes.add(track.keyframes[indices[i]])
        }

        // Always keep last keyframe
        if (config.preserveFirstLastKeys && track.keyframes.size > 1) {
            filteredKeyframes.add(track.keyframes.last())
        }

        return track.copy(keyframes = filteredKeyframes)
    }

    /**
     * Douglas-Peucker algorithm for curve simplification
     */
    fun douglasPeucker(
        keyframes: List<Keyframe>,
        tolerance: Float,
        startIndex: Int,
        endIndex: Int
    ): List<Int> {
        if (endIndex - startIndex <= 1) {
            return listOf(startIndex, endIndex)
        }

        var maxDistance = 0f
        var maxIndex = -1

        // Find the point with maximum distance from line segment
        for (i in startIndex + 1 until endIndex) {
            val distance = pointToLineDistance(
                keyframes[i],
                keyframes[startIndex],
                keyframes[endIndex]
            )
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }

        // If max distance is greater than tolerance, recursively subdivide
        if (maxDistance > tolerance && maxIndex != -1) {
            val leftIndices = douglasPeucker(keyframes, tolerance, startIndex, maxIndex)
            val rightIndices = douglasPeucker(keyframes, tolerance, maxIndex, endIndex)

            return leftIndices + rightIndices.drop(1) // Remove duplicate middle point
        } else {
            return listOf(startIndex, endIndex)
        }
    }

    /**
     * Calculate distance from point to line segment
     */
    fun pointToLineDistance(
        point: Keyframe,
        lineStart: Keyframe,
        lineEnd: Keyframe
    ): Float {
        // Simplified distance calculation in time-value space
        val lineLength = lineEnd.time - lineStart.time
        if (lineLength == 0f) return point.distanceTo(lineStart)

        val t = ((point.time - lineStart.time) / lineLength).coerceIn(0f, 1f)

        // Linear interpolation between line endpoints
        val interpolatedValue = FloatArray(point.value.size) { i ->
            lineStart.value[i] + t * (lineEnd.value[i] - lineStart.value[i])
        }

        val interpolatedKeyframe = Keyframe(point.time, interpolatedValue)
        return point.distanceTo(interpolatedKeyframe)
    }

    /**
     * Apply tolerance-based compression
     */
    fun applyToleranceCompression(
        track: AnimationTrack,
        config: CompressionConfig
    ): AnimationTrack {
        val tolerance = when (track.type) {
            AnimationTrack.TrackType.POSITION -> config.positionTolerance
            AnimationTrack.TrackType.ROTATION -> config.rotationTolerance
            AnimationTrack.TrackType.SCALE -> config.scaleTolerance
            else -> 0.001f
        }

        val compressedKeyframes = mutableListOf<Keyframe>()
        var lastKeyframe: Keyframe? = null

        for (keyframe in track.keyframes) {
            if (lastKeyframe == null || keyframe.distanceTo(lastKeyframe) > tolerance) {
                compressedKeyframes.add(keyframe)
                lastKeyframe = keyframe
            }
        }

        return track.copy(keyframes = compressedKeyframes)
    }
}
