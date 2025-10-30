/**
 * Point-to-point constraint implementation (ball-socket joint)
 */
package io.materia.physics.constraints

import io.materia.core.math.Vector3
import io.materia.physics.ConstraintParam
import io.materia.physics.PhysicsConstraintImpl
import io.materia.physics.PointToPointConstraint
import io.materia.physics.RigidBody

/**
 * Constrains two points on different bodies to remain at the same position
 */
class PointToPointConstraintImpl(
    id: String,
    bodyA: RigidBody,
    bodyB: RigidBody?,
    override val pivotA: Vector3,
    override val pivotB: Vector3
) : PhysicsConstraintImpl(id, bodyA, bodyB), PointToPointConstraint {
    private var _pivotA = pivotA
    private var _pivotB = pivotB

    override fun setPivotA(pivot: Vector3) {
        _pivotA = pivot
    }

    override fun setPivotB(pivot: Vector3) {
        _pivotB = pivot
    }

    override fun updateRHS(timeStep: Float) {
        if (!isEnabled() || !isValid()) return

        val transformA = bodyA.getWorldTransform()
        val worldPivotA = transformA.transformPoint(_pivotA)

        val worldPivotB = if (bodyB != null) {
            val transformB = bodyB.getWorldTransform()
            transformB.transformPoint(_pivotB)
        } else {
            _pivotB // World space pivot for static constraint
        }

        // Calculate constraint error
        val constraintError = worldPivotA - worldPivotB
        val errorMagnitude = constraintError.length()

        // Apply corrective impulse if error is significant
        if (errorMagnitude > 0.001f) {
            val correctionFactor = getParam(ConstraintParam.ERP, -1) / timeStep
            val impulse = constraintError * correctionFactor

            // Apply impulse to body A
            bodyA.applyCentralImpulse(-impulse)
            // Apply opposite impulse to body B (if it exists)
            bodyB?.applyCentralImpulse(impulse)

            updateAppliedImpulse(impulse.length())
        }
    }

    /**
     * Get constraint jacobian for physics solver
     */
    fun getJacobian(): ConstraintJacobian {
        val transformA = bodyA.getWorldTransform()
        val worldPivotA = transformA.transformPoint(_pivotA)

        val worldPivotB = if (bodyB != null) {
            val transformB = bodyB.getWorldTransform()
            transformB.transformPoint(_pivotB)
        } else {
            _pivotB
        }

        val relativePos = worldPivotA - worldPivotB

        return ConstraintJacobian(
            linearA = Vector3.ONE,
            angularA = relativePos.cross(Vector3.ONE),
            linearB = if (bodyB != null) -Vector3.ONE else Vector3.ZERO,
            angularB = if (bodyB != null) -relativePos.cross(Vector3.ONE) else Vector3.ZERO
        )
    }
}
