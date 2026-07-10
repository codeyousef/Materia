package io.materia.audio

import io.materia.camera.Camera
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D

actual class AudioListener actual constructor(camera: Camera?) : Object3D() {
    private val attachedCamera = camera

    actual override fun updateMatrixWorld(force: Boolean) {
        val camera = attachedCamera
        if (camera == null) {
            super.updateMatrixWorld(force)
            return
        }

        camera.quaternion.setFromEuler(camera.rotation)
        camera.updateMatrix()
        camera.updateMatrixWorld(force)
        super.updateMatrixWorld(force)

        position.copy(camera.position)
        rotation.copy(camera.rotation)
        quaternion.copy(camera.quaternion)
        scale.copy(camera.scale)

        matrix.copy(camera.matrix)
        matrixWorld.copy(camera.matrixWorld)
        matrixWorldNeedsUpdate = false

        updateNativeListener(camera)

        if (children.isNotEmpty()) {
            for (child in children) {
                child.updateMatrixWorld(force)
            }
        }
    }

    private fun updateNativeListener(camera: Camera) {
        val context = BrowserAudioEngine.contextOrNull() ?: return
        val listener = context.listener ?: return
        val now = (context.currentTime as Number).toDouble()
        val forward = Vector3(0f, 0f, -1f).applyQuaternion(camera.getWorldQuaternion(Quaternion()))
        val up = camera.up.clone().applyQuaternion(camera.getWorldQuaternion(Quaternion()))

        if (listener.positionX != undefined) {
            listener.positionX.setValueAtTime(camera.position.x, now)
            listener.positionY.setValueAtTime(camera.position.y, now)
            listener.positionZ.setValueAtTime(camera.position.z, now)
            listener.forwardX.setValueAtTime(forward.x, now)
            listener.forwardY.setValueAtTime(forward.y, now)
            listener.forwardZ.setValueAtTime(forward.z, now)
            listener.upX.setValueAtTime(up.x, now)
            listener.upY.setValueAtTime(up.y, now)
            listener.upZ.setValueAtTime(up.z, now)
        } else {
            listener.setPosition(camera.position.x, camera.position.y, camera.position.z)
            listener.setOrientation(forward.x, forward.y, forward.z, up.x, up.y, up.z)
        }
    }
}
