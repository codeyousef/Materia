package io.materia.xr.input

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.xr.*
import kotlin.math.PI
import kotlin.math.acos

/**
 * Hand tracking implementation and utilities.
 */

/**
 * Default implementation of XRHand interface
 * Manages hand joint tracking and gesture detection
 */
class DefaultXRHand(
    private val handedness: XRHandedness
) : XRHand {
    private val _joints: MutableMap<XRHandJoint, XRJointSpace> = mutableMapOf()
    override val joints: Map<XRHandJoint, XRJointSpace> get() = _joints
    var size: Float = 0.18f // Average hand size in meters

    private var _isTracked = false
    private val jointPoses = mutableMapOf<XRHandJoint, XRJointPose>()
    private val gestureDetector = HandGestureDetector(this)
    private var calibrationData: HandCalibrationData? = null

    init {
        initializeJoints()
    }

    fun getJoint(joint: XRHandJoint): XRJointSpace? = _joints[joint]

    fun getJointPose(joint: XRHandJoint, frame: XRFrame, referenceSpace: XRSpace): XRJointPose? {
        if (!isTracked()) return null
        val jointSpace = _joints[joint] ?: return null
        return frame.getPose(jointSpace, referenceSpace)?.let { pose ->
            object : XRJointPose {
                override val transform: Matrix4 = pose.transform
                override val emulatedPosition: Boolean = pose.emulatedPosition
                override val linearVelocity: Vector3? = pose.linearVelocity
                override val angularVelocity: Vector3? = pose.angularVelocity
                override val radius: Float = 0.01f
            }
        }
    }

    fun isTracked(): Boolean = _isTracked

    fun updateTracking(tracked: Boolean) {
        _isTracked = tracked
    }

    fun updateJointPose(joint: XRHandJoint, pose: XRJointPose) {
        jointPoses[joint] = pose

        if (joint == XRHandJoint.MIDDLE_FINGER_TIP) {
            jointPoses[XRHandJoint.WRIST]?.let { wristPose ->
                val distance =
                    wristPose.transform.getTranslation().distanceTo(pose.transform.getTranslation())
                size = distance * 1.1f
            }
        }
    }

    fun getJointPose(joint: XRHandJoint): XRJointPose? = jointPoses[joint]

    fun getAllJointPoses(): Map<XRHandJoint, XRJointPose> = jointPoses.toMap()

    fun detectGestures(): List<HandGesture> {
        return if (isTracked()) gestureDetector.detectCurrentGestures() else emptyList()
    }

    fun calibrate(calibrationData: HandCalibrationData) {
        this.calibrationData = calibrationData
        size = calibrationData.handSize
    }

    private fun initializeJoints() {
        XRHandJoint.values().forEach { joint ->
            _joints[joint] = DefaultXRJointSpace(joint)
        }
    }

    fun getFingerCurl(finger: Finger): Float {
        val joints = getFingerJoints(finger)
        if (joints.size < 4) return 0f

        val jointsList = _joints.keys.toList()
        if (jointsList.size < 4) return 0f

        val metacarpal = jointPoses[jointsList[0]]?.transform?.getTranslation() ?: return 0f
        val proximal = jointPoses[jointsList[1]]?.transform?.getTranslation() ?: return 0f
        val intermediate = jointPoses[jointsList[2]]?.transform?.getTranslation() ?: return 0f
        val distal = jointPoses[jointsList[3]]?.transform?.getTranslation() ?: return 0f

        val angle1 = calculateJointAngle(metacarpal, proximal, intermediate)
        val angle2 = calculateJointAngle(proximal, intermediate, distal)

        return ((angle1 + angle2) / 270f).coerceIn(0f, 1f)
    }

    fun getPinchStrength(finger: Finger = Finger.INDEX): Float {
        val thumbTip = jointPoses[XRHandJoint.THUMB_TIP]?.transform?.getTranslation() ?: return 0f
        val fingerTip =
            jointPoses[getFingerTipJoint(finger)]?.transform?.getTranslation() ?: return 0f

        val distance = thumbTip.distanceTo(fingerTip)
        val normalizedDistance = (distance / size).coerceIn(0f, 1f)

        return 1f - normalizedDistance
    }

    private fun getFingerJoints(finger: Finger): List<XRHandJoint> {
        return when (finger) {
            Finger.THUMB -> listOf(
                XRHandJoint.THUMB_METACARPAL,
                XRHandJoint.THUMB_PHALANX_PROXIMAL,
                XRHandJoint.THUMB_PHALANX_DISTAL,
                XRHandJoint.THUMB_TIP
            )

            Finger.INDEX -> listOf(
                XRHandJoint.INDEX_FINGER_METACARPAL,
                XRHandJoint.INDEX_FINGER_PHALANX_PROXIMAL,
                XRHandJoint.INDEX_FINGER_PHALANX_INTERMEDIATE,
                XRHandJoint.INDEX_FINGER_PHALANX_DISTAL
            )

            Finger.MIDDLE -> listOf(
                XRHandJoint.MIDDLE_FINGER_METACARPAL,
                XRHandJoint.MIDDLE_FINGER_PHALANX_PROXIMAL,
                XRHandJoint.MIDDLE_FINGER_PHALANX_INTERMEDIATE,
                XRHandJoint.MIDDLE_FINGER_PHALANX_DISTAL
            )

            Finger.RING -> listOf(
                XRHandJoint.RING_FINGER_METACARPAL,
                XRHandJoint.RING_FINGER_PHALANX_PROXIMAL,
                XRHandJoint.RING_FINGER_PHALANX_INTERMEDIATE,
                XRHandJoint.RING_FINGER_PHALANX_DISTAL
            )

            Finger.PINKY -> listOf(
                XRHandJoint.PINKY_FINGER_METACARPAL,
                XRHandJoint.PINKY_FINGER_PHALANX_PROXIMAL,
                XRHandJoint.PINKY_FINGER_PHALANX_INTERMEDIATE,
                XRHandJoint.PINKY_FINGER_PHALANX_DISTAL
            )
        }
    }

    private fun getFingerTipJoint(finger: Finger): XRHandJoint {
        return when (finger) {
            Finger.THUMB -> XRHandJoint.THUMB_TIP
            Finger.INDEX -> XRHandJoint.INDEX_FINGER_TIP
            Finger.MIDDLE -> XRHandJoint.MIDDLE_FINGER_TIP
            Finger.RING -> XRHandJoint.RING_FINGER_TIP
            Finger.PINKY -> XRHandJoint.PINKY_FINGER_TIP
        }
    }

    private fun calculateJointAngle(p1: Vector3, p2: Vector3, p3: Vector3): Float {
        val v1 = p1.clone().sub(p2)
        val v1Length = v1.length()
        if (v1Length > 0.001f) {
            v1.normalize()
        } else {
            // Default to up vector if degenerate
            v1.set(0f, 1f, 0f)
        }

        val v2 = p3.clone().sub(p2)
        val v2Length = v2.length()
        if (v2Length > 0.001f) {
            v2.normalize()
        } else {
            // Default to forward vector if degenerate
            v2.set(0f, 0f, 1f)
        }

        return acos(v1.dot(v2).coerceIn(-1f, 1f)) * 180f / PI.toFloat()
    }
}

/**
 * Finger enumeration
 */
enum class Finger {
    THUMB,
    INDEX,
    MIDDLE,
    RING,
    PINKY
}

/**
 * Hand calibration data
 */
data class HandCalibrationData(
    val handSize: Float = 0.18f,
    val fingerLengths: Map<Finger, Float> = emptyMap(),
    val palmWidth: Float = 0.08f
)
