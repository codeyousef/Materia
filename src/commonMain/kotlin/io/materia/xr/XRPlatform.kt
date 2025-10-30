package io.materia.xr

import io.materia.core.math.Vector2

/**
 * Platform-specific XR function declarations
 */

// Platform support checks
expect fun isPlatformXRSupported(): Boolean
expect fun isWebXRSupported(): Boolean
expect fun isImmersiveVRSupported(): Boolean
expect fun isImmersiveARSupported(): Boolean
expect fun getPlatformXRDevices(): List<XRDevice>

// Permission checks
expect fun checkCameraPermission(): PermissionState
expect fun checkEyeTrackingPermission(): PermissionState
expect fun checkHandTrackingPermission(): PermissionState

// Platform pose and input functions
expect suspend fun getPlatformViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose?
expect suspend fun getPlatformInputPose(
    inputSource: XRInputSource,
    referenceSpace: XRReferenceSpace
): XRPose?

expect suspend fun getPlatformJointPose(joint: XRJointSpace, baseSpace: XRSpace): XRJointPose?

// Platform input detection
expect fun detectPlatformInputSources(): List<XRInputSource>

// Platform bounds and spatial functions
expect fun getBoundsFromPlatform(): List<Vector2>
expect fun calculateRelativePose(space: XRSpace, baseSpace: XRSpace): XRPose?

// Platform hit testing
expect fun getPlatformTransientHitTestResults(source: XRTransientInputHitTestSource): List<XRTransientInputHitTestResult>

// Platform lighting
expect fun getPlatformLightEstimate(lightProbe: XRLightProbe): XRLightEstimate?

// Platform transform utilities
expect fun combineTransforms(first: XRPose, second: XRPose): XRPose