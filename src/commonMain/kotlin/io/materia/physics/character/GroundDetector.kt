package io.materia.physics.character

import io.materia.core.math.Vector3
import io.materia.physics.CollisionObject
import io.materia.physics.PhysicsWorld
import kotlin.math.acos

/**
 * Ground detection system for character controllers
 */
object GroundDetector {

    /**
     * Ground detection result
     */
    data class GroundInfo(
        val isOnGround: Boolean,
        val groundNormal: Vector3,
        val groundObject: CollisionObject?,
        val groundDistance: Float,
        val groundHitPoint: Vector3
    )

    /**
     * Perform ground detection check
     */
    fun check(
        world: PhysicsWorld,
        position: Vector3,
        stepHeight: Float,
        skinWidth: Float,
        maxSlopeAngle: Float
    ): GroundInfo {
        val rayStart = position + Vector3(0f, stepHeight, 0f)
        val rayEnd = position - Vector3(0f, stepHeight + skinWidth, 0f)

        val raycastResult = world.raycast(rayStart, rayEnd)

        return if (raycastResult?.hasHit == true) {
            val groundDistance = raycastResult.distance - stepHeight
            val groundNormal = raycastResult.hitNormal
            val slopeAngle = acos(groundNormal.dot(Vector3.UNIT_Y).coerceIn(-1f, 1f))

            val isOnGround = slopeAngle <= maxSlopeAngle && groundDistance <= stepHeight

            GroundInfo(
                isOnGround = isOnGround,
                groundNormal = groundNormal,
                groundObject = if (isOnGround) raycastResult.hitObject else null,
                groundDistance = groundDistance,
                groundHitPoint = raycastResult.hitPoint
            )
        } else {
            GroundInfo(
                isOnGround = false,
                groundNormal = Vector3.UNIT_Y,
                groundObject = null,
                groundDistance = Float.MAX_VALUE,
                groundHitPoint = Vector3.ZERO
            )
        }
    }
}
