package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Euler
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * First-person camera controls implementation
 * Provides WASD movement and mouse look controls similar to FPS games
 */
class FirstPersonControls(
    camera: Camera,
    config: ControlsConfig = ControlsConfig()
) : BaseCameraControls(camera, config) {

    // Movement state
    private val velocity = Vector3()
    private val direction = Vector3()

    // Look state
    private var yaw: Float = 0f      // Horizontal rotation
    private var pitch: Float = 0f    // Vertical rotation

    // Mouse sensitivity
    var mouseSensitivity: Float = 0.002f

    // Movement speeds
    var walkSpeed: Float = 5f
    var runSpeed: Float = 10f
    var jumpSpeed: Float = 8f

    // Physics simulation
    var enableGravity: Boolean = true
    var gravity: Float = -9.8f
    var isGrounded: Boolean = true
    var groundHeight: Float = 0f

    // Collision detection
    var enableCollision: Boolean = false
    var collisionRadius: Float = 0.5f

    // Smooth movement
    var enableSmoothing: Boolean = true
    var accelerationFactor: Float = 10f
    var decelerationFactor: Float = 15f

    // Working vectors
    private val forward = Vector3()
    private val right = Vector3()
    private val up = Vector3(0f, 1f, 0f)
    private val worldUp = Vector3(0f, 1f, 0f)

    // Input state
    private var pointerLocked = false
    private val lastMousePosition = Vector2()

    init {
        // Initialize look angles from camera rotation
        val euler = Euler().setFromQuaternion(camera.quaternion)
        yaw = euler.y
        pitch = euler.x

        // Constrain pitch
        pitch = pitch.coerceIn(-PI.toFloat() / 2f + 0.01f, PI.toFloat() / 2f - 0.01f)
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        var hasChanged = false

        // Handle movement input
        if (config.enableKeys) {
            hasChanged = updateMovement(deltaTime) || hasChanged
        }

        // Apply physics
        if (enableGravity) {
            hasChanged = updatePhysics(deltaTime) || hasChanged
        }

        // Update camera look direction
        hasChanged = updateLookDirection() || hasChanged

        if (hasChanged) {
            dispatchEvent("change")
        }
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        if (!enabled || !config.enableRotate) return

        // Only process mouse look if we have pointer lock or are dragging
        if (pointerLocked || state.isPointerDown) {
            val mouseDeltaX = deltaX * mouseSensitivity * config.rotateSpeed
            val mouseDeltaY = deltaY * mouseSensitivity * config.rotateSpeed

            yaw -= mouseDeltaX
            pitch -= mouseDeltaY

            // Constrain pitch to prevent camera flipping
            pitch = pitch.coerceIn(-PI.toFloat() / 2f + 0.01f, PI.toFloat() / 2f - 0.01f)

            // Wrap yaw around 2π
            if (yaw > PI) yaw -= 2f * PI.toFloat()
            if (yaw < -PI) yaw += 2f * PI.toFloat()

            dispatchEvent("change")
        }
    }

    override fun onPointerDown(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        state.isPointerDown = true
        state.pointerButton = button
        lastMousePosition.set(x, y)
        dispatchEvent("start")
    }

    override fun onPointerUp(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        state.isPointerDown = false
        dispatchEvent("end")
    }

    override fun onWheel(deltaX: Float, deltaY: Float) {
        // First person controls typically don't use wheel input
        // Could be used for weapon switching or other game mechanics
    }

    override fun onKeyDown(key: Key) {
        if (!enabled || !config.enableKeys) return
        state.keysDown.add(key)
    }

    override fun onKeyUp(key: Key) {
        if (!enabled || !config.enableKeys) return
        state.keysDown.remove(key)
    }

    override fun lookAt(target: Vector3, duration: Float) {
        // Calculate look angles to target
        val direction = target.clone().sub(camera.position)
        val dirLength = direction.length()
        if (dirLength > 0.001f) {
            direction.normalize()
        } else {
            // Default to forward if target is too close
            direction.set(0f, 0f, -1f)
        }

        yaw = atan2(direction.x, direction.z)
        pitch = asin((-direction.y).coerceIn(-1f, 1f)).coerceIn(
            -PI.toFloat() / 2f + 0.01f,
            PI.toFloat() / 2f - 0.01f
        )

        updateLookDirection()
        dispatchEvent("change")
    }

    // Movement update
    private fun updateMovement(deltaTime: Float): Boolean {
        val inputDirection = Vector3()
        var hasInput = false

        // Calculate input direction
        if (Key.W in state.keysDown) {
            inputDirection.z -= 1f
            hasInput = true
        }
        if (Key.S in state.keysDown) {
            inputDirection.z += 1f
            hasInput = true
        }
        if (Key.A in state.keysDown) {
            inputDirection.x -= 1f
            hasInput = true
        }
        if (Key.D in state.keysDown) {
            inputDirection.x += 1f
            hasInput = true
        }

        // Normalize input direction
        if (hasInput) {
            val inputLength = inputDirection.length()
            if (inputLength > 0.001f) {
                inputDirection.normalize()
            }
        }

        // Calculate forward and right vectors
        forward.set(-sin(yaw), 0f, -cos(yaw))
        right.set(cos(yaw), 0f, -sin(yaw))

        // Calculate target velocity
        val targetVelocity = Vector3()
        if (hasInput) {
            val currentSpeed = if (Key.SHIFT in state.keysDown) runSpeed else walkSpeed

            targetVelocity.add(forward.clone().multiplyScalar(inputDirection.z * currentSpeed))
            targetVelocity.add(right.clone().multiplyScalar(inputDirection.x * currentSpeed))
        }

        // Apply smoothing or direct movement
        val previousPosition = camera.position.clone()

        if (enableSmoothing) {
            // Smooth acceleration/deceleration
            val factor = if (hasInput) accelerationFactor else decelerationFactor
            velocity.lerp(targetVelocity, factor * deltaTime)
        } else {
            // Direct movement
            velocity.copy(targetVelocity)
        }

        // Handle jumping
        if (Key.SPACE in state.keysDown && isGrounded && enableGravity) {
            velocity.y = jumpSpeed
            isGrounded = false
        }

        // Apply horizontal movement
        val horizontalMovement = Vector3(velocity.x, 0f, velocity.z).multiplyScalar(deltaTime)
        camera.position.add(horizontalMovement)

        // Check if position changed
        return !camera.position.equals(previousPosition)
    }

    // Physics update
    private fun updatePhysics(deltaTime: Float): Boolean {
        val previousPosition = camera.position.clone()

        // Apply gravity
        if (!isGrounded) {
            velocity.y += gravity * deltaTime
        }

        // Apply vertical movement
        camera.position.y += velocity.y * deltaTime

        // Ground collision
        if (camera.position.y <= groundHeight) {
            camera.position.y = groundHeight
            velocity.y = 0f
            isGrounded = true
        } else {
            isGrounded = false
        }

        // Wall collision (simplified sphere collision)
        if (enableCollision) {
            // Simplified boundary collision
            // Physics engine integration recommended for complex collision detection
            val minX = -100f + collisionRadius
            val maxX = 100f - collisionRadius
            val minZ = -100f + collisionRadius
            val maxZ = 100f - collisionRadius

            camera.position.x = camera.position.x.coerceIn(minX, maxX)
            camera.position.z = camera.position.z.coerceIn(minZ, maxZ)
        }

        return !camera.position.equals(previousPosition)
    }

    // Look direction update
    private fun updateLookDirection(): Boolean {
        val previousQuaternion = camera.quaternion.clone()

        // Create rotation quaternion from yaw and pitch
        val yawQuaternion = Quaternion().setFromAxisAngle(worldUp, yaw)
        val pitchQuaternion = Quaternion().setFromAxisAngle(Vector3(1f, 0f, 0f), pitch)

        camera.quaternion.multiplyQuaternions(yawQuaternion, pitchQuaternion)

        return !camera.quaternion.equals(previousQuaternion)
    }

    /**
     * Enable pointer lock (for web platform)
     */
    fun requestPointerLock() {
        pointerLocked = true
    }

    /**
     * Disable pointer lock
     */
    fun exitPointerLock() {
        pointerLocked = false
    }

    /**
     * Check if pointer is locked
     */
    fun isPointerLocked(): Boolean = pointerLocked

    /**
     * Set camera position
     */
    fun setPosition(position: Vector3) {
        camera.position.copy(position)

        // Ensure we're above ground
        if (enableGravity && camera.position.y < groundHeight) {
            camera.position.y = groundHeight
            isGrounded = true
        }
    }

    /**
     * Get movement velocity
     */
    fun getVelocity(): Vector3 = velocity.clone()

    /**
     * Set movement velocity
     */
    fun setVelocity(vel: Vector3) {
        velocity.copy(vel)
    }

    /**
     * Get look angles
     */
    fun getLookAngles(): Vector2 = Vector2(yaw, pitch)

    /**
     * Set look angles
     */
    fun setLookAngles(yawAngle: Float, pitchAngle: Float) {
        yaw = yawAngle
        pitch = pitchAngle.coerceIn(-PI.toFloat() / 2f + 0.01f, PI.toFloat() / 2f - 0.01f)
        updateLookDirection()
    }

    /**
     * Get forward direction vector
     */
    fun getForwardDirection(): Vector3 {
        return Vector3(-sin(yaw), 0f, -cos(yaw))
    }

    /**
     * Get right direction vector
     */
    fun getRightDirection(): Vector3 {
        return Vector3(cos(yaw), 0f, -sin(yaw))
    }

    /**
     * Teleport to position (no physics)
     */
    fun teleport(position: Vector3) {
        camera.position.copy(position)
        velocity.set(0f, 0f, 0f)

        if (enableGravity) {
            isGrounded = position.y <= groundHeight + 0.1f
        }
    }

    /**
     * Reset to initial state
     */
    override fun reset() {
        super.reset()
        velocity.set(0f, 0f, 0f)
        yaw = 0f
        pitch = 0f
        isGrounded = true
        pointerLocked = false
        updateLookDirection()
    }
}