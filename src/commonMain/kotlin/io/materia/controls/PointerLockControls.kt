package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Euler
import io.materia.core.math.EulerOrder
import io.materia.core.math.Vector3
import kotlin.math.PI

/**
 * PointerLockControls - First-person camera controls using the Pointer Lock API.
 *
 * Provides mouse-look camera rotation when pointer is locked to the canvas.
 * This is the Three.js-compatible implementation for FPS-style camera controls.
 *
 * ## Usage
 *
 * ```kotlin
 * val controls = PointerLockControls(camera, canvas)
 *
 * // Lock pointer on click
 * canvas.onClick = { controls.lock() }
 *
 * // In render loop
 * controls.update(deltaTime)
 *
 * // Movement (WASD)
 * if (controls.isLocked) {
 *     if (moveForward) controls.moveForward(speed * deltaTime)
 *     if (moveRight) controls.moveRight(speed * deltaTime)
 * }
 * ```
 *
 * ## Events
 *
 * ```kotlin
 * controls.onLock = { println("Pointer locked") }
 * controls.onUnlock = { println("Pointer unlocked") }
 * controls.onChange = { println("Camera moved") }
 * ```
 *
 * @param camera The camera to control.
 * @param domElement The DOM element to lock pointer to (platform-specific).
 */
class PointerLockControls(
    val camera: Camera,
    private val domElement: Any? = null
) {
    /** Whether controls are enabled. */
    var enabled: Boolean = true

    /** Whether the pointer is currently locked. */
    var isLocked: Boolean = false
        private set

    /** Minimum polar angle (looking up limit). Default: 0 */
    var minPolarAngle: Float = 0f

    /** Maximum polar angle (looking down limit). Default: PI */
    var maxPolarAngle: Float = PI.toFloat()

    /** Mouse sensitivity multiplier. */
    var pointerSpeed: Float = 1f

    // Callbacks
    /** Called when pointer is locked. */
    var onLock: (() -> Unit)? = null

    /** Called when pointer is unlocked. */
    var onUnlock: (() -> Unit)? = null

    /** Called when camera orientation changes. */
    var onChange: (() -> Unit)? = null

    /** Called on errors (e.g., pointer lock denied). */
    var onError: ((String) -> Unit)? = null

    // Internal state
    private val euler = Euler(0f, 0f, 0f, EulerOrder.YXZ)
    private val direction = Vector3()

    // Movement vectors
    private val moveDirection = Vector3()
    private val velocity = Vector3()

    init {
        // Initialize euler from camera quaternion
        euler.setFromQuaternion(camera.quaternion)
    }

    /**
     * Request pointer lock on the DOM element.
     */
    fun lock() {
        if (!PointerLock.isSupported()) {
            onError?.invoke("Pointer lock is not supported on this platform")
            return
        }

        PointerLock.request(domElement) { error ->
            onError?.invoke(error)
        }

        // Assume lock succeeded if no immediate error
        // Real implementation would listen for pointerlockchange event
        isLocked = true
        onLock?.invoke()
    }

    /**
     * Release pointer lock.
     */
    fun unlock() {
        PointerLock.exit()
        isLocked = false
        onUnlock?.invoke()
    }

    /**
     * Connect controls to start listening for events.
     * Called automatically in most cases.
     */
    fun connect() {
        // Platform-specific event listener setup would go here
        enabled = true
    }

    /**
     * Disconnect controls and stop listening for events.
     */
    fun disconnect() {
        unlock()
        enabled = false
    }

    /**
     * Dispose of controls and release resources.
     */
    fun dispose() {
        disconnect()
    }

    /**
     * Handle mouse movement when pointer is locked.
     *
     * @param movementX Horizontal mouse movement in pixels.
     * @param movementY Vertical mouse movement in pixels.
     */
    fun onMouseMove(movementX: Float, movementY: Float) {
        if (!enabled || !isLocked) return

        // Apply mouse movement to euler angles
        euler.y -= movementX * 0.002f * pointerSpeed
        euler.x -= movementY * 0.002f * pointerSpeed

        // Clamp vertical rotation
        val halfPi = PI.toFloat() / 2f - 0.0001f
        euler.x = euler.x.coerceIn(
            -halfPi.coerceAtLeast(minPolarAngle - PI.toFloat() / 2f),
            halfPi.coerceAtMost(maxPolarAngle - PI.toFloat() / 2f)
        )

        // Apply to camera
        camera.quaternion.setFromEuler(euler)

        onChange?.invoke()
    }

    /**
     * Get the direction the camera is facing.
     *
     * @param target Vector3 to store the result.
     * @return The direction vector.
     */
    fun getDirection(target: Vector3 = Vector3()): Vector3 {
        return target.set(0f, 0f, -1f).applyQuaternion(camera.quaternion)
    }

    /**
     * Get the object containing the camera (for adding to scene).
     * In Three.js this returns a separate Object3D, but we return the camera directly.
     */
    fun getObject(): Camera = camera

    /**
     * Move the camera forward/backward relative to its orientation.
     *
     * @param distance Distance to move (negative = backward).
     */
    fun moveForward(distance: Float) {
        // Get forward direction (ignore Y component for ground movement)
        direction.setFromMatrixColumn(camera.matrix, 0)
        direction.crossVectors(camera.up, direction)
        camera.position.add(direction.clone().multiplyScalar(distance))
    }

    /**
     * Move the camera right/left relative to its orientation.
     *
     * @param distance Distance to move (negative = left).
     */
    fun moveRight(distance: Float) {
        direction.setFromMatrixColumn(camera.matrix, 0)
        camera.position.add(direction.clone().multiplyScalar(distance))
    }

    /**
     * Move the camera up/down in world space.
     *
     * @param distance Distance to move (negative = down).
     */
    fun moveUp(distance: Float) {
        camera.position.y += distance
    }

    /**
     * Update controls. Call this in your animation loop.
     *
     * @param deltaTime Time since last frame in seconds.
     */
    fun update(deltaTime: Float) {
        if (!enabled) return

        // Apply any velocity-based movement
        if (velocity.lengthSquared() > 0.0001f) {
            camera.position.add(velocity.clone().multiplyScalar(deltaTime))
        }
    }

    /**
     * Reset the camera to look at a target position.
     *
     * @param x Target X coordinate.
     * @param y Target Y coordinate.
     * @param z Target Z coordinate.
     */
    fun lookAt(x: Float, y: Float, z: Float) {
        val target = Vector3(x, y, z)
        val position = camera.position

        // Calculate direction to target
        direction.subVectors(target, position).normalize()

        // Calculate euler angles
        euler.y = kotlin.math.atan2(direction.x, direction.z)
        euler.x = kotlin.math.asin(-direction.y)

        camera.quaternion.setFromEuler(euler)
    }

    companion object {
        /**
         * Check if pointer lock is supported on the current platform.
         */
        fun isSupported(): Boolean = PointerLock.isSupported()
    }
}
