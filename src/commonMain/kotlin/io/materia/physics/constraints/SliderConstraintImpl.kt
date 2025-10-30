/**
 * Slider constraint implementation (prismatic joint)
 */
package io.materia.physics.constraints

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.physics.ConstraintParam
import io.materia.physics.PhysicsConstraintImpl
import io.materia.physics.RigidBody
import io.materia.physics.SliderConstraint
import kotlin.math.abs

/**
 * Allows linear motion along one axis while constraining rotation
 */
class SliderConstraintImpl(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : PhysicsConstraintImpl(id, bodyA, bodyB), SliderConstraint {
    override var lowerLinearLimit: Float = -1f
    override var upperLinearLimit: Float = 1f
    override var lowerAngularLimit: Float = 0f
    override var upperAngularLimit: Float = 0f
    override var poweredLinearMotor: Boolean = false
    override var targetLinearMotorVelocity: Float = 0f
    override var maxLinearMotorForce: Float = 0f
    override var poweredAngularMotor: Boolean = false
    override var targetAngularMotorVelocity: Float = 0f
    override var maxAngularMotorForce: Float = 0f

    private var _currentLinearPos = 0f
    private var _currentAngularPos = 0f

    override fun getLinearPos(): Float {
        updateCurrentPosition()
        return _currentLinearPos
    }

    override fun getAngularPos(): Float {
        updateCurrentPosition()
        return _currentAngularPos
    }

    private fun updateCurrentPosition() {
        // Calculate relative transform
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY
        val relativeTransform = frameA.inverse() * transformA.inverse() * transformB * frameB

        // Extract linear position along slider axis (Z-axis in frame coordinates)
        val sliderAxis = Vector3.UNIT_Z
        val relativePosition = relativeTransform.getTranslation()
        _currentLinearPos = relativePosition.dot(sliderAxis)

        // Extract angular position around slider axis
        val relativeRotation = relativeTransform.getRotation()
        val eulerAngles = relativeRotation.toEulerAngles()
        _currentAngularPos = eulerAngles.z // Rotation around Z-axis
    }

    /**
     * Solve slider constraint
     */
    internal fun solveConstraint(deltaTime: Float) {
        // Solve positional constraints (maintain alignment except along slider axis)
        solvePositionalConstraints(deltaTime)
        // Solve angular constraints (prevent rotation except around slider axis)
        solveAngularConstraints(deltaTime)
        // Apply linear motor if enabled
        if (poweredLinearMotor && maxLinearMotorForce > 0f) {
            applyLinearMotor(deltaTime)
        }
        // Apply angular motor if enabled
        if (poweredAngularMotor && maxAngularMotorForce > 0f) {
            applyAngularMotor(deltaTime)
        }
        // Enforce limits
        enforceLinearLimits(deltaTime)
        enforceAngularLimits(deltaTime)
    }

    private fun solvePositionalConstraints(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldFrameA = transformA * frameA
        val worldFrameB = transformB * frameB

        val positionA = worldFrameA.getTranslation()
        val positionB = worldFrameB.getTranslation()

        // Calculate constraint error perpendicular to slider axis
        val sliderAxisWorld = worldFrameA.transformDirection(Vector3.UNIT_Z)
        val positionError = positionB - positionA
        val parallelComponent = sliderAxisWorld * positionError.dot(sliderAxisWorld)
        val perpendicularError = positionError - parallelComponent

        if (perpendicularError.length() > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / deltaTime
            val impulse = perpendicularError * correctionFactor
            bodyA.applyCentralImpulse(-impulse)
            bodyB?.applyCentralImpulse(impulse)
        }
    }

    private fun solveAngularConstraints(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldFrameA = transformA * frameA
        val worldFrameB = transformB * frameB

        val orientationA = worldFrameA.getRotation()
        val orientationB = worldFrameB.getRotation()

        // Calculate relative rotation
        val relativeRotation = orientationA.inverse() * orientationB
        val axis = Vector3.UNIT_Z // Slider axis in frame coordinates

        // Allow rotation only around slider axis
        val worldSliderAxis = worldFrameA.transformDirection(axis)
        val angularError = relativeRotation.toAxisAngle()
        val rotationAxis = angularError.first.normalized()
        val rotationAngle = angularError.second

        // Project rotation onto plane perpendicular to slider axis
        val perpendicularRotation =
            rotationAxis - worldSliderAxis * rotationAxis.dot(worldSliderAxis)

        if (perpendicularRotation.length() > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / deltaTime
            val torqueImpulse =
                perpendicularRotation.normalized() * rotationAngle * correctionFactor
            bodyA.applyTorqueImpulse(-torqueImpulse)
            bodyB?.applyTorqueImpulse(torqueImpulse)
        }
    }

    private fun applyLinearMotor(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val worldFrameA = transformA * frameA
        val sliderAxisWorld = worldFrameA.transformDirection(Vector3.UNIT_Z)

        val velocityA = bodyA.linearVelocity.dot(sliderAxisWorld)
        val velocityB = bodyB?.linearVelocity?.dot(sliderAxisWorld) ?: 0f
        val relativeVelocity = velocityA - velocityB

        val velocityError = targetLinearMotorVelocity - relativeVelocity
        val motorForce =
            (velocityError / deltaTime).coerceIn(-maxLinearMotorForce, maxLinearMotorForce)

        if (abs(motorForce) > 0.001f) {
            val impulse = sliderAxisWorld * motorForce * deltaTime
            bodyA.applyCentralImpulse(impulse)
            bodyB?.applyCentralImpulse(-impulse)
            updateAppliedImpulse(abs(motorForce))
        }
    }

    private fun applyAngularMotor(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val worldFrameA = transformA * frameA
        val sliderAxisWorld = worldFrameA.transformDirection(Vector3.UNIT_Z)

        val angularVelocityA = bodyA.angularVelocity.dot(sliderAxisWorld)
        val angularVelocityB = bodyB?.angularVelocity?.dot(sliderAxisWorld) ?: 0f
        val relativeAngularVelocity = angularVelocityA - angularVelocityB

        val velocityError = targetAngularMotorVelocity - relativeAngularVelocity
        val motorTorque =
            (velocityError / deltaTime).coerceIn(-maxAngularMotorForce, maxAngularMotorForce)

        if (abs(motorTorque) > 0.001f) {
            val torqueImpulse = sliderAxisWorld * motorTorque * deltaTime
            bodyA.applyTorqueImpulse(torqueImpulse)
            bodyB?.applyTorqueImpulse(-torqueImpulse)
        }
    }

    private fun enforceLinearLimits(deltaTime: Float) {
        val position = getLinearPos()
        var limitImpulse = 0f

        when {
            position < lowerLinearLimit -> {
                limitImpulse = (lowerLinearLimit - position) / deltaTime
            }

            position > upperLinearLimit -> {
                limitImpulse = (upperLinearLimit - position) / deltaTime
            }
        }

        if (abs(limitImpulse) > 0.001f) {
            val transformA = bodyA.getWorldTransform()
            val worldFrameA = transformA * frameA
            val sliderAxisWorld = worldFrameA.transformDirection(Vector3.UNIT_Z)
            val impulse = sliderAxisWorld * limitImpulse
            bodyA.applyCentralImpulse(impulse)
            bodyB?.applyCentralImpulse(-impulse)
        }
    }

    private fun enforceAngularLimits(deltaTime: Float) {
        val angle = getAngularPos()
        var limitTorque = 0f

        when {
            angle < lowerAngularLimit -> {
                limitTorque = (lowerAngularLimit - angle) / deltaTime
            }

            angle > upperAngularLimit -> {
                limitTorque = (upperAngularLimit - angle) / deltaTime
            }
        }

        if (abs(limitTorque) > 0.001f) {
            val transformA = bodyA.getWorldTransform()
            val worldFrameA = transformA * frameA
            val sliderAxisWorld = worldFrameA.transformDirection(Vector3.UNIT_Z)
            val torqueImpulse = sliderAxisWorld * limitTorque
            bodyA.applyTorqueImpulse(torqueImpulse)
            bodyB?.applyTorqueImpulse(-torqueImpulse)
        }
    }
}
