package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Trackball camera controls for free rotation around target.
 * 
 * Similar to OrbitControls but allows unrestricted rotation (no pole constraints).
 * The camera rotates around a target point with mouse/touch drag operations.
 * Similar to Three.js TrackballControls.
 *
 * Usage:
 * ```kotlin
 * val controls = TrackballControls(camera)
 * controls.target.set(0f, 0f, 0f)
 * controls.rotateSpeed = 1.0f
 * 
 * // In render loop
 * controls.update(deltaTime)
 * 
 * // Handle input
 * controls.onPointerDown(x, y, PointerButton.PRIMARY)
 * controls.onPointerMove(deltaX, deltaY, PointerButton.PRIMARY)
 * controls.onPointerUp(x, y, PointerButton.PRIMARY)
 * ```
 */
class TrackballControls(
    override var camera: Camera,
    config: TrackballControlsConfig = TrackballControlsConfig()
) : CameraControls {

    /**
     * Configuration for trackball controls
     */
    data class TrackballControlsConfig(
        val rotateSpeed: Float = 1.0f,
        val zoomSpeed: Float = 1.2f,
        val panSpeed: Float = 0.3f,
        val noRotate: Boolean = false,
        val noZoom: Boolean = false,
        val noPan: Boolean = false,
        val staticMoving: Boolean = false,
        val dynamicDampingFactor: Float = 0.2f,
        val minDistance: Float = 0f,
        val maxDistance: Float = Float.POSITIVE_INFINITY
    )

    // Configuration
    private val config = config.copy()

    override var enabled: Boolean = true
    override var target: Vector3 = Vector3()

    /** Rotation speed multiplier */
    var rotateSpeed: Float = config.rotateSpeed

    /** Zoom speed multiplier */
    var zoomSpeed: Float = config.zoomSpeed

    /** Pan speed multiplier */
    var panSpeed: Float = config.panSpeed

    /** Disable rotation when true */
    var noRotate: Boolean = config.noRotate

    /** Disable zoom when true */
    var noZoom: Boolean = config.noZoom

    /** Disable pan when true */
    var noPan: Boolean = config.noPan

    /** Disable damping when true */
    var staticMoving: Boolean = config.staticMoving

    /** Damping factor for smooth motion */
    var dynamicDampingFactor: Float = config.dynamicDampingFactor

    /** Minimum camera distance from target */
    var minDistance: Float = config.minDistance

    /** Maximum camera distance from target */
    var maxDistance: Float = config.maxDistance

    // State tracking
    private var state: ControlState = ControlState.NONE

    // Screen dimensions
    private var screenWidth: Float = 800f
    private var screenHeight: Float = 600f

    // Eye vector (camera position relative to target)
    private val eye = Vector3()

    // Rotation state
    private val rotateStart = Vector3()
    private val rotateEnd = Vector3()
    private val rotateAxis = Vector3()
    private var rotateAngle: Float = 0f

    // Zoom state
    private val zoomStart = Vector2()
    private val zoomEnd = Vector2()

    // Pan state
    private val panStart = Vector2()
    private val panEnd = Vector2()

    // Working objects
    private val target0 = Vector3()
    private val position0 = Vector3()
    private val up0 = Vector3()
    private val lastPosition = Vector3()
    private val lastQuaternion = Quaternion()

    // Event listeners
    private val listeners = mutableListOf<ControlsEventListener>()

    private enum class ControlState {
        NONE,
        ROTATE,
        ZOOM,
        PAN,
        TOUCH_ROTATE,
        TOUCH_ZOOM_PAN
    }

    init {
        // Store initial state for reset
        target0.copy(target)
        position0.copy(camera.position)
        up0.copy(camera.up)
    }

    /**
     * Set screen dimensions for proper coordinate mapping
     */
    fun setScreenDimensions(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        eye.copy(camera.position).sub(target)

        if (!noRotate) {
            rotateCamera()
        }

        if (!noZoom) {
            zoomCamera()
        }

        if (!noPan) {
            panCamera()
        }

        camera.position.copy(target).add(eye)
        checkDistances()
        camera.lookAt(target)

        // Check if anything changed
        if (lastPosition.distanceToSquared(camera.position) > 0.000001f) {
            dispatchEvent("change")
            lastPosition.copy(camera.position)
        }
    }

    private fun rotateCamera() {
        val moveAngle = rotateEnd.clone().sub(rotateStart).length()
        
        if (moveAngle > 0.0001f) {
            val axis = rotateEnd.clone().cross(rotateStart).normalize()
            var angle = moveAngle * rotateSpeed
            
            val quaternion = Quaternion().setFromAxisAngle(axis, angle)
            
            eye.applyQuaternion(quaternion)
            camera.up.applyQuaternion(quaternion)
            
            rotateEnd.applyQuaternion(quaternion)
            
            if (staticMoving) {
                rotateStart.copy(rotateEnd)
            } else {
                quaternion.setFromAxisAngle(axis, angle * (dynamicDampingFactor - 1f))
                rotateStart.applyQuaternion(quaternion)
            }
        }
    }

    private fun zoomCamera() {
        val factor = 1f + (zoomEnd.y - zoomStart.y) * zoomSpeed
        
        if (factor != 1f && factor > 0f) {
            eye.multiplyScalar(factor)
        }
        
        if (staticMoving) {
            zoomStart.copy(zoomEnd)
        } else {
            zoomStart.y += (zoomEnd.y - zoomStart.y) * dynamicDampingFactor
        }
    }

    private fun panCamera() {
        val mouseChange = panEnd.clone().sub(panStart)
        
        if (mouseChange.lengthSquared() > 0.0001f) {
            mouseChange.multiplyScalar(eye.length() * panSpeed)
            
            val pan = eye.clone().cross(camera.up).setLength(mouseChange.x)
            pan.add(camera.up.clone().setLength(mouseChange.y))
            
            camera.position.add(pan)
            target.add(pan)
            
            if (staticMoving) {
                panStart.copy(panEnd)
            } else {
                panStart.add(
                    panEnd.clone().sub(panStart).multiplyScalar(dynamicDampingFactor)
                )
            }
        }
    }

    private fun checkDistances() {
        if (minDistance > 0f || maxDistance < Float.POSITIVE_INFINITY) {
            val distance = camera.position.distanceTo(target)
            
            if (distance < minDistance) {
                camera.position.copy(target).add(
                    eye.normalize().multiplyScalar(minDistance)
                )
            } else if (distance > maxDistance) {
                camera.position.copy(target).add(
                    eye.normalize().multiplyScalar(maxDistance)
                )
            }
        }
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        when (state) {
            ControlState.ROTATE -> {
                rotateEnd.copy(getMouseProjectionOnBall(deltaX, deltaY))
            }
            ControlState.ZOOM -> {
                zoomEnd.copy(getMouseOnScreen(deltaX, deltaY))
            }
            ControlState.PAN -> {
                panEnd.copy(getMouseOnScreen(deltaX, deltaY))
            }
            else -> {}
        }
    }

    override fun onPointerDown(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        state = when (button) {
            PointerButton.PRIMARY -> {
                if (!noRotate) {
                    rotateStart.copy(getMouseProjectionOnBall(x, y))
                    rotateEnd.copy(rotateStart)
                    ControlState.ROTATE
                } else ControlState.NONE
            }
            PointerButton.AUXILIARY -> {
                if (!noZoom) {
                    zoomStart.copy(getMouseOnScreen(x, y))
                    zoomEnd.copy(zoomStart)
                    ControlState.ZOOM
                } else ControlState.NONE
            }
            PointerButton.SECONDARY -> {
                if (!noPan) {
                    panStart.copy(getMouseOnScreen(x, y))
                    panEnd.copy(panStart)
                    ControlState.PAN
                } else ControlState.NONE
            }
        }

        dispatchEvent("start")
    }

    override fun onPointerUp(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return
        state = ControlState.NONE
        dispatchEvent("end")
    }

    override fun onWheel(deltaX: Float, deltaY: Float) {
        if (!enabled || noZoom) return

        val factor = if (deltaY > 0) 1f / zoomSpeed else zoomSpeed
        eye.multiplyScalar(factor)

        dispatchEvent("change")
    }

    override fun onKeyDown(key: Key) {
        // Can add keyboard shortcuts for rotation/zoom/pan
    }

    override fun onKeyUp(key: Key) {
        // Handle key releases
    }

    override fun reset() {
        state = ControlState.NONE

        target.copy(target0)
        camera.position.copy(position0)
        camera.up.copy(up0)

        eye.copy(camera.position).sub(target)
        camera.lookAt(target)

        dispatchEvent("change")
    }

    override fun lookAt(target: Vector3, duration: Float) {
        this.target.copy(target)
        camera.lookAt(target)
        dispatchEvent("change")
    }

    /**
     * Project mouse coordinates onto virtual trackball sphere
     */
    private fun getMouseProjectionOnBall(x: Float, y: Float): Vector3 {
        val mouseOnBall = Vector3(
            (x - screenWidth * 0.5f) / (screenWidth * 0.5f),
            (screenHeight * 0.5f - y) / (screenHeight * 0.5f),
            0f
        )

        val length = mouseOnBall.length()

        if (length > 1f) {
            mouseOnBall.normalize()
        } else {
            mouseOnBall.z = sqrt(1f - length * length)
        }

        // Transform to camera space
        eye.copy(camera.position).sub(target)
        
        val projection = camera.up.clone().setLength(mouseOnBall.y)
        projection.add(
            camera.up.clone().cross(eye).setLength(mouseOnBall.x)
        )
        projection.add(eye.clone().setLength(mouseOnBall.z))

        return projection
    }

    /**
     * Get normalized screen coordinates
     */
    private fun getMouseOnScreen(x: Float, y: Float): Vector2 {
        return Vector2(
            x / screenWidth,
            y / screenHeight
        )
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

    /**
     * Dispose of the controls
     */
    fun dispose() {
        reset()
        listeners.clear()
    }
}

// Extension functions for Vector3
private fun Vector3.setLength(length: Float): Vector3 {
    return this.normalize().multiplyScalar(length)
}
