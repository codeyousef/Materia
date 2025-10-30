package io.materia.xr.input

import io.materia.xr.EyeTrackingData
import io.materia.xr.XRHandJoint
import io.materia.xr.XRJointPose

/**
 * Native implementation of XR input functions
 */
// Note: getPlatformHandJointPoses with (inputSource, hand, baseSpace) is in ARPlatform.native.kt (io.materia.xr package)

internal actual suspend fun getPlatformHandJointPoses(
    hand: DefaultXRHand
): Map<XRHandJoint, XRJointPose> = emptyMap()

internal actual suspend fun getPlatformEyeTrackingData(): EyeTrackingData? = null