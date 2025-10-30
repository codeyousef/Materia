/**
 * Physics constraint implementations for mechanical joints and connections
 * Provides comprehensive constraint system for realistic physics simulation
 */
package io.materia.physics

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.physics.constraints.ConeTwistConstraintImpl
import io.materia.physics.constraints.Generic6DofConstraintImpl
import io.materia.physics.constraints.HingeConstraintImpl
import io.materia.physics.constraints.PointToPointConstraintImpl
import io.materia.physics.constraints.SliderConstraintImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Base constraint implementation with common functionality
 */
abstract class PhysicsConstraintImpl(
    override val id: String,
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?
) : PhysicsConstraint {
    private val _enabled = MutableStateFlow(true)
    override var enabled: Boolean
        get() = _enabled.value
        set(value) {
            _enabled.value = value
        }

    override var breakingThreshold: Float = Float.POSITIVE_INFINITY

    private val _appliedImpulse = MutableStateFlow(0f)
    private val constraintParams = mutableMapOf<Pair<ConstraintParam, Int>, Float>()

    /**
     * Constraint state tracking
     */
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()
    val appliedImpulseFlow: StateFlow<Float> = _appliedImpulse.asStateFlow()

    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {
        constraintParams[Pair(param, axis)] = value
        onParameterChanged(param, value, axis)
    }

    override fun getParam(param: ConstraintParam, axis: Int): Float {
        return constraintParams[Pair(param, axis)] ?: getDefaultParamValue(param)
    }

    override fun getAppliedImpulse(): Float = _appliedImpulse.value
    override fun isEnabled(): Boolean = _enabled.value

    override fun getInfo(info: ConstraintInfo) {
        // Default constraint info - subclasses can override
        // For now, this is a placeholder - concrete implementations would populate the info
    }

    /**
     * Update applied impulse (called by physics solver)
     */
    internal fun updateAppliedImpulse(impulse: Float) {
        _appliedImpulse.value = impulse
        // Check for constraint breaking
        if (abs(impulse) > breakingThreshold) {
            enabled = false
        }
    }

    /**
     * Called when constraint parameters change - subclasses can override
     */
    protected open fun onParameterChanged(param: ConstraintParam, value: Float, axis: Int) {
        // Default implementation does nothing
    }

    /**
     * Get default value for constraint parameter
     */
    protected open fun getDefaultParamValue(param: ConstraintParam): Float {
        return when (param) {
            ConstraintParam.ERP -> 0.2f
            ConstraintParam.STOP_ERP -> 0.1f
            ConstraintParam.CFM -> 0f
            ConstraintParam.STOP_CFM -> 0f
            ConstraintParam.LINEAR_LOWER_LIMIT -> -Float.MAX_VALUE
            ConstraintParam.LINEAR_UPPER_LIMIT -> Float.MAX_VALUE
            ConstraintParam.ANGULAR_LOWER_LIMIT -> -Float.MAX_VALUE
            ConstraintParam.ANGULAR_UPPER_LIMIT -> Float.MAX_VALUE
            ConstraintParam.TARGET_VELOCITY -> 0f
            ConstraintParam.MAX_MOTOR_FORCE -> 0f
        }
    }

    /**
     * Calculate relative transform between bodies
     */
    protected fun getRelativeTransform(): Matrix4 {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY
        return transformA.inverse() * transformB
    }

    /**
     * Check if constraint is valid (both bodies exist and are properly configured)
     */
    fun isValid(): Boolean {
        return bodyA.isActive() && (bodyB?.isActive() != false)
    }
}

/**
 * Factory for creating physics constraints
 */
object PhysicsConstraintFactory {
    fun createPointToPointConstraint(
        id: String,
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3
    ): PointToPointConstraint = PointToPointConstraintImpl(id, bodyA, bodyB, pivotA, pivotB)

    fun createHingeConstraint(
        id: String,
        bodyA: RigidBody,
        bodyB: RigidBody?,
        pivotA: Vector3,
        pivotB: Vector3,
        axisA: Vector3,
        axisB: Vector3
    ): HingeConstraint = HingeConstraintImpl(id, bodyA, bodyB, pivotA, pivotB, axisA, axisB)

    fun createSliderConstraint(
        id: String,
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): SliderConstraint = SliderConstraintImpl(id, bodyA, bodyB, frameA, frameB)

    fun createConeTwistConstraint(
        id: String,
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): ConeTwistConstraint = ConeTwistConstraintImpl(id, bodyA, bodyB, frameA, frameB)

    fun createGeneric6DofConstraint(
        id: String,
        bodyA: RigidBody,
        bodyB: RigidBody?,
        frameA: Matrix4,
        frameB: Matrix4
    ): Generic6DofConstraint = Generic6DofConstraintImpl(id, bodyA, bodyB, frameA, frameB)
}
