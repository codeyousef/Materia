/**
 * Cone-twist constraint implementation (shoulder joint)
 */
package io.materia.physics.constraints

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.physics.ConeTwistConstraint
import io.materia.physics.ConstraintParam
import io.materia.physics.PhysicsConstraintImpl
import io.materia.physics.RigidBody
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Allows rotation with cone and twist limits, simulating ball-and-socket with limits
 */
class ConeTwistConstraintImpl(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val frameA: Matrix4,
    override val frameB: Matrix4
) : PhysicsConstraintImpl(id, bodyA, bodyB), ConeTwistConstraint {
    override var swingSpan1: Float = PI.toFloat() / 4f // 45 degrees
    override var swingSpan2: Float = PI.toFloat() / 4f // 45 degrees
    override var twistSpan: Float = PI.toFloat() / 6f  // 30 degrees
    override var damping: Float = 0.01f

    private var _motorEnabled = false
    private var _motorTarget = Quaternion.IDENTITY
    private var _maxMotorImpulse = 0f

    override fun setLimit(
        swingSpan1: Float,
        swingSpan2: Float,
        twistSpan: Float,
        softness: Float,
        biasFactor: Float,
        relaxationFactor: Float
    ) {
        this.swingSpan1 = swingSpan1.coerceIn(0f, PI.toFloat())
        this.swingSpan2 = swingSpan2.coerceIn(0f, PI.toFloat())
        this.twistSpan = twistSpan.coerceIn(0f, PI.toFloat())
        setParam(ConstraintParam.STOP_ERP, biasFactor, -1)
        setParam(ConstraintParam.STOP_CFM, relaxationFactor, -1)
    }

    override fun enableMotor(enable: Boolean) {
        _motorEnabled = enable
    }

    override fun setMaxMotorImpulse(maxMotorImpulse: Float) {
        _maxMotorImpulse = maxOf(0f, maxMotorImpulse)
    }

    override fun setMotorTarget(q: Quaternion) {
        _motorTarget = q.normalized()
    }

    /**
     * Solve cone-twist constraint
     */
    internal fun solveConstraint(deltaTime: Float) {
        // Solve positional constraints
        solvePositionalConstraint(deltaTime)
        // Solve angular constraints with cone and twist limits
        solveAngularConstraints(deltaTime)
        // Apply motor if enabled
        if (_motorEnabled && _maxMotorImpulse > 0f) {
            applyMotor(deltaTime)
        }
    }

    private fun solvePositionalConstraint(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldFrameA = transformA * frameA
        val worldFrameB = transformB * frameB

        val positionA = worldFrameA.getTranslation()
        val positionB = worldFrameB.getTranslation()
        val constraintError = positionA - positionB

        if (constraintError.length() > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / deltaTime
            val impulse = constraintError * correctionFactor
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
        val relativeRotation = orientationA.inverse() * orientationB

        // Decompose into swing and twist components
        val (swingRotation, twistRotation) = decomposeSwingTwist(relativeRotation, Vector3.UNIT_X)

        // Check swing limits
        val swingAxis = swingRotation.toAxisAngle()
        val swingAngle = swingAxis.second
        val swingAxisNormalized = swingAxis.first.normalized()

        if (swingAngle > 0.001f) {
            // Project swing axis onto Y-Z plane
            val swingY = swingAxisNormalized.y
            val swingZ = swingAxisNormalized.z
            val normalizedSwingY =
                if (abs(swingY) > 0.001f) swingY / sqrt(swingY * swingY + (swingZ * swingZ)) else 0f
            val normalizedSwingZ =
                if (abs(swingZ) > 0.001f) swingZ / sqrt(swingY * swingY + (swingZ * swingZ)) else 0f

            // Calculate elliptical cone limits
            val maxSwingY = swingSpan1
            val maxSwingZ = swingSpan2
            val ellipseLimit = sqrt(
                ((normalizedSwingY * normalizedSwingY)) / ((maxSwingY * maxSwingY)) +
                        ((normalizedSwingZ * normalizedSwingZ)) / ((maxSwingZ * maxSwingZ))
            )

            if (ellipseLimit > 1f) {
                // Swing limit violated - apply corrective torque
                val limitViolation = swingAngle * (ellipseLimit - 1f) / ellipseLimit
                val correctionTorque = swingAxisNormalized * limitViolation / deltaTime
                val worldCorrectionTorque = worldFrameA.transformDirection(correctionTorque)
                bodyA.applyTorqueImpulse(-worldCorrectionTorque)
                bodyB?.applyTorqueImpulse(worldCorrectionTorque)
            }
        }

        // Check twist limits
        val twistAxis = twistRotation.toAxisAngle()
        val twistAngle = twistAxis.second

        if (abs(twistAngle) > twistSpan) {
            // Twist limit violated - apply corrective torque
            val limitViolation = if (twistAngle > 0f) {
                twistAngle - twistSpan
            } else {
                twistAngle + twistSpan
            }
            val correctionTorque = Vector3.UNIT_X * limitViolation / deltaTime
            val worldCorrectionTorque = worldFrameA.transformDirection(correctionTorque)
            bodyA.applyTorqueImpulse(-worldCorrectionTorque)
            bodyB?.applyTorqueImpulse(worldCorrectionTorque)
        }

        // Apply damping
        if (damping > 0f) {
            val relativeAngularVelocity =
                bodyA.angularVelocity - (bodyB?.angularVelocity ?: Vector3.ZERO)
            val dampingTorque = relativeAngularVelocity * damping
            bodyA.applyTorqueImpulse(-(dampingTorque * deltaTime))
            bodyB?.applyTorqueImpulse((dampingTorque * deltaTime))
        }
    }

    private fun applyMotor(deltaTime: Float) {
        val transformA = bodyA.getWorldTransform()
        val transformB = bodyB?.getWorldTransform() ?: Matrix4.IDENTITY

        val worldFrameA = transformA * frameA
        val worldFrameB = transformB * frameB

        val currentOrientation = worldFrameA.getRotation().inverse() * worldFrameB.getRotation()
        val targetOrientation = _motorTarget

        // Calculate rotation needed to reach target
        val rotationError = targetOrientation * currentOrientation.inverse()
        val (errorAxis, errorAngle) = rotationError.toAxisAngle()

        if (errorAngle > 0.001f) {
            val motorTorque = (errorAxis.normalized() * errorAngle / deltaTime)
                .coerceLength(0f, _maxMotorImpulse)
            val worldMotorTorque = worldFrameA.transformDirection(motorTorque)
            bodyA.applyTorqueImpulse((worldMotorTorque * deltaTime))
            bodyB?.applyTorqueImpulse(-(worldMotorTorque * deltaTime))
            updateAppliedImpulse(motorTorque.length())
        }
    }

    private fun decomposeSwingTwist(
        rotation: Quaternion,
        twistAxis: Vector3
    ): Pair<Quaternion, Quaternion> {
        // Decompose rotation into swing (around Y,Z axes) and twist (around X axis)
        val rotationVector = Vector3(rotation.x, rotation.y, rotation.z)
        val twistComponent = twistAxis * rotationVector.dot(twistAxis)
        val swingComponent = rotationVector - twistComponent

        val twistRotation = Quaternion(
            twistComponent.x,
            twistComponent.y,
            twistComponent.z,
            rotation.w
        ).normalized()

        val swingRotation = rotation * twistRotation.inverse()
        return Pair(swingRotation, twistRotation)
    }
}

/**
 * Extension function to coerce vector length
 */
private fun Vector3.coerceLength(min: Float, max: Float): Vector3 {
    val len = this.length()
    return if (len > max) {
        this.normalized() * max
    } else if (len < min) {
        this.normalized() * min
    } else {
        this
    }
}
