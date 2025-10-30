package io.materia.physics

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import java.util.*

/**
 * JVM implementations of physics constraints
 */

abstract class JvmPhysicsConstraint(
    override val bodyA: RigidBody,
    override val bodyB: RigidBody?
) : PhysicsConstraint {
    override val id: String = UUID.randomUUID().toString()
    override var enabled: Boolean = true
    override var breakingThreshold: Float = Float.MAX_VALUE

    protected var _appliedImpulse: Float = 0f
    protected val constraintParams = mutableMapOf<ConstraintParam, Float>()

    override fun setParam(param: ConstraintParam, value: Float, axis: Int) {
        constraintParams[param] = value
    }

    override fun getParam(param: ConstraintParam, axis: Int): Float {
        return constraintParams[param] ?: 0f
    }

    override fun getAppliedImpulse(): Float = _appliedImpulse

    override fun isEnabled(): Boolean = enabled

    override fun getInfo(info: ConstraintInfo) {
        info.m_numIterations = 10
        info.m_tau = 0.3f
        info.m_damping = 1f
        info.m_impulseClamp = 0f
    }
}

/**
 * Point-to-point constraint implementation
 */
class JvmPointToPointConstraint(
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3
) : JvmPhysicsConstraint(bodyA, bodyB), PointToPointConstraint {

    private var currentPivotA = pivotA.copy()
    private var currentPivotB = pivotB.copy()

    override fun setPivotA(pivot: Vector3) {
        currentPivotA = pivot
    }

    override fun setPivotB(pivot: Vector3) {
        currentPivotB = pivot
    }

    override fun updateRHS(timeStep: Float) {
        if (!enabled) return

        // Get world positions of pivot points
        val worldPivotA = bodyA.getWorldTransform().multiplyPoint3(currentPivotA)
        val worldPivotB = bodyB?.getWorldTransform()?.multiplyPoint3(currentPivotB)
            ?: worldPivotA + currentPivotB

        // Calculate error
        val error = worldPivotB - worldPivotA

        // Apply corrective impulse
        if (error.lengthSquared() > 0.001f) {
            val impulse = error * (1f / timeStep)

            if (bodyA.bodyType == RigidBodyType.DYNAMIC) {
                bodyA.applyCentralImpulse(impulse * 0.5f)
            }

            bodyB?.let {
                if (it.bodyType == RigidBodyType.DYNAMIC) {
                    it.applyCentralImpulse(-impulse * 0.5f)
                }
            }

            _appliedImpulse = impulse.length()
        }
    }
}

/**
 * Hinge constraint implementation
 */
class JvmHingeConstraint(
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3,
    override val axisA: Vector3,
    override val axisB: Vector3
) : JvmPhysicsConstraint(bodyA, bodyB), HingeConstraint {

    override var lowerLimit: Float = -kotlin.math.PI.toFloat()
    override var upperLimit: Float = kotlin.math.PI.toFloat()
    override var enableAngularMotor: Boolean = false
    override var targetVelocity: Float = 0f
    override var maxMotorImpulse: Float = 0f

    private var currentAngle: Float = 0f
    private var limitSoftness: Float = 0.9f
    private var limitBiasFactor: Float = 0.3f
    private var limitRelaxationFactor: Float = 1f

    override fun setLimit(
        low: Float,
        high: Float,
        softness: Float,
        biasFactor: Float,
        relaxationFactor: Float
    ) {
        lowerLimit = low
        upperLimit = high
        limitSoftness = softness
        limitBiasFactor = biasFactor
        limitRelaxationFactor = relaxationFactor
    }

    override fun enableMotor(enable: Boolean) {
        enableAngularMotor = enable
    }

    override fun setMotorTarget(targetAngle: Float, deltaTime: Float) {
        if (enableAngularMotor) {
            val currentVel = (targetAngle - currentAngle) / deltaTime
            targetVelocity = currentVel.coerceIn(-maxMotorImpulse, maxMotorImpulse)
        }
    }

    override fun getHingeAngle(): Float {
        // Calculate angle between bodies around hinge axis
        val rotA = bodyA.getWorldTransform().getRotation()
        val rotB = bodyB?.getWorldTransform()?.getRotation() ?: Quaternion.IDENTITY

        // Project onto hinge axis
        val worldAxisA = Matrix4().makeRotationFromQuaternion(rotA).transformDirection(axisA)
        val worldAxisB = Matrix4().makeRotationFromQuaternion(rotB).transformDirection(axisB)

        // Calculate angle
        val dot = worldAxisA.dot(worldAxisB).coerceIn(-1f, 1f)
        currentAngle = kotlin.math.acos(dot)

        return currentAngle
    }

    fun solveConstraint(deltaTime: Float) {
        if (!enabled) return

        val angle = getHingeAngle()

        // Apply limits
        if (angle < lowerLimit) {
            val error = lowerLimit - angle
            val correction = error * limitBiasFactor / deltaTime
            applyAngularImpulse(correction)
        } else if (angle > upperLimit) {
            val error = angle - upperLimit
            val correction = -error * limitBiasFactor / deltaTime
            applyAngularImpulse(correction)
        }

        // Apply motor
        if (enableAngularMotor) {
            val motorImpulse =
                (targetVelocity * deltaTime).coerceIn(-maxMotorImpulse, maxMotorImpulse)
            applyAngularImpulse(motorImpulse)
        }
    }

    private fun applyAngularImpulse(impulse: Float) {
        val worldAxis =
            Matrix4().makeRotationFromQuaternion(bodyA.getWorldTransform().getRotation())
                .transformDirection(axisA)
        val torqueImpulse = worldAxis * impulse

        if (bodyA.bodyType == RigidBodyType.DYNAMIC) {
            bodyA.applyTorqueImpulse(torqueImpulse)
        }

        bodyB?.let {
            if (it.bodyType == RigidBodyType.DYNAMIC) {
                it.applyTorqueImpulse(-torqueImpulse)
            }
        }

        _appliedImpulse += kotlin.math.abs(impulse)
    }
}

/**
 * Slider constraint implementation
 */
class JvmSliderConstraint(
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : JvmPhysicsConstraint(bodyA, bodyB), SliderConstraint {

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

    private var currentLinearPos: Float = 0f
    private var currentAngularPos: Float = 0f

    override fun getLinearPos(): Float {
        // Calculate relative position along slider axis
        val worldFrameA = bodyA.getWorldTransform() * frameA
        val worldFrameB = bodyB?.let { it.getWorldTransform() * frameB }
            ?: Matrix4().makeTranslation(
                frameB.getTranslation().x,
                frameB.getTranslation().y,
                frameB.getTranslation().z
            )

        val sliderAxis =
            Matrix4().makeRotationFromQuaternion(worldFrameA.getRotation())
                .transformDirection(Vector3.UNIT_X)
        val delta = worldFrameB.getTranslation() - worldFrameA.getTranslation()

        currentLinearPos = delta.dot(sliderAxis)
        return currentLinearPos
    }

    override fun getAngularPos(): Float {
        // Calculate relative angle around slider axis
        val worldFrameA = bodyA.getWorldTransform() * frameA
        val worldFrameB = bodyB?.let { it.getWorldTransform() * frameB }
            ?: frameB

        val rotA = worldFrameA.getRotation()
        val rotB = worldFrameB.getRotation()

        val relativeRot = rotB * rotA.conjugate()
        currentAngularPos = relativeRot.toAxisAngle().second

        return currentAngularPos
    }

    fun solveConstraint(deltaTime: Float) {
        if (!enabled) return

        val linearPos = getLinearPos()
        val angularPos = getAngularPos()

        // Apply linear limits
        if (linearPos < lowerLinearLimit) {
            val error = lowerLinearLimit - linearPos
            applyLinearCorrection(error / deltaTime)
        } else if (linearPos > upperLinearLimit) {
            val error = linearPos - upperLinearLimit
            applyLinearCorrection(-error / deltaTime)
        }

        // Apply angular limits
        if (lowerAngularLimit != upperAngularLimit) {
            if (angularPos < lowerAngularLimit) {
                val error = lowerAngularLimit - angularPos
                applyAngularCorrection(error / deltaTime)
            } else if (angularPos > upperAngularLimit) {
                val error = angularPos - upperAngularLimit
                applyAngularCorrection(-error / deltaTime)
            }
        }

        // Apply linear motor
        if (poweredLinearMotor) {
            val motorForce = (targetLinearMotorVelocity * deltaTime)
                .coerceIn(-maxLinearMotorForce, maxLinearMotorForce)
            applyLinearCorrection(motorForce)
        }

        // Apply angular motor
        if (poweredAngularMotor) {
            val motorTorque = (targetAngularMotorVelocity * deltaTime)
                .coerceIn(-maxAngularMotorForce, maxAngularMotorForce)
            applyAngularCorrection(motorTorque)
        }
    }

    private fun applyLinearCorrection(correction: Float) {
        val worldFrameA = bodyA.getWorldTransform() * frameA
        val sliderAxis =
            Matrix4().makeRotationFromQuaternion(worldFrameA.getRotation())
                .transformDirection(Vector3.UNIT_X)
        val impulse = sliderAxis * correction

        if (bodyA.bodyType == RigidBodyType.DYNAMIC) {
            bodyA.applyCentralImpulse(impulse)
        }

        bodyB?.let {
            if (it.bodyType == RigidBodyType.DYNAMIC) {
                it.applyCentralImpulse(-impulse)
            }
        }

        _appliedImpulse += kotlin.math.abs(correction)
    }

    private fun applyAngularCorrection(correction: Float) {
        val worldFrameA = bodyA.getWorldTransform() * frameA
        val sliderAxis =
            Matrix4().makeRotationFromQuaternion(worldFrameA.getRotation())
                .transformDirection(Vector3.UNIT_X)
        val torque = sliderAxis * correction

        if (bodyA.bodyType == RigidBodyType.DYNAMIC) {
            bodyA.applyTorqueImpulse(torque)
        }

        bodyB?.let {
            if (it.bodyType == RigidBodyType.DYNAMIC) {
                it.applyTorqueImpulse(-torque)
            }
        }

        _appliedImpulse += kotlin.math.abs(correction)
    }
}

