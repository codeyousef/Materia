/**
 * StereoCamera - Dual camera setup for VR/stereo rendering
 *
 * Manages two cameras positioned to simulate human binocular vision.
 * Used for VR headsets and stereoscopic displays.
 */
package io.materia.camera

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import kotlin.math.tan

/**
 * Camera system for stereoscopic rendering
 *
 * @property aspect Aspect ratio of the viewport
 * @property eyeSep Eye separation (interpupillary distance) in world units
 * @property convergence Focus distance for eye convergence
 * @property cameraL Left eye camera
 * @property cameraR Right eye camera
 */
class StereoCamera {
    var aspect: Float = 1f
    var eyeSep: Float = 0.064f  // Default IPD of 64mm
    var convergence: Float = 10f
    var focalLength: Float = 10f

    val cameraL: PerspectiveCamera = PerspectiveCamera()
    val cameraR: PerspectiveCamera = PerspectiveCamera()

    private val eyeLeft = Matrix4()
    private val eyeRight = Matrix4()

    /**
     * Update stereo cameras based on a main camera
     * @param camera The main camera to base the stereo pair on
     */
    fun update(camera: Camera) {
        if (camera !is PerspectiveCamera) return

        // Get camera properties
        val fov = camera.fov
        val aspect = this.aspect
        val near = camera.near
        val far = camera.far
        val zoom = camera.zoom

        // Cache main camera matrices
        val projectionMatrix = camera.projectionMatrix
        val matrixWorld = camera.matrixWorld
        val matrixWorldInverse = camera.matrixWorldInverse

        // Calculate stereo offsets
        val halfEyeSep = eyeSep / 2f
        val eyeSepOnProjection = halfEyeSep * near / convergence
        val yMax = (near * tan(kotlin.math.PI.toFloat() * fov / 360f)) / zoom
        var xMin: Float
        var xMax: Float

        // Left eye
        xMin = -yMax * aspect + eyeSepOnProjection
        xMax = yMax * aspect + eyeSepOnProjection

        // Create off-axis frustum for left eye
        cameraL.projectionMatrix.makePerspective(
            xMin, xMax, yMax, -yMax, near, far
        )

        // Position left camera
        eyeLeft.elements[12] = -halfEyeSep
        cameraL.matrixWorld.multiplyMatrices(matrixWorld, eyeLeft)
        cameraL.matrixWorldInverse.copy(cameraL.matrixWorld).invert()

        // Right eye
        xMin = -yMax * aspect - eyeSepOnProjection
        xMax = yMax * aspect - eyeSepOnProjection

        // Create off-axis frustum for right eye
        cameraR.projectionMatrix.makePerspective(
            xMin, xMax, yMax, -yMax, near, far
        )

        // Position right camera
        eyeRight.elements[12] = halfEyeSep
        cameraR.matrixWorld.multiplyMatrices(matrixWorld, eyeRight)
        cameraR.matrixWorldInverse.copy(cameraR.matrixWorld).invert()

        // Update camera properties
        cameraL.fov = fov
        cameraL.aspect = aspect
        cameraL.near = near
        cameraL.far = far
        cameraL.zoom = zoom

        cameraR.fov = fov
        cameraR.aspect = aspect
        cameraR.near = near
        cameraR.far = far
        cameraR.zoom = zoom

        // Copy position and rotation from main camera
        cameraL.position.copy(camera.position)
        cameraR.position.copy(camera.position)

        // Apply stereo offset
        val offset = Vector3(halfEyeSep, 0f, 0f)
        cameraL.position.sub(offset.applyMatrix4(camera.matrixWorld))
        cameraR.position.add(offset.applyMatrix4(camera.matrixWorld))

        // Update matrices
        cameraL.updateMatrixWorld()
        cameraR.updateMatrixWorld()
    }

    /**
     * Set interpupillary distance
     * @param distance IPD in world units
     */
    fun setIPD(distance: Float) {
        eyeSep = distance
    }

    /**
     * Get interpupillary distance
     */
    fun getIPD(): Float = eyeSep


    /**
     * Configure for specific VR headset
     */
    fun configureForHeadset(headsetType: VRHeadsetType) {
        when (headsetType) {
            VRHeadsetType.OCULUS_RIFT -> {
                eyeSep = 0.064f
                convergence = 10f
            }

            VRHeadsetType.HTC_VIVE -> {
                eyeSep = 0.063f
                convergence = 10f
            }

            VRHeadsetType.QUEST_2 -> {
                eyeSep = 0.063f
                convergence = 10f
            }

            VRHeadsetType.PSVR -> {
                eyeSep = 0.063f
                convergence = 10f
            }

            VRHeadsetType.CUSTOM -> {
                // Use current settings
            }
        }
    }
}

/**
 * Common VR headset types with typical IPD values
 */
enum class VRHeadsetType {
    OCULUS_RIFT,
    HTC_VIVE,
    QUEST_2,
    PSVR,
    CUSTOM
}

/**
 * Stereo rendering helper utilities
 */
object StereoUtils {
    /**
     * Calculate appropriate convergence distance based on scene
     */
    fun calculateOptimalConvergence(
        nearObjects: Float,
        farObjects: Float
    ): Float {
        // Use geometric mean for balanced convergence
        return kotlin.math.sqrt(nearObjects * farObjects)
    }

    /**
     * Adjust IPD for scale
     * @param realWorldIPD IPD in millimeters
     * @param worldScale Scale of the world (1 unit = X meters)
     */
    fun scaleIPD(realWorldIPD: Float, worldScale: Float): Float {
        return (realWorldIPD / 1000f) * worldScale
    }

    /**
     * Calculate stereo disparity for depth perception
     */
    fun calculateDisparity(
        depth: Float,
        eyeSeparation: Float,
        convergence: Float
    ): Float {
        return eyeSeparation * (1f / convergence - 1f / depth)
    }
}