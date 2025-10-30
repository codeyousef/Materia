package io.materia.animation

import io.materia.animation.state.*
import io.materia.core.platform.currentTimeMillis

/**
 * Advanced Animation State Machine for complex animation flow management.
 * Supports conditional transitions, parameter-driven blending, and hierarchical states.
 *
 * T039 - StateMachine for animation state management
 */
class StateMachine(
    val name: String = "AnimationStateMachine"
) {

    // Current state management
    private var currentState: AnimationState? = null
    private var previousState: AnimationState? = null

    // Core managers
    private val states = mutableMapOf<String, AnimationState>()
    private val transitionEngine = TransitionEngine()
    private val parameterManager = ParameterManager()
    private val listenerManager = StateListenerManager()

    // Update timing
    private var lastUpdateTime = 0L
    private var deltaTime = 0f

    // Debug mode
    var debugMode: Boolean
        get() = listenerManager.debugMode
        set(value) {
            listenerManager.debugMode = value
        }

    /**
     * Animation clip reference
     */
    data class AnimationClip(
        val name: String,
        val duration: Float,
        val tracks: List<AnimationTrack> = emptyList()
    )

    /**
     * Animation track (simplified)
     */
    data class AnimationTrack(
        val property: String,
        val keyframes: List<Keyframe>
    )

    /**
     * Keyframe data
     */
    data class Keyframe(
        val time: Float,
        val value: Float,
        val inTangent: Float = 0f,
        val outTangent: Float = 0f
    )

    /**
     * Add a state to the state machine
     */
    fun addState(state: AnimationState) {
        states[state.name] = state
    }

    /**
     * Add a transition between states
     */
    fun addTransition(transition: StateTransition) {
        transitionEngine.addTransition(transition)
    }

    /**
     * Add parameter
     */
    fun addParameter(parameter: Parameter) {
        parameterManager.addParameter(parameter)
    }

    /**
     * Set parameter value
     */
    fun setParameter(name: String, value: Any) {
        parameterManager.setParameter(name, value)
    }

    /**
     * Get parameter value
     */
    fun getParameter(name: String): Parameter? = parameterManager.getParameter(name)

    /**
     * Trigger a parameter (for trigger type parameters)
     */
    fun trigger(parameterName: String) {
        parameterManager.trigger(parameterName)
    }

    /**
     * Set the current state
     */
    fun setState(stateName: String, force: Boolean = false) {
        val state = states[stateName] ?: return

        if (currentState?.name == stateName && !force) return

        val oldState = currentState
        oldState?.exit()

        previousState = oldState
        currentState = state
        state.enter()

        // Notify listeners
        listenerManager.notifyStateChange(oldState?.name, stateName, null)

        // Add to debug history
        listenerManager.addToHistory(
            StateChangeEvent(
                timestamp = currentTimeMillis(),
                fromState = oldState?.name,
                toState = stateName,
                parameters = parameterManager.getParameterValues(),
                transitionDuration = 0f
            )
        )
    }

    /**
     * Update the state machine
     */
    fun update(deltaTime: Float) {
        this.deltaTime = deltaTime
        val currentTime = currentTimeMillis()

        // Update current state
        currentState?.update(deltaTime)

        // Update transition if active
        if (transitionEngine.updateTransition(deltaTime)) {
            finishTransition()
        }

        // Check for state transitions
        if (!transitionEngine.isTransitioning()) {
            checkForTransitions()
        }

        // Reset triggers
        parameterManager.resetTriggers()

        lastUpdateTime = currentTime
    }

    /**
     * Check for valid transitions from current state
     */
    private fun checkForTransitions() {
        val currentStateName = currentState?.name ?: return
        val currentNormalizedTime = currentState?.normalizedTime ?: 0f

        val transition = transitionEngine.checkForTransitions(
            currentStateName,
            currentNormalizedTime,
            parameterManager.getAllParameters()
        )

        if (transition != null) {
            startTransition(transition)
        }
    }

    /**
     * Start a state transition
     */
    private fun startTransition(transition: StateTransition) {
        val toState = states[transition.toState] ?: return

        if (!transitionEngine.startTransition(transition)) {
            return
        }

        // Notify listeners
        listenerManager.notifyStateChange(currentState?.name, transition.toState, transition)

        // Add to debug history
        listenerManager.addToHistory(
            StateChangeEvent(
                timestamp = currentTimeMillis(),
                fromState = currentState?.name,
                toState = transition.toState,
                parameters = parameterManager.getParameterValues(),
                transitionDuration = transition.duration
            )
        )
    }

    /**
     * Finish the current transition
     */
    private fun finishTransition() {
        val transition = transitionEngine.getActiveTransition()?.transition ?: return
        val toState = states[transition.toState] ?: return

        // Switch to new state
        currentState?.exit()
        previousState = currentState
        currentState = toState
        toState.enter()
    }

    /**
     * Get current blend weights for all active states
     */
    fun getBlendWeights(): Map<String, Float> {
        val weights = mutableMapOf<String, Float>()

        currentState?.let { state ->
            val (currentWeight, _) = transitionEngine.getBlendWeights()
            weights[state.name] = state.weight * currentWeight
        }

        // Add transitioning state weight
        transitionEngine.getActiveTransition()?.let { transition ->
            val toState = states[transition.transition.toState]
            if (toState != null) {
                val (_, targetWeight) = transitionEngine.getBlendWeights()
                weights[toState.name] = toState.weight * targetWeight
            }
        }

        return weights
    }

    /**
     * Add state change listener
     */
    fun addStateChangeListener(listener: StateChangeListener) {
        listenerManager.addStateChangeListener(listener)
    }

    /**
     * Remove state change listener
     */
    fun removeStateChangeListener(listener: StateChangeListener) {
        listenerManager.removeStateChangeListener(listener)
    }

    /**
     * Get current state name
     */
    fun getCurrentStateName(): String? = currentState?.name

    /**
     * Get current state
     */
    fun getCurrentState(): AnimationState? = currentState

    /**
     * Check if currently transitioning
     */
    fun isTransitioning(): Boolean = transitionEngine.isTransitioning()

    /**
     * Get transition progress (0.0 to 1.0)
     */
    fun getTransitionProgress(): Float = transitionEngine.getTransitionProgress()

    /**
     * Force complete current transition
     */
    fun completeTransition() {
        transitionEngine.completeTransition()
        finishTransition()
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): StateMachineDebugInfo {
        return StateMachineDebugInfo(
            currentState = currentState?.name,
            previousState = previousState?.name,
            isTransitioning = isTransitioning(),
            transitionProgress = getTransitionProgress(),
            parameters = parameterManager.getParameterValues(),
            recentHistory = listenerManager.getRecentHistory(10)
        )
    }

    data class StateMachineDebugInfo(
        val currentState: String?,
        val previousState: String?,
        val isTransitioning: Boolean,
        val transitionProgress: Float,
        val parameters: Map<String, Any>,
        val recentHistory: List<StateChangeEvent>
    )

    /**
     * Clear debug history
     */
    fun clearDebugHistory() {
        listenerManager.clearHistory()
    }

    /**
     * Get all state names
     */
    fun getStateNames(): List<String> = states.keys.toList()

    /**
     * Get all parameter names
     */
    fun getParameterNames(): List<String> = parameterManager.getParameterNames()

    /**
     * Remove state
     */
    fun removeState(stateName: String) {
        states.remove(stateName)
        transitionEngine.removeStateTransitions(stateName)
    }

    /**
     * Remove parameter
     */
    fun removeParameter(parameterName: String) {
        parameterManager.removeParameter(parameterName)
    }

    /**
     * Clear all states and transitions
     */
    fun clear() {
        currentState?.exit()
        currentState = null
        previousState = null
        states.clear()
        transitionEngine.clear()
        parameterManager.clear()
        listenerManager.clear()
    }

    fun dispose() {
        clear()
    }
}
