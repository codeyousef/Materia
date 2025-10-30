package io.materia.physics.character

import io.materia.core.math.Vector3
import io.materia.physics.CollisionObject

/**
 * Platform tracking and velocity inheritance system
 */
class PlatformTracker {
    private var currentPlatform: CollisionObject? = null
    private var platformVelocity = Vector3.ZERO
    private var lastPlatformPosition = Vector3.ZERO

    /**
     * Update platform tracking and calculate platform velocity
     */
    fun update(groundObject: CollisionObject?) {
        if (groundObject == currentPlatform) {
            // Still on same platform - calculate platform velocity
            val currentPlatformPos =
                groundObject?.getWorldTransform()?.getTranslation() ?: Vector3.ZERO
            platformVelocity = if (lastPlatformPosition != Vector3.ZERO) {
                (currentPlatformPos - lastPlatformPosition) * 60f // Assume 60 FPS for velocity calculation
            } else {
                Vector3.ZERO
            }
            lastPlatformPosition = currentPlatformPos
        } else {
            // Platform changed
            currentPlatform = groundObject
            lastPlatformPosition =
                groundObject?.getWorldTransform()?.getTranslation() ?: Vector3.ZERO
            platformVelocity = Vector3.ZERO
        }
    }

    /**
     * Get current platform velocity
     */
    fun getVelocity(): Vector3 = platformVelocity

    /**
     * Get current platform
     */
    fun getPlatform(): CollisionObject? = currentPlatform

    /**
     * Reset platform tracking
     */
    fun reset() {
        currentPlatform = null
        platformVelocity = Vector3.ZERO
        lastPlatformPosition = Vector3.ZERO
    }
}
