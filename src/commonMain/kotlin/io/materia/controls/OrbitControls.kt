package io.materia.controls

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tan

/**
 * Spherical orbit camera controller.
 *
 * Enables user interaction to orbit, zoom, and pan around a target point.
 * Supports mouse, touch, and keyboard input with configurable sensitivity,
 * damping, and constraints.
 *
 * Features:
 * - Left-drag: Rotate around target
 * - Right-drag: Pan camera
 * - Scroll wheel: Zoom in/out
 * - Keyboard: WASD for movement, arrows for rotation
 * - Auto-rotation when idle
 * - Smooth damping for natural feel
 * - Angle and distance constraints
 *
 * Compatible with the Three.js OrbitControls API.
 *
 * @param camera The camera to control.
 * @param config Optional configuration for sensitivity and constraints.
 */
class OrbitControls(
    camera: Camera,
    config: ControlsConfig = ControlsConfig()
) : BaseCameraControls(camera, config) {

    // Control modes
    private enum class ControlMode {
        NONE, ROTATE, DOLLY, PAN
    }

    private var mode = ControlMode.NONE

    // Rotation state
    private val rotateStart = Vector2()
    private val rotateEnd = Vector2()
    private val rotateDelta = Vector2()

    // Zoom/dolly state
    private val dollyStart = Vector2()
    private val dollyEnd = Vector2()
    private val dollyDelta = Vector2()

    // Pan state
    private val panStart = Vector2()
    private val panEnd = Vector2()
    private val panDelta = Vector2()

    // Working vectors
    private val sphericalDelta = SphericalCoordinate()
    private val sphericalDump = SphericalCoordinate()
    private val panOffset = Vector3()
    private val lastPosition = Vector3()
    private val lastQuaternion = camera.quaternion.clone()

    init {
        // Initialize spherical coordinates from current camera position
        state.spherical.fromVector3(offset)
        constrainSpherical(state.spherical)
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        val position = camera.position

        // Check if we need to update
        var hasChanged = false

        // Auto-rotation
        if (config.autoRotate && mode == ControlMode.NONE) {
            rotateLeft(getAutoRotationAngle(deltaTime))
            hasChanged = true
        }

        // Handle keyboard input
        if (config.enableKeys) {
            handleKeyboardInput(deltaTime)
            hasChanged = true
        }

        // Apply spherical delta from rotation
        state.spherical.theta += sphericalDelta.theta
        state.spherical.phi += sphericalDelta.phi

        // Apply scale to radius
        state.spherical.radius *= state.scale

        // Apply constraints
        constrainSpherical(state.spherical)

        // Convert spherical to cartesian
        val cartesian = state.spherical.toCartesian()
        position.copy(cartesian.add(target))

        // Look at target
        camera.lookAt(target)

        // Apply damping
        if (config.enableDamping && mode == ControlMode.NONE) {
            sphericalDelta.theta *= (1f - config.dampingFactor)
            sphericalDelta.phi *= (1f - config.dampingFactor)

            if (abs(sphericalDelta.theta) < 1e-6 && abs(sphericalDelta.phi) < 1e-6) {
                sphericalDelta.theta = 0f
                sphericalDelta.phi = 0f
            }
        } else {
            sphericalDelta.theta = 0f
            sphericalDelta.phi = 0f
        }

        state.scale = 1f

        // Update target position if we have pan offset
        if (panOffset.lengthSq() > 0f) {
            target.add(panOffset)
            camera.position.add(panOffset)
            panOffset.set(0f, 0f, 0f)
            hasChanged = true
        }

        // Check if position/rotation changed
        if (hasChanged ||
            lastPosition.distanceToSquared(camera.position) > 1e-6 ||
            8f * (1f - lastQuaternion.dot(camera.quaternion)) > 1e-6
        ) {

            dispatchEvent("change")
            lastPosition.copy(camera.position)
            lastQuaternion.copy(camera.quaternion)
        }

        // Handle smooth animation to target
        state.targetPosition?.let { targetPos ->
            val elapsed = getCurrentTime() - state.animationStartTime
            val progress = if (state.animationDuration > 0f) {
                (elapsed / state.animationDuration).coerceIn(0f, 1f)
            } else {
                1f
            }

            if (progress >= 1f) {
                // Animation complete
                camera.position.copy(targetPos)
                state.targetPosition = null
                hasChanged = true
            } else {
                // Interpolate position
                val startPos = lastPosition
                camera.position.lerpVectors(startPos, targetPos, smoothstep(progress))
                hasChanged = true
            }
        }
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        if (!enabled) return

        when (mode) {
            ControlMode.ROTATE -> {
                if (config.enableRotate) {
                    rotateEnd.set(deltaX, deltaY)
                    rotateDelta.subVectors(rotateEnd, rotateStart)
                        .multiplyScalar(config.rotateSpeed)

                    val element = camera.matrix.elements
                    rotateLeft(2f * PI.toFloat() * rotateDelta.x / 1000f) // Assuming screen width ~1000
                    rotateUp(2f * PI.toFloat() * rotateDelta.y / 1000f)

                    rotateStart.copy(rotateEnd)
                    dispatchEvent("change")
                }
            }

            ControlMode.DOLLY -> {
                if (config.enableZoom) {
                    dollyEnd.set(deltaX, deltaY)
                    dollyDelta.subVectors(dollyEnd, dollyStart)

                    if (dollyDelta.y > 0) {
                        dollyOut(getZoomScale())
                    } else if (dollyDelta.y < 0) {
                        dollyIn(getZoomScale())
                    }

                    dollyStart.copy(dollyEnd)
                    dispatchEvent("change")
                }
            }

            ControlMode.PAN -> {
                if (config.enablePan) {
                    panEnd.set(deltaX, deltaY)
                    panDelta.subVectors(panEnd, panStart).multiplyScalar(config.panSpeed)

                    pan(panDelta.x, panDelta.y)
                    panStart.copy(panEnd)
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
                if (config.enableRotate) {
                    rotateStart.set(x, y)
                    ControlMode.ROTATE
                } else ControlMode.NONE
            }

            PointerButton.SECONDARY -> {
                if (config.enablePan) {
                    panStart.set(x, y)
                    ControlMode.PAN
                } else ControlMode.NONE
            }

            PointerButton.AUXILIARY -> {
                if (config.enableZoom) {
                    dollyStart.set(x, y)
                    ControlMode.DOLLY
                } else ControlMode.NONE
            }
        }

        if (mode != ControlMode.NONE) {
            state.isPointerDown = true
            state.pointerButton = button
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
            dollyIn(getZoomScale())
        } else if (deltaY > 0) {
            dollyOut(getZoomScale())
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
        val distance = this.target.distanceTo(target)
        val newPosition = camera.position.clone().add(target.clone().sub(this.target))

        this.target.copy(target)
        state.targetPosition = newPosition
        state.animationStartTime = getCurrentTime()
        state.animationDuration = duration
    }

    // Internal methods

    private fun rotateLeft(angle: Float) {
        sphericalDelta.theta -= angle
    }

    private fun rotateUp(angle: Float) {
        sphericalDelta.phi -= angle
    }

    private fun dollyOut(dollyScale: Float) {
        val cam = camera // Create immutable reference for smart casting
        if (cam is PerspectiveCamera) {
            state.scale /= dollyScale
        } else {
            cam.zoom /= dollyScale
            cam.updateProjectionMatrix()
        }
    }

    private fun dollyIn(dollyScale: Float) {
        val cam = camera // Create immutable reference for smart casting
        if (cam is PerspectiveCamera) {
            state.scale *= dollyScale
        } else {
            cam.zoom *= dollyScale
            cam.updateProjectionMatrix()
        }
    }

    private fun pan(deltaX: Float, deltaY: Float) {
        val offset = Vector3()
        val cam = camera // Create immutable reference for smart casting

        if (cam is PerspectiveCamera) {
            // Perspective projection
            val position = cam.position
            offset.copy(position).sub(target)
            var targetDistance = offset.length()

            // Half of the fov is center to top of screen
            targetDistance *= tan((cam.fov / 2f) * PI.toFloat() / 180f)

            // Calculate pan vectors
            panLeft(
                2f * deltaX * targetDistance / 1000f,
                cam.matrix
            ) // Assuming screen height ~1000
            panUp(2f * deltaY * targetDistance / 1000f, cam.matrix)
        } else {
            // Orthographic projection
            val zoomFactor = if (abs(cam.zoom) > 0.001f) cam.zoom else 1f
            panLeft(deltaX * (cam.right - cam.left) / zoomFactor / 1000f, cam.matrix)
            panUp(deltaY * (cam.top - cam.bottom) / zoomFactor / 1000f, cam.matrix)
        }
    }

    private fun panLeft(distance: Float, matrix: Matrix4) {
        val v = Vector3()
        v.setFromMatrixColumn(matrix, 0) // Get X column of matrix
        v.multiplyScalar(-distance)
        panOffset.add(v)
    }

    private fun panUp(distance: Float, matrix: Matrix4) {
        val v = Vector3()
        v.setFromMatrixColumn(matrix, 1) // Get Y column of matrix
        v.multiplyScalar(distance)
        panOffset.add(v)
    }

    private fun handleKeyboardInput(deltaTime: Float) {
        val moveSpeed = config.keyboardSpeed * deltaTime

        if (Key.W in state.keysDown) {
            dollyIn(getZoomScale())
        }
        if (Key.S in state.keysDown) {
            dollyOut(getZoomScale())
        }
        if (Key.A in state.keysDown) {
            panLeft(moveSpeed, camera.matrix)
        }
        if (Key.D in state.keysDown) {
            panLeft(-moveSpeed, camera.matrix)
        }
        if (Key.Q in state.keysDown) {
            panUp(moveSpeed, camera.matrix)
        }
        if (Key.E in state.keysDown) {
            panUp(-moveSpeed, camera.matrix)
        }

        // Arrow keys for rotation
        if (Key.ARROW_LEFT in state.keysDown) {
            rotateLeft(moveSpeed)
        }
        if (Key.ARROW_RIGHT in state.keysDown) {
            rotateLeft(-moveSpeed)
        }
        if (Key.ARROW_UP in state.keysDown) {
            rotateUp(moveSpeed)
        }
        if (Key.ARROW_DOWN in state.keysDown) {
            rotateUp(-moveSpeed)
        }
    }

    private fun getAutoRotationAngle(deltaTime: Float): Float {
        return (config.autoRotateSpeed / 60f) * deltaTime // 60 fps reference
    }

    private fun getZoomScale(): Float {
        return 0.95f.pow(config.zoomSpeed)
    }

    private fun smoothstep(t: Float): Float {
        return t * t * (3f - 2f * t)
    }

    // Platform-specific time function
    private fun getCurrentTime(): Float {
        // Use Kotlin's system time in milliseconds converted to seconds
        return (kotlin.time.TimeSource.Monotonic.markNow()
            .elapsedNow().inWholeMilliseconds / 1000.0f)
    }

    /**
     * Returns the current distance from the camera to the target.
     */
    fun getDistance(): Float = state.spherical.radius

    /**
     * Sets the distance from the camera to the target.
     *
     * Clamped to [ControlsConfig.minDistance] and [ControlsConfig.maxDistance].
     *
     * @param distance The desired distance.
     */
    fun setDistance(distance: Float) {
        state.spherical.radius = distance.coerceIn(config.minDistance, config.maxDistance)
    }

    /**
     * Returns the polar (vertical) angle in radians.
     *
     * 0 is looking down the Y axis, PI/2 is horizontal.
     */
    fun getPolarAngle(): Float = state.spherical.phi

    /**
     * Sets the polar (vertical) angle.
     *
     * Clamped to [ControlsConfig.minPolarAngle] and [ControlsConfig.maxPolarAngle].
     *
     * @param angle The angle in radians.
     */
    fun setPolarAngle(angle: Float) {
        state.spherical.phi = angle.coerceIn(config.minPolarAngle, config.maxPolarAngle)
    }

    /**
     * Returns the azimuthal (horizontal) angle in radians.
     */
    fun getAzimuthalAngle(): Float = state.spherical.theta

    /**
     * Sets the azimuthal (horizontal) angle.
     *
     * Clamped to [ControlsConfig.minAzimuthAngle] and [ControlsConfig.maxAzimuthAngle].
     *
     * @param angle The angle in radians.
     */
    fun setAzimuthalAngle(angle: Float) {
        state.spherical.theta = angle.coerceIn(config.minAzimuthAngle, config.maxAzimuthAngle)
    }

    /**
     * Captures the current control state for later restoration.
     *
     * @return A snapshot of the current state.
     */
    fun saveState(): ControlsState {
        return state.copy()
    }

    /**
     * Restores a previously saved control state.
     *
     * @param savedState The state to restore.
     */
    fun restoreState(savedState: ControlsState) {
        state.spherical.radius = savedState.spherical.radius
        state.spherical.phi = savedState.spherical.phi
        state.spherical.theta = savedState.spherical.theta
        state.panOffset.copy(savedState.panOffset)
        state.scale = savedState.scale
        target.add(state.panOffset)
    }
}