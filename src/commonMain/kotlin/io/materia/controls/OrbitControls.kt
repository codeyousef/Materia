package io.materia.controls

import io.materia.camera.Camera
import io.materia.camera.PerspectiveCamera
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.tan

/**
 * Spherical orbit camera controller with frame-rate-independent settling and
 * interruptible pose transitions.
 */
class OrbitControls(
    camera: Camera,
    config: ControlsConfig = ControlsConfig()
) : BaseCameraControls(camera, config) {

    private enum class ControlMode {
        NONE, ROTATE, DOLLY, PAN
    }

    private var mode = ControlMode.NONE

    private val rotateStart = Vector2()
    private val rotateEnd = Vector2()
    private val rotateDelta = Vector2()
    private val dollyStart = Vector2()
    private val dollyEnd = Vector2()
    private val dollyDelta = Vector2()
    private val panStart = Vector2()
    private val panEnd = Vector2()
    private val panDelta = Vector2()

    private val requestedSpherical = SphericalCoordinate()
    private val panOffset = state.panOffset
    private val lastPosition = camera.position.clone()
    private val lastQuaternion = camera.quaternion.clone()

    init {
        syncSphericalFromCamera()
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return

        val safeDelta = deltaTime
            .coerceAtLeast(0f)
            .coerceAtMost(config.maxDeltaTime.coerceAtLeast(0f))

        if (advancePoseAnimation(safeDelta)) {
            notifyIfChanged(force = true)
            return
        }

        var forceChange = false
        if (config.autoRotate && mode == ControlMode.NONE) {
            rotateLeft(getAutoRotationAngle(safeDelta))
            forceChange = true
        }

        if (config.enableKeys && state.keysDown.isNotEmpty()) {
            handleKeyboardInput(safeDelta)
            forceChange = true
        }

        if (panOffset.lengthSq() > 0f) {
            target.add(panOffset)
            panOffset.set(0f, 0f, 0f)
            forceChange = true
        }

        constrainSpherical(requestedSpherical)
        if (config.enableDamping) {
            val alpha = if (config.dampingTime <= 0f) {
                1f
            } else {
                (1.0 - exp((-safeDelta / config.dampingTime).toDouble())).toFloat()
            }
            state.spherical.theta += (requestedSpherical.theta - state.spherical.theta) * alpha
            state.spherical.phi += (requestedSpherical.phi - state.spherical.phi) * alpha
            state.spherical.radius += (requestedSpherical.radius - state.spherical.radius) * alpha
            snapSettledAxes()
        } else {
            copySpherical(requestedSpherical, state.spherical)
        }

        constrainSpherical(state.spherical)
        camera.position.copy(state.spherical.toCartesian().add(target))
        camera.lookAt(target)
        state.scale = 1f

        notifyIfChanged(forceChange)
    }

    override fun onPointerMove(deltaX: Float, deltaY: Float, button: PointerButton) {
        if (!enabled) return

        when (mode) {
            ControlMode.ROTATE -> if (config.enableRotate) {
                rotateEnd.set(deltaX, deltaY)
                rotateDelta.subVectors(rotateEnd, rotateStart).multiplyScalar(config.rotateSpeed)
                rotateLeft(2f * PI.toFloat() * rotateDelta.x / POINTER_REFERENCE_SIZE)
                rotateUp(2f * PI.toFloat() * rotateDelta.y / POINTER_REFERENCE_SIZE)
                rotateStart.copy(rotateEnd)
            }

            ControlMode.DOLLY -> if (config.enableZoom) {
                dollyEnd.set(deltaX, deltaY)
                dollyDelta.subVectors(dollyEnd, dollyStart)
                when {
                    dollyDelta.y > 0 -> dollyOut(getZoomScale())
                    dollyDelta.y < 0 -> dollyIn(getZoomScale())
                }
                dollyStart.copy(dollyEnd)
            }

            ControlMode.PAN -> if (config.enablePan) {
                panEnd.set(deltaX, deltaY)
                panDelta.subVectors(panEnd, panStart).multiplyScalar(config.panSpeed)
                pan(panDelta.x, panDelta.y)
                panStart.copy(panEnd)
            }

            ControlMode.NONE -> Unit
        }
    }

    override fun onPointerDown(x: Float, y: Float, button: PointerButton) {
        if (!enabled) return

        cancelAnimation()
        cancelMomentum()
        mode = when (button) {
            PointerButton.PRIMARY -> if (config.enableRotate) {
                rotateStart.set(x, y)
                ControlMode.ROTATE
            } else ControlMode.NONE

            PointerButton.SECONDARY -> if (config.enablePan) {
                panStart.set(x, y)
                ControlMode.PAN
            } else ControlMode.NONE

            PointerButton.AUXILIARY -> if (config.enableZoom) {
                dollyStart.set(x, y)
                ControlMode.DOLLY
            } else ControlMode.NONE
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
        cancelAnimation()
        when {
            deltaY < 0 -> dollyIn(getZoomScale())
            deltaY > 0 -> dollyOut(getZoomScale())
        }
    }

    override fun onKeyDown(key: Key) {
        if (!enabled || !config.enableKeys) return
        cancelAnimation()
        state.keysDown.add(key)
    }

    override fun onKeyUp(key: Key) {
        if (!enabled || !config.enableKeys) return
        state.keysDown.remove(key)
    }

    override fun lookAt(target: Vector3, duration: Float) {
        val targetDelta = target.clone().sub(this.target)
        moveTo(camera.position.clone().add(targetDelta), target, duration)
    }

    /** Smoothly move both the camera and its orbit target. Duration is in seconds. */
    fun moveTo(position: Vector3, target: Vector3, duration: Float = 1f) {
        cancelMomentum()
        if (duration <= 0f) {
            camera.position.copy(position)
            this.target.copy(target)
            camera.lookAt(this.target)
            clearAnimationState()
            syncSphericalFromCamera()
            notifyIfChanged(force = true)
            return
        }

        state.animationStartPosition = camera.position.clone()
        state.animationStartTarget = this.target.clone()
        state.targetPosition = position.clone()
        state.targetLookAt = target.clone()
        state.animationElapsed = 0f
        state.animationDuration = duration
    }

    /** Immediately apply a camera pose and reset all residual control motion. */
    fun setPose(position: Vector3, target: Vector3) {
        moveTo(position, target, duration = 0f)
    }

    /** Stop a guided transition at its current interpolated pose. */
    fun cancelAnimation() {
        if (state.targetPosition == null) return
        clearAnimationState()
        syncSphericalFromCamera()
    }

    /** Stop convergence at the camera's current pose. */
    fun cancelMomentum() {
        copySpherical(state.spherical, requestedSpherical)
        panOffset.set(0f, 0f, 0f)
        state.scale = 1f
    }

    /** True when neither a transition nor residual orbit convergence remains. */
    fun isSettled(): Boolean {
        return state.targetPosition == null &&
            abs(requestedSpherical.theta - state.spherical.theta) <= config.settleEpsilon &&
            abs(requestedSpherical.phi - state.spherical.phi) <= config.settleEpsilon &&
            abs(requestedSpherical.radius - state.spherical.radius) <= config.settleEpsilon
    }

    override fun reset() {
        super.reset()
        clearAnimationState()
        syncSphericalFromCamera()
        mode = ControlMode.NONE
    }

    fun getDistance(): Float = state.spherical.radius

    fun setDistance(distance: Float) {
        requestedSpherical.radius = distance.coerceIn(config.minDistance, config.maxDistance)
        if (!config.enableDamping) state.spherical.radius = requestedSpherical.radius
    }

    fun getPolarAngle(): Float = state.spherical.phi

    fun setPolarAngle(angle: Float) {
        requestedSpherical.phi = angle.coerceIn(config.minPolarAngle, config.maxPolarAngle)
        if (!config.enableDamping) state.spherical.phi = requestedSpherical.phi
    }

    fun getAzimuthalAngle(): Float = state.spherical.theta

    fun setAzimuthalAngle(angle: Float) {
        requestedSpherical.theta = angle.coerceIn(config.minAzimuthAngle, config.maxAzimuthAngle)
        if (!config.enableDamping) state.spherical.theta = requestedSpherical.theta
    }

    fun saveState(): ControlsState = state.copy(
        spherical = state.spherical.copy(),
        panOffset = state.panOffset.clone(),
        lastPointerPosition = state.lastPointerPosition.clone(),
        keysDown = state.keysDown.toMutableSet(),
        targetPosition = state.targetPosition?.clone(),
        animationStartPosition = state.animationStartPosition?.clone(),
        animationStartTarget = state.animationStartTarget?.clone(),
        targetLookAt = state.targetLookAt?.clone()
    )

    fun restoreState(savedState: ControlsState) {
        copySpherical(savedState.spherical, state.spherical)
        copySpherical(savedState.spherical, requestedSpherical)
        panOffset.copy(savedState.panOffset)
        state.scale = savedState.scale
        state.keysDown.clear()
        state.keysDown.addAll(savedState.keysDown)
        clearAnimationState()
        camera.position.copy(state.spherical.toCartesian().add(target))
        camera.lookAt(target)
    }

    private fun advancePoseAnimation(deltaTime: Float): Boolean {
        val endPosition = state.targetPosition ?: return false
        val endTarget = state.targetLookAt ?: return false
        val startPosition = state.animationStartPosition ?: camera.position.clone()
        val startTarget = state.animationStartTarget ?: target.clone()

        state.animationElapsed += deltaTime
        val progress = (state.animationElapsed / state.animationDuration).coerceIn(0f, 1f)
        val eased = smoothstep(progress)
        camera.position.lerpVectors(startPosition, endPosition, eased)
        target.lerpVectors(startTarget, endTarget, eased)
        camera.lookAt(target)

        if (progress >= 1f) {
            camera.position.copy(endPosition)
            target.copy(endTarget)
            camera.lookAt(target)
            clearAnimationState()
            syncSphericalFromCamera()
        }
        return true
    }

    private fun rotateLeft(angle: Float) {
        requestedSpherical.theta -= angle
    }

    private fun rotateUp(angle: Float) {
        requestedSpherical.phi -= angle
    }

    private fun dollyOut(dollyScale: Float) {
        val cam = camera
        if (cam is PerspectiveCamera) {
            requestedSpherical.radius = (requestedSpherical.radius / dollyScale)
                .coerceIn(config.minDistance, config.maxDistance)
        } else {
            cam.zoom /= dollyScale
            cam.updateProjectionMatrix()
        }
    }

    private fun dollyIn(dollyScale: Float) {
        val cam = camera
        if (cam is PerspectiveCamera) {
            requestedSpherical.radius = (requestedSpherical.radius * dollyScale)
                .coerceIn(config.minDistance, config.maxDistance)
        } else {
            cam.zoom *= dollyScale
            cam.updateProjectionMatrix()
        }
    }

    private fun pan(deltaX: Float, deltaY: Float) {
        val cam = camera
        if (cam is PerspectiveCamera) {
            val offset = cam.position.clone().sub(target)
            var targetDistance = offset.length()
            targetDistance *= tan((cam.fov / 2f) * PI.toFloat() / 180f)
            panLeft(2f * deltaX * targetDistance / POINTER_REFERENCE_SIZE, cam.matrix)
            panUp(2f * deltaY * targetDistance / POINTER_REFERENCE_SIZE, cam.matrix)
        } else {
            val zoomFactor = if (abs(cam.zoom) > 0.001f) cam.zoom else 1f
            panLeft(deltaX * (cam.right - cam.left) / zoomFactor / POINTER_REFERENCE_SIZE, cam.matrix)
            panUp(deltaY * (cam.top - cam.bottom) / zoomFactor / POINTER_REFERENCE_SIZE, cam.matrix)
        }
    }

    private fun panLeft(distance: Float, matrix: Matrix4) {
        val value = Vector3().setFromMatrixColumn(matrix, 0).multiplyScalar(-distance)
        panOffset.add(value)
    }

    private fun panUp(distance: Float, matrix: Matrix4) {
        val value = Vector3().setFromMatrixColumn(matrix, 1).multiplyScalar(distance)
        panOffset.add(value)
    }

    private fun handleKeyboardInput(deltaTime: Float) {
        val moveSpeed = config.keyboardSpeed * deltaTime
        val zoomScale = 0.95f.pow(config.zoomSpeed * deltaTime * 60f)

        if (Key.W in state.keysDown) dollyIn(zoomScale)
        if (Key.S in state.keysDown) dollyOut(zoomScale)
        if (Key.A in state.keysDown) panLeft(moveSpeed, camera.matrix)
        if (Key.D in state.keysDown) panLeft(-moveSpeed, camera.matrix)
        if (Key.Q in state.keysDown) panUp(moveSpeed, camera.matrix)
        if (Key.E in state.keysDown) panUp(-moveSpeed, camera.matrix)
        if (Key.ARROW_LEFT in state.keysDown) rotateLeft(moveSpeed)
        if (Key.ARROW_RIGHT in state.keysDown) rotateLeft(-moveSpeed)
        if (Key.ARROW_UP in state.keysDown) rotateUp(moveSpeed)
        if (Key.ARROW_DOWN in state.keysDown) rotateUp(-moveSpeed)
    }

    private fun getAutoRotationAngle(deltaTime: Float): Float {
        return (2f * PI.toFloat() / 60f) * config.autoRotateSpeed * deltaTime
    }

    private fun getZoomScale(): Float = 0.95f.pow(config.zoomSpeed)

    private fun smoothstep(value: Float): Float = value * value * (3f - 2f * value)

    private fun snapSettledAxes() {
        if (abs(requestedSpherical.theta - state.spherical.theta) <= config.settleEpsilon) {
            state.spherical.theta = requestedSpherical.theta
        }
        if (abs(requestedSpherical.phi - state.spherical.phi) <= config.settleEpsilon) {
            state.spherical.phi = requestedSpherical.phi
        }
        if (abs(requestedSpherical.radius - state.spherical.radius) <= config.settleEpsilon) {
            state.spherical.radius = requestedSpherical.radius
        }
    }

    private fun syncSphericalFromCamera() {
        state.spherical.fromVector3(camera.position.clone().sub(target))
        constrainSpherical(state.spherical)
        copySpherical(state.spherical, requestedSpherical)
    }

    private fun clearAnimationState() {
        state.targetPosition = null
        state.targetLookAt = null
        state.animationStartPosition = null
        state.animationStartTarget = null
        state.animationElapsed = 0f
        state.animationDuration = 0f
        state.animationStartTime = 0f
    }

    private fun notifyIfChanged(force: Boolean) {
        if (force ||
            lastPosition.distanceToSquared(camera.position) > CHANGE_EPSILON ||
            8f * (1f - lastQuaternion.dot(camera.quaternion)) > CHANGE_EPSILON
        ) {
            dispatchEvent("change")
            lastPosition.copy(camera.position)
            lastQuaternion.copy(camera.quaternion)
        }
    }

    private fun copySpherical(source: SphericalCoordinate, target: SphericalCoordinate) {
        target.radius = source.radius
        target.phi = source.phi
        target.theta = source.theta
    }

    private companion object {
        const val POINTER_REFERENCE_SIZE = 1000f
        const val CHANGE_EPSILON = 1e-6f
    }
}
