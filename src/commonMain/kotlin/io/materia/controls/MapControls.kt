package io.materia.controls

import io.materia.camera.Camera
import io.materia.camera.OrthographicCamera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Box3
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Map camera controls implementation
 * Optimized for 2D/3D map navigation with pan, zoom, and tilt controls
 * Similar to Google Maps or other mapping applications
 */
class MapControls(
    camera: Camera,
    config: ControlsConfig = ControlsConfig()
) : BaseCameraControls(camera, config) {

    // Map-specific configuration
    data class MapConfig(
        val minZoom: Float = 0.1f,
        val maxZoom: Float = 100f,
        val zoomSpeed: Float = 1.5f,
        val panSpeed: Float = 1f,
        val tiltSpeed: Float = 0.5f,
        val rotationSpeed: Float = 1f,

        // Map bounds
        val enableBounds: Boolean = false,
        val bounds: Box3 = Box3(),

        // Behavior
        val enableRotation: Boolean = true,
        val enableTilt: Boolean = true,
        val smoothZoom: Boolean = true,
        val centerOnDoubleClick: Boolean = true,

        // Orthographic vs Perspective
        val preferOrthographic: Boolean = false,
        val maxTiltAngle: Float = 60f * PI.toFloat() / 180f,
        val minHeight: Float = 1f,
        val maxHeight: Float = 1000f
    )

    private val mapConfig = MapConfig()

    // Control modes
    private enum class ControlMode {
        NONE, PAN, ZOOM, ROTATE, TILT
    }

    private var mode = ControlMode.NONE

    // Input state
    private val panStart = Vector2()
    private val panEnd = Vector2()
    private val zoomStart = Vector2()
    private val zoomEnd = Vector2()
    private val rotateStart = Vector2()
    private val rotateEnd = Vector2()

    // Map state
    private var currentZoom: Float = 1f
    private var currentRotation: Float = 0f
    private var currentTilt: Float = 0f
    private var targetHeight: Float = 10f

    // Touch/gesture state
    private var lastPinchDistance: Float = 0f
    private var lastRotationAngle: Float = 0f

    // Animation state
    private var animating = false
    private var animationStartTime: Float = 0f
    private var animationDuration: Float = 1f
    private var animationStartPos = Vector3()
    private var animationTargetPos = Vector3()
    private var animationStartZoom: Float = 1f
    private var animationTargetZoom: Float = 1f

    init {
        // Set up initial camera position for map view
        setupMapView()
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        var hasChanged = false

        // Handle animation
        if (animating) {
            hasChanged = updateAnimation() || hasChanged
        }

        // Handle keyboard input for map navigation
        if (config.enableKeys) {
            hasChanged = handleKeyboardInput(deltaTime) || hasChanged
        }

        // Apply camera constraints
        hasChanged = applyConstraints() || hasChanged

        if (hasChanged) {
            dispatchEvent("change")
        }
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        if (!enabled) return

        when (mode) {
            ControlMode.PAN -> {
                if (config.enablePan) {
                    panEnd.set(deltaX, deltaY)
                    val panDelta = panEnd.clone().sub(panStart).multiplyScalar(mapConfig.panSpeed)

                    pan(panDelta.x, panDelta.y)
                    panStart.copy(panEnd)
                    dispatchEvent("change")
                }
            }

            ControlMode.ROTATE -> {
                if (mapConfig.enableRotation) {
                    rotateEnd.set(deltaX, deltaY)
                    val rotateDelta = rotateEnd.clone().sub(rotateStart)

                    currentRotation += rotateDelta.x * mapConfig.rotationSpeed * 0.01f
                    if (mapConfig.enableTilt) {
                        currentTilt += rotateDelta.y * mapConfig.tiltSpeed * 0.01f
                        currentTilt = currentTilt.coerceIn(0f, mapConfig.maxTiltAngle)
                    }

                    updateCameraOrientation()
                    rotateStart.copy(rotateEnd)
                    dispatchEvent("change")
                }
            }

            ControlMode.ZOOM -> {
                zoomEnd.set(deltaX, deltaY)
                val zoomDelta = zoomEnd.clone().sub(zoomStart)

                if (zoomDelta.y < 0) {
                    zoomIn(mapConfig.zoomSpeed)
                } else {
                    zoomOut(mapConfig.zoomSpeed)
                }

                zoomStart.copy(zoomEnd)
                dispatchEvent("change")
            }

            ControlMode.TILT -> {
                if (mapConfig.enableTilt) {
                    val tiltDelta = deltaY * mapConfig.tiltSpeed * 0.01f
                    currentTilt += tiltDelta
                    currentTilt = currentTilt.coerceIn(0f, mapConfig.maxTiltAngle)
                    updateCameraOrientation()
                    dispatchEvent("change")
                }
            }

            ControlMode.NONE -> { /* No action */
            }
        }
    }

    override fun onPointerDown(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        mode = when (button) {
            PointerButton.PRIMARY -> {
                if (config.enablePan) {
                    panStart.set(x, y)
                    ControlMode.PAN
                } else ControlMode.NONE
            }

            PointerButton.SECONDARY -> {
                if (mapConfig.enableRotation) {
                    rotateStart.set(x, y)
                    ControlMode.ROTATE
                } else ControlMode.NONE
            }

            PointerButton.AUXILIARY -> {
                if (config.enableZoom) {
                    zoomStart.set(x, y)
                    ControlMode.ZOOM
                } else ControlMode.NONE
            }
        }

        if (mode != ControlMode.NONE) {
            state.isPointerDown = true
            state.pointerButton = button
            animating = false // Stop any ongoing animation
            dispatchEvent("start")
        }
    }

    override fun onPointerUp(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        if (state.isPointerDown && state.pointerButton == button) {
            mode = ControlMode.NONE
            state.isPointerDown = false
            dispatchEvent("end")
        }
    }

    override fun onWheel(deltaX: Float, deltaY: Float) {
        if (!enabled || !config.enableZoom) return

        if (deltaY < 0) {
            zoomIn(mapConfig.zoomSpeed)
        } else if (deltaY > 0) {
            zoomOut(mapConfig.zoomSpeed)
        }

        dispatchEvent("change")
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
        flyTo(target, currentZoom, duration)
    }

    // Map-specific methods

    /**
     * Fly to a specific location with optional zoom and duration
     */
    fun flyTo(
        target: Vector3,
        zoom: Float = currentZoom,
        duration: Float = 1f,
        rotation: Float = currentRotation,
        tilt: Float = currentTilt
    ) {
        animationStartPos.copy(this.target)
        animationTargetPos.copy(target)
        animationStartZoom = currentZoom
        animationTargetZoom = zoom.coerceIn(mapConfig.minZoom, mapConfig.maxZoom)

        animating = true
        animationStartTime = getCurrentTime()
        animationDuration = duration

        currentRotation = rotation
        currentTilt = tilt.coerceIn(0f, mapConfig.maxTiltAngle)
    }

    /**
     * Set map bounds to restrict panning
     */
    fun setBounds(bounds: Box3) {
        mapConfig.bounds.copy(bounds)
    }

    /**
     * Get current map bounds
     */
    fun getBounds(): Box3 = mapConfig.bounds

    /**
     * Set zoom level
     */
    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        updateCameraPosition()
    }

    /**
     * Get current zoom level
     */
    fun getZoom(): Float = currentZoom

    /**
     * Set map rotation
     */
    fun setRotation(rotation: Float) {
        currentRotation = rotation
        updateCameraOrientation()
    }

    /**
     * Get current map rotation
     */
    fun getRotation(): Float = currentRotation

    /**
     * Set map tilt
     */
    fun setTilt(tilt: Float) {
        currentTilt = tilt.coerceIn(0f, mapConfig.maxTiltAngle)
        updateCameraOrientation()
    }

    /**
     * Get current map tilt
     */
    fun getTilt(): Float = currentTilt

    /**
     * Convert screen coordinates to world coordinates
     */
    fun screenToWorld(screenX: Float, screenY: Float): Vector3? {
        // Ray casting from screen to world space
        val ndcX = (screenX / 1920f) * 2f - 1f // Assuming standard viewport width
        val ndcY = 1f - (screenY / 1080f) * 2f // Assuming standard viewport height

        // Create ray from camera through screen point
        val rayOrigin = camera.position.clone()
        val rayDirection = Vector3(ndcX, ndcY, -1f).unproject(camera)
        rayDirection.sub(rayOrigin)

        val rayLength = rayDirection.length()
        if (rayLength > 0.001f) {
            rayDirection.normalize()
        } else {
            // Default to downward ray if degenerate
            rayDirection.set(0f, -1f, 0f)
        }

        // Intersect with ground plane (y = 0)
        val t = if (abs(rayDirection.y) > 0.000001f) {
            -rayOrigin.y / rayDirection.y
        } else {
            -1f // Ray parallel to ground
        }
        if (t < 0) return null // Ray pointing away from ground

        return Vector3(
            rayOrigin.x + rayDirection.x * t,
            0f,
            rayOrigin.z + rayDirection.z * t
        )
    }

    /**
     * Convert world coordinates to screen coordinates
     */
    fun worldToScreen(worldPos: Vector3): Vector2? {
        // Project world position to screen space
        val projected = worldPos.clone().project(camera)

        // Convert from NDC to screen coordinates
        val screenX = (projected.x + 1f) * 960f // Half of standard viewport width
        val screenY = (1f - projected.y) * 540f // Half of standard viewport height

        // Check if behind camera
        if (projected.z < -1f || projected.z > 1f) return null

        return Vector2(screenX, screenY)
    }

    // Internal methods

    private fun setupMapView() {
        // Position camera for top-down map view
        target.set(0f, 0f, 0f)
        targetHeight = 10f
        currentZoom = 1f
        currentRotation = 0f
        currentTilt = 0f

        updateCameraPosition()
        updateCameraOrientation()
    }

    private fun updateCameraPosition() {
        val height = targetHeight / currentZoom
        height.coerceIn(mapConfig.minHeight, mapConfig.maxHeight)

        // Calculate position based on tilt
        val tiltOffset = height * sin(currentTilt)
        val actualHeight = height * cos(currentTilt)

        // Position camera above target
        camera.position.set(
            target.x + tiltOffset * sin(currentRotation),
            target.y + actualHeight,
            target.z + tiltOffset * cos(currentRotation)
        )

        // Update zoom for orthographic camera
        val cam = camera // Create immutable reference for smart casting
        if (cam is OrthographicCamera) {
            cam.zoom = currentZoom
            cam.updateProjectionMatrix()
        }
    }

    private fun updateCameraOrientation() {
        updateCameraPosition()
        camera.lookAt(target)
    }

    private fun pan(deltaX: Float, deltaY: Float) {
        val distance = camera.position.distanceTo(target)
        val factor = distance * 0.001f

        // Calculate pan vector in world space
        val panVector = Vector3()

        val cam = camera // Create immutable reference for smart casting
        if (cam is PerspectiveCamera) {
            val fov = cam.fov * PI.toFloat() / 180f
            val targetDistance = distance * tan(fov / 2f)
            val panX = deltaX * targetDistance * factor
            val panY = deltaY * targetDistance * factor

            // Transform pan relative to camera orientation
            val forward = Vector3(0f, 0f, -1f).applyQuaternion(cam.quaternion)
            val right = Vector3(1f, 0f, 0f).applyQuaternion(cam.quaternion)

            panVector.add(right.multiplyScalar(-panX))
            panVector.add(forward.multiplyScalar(-panY))
        } else {
            // Orthographic projection
            panVector.set(-deltaX * factor, 0f, -deltaY * factor)
        }

        target.add(panVector)
        updateCameraPosition()
    }

    private fun zoomIn(factor: Float) {
        if (mapConfig.smoothZoom) {
            currentZoom *= factor
        } else {
            currentZoom = (currentZoom * factor).coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        }
        currentZoom = currentZoom.coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        updateCameraPosition()
    }

    private fun zoomOut(factor: Float) {
        if (mapConfig.smoothZoom) {
            currentZoom /= factor
        } else {
            currentZoom = (currentZoom / factor).coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        }
        currentZoom = currentZoom.coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        updateCameraPosition()
    }

    private fun handleKeyboardInput(deltaTime: Float): Boolean {
        val moveSpeed = config.keyboardSpeed * deltaTime
        var moved = false

        // WASD for panning
        if (Key.W in state.keysDown) {
            pan(0f, -moveSpeed * 100f)
            moved = true
        }
        if (Key.S in state.keysDown) {
            pan(0f, moveSpeed * 100f)
            moved = true
        }
        if (Key.A in state.keysDown) {
            pan(-moveSpeed * 100f, 0f)
            moved = true
        }
        if (Key.D in state.keysDown) {
            pan(moveSpeed * 100f, 0f)
            moved = true
        }

        // Q/E for zoom
        if (Key.Q in state.keysDown) {
            zoomOut(1f + moveSpeed)
            moved = true
        }
        if (Key.E in state.keysDown) {
            zoomIn(1f + moveSpeed)
            moved = true
        }

        // Arrow keys for rotation
        if (Key.ARROW_LEFT in state.keysDown) {
            currentRotation -= moveSpeed
            updateCameraOrientation()
            moved = true
        }
        if (Key.ARROW_RIGHT in state.keysDown) {
            currentRotation += moveSpeed
            updateCameraOrientation()
            moved = true
        }
        if (Key.ARROW_UP in state.keysDown) {
            currentTilt = (currentTilt + moveSpeed).coerceIn(0f, mapConfig.maxTiltAngle)
            updateCameraOrientation()
            moved = true
        }
        if (Key.ARROW_DOWN in state.keysDown) {
            currentTilt = (currentTilt - moveSpeed).coerceIn(0f, mapConfig.maxTiltAngle)
            updateCameraOrientation()
            moved = true
        }

        return moved
    }

    private fun updateAnimation(): Boolean {
        val elapsed = getCurrentTime() - animationStartTime
        val progress = (elapsed / animationDuration).coerceIn(0f, 1f)

        if (progress >= 1f) {
            // Animation complete
            target.copy(animationTargetPos)
            currentZoom = animationTargetZoom
            animating = false
            updateCameraPosition()
            return true
        } else {
            // Interpolate
            val smoothProgress = smoothstep(progress)
            target.lerpVectors(animationStartPos, animationTargetPos, smoothProgress)
            currentZoom =
                animationStartZoom + (animationTargetZoom - animationStartZoom) * smoothProgress
            updateCameraPosition()
            return true
        }
    }

    private fun applyConstraints(): Boolean {
        var changed = false

        // Apply bounds constraints
        if (mapConfig.enableBounds && !mapConfig.bounds.isEmpty()) {
            val clampedTarget = target.clone().clamp(mapConfig.bounds.min, mapConfig.bounds.max)
            if (!clampedTarget.equals(target)) {
                target.copy(clampedTarget)
                updateCameraPosition()
                changed = true
            }
        }

        // Apply zoom constraints
        val clampedZoom = currentZoom.coerceIn(mapConfig.minZoom, mapConfig.maxZoom)
        if (clampedZoom != currentZoom) {
            currentZoom = clampedZoom
            updateCameraPosition()
            changed = true
        }

        return changed
    }

    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)

    // Platform-specific time function
    private fun getCurrentTime(): Float {
        // Use Kotlin's system time in milliseconds converted to seconds
        return (kotlin.time.TimeSource.Monotonic.markNow()
            .elapsedNow().inWholeMilliseconds / 1000.0f)
    }
}