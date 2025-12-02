package io.materia.engine.camera

import io.materia.engine.math.Mat4
import io.materia.engine.math.Vec3
import io.materia.engine.math.mat4
import io.materia.engine.scene.Node

/**
 * Perspective projection camera for 3D rendering.
 *
 * Simulates how the human eye perceives depth—distant objects appear smaller.
 * The camera inherits position and orientation from [Node] and maintains its
 * own projection and view matrices.
 *
 * Call [updateProjection] after changing [fovDegrees], [aspect], [near], or [far],
 * and [lookAt] to orient the camera toward a target point.
 *
 * @param fovDegrees Vertical field of view in degrees.
 * @param aspect Aspect ratio (width / height).
 * @param near Near clipping plane distance.
 * @param far Far clipping plane distance.
 * @param name Optional identifier for debugging.
 */
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

    /**
     * Recalculates the projection matrix from current FOV, aspect, near, and far values.
     *
     * Uses WebGPU/Vulkan depth range [0, 1].
     */
    fun updateProjection() {
        projectionMatrix.setPerspective(fovDegrees, aspect, near, far)
    }

    /**
     * Orients the camera to look at a target point in world space.
     *
     * Updates the view matrix to face the target from the camera's current position.
     *
     * @param target The world-space point to look at.
     */
    fun lookAt(target: Vec3) {
        val position = transform.position
        viewMatrix.setLookAt(position, target)
    }

    /** Returns the perspective projection matrix. */
    fun projectionMatrix(): Mat4 = projectionMatrix

    /** Returns the view (camera-space) matrix. */
    fun viewMatrix(): Mat4 = viewMatrix
}
