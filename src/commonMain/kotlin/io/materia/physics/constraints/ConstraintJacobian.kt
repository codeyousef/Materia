/**
 * Constraint jacobian for physics solver
 */
package io.materia.physics.constraints

import io.materia.core.math.Vector3

/**
 * Constraint jacobian for physics solver
 */
data class ConstraintJacobian(
    val linearA: Vector3,
    val angularA: Vector3,
    val linearB: Vector3,
    val angularB: Vector3
)
