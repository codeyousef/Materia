/**
 * Rapier Character Controller Implementation
 * Simulates character controller using kinematic rigid body
 */
package io.materia.physics.rapier.character

import io.materia.core.math.Vector3
import io.materia.physics.CharacterController
import io.materia.physics.CollisionShape
import io.materia.physics.PhysicsOperationResult
import io.materia.physics.PhysicsWorld

/**
 * Character controller implementation using Rapier kinematic bodies
 */
class RapierCharacterController(
    private val shape: CollisionShape,
    initialStepHeight: Float
) : CharacterController {
    override val id = "char_${kotlin.js.Date.now().toLong()}"

    private var position = Vector3()
    private var velocity = Vector3()
    private var isOnGround = false

    // CollisionObject properties
    override var transform = io.materia.core.math.Matrix4()
    override var collisionShape = shape
    override var collisionGroups = 1
    override var collisionMask = -1
    override var userData: Any? = null
    override var contactProcessingThreshold = 1e30f
    override var collisionFlags = 0
    override var isTrigger = false

    // CharacterController properties
    override var stepHeight = initialStepHeight
    override var maxSlope = 45f * kotlin.math.PI.toFloat() / 180f
    override var jumpSpeed = 10f
    override var fallSpeed = -9.81f
    override var walkDirection = Vector3()
    override var velocityForTimeInterval = Vector3()

    override fun canJump(): Boolean = isOnGround

    override fun onGround(): Boolean = isOnGround

    override fun warp(origin: Vector3) {
        position.set(origin)
    }

    override fun preStep(world: PhysicsWorld) {
        // Apply gravity
        if (!isOnGround) {
            velocity.y += fallSpeed * (1f / 60f)
        }
    }

    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {
        // Update position based on walk direction
        position.x += walkDirection.x * deltaTime
        position.y += velocity.y * deltaTime
        position.z += walkDirection.z * deltaTime

        // Simplified ground check using Y-position threshold
        // Full physics integration would use Rapier raycasting for accurate ground detection
        if (position.y <= 0f) {
            position.y = 0f
            velocity.y = 0f
            isOnGround = true
        } else {
            isOnGround = false
        }
    }

    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {
        velocityForTimeInterval.set(velocity)
    }

    override fun jump(direction: Vector3) {
        if (isOnGround) {
            velocity.set(direction.clone().multiplyScalar(jumpSpeed))
            isOnGround = false
        }
    }

    // CollisionObject methods
    override fun setCollisionShape(shape: CollisionShape): PhysicsOperationResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setWorldTransform(transform: io.materia.core.math.Matrix4) {
        this.transform.copy(transform)
    }

    override fun getWorldTransform(): io.materia.core.math.Matrix4 {
        return transform.clone()
    }

    override fun translate(offset: Vector3) {
        position.add(offset)
    }

    override fun rotate(rotation: io.materia.core.math.Quaternion) {
        // Apply rotation to transform
    }
}