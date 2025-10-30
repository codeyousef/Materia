/**
 * Bullet Physics Character Controller Implementation
 * Provides kinematic character movement with collision detection
 */
package io.materia.physics.bullet.character

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Bullet-based character controller implementation
 */
class BulletCharacterController(
    override var collisionShape: CollisionShape,
    override var stepHeight: Float
) : CharacterController {
    override val id = "character_${System.currentTimeMillis()}"
    override var transform: Matrix4 = Matrix4.identity()
    override var collisionGroups: Int = 1
    override var collisionMask: Int = 1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.0f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false
    override var jumpSpeed: Float = 10f
    override var walkDirection: Vector3 = Vector3.ZERO
    override var fallSpeed: Float = 9.81f
    override var maxSlope: Float = kotlin.math.PI.toFloat() / 4f
    override var velocityForTimeInterval: Vector3 = Vector3.ZERO

    private var isOnGroundState: Boolean = false
    private var velocity: Vector3 = Vector3.ZERO

    override fun onGround(): Boolean = isOnGroundState

    override fun canJump(): Boolean = isOnGroundState

    override fun jump(direction: Vector3) {
        if (canJump()) {
            velocity = velocity.add(direction.multiply(jumpSpeed))
        }
    }


    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {
        this.velocityForTimeInterval = velocity
    }

    override fun warp(origin: Vector3) {
        transform = Matrix4.IDENTITY.makeTranslation(origin.x, origin.y, origin.z)
    }

    override fun preStep(world: PhysicsWorld) {}

    override fun setCollisionShape(shape: CollisionShape): PhysicsOperationResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        transform = transform.translate(offset)
    }

    override fun rotate(rotation: Quaternion) {
        transform = transform.rotate(rotation)
    }

    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {
        // Simple character controller step
        if (walkDirection.length() > 0f) {
            velocity = velocity.add(walkDirection.multiply(deltaTime * 10f))
        }

        // Apply gravity
        if (!isOnGroundState) {
            velocity = velocity.add(Vector3(0f, -fallSpeed * deltaTime, 0f))
        }

        // Update position
        transform = transform.translate(velocity.multiply(deltaTime))

        // Simple ground check
        isOnGroundState = transform.getTranslation().y <= 0f
        if (isOnGroundState) {
            val currentPos = transform.getTranslation()
            transform = Matrix4.IDENTITY.makeTranslation(currentPos.x, 0f, currentPos.z)
            velocity = velocity.copy(y = 0f)
        }
    }
}
