package io.materia.animation.state

/**
 * State listener system for state machine events.
 * Handles callbacks and event notifications for state changes.
 */
class StateListenerManager {
    private val stateListeners = mutableListOf<StateChangeListener>()
    private val debugHistory = mutableListOf<StateChangeEvent>()
    var debugMode = false

    /**
     * Add state change listener
     */
    fun addStateChangeListener(listener: StateChangeListener) {
        stateListeners.add(listener)
    }

    /**
     * Remove state change listener
     */
    fun removeStateChangeListener(listener: StateChangeListener) {
        stateListeners.remove(listener)
    }

    /**
     * Notify listeners of state change
     */
    fun notifyStateChange(fromState: String?, toState: String, transition: StateTransition?) {
        stateListeners.forEach { listener ->
            listener.onStateChanged(fromState, toState, transition)
        }
    }

    /**
     * Add to debug history
     */
    fun addToHistory(event: StateChangeEvent) {
        if (debugMode) {
            debugHistory.add(event)
        }
    }

    /**
     * Get recent history
     */
    fun getRecentHistory(count: Int = 10): List<StateChangeEvent> {
        return debugHistory.takeLast(count)
    }

    /**
     * Clear debug history
     */
    fun clearHistory() {
        debugHistory.clear()
    }

    /**
     * Clear all listeners
     */
    fun clear() {
        stateListeners.clear()
        debugHistory.clear()
    }
}

/**
 * State change listener interface
 */
fun interface StateChangeListener {
    fun onStateChanged(fromState: String?, toState: String, transition: StateTransition?)
}
