/**
 * Character controller implementation for player movement and interaction
 * Provides game-ready character physics with robust collision handling
 */
package io.materia.physics

import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.physics.character.*
import io.materia.physics.shapes.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos

/**
 * Comprehensive character controller implementation
 * Handles movement, jumping, slopes, steps, and platform interaction
 */
class CharacterControllerImpl(
    override val id: String,
    initialShape: CollisionShape,
    initialStepHeight: Float = 0.35f,
    initialTransform: Matrix4 = Matrix4.identity()
) : CharacterController {

    // CollisionObject implementation
    override var transform: Matrix4 = initialTransform
    override var collisionShape: CollisionShape = initialShape
    override var collisionGroups: Int = 1
    override var collisionMask: Int = -1
    override var userData: Any? = null
    override var contactProcessingThreshold: Float = 0.0f
    override var collisionFlags: Int = 0
    override var isTrigger: Boolean = false

    // CollisionObject methods
    override fun setCollisionShape(shape: CollisionShape): PhysicsResult<Unit> {
        collisionShape = shape
        return PhysicsOperationResult.Success(Unit)
    }


    override fun setWorldTransform(transform: Matrix4) {
        this.transform = transform
    }

    override fun getWorldTransform(): Matrix4 = transform

    override fun translate(offset: Vector3) {
        val position = transform.getPosition()
        position.add(offset)
        transform.setPosition(position)
    }

    override fun rotate(rotation: Quaternion) {
        val currentRotation = transform.getRotation()
        val newRotation = currentRotation * rotation
        val position = transform.getPosition()
        val scale = transform.getScale()
        transform = Matrix4.fromTranslationRotationScale(position, newRotation, scale)
    }

    // Movement properties
    override var stepHeight: Float = initialStepHeight
        set(value) {
            require(value >= 0f) { "Step height cannot be negative" }
            field = value
        }

    override var maxSlope: Float = PI.toFloat() / 4f // 45 degrees
        set(value) {
            require(value in 0f..PI.toFloat() / 2f) { "Max slope must be between 0 and 90 degrees" }
            field = value
        }

    override var jumpSpeed: Float = 5f
        set(value) {
            require(value >= 0f) { "Jump speed cannot be negative" }
            field = value
        }

    override var fallSpeed: Float = 20f
        set(value) {
            require(value >= 0f) { "Fall speed cannot be negative" }
            field = value
        }

    private val _walkDirection = MutableStateFlow(Vector3(0f, 0f, 0f))
    override var walkDirection: Vector3
        get() = _walkDirection.value.clone()
        set(value) {
            _walkDirection.value = value.clone()
        }

    private val _velocityForTimeInterval = MutableStateFlow(Vector3(0f, 0f, 0f))
    override var velocityForTimeInterval: Vector3
        get() = _velocityForTimeInterval.value.clone()
        set(value) {
            _velocityForTimeInterval.value = value.clone()
        }

    // Character state
    private val _onGround = MutableStateFlow(false)
    private val _canJump = MutableStateFlow(true)
    private var _verticalVelocity = 0f
    private var _wasOnGround = false
    private var _gravityEnabled = true

    // Ground detection
    private var _groundNormal = Vector3.UNIT_Y
    private var _groundObject: CollisionObject? = null
    private var _groundDistance = 0f
    private var _groundHitPoint = Vector3.ZERO

    // Movement settings
    var gravity: Float = -9.81f
    var maxStepHeight: Float = 0.35f
    var maxSlopeAngle: Float = PI.toFloat() / 4f // 45 degrees
    var skinWidth: Float = 0.08f
    var pushForce: Float = 1f
    var airControl: Float = 0.2f
    var groundStickiness: Float = 1f

    // Subsystems
    private val jumpSystem = JumpSystem(jumpSpeed, 0.1f, 0.15f)
    private val platformTracker = PlatformTracker()

    // State flows for reactive updates
    val onGroundFlow: StateFlow<Boolean> = _onGround.asStateFlow()
    val canJumpFlow: StateFlow<Boolean> = _canJump.asStateFlow()

    // Character dimensions (assumes capsule shape)
    private val characterRadius: Float
        get() = CharacterMetrics.getRadius(collisionShape)

    private val characterHeight: Float
        get() = CharacterMetrics.getHeight(collisionShape)

    override fun onGround(): Boolean = _onGround.value

    override fun canJump(): Boolean = jumpSystem.canJump(_onGround.value)

    override fun jump(direction: Vector3) {
        if (!canJump()) return

        jumpSystem.jump(direction) { vertVel, horizVel ->
            _verticalVelocity = vertVel
            _onGround.value = false
            _canJump.value = false
            if (horizVel != Vector3.ZERO) {
                velocityForTimeInterval = horizVel
            }
        }
    }


    override fun setVelocityForTimeInterval(velocity: Vector3, timeInterval: Float) {
        this.velocityForTimeInterval = velocity
    }

    override fun warp(origin: Vector3) {
        val newTransform = Matrix4.fromTranslationRotationScale(
            origin,
            transform.getRotation(),
            transform.getScale()
        )
        transform = newTransform
        _verticalVelocity = 0f
        _onGround.value = false
        _groundObject = null
    }

    override fun preStep(world: PhysicsWorld) {
        // Update platform tracking
        platformTracker.update(_groundObject)

        // Update ground detection
        val groundInfo = GroundDetector.check(
            world,
            transform.getTranslation(),
            stepHeight,
            skinWidth,
            maxSlopeAngle
        )

        val wasOnGround = _onGround.value
        _onGround.value = groundInfo.isOnGround
        _groundNormal = groundInfo.groundNormal
        _groundObject = groundInfo.groundObject
        _groundDistance = groundInfo.groundDistance
        _groundHitPoint = groundInfo.groundHitPoint

        // Reset jump ability when landing
        if (groundInfo.isOnGround && !wasOnGround) {
            _canJump.value = true
        }

        // Update jump state
        jumpSystem.update(0.016f, _onGround.value, wasOnGround, _verticalVelocity)
    }

    override fun playerStep(world: PhysicsWorld, deltaTime: Float) {
        if (deltaTime <= 0f) return

        val currentPosition = transform.getTranslation()
        var newPosition = currentPosition

        // Handle gravity and vertical movement
        if (_gravityEnabled) {
            if (!_onGround.value || _verticalVelocity > 0f) {
                _verticalVelocity = _verticalVelocity + gravity * deltaTime

                // Terminal velocity
                if (_verticalVelocity < -fallSpeed) {
                    _verticalVelocity = -fallSpeed
                }
            } else {
                // Stick to ground
                _verticalVelocity = 0f
            }
        }

        // Calculate movement vector
        var movement = Vector3.ZERO

        // Add walk direction (horizontal movement)
        val walkLen = walkDirection.length()
        if (walkLen > 0.001f) {
            val horizontalVec = Vector3(walkDirection.x, 0f, walkDirection.z)
            val horizontalLen = horizontalVec.length()
            val horizontalMovement = if (horizontalLen > 0.001f) {
                horizontalVec.normalize()
            } else {
                Vector3.ZERO
            }
            val movementSpeed = walkLen

            if (_onGround.value) {
                movement = horizontalMovement * (movementSpeed * deltaTime)
            } else {
                // Reduced air control
                movement = horizontalMovement * movementSpeed * (deltaTime * airControl)
            }
        }

        // Add velocity for time interval (external forces, pushes, etc.)
        if (velocityForTimeInterval.length() > 0.001f) {
            movement = movement + velocityForTimeInterval * deltaTime
            // Gradually reduce external velocity
            velocityForTimeInterval = velocityForTimeInterval * 0.95f
        }

        // Add vertical movement
        movement.y = movement.y + _verticalVelocity * deltaTime

        // Add platform velocity
        if (platformTracker.getPlatform() != null && _onGround.value) {
            movement = movement + (platformTracker.getVelocity() * deltaTime)
        }

        // Perform movement with collision detection
        newPosition = performMovement(world, currentPosition, movement, deltaTime)

        // Update transform
        val newTransform = Matrix4.fromTranslationRotationScale(
            newPosition,
            transform.getRotation(),
            transform.getScale()
        )
        transform = newTransform

        // Update state
        _wasOnGround = _onGround.value
        updateTimers(deltaTime)
    }

    private fun performMovement(
        world: PhysicsWorld,
        startPosition: Vector3,
        movement: Vector3,
        deltaTime: Float
    ): Vector3 {
        var currentPosition = startPosition
        var remainingMovement = movement

        // Separate horizontal and vertical movement
        val horizontalMovement = Vector3(remainingMovement.x, 0f, remainingMovement.z)
        val verticalMovement = Vector3(0f, remainingMovement.y, 0f)

        // Perform horizontal movement first
        if (horizontalMovement.length() > 0.001f) {
            currentPosition = performHorizontalMovement(world, currentPosition, horizontalMovement)
        }

        // Then perform vertical movement
        if (abs(verticalMovement.y) > 0.001f) {
            currentPosition = performVerticalMovement(world, currentPosition, verticalMovement)
        }

        return currentPosition
    }

    private fun performHorizontalMovement(
        world: PhysicsWorld,
        startPosition: Vector3,
        movement: Vector3
    ): Vector3 {
        return MovementSystem.performHorizontal(
            world,
            startPosition,
            movement,
            characterRadius,
            characterHeight,
            skinWidth,
            maxSlopeAngle
        )
    }

    private fun performVerticalMovement(
        world: PhysicsWorld,
        startPosition: Vector3,
        movement: Vector3
    ): Vector3 {
        return MovementSystem.performVertical(
            world,
            startPosition,
            movement,
            characterRadius,
            characterHeight,
            skinWidth,
            maxSlopeAngle
        ) { isGround, normal, obj ->
            if (isGround) {
                _onGround.value = true
                _groundNormal = normal ?: Vector3.UNIT_Y
                _groundObject = obj as? CollisionObject
                _verticalVelocity = 0f
            } else if (movement.y > 0f) {
                _verticalVelocity = 0f
            }
        }
    }

    private fun updateTimers(deltaTime: Float) {
        // Timers are now handled by JumpSystem
    }

    /**
     * Configure character movement parameters
     */
    fun configureMovement(
        gravity: Float = this.gravity,
        jumpSpeed: Float = this.jumpSpeed,
        fallSpeed: Float = this.fallSpeed,
        stepHeight: Float = this.stepHeight,
        maxSlope: Float = this.maxSlope,
        airControl: Float = this.airControl
    ) {
        this.gravity = gravity
        this.jumpSpeed = jumpSpeed
        this.fallSpeed = fallSpeed
        this.stepHeight = stepHeight
        this.maxSlope = maxSlope
        this.airControl = airControl
    }

    /**
     * Configure collision parameters
     */
    fun configureCollision(
        skinWidth: Float = this.skinWidth,
        pushForce: Float = this.pushForce,
        groundStickiness: Float = this.groundStickiness
    ) {
        this.skinWidth = skinWidth
        this.pushForce = pushForce
        this.groundStickiness = groundStickiness
    }

    /**
     * Configure timing parameters
     */
    fun configureTiming(
        jumpGraceTime: Float = jumpSystem.jumpGraceTime,
        coyoteTime: Float = jumpSystem.coyoteTime
    ) {
        jumpSystem.jumpGraceTime = jumpGraceTime
        jumpSystem.coyoteTime = coyoteTime
    }

    /**
     * Get character state information
     */
    fun getCharacterState(): CharacterState {
        return CharacterState(
            position = transform.getTranslation(),
            onGround = _onGround.value,
            canJump = _canJump.value,
            verticalVelocity = _verticalVelocity,
            groundNormal = _groundNormal,
            groundDistance = _groundDistance,
            platformVelocity = platformTracker.getVelocity(),
            currentPlatform = platformTracker.getPlatform()
        )
    }

    /**
     * Push the character (for external forces)
     */
    fun pushCharacter(force: Vector3, mode: PushMode = PushMode.VELOCITY_CHANGE) {
        when (mode) {
            PushMode.FORCE -> {
                // Apply as continuous force integrated over fixed timestep
                velocityForTimeInterval = velocityForTimeInterval + force * 0.1f
            }

            PushMode.IMPULSE -> {
                // Apply as immediate impulse
                velocityForTimeInterval = velocityForTimeInterval + force
            }

            PushMode.VELOCITY_CHANGE -> {
                // Replace current velocity
                velocityForTimeInterval = force
            }
        }
    }

    /**
     * Teleport character to new position
     */
    fun teleport(newPosition: Vector3, resetVelocity: Boolean = true) {
        warp(newPosition)
        if (resetVelocity) {
            velocityForTimeInterval = Vector3.ZERO
            _verticalVelocity = 0f
        }
    }

    /**
     * Check if character can move to a specific position
     */
    fun canMoveTo(world: PhysicsWorld, targetPosition: Vector3): Boolean {
        val currentPosition = transform.getTranslation()
        val sweepResult = SweepTester.sweep(
            world,
            currentPosition,
            targetPosition,
            characterRadius,
            characterHeight
        )
        return !sweepResult.hasHit
    }

    /**
     * Get the closest walkable position to a target
     */
    fun getClosestWalkablePosition(world: PhysicsWorld, targetPosition: Vector3): Vector3 {
        val currentPosition = transform.getTranslation()
        val sweepResult = SweepTester.sweep(
            world,
            currentPosition,
            targetPosition,
            characterRadius,
            characterHeight
        )

        return if (sweepResult.hasHit) {
            val directionVec = targetPosition - currentPosition
            val dirLen = directionVec.length()
            val direction = if (dirLen > 0.001f) {
                directionVec.normalize()
            } else {
                return currentPosition // Already at target
            }
            val safeDistance = maxOf(0f, sweepResult.distance - skinWidth)
            currentPosition + (direction * safeDistance)
        } else {
            targetPosition
        }
    }
}

/**
 * Character state data class
 */
data class CharacterState(
    val position: Vector3,
    val onGround: Boolean,
    val canJump: Boolean,
    val verticalVelocity: Float,
    val groundNormal: Vector3,
    val groundDistance: Float,
    val platformVelocity: Vector3,
    val currentPlatform: CollisionObject?
)

/**
 * Push mode for character forces
 */
enum class PushMode {
    FORCE,           // Apply as continuous force
    IMPULSE,         // Apply as immediate impulse
    VELOCITY_CHANGE  // Replace current velocity
}

/**
 * Factory for creating character controllers
 */
object CharacterControllerFactory {

    /**
     * Create a standard humanoid character controller
     */
    fun createHumanoidController(
        id: String,
        height: Float = 1.8f,
        radius: Float = 0.3f,
        position: Vector3 = Vector3.ZERO
    ): CharacterController {
        val capsuleShape = CapsuleShapeImpl(radius, height - (radius * 2f))
        val transform = Matrix4.fromTranslation(position)

        return CharacterControllerImpl(id, capsuleShape, height * 0.2f, transform).apply {
            configureMovement(
                gravity = -9.81f,
                jumpSpeed = 5f,
                fallSpeed = 20f,
                stepHeight = height * 0.2f,
                maxSlope = PI.toFloat() / 4f,
                airControl = 0.2f
            )
        }
    }

    /**
     * Create a small character controller (for child characters, creatures, etc.)
     */
    fun createSmallController(
        id: String,
        height: Float = 1.2f,
        radius: Float = 0.2f,
        position: Vector3 = Vector3.ZERO
    ): CharacterController {
        val capsuleShape = CapsuleShapeImpl(radius, height - (radius * 2f))
        val transform = Matrix4.fromTranslation(position)

        return CharacterControllerImpl(id, capsuleShape, height * 0.15f, transform).apply {
            configureMovement(
                gravity = -9.81f,
                jumpSpeed = 3f,
                fallSpeed = 15f,
                stepHeight = height * 0.15f,
                maxSlope = PI.toFloat() / 3f, // Steeper slopes allowed
                airControl = 0.3f
            )
        }
    }

    /**
     * Create a vehicle-like character controller
     */
    fun createVehicleController(
        id: String,
        length: Float = 4f,
        width: Float = 2f,
        height: Float = 1.5f,
        position: Vector3 = Vector3.ZERO
    ): CharacterController {
        val boxShape = BoxShapeImpl(Vector3(length * 0.5f, height * 0.5f, width * 0.5f))
        val transform = Matrix4.fromTranslation(position)

        return CharacterControllerImpl(id, boxShape, 0.1f, transform).apply {
            configureMovement(
                gravity = -9.81f,
                jumpSpeed = 0f, // Vehicles don't jump
                fallSpeed = 30f,
                stepHeight = 0.1f,
                maxSlope = PI.toFloat() / 6f, // 30 degrees max
                airControl = 0.1f
            )
        }
    }

    /**
     * Create a floating character controller (for flying characters)
     */
    fun createFloatingController(
        id: String,
        radius: Float = 0.5f,
        position: Vector3 = Vector3.ZERO
    ): CharacterController {
        val sphereShape = SphereShapeImpl(radius)
        val transform = Matrix4.fromTranslation(position)

        return CharacterControllerImpl(id, sphereShape, 0f, transform).apply {
            gravity = 0f // No gravity for floating controllers
            configureMovement(
                gravity = 0f,
                jumpSpeed = 5f,
                fallSpeed = 5f,
                stepHeight = 0f,
                maxSlope = PI.toFloat() / 2f, // Can move on any surface
                airControl = 1f // Full air control
            )
        }
    }
}