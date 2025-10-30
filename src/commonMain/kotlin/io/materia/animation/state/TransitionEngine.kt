package io.materia.animation.state

import io.materia.core.platform.currentTimeMillis

/**
 * Transition engine for managing state transitions.
 * Handles transition logic, blending, and state switching.
 */
class TransitionEngine {
    private val transitions = mutableMapOf<String, MutableList<StateTransition>>()
    private var activeTransition: TransitionState? = null

    /**
     * Add a transition between states
     */
    fun addTransition(transition: StateTransition) {
        transitions.getOrPut(transition.fromState) { mutableListOf() }.add(transition)
    }

    /**
     * Check for valid transitions from current state
     */
    fun checkForTransitions(
        currentStateName: String,
        currentNormalizedTime: Float,
        parameters: Map<String, Parameter>
    ): StateTransition? {
        val availableTransitions = transitions[currentStateName] ?: return null

        // Sort by priority (higher priority first)
        val sortedTransitions = availableTransitions.sortedByDescending { it.priority }

        for (transition in sortedTransitions) {
            if (transition.canTransition(parameters, currentNormalizedTime)) {
                return transition
            }
        }

        return null
    }

    /**
     * Start a state transition
     */
    fun startTransition(transition: StateTransition): Boolean {
        // Check if current transition can be interrupted
        activeTransition?.let { currentTransition ->
            if (!currentTransition.transition.canInterrupt) {
                return false
            }
        }

        activeTransition = TransitionState(
            transition = transition,
            startTime = currentTimeMillis().toFloat()
        )

        transition.onTransitionStart?.invoke()
        return true
    }

    /**
     * Update active transition
     */
    fun updateTransition(deltaTime: Float): Boolean {
        val transition = activeTransition ?: return false

        transition.currentTime = transition.currentTime + deltaTime

        if (transition.isComplete) {
            finishTransition()
            return true
        }

        return false
    }

    /**
     * Get current transition state
     */
    fun getActiveTransition(): TransitionState? = activeTransition

    /**
     * Get transition progress (0.0 to 1.0)
     */
    fun getTransitionProgress(): Float = activeTransition?.progress ?: 0f

    /**
     * Check if currently transitioning
     */
    fun isTransitioning(): Boolean = activeTransition != null

    /**
     * Force complete current transition
     */
    fun completeTransition() {
        activeTransition?.let { transition ->
            transition.currentTime = transition.transition.duration
            finishTransition()
        }
    }

    /**
     * Get blend weights for current and target states
     */
    fun getBlendWeights(): Pair<Float, Float> {
        val transition = activeTransition ?: return Pair(1f, 0f)
        val targetWeight = transition.getBlendWeight(transition.transition.curve)
        val currentWeight = 1f - targetWeight
        return Pair(currentWeight, targetWeight)
    }

    /**
     * Finish the current transition
     */
    private fun finishTransition() {
        activeTransition?.transition?.onTransitionEnd?.invoke()
        activeTransition = null
    }

    /**
     * Remove all transitions to/from a state
     */
    fun removeStateTransitions(stateName: String) {
        transitions.remove(stateName)
        // Remove transitions TO this state
        transitions.values.forEach { transitionList ->
            transitionList.removeAll { it.toState == stateName }
        }
    }

    /**
     * Clear all transitions
     */
    fun clear() {
        activeTransition = null
        transitions.clear()
    }
}
