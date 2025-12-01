package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Arcball camera controls for intuitive 3D rotation.
 *
 * Arcball controls provide a natural way to rotate the camera around
 * a target point by mapping 2D mouse movements to a virtual sphere.
 * This gives intuitive control similar to rotating a physical ball.
 *
 * Key features:
 * - Intuitive rotation by dragging on a virtual sphere
 * - Smooth momentum-based animation
 * - Configurable constraints on rotation
 * - Support for pan and zoom operations
 *
 * Based on the Shoemake arcball algorithm and Three.js ArcballControls.
 *
 * Usage:
 * ```kotlin
 * val controls = ArcballControls(camera, domElement)
 * controls.target.set(0f, 0f, 0f)
 * controls.enableRotate = true
 * controls.enablePan = true
 * controls.enableZoom = true
 *
 * // In animation loop
 * controls.update(deltaTime)
 * ```
 */
class ArcballControls(
    var camera: Camera,
    private val domElement: Any? = null
) {

    /**
     * The point to rotate around and focus on.
     */
    val target: Vector3 = Vector3(0f, 0f, 0f)

    /**
     * Enable or disable rotation.
     */
    var enableRotate: Boolean = true

    /**
     * Enable or disable panning.
     */
    var enablePan: Boolean = true

    /**
     * Enable or disable zooming.
     */
    var enableZoom: Boolean = true

    /**
     * Enable or disable keyboard controls.
     */
    var enableKeys: Boolean = true

    /**
     * Enable or disable damping (smooth movement).
     */
    var enableDamping: Boolean = true

    /**
     * Damping factor for smooth movement (0.0 to 1.0).
     * Lower values = more damping/smoothing.
     */
    var dampingFactor: Float = 0.25f

    /**
     * Speed of rotation (higher = faster).
     */
    var rotateSpeed: Float = 1f

    /**
     * Speed of panning (higher = faster).
     */
    var panSpeed: Float = 1f

    /**
     * Speed of zooming (higher = faster).
     */
    var zoomSpeed: Float = 1f

    /**
     * Minimum distance for zooming.
     */
    var minDistance: Float = 0f

    /**
     * Maximum distance for zooming.
     */
    var maxDistance: Float = Float.POSITIVE_INFINITY

    /**
     * Minimum polar angle (vertical rotation constraint).
     * 0 = looking straight down.
     */
    var minPolarAngle: Float = 0f

    /**
     * Maximum polar angle (vertical rotation constraint).
     * PI = looking straight up.
     */
    var maxPolarAngle: Float = kotlin.math.PI.toFloat()

    /**
     * Minimum azimuthal angle (horizontal rotation constraint).
     * Set to -Infinity for no limit.
     */
    var minAzimuthAngle: Float = Float.NEGATIVE_INFINITY

    /**
     * Maximum azimuthal angle (horizontal rotation constraint).
     * Set to Infinity for no limit.
     */
    var maxAzimuthAngle: Float = Float.POSITIVE_INFINITY

    /**
     * Scale factor for the virtual arcball sphere.
     * Larger values make rotation more sensitive.
     */
    var arcballRadius: Float = 1f

    /**
     * Whether the controls have been updated this frame.
     */
    val hasUpdated: Boolean
        get() = _hasUpdated

    /**
     * Current control state
     */
    enum class State {
        NONE,
        ROTATE,
        PAN,
        ZOOM
    }

    // Internal state
    private var state: State = State.NONE
    private var _hasUpdated: Boolean = false

    // Rotation tracking
    private var rotationStart = Quaternion()
    private var rotationEnd = Quaternion()
    private var rotationDelta = Quaternion()

    // Mouse/pointer tracking
    private var pointerStart = Vector2(0f, 0f)
    private var pointerEnd = Vector2(0f, 0f)
    private var pointerDelta = Vector2(0f, 0f)

    // Arcball vectors
    private var arcballStart = Vector3(0f, 0f, 0f)
    private var arcballEnd = Vector3(0f, 0f, 0f)

    // Camera state
    private var cameraPosition = Vector3(0f, 0f, 0f)
    private var cameraQuaternion = Quaternion()
    private var cameraUp = Vector3(0f, 1f, 0f)

    // Pan state
    private var panStart = Vector2(0f, 0f)
    private var panEnd = Vector2(0f, 0f)
    private var panDelta = Vector2(0f, 0f)
    private var panOffset = Vector3(0f, 0f, 0f)

    // Zoom state
    private var zoomStart = 0f
    private var zoomEnd = 0f
    private var zoomDelta = 0f
    private var scale = 1f

    // Temporary vectors
    private val tempVec = Vector3(0f, 0f, 0f)
    private val tempQuat = Quaternion()

    // Viewport dimensions
    private var viewportWidth: Int = 1
    private var viewportHeight: Int = 1

    init {
        saveState()
    }

    /**
     * Sets the viewport dimensions for proper arcball mapping.
     */
    fun setViewportSize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
    }

    /**
     * Updates the controls. Call this in your animation loop.
     * @param deltaTime Time since last update in seconds
     * @return True if the camera was updated
     */
    fun update(deltaTime: Float = 0.016f): Boolean {
        _hasUpdated = false

        if (!enableDamping) {
            // Apply rotation directly
            if (rotationDelta.x != 0f || rotationDelta.y != 0f || 
                rotationDelta.z != 0f || rotationDelta.w != 1f) {
                applyRotation()
                rotationDelta = Quaternion()
                _hasUpdated = true
            }

            // Apply pan directly
            if (panOffset.x != 0f || panOffset.y != 0f || panOffset.z != 0f) {
                applyPan()
                panOffset.set(0f, 0f, 0f)
                _hasUpdated = true
            }

            // Apply zoom directly
            if (scale != 1f) {
                applyZoom()
                scale = 1f
                _hasUpdated = true
            }
        } else {
            // Apply with damping
            val dampFactor = 1f - dampingFactor

            // Rotation damping
            rotationDelta.slerp(Quaternion(), dampFactor)
            if (rotationDelta.dot(Quaternion()) < 0.9999f) {
                applyRotation()
                _hasUpdated = true
            }

            // Pan damping
            panOffset.multiplyScalar(dampFactor)
            if (panOffset.lengthSq() > 0.0001f) {
                applyPan()
                _hasUpdated = true
            }

            // Zoom damping
            scale = 1f + (scale - 1f) * dampFactor
            if (abs(scale - 1f) > 0.0001f) {
                applyZoom()
                _hasUpdated = true
            }
        }

        return _hasUpdated
    }

    /**
     * Handles the start of a rotation gesture.
     * @param x Screen X coordinate (normalized 0-1)
     * @param y Screen Y coordinate (normalized 0-1)
     */
    fun rotateStart(x: Float, y: Float) {
        if (!enableRotate) return

        state = State.ROTATE
        pointerStart.set(x, y)
        arcballStart = screenToArcball(x, y)
        rotationStart = cameraQuaternion.clone()
    }

    /**
     * Handles rotation movement.
     * @param x Screen X coordinate (normalized 0-1)
     * @param y Screen Y coordinate (normalized 0-1)
     */
    fun rotateMove(x: Float, y: Float) {
        if (!enableRotate || state != State.ROTATE) return

        pointerEnd.set(x, y)
        arcballEnd = screenToArcball(x, y)

        // Calculate rotation from arcball vectors
        rotationDelta = arcballRotation(arcballStart, arcballEnd)
        // Scale the rotation by rotate speed using slerp
        if (rotateSpeed != 1f) {
            rotationDelta.slerp(Quaternion(), 1f - rotateSpeed)
        }
    }

    /**
     * Handles the end of a rotation gesture.
     */
    fun rotateEnd() {
        state = State.NONE
    }

    /**
     * Handles the start of a pan gesture.
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     */
    fun panStart(x: Float, y: Float) {
        if (!enablePan) return

        state = State.PAN
        panStart.set(x, y)
    }

    /**
     * Handles pan movement.
     * @param x Screen X coordinate
     * @param y Screen Y coordinate
     */
    fun panMove(x: Float, y: Float) {
        if (!enablePan || state != State.PAN) return

        panEnd.set(x, y)
        panDelta.set(panEnd.x - panStart.x, panEnd.y - panStart.y)

        pan(panDelta.x * panSpeed, panDelta.y * panSpeed)

        panStart.copy(panEnd)
    }

    /**
     * Handles the end of a pan gesture.
     */
    fun panEnd() {
        state = State.NONE
    }

    /**
     * Handles zoom input (e.g., mouse wheel).
     * @param delta Zoom delta (positive = zoom in, negative = zoom out)
     */
    fun zoom(delta: Float) {
        if (!enableZoom) return

        if (delta > 0) {
            scale /= 0.95f.pow(zoomSpeed)
        } else if (delta < 0) {
            scale *= 0.95f.pow(zoomSpeed)
        }
    }

    /**
     * Resets the controls to the saved state.
     */
    fun reset() {
        target.set(0f, 0f, 0f)
        camera.position.copy(cameraPosition)
        camera.quaternion.copy(cameraQuaternion)
        camera.up.copy(cameraUp)
        camera.updateProjectionMatrix()
        
        rotationDelta = Quaternion()
        panOffset.set(0f, 0f, 0f)
        scale = 1f
        
        _hasUpdated = true
    }

    /**
     * Saves the current state for later reset.
     */
    fun saveState() {
        cameraPosition.copy(camera.position)
        cameraQuaternion.copy(camera.quaternion)
        cameraUp.copy(camera.up)
    }

    /**
     * Maps screen coordinates to a point on the arcball sphere.
     */
    private fun screenToArcball(x: Float, y: Float): Vector3 {
        // Convert to centered coordinates (-1 to 1)
        val nx = (x * 2f - 1f) * arcballRadius
        val ny = (1f - y * 2f) * arcballRadius

        // Project onto sphere
        val lengthSq = nx * nx + ny * ny
        
        return if (lengthSq <= 1f) {
            // Point is on the sphere
            Vector3(nx, ny, sqrt(1f - lengthSq))
        } else {
            // Point is outside sphere, project onto edge
            val length = sqrt(lengthSq)
            Vector3(nx / length, ny / length, 0f)
        }
    }

    /**
     * Calculates the rotation quaternion from two arcball points.
     */
    private fun arcballRotation(from: Vector3, to: Vector3): Quaternion {
        // Calculate rotation axis
        val axis = from.clone().cross(to)
        
        if (axis.lengthSq() < 0.0001f) {
            // Points are too close, no rotation
            return Quaternion()
        }
        
        axis.normalize()

        // Calculate rotation angle
        val dot = from.dot(to).coerceIn(-1f, 1f)
        val angle = acos(dot)

        // Create rotation quaternion
        return Quaternion().setFromAxisAngle(axis, angle)
    }

    /**
     * Applies the current rotation to the camera.
     */
    private fun applyRotation() {
        // Get current offset from target
        val offset = camera.position.clone().subtract(target)
        
        // Apply rotation to offset
        offset.applyQuaternion(rotationDelta)
        
        // Update camera position
        camera.position.copy(target).add(offset)
        
        // Make camera look at target
        camera.lookAt(target)
    }

    /**
     * Applies the current pan offset.
     */
    private fun applyPan() {
        target.add(panOffset)
        camera.position.add(panOffset)
    }

    /**
     * Applies the current zoom scale.
     */
    private fun applyZoom() {
        // Get offset from target
        val offset = camera.position.clone().subtract(target)
        
        // Scale the offset
        var distance = offset.length() * scale
        
        // Clamp distance
        distance = distance.coerceIn(minDistance, maxDistance)
        
        // Apply new distance
        offset.normalize().multiplyScalar(distance)
        camera.position.copy(target).add(offset)
        
        // Reset scale
        scale = 1f
    }

    /**
     * Pans the camera by the given screen delta.
     */
    private fun pan(deltaX: Float, deltaY: Float) {
        val offset = camera.position.clone().subtract(target)
        val distance = offset.length()

        // Calculate pan distance based on camera distance
        val panDistance = distance * 0.001f

        // Get camera's local axes
        val right = Vector3(1f, 0f, 0f).applyQuaternion(camera.quaternion)
        val up = Vector3(0f, 1f, 0f).applyQuaternion(camera.quaternion)

        // Calculate pan offset
        panOffset.add(right.multiplyScalar(-deltaX * panDistance))
        panOffset.add(up.multiplyScalar(deltaY * panDistance))
    }

    fun dispose() {
        // Clean up any event listeners if attached
    }
}
