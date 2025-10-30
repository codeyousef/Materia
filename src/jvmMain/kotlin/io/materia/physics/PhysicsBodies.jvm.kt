package io.materia.physics

import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import java.util.*

/**
 * JVM implementation of rigid body
 */
class JvmRigidBody(
    override var collisionShape: CollisionShape,
    override var mass: Float,
    override var transform: Matrix4
) : RigidBody {

    override val id: String = UUID.randomUUID().toString()
    override var collisionGroups: Int = -1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.01f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    // Physical properties
    override var density: Float = 1000f // kg/m³ like water
    override var restitution: Float = 0f
    override var friction: Float = 0.5f
    override var rollingFriction: Float = 0f
    override var linearDamping: Float = 0f
    override var angularDamping: Float = 0f

    // Motion state
    override var linearVelocity: Vector3 = Vector3.ZERO
    override var angularVelocity: Vector3 = Vector3.ZERO
    override var linearFactor: Vector3 = Vector3.ONE
    override var angularFactor: Vector3 = Vector3.ONE

    // Body type and state
    override var bodyType: RigidBodyType =
        if (mass > 0f) RigidBodyType.DYNAMIC else RigidBodyType.STATIC
    override var activationState: ActivationState = ActivationState.ACTIVE
    override var sleepThreshold: Float = 0.8f
    override var ccdMotionThreshold: Float = 0f
    override var ccdSweptSphereRadius: Float = 0f

    // Internal forces
    private var totalForce: Vector3 = Vector3.ZERO
    private var totalTorque: Vector3 = Vector3.ZERO
    private val _inertia: Matrix3 by lazy { collisionShape.calculateInertia(mass) }
    private val _inverseInertia: Matrix3 by lazy { _inertia.invert() }

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        val currentPos = transform.getTranslation()
        val newPos = Vector3(
            currentPos.x + offset.x,
            currentPos.y + offset.y,
            currentPos.z + offset.z
        )
        transform = Matrix4().makeTranslation(newPos.x, newPos.y, newPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(transform.getRotation())
        )
    }

    override fun rotate(rotation: Quaternion) {
        val currentPos = transform.getTranslation()
        val currentRot = transform.getRotation()
        val newRot = currentRot.multiply(rotation)
        transform = Matrix4().makeTranslation(currentPos.x, currentPos.y, currentPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(newRot)
        )
    }

    override fun applyForce(force: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        if (bodyType != RigidBodyType.DYNAMIC) {
            return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply force to non-dynamic body"))
        }

        totalForce = totalForce.plus(force.times(linearFactor))
        if (relativePosition != Vector3.ZERO) {
            totalTorque = totalTorque.plus(relativePosition.cross(force).times(angularFactor))
        }

        activationState = ActivationState.ACTIVE
        return PhysicsOperationResult.Success(Unit)
    }

    override fun applyImpulse(impulse: Vector3, relativePosition: Vector3): PhysicsResult<Unit> {
        if (bodyType != RigidBodyType.DYNAMIC) {
            return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply impulse to non-dynamic body"))
        }

        if (mass > 0f) {
            linearVelocity = linearVelocity.plus(impulse.div(mass).times(linearFactor))

            if (relativePosition != Vector3.ZERO) {
                val torqueImpulse = relativePosition.cross(impulse)
                angularVelocity =
                    angularVelocity.plus(_inverseInertia.times(torqueImpulse).times(angularFactor))
            }
        }

        activationState = ActivationState.ACTIVE
        return PhysicsOperationResult.Success(Unit)
    }

    override fun applyTorque(torque: Vector3): PhysicsResult<Unit> {
        if (bodyType != RigidBodyType.DYNAMIC) {
            return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply torque to non-dynamic body"))
        }

        totalTorque = totalTorque.plus(torque.times(angularFactor))
        activationState = ActivationState.ACTIVE
        return PhysicsOperationResult.Success(Unit)
    }

    override fun applyTorqueImpulse(torque: Vector3): PhysicsResult<Unit> {
        if (bodyType != RigidBodyType.DYNAMIC) {
            return PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Cannot apply torque impulse to non-dynamic body"))
        }

        angularVelocity = angularVelocity.plus(_inverseInertia.times(torque).times(angularFactor))
        activationState = ActivationState.ACTIVE
        return PhysicsOperationResult.Success(Unit)
    }

    override fun applyCentralForce(force: Vector3): PhysicsResult<Unit> {
        return applyForce(force, Vector3.ZERO)
    }

    override fun applyCentralImpulse(impulse: Vector3): PhysicsResult<Unit> {
        return applyImpulse(impulse, Vector3.ZERO)
    }

    override fun isActive(): Boolean = activationState == ActivationState.ACTIVE
    override fun isKinematic(): Boolean = bodyType == RigidBodyType.KINEMATIC
    override fun isStatic(): Boolean = bodyType == RigidBodyType.STATIC

    override fun getInertia(): Matrix3 = _inertia
    override fun getInverseInertia(): Matrix3 = _inverseInertia
    override fun getTotalForce(): Vector3 = totalForce
    override fun getTotalTorque(): Vector3 = totalTorque

    override fun setTransform(position: Vector3, rotation: Quaternion) {
        transform = Matrix4().makeTranslation(position.x, position.y, position.z).multiply(
            Matrix4().makeRotationFromQuaternion(rotation)
        )
    }

    override fun getCenterOfMassTransform(): Matrix4 = transform

    fun clearForces() {
        totalForce = Vector3.ZERO
        totalTorque = Vector3.ZERO
    }

    fun integrateVelocities(deltaTime: Float) {
        if (bodyType == RigidBodyType.DYNAMIC && mass > 0f) {
            // Linear integration
            linearVelocity =
                linearVelocity.plus(totalForce.div(mass).times(deltaTime).times(linearFactor))

            // Angular integration
            angularVelocity =
                angularVelocity.plus(
                    _inverseInertia.times(totalTorque).times(deltaTime).times(angularFactor)
                )

            // Apply damping
            linearVelocity = linearVelocity.times((1f - linearDamping).coerceAtLeast(0f))
            angularVelocity = angularVelocity.times((1f - angularDamping).coerceAtLeast(0f))

            // Check for deactivation
            val linVelSq = linearVelocity.lengthSquared()
            val angVelSq = angularVelocity.lengthSquared()

            if (linVelSq < sleepThreshold * sleepThreshold &&
                angVelSq < sleepThreshold * sleepThreshold
            ) {
                if (activationState == ActivationState.ACTIVE) {
                    activationState = ActivationState.WANTS_DEACTIVATION
                }
            } else {
                activationState = ActivationState.ACTIVE
            }
        }
    }
}

/**
 * JVM implementation of character controller
 */
class JvmCharacterController(
    override var collisionShape: CollisionShape,
    override var stepHeight: Float
) : CharacterController {

    override val id: String = UUID.randomUUID().toString()
    override var transform: Matrix4 = Matrix4.IDENTITY
    override var collisionGroups: Int = -1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.01f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    // Movement properties
    override var maxSlope: Float = 45f * (kotlin.math.PI / 180f).toFloat()
    override var jumpSpeed: Float = 10f
    override var fallSpeed: Float = 55f
    override var walkDirection: Vector3 = Vector3.ZERO
    override var velocityForTimeInterval: Vector3 = Vector3.ZERO

    private var verticalVelocity: Float = 0f
    private var verticalOffset: Float = 0f
    private var isOnGround: Boolean = false
    private var canJumpFlag: Boolean = true

    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }

    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        val currentPos = transform.getTranslation()
        val newPos = Vector3(
            currentPos.x + offset.x,
            currentPos.y + offset.y,
            currentPos.z + offset.z
        )
        transform = Matrix4().makeTranslation(newPos.x, newPos.y, newPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(transform.getRotation())
        )
    }

    override fun rotate(rotation: Quaternion) {
        val currentPos = transform.getTranslation()
        val currentRot = transform.getRotation()
        val newRot = currentRot.multiply(rotation)
        transform = Matrix4().makeTranslation(currentPos.x, currentPos.y, currentPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(newRot)
        )
    }

    override fun onGround(): Boolean = isOnGround

    override fun canJump(): Boolean = isOnGround && canJumpFlag

    override fun jump(direction: Vector3) {
        if (canJump()) {
            verticalVelocity = jumpSpeed
            isOnGround = false
            canJumpFlag = false

            // Add horizontal component if specified
            if (direction != Vector3.UNIT_Y) {
                val horizontalDir = Vector3(direction.x, 0f, direction.z).normalize()
                velocityForTimeInterval =
                    velocityForTimeInterval.plus(horizontalDir.times(jumpSpeed * 0.5f))
            }
        }
    }

    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {
        velocityForTimeInterval = velocity
    }

    override fun warp(origin: Vector3) {
        transform = Matrix4().makeTranslation(origin.x, origin.y, origin.z).multiply(
            Matrix4().makeRotationFromQuaternion(transform.getRotation())
        )
        verticalVelocity = 0f
        velocityForTimeInterval = Vector3.ZERO
    }

    override fun preStep(world: PhysicsWorld) {
        // Reset on-ground flag before collision detection
        isOnGround = false
    }

    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {
        // Apply gravity
        if (!isOnGround) {
            verticalVelocity -= world.gravity.y * deltaTime
            verticalVelocity = maxOf(verticalVelocity, -fallSpeed)
        }

        // Calculate movement
        val currentPos = transform.getTranslation()
        var movement = walkDirection.times(deltaTime)
        movement = movement.copy(y = movement.y + verticalVelocity * deltaTime)

        // Add velocity for time interval
        movement = movement.plus(velocityForTimeInterval.times(deltaTime))

        // Simple ground check (would need actual collision detection)
        val newPos = currentPos.plus(movement)

        // Perform step up if needed
        if (isOnGround && kotlin.math.abs(movement.y) < stepHeight) {
            // Can step up
            newPos.y = maxOf(newPos.y, currentPos.y)
        }

        // Update position
        transform = Matrix4().makeTranslation(newPos.x, newPos.y, newPos.z).multiply(
            Matrix4().makeRotationFromQuaternion(transform.getRotation())
        )

        // Simple ground detection (y = 0 plane)
        if (newPos.y <= 0f) {
            isOnGround = true
            verticalVelocity = 0f
            canJumpFlag = true

            // Clamp to ground
            val groundPos = newPos.copy(y = 0f)
            transform = Matrix4().makeTranslation(groundPos.x, groundPos.y, groundPos.z).multiply(
                Matrix4().makeRotationFromQuaternion(transform.getRotation())
            )
        }

        // Clear per-frame velocity
        velocityForTimeInterval = Vector3.ZERO
    }
}

/**
 * RaycastResult implementation for JVM
 */
class JvmRaycastResult(
    private val collisionObject: CollisionObject,
    private val point: Vector3,
    private val normal: Vector3,
    private val distanceValue: Float
) : io.materia.physics.RaycastResult {
    override val hasHit: Boolean = true
    override val hitObject: CollisionObject = collisionObject
    override val hitPoint: Vector3 = point
    override val hitNormal: Vector3 = normal
    override val hitFraction: Float = distanceValue
    override val distance: Float = distanceValue
}