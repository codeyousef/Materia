package io.materia.audio

import io.materia.camera.Camera
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D

actual class AudioListener actual constructor(camera: Camera?) : Object3D() {
    private val attachedCamera = camera
    private val defaultUp = Vector3(0f, 1f, 0f)

    actual val up: Vector3
        get() = defaultUp

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

        if (children.isNotEmpty()) {
            for (child in children) {
                child.updateMatrixWorld(force)
            }
        }
    }
}
