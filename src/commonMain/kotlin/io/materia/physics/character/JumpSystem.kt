package io.materia.physics.character

import io.materia.core.math.Vector3

/**
 * Jump system with coyote time and grace period support
 */
class JumpSystem(
    var jumpSpeed: Float = 5f,
    var jumpGraceTime: Float = 0.1f,
    var coyoteTime: Float = 0.15f
) {
    private var jumpTimeout = 0f
    private var wasJumping = false

    /**
     * Check if jump is allowed
     */
    fun canJump(isOnGround: Boolean): Boolean {
        return isOnGround || jumpTimeout > 0f
    }

    /**
     * Execute jump
     */
    fun jump(
        direction: Vector3 = Vector3.UNIT_Y,
        onJump: (Float, Vector3) -> Unit
    ): Boolean {
        val verticalVelocity = jumpSpeed
        wasJumping = true
        jumpTimeout = 0f

        // Apply directional jump if specified
        val horizontalVelocity = if (direction != Vector3.UNIT_Y && direction.length() > 0.001f) {
            val horizontalDirection = direction.copy().normalize()
            val horizontalSpeed = jumpSpeed * 0.5f // Reduced horizontal component
            horizontalDirection * horizontalSpeed
        } else {
            Vector3.ZERO
        }

        onJump(verticalVelocity, horizontalVelocity)
        return true
    }

    /**
     * Update jump state
     */
    fun update(
        deltaTime: Float,
        isOnGround: Boolean,
        wasOnGround: Boolean,
        verticalVelocity: Float
    ) {
        // Start coyote time if just left ground
        if (wasOnGround && !isOnGround && !wasJumping) {
            jumpTimeout = coyoteTime
        }

        // Update jump timeout (coyote time)
        if (jumpTimeout > 0f) {
            jumpTimeout = jumpTimeout - deltaTime
            if (jumpTimeout <= 0f) {
                jumpTimeout = 0f
            }
        }

        // Reset jumping state when landing
        if (isOnGround && wasJumping && verticalVelocity <= 0f) {
            wasJumping = false
        }
    }

    /**
     * Reset jump state
     */
    fun reset() {
        jumpTimeout = 0f
        wasJumping = false
    }
}
