/**
 * Contract test: StereoCamera for VR
 * Covers: FR-C003, FR-C004 from contracts/camera-api.kt
 *
 * Test Cases:
 * - Maintain two cameras with eye separation
 * - Compute correct view matrices
 * - Adjustable inter-pupillary distance
 *
 * Expected: All tests FAIL (TDD requirement)
 */
package io.materia.camera

import io.materia.core.math.Vector3
import kotlin.test.*

class StereoCameraContractTest {

    /**
     * FR-C003: StereoCamera should maintain two cameras for left and right eyes
     */
    @Test
    fun testStereoCameraHasTwoCameras() {
        // Given: Stereo camera parameters
        val aspect = 1.77f // 16:9
        val eyeSep = 0.064f // 64mm average IPD
        val cameraL = PerspectiveCamera()
        val cameraR = PerspectiveCamera()

        // When: Creating stereo camera
        val stereoCamera = StereoCamera()
        stereoCamera.aspect = aspect
        stereoCamera.eyeSep = eyeSep

        // Then: Should have left and right cameras
        assertNotNull(stereoCamera.cameraL, "StereoCamera should have left camera")
        assertNotNull(stereoCamera.cameraR, "StereoCamera should have right camera")

        // Then: Both should be perspective cameras
        assertTrue(stereoCamera.cameraL is PerspectiveCamera)
        assertTrue(stereoCamera.cameraR is PerspectiveCamera)
    }

    /**
     * FR-C003: StereoCamera should apply eye separation
     */
    @Test
    fun testStereoCameraAppliesEyeSeparation() {
        // Given: Stereo camera with IPD
        val eyeSep = 0.064f
        val stereoCamera = StereoCamera()
        stereoCamera.eyeSep = eyeSep

        // When: Updating stereo camera
        stereoCamera.update(PerspectiveCamera())

        // Then: Left and right cameras should be offset
        val leftPos = stereoCamera.cameraL.position
        val rightPos = stereoCamera.cameraR.position

        assertNotEquals(
            leftPos.x, rightPos.x,
            "Left and right cameras should have different X positions"
        )

        // Then: Separation should match IPD
        val separation = kotlin.math.abs(rightPos.x - leftPos.x)
        assertEquals(
            eyeSep, separation, 0.001f,
            "Camera separation should match IPD"
        )
    }

    /**
     * FR-C004: StereoCamera should compute correct view matrices
     */
    @Test
    fun testStereoCameraComputesViewMatrices() {
        // Given: Stereo camera
        val stereoCamera = StereoCamera()
        val mainCamera = PerspectiveCamera(75f, 1.77f, 0.1f, 1000f)

        // When: Updating with main camera
        stereoCamera.update(mainCamera)

        // Then: View matrices should be different for each eye
        val leftMatrix = stereoCamera.cameraL.matrixWorldInverse
        val rightMatrix = stereoCamera.cameraR.matrixWorldInverse

        assertNotNull(leftMatrix)
        assertNotNull(rightMatrix)
        assertNotEquals(
            leftMatrix, rightMatrix,
            "Left and right view matrices should be different"
        )
    }

    /**
     * FR-C004: StereoCamera should adjust IPD (inter-pupillary distance)
     */
    @Test
    fun testStereoCameraAdjustableIPD() {
        // Given: Stereo camera
        val stereoCamera = StereoCamera()

        // When: Setting different IPD values
        val ipds = listOf(0.055f, 0.064f, 0.072f) // Min, average, max human IPD

        for (ipd in ipds) {
            stereoCamera.eyeSep = ipd
            stereoCamera.update(PerspectiveCamera())

            // Then: Camera separation should match IPD
            val separation = kotlin.math.abs(
                stereoCamera.cameraR.position.x - stereoCamera.cameraL.position.x
            )
            assertEquals(
                ipd, separation, 0.001f,
                "Camera separation should match IPD of $ipd"
            )
        }
    }

    /**
     * StereoCamera should support convergence adjustment
     */
    @Test
    fun testStereoCameraConvergence() {
        // Given: Stereo camera
        val stereoCamera = StereoCamera()
        stereoCamera.convergence = 10f // Focus distance

        // When: Updating cameras
        stereoCamera.update(PerspectiveCamera())

        // Then: Cameras should converge at focus distance
        val leftDir = stereoCamera.cameraL.getWorldDirection(Vector3())
        val rightDir = stereoCamera.cameraR.getWorldDirection(Vector3())

        // Directions should converge (not parallel)
        val dot = leftDir.dot(rightDir)
        assertTrue(dot < 1.0f, "Camera directions should converge")
    }

    /**
     * StereoCamera should inherit main camera properties
     */
    @Test
    fun testStereoCameraInheritsProperties() {
        // Given: Main camera with specific properties
        val mainCamera = PerspectiveCamera(90f, 2.0f, 0.5f, 500f)
        mainCamera.position.set(0f, 10f, 20f)
        mainCamera.lookAt(Vector3(0f, 0f, 0f))

        // Given: Stereo camera
        val stereoCamera = StereoCamera()

        // When: Updating from main camera
        stereoCamera.update(mainCamera)

        // Then: Both eyes should inherit FOV
        assertEquals(
            90f,
            (stereoCamera.cameraL as PerspectiveCamera).fov,
            "Left camera should inherit FOV"
        )
        assertEquals(
            90f,
            (stereoCamera.cameraR as PerspectiveCamera).fov,
            "Right camera should inherit FOV"
        )

        // Then: Both should have similar near/far planes
        assertEquals(0.5f, (stereoCamera.cameraL as PerspectiveCamera).near)
        assertEquals(500f, (stereoCamera.cameraL as PerspectiveCamera).far)
    }

    /**
     * StereoCamera should support asymmetric frustum for correct stereo
     */
    @Test
    fun testStereoCameraAsymmetricFrustum() {
        // Given: Stereo camera
        val stereoCamera = StereoCamera()
        stereoCamera.eyeSep = 0.064f

        // When: Updating
        stereoCamera.update(PerspectiveCamera())

        // Then: Left and right projection matrices should be asymmetric
        val leftProj = stereoCamera.cameraL.projectionMatrix
        val rightProj = stereoCamera.cameraR.projectionMatrix

        // Check that projection matrices are different (asymmetric frustums)
        assertNotEquals(
            leftProj.elements[8], rightProj.elements[8],
            "Left and right should have asymmetric frustums"
        )
    }
}

// Extension class for StereoCamera
class StereoCamera {
    var aspect: Float = 1f
    var eyeSep: Float = 0.064f
    var convergence: Float = 10f
    var cameraL: PerspectiveCamera = PerspectiveCamera()
    var cameraR: PerspectiveCamera = PerspectiveCamera()

    fun update(camera: Camera) {
        // Copy main camera properties to both eyes
        if (camera is PerspectiveCamera) {
            // Copy FOV and near/far planes
            (cameraL as PerspectiveCamera).fov = camera.fov
            (cameraL as PerspectiveCamera).aspect = camera.aspect
            (cameraL as PerspectiveCamera).near = camera.near
            (cameraL as PerspectiveCamera).far = camera.far

            (cameraR as PerspectiveCamera).fov = camera.fov
            (cameraR as PerspectiveCamera).aspect = camera.aspect
            (cameraR as PerspectiveCamera).near = camera.near
            (cameraR as PerspectiveCamera).far = camera.far
        }

        // Ensure main camera has updated its world matrix
        camera.updateMatrixWorld()

        // Copy position and rotation from main camera
        cameraL.position.copy(camera.position)
        cameraL.quaternion.copy(camera.quaternion)

        cameraR.position.copy(camera.position)
        cameraR.quaternion.copy(camera.quaternion)

        // Apply eye separation (left camera moves left, right camera moves right)
        val halfSep = eyeSep * 0.5f

        // Get the right vector from the camera's orientation
        val rightVector = Vector3(1f, 0f, 0f)
        rightVector.applyQuaternion(camera.quaternion)

        // Position left eye (move left)
        val leftOffset = rightVector.clone().multiplyScalar(-halfSep)
        cameraL.position.add(leftOffset)

        // Position right eye (move right)
        val rightOffset = rightVector.clone().multiplyScalar(halfSep)
        cameraR.position.add(rightOffset)

        // Apply convergence if needed (toe-in method)
        if (convergence != 0f && convergence < Float.POSITIVE_INFINITY) {
            // Calculate the convergence angle
            val convergenceAngle = kotlin.math.atan(halfSep / convergence)

            // Rotate left camera slightly right (positive Y rotation)
            val leftRotation = io.materia.core.math.Quaternion()
            leftRotation.setFromAxisAngle(Vector3(0f, 1f, 0f), convergenceAngle)
            cameraL.quaternion.multiply(leftRotation)

            // Rotate right camera slightly left (negative Y rotation)
            val rightRotation = io.materia.core.math.Quaternion()
            rightRotation.setFromAxisAngle(Vector3(0f, 1f, 0f), -convergenceAngle)
            cameraR.quaternion.multiply(rightRotation)
        }

        // Update world matrices
        cameraL.updateMatrixWorld()
        cameraR.updateMatrixWorld()

        // Create asymmetric frustums for proper stereo
        if (camera is PerspectiveCamera && convergence != 0f && convergence < Float.POSITIVE_INFINITY) {
            val fov = camera.fov
            val near = camera.near
            val far = camera.far
            val aspect = camera.aspect

            // Calculate frustum dimensions at near plane
            val top = near * kotlin.math.tan(fov * kotlin.math.PI.toFloat() / 360f)

            // Calculate the horizontal offset for asymmetric frustums (in world units)
            val offset = halfSep * near / convergence

            val leftCam = cameraL as PerspectiveCamera
            val rightCam = cameraR as PerspectiveCamera

            // Manually calculate asymmetric frustum for left camera (offset to the right)
            var left = -aspect * top + offset
            var right = aspect * top + offset
            leftCam.projectionMatrix.makePerspective(left, right, top, -top, near, far)

            // Manually calculate asymmetric frustum for right camera (offset to the left)
            left = -aspect * top - offset
            right = aspect * top - offset
            rightCam.projectionMatrix.makePerspective(left, right, top, -top, near, far)
        } else {
            // No asymmetric frustum, just update normally
            cameraL.updateProjectionMatrix()
            cameraR.updateProjectionMatrix()
        }

        // Compute view matrices (world inverse)
        cameraL.matrixWorldInverse.copy(cameraL.matrixWorld).invert()
        cameraR.matrixWorldInverse.copy(cameraR.matrixWorld).invert()
    }
}