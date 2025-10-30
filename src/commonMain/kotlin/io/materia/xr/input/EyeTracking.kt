package io.materia.xr.input

import io.materia.core.math.Vector3
import io.materia.xr.*

/**
 * Eye tracking implementation and utilities.
 */

/**
 * Default implementation of XRGaze interface
 * Manages eye tracking and gaze direction
 */
class DefaultXRGaze : XRGaze {
    override val eyeSpace: XRSpace = DefaultXRSpace()
    override var isTracked: Boolean = false
        private set

    private var currentPose: XRPose? = null
    private var gazeDirection: Vector3? = null
    private var convergenceDistance: Float = 1f
    private var eyeOpenness = EyeOpenness(left = 1f, right = 1f)
    private var pupilDilation = PupilDilation(left = 0.5f, right = 0.5f)
    private val calibrationData = EyeCalibrationData()

    override fun getEyePose(frame: XRFrame, referenceSpace: XRSpace): XRPose? {
        if (!isTracked) return null
        return frame.getPose(eyeSpace, referenceSpace)
    }

    override fun getGazeDirection(frame: XRFrame, referenceSpace: XRSpace): Vector3? {
        if (!isTracked) return null

        val pose = getEyePose(frame, referenceSpace) ?: return null

        val direction = Vector3(0f, 0f, -1f)
        direction.applyQuaternion(pose.transform.getRotation())

        return direction
    }

    fun updateTracking(tracked: Boolean) {
        isTracked = tracked
    }

    fun updateGaze(pose: XRPose, direction: Vector3) {
        currentPose = pose
        gazeDirection = direction
    }

    fun updateEyeMetrics(
        openness: EyeOpenness,
        dilation: PupilDilation,
        convergence: Float
    ) {
        eyeOpenness = openness
        pupilDilation = dilation
        convergenceDistance = convergence
    }

    fun getConvergencePoint(frame: XRFrame, referenceSpace: XRSpace): Vector3? {
        val direction = getGazeDirection(frame, referenceSpace) ?: return null
        val pose = getEyePose(frame, referenceSpace) ?: return null

        return pose.transform.getTranslation().clone().add(
            direction.multiplyScalar(convergenceDistance)
        )
    }

    fun calibrate(calibrationData: EyeCalibrationData) {
        this.calibrationData.update(calibrationData)
    }

    fun getEyeOpenness(): EyeOpenness = eyeOpenness

    fun getPupilDilation(): PupilDilation = pupilDilation

    fun isBlinking(): Boolean {
        return eyeOpenness.left < 0.2f && eyeOpenness.right < 0.2f
    }

    fun isWinking(): Boolean {
        return (eyeOpenness.left < 0.2f && eyeOpenness.right > 0.8f) ||
                (eyeOpenness.right < 0.2f && eyeOpenness.left > 0.8f)
    }
}

/**
 * Eye openness data
 */
data class EyeOpenness(
    val left: Float,  // 0 = closed, 1 = fully open
    val right: Float
)

/**
 * Pupil dilation data
 */
data class PupilDilation(
    val left: Float,  // Normalized 0-1
    val right: Float
)

/**
 * Eye calibration data
 */
data class EyeCalibrationData(
    var interpupillaryDistance: Float = 0.063f,
    var dominantEye: XREye = XREye.RIGHT,
    var calibrationPoints: List<Vector3> = emptyList()
) {
    fun update(other: EyeCalibrationData) {
        interpupillaryDistance = other.interpupillaryDistance
        dominantEye = other.dominantEye
        calibrationPoints = other.calibrationPoints
    }
}

/**
 * Eye tracking data from platform
 */
data class EyeTrackingData(
    val pose: XRPose,
    val direction: Vector3,
    val openness: EyeOpenness,
    val dilation: PupilDilation,
    val convergence: Float
)
