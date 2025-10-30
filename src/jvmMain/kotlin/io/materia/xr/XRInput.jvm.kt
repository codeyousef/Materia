/**
 * JVM implementations for XR input platform functions
 * These are placeholder implementations since XR is not available on JVM
 */
package io.materia.xr.input

import io.materia.xr.EyeTrackingData
import io.materia.xr.XRHandJoint
import io.materia.xr.XRJointPose

// JVM implementation for XR input functions (OpenXR simulation)
// Note: getPlatformHandJointPoses with (inputSource, hand, baseSpace) is in ARPlatform.jvm.kt (io.materia.xr package)

internal actual suspend fun getPlatformHandJointPoses(
    hand: DefaultXRHand
): Map<XRHandJoint, XRJointPose> = emptyMap()

internal actual suspend fun getPlatformEyeTrackingData(): EyeTrackingData? = null