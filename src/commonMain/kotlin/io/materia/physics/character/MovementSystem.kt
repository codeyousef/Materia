package io.materia.physics.character

import io.materia.core.math.Vector3
import io.materia.physics.PhysicsWorld

/**
 * Character movement system handling horizontal and vertical movement
 */
object MovementSystem {

    /**
     * Perform horizontal movement with collision detection
     */
    fun performHorizontal(
        world: PhysicsWorld,
        startPosition: Vector3,
        movement: Vector3,
        characterRadius: Float,
        characterHeight: Float,
        skinWidth: Float,
        maxSlopeAngle: Float
    ): Vector3 {
        val movementDistance = movement.length()
        if (movementDistance < 0.001f) return startPosition

        val movementDirection = movement.clone().normalize() // Don't mutate input
        var currentPosition = startPosition

        // Slide along surfaces
        var remainingDistance = movementDistance
        var slideIterations = 0
        val maxSlideIterations = 4

        while (remainingDistance > 0.001f && slideIterations < maxSlideIterations) {
            slideIterations++

            val testMovement = movementDirection * remainingDistance
            val testPosition = currentPosition + testMovement

            // Perform sweep test
            val sweepResult = SweepTester.sweep(
                world,
                currentPosition,
                testPosition,
                characterRadius,
                characterHeight
            )

            if (sweepResult.hasHit) {
                // Move to hit point minus skin width
                val safeDistance = sweepResult.distance - skinWidth
                if (safeDistance > 0.001f) {
                    currentPosition = currentPosition + movementDirection * safeDistance
                    remainingDistance = remainingDistance - safeDistance
                } else {
                    remainingDistance = 0f
                }

                // Calculate slide direction
                val slideDirection =
                    calculateSlideDirection(movementDirection, sweepResult.normal, maxSlopeAngle)
                val slideLen = slideDirection.length()

                if (slideLen > 0.001f) {
                    movementDirection.copy(slideDirection.clone().normalize())
                } else {
                    break // Cannot slide further
                }
            } else {
                // No collision, move full distance
                currentPosition = testPosition
                break
            }
        }

        return currentPosition
    }

    /**
     * Perform vertical movement with collision detection
     */
    fun performVertical(
        world: PhysicsWorld,
        startPosition: Vector3,
        movement: Vector3,
        characterRadius: Float,
        characterHeight: Float,
        skinWidth: Float,
        maxSlopeAngle: Float,
        onGroundCallback: (Boolean, Vector3?, Any?) -> Unit
    ): Vector3 {
        val movementDirection = if (movement.y > 0f) Vector3.UNIT_Y else -Vector3.UNIT_Y
        val movementDistance = kotlin.math.abs(movement.y)

        if (movementDistance < 0.001f) return startPosition

        val testPosition = startPosition + movement
        val sweepResult =
            SweepTester.sweep(world, startPosition, testPosition, characterRadius, characterHeight)

        return if (sweepResult.hasHit) {
            // Hit something during vertical movement
            val safeDistance = sweepResult.distance - skinWidth
            val newPosition = startPosition + movementDirection * kotlin.math.max(0f, safeDistance)

            // Check if we hit the ground or ceiling
            if (movement.y < 0f) {
                // Falling - check if we hit the ground
                val slopeAngle =
                    kotlin.math.acos(sweepResult.normal.dot(Vector3.UNIT_Y).coerceIn(-1f, 1f))
                if (slopeAngle <= maxSlopeAngle) {
                    onGroundCallback(true, sweepResult.normal, sweepResult.hitObject)
                }
            } else {
                // Jumping/rising - hit ceiling (handled by callback)
                onGroundCallback(false, null, null)
            }

            newPosition
        } else {
            testPosition
        }
    }

    /**
     * Calculate slide direction along a surface
     */
    private fun calculateSlideDirection(
        movementDirection: Vector3,
        surfaceNormal: Vector3,
        maxSlopeAngle: Float
    ): Vector3 {
        // Project movement direction onto surface plane
        val projectedMovement =
            movementDirection - surfaceNormal * movementDirection.dot(surfaceNormal)

        // Check if surface is too steep to walk on
        val slopeAngle = kotlin.math.acos(surfaceNormal.dot(Vector3.UNIT_Y).coerceIn(-1f, 1f))
        if (slopeAngle > maxSlopeAngle) {
            // Too steep - slide down the slope
            val downSlope = Vector3.UNIT_Y - surfaceNormal * Vector3.UNIT_Y.dot(surfaceNormal)
            val len = downSlope.length()
            return if (len > 0.001f) downSlope.normalize() else Vector3.ZERO
        }

        return projectedMovement
    }
}
