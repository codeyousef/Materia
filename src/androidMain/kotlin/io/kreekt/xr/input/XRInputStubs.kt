package io.kreekt.xr.input

import io.kreekt.xr.EyeTrackingData
import io.kreekt.xr.XRHandJoint
import io.kreekt.xr.XRJointPose

internal actual suspend fun getPlatformHandJointPoses(hand: DefaultXRHand): Map<XRHandJoint, XRJointPose> = emptyMap()

internal actual suspend fun getPlatformEyeTrackingData(): EyeTrackingData? = null
