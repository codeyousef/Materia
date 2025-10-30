package io.materia.animation.state

import io.materia.animation.StateMachine.AnimationClip
import io.materia.animation.StateMachine.AnimationTrack
import io.materia.animation.StateMachine.Keyframe

/**
 * State type definitions for animation state machine.
 * Contains state data structures, blend modes, and transition curves.
 */

/**
 * Animation state definition
 */
data class AnimationState(
    val name: String,
    val animations: List<AnimationClip> = emptyList(),
    val blendMode: BlendMode = BlendMode.REPLACE,
    val speed: Float = 1f,
    val loop: Boolean = true,
    val weight: Float = 1f,
    val onEnter: (() -> Unit)? = null,
    val onExit: (() -> Unit)? = null,
    val onUpdate: ((Float) -> Unit)? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
    var isActive = false
    var currentTime = 0f
    var normalizedTime = 0f

    enum class BlendMode {
        REPLACE,    // Replace previous animation
        ADDITIVE,   // Add to previous animation
        MULTIPLY,   // Multiply with previous
        SUBTRACT    // Subtract from previous
    }

    fun update(deltaTime: Float) {
        if (!isActive) return

        currentTime = currentTime + deltaTime * speed

        // Calculate normalized time
        val totalDuration = animations.maxOfOrNull { it.duration } ?: 0f
        if (totalDuration > 0f) {
            normalizedTime = if (loop) {
                (currentTime % totalDuration) / totalDuration
            } else {
                (currentTime / totalDuration).coerceAtMost(1f)
            }
        }

        onUpdate?.invoke(normalizedTime)
    }

    fun reset() {
        currentTime = 0f
        normalizedTime = 0f
    }

    fun enter() {
        isActive = true
        reset()
        onEnter?.invoke()
    }

    fun exit() {
        isActive = false
        onExit?.invoke()
    }
}

/**
 * Transition curves for smooth blending
 */
enum class TransitionCurve {
    LINEAR,
    SMOOTH,
    EASE_IN,
    EASE_OUT,
    EASE_IN_OUT,
    SNAP
}

/**
 * Current transition state
 */
data class TransitionState(
    val transition: StateTransition,
    val startTime: Float,
    var currentTime: Float = 0f
) {
    val progress: Float
        get() = if (transition.duration > 0f) {
            (currentTime / transition.duration).coerceIn(0f, 1f)
        } else 1f

    val isComplete: Boolean
        get() = progress >= 1f

    fun getBlendWeight(curve: TransitionCurve): Float {
        return when (curve) {
            TransitionCurve.LINEAR -> progress
            TransitionCurve.SMOOTH -> progress * progress * (3f - (2f * progress))
            TransitionCurve.EASE_IN -> progress * progress
            TransitionCurve.EASE_OUT -> 1f - (1f - progress) * (1f - progress)
            TransitionCurve.EASE_IN_OUT -> {
                if (progress < 0.5f) {
                    2f * (progress * progress)
                } else {
                    1f - 2f * (1f - progress) * (1f - progress)
                }
            }

            TransitionCurve.SNAP -> if (progress > 0.5f) 1f else 0f
        }
    }
}

/**
 * State transition definition
 */
data class StateTransition(
    val fromState: String,
    val toState: String,
    val conditions: List<TransitionCondition>,
    val duration: Float = 0.25f,
    val curve: TransitionCurve = TransitionCurve.SMOOTH,
    val priority: Int = 0,
    val canInterrupt: Boolean = true,
    val exitTime: Float? = null, // Normalized time to exit
    val onTransitionStart: (() -> Unit)? = null,
    val onTransitionEnd: (() -> Unit)? = null
) {
    fun canTransition(parameters: Map<String, Parameter>, currentNormalizedTime: Float): Boolean {
        // Check exit time condition
        exitTime?.let { exitNormTime ->
            if (currentNormalizedTime < exitNormTime) return false
        }

        // Check all conditions
        return conditions.all { condition ->
            condition.evaluate(parameters)
        }
    }
}

/**
 * State change event for debugging
 */
data class StateChangeEvent(
    val timestamp: Long,
    val fromState: String?,
    val toState: String,
    val parameters: Map<String, Any>,
    val transitionDuration: Float
)
