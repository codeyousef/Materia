/**
 * XR Input and Controller Types
 * Input sources, controllers, gamepads, and hand tracking
 */
package io.materia.xr

import io.materia.core.math.*

/**
 * XR Input Source interface
 */
interface XRInputSource {
    val handedness: XRHandedness
    val targetRayMode: XRTargetRayMode
    val targetRaySpace: XRSpace
    val gripSpace: XRSpace?
    val profiles: List<String>
    val gamepad: XRGamepad?
    val hand: XRHand?
}

/**
 * XR Gamepad interface
 */
interface XRGamepad {
    val connected: Boolean
    val index: Int
    val id: String
    val mapping: String
    val axes: List<Float>
    val buttons: List<XRGamepadButton>
    val hapticActuators: List<XRHapticActuator>
}

/**
 * XR Gamepad Button interface
 */
interface XRGamepadButton {
    val pressed: Boolean
    val touched: Boolean
    val value: Float
}

/**
 * XR Haptic Actuator interface
 */
interface XRHapticActuator {
    fun playHapticEffect(type: String, params: Map<String, Any>)
    fun stopHaptics()
}

/**
 * XR Hand interface for hand tracking
 */
interface XRHand {
    val joints: Map<XRHandJoint, XRJointSpace>
}

/**
 * XR Eye Gaze interface
 */
interface XRGaze {
    val eyeSpace: XRSpace
    val isTracked: Boolean

    fun getEyePose(frame: XRFrame, referenceSpace: XRSpace): XRPose?
    fun getGazeDirection(frame: XRFrame, referenceSpace: XRSpace): Vector3?
}

/**
 * XR Joint Space interface
 */
interface XRJointSpace : XRSpace {
    val joint: XRHandJoint
}

/**
 * XR Controller interface for controller management
 */
interface XRController {
    val controllerId: String
    val handedness: XRHandedness
    val targetRayMode: XRTargetRayMode
    val targetRaySpace: XRSpace
    val gripSpace: XRSpace?
    val gamepad: XRGamepad?
    val hand: XRHand?
    val profiles: List<String>
    val isConnected: Boolean
    val pose: XRPose?

    fun vibrate(intensity: Float, duration: Float): Boolean
    fun getButton(button: XRControllerButton): XRGamepadButton?
    fun getAxis(axis: XRControllerAxis): Float
    fun onButtonDown(button: XRControllerButton, callback: () -> Unit)
    fun onButtonUp(button: XRControllerButton, callback: () -> Unit)
    fun onAxisChange(axis: XRControllerAxis, callback: (Float) -> Unit)
}

/**
 * XR Joint Pose interface
 */
interface XRJointPose : XRPose {
    val radius: Float
}

/**
 * XR Controller Pose data class
 */
data class XRControllerPose(
    val position: Vector3,
    val orientation: io.materia.core.math.Quaternion,
    override val linearVelocity: Vector3?,
    override val angularVelocity: Vector3?,
    val valid: Boolean
) : XRPose {
    override val transform: Matrix4
        get() = Matrix4().compose(position, orientation, Vector3(1f, 1f, 1f))

    override val emulatedPosition: Boolean
        get() = false
}

/**
 * Default implementation of XRJointSpace interface
 */
class DefaultXRJointSpace(
    override val joint: XRHandJoint,
    override val spaceId: String = "joint_space_${joint.name}"
) : XRJointSpace
