package io.kreekt.engine.camera

import io.kreekt.engine.math.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class OrbitController(
    private val camera: PerspectiveCamera,
    private val target: Vector3f = Vector3f.Zero.copy(),
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

    fun rotate(deltaTheta: Float, deltaPhi: Float) {
        theta += deltaTheta
        phi = (phi + deltaPhi).coerceIn(0.01f, 3.04f)
        updateCameraPosition()
    }

    fun zoom(delta: Float) {
        radius = (radius + delta).coerceIn(0.5f, 50f)
        updateCameraPosition()
    }

    fun pan(deltaX: Float, deltaY: Float) {
        target.x += deltaX
        target.y += deltaY
        updateCameraPosition()
    }

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
