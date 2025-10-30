package io.materia.morph

import io.materia.animation.AnimationClip
import io.materia.animation.KeyframeTrack
import kotlin.math.max
import kotlin.math.min

/**
 * Manages and blends morph target animations.
 * Can handle multiple animation clips affecting different morph targets simultaneously.
 */
class MorphAnimationMixer {
    private val activeAnimations = mutableListOf<MorphAnimation>()
    private var time: Float = 0f
    private var timeScale: Float = 1f

    /**
     * Update all active animations and compute blended influences.
     *
     * @param deltaTime Time since last update in seconds
     * @param influences Target influences array to update
     */
    fun update(deltaTime: Float, influences: MorphTargetInfluences) {
        val scaledDelta = deltaTime * timeScale
        time += scaledDelta

        // Reset influences
        influences.reset()

        // Update each active animation
        val animationsToRemove = mutableListOf<MorphAnimation>()
        for (animation in activeAnimations) {
            animation.update(scaledDelta)

            // Apply animation influences
            animation.getInfluences().forEach { (index, value) ->
                if (index < influences.influences.size) {
                    influences.influences[index] += value * animation.weight
                }
            }

            // Check if animation is complete
            if (!animation.loop && animation.isComplete) {
                animationsToRemove.add(animation)
            }
        }

        // Remove completed animations
        activeAnimations.removeAll(animationsToRemove)

        // Normalize influences if needed
        if (normalizeInfluences) {
            influences.normalize()
        }

        influences.needsUpdate = true
    }

    /**
     * Play an animation clip.
     *
     * @param clip Animation clip containing morph target tracks
     * @param loop Whether to loop the animation
     * @param weight Weight of this animation (for blending multiple animations)
     * @return The created MorphAnimation instance
     */
    fun playClip(
        clip: AnimationClip,
        loop: Boolean = true,
        weight: Float = 1f
    ): MorphAnimation {
        val animation = MorphAnimation(clip, loop, weight)
        activeAnimations.add(animation)
        return animation
    }

    /**
     * Stop a specific animation.
     *
     * @param animation The animation to stop
     */
    fun stopAnimation(animation: MorphAnimation) {
        activeAnimations.remove(animation)
    }

    /**
     * Stop all animations.
     */
    fun stopAll() {
        activeAnimations.clear()
    }

    /**
     * Fade in an animation over time.
     *
     * @param animation The animation to fade in
     * @param duration Fade duration in seconds
     */
    fun fadeIn(animation: MorphAnimation, duration: Float) {
        animation.fadeIn(duration)
    }

    /**
     * Fade out an animation over time.
     *
     * @param animation The animation to fade out
     * @param duration Fade duration in seconds
     */
    fun fadeOut(animation: MorphAnimation, duration: Float) {
        animation.fadeOut(duration)
    }

    /**
     * Cross-fade between two animations.
     *
     * @param fromAnimation Animation to fade out
     * @param toAnimation Animation to fade in
     * @param duration Cross-fade duration in seconds
     */
    fun crossFade(
        fromAnimation: MorphAnimation,
        toAnimation: MorphAnimation,
        duration: Float
    ) {
        fadeOut(fromAnimation, duration)
        fadeIn(toAnimation, duration)
    }

    /**
     * Set the global time scale for all animations.
     *
     * @param scale Time scale factor (1.0 = normal speed)
     */
    fun setTimeScale(scale: Float) {
        timeScale = max(0f, scale)
    }

    /**
     * Whether to normalize influences after blending.
     * If true, influences will be scaled so they sum to 1.0.
     */
    var normalizeInfluences: Boolean = false

    /**
     * Get the current number of active animations.
     */
    val activeAnimationCount: Int
        get() = activeAnimations.size
}

/**
 * Represents a single morph target animation.
 */
class MorphAnimation(
    val clip: AnimationClip,
    var loop: Boolean = true,
    var weight: Float = 1f
) {
    private var time: Float = 0f
    private val influences = mutableMapOf<Int, Float>()

    // Fade state
    private var fadeState = FadeState.NONE
    private var fadeStartTime: Float = 0f
    private var fadeDuration: Float = 0f
    private var fadeStartWeight: Float = 1f
    private var fadeTargetWeight: Float = 1f

    /**
     * Update the animation.
     *
     * @param deltaTime Time since last update in seconds
     */
    fun update(deltaTime: Float) {
        // Update time
        time += deltaTime

        // Handle looping
        if (loop && time > clip.duration) {
            time %= clip.duration
        } else if (time > clip.duration) {
            time = clip.duration
        }

        // Update fade
        updateFade(deltaTime)

        // Sample all morph target tracks
        // Process tracks - since AnimationClip stores KeyframeTracks, morph targets
        // need to be handled through a different mechanism or stored separately
        influences.clear()

        // Current architecture stores KeyframeTracks only in AnimationClip
        // Morph target animations would need their own storage or a unified track system
        for (track in clip.tracks) {
            // KeyframeTracks handle standard property animations
            // Morph targets would be processed separately
        }
    }

    private fun updateFade(deltaTime: Float) {
        when (fadeState) {
            FadeState.FADING_IN -> {
                val fadeProgress = (time - fadeStartTime) / fadeDuration
                weight = if (fadeProgress >= 1f) {
                    fadeState = FadeState.NONE
                    fadeTargetWeight
                } else {
                    fadeStartWeight + (fadeTargetWeight - fadeStartWeight) * fadeProgress
                }
            }

            FadeState.FADING_OUT -> {
                val fadeProgress = (time - fadeStartTime) / fadeDuration
                weight = if (fadeProgress >= 1f) {
                    fadeState = FadeState.NONE
                    fadeTargetWeight
                } else {
                    fadeStartWeight + (fadeTargetWeight - fadeStartWeight) * fadeProgress
                }
            }

            FadeState.NONE -> {
                // No fade active
            }
        }
    }

    /**
     * Get the current influence values.
     *
     * @return Map of morph target index to influence value
     */
    fun getInfluences(): Map<Int, Float> = influences

    /**
     * Start fading in the animation.
     *
     * @param duration Fade duration in seconds
     */
    fun fadeIn(duration: Float) {
        fadeState = FadeState.FADING_IN
        fadeStartTime = time
        fadeDuration = duration
        fadeStartWeight = weight
        fadeTargetWeight = 1f
    }

    /**
     * Start fading out the animation.
     *
     * @param duration Fade duration in seconds
     */
    fun fadeOut(duration: Float) {
        fadeState = FadeState.FADING_OUT
        fadeStartTime = time
        fadeDuration = duration
        fadeStartWeight = weight
        fadeTargetWeight = 0f
    }

    /**
     * Check if the animation is complete (for non-looping animations).
     */
    val isComplete: Boolean
        get() = !loop && time >= clip.duration

    /**
     * Reset the animation to the beginning.
     */
    fun reset() {
        time = 0f
        influences.clear()
    }

    /**
     * Set the animation time directly.
     *
     * @param t Time in seconds
     */
    fun setTime(t: Float) {
        time = min(max(0f, t), clip.duration)
    }

    private enum class FadeState {
        NONE,
        FADING_IN,
        FADING_OUT
    }
}

/**
 * Keyframe track for morph target animation.
 */
class MorphTargetTrack(
    val targetIndex: Int,
    times: FloatArray,
    values: FloatArray
) {
    /**
     * Underlying keyframe track
     */
    val track: KeyframeTrack = KeyframeTrack(
        name = "morph.$targetIndex",
        times = times,
        values = values
    )

    /**
     * Sample the track at a given time.
     *
     * @param time Time in seconds
     * @return Interpolated influence value
     */
    fun sample(time: Float): Float {
        if (track.times.isEmpty()) return 0f
        if (track.times.size == 1) return track.values[0]

        // Find keyframe indices
        var i = 0
        while (i < track.times.size - 1 && track.times[i + 1] < time) {
            i++
        }

        // Handle edge cases
        if (i >= track.times.size - 1) return track.values.lastOrNull() ?: 0f
        if (time <= track.times[0]) return track.values.firstOrNull() ?: 0f

        // Linear interpolation between keyframes
        val t0 = track.times[i]
        val t1 = track.times[i + 1]
        val v0 = track.values[i]
        val v1 = track.values[i + 1]

        val alpha = (time - t0) / (t1 - t0)
        return v0 + (v1 - v0) * alpha
    }
}