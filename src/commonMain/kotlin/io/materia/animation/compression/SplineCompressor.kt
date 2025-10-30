package io.materia.animation.compression

import io.materia.animation.AnimationCompressor.AnimationTrack
import io.materia.animation.AnimationCompressor.CompressionConfig
import io.materia.animation.AnimationCompressor.Keyframe
import io.materia.core.math.Vector3

/**
 * Spline-based compression for animation tracks
 */
object SplineCompressor {

    /**
     * Spline segment for curve compression
     */
    data class SplineSegment(
        val startTime: Float,
        val endTime: Float,
        val controlPoints: List<Vector3>,
        val error: Float
    )

    /**
     * Apply spline-based compression
     */
    fun compress(
        track: AnimationTrack,
        config: CompressionConfig
    ): AnimationTrack {
        if (track.keyframes.size < 4) return track

        val segments = generateSegments(track, config)
        val optimizedKeyframes = convertToKeyframes(segments)

        return track.copy(
            keyframes = optimizedKeyframes.toMutableList(),
            interpolation = AnimationTrack.InterpolationType.CUBIC
        )
    }

    /**
     * Generate spline segments for track
     */
    fun generateSegments(
        track: AnimationTrack,
        config: CompressionConfig
    ): List<SplineSegment> {
        val segments = mutableListOf<SplineSegment>()
        var startIndex = 0

        while (startIndex < track.keyframes.size - 1) {
            val segment = findOptimalSegment(
                track.keyframes,
                startIndex,
                config.splineErrorThreshold,
                config.maxSplineSegmentLength
            )
            segments.add(segment)

            // Find next start index
            startIndex = track.keyframes.indexOfFirst { it.time >= segment.endTime }
            if (startIndex == -1) break
        }

        return segments
    }

    /**
     * Find optimal spline segment starting from given index
     */
    fun findOptimalSegment(
        keyframes: List<Keyframe>,
        startIndex: Int,
        errorThreshold: Float,
        maxLength: Float
    ): SplineSegment {
        val startKeyframe = keyframes[startIndex]
        var endIndex = startIndex + 1
        var bestEndIndex = endIndex

        // Extend segment while error is acceptable
        while (endIndex < keyframes.size) {
            val endKeyframe = keyframes[endIndex]
            val segmentLength = endKeyframe.time - startKeyframe.time

            if (segmentLength > maxLength) break

            val error = calculateError(keyframes, startIndex, endIndex)
            if (error <= errorThreshold) {
                bestEndIndex = endIndex
            } else {
                break
            }

            endIndex++
        }

        val endKeyframe = keyframes[bestEndIndex]
        val controlPoints = generateControlPoints(keyframes, startIndex, bestEndIndex)

        return SplineSegment(
            startTime = startKeyframe.time,
            endTime = endKeyframe.time,
            controlPoints = controlPoints,
            error = calculateError(keyframes, startIndex, bestEndIndex)
        )
    }

    /**
     * Calculate error for spline approximation
     */
    fun calculateError(
        keyframes: List<Keyframe>,
        startIndex: Int,
        endIndex: Int
    ): Float {
        // Simplified error calculation
        var totalError = 0f
        val segmentKeyframes = keyframes.subList(startIndex, endIndex + 1)

        for (i in 1 until segmentKeyframes.size - 1) {
            val keyframe = segmentKeyframes[i]
            val interpolated = interpolate(
                segmentKeyframes.first(),
                segmentKeyframes.last(),
                keyframe.time
            )
            totalError = totalError + keyframe.distanceTo(interpolated)
        }

        return totalError / maxOf(1, segmentKeyframes.size - 2)
    }

    /**
     * Generate control points for spline
     */
    fun generateControlPoints(
        keyframes: List<Keyframe>,
        startIndex: Int,
        endIndex: Int
    ): List<Vector3> {
        // Simplified - generate basic control points
        val startKeyframe = keyframes[startIndex]
        val endKeyframe = keyframes[endIndex]

        return listOf(
            startKeyframe.vector3,
            endKeyframe.vector3
        )
    }

    /**
     * Interpolate using spline
     */
    fun interpolate(
        start: Keyframe,
        end: Keyframe,
        time: Float
    ): Keyframe {
        val t = (time - start.time) / (end.time - start.time)
        val interpolatedValue = FloatArray(start.value.size) { i ->
            start.value[i] + t * (end.value[i] - start.value[i])
        }
        return Keyframe(time, interpolatedValue)
    }

    /**
     * Convert spline segments back to keyframes
     */
    fun convertToKeyframes(segments: List<SplineSegment>): List<Keyframe> {
        val keyframes = mutableListOf<Keyframe>()

        for (segment in segments) {
            // Add start point
            keyframes.add(
                Keyframe(
                    segment.startTime,
                    floatArrayOf(
                        segment.controlPoints[0].x,
                        segment.controlPoints[0].y,
                        segment.controlPoints[0].z
                    )
                )
            )

            // Add end point (will be deduplicated)
            if (segment == segments.last()) {
                keyframes.add(
                    Keyframe(
                        segment.endTime,
                        floatArrayOf(
                            segment.controlPoints.last().x,
                            segment.controlPoints.last().y,
                            segment.controlPoints.last().z
                        )
                    )
                )
            }
        }

        return keyframes.distinctBy { it.time }
    }
}
