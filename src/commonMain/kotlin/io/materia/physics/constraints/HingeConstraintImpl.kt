/**
 * Hinge constraint implementation (revolute joint)
 */
package io.materia.physics.constraints

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.physics.ConstraintParam
import io.materia.physics.HingeConstraint
import io.materia.physics.PhysicsConstraintImpl
import io.materia.physics.RigidBody
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Allows rotation around a single axis while constraining translation
 */
class HingeConstraintImpl(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3,
    override val axisA: Vector3,
    override val axisB: Vector3
) : PhysicsConstraintImpl(id, bodyA, bodyB), HingeConstraint {
    private var _lowerLimit = -Float.MAX_VALUE
    private var _upperLimit = Float.MAX_VALUE
    private var _enableAngularMotor = false
    private var _targetVelocity = 0f
    private var _maxMotorImpulse = 0f

    // Hinge state
    private var _currentAngle = 0f
    private var _referenceSign = 1f
    private var _angularOnly = false
    private var _solveLimit = false

    override var lowerLimit: Float
        get() = _lowerLimit
        set(value) {
            _lowerLimit = value
            updateLimitState()
        }

    override var upperLimit: Float
        get() = _upperLimit
        set(value) {
            _upperLimit = value
            updateLimitState()
        }

    override var enableAngularMotor: Boolean
        get() = _enableAngularMotor
        set(value) {
            _enableAngularMotor = value
        }

    override var targetVelocity: Float
        get() = _targetVelocity
        set(value) {
            _targetVelocity = value
        }

    override var maxMotorImpulse: Float
        get() = _maxMotorImpulse
        set(value) {
            _maxMotorImpulse = maxOf(0f, value)
        }

    override fun setLimit(
        low: Float,
        high: Float,
        softness: Float,
        biasFactor: Float,
        relaxationFactor: Float
    ) {
        _lowerLimit = low
        _upperLimit = high
        setParam(ConstraintParam.STOP_ERP, biasFactor, -1)
        setParam(ConstraintParam.STOP_CFM, relaxationFactor, -1)
        updateLimitState()
    }

    override fun enableMotor(enable: Boolean) {
        _enableAngularMotor = enable
    }

    override fun setMotorTarget(targetAngle: Float, deltaTime: Float) {
        val currentAngle = getHingeAngle()
        val angleDiff = normalizeAngle(targetAngle - currentAngle)
        _targetVelocity = angleDiff / deltaTime
    }

    override fun getHingeAngle(): Float {
        updateCurrentAngle()
        return _currentAngle
    }

    private fun updateCurrentAngle() {
        // Get world-space axes
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldAxisA = transformA.transformDirection(axisA.normalized())
        val worldAxisB = transformB.transformDirection(axisB.normalized())

        // Get perpendicular vectors for angle calculation
        val perpA = getPerpendicularVector(worldAxisA)
        val perpB = getPerpendicularVector(worldAxisB)

        // Project perpendicular vectors onto plane perpendicular to hinge axis
        val projectedPerpA = (perpA - worldAxisA * perpA.dot(worldAxisA)).normalized()
        val projectedPerpB = (perpB - worldAxisA * perpB.dot(worldAxisA)).normalized()

        // Calculate angle between projected vectors
        val cosAngle = projectedPerpA.dot(projectedPerpB).coerceIn(-1f, 1f)
        val sinAngle = projectedPerpA.cross(projectedPerpB).dot(worldAxisA)

        _currentAngle = atan2(sinAngle, cosAngle) * _referenceSign
    }

    private fun updateLimitState() {
        val angle = getHingeAngle()
        _solveLimit = angle < _lowerLimit || angle > _upperLimit
    }

    private fun getPerpendicularVector(v: Vector3): Vector3 {
        val absX = abs(v.x)
        val absY = abs(v.y)
        val absZ = abs(v.z)

        return when {
            absX < absY && absX < absZ -> Vector3.UNIT_X
            absY < absZ -> Vector3.UNIT_Y
            else -> Vector3.UNIT_Z
        }.cross(v).normalized()
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > PI) normalized = normalized - 2f * PI.toFloat()
        while (normalized < -PI) normalized = normalized + 2f * PI.toFloat()
        return normalized
    }

    /**
     * Apply constraint forces during physics step
     */
    internal fun solveConstraint(deltaTime: Float) {
        // Solve positional constraint (point-to-point part)
        solvePositionalConstraint(deltaTime)
        // Solve angular constraints
        solveAngularConstraint(deltaTime)
        // Apply motor forces if enabled
        if (_enableAngularMotor && _maxMotorImpulse > 0f) {
            applyMotorForces(deltaTime)
        }
        // Enforce limits if active
        if (_solveLimit) {
            enforceLimits(deltaTime)
        }
    }

    private fun solvePositionalConstraint(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldPivotA = transformA.transformPoint(pivotA)
        val worldPivotB = if (bodyB != null) {
            transformB.transformPoint(pivotB)
        } else {
            pivotB
        }

        val constraintError = worldPivotA - worldPivotB
        val errorMagnitude = constraintError.length()

        if (errorMagnitude > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / deltaTime
            val impulse = constraintError * correctionFactor
            bodyA.applyCentralImpulse(-impulse)
            bodyB?.applyCentralImpulse(impulse)
        }
    }

    private fun solveAngularConstraint(deltaTime: Float) {
        // Calculate angular error (axes should be aligned)
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldAxisA = transformA.transformDirection(axisA.normalized())
        val worldAxisB = transformB.transformDirection(axisB.normalized())

        val angularError = worldAxisA.cross(worldAxisB)
        val errorMagnitude = angularError.length()

        if (errorMagnitude > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / deltaTime
            val torqueImpulse = angularError * correctionFactor
            bodyA.applyTorqueImpulse(-torqueImpulse)
            bodyB?.applyTorqueImpulse(torqueImpulse)
        }
    }

    private fun applyMotorForces(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val worldAxisA = transformA.transformDirection(axisA.normalized())

        val currentAngularVelocityA = bodyA.angularVelocity.dot(worldAxisA)
        val currentAngularVelocityB = bodyB?.angularVelocity?.dot(worldAxisA) ?: 0f
        val relativeAngularVelocity = currentAngularVelocityA - currentAngularVelocityB

        val velocityError = _targetVelocity - relativeAngularVelocity
        val motorImpulse =
            ((velocityError * deltaTime)).coerceIn(-_maxMotorImpulse, _maxMotorImpulse)

        if (abs(motorImpulse) > 0.001f) {
            val torqueImpulse = worldAxisA * motorImpulse
            bodyA.applyTorqueImpulse(torqueImpulse)
            bodyB?.applyTorqueImpulse(-torqueImpulse)
            updateAppliedImpulse(abs(motorImpulse))
        }
    }

    private fun enforceLimits(deltaTime: Float) {
        val angle = getHingeAngle()
        var limitImpulse = 0f

        when {
            angle < _lowerLimit -> {
                val limitViolation = _lowerLimit - angle
                limitImpulse = limitViolation / deltaTime
            }

            angle > _upperLimit -> {
                val limitViolation = angle - _upperLimit
                limitImpulse = -limitViolation / deltaTime
            }
        }

        if (abs(limitImpulse) > 0.001f) {
            val transformA = bodyA.getWorldTransform()
            val worldAxisA = transformA.transformDirection(axisA.normalized())
            val torqueImpulse = worldAxisA * limitImpulse
            bodyA.applyTorqueImpulse(torqueImpulse)
            bodyB?.applyTorqueImpulse(-torqueImpulse)
        }
    }
}
