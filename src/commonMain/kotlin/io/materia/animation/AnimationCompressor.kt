package io.materia.animation

import io.materia.animation.compression.*
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.core.platform.currentTimeMillis
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Advanced Animation Compressor for optimizing animation tracks and reducing memory usage.
 * Implements keyframe optimization, quaternion compression, spline fitting, and quality optimization.
 *
 * T040 - AnimationCompressor for track optimization
 */
object AnimationCompressor {

    /**
     * Compression configuration
     */
    data class CompressionConfig(
        // General settings
        val removeRedundantKeys: Boolean = true,
        val positionTolerance: Float = 0.001f,
        val rotationTolerance: Float = 0.001f,
        val scaleTolerance: Float = 0.001f,
        val timeTolerance: Float = 0.001f,

        // Spline optimization
        val useSplineCompression: Boolean = true,
        val splineErrorThreshold: Float = 0.01f,
        val maxSplineSegmentLength: Float = 2.0f,

        // Quaternion compression
        val quantizeQuaternions: Boolean = true,
        val quaternionBits: Int = 16, // Bits per component
        val useQuaternionShortestPath: Boolean = true,

        // Curve fitting
        val useCurveFitting: Boolean = true,
        val maxCurveError: Float = 0.005f,
        val simplificationRatio: Float = 0.5f,

        // Quality settings
        val targetCompressionRatio: Float = 0.3f, // Target 30% of original size
        val preserveFirstLastKeys: Boolean = true,
        val enableMultithreading: Boolean = true
    )

    /**
     * Compression result
     */
    data class CompressionResult(
        val originalSize: Int,
        val compressedSize: Int,
        val compressionRatio: Float,
        val qualityLoss: Float,
        val processingTime: Long,
        val optimizations: List<String>
    ) {
        val spaceSavings: Float
            get() = 1f - compressionRatio
    }

    /**
     * Animation track for compression
     */
    data class AnimationTrack(
        val name: String,
        val type: TrackType,
        val keyframes: MutableList<Keyframe>,
        val interpolation: InterpolationType = InterpolationType.LINEAR
    ) {
        enum class TrackType {
            POSITION,
            ROTATION,
            SCALE,
            MORPH_WEIGHTS,
            CUSTOM
        }

        enum class InterpolationType {
            STEP,
            LINEAR,
            CUBIC,
            HERMITE,
            BEZIER
        }

        fun getDuration(): Float = keyframes.maxOfOrNull { it.time } ?: 0f
        fun getKeyframeCount(): Int = keyframes.size
        fun getSize(): Int = keyframes.size * when (type) {
            TrackType.POSITION, TrackType.SCALE -> 12 // 3 floats
            TrackType.ROTATION -> 16 // 4 floats
            TrackType.MORPH_WEIGHTS -> 4 // 1 float
            TrackType.CUSTOM -> 4 // 1 float default
        }
    }

    /**
     * Keyframe data
     */
    data class Keyframe(
        val time: Float,
        val value: FloatArray,
        val inTangent: FloatArray? = null,
        val outTangent: FloatArray? = null
    ) {
        // Vector3 helpers
        val vector3: Vector3
            get() = Vector3(
                value.getOrElse(0) { 0f },
                value.getOrElse(1) { 0f },
                value.getOrElse(2) { 0f }
            )

        // Quaternion helpers
        val quaternion: Quaternion
            get() = Quaternion(
                value.getOrElse(0) { 0f },
                value.getOrElse(1) { 0f },
                value.getOrElse(2) { 0f },
                value.getOrElse(3) { 1f }
            )

        fun distanceTo(other: Keyframe): Float {
            var distance = 0f
            for (i in 0 until minOf(value.size, other.value.size)) {
                val diff = value[i] - other.value[i]
                distance += (diff * diff)
            }
            return sqrt(distance)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Keyframe) return false
            return time == other.time && value.contentEquals(other.value)
        }

        override fun hashCode(): Int {
            return time.hashCode() * 31 + value.contentHashCode()
        }
    }

    /**
     * Main compression function
     */
    fun compressAnimation(
        tracks: List<AnimationTrack>,
        config: CompressionConfig = CompressionConfig()
    ): Pair<List<AnimationTrack>, CompressionResult> {
        val startTime = currentTimeMillis()
        val originalSize = tracks.sumOf { it.getSize() }
        val optimizations = mutableListOf<String>()

        val compressedTracks = tracks.map { track ->
            compressTrack(track, config, optimizations)
        }

        val compressedSize = compressedTracks.sumOf { it.getSize() }
        val compressionRatio = compressedSize.toFloat() / originalSize.toFloat()
        val processingTime = currentTimeMillis() - startTime

        val result = CompressionResult(
            originalSize = originalSize,
            compressedSize = compressedSize,
            compressionRatio = compressionRatio,
            qualityLoss = QualityEstimator.estimateQualityLoss(tracks, compressedTracks),
            processingTime = processingTime,
            optimizations = optimizations
        )

        return Pair(compressedTracks, result)
    }

    /**
     * Compress individual track
     */
    private fun compressTrack(
        track: AnimationTrack,
        config: CompressionConfig,
        optimizations: MutableList<String>
    ): AnimationTrack {
        var processedTrack = track.copy()

        // Step 1: Remove redundant keyframes
        if (config.removeRedundantKeys) {
            processedTrack = KeyframeOptimizer.removeRedundant(processedTrack, config)
            optimizations.add("Removed redundant keyframes for ${track.name}")
        }

        // Step 2: Apply tolerance-based compression
        processedTrack = KeyframeOptimizer.applyToleranceCompression(processedTrack, config)
        optimizations.add("Applied tolerance compression for ${track.name}")

        // Step 3: Quaternion-specific compression
        if (track.type == AnimationTrack.TrackType.ROTATION && config.quantizeQuaternions) {
            processedTrack = QuaternionCompressor.compress(processedTrack, config)
            optimizations.add("Compressed quaternions for ${track.name}")
        }

        // Step 4: Spline compression
        if (config.useSplineCompression) {
            processedTrack = SplineCompressor.compress(processedTrack, config)
            optimizations.add("Applied spline compression for ${track.name}")
        }

        // Step 5: Curve fitting optimization
        if (config.useCurveFitting) {
            processedTrack = CurveFitter.fitCurve(processedTrack, config)
            optimizations.add("Applied curve fitting for ${track.name}")
        }

        return processedTrack
    }

    /**
     * Batch compress multiple animations
     */
    fun batchCompress(
        animations: Map<String, List<AnimationTrack>>,
        config: CompressionConfig = CompressionConfig()
    ): Map<String, Pair<List<AnimationTrack>, CompressionResult>> {
        return animations.mapValues { (_, tracks) ->
            compressAnimation(tracks, config)
        }
    }

    /**
     * Optimize compression config for target quality/size ratio
     */
    fun optimizeConfig(
        sampleTracks: List<AnimationTrack>,
        targetCompressionRatio: Float,
        maxQualityLoss: Float
    ): CompressionConfig =
        ConfigOptimizer.optimize(sampleTracks, targetCompressionRatio, maxQualityLoss)
}