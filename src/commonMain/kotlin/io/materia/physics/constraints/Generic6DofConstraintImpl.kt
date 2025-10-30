/**
 * Generic 6DOF constraint implementation
 */
package io.materia.physics.constraints

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.physics.Generic6DofConstraint
import io.materia.physics.PhysicsConstraintImpl
import io.materia.physics.RigidBody
import kotlin.math.abs

/**
 * Provides full control over all 6 degrees of freedom (3 linear + 3 angular)
 */
class Generic6DofConstraintImpl(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : PhysicsConstraintImpl(id, bodyA, bodyB), Generic6DofConstraint {
    private var _linearLowerLimit = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    private var _linearUpperLimit = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var _angularLowerLimit = Vector3(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
    private var _angularUpperLimit = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)

    private val _motorEnabled = BooleanArray(6) { false } // 3 linear + 3 angular
    private val _motorTargetVelocity = FloatArray(6) { 0f }
    private val _motorMaxForce = FloatArray(6) { 0f }

    override fun setLinearLowerLimit(linearLower: Vector3) {
        _linearLowerLimit = linearLower
    }

    override fun setLinearUpperLimit(linearUpper: Vector3) {
        _linearUpperLimit = linearUpper
    }

    override fun getLinearLowerLimit(): Vector3 = _linearLowerLimit
    override fun getLinearUpperLimit(): Vector3 = _linearUpperLimit

    override fun setAngularLowerLimit(angularLower: Vector3) {
        _angularLowerLimit = angularLower
    }

    override fun setAngularUpperLimit(angularUpper: Vector3) {
        _angularUpperLimit = angularUpper
    }

    override fun getAngularLowerLimit(): Vector3 = _angularLowerLimit
    override fun getAngularUpperLimit(): Vector3 = _angularUpperLimit

    override fun enableMotor(index: Int, enable: Boolean) {
        require(index in 0..5) { "Motor index must be 0-5 (0-2 linear, 3-5 angular)" }
        _motorEnabled[index] = enable
    }

    override fun setMotorTargetVelocity(index: Int, velocity: Float) {
        require(index in 0..5) { "Motor index must be 0-5 (0-2 linear, 3-5 angular)" }
        _motorTargetVelocity[index] = velocity
    }

    override fun setMotorMaxForce(index: Int, force: Float) {
        require(index in 0..5) { "Motor index must be 0-5 (0-2 linear, 3-5 angular)" }
        _motorMaxForce[index] = maxOf(0f, force)
    }

    /**
     * Solve 6DOF constraint
     */
    internal fun solveConstraint(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldFrameA = transformA * frameA
        val worldFrameB = transformB * frameB

        // Solve linear constraints
        solveLinearConstraints(worldFrameA, worldFrameB, deltaTime)
        // Solve angular constraints
        solveAngularConstraints(worldFrameA, worldFrameB, deltaTime)
        // Apply motors
        applyMotors(worldFrameA, worldFrameB, deltaTime)
    }

    private fun solveLinearConstraints(
        worldFrameA: Matrix4,
        worldFrameB: Matrix4,
        deltaTime: Float
    ) {
        val positionA = worldFrameA.getTranslation()
        val positionB = worldFrameB.getTranslation()
        val relativePosition = positionA - positionB

        // Transform to frame A coordinates
        val localRelativePosition = worldFrameA.inverse().transformDirection(relativePosition)

        for (axis in 0..2) {
            val position = localRelativePosition.componentAt(axis)
            val lowerLimit = _linearLowerLimit.componentAt(axis)
            val upperLimit = _linearUpperLimit.componentAt(axis)

            var limitImpulse = 0f
            when {
                position < lowerLimit && lowerLimit > -Float.MAX_VALUE -> {
                    limitImpulse = (lowerLimit - position) / deltaTime
                }

                position > upperLimit && upperLimit < Float.MAX_VALUE -> {
                    limitImpulse = (upperLimit - position) / deltaTime
                }
            }

            if (abs(limitImpulse) > 0.001f) {
                val axisVector = when (axis) {
                    0 -> Vector3.UNIT_X
                    1 -> Vector3.UNIT_Y
                    else -> Vector3.UNIT_Z
                }
                val worldAxisVector = worldFrameA.transformDirection(axisVector)
                val impulse = worldAxisVector * limitImpulse
                bodyA.applyCentralImpulse(impulse)
                bodyB?.applyCentralImpulse(-impulse)
            }
        }
    }

    private fun solveAngularConstraints(
        worldFrameA: Matrix4,
        worldFrameB: Matrix4,
        deltaTime: Float
    ) {
        val orientationA = worldFrameA.getRotation()
        val orientationB = worldFrameB.getRotation()
        val relativeRotation = orientationA.inverse() * orientationB
        val eulerAngles = relativeRotation.toEulerAngles()

        for (axis in 0..2) {
            val angle = eulerAngles.componentAt(axis)
            val lowerLimit = _angularLowerLimit.componentAt(axis)
            val upperLimit = _angularUpperLimit.componentAt(axis)

            var limitTorque = 0f
            when {
                angle < lowerLimit && lowerLimit > -Float.MAX_VALUE -> {
                    limitTorque = (lowerLimit - angle) / deltaTime
                }

                angle > upperLimit && upperLimit < Float.MAX_VALUE -> {
                    limitTorque = (upperLimit - angle) / deltaTime
                }
            }

            if (abs(limitTorque) > 0.001f) {
                val axisVector = when (axis) {
                    0 -> Vector3.UNIT_X
                    1 -> Vector3.UNIT_Y
                    else -> Vector3.UNIT_Z
                }
                val worldAxisVector = worldFrameA.transformDirection(axisVector)
                val torqueImpulse = worldAxisVector * limitTorque
                bodyA.applyTorqueImpulse(torqueImpulse)
                bodyB?.applyTorqueImpulse(-torqueImpulse)
            }
        }
    }

    private fun applyMotors(worldFrameA: Matrix4, worldFrameB: Matrix4, deltaTime: Float) {
        // Linear motors (0-2)
        for (axis in 0..2) {
            if (!_motorEnabled[axis] || _motorMaxForce[axis] <= 0f) continue

            val axisVector = when (axis) {
                0 -> Vector3.UNIT_X
                1 -> Vector3.UNIT_Y
                else -> Vector3.UNIT_Z
            }
            val worldAxisVector = worldFrameA.transformDirection(axisVector)

            val velocityA = bodyA.linearVelocity.dot(worldAxisVector)
            val velocityB = bodyB?.linearVelocity?.dot(worldAxisVector) ?: 0f
            val relativeVelocity = velocityA - velocityB

            val velocityError = _motorTargetVelocity[axis] - relativeVelocity
            val motorForce =
                (velocityError / deltaTime).coerceIn(-_motorMaxForce[axis], _motorMaxForce[axis])

            if (abs(motorForce) > 0.001f) {
                val impulse = worldAxisVector * motorForce * deltaTime
                bodyA.applyCentralImpulse(impulse)
                bodyB?.applyCentralImpulse(-impulse)
            }
        }

        // Angular motors (3-5)
        for (axis in 3..5) {
            if (!_motorEnabled[axis] || _motorMaxForce[axis] <= 0f) continue

            val axisIndex = axis - 3
            val axisVector = when (axisIndex) {
                0 -> Vector3.UNIT_X
                1 -> Vector3.UNIT_Y
                else -> Vector3.UNIT_Z
            }
            val worldAxisVector = worldFrameA.transformDirection(axisVector)

            val angularVelocityA = bodyA.angularVelocity.dot(worldAxisVector)
            val angularVelocityB = bodyB?.angularVelocity?.dot(worldAxisVector) ?: 0f
            val relativeAngularVelocity = angularVelocityA - angularVelocityB

            val velocityError = _motorTargetVelocity[axis] - relativeAngularVelocity
            val motorTorque =
                (velocityError / deltaTime).coerceIn(-_motorMaxForce[axis], _motorMaxForce[axis])

            if (abs(motorTorque) > 0.001f) {
                val torqueImpulse = worldAxisVector * motorTorque * deltaTime
                bodyA.applyTorqueImpulse(torqueImpulse)
                bodyB?.applyTorqueImpulse(-torqueImpulse)
                updateAppliedImpulse(abs(motorTorque))
            }
        }
    }
}
