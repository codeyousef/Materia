/**
 * CubeCamera - Renders scene to a cube texture
 *
 * Creates a cube texture by rendering the scene from a single point
 * in six directions (±X, ±Y, ±Z). Used for dynamic environment mapping
 * and reflections.
 */
package io.materia.camera

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.renderer.CubeRenderTarget
import io.materia.texture.CubeTexture

/**
 * Camera that renders to 6 faces of a cube texture.
 * 
 * This camera manages 6 internal perspective cameras oriented to capture
 * the scene in all directions. Use with a rendering system that supports
 * cube render targets.
 *
 * @property near Near clipping plane
 * @property far Far clipping plane
 * @property renderTarget The cube render target
 */
class CubeCamera(
    nearClip: Float = 0.1f,
    farClip: Float = 1000f,
    cubeResolution: Int = 256
) : Camera() {

    /**
     * The cube render target where the scene is rendered
     */
    val renderTarget: CubeRenderTarget = CubeRenderTarget(
        cubeResolution, cubeResolution
    )

    /**
     * Internal cameras for each cube face
     */
    private val cameraPX = PerspectiveCamera(90f, 1f, nearClip, farClip)
    private val cameraNX = PerspectiveCamera(90f, 1f, nearClip, farClip)
    private val cameraPY = PerspectiveCamera(90f, 1f, nearClip, farClip)
    private val cameraNY = PerspectiveCamera(90f, 1f, nearClip, farClip)
    private val cameraPZ = PerspectiveCamera(90f, 1f, nearClip, farClip)
    private val cameraNZ = PerspectiveCamera(90f, 1f, nearClip, farClip)

    private val cameras = listOf(cameraPX, cameraNX, cameraPY, cameraNY, cameraPZ, cameraNZ)

    init {
        near = nearClip
        far = farClip
        setupCameraOrientations()
        updateCameraPositions()
    }

    /**
     * Setup the orientation for each camera to look at the correct cube face
     */
    private fun setupCameraOrientations() {
        cameraPX.rotation.set(0f, kotlin.math.PI.toFloat() / 2f, 0f)
        cameraNX.rotation.set(0f, -kotlin.math.PI.toFloat() / 2f, 0f)
        cameraPY.rotation.set(-kotlin.math.PI.toFloat() / 2f, 0f, 0f)
        cameraNY.rotation.set(kotlin.math.PI.toFloat() / 2f, 0f, 0f)
        cameraPZ.rotation.set(0f, 0f, 0f)
        cameraNZ.rotation.set(0f, kotlin.math.PI.toFloat(), 0f)
    }

    /**
     * Update all camera positions to match the CubeCamera position
     */
    private fun updateCameraPositions() {
        cameras.forEach { camera ->
            camera.position.copy(this.position)
        }
    }

    /**
     * Get the camera for a specific cube face.
     * @param faceIndex The face index (0-5: +X, -X, +Y, -Y, +Z, -Z)
     * @return The perspective camera for that face
     */
    fun getCameraForFace(faceIndex: Int): PerspectiveCamera {
        require(faceIndex in 0..5) { "Face index must be 0-5, got $faceIndex" }
        updateCameraPositions()
        cameras[faceIndex].position.copy(this.position)
        cameras[faceIndex].updateMatrixWorld()
        return cameras[faceIndex]
    }

    /**
     * Get all 6 face cameras.
     */
    fun getAllCameras(): List<PerspectiveCamera> {
        updateCameraPositions()
        cameras.forEach { it.updateMatrixWorld() }
        return cameras
    }

    /**
     * Get the environment texture for use in materials
     */
    fun getTexture(): CubeTexture {
        return renderTarget.cubeTexture
    }

    /**
     * Dispose of resources
     */
    fun dispose() {
        renderTarget.dispose()
    }

    override fun updateProjectionMatrix() {
        cameras.forEach { it.updateProjectionMatrix() }
    }

    override fun setViewOffset(
        fullWidth: Int,
        fullHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        cameras.forEach {
            it.setViewOffset(fullWidth, fullHeight, x, y, width, height)
        }
    }

    override fun clearViewOffset() {
        cameras.forEach { it.clearViewOffset() }
    }

    companion object {
        /**
         * Create view matrices for each cube face.
         * These are the standard cube map view matrices.
         */
        fun getCubeViewMatrices(): List<Matrix4> {
            return listOf(
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, -1f, 0f)),
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(-1f, 0f, 0f), Vector3(0f, -1f, 0f)),
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f)),
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(0f, -1f, 0f), Vector3(0f, 0f, -1f)),
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(0f, 0f, 1f), Vector3(0f, -1f, 0f)),
                Matrix4().lookAt(Vector3(0f, 0f, 0f), Vector3(0f, 0f, -1f), Vector3(0f, -1f, 0f))
            )
        }

        /** Face names for debugging */
        val FACE_NAMES = listOf("+X", "-X", "+Y", "-Y", "+Z", "-Z")
    }
}