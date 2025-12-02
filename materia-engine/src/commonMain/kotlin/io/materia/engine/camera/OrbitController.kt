package io.materia.engine.camera

import io.materia.engine.math.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Spherical orbit controller for interactive camera manipulation.
 *
 * Positions the camera on a sphere centered at [target], controlled by
 * spherical coordinates ([theta], [phi]) and [radius]. Supports rotation,
 * zoom, and panning operations.
 *
 * @param camera The camera to control.
 * @param target The point the camera orbits around.
 * @param radius Initial distance from the target.
 * @param theta Initial azimuthal angle (horizontal rotation) in radians.
 * @param phi Initial polar angle (vertical rotation) in radians, clamped to avoid gimbal lock.
 */
class OrbitController(
    private val camera: PerspectiveCamera,
    private val target: Vec3 = Vec3.Zero.copy(),
    radius: Float = 5f,
    theta: Float = 0f,
    phi: Float = 0.5f
) {
    var radius = radius
        private set

    var theta = theta
        private set

    var phi = phi
        private set

    /**
     * Rotates the camera around the target.
     *
     * @param deltaTheta Change in azimuthal angle (horizontal).
     * @param deltaPhi Change in polar angle (vertical).
     */
    fun rotate(deltaTheta: Float, deltaPhi: Float) {
        theta += deltaTheta
        phi = (phi + deltaPhi).coerceIn(0.01f, 3.04f)
        updateCameraPosition()
    }

    /**
     * Zooms the camera toward or away from the target.
     *
     * @param delta Change in radius (positive = zoom out, negative = zoom in).
     */
    fun zoom(delta: Float) {
        radius = (radius + delta).coerceIn(0.5f, 50f)
        updateCameraPosition()
    }

    /**
     * Pans the orbit center in the camera's local XY plane.
     *
     * @param deltaX Horizontal pan amount.
     * @param deltaY Vertical pan amount.
     */
    fun pan(deltaX: Float, deltaY: Float) {
        target.x += deltaX
        target.y += deltaY
        updateCameraPosition()
    }

    /**
     * Resets the controller to its default state.
     */
    fun reset() {
        radius = 5f
        theta = 0f
        phi = 0.5f
        updateCameraPosition()
    }

    private fun updateCameraPosition() {
        val x = target.x + radius * cos(theta.toDouble()).toFloat() * sin(phi.toDouble()).toFloat()
        val y = target.y + radius * cos(phi.toDouble()).toFloat()
        val z = target.z + radius * sin(theta.toDouble()).toFloat() * sin(phi.toDouble()).toFloat()
        camera.transform.setPosition(x, y, z)
        camera.lookAt(target)
    }
}
