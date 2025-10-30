package io.materia.audio

import io.materia.camera.Camera
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D

actual class AudioListener actual constructor(camera: Camera?) : Object3D() {
    private val _camera = camera
    private val _up = Vector3(0f, 1f, 0f)

    actual val up: Vector3
        get() = _up

    actual override fun updateMatrixWorld(force: Boolean) {
        // If attached to camera, sync transform
        _camera?.let {
            // Copy position from camera
            position.copy(it.position)
            // Sync quaternion from camera's rotation (in case rotation was set directly)
            quaternion.setFromEuler(it.rotation)
        }
        super.updateMatrixWorld(force)
    }
}
