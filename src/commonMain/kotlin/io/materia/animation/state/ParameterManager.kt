package io.materia.animation.state

import kotlin.math.abs

/**
 * Parameter manager for state machine parameters.
 * Handles parameter storage, updates, and condition evaluation.
 */
class ParameterManager {
    private val parameters = mutableMapOf<String, Parameter>()

    /**
     * Add parameter
     */
    fun addParameter(parameter: Parameter) {
        parameters[parameter.name] = parameter
    }

    /**
     * Set parameter value
     */
    fun setParameter(name: String, value: Any) {
        parameters[name]?.setValue(value)
    }

    /**
     * Get parameter value
     */
    fun getParameter(name: String): Parameter? = parameters[name]

    /**
     * Get all parameters
     */
    fun getAllParameters(): Map<String, Parameter> = parameters

    /**
     * Trigger a parameter (for trigger type parameters)
     */
    fun trigger(parameterName: String) {
        val param = parameters[parameterName]
        if (param?.type == Parameter.Type.TRIGGER) {
            param.setValue(true)
        }
    }

    /**
     * Reset all trigger parameters
     */
    fun resetTriggers() {
        parameters.values.forEach { param ->
            if (param.type == Parameter.Type.TRIGGER) {
                param.resetTrigger()
            }
        }
    }

    /**
     * Get parameter names
     */
    fun getParameterNames(): List<String> = parameters.keys.toList()

    /**
     * Remove parameter
     */
    fun removeParameter(parameterName: String) {
        parameters.remove(parameterName)
    }

    /**
     * Clear all parameters
     */
    fun clear() {
        parameters.clear()
    }

    /**
     * Get parameter values as map
     */
    fun getParameterValues(): Map<String, Any> {
        return parameters.mapValues { (_, param) ->
            when (param.type) {
                Parameter.Type.BOOL -> param.boolValue
                Parameter.Type.INT -> param.intValue
                Parameter.Type.FLOAT -> param.floatValue
                Parameter.Type.TRIGGER -> param.triggered
            }
        }
    }
}

/**
 * State machine parameters
 */
data class Parameter(
    val name: String,
    val type: Type,
    private var _value: Any = getDefaultValue(type)
) {
    enum class Type {
        BOOL,
        INT,
        FLOAT,
        TRIGGER
    }

    val boolValue: Boolean
        get() = _value as? Boolean ?: false

    val intValue: Int
        get() = _value as? Int ?: 0

    val floatValue: Float
        get() = _value as? Float ?: 0f

    var triggered: Boolean = false
        private set

    fun setValue(value: Any) {
        when (type) {
            Type.BOOL -> _value = value as? Boolean ?: false
            Type.INT -> _value = value as? Int ?: 0
            Type.FLOAT -> _value = value as? Float ?: 0f
            Type.TRIGGER -> {
                triggered = value as? Boolean ?: false
            }
        }
    }

    fun resetTrigger() {
        if (type == Type.TRIGGER) {
            triggered = false
        }
    }

    companion object {
        fun getDefaultValue(type: Type): Any {
            return when (type) {
                Type.BOOL -> false
                Type.INT -> 0
                Type.FLOAT -> 0f
                Type.TRIGGER -> false
            }
        }
    }
}

/**
 * Transition condition types
 */
sealed class TransitionCondition {
    abstract fun evaluate(parameters: Map<String, Parameter>): Boolean

    data class ParameterEquals(
        val parameterName: String,
        val value: Any
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            val param = parameters[parameterName] ?: return false
            return when (param.type) {
                Parameter.Type.BOOL -> param.boolValue == (value as? Boolean ?: false)
                Parameter.Type.INT -> param.intValue == (value as? Int ?: 0)
                Parameter.Type.FLOAT -> abs(param.floatValue - (value as? Float ?: 0f)) < 0.001f
                Parameter.Type.TRIGGER -> param.triggered
            }
        }
    }

    data class ParameterGreater(
        val parameterName: String,
        val threshold: Float
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            val param = parameters[parameterName] ?: return false
            return when (param.type) {
                Parameter.Type.FLOAT -> param.floatValue > threshold
                Parameter.Type.INT -> param.intValue > threshold
                else -> false
            }
        }
    }

    data class ParameterLess(
        val parameterName: String,
        val threshold: Float
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            val param = parameters[parameterName] ?: return false
            return when (param.type) {
                Parameter.Type.FLOAT -> param.floatValue < threshold
                Parameter.Type.INT -> param.intValue < threshold
                else -> false
            }
        }
    }

    data class And(
        val conditions: List<TransitionCondition>
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            return conditions.all { it.evaluate(parameters) }
        }
    }

    data class Or(
        val conditions: List<TransitionCondition>
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            return conditions.any { it.evaluate(parameters) }
        }
    }

    data class Not(
        val condition: TransitionCondition
    ) : TransitionCondition() {
        override fun evaluate(parameters: Map<String, Parameter>): Boolean {
            return !condition.evaluate(parameters)
        }
    }
}
