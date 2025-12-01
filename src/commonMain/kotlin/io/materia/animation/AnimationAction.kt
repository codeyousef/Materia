package io.materia.animation

import io.materia.core.math.Euler
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3

/**
 * Animation action interface for controlling individual animation playback
 * Provides play, pause, stop, and fade functionality
 */
interface AnimationAction {
    /**
     * The animation clip being played
     */
    val clip: AnimationClip

    /**
     * Whether this action is currently running
     */
    val isRunning: Boolean

    /**
     * Whether this action is currently paused
     */
    val isPaused: Boolean

    /**
     * Current playback time in seconds
     */
    var time: Float

    /**
     * Time scale multiplier for playback speed
     */
    var timeScale: Float

    /**
     * Weight of this action in blending (0.0 to 1.0)
     */
    var weight: Float

    /**
     * Whether the animation should loop
     */
    var loop: Boolean

    /**
     * Starts playing the animation
     * @return This action for chaining
     */
    fun play(): AnimationAction

    /**
     * Stops the animation and resets to beginning
     * @return This action for chaining
     */
    fun stop(): AnimationAction

    /**
     * Pauses the animation at current time
     * @return This action for chaining
     */
    fun pause(): AnimationAction

    /**
     * Resumes the animation from current time
     * @return This action for chaining
     */
    fun resume(): AnimationAction

    /**
     * Fades in the animation over the specified duration
     * @param duration Fade duration in seconds
     * @return This action for chaining
     */
    fun fadeIn(duration: Float): AnimationAction

    /**
     * Fades out the animation over the specified duration
     * @param duration Fade duration in seconds
     * @return This action for chaining
     */
    fun fadeOut(duration: Float): AnimationAction

    /**
     * Cross-fades from this action to another action
     * @param toAction The action to fade to
     * @param duration Cross-fade duration in seconds
     * @return This action for chaining
     */
    fun crossFadeTo(toAction: AnimationAction, duration: Float): AnimationAction

    /**
     * Updates the action with the given delta time
     * @param deltaTime Time elapsed since last update in seconds
     */
    fun update(deltaTime: Float)

    /**
     * Disposes of action resources
     */
    fun dispose()
}

/**
 * Clip action implementation for controlling animation clip playback
 */
interface ClipAction : AnimationAction {
    /**
     * The mixer this action belongs to
     */
    val mixer: AnimationMixer

    /**
     * Whether this action has been disposed
     */
    val isDisposed: Boolean
}

/**
 * Default implementation of ClipAction
 */
class DefaultClipAction(
    override val clip: AnimationClip,
    override val mixer: AnimationMixer
) : ClipAction {

    private var _isRunning = false
    private var _isPaused = false
    private var _isDisposed = false

    override val isRunning: Boolean get() = _isRunning && !_isPaused
    override val isPaused: Boolean get() = _isPaused
    override val isDisposed: Boolean get() = _isDisposed

    override var time: Float = 0f
    override var timeScale: Float = 1f
    override var weight: Float = 1f
    override var loop: Boolean = true

    private var fadeDirection = 0f // -1 for fade out, 1 for fade in, 0 for no fade
    private var fadeDuration = 0f
    private var fadeTime = 0f
    private var fadeStartWeight = 0f
    private var fadeInitialized = false

    override fun play(): AnimationAction {
        if (_isDisposed) return this

        _isRunning = true
        _isPaused = false
        return this
    }

    override fun stop(): AnimationAction {
        if (_isDisposed) return this

        _isRunning = false
        _isPaused = false
        time = 0f
        weight = 1f
        fadeDirection = 0f
        fadeInitialized = false
        return this
    }

    override fun pause(): AnimationAction {
        if (_isDisposed) return this

        _isPaused = true
        return this
    }

    override fun resume(): AnimationAction {
        if (_isDisposed) return this

        _isPaused = false
        return this
    }

    override fun fadeIn(duration: Float): AnimationAction {
        if (_isDisposed) return this

        startFade(1f, duration)
        play()
        return this
    }

    override fun fadeOut(duration: Float): AnimationAction {
        if (_isDisposed) return this

        startFade(-1f, duration)

        // Ensure the action is running so fade can be processed
        if (!_isRunning) {
            _isRunning = true
            _isPaused = false
        }

        return this
    }

    override fun crossFadeTo(toAction: AnimationAction, duration: Float): AnimationAction {
        if (_isDisposed) return this

        fadeOut(duration)
        toAction.fadeIn(duration)
        return this
    }

    private fun startFade(direction: Float, duration: Float) {
        fadeDirection = direction
        fadeDuration = duration
        fadeTime = 0f
        fadeInitialized = false

        if (direction > 0) {
            // Fade in: start from 0
            fadeStartWeight = 0f
            weight = 0f
            fadeInitialized = true
        }
        // For fade out, we'll capture the start weight on first update
    }

    override fun update(deltaTime: Float) {
        if (_isDisposed || !_isRunning || _isPaused) return

        // Update animation time
        time += deltaTime * timeScale

        // Handle looping
        if (loop && time > clip.duration) {
            time = time % clip.duration
        } else if (!loop && time > clip.duration) {
            time = clip.duration
            stop()
            return
        }

        // Update fading
        if (fadeDirection != 0f) {
            // Initialize fade start weight on first update for fade out
            if (!fadeInitialized) {
                fadeStartWeight = weight
                fadeInitialized = true
            }

            fadeTime += deltaTime
            val fadeProgress = (fadeTime / fadeDuration).coerceIn(0f, 1f)

            weight = when {
                fadeDirection > 0 -> fadeProgress // Fade in: 0 -> 1
                else -> fadeStartWeight * (1f - fadeProgress) // Fade out: start weight -> 0
            }

            if (fadeProgress >= 1f) {
                fadeDirection = 0f
                val shouldStop = weight <= 0f
                if (shouldStop) {
                    // Stop the action but preserve the final weight (0)
                    _isRunning = false
                    _isPaused = false
                    time = 0f
                    // Don't reset weight - keep it at 0 for fade out completion
                }
            }
        }

        // Apply animation to the target object
        applyAnimation()
    }

    private fun applyAnimation() {
        if (weight <= 0f) return

        // Apply animation tracks to the target object
        clip.tracks.forEach { track ->
            val value = interpolateTrack(track, time)
            applyTrackValue(track.name, value)
        }
    }

    private fun interpolateTrack(track: KeyframeTrack, time: Float): FloatArray {
        val times = track.times
        val values = track.values

        // Check for empty times to avoid division by zero
        if (times.isEmpty()) {
            return floatArrayOf()
        }

        val valuesPerKey = if (times.isNotEmpty()) values.size / times.size else 0

        // Find the keyframe indices
        var index = 0
        for (i in times.indices) {
            if (time >= times[i]) {
                index = i
            } else {
                break
            }
        }

        val nextIndex = (index + 1).coerceAtMost(times.lastIndex)

        if (index == nextIndex) {
            // Return the exact value
            val startIdx = index * valuesPerKey
            return values.sliceArray(startIdx until startIdx + valuesPerKey)
        }

        // Interpolate between keyframes
        val t1 = times[index]
        val t2 = times[nextIndex]
        val alpha = ((time - t1) / (t2 - t1)).coerceIn(0f, 1f)

        val startIdx1 = index * valuesPerKey
        val startIdx2 = nextIndex * valuesPerKey

        val result = FloatArray(valuesPerKey)
        for (i in 0 until valuesPerKey) {
            if (startIdx1 + i < values.size && startIdx2 + i < values.size) {
                val v1 = values[startIdx1 + i]
                val v2 = values[startIdx2 + i]
                result[i] = when (track.interpolation) {
                    InterpolationType.LINEAR -> v1 + (v2 - v1) * alpha
                    InterpolationType.STEP -> v1
                    InterpolationType.CUBIC_SPLINE -> {
                        // Simplified cubic interpolation
                        v1 + (v2 - v1) * alpha * alpha * (3f - 2f * alpha)
                    }
                }
            }
        }

        return result
    }

    private fun applyTrackValue(trackName: String, value: FloatArray) {
        // Apply the animated value to the target object
        val root = mixer.root

        // Parse track name to extract property and potential sub-paths
        val parts = trackName.split(".")
        val propertyName = parts.lastOrNull() ?: return

        // Handle standard Three.js-style track names
        when {
            propertyName == "position" || trackName.endsWith(".position") -> {
                if (value.size >= 3) {
                    val blendedPos = root.position.clone()
                    val newPos = Vector3(value[0], value[1], value[2])

                    // Apply with blending based on weight
                    if (weight >= 1f) {
                        root.position.copy(newPos)
                    } else if (weight > 0f) {
                        root.position.copy(blendedPos.lerp(newPos, weight))
                    }
                }
            }

            propertyName == "quaternion" || trackName.endsWith(".quaternion") -> {
                if (value.size >= 4) {
                    val blendedRot = root.quaternion.clone()
                    val newRot = Quaternion(value[0], value[1], value[2], value[3]).normalize()

                    // Apply with blending based on weight
                    if (weight >= 1f) {
                        root.quaternion.copy(newRot)
                    } else if (weight > 0f) {
                        root.quaternion.copy(blendedRot.slerp(newRot, weight))
                    }
                }
            }

            propertyName == "rotation" || trackName.endsWith(".rotation") -> {
                // Euler rotation support
                if (value.size >= 3) {
                    val euler = Euler(value[0], value[1], value[2])
                    val newRot = Quaternion().setFromEuler(euler)
                    val blendedRot = root.quaternion.clone()

                    // Apply with blending based on weight
                    if (weight >= 1f) {
                        root.quaternion.copy(newRot)
                    } else if (weight > 0f) {
                        root.quaternion.copy(blendedRot.slerp(newRot, weight))
                    }
                }
            }

            propertyName == "scale" || trackName.endsWith(".scale") -> {
                if (value.size >= 3) {
                    val blendedScale = root.scale.clone()
                    val newScale = Vector3(value[0], value[1], value[2])

                    // Apply with blending based on weight
                    if (weight >= 1f) {
                        root.scale.copy(newScale)
                    } else if (weight > 0f) {
                        root.scale.copy(blendedScale.lerp(newScale, weight))
                    }
                }
            }

            // Support for morph targets
            propertyName.startsWith("morphTargetInfluences[") -> {
                val index = propertyName.substringAfter("[").substringBefore("]").toIntOrNull()
                if (index != null && value.isNotEmpty()) {
                    // Morph target influences stored in userData for mesh renderer access
                    @Suppress("UNCHECKED_CAST")
                    val morphTargets = root.userData["morphTargetInfluences"] as? MutableList<Float>
                        ?: mutableListOf<Float>().also {
                            root.userData["morphTargetInfluences"] = it
                        }

                    while (morphTargets.size <= index) {
                        morphTargets.add(0f)
                    }

                    val currentValue = morphTargets[index]
                    morphTargets[index] = if (weight >= 1f) {
                        value[0]
                    } else {
                        currentValue + (value[0] - currentValue) * weight
                    }
                }
            }

            // Support for material properties
            propertyName == "opacity" || trackName.contains("material.opacity") -> {
                if (value.isNotEmpty()) {
                    @Suppress("UNCHECKED_CAST")
                    val materials = root.userData["materials"] as? MutableMap<String, Any>
                        ?: mutableMapOf<String, Any>().also { root.userData["materials"] = it }

                    val currentOpacity = (materials["opacity"] as? Float) ?: 1f
                    materials["opacity"] = if (weight >= 1f) {
                        value[0]
                    } else {
                        currentOpacity + (value[0] - currentOpacity) * weight
                    }
                }
            }

            // Support for visibility
            propertyName == "visible" -> {
                if (value.isNotEmpty()) {
                    // Visibility is typically binary, but we can use weight as threshold
                    root.visible = value[0] > 0.5f || (weight < 0.5f && root.visible)
                }
            }

            // Support for custom properties via userData
            else -> {
                // Store in userData for custom property animation
                @Suppress("UNCHECKED_CAST")
                val animatedProps =
                    root.userData["animatedProperties"] as? MutableMap<String, FloatArray>
                        ?: mutableMapOf<String, FloatArray>().also {
                            root.userData["animatedProperties"] = it
                        }

                if (weight >= 1f) {
                    animatedProps[trackName] = value.copyOf()
                } else if (weight > 0f) {
                    val current = animatedProps[trackName] ?: FloatArray(value.size) { 0f }
                    val blended = FloatArray(value.size) { i ->
                        current[i] + (value[i] - current[i]) * weight
                    }
                    animatedProps[trackName] = blended
                }
            }
        }
    }

    override fun dispose() {
        if (_isDisposed) return

        stop()
        _isDisposed = true
    }
}