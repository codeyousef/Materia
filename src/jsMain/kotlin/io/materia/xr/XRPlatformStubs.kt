package io.materia.xr

import io.materia.core.math.Vector2

/**
 * JavaScript platform implementations for XR functions
 */

// Platform capabilities
actual fun isPlatformXRSupported(): Boolean = false
actual fun isWebXRSupported(): Boolean = false
actual fun isImmersiveVRSupported(): Boolean = false
actual fun isImmersiveARSupported(): Boolean = false
actual fun getPlatformXRDevices(): List<XRDevice> = emptyList()

// Permissions
actual fun checkCameraPermission(): PermissionState = PermissionState.DENIED
actual fun checkEyeTrackingPermission(): PermissionState = PermissionState.DENIED
actual fun checkHandTrackingPermission(): PermissionState = PermissionState.DENIED

// Pose tracking
actual suspend fun getPlatformViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose? = null
actual suspend fun getPlatformInputPose(
    inputSource: XRInputSource,
    referenceSpace: XRReferenceSpace
): XRPose? = null

actual suspend fun getPlatformJointPose(joint: XRJointSpace, baseSpace: XRSpace): XRJointPose? =
    null

// Input detection
actual fun detectPlatformInputSources(): List<XRInputSource> = emptyList()

// Spatial
actual fun getBoundsFromPlatform(): List<Vector2> = emptyList()
actual fun calculateRelativePose(space: XRSpace, baseSpace: XRSpace): XRPose? = null

// Hit testing
actual fun getPlatformTransientHitTestResults(source: XRTransientInputHitTestSource): List<XRTransientInputHitTestResult> =
    emptyList()

// Lighting
actual fun getPlatformLightEstimate(lightProbe: XRLightProbe): XRLightEstimate? = null

// Transforms
actual fun combineTransforms(first: XRPose, second: XRPose): XRPose = first