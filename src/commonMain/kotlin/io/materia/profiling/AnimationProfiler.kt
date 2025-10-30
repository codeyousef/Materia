package io.materia.profiling

/**
 * Profiling utilities for animation systems.
 * Instruments skeletal animation, morph targets, state machines, and keyframe interpolation.
 */
object AnimationProfiler {

    /**
     * Profile animation mixer update
     */
    fun <T> profileMixerUpdate(deltaTime: Float, actionCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.mixer.actions", actionCount.toLong())
        PerformanceProfiler.recordCounter(
            "animation.mixer.deltaTimeMs",
            (deltaTime * 1000).toLong()
        )
        return PerformanceProfiler.measure(
            "animation.mixer.update",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile skeletal animation update
     */
    fun <T> profileSkeletalUpdate(boneCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.skeletal.boneCount", boneCount.toLong())
        return PerformanceProfiler.measure(
            "animation.skeletal.update",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile morph target blending
     */
    fun <T> profileMorphTargetBlending(targetCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.morph.targetCount", targetCount.toLong())
        return PerformanceProfiler.measure(
            "animation.morph.blend",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile IK solver
     */
    fun <T> profileIKSolver(chainLength: Int, iterations: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.ik.chainLength", chainLength.toLong())
        PerformanceProfiler.recordCounter("animation.ik.iterations", iterations.toLong())
        return PerformanceProfiler.measure("animation.ik.solve", ProfileCategory.ANIMATION, block)
    }

    /**
     * Profile state machine transition
     */
    fun <T> profileStateTransition(fromState: String, toState: String, block: () -> T): T {
        return PerformanceProfiler.measure(
            "animation.stateMachine.transition.$fromState-$toState",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile state machine update
     */
    fun <T> profileStateMachineUpdate(stateCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.stateMachine.states", stateCount.toLong())
        return PerformanceProfiler.measure(
            "animation.stateMachine.update",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile keyframe interpolation
     */
    fun <T> profileKeyframeInterpolation(
        keyframeCount: Int,
        interpolationType: String,
        block: () -> T
    ): T {
        PerformanceProfiler.recordCounter("animation.keyframes.count", keyframeCount.toLong())
        return PerformanceProfiler.measure(
            "animation.interpolation.$interpolationType",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile animation clip playback
     */
    fun <T> profileClipPlayback(clipName: String, duration: Float, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.clip.durationMs", (duration * 1000).toLong())
        return PerformanceProfiler.measure(
            "animation.clip.$clipName",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile animation blending
     */
    fun <T> profileBlending(blendMode: String, trackCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("animation.blend.tracks", trackCount.toLong())
        return PerformanceProfiler.measure(
            "animation.blend.$blendMode",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile pose generation
     */
    fun <T> profilePoseGeneration(block: () -> T): T {
        return PerformanceProfiler.measure(
            "animation.pose.generate",
            ProfileCategory.ANIMATION,
            block
        )
    }

    /**
     * Profile animation compression
     */
    fun <T> profileCompression(
        originalKeyframes: Int,
        compressedKeyframes: Int,
        block: () -> T
    ): T {
        PerformanceProfiler.recordCounter(
            "animation.compression.original",
            originalKeyframes.toLong()
        )
        PerformanceProfiler.recordCounter(
            "animation.compression.compressed",
            compressedKeyframes.toLong()
        )

        return PerformanceProfiler.measure("animation.compression", ProfileCategory.ANIMATION) {
            val result = block()

            val compressionRatio = 1f - (compressedKeyframes.toFloat() / originalKeyframes)
            PerformanceProfiler.recordCounter(
                "animation.compression.ratioPercent",
                (compressionRatio * 100).toLong()
            )

            result
        }
    }

    /**
     * Analyze animation complexity
     */
    fun analyzeAnimationComplexity(
        trackCount: Int,
        keyframeCount: Int,
        duration: Float,
        boneCount: Int = 0
    ): AnimationComplexity {
        return PerformanceProfiler.measure(
            "animation.analyzeComplexity",
            ProfileCategory.ANIMATION
        ) {
            val keyframesPerSecond = if (duration > 0) keyframeCount / duration else 0f
            val keyframesPerTrack = if (trackCount > 0) keyframeCount / trackCount else 0

            AnimationComplexity(
                trackCount = trackCount,
                keyframeCount = keyframeCount,
                duration = duration,
                boneCount = boneCount,
                keyframesPerSecond = keyframesPerSecond,
                keyframesPerTrack = keyframesPerTrack
            )
        }
    }
}

/**
 * Animation complexity analysis result
 */
data class AnimationComplexity(
    val trackCount: Int,
    val keyframeCount: Int,
    val duration: Float,
    val boneCount: Int,
    val keyframesPerSecond: Float,
    val keyframesPerTrack: Int
) {
    /**
     * Check if animation is considered complex
     */
    fun isComplex(): Boolean {
        return trackCount > 50 || keyframeCount > 1000 || boneCount > 100
    }

    /**
     * Get complexity score (0-10)
     */
    fun getComplexityScore(): Float {
        val trackScore = (trackCount / 10f).coerceAtMost(10f)
        val keyframeScore = (keyframeCount / 200f).coerceAtMost(10f)
        val boneScore = (boneCount / 20f).coerceAtMost(10f)

        return (trackScore + keyframeScore + boneScore) / 3f
    }

    /**
     * Get optimization recommendations
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (keyframesPerSecond > 60) {
            recommendations.add(
                "Consider reducing keyframe frequency (current: ${
                    io.materia.core.platform.formatFloat(
                        keyframesPerSecond,
                        1
                    )
                }/s)"
            )
        }

        if (keyframeCount > 1000) {
            recommendations.add("Consider animation compression to reduce keyframe count")
        }

        if (trackCount > 100) {
            recommendations.add("Large number of tracks ($trackCount) - consider splitting animation")
        }

        if (boneCount > 100) {
            recommendations.add("High bone count ($boneCount) - may impact performance on mobile")
        }

        return recommendations
    }
}
