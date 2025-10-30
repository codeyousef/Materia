package io.materia.controls

import io.materia.camera.Camera
import io.materia.camera.OrthographicCamera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3

/**
 * Factory for creating different types of camera controls
 * Provides convenient methods to create commonly used control configurations
 */
object ControlsFactory {

    /**
     * Create orbit controls with default configuration
     */
    fun createOrbitControls(
        camera: Camera,
        target: Vector3 = Vector3.ZERO,
        config: ControlsConfig = ControlsConfig()
    ): OrbitControls {
        val controls = OrbitControls(camera, config)
        controls.target = target
        return controls
    }

    /**
     * Create orbit controls optimized for 3D model viewing
     */
    fun createModelViewerControls(
        camera: Camera,
        target: Vector3 = Vector3.ZERO,
        minDistance: Float = 2f,
        maxDistance: Float = 100f
    ): OrbitControls {
        val config = ControlsConfig(
            minDistance = minDistance,
            maxDistance = maxDistance,
            enableDamping = true,
            dampingFactor = 0.1f,
            rotateSpeed = 1.5f,
            zoomSpeed = 1.2f,
            panSpeed = 0.8f,
            autoRotate = false
        )
        return createOrbitControls(camera, target, config)
    }

    /**
     * Create orbit controls for architectural visualization
     */
    fun createArchitecturalControls(
        camera: Camera,
        target: Vector3 = Vector3.ZERO
    ): OrbitControls {
        val config = ControlsConfig(
            minDistance = 1f,
            maxDistance = 1000f,
            minPolarAngle = 0f,
            maxPolarAngle = kotlin.math.PI.toFloat() / 2f - 0.1f, // Prevent going below ground
            enableDamping = true,
            dampingFactor = 0.05f,
            rotateSpeed = 0.8f,
            zoomSpeed = 1f,
            panSpeed = 1.2f
        )
        return createOrbitControls(camera, target, config)
    }

    /**
     * Create first-person controls with default configuration
     */
    fun createFirstPersonControls(
        camera: Camera,
        config: ControlsConfig = ControlsConfig()
    ): FirstPersonControls {
        return FirstPersonControls(camera, config)
    }

    /**
     * Create first-person controls optimized for gaming
     */
    fun createGameControls(
        camera: Camera,
        walkSpeed: Float = 5f,
        runSpeed: Float = 10f,
        mouseSensitivity: Float = 0.002f
    ): FirstPersonControls {
        val config = ControlsConfig(
            enableDamping = false,
            rotateSpeed = 1f,
            keyboardSpeed = 1f,
            enableKeys = true,
            enableRotate = true
        )
        val controls = FirstPersonControls(camera, config)
        controls.walkSpeed = walkSpeed
        controls.runSpeed = runSpeed
        controls.mouseSensitivity = mouseSensitivity
        controls.enableGravity = true
        controls.enableSmoothing = true
        return controls
    }

    /**
     * Create first-person controls for architectural walkthrough
     */
    fun createWalkthroughControls(
        camera: Camera,
        groundHeight: Float = 0f
    ): FirstPersonControls {
        val config = ControlsConfig(
            enableDamping = true,
            dampingFactor = 0.1f,
            rotateSpeed = 0.8f,
            keyboardSpeed = 1f
        )
        val controls = FirstPersonControls(camera, config)
        controls.walkSpeed = 3f
        controls.runSpeed = 6f
        controls.mouseSensitivity = 0.001f
        controls.enableGravity = true
        controls.groundHeight = groundHeight
        controls.enableCollision = true
        controls.enableSmoothing = true
        return controls
    }

    /**
     * Create map controls with default configuration
     */
    fun createMapControls(
        camera: Camera,
        config: ControlsConfig = ControlsConfig()
    ): MapControls {
        return MapControls(camera, config)
    }

    /**
     * Create map controls optimized for 2D mapping
     */
    fun create2DMapControls(
        camera: OrthographicCamera,
        minZoom: Float = 0.1f,
        maxZoom: Float = 10f
    ): MapControls {
        val config = ControlsConfig(
            enableRotate = false,
            enableDamping = true,
            dampingFactor = 0.1f,
            panSpeed = 1f,
            zoomSpeed = 1.2f
        )
        return MapControls(camera, config)
    }

    /**
     * Create map controls optimized for 3D terrain
     */
    fun create3DMapControls(
        camera: PerspectiveCamera,
        minHeight: Float = 1f,
        maxHeight: Float = 1000f
    ): MapControls {
        val config = ControlsConfig(
            enableRotate = true,
            enableDamping = true,
            dampingFactor = 0.08f,
            panSpeed = 1.2f,
            zoomSpeed = 1.5f,
            rotateSpeed = 1f
        )
        return MapControls(camera, config)
    }

    /**
     * Create adaptive controls that choose the best type based on camera and use case
     */
    fun createAdaptiveControls(
        camera: Camera,
        useCase: ControlsUseCase = ControlsUseCase.GENERAL_3D,
        target: Vector3 = Vector3.ZERO
    ): CameraControls {
        return when (useCase) {
            ControlsUseCase.MODEL_VIEWER -> createModelViewerControls(camera, target)
            ControlsUseCase.ARCHITECTURAL -> createArchitecturalControls(camera, target)
            ControlsUseCase.GAME_FPS -> createGameControls(camera)
            ControlsUseCase.WALKTHROUGH -> createWalkthroughControls(camera)
            ControlsUseCase.MAP_2D -> {
                if (camera is OrthographicCamera) {
                    create2DMapControls(camera)
                } else {
                    createMapControls(camera)
                }
            }

            ControlsUseCase.MAP_3D -> {
                if (camera is PerspectiveCamera) {
                    create3DMapControls(camera)
                } else {
                    createMapControls(camera)
                }
            }

            ControlsUseCase.GENERAL_3D -> createOrbitControls(camera, target)
        }
    }

    /**
     * Create controls with touch/mobile optimizations
     */
    fun createTouchControls(
        camera: Camera,
        baseControls: CameraControls
    ): TouchControls {
        return TouchControls(baseControls)
    }
}

/**
 * Use cases for different control schemes
 */
enum class ControlsUseCase {
    MODEL_VIEWER,   // 3D model inspection
    ARCHITECTURAL,  // Building/room exploration
    GAME_FPS,       // First-person gaming
    WALKTHROUGH,    // Architectural walkthrough
    MAP_2D,         // 2D map navigation
    MAP_3D,         // 3D terrain/map navigation
    GENERAL_3D      // General 3D scene navigation
}

/**
 * Touch-optimized controls wrapper
 * Adds gesture recognition and mobile-specific behaviors
 */
class TouchControls(
    private val baseControls: CameraControls
) : CameraControls by baseControls {

    // Touch state
    private var isMultiTouch = false
    private var lastTouchDistance = 0f
    private var lastTouchAngle = 0f
    private val touchPoints = mutableMapOf<Int, Vector2>()

    // Gesture recognition
    var pinchSensitivity: Float = 1f
    var rotateSensitivity: Float = 1f
    var enablePinchZoom: Boolean = true
    var enableRotateGesture: Boolean = true
    var enableTwoFingerPan: Boolean = true

    /**
     * Handle touch start
     */
    fun onTouchStart(pointerId: Int, x: Float, y: Float) {
        touchPoints[pointerId] = Vector2(x, y)

        when (touchPoints.size) {
            1 -> {
                // Single touch - start primary action
                isMultiTouch = false
                baseControls.onPointerDown(x, y, PointerButton.PRIMARY)
            }

            2 -> {
                // Two touches - start gesture recognition
                isMultiTouch = true
                val points = touchPoints.values.toList()
                lastTouchDistance = points[0].distanceTo(points[1])
                lastTouchAngle = calculateAngle(points[0], points[1])
            }
        }
    }

    /**
     * Handle touch move
     */
    fun onTouchMove(pointerId: Int, x: Float, y: Float) {
        val oldPos = touchPoints[pointerId] ?: return
        val newPos = Vector2(x, y)
        touchPoints[pointerId] = newPos

        if (isMultiTouch && touchPoints.size >= 2) {
            handleMultiTouch()
        } else if (!isMultiTouch && touchPoints.size == 1) {
            val delta = newPos.clone().sub(oldPos)
            baseControls.onPointerMove(delta.x, delta.y, PointerButton.PRIMARY)
        }
    }

    /**
     * Handle touch end
     */
    fun onTouchEnd(pointerId: Int, x: Float, y: Float) {
        touchPoints.remove(pointerId)

        if (touchPoints.isEmpty()) {
            isMultiTouch = false
            baseControls.onPointerUp(x, y, PointerButton.PRIMARY)
        } else if (touchPoints.size == 1 && isMultiTouch) {
            // Transition from multi-touch to single touch
            isMultiTouch = false
            val remainingPoint = touchPoints.values.first()
            baseControls.onPointerDown(remainingPoint.x, remainingPoint.y, PointerButton.PRIMARY)
        }
    }

    private fun handleMultiTouch() {
        val points = touchPoints.values.toList()
        if (points.size < 2) return

        val point1 = points[0]
        val point2 = points[1]

        // Calculate pinch/zoom
        if (enablePinchZoom) {
            val currentDistance = point1.distanceTo(point2)
            val distanceDelta = currentDistance - lastTouchDistance

            if (kotlin.math.abs(distanceDelta) > 5f) { // Threshold to prevent jitter
                baseControls.onWheel(0f, -distanceDelta * pinchSensitivity)
                lastTouchDistance = currentDistance
            }
        }

        // Calculate rotation
        if (enableRotateGesture) {
            val currentAngle = calculateAngle(point1, point2)
            val angleDelta = currentAngle - lastTouchAngle

            if (kotlin.math.abs(angleDelta) > 0.1f) { // Threshold to prevent jitter
                // Apply rotation based on control type
                when (baseControls) {
                    is OrbitControls -> {
                        // Rotate around target
                        baseControls.onPointerMove(
                            angleDelta * rotateSensitivity * 100f, 0f,
                            PointerButton.SECONDARY
                        )
                    }

                    is MapControls -> {
                        // Map rotation - these methods would need to be implemented in MapControls
                        // For now, just pass rotation as pointer movement
                        baseControls.onPointerMove(
                            angleDelta * rotateSensitivity * 100f, 0f,
                            PointerButton.SECONDARY
                        )
                    }
                }
                lastTouchAngle = currentAngle
            }
        }

        // Two-finger pan
        if (enableTwoFingerPan && points.size == 2) {
            val center = point1.clone().add(point2).divideScalar(2f)
            // Implementation would track center movement for panning
        }
    }

    private fun calculateAngle(point1: Vector2, point2: Vector2): Float {
        return kotlin.math.atan2((point2.y - point1.y).toDouble(), (point2.x - point1.x).toDouble())
            .toFloat()
    }
}