package io.materia.xr.input

import io.materia.xr.EyeTrackingData
import io.materia.xr.XRHandJoint
import io.materia.xr.XRJointPose

internal actual suspend fun getPlatformHandJointPoses(hand: DefaultXRHand): Map<XRHandJoint, XRJointPose> =
    emptyMap()

internal actual suspend fun getPlatformEyeTrackingData(): EyeTrackingData? = null
