package io.kreekt.examples.common

import io.kreekt.camera.Camera
import io.kreekt.core.math.Vector3
import io.kreekt.curve.CatmullRomCurve3

class CameraRails(
    controlPoints: List<Vector3>
) {
    private val curve = CatmullRomCurve3(controlPoints, closed = true)

    fun positionAt(t: Float, target: Vector3 = Vector3()): Vector3 {
        val wrapped = (t % 1f + 1f) % 1f
        curve.getPoint(wrapped, target)
        return target
    }

    fun attach(camera: Camera, t: Float, lookAt: Vector3) {
        val position = positionAt(t)
        camera.position.copy(position)
        camera.lookAt(lookAt)
        camera.updateMatrixWorld(true)
    }
}
