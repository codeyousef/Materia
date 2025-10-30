package io.materia.engine.camera

import io.materia.engine.math.Mat4
import io.materia.engine.math.Vec3
import io.materia.engine.math.mat4
import io.materia.engine.scene.Node

class PerspectiveCamera(
    var fovDegrees: Float = 60f,
    var aspect: Float = 16f / 9f,
    var near: Float = 0.1f,
    var far: Float = 100f,
    name: String = "Camera"
) : Node(name) {

    private val projectionMatrix: Mat4 = mat4()
    private val viewMatrix: Mat4 = mat4().setIdentity()

    init {
        updateProjection()
    }

    fun updateProjection() {
        projectionMatrix.setPerspective(fovDegrees, aspect, near, far)
    }

    fun lookAt(target: Vec3) {
        val position = transform.position
        viewMatrix.setLookAt(position, target)
    }

    fun projectionMatrix(): Mat4 = projectionMatrix
    fun viewMatrix(): Mat4 = viewMatrix
}
