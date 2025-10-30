package io.materia.controls

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3

/**
 * Base interface for camera control systems
 * Provides common interface for different control schemes (orbit, first-person, etc.)
 */
interface CameraControls {
    var enabled: Boolean
    var camera: Camera
    var target: Vector3

    /**
     * Update the controls with input events
     */
    fun update(deltaTime: Float)

    /**
     * Handle pointer/mouse input
     */
    fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton)
    fun onPointerDown(x: Float, y: Float, button: PointerButton)
    fun onPointerUp(x: Float, y: Float, button: PointerButton)

    /**
     * Handle wheel/scroll input
     */
    fun onWheel(deltaX: Float, deltaY: Float)

    /**
     * Handle keyboard input
     */
    fun onKeyDown(key: Key)
    fun onKeyUp(key: Key)

    /**
     * Reset controls to default state
     */
    fun reset()

    /**
     * Smoothly animate to look at target
     */
    fun lookAt(target: Vector3, duration: Float = 1f)
}

/**
 * Input event types
 */
enum class PointerButton {
    PRIMARY,   // Left mouse button / primary touch
    SECONDARY, // Right mouse button / secondary touch
    AUXILIARY  // Middle mouse button / other inputs
}

enum class Key {
    W, A, S, D,           // Movement
    Q, E,                 // Up/Down
    SHIFT, SPACE,         // Modifiers
    ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT,
    ESCAPE, ENTER,
    // Add more as needed
}

/**
 * Control configuration and constraints
 */
data class ControlsConfig(
    // Movement constraints
    val minDistance: Float = 1f,
    val maxDistance: Float = 1000f,
    val minPolarAngle: Float = 0f,
    val maxPolarAngle: Float = kotlin.math.PI.toFloat(),
    val minAzimuthAngle: Float = -Float.MAX_VALUE,
    val maxAzimuthAngle: Float = Float.MAX_VALUE,

    // Sensitivity settings
    val rotateSpeed: Float = 1f,
    val zoomSpeed: Float = 1f,
    val panSpeed: Float = 1f,
    val keyboardSpeed: Float = 1f,

    // Behavior flags
    val enableRotate: Boolean = true,
    val enableZoom: Boolean = true,
    val enablePan: Boolean = true,
    val enableKeys: Boolean = true,
    val enableDamping: Boolean = true,
    val dampingFactor: Float = 0.05f,

    // Auto-rotation
    val autoRotate: Boolean = false,
    val autoRotateSpeed: Float = 2f
)

/**
 * Control state for tracking input and animation
 */
data class ControlsState(
    var spherical: SphericalCoordinate = SphericalCoordinate(),
    var panOffset: Vector3 = Vector3.ZERO,
    var scale: Float = 1f,

    // Input state
    var isPointerDown: Boolean = false,
    var pointerButton: PointerButton = PointerButton.PRIMARY,
    var lastPointerPosition: Vector2 = Vector2.ZERO,

    // Keyboard state
    var keysDown: MutableSet<Key> = mutableSetOf(),

    // Animation state
    var targetPosition: Vector3? = null,
    var animationStartTime: Float = 0f,
    var animationDuration: Float = 0f
)

/**
 * Spherical coordinate system for orbit calculations
 */
data class SphericalCoordinate(
    var radius: Float = 1f,
    var phi: Float = 0f,    // Polar angle (0 to PI)
    var theta: Float = 0f   // Azimuth angle (-PI to PI)
) {
    fun toCartesian(): Vector3 {
        val sinPhiRadius = kotlin.math.sin(phi) * radius
        return Vector3(
            sinPhiRadius * kotlin.math.sin(theta),
            kotlin.math.cos(phi) * radius,
            sinPhiRadius * kotlin.math.cos(theta)
        )
    }

    fun fromVector3(position: Vector3): SphericalCoordinate {
        radius = position.length()
        if (radius == 0f) {
            theta = 0f
            phi = 0f
        } else {
            theta = kotlin.math.atan2(position.x, position.z)
            phi = kotlin.math.acos((position.y / radius).coerceIn(-1f, 1f))
        }
        return this
    }
}

/**
 * Event system for controls
 */
interface ControlsEventListener {
    fun onControlsChange() {}
    fun onControlsStart() {}
    fun onControlsEnd() {}
}

/**
 * Abstract base class for camera controls with common functionality
 */
abstract class BaseCameraControls(
    override var camera: Camera,
    protected val config: ControlsConfig = ControlsConfig()
) : CameraControls {

    override var enabled: Boolean = true
    override var target: Vector3 = Vector3.ZERO

    protected val state = ControlsState()
    protected val listeners = mutableListOf<ControlsEventListener>()

    // Computed properties
    protected val offset: Vector3 get() = camera.position - target
    protected val quat: Quaternion = Quaternion.fromRotationMatrix(camera.matrix)
    protected val quatInverse: Quaternion = quat.inverse()

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

    protected fun dispatchEvent(event: String) {
        when (event) {
            "change" -> listeners.forEach { it.onControlsChange() }
            "start" -> listeners.forEach { it.onControlsStart() }
            "end" -> listeners.forEach { it.onControlsEnd() }
        }
    }

    /**
     * Apply constraints to spherical coordinates
     */
    protected fun constrainSpherical(spherical: SphericalCoordinate) {
        spherical.phi = spherical.phi.coerceIn(config.minPolarAngle, config.maxPolarAngle)
        spherical.theta = spherical.theta.coerceIn(config.minAzimuthAngle, config.maxAzimuthAngle)
        spherical.radius = spherical.radius.coerceIn(config.minDistance, config.maxDistance)
    }

    /**
     * Calculate pan vector in world space
     */
    protected fun calculatePanVector(deltaX: Float, deltaY: Float): Vector3 {
        val cam = camera // Create immutable reference for smart casting
        if (cam !is PerspectiveCamera) {
            return Vector3.ZERO
        }

        val element = cam.matrix.elements
        val targetDistance = offset.length()

        // Calculate pan amount based on camera position and target distance
        val fov = cam.fov * kotlin.math.PI.toFloat() / 180f
        val panX = (2f * deltaX * targetDistance * kotlin.math.tan(fov / 2f)) / cam.aspect
        val panY = (2f * deltaY * targetDistance * kotlin.math.tan(fov / 2f))

        // Create pan vector in camera space
        val panVector = Vector3(-panX, panY, 0f)

        // Transform to world space
        return panVector.applyMatrix4(cam.matrix)
    }

    override fun reset() {
        state.spherical.fromVector3(offset)
        state.panOffset = Vector3.ZERO
        state.scale = 1f
        state.isPointerDown = false
        state.keysDown.clear()
        dispatchEvent("change")
    }
}