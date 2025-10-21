package io.kreekt.engine.camera

import io.kreekt.engine.math.MatrixOps
import io.kreekt.engine.math.Vector3f
import io.kreekt.engine.scene.Node
import kotlin.math.tan

class PerspectiveCamera(
    var fovDegrees: Float = 60f,
    var aspect: Float = 16f / 9f,
    var near: Float = 0.1f,
    var far: Float = 100f,
    name: String = "Camera"
) : Node(name) {

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = MatrixOps.identity()

    init {
        updateProjection()
    }

    fun updateProjection() {
        val f = 1f / tan(Math.toRadians((fovDegrees / 2f).toDouble())).toFloat()
        projectionMatrix[0] = f / aspect
        projectionMatrix[1] = 0f
        projectionMatrix[2] = 0f
        projectionMatrix[3] = 0f

        projectionMatrix[4] = 0f
        projectionMatrix[5] = f
        projectionMatrix[6] = 0f
        projectionMatrix[7] = 0f

        projectionMatrix[8] = 0f
        projectionMatrix[9] = 0f
        projectionMatrix[10] = (far + near) / (near - far)
        projectionMatrix[11] = -1f

        projectionMatrix[12] = 0f
        projectionMatrix[13] = 0f
        projectionMatrix[14] = (2f * far * near) / (near - far)
        projectionMatrix[15] = 0f
    }

    fun lookAt(target: Vector3f) {
        val pos = transform.position
        val forward = Vector3f(target.x - pos.x, target.y - pos.y, target.z - pos.z).normalize()
        val up = Vector3f.Up
        val right = Vector3f(
            up.y * forward.z - up.z * forward.y,
            up.z * forward.x - up.x * forward.z,
            up.x * forward.y - up.y * forward.x
        ).normalize()

        val realUp = Vector3f(
            forward.y * right.z - forward.z * right.y,
            forward.z * right.x - forward.x * right.z,
            forward.x * right.y - forward.y * right.x
        )

        viewMatrix[0] = right.x
        viewMatrix[4] = right.y
        viewMatrix[8] = right.z
        viewMatrix[12] = -(right.x * pos.x + right.y * pos.y + right.z * pos.z)

        viewMatrix[1] = realUp.x
        viewMatrix[5] = realUp.y
        viewMatrix[9] = realUp.z
        viewMatrix[13] = -(realUp.x * pos.x + realUp.y * pos.y + realUp.z * pos.z)

        viewMatrix[2] = -forward.x
        viewMatrix[6] = -forward.y
        viewMatrix[10] = -forward.z
        viewMatrix[14] = forward.x * pos.x + forward.y * pos.y + forward.z * pos.z

        viewMatrix[3] = 0f
        viewMatrix[7] = 0f
        viewMatrix[11] = 0f
        viewMatrix[15] = 1f
    }

    fun projectionMatrix(): FloatArray = projectionMatrix
    fun viewMatrix(): FloatArray = viewMatrix
}
