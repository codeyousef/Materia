package io.materia.physics.character

import io.materia.core.math.Vector3
import io.materia.physics.CollisionObject
import io.materia.physics.PhysicsWorld

/**
 * Sweep testing utility for character movement
 */
object SweepTester {

    /**
     * Sweep test result
     */
    data class SweepResult(
        val hasHit: Boolean,
        val distance: Float,
        val normal: Vector3,
        val hitPoint: Vector3,
        val hitObject: CollisionObject?
    )

    /**
     * Perform sweep test for character movement
     */
    fun sweep(
        world: PhysicsWorld,
        from: Vector3,
        to: Vector3,
        characterRadius: Float,
        characterHeight: Float
    ): SweepResult {
        // Simplified sweep test - this would use the physics world's sweep functionality
        val distance = (to - from).length()
        if (distance < 0.001f) {
            return SweepResult(false, 0f, Vector3.UNIT_Y, from, null)
        }
        val direction = (to - from).normalize()

        // Perform raycast from character center
        val rayStart = from + Vector3(0f, characterHeight * 0.5f, 0f)
        val rayEnd = rayStart + direction * (distance + characterRadius)

        val raycastResult = world.raycast(rayStart, rayEnd)

        return if (raycastResult?.hasHit == true) {
            SweepResult(
                hasHit = true,
                distance = kotlin.math.max(0f, raycastResult.distance - characterRadius),
                normal = raycastResult.hitNormal,
                hitPoint = raycastResult.hitPoint,
                hitObject = raycastResult.hitObject
            )
        } else {
            SweepResult(
                hasHit = false,
                distance = distance,
                normal = Vector3.UNIT_Y,
                hitPoint = to,
                hitObject = null
            )
        }
    }
}
