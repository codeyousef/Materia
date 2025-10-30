package io.materia.xr.input

import io.materia.xr.EyeTrackingData
import io.materia.xr.XRHandJoint
import io.materia.xr.XRJointPose

/**
 * JavaScript implementation of XR Input internal functions
 */

// Platform input functions for WebXR
internal actual suspend fun getPlatformHandJointPoses(
    hand: DefaultXRHand
): Map<XRHandJoint, XRJointPose> = emptyMap()

internal actual suspend fun getPlatformEyeTrackingData(): EyeTrackingData? = null