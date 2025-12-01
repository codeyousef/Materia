package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import kotlin.math.PI

/**
 * Flight simulator style camera controls.
 * 
 * Provides first-person flight controls where the camera can move freely
 * in 3D space with pitch and yaw rotation. Similar to Three.js FlyControls.
 *
 * Controls:
 * - W/S: Forward/backward
 * - A/D: Strafe left/right
 * - Q/E: Roll left/right
 * - R/F: Up/down
 * - Mouse: Look around (pitch/yaw)
 *
 * Usage:
 * ```kotlin
 * val controls = FlyControls(camera)
 * controls.movementSpeed = 10f
 * controls.rollSpeed = 0.5f
 * 
 * // In render loop
 * controls.update(deltaTime)
 * 
 * // Handle input
 * controls.onKeyDown(Key.W)
 * controls.onPointerMove(deltaX, deltaY)
 * ```
 */
class FlyControls(
    override var camera: Camera,
    config: FlyControlsConfig = FlyControlsConfig()
) : CameraControls {

    /**
     * Configuration for fly controls
     */
    data class FlyControlsConfig(
        val movementSpeed: Float = 1f,
        val rollSpeed: Float = 0.005f,
        val dragToLook: Boolean = false,
        val autoForward: Boolean = false
    )

    // Configuration
    private val config = config.copy()

    override var enabled: Boolean = true
    override var target: Vector3 = Vector3()

    /** Movement speed multiplier */
    var movementSpeed: Float = config.movementSpeed

    /** Roll rotation speed */
    var rollSpeed: Float = config.rollSpeed

    /** If true, must hold mouse button to look */
    var dragToLook: Boolean = config.dragToLook

    /** If true, automatically moves forward */
    var autoForward: Boolean = config.autoForward

    // Movement state
    private var moveForward: Boolean = false
    private var moveBackward: Boolean = false
    private var moveLeft: Boolean = false
    private var moveRight: Boolean = false
    private var moveUp: Boolean = false
    private var moveDown: Boolean = false
    private var rollLeft: Boolean = false
    private var rollRight: Boolean = false

    // Look state
    private var viewHalfX: Float = 0f
    private var viewHalfY: Float = 0f
    private var mouseX: Float = 0f
    private var mouseY: Float = 0f
    private var isMouseDown: Boolean = false

    // Working objects
    private val moveVector = Vector3()
    private val rotationVector = Vector3()
    private val quaternion = Quaternion()
    private val lastQuaternion = Quaternion()
    private val lastPosition = Vector3()

    // Event listeners
    private val listeners = mutableListOf<ControlsEventListener>()

    init {
        // Initialize quaternion from camera
        quaternion.copy(camera.quaternion)
    }

    /**
     * Set the viewport dimensions for mouse calculations
     */
    fun setViewport(width: Float, height: Float) {
        viewHalfX = width / 2f
        viewHalfY = height / 2f
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        val delta = deltaTime

        // Calculate movement direction
        moveVector.set(0f, 0f, 0f)

        // Forward/backward
        if (moveForward || (autoForward && !moveBackward)) {
            moveVector.z = -1f
        }
        if (moveBackward) {
            moveVector.z = 1f
        }

        // Left/right
        if (moveLeft) {
            moveVector.x = -1f
        }
        if (moveRight) {
            moveVector.x = 1f
        }

        // Up/down
        if (moveUp) {
            moveVector.y = 1f
        }
        if (moveDown) {
            moveVector.y = -1f
        }

        // Normalize and apply speed
        if (moveVector.lengthSquared() > 0) {
            moveVector.normalize()
        }
        moveVector.multiplyScalar(movementSpeed * delta)

        // Apply movement in camera's local space
        val forward = Vector3(0f, 0f, -1f).applyQuaternion(camera.quaternion)
        val right = Vector3(1f, 0f, 0f).applyQuaternion(camera.quaternion)
        val up = Vector3(0f, 1f, 0f).applyQuaternion(camera.quaternion)

        camera.position.addScaledVector(forward, -moveVector.z)
        camera.position.addScaledVector(right, moveVector.x)
        camera.position.addScaledVector(up, moveVector.y)

        // Calculate rotation
        rotationVector.set(0f, 0f, 0f)

        // Roll
        if (rollLeft) {
            rotationVector.z = 1f
        }
        if (rollRight) {
            rotationVector.z = -1f
        }

        // Mouse look (pitch and yaw)
        if (!dragToLook || isMouseDown) {
            rotationVector.x = -mouseY * 0.002f  // Pitch
            rotationVector.y = -mouseX * 0.002f  // Yaw
        }

        rotationVector.multiplyScalar(rollSpeed * delta * 100f)

        // Apply rotation
        val pitchQuat = Quaternion().setFromAxisAngle(Vector3(1f, 0f, 0f), rotationVector.x)
        val yawQuat = Quaternion().setFromAxisAngle(Vector3(0f, 1f, 0f), rotationVector.y)
        val rollQuat = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), rotationVector.z)

        camera.quaternion.multiply(pitchQuat)
        camera.quaternion.premultiply(yawQuat)
        camera.quaternion.multiply(rollQuat)

        // Normalize to prevent accumulation of floating-point errors
        camera.quaternion.normalize()

        // Update target based on camera direction
        target.copy(camera.position)
        target.add(Vector3(0f, 0f, -1f).applyQuaternion(camera.quaternion))

        // Dispatch change event if position or rotation changed
        if (
            lastPosition.distanceToSquared(camera.position) > 0.0001f ||
            !quaternionsEqual(lastQuaternion, camera.quaternion)
        ) {
            dispatchEvent("change")
            lastPosition.copy(camera.position)
            lastQuaternion.copy(camera.quaternion)
        }

        // Reset mouse movement for next frame
        mouseX = 0f
        mouseY = 0f
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        mouseX = deltaX
        mouseY = deltaY
    }

    override fun onPointerDown(x: Float, y: Float, button: PointerButton) {
        isMouseDown = true
        dispatchEvent("start")
    }

    override fun onPointerUp(x: Float, y: Float, button: PointerButton) {
        isMouseDown = false
        dispatchEvent("end")
    }

    override fun onWheel(deltaX: Float, deltaY: Float) {
        // Wheel can adjust movement speed
        movementSpeed += deltaY * 0.1f
        movementSpeed = movementSpeed.coerceIn(0.1f, 100f)
    }

    override fun onKeyDown(key: Key) {
        when (key) {
            Key.W, Key.ARROW_UP -> moveForward = true
            Key.S, Key.ARROW_DOWN -> moveBackward = true
            Key.A, Key.ARROW_LEFT -> moveLeft = true
            Key.D, Key.ARROW_RIGHT -> moveRight = true
            Key.Q -> rollLeft = true
            Key.E -> rollRight = true
            Key.SPACE -> moveUp = true
            Key.SHIFT -> moveDown = true
            else -> {}
        }
    }

    override fun onKeyUp(key: Key) {
        when (key) {
            Key.W, Key.ARROW_UP -> moveForward = false
            Key.S, Key.ARROW_DOWN -> moveBackward = false
            Key.A, Key.ARROW_LEFT -> moveLeft = false
            Key.D, Key.ARROW_RIGHT -> moveRight = false
            Key.Q -> rollLeft = false
            Key.E -> rollRight = false
            Key.SPACE -> moveUp = false
            Key.SHIFT -> moveDown = false
            else -> {}
        }
    }

    override fun reset() {
        moveForward = false
        moveBackward = false
        moveLeft = false
        moveRight = false
        moveUp = false
        moveDown = false
        rollLeft = false
        rollRight = false
        mouseX = 0f
        mouseY = 0f
        isMouseDown = false

        quaternion.copy(camera.quaternion)
        dispatchEvent("change")
    }

    override fun lookAt(target: Vector3, duration: Float) {
        val direction = target.clone().sub(camera.position).normalize()
        val targetQuaternion = Quaternion().setFromUnitVectors(
            Vector3(0f, 0f, -1f),
            direction
        )
        camera.quaternion.copy(targetQuaternion)
        this.target.copy(target)
        dispatchEvent("change")
    }

    /**
     * Add event listener
     */
    fun addEventListener(listener: ControlsEventListener) {
        listeners.add(listener)
    }

    /**
     * Remove event listener
     */
    fun removeEventListener(listener: ControlsEventListener) {
        listeners.remove(listener)
    }

    private fun dispatchEvent(event: String) {
        when (event) {
            "change" -> listeners.forEach { it.onControlsChange() }
            "start" -> listeners.forEach { it.onControlsStart() }
            "end" -> listeners.forEach { it.onControlsEnd() }
        }
    }

    private fun quaternionsEqual(a: Quaternion, b: Quaternion): Boolean {
        val threshold = 0.0001f
        return kotlin.math.abs(a.x - b.x) < threshold &&
               kotlin.math.abs(a.y - b.y) < threshold &&
               kotlin.math.abs(a.z - b.z) < threshold &&
               kotlin.math.abs(a.w - b.w) < threshold
    }

    /**
     * Dispose of the controls
     */
    fun dispose() {
        reset()
        listeners.clear()
    }
}

// Extension functions
private fun Vector3.addScaledVector(v: Vector3, scale: Float): Vector3 {
    x += v.x * scale
    y += v.y * scale
    z += v.z * scale
    return this
}
