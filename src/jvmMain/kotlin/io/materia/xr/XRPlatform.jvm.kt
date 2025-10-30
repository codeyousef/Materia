package io.materia.xr

import io.materia.core.math.Vector2

/**
 * JVM implementation of XR platform functions
 * Note: JVM platform does not support native XR capabilities
 */

actual fun isPlatformXRSupported(): Boolean = false

actual fun isWebXRSupported(): Boolean = false

actual fun isImmersiveVRSupported(): Boolean = false

actual fun isImmersiveARSupported(): Boolean = false

actual fun getPlatformXRDevices(): List<XRDevice> = emptyList()

// Permission checks
actual fun checkCameraPermission(): PermissionState = PermissionState.DENIED

actual fun checkEyeTrackingPermission(): PermissionState = PermissionState.DENIED

actual fun checkHandTrackingPermission(): PermissionState = PermissionState.DENIED

// Platform pose and input functions
actual suspend fun getPlatformViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose? = null

actual suspend fun getPlatformInputPose(
    inputSource: XRInputSource,
    referenceSpace: XRReferenceSpace
): XRPose? = null

actual suspend fun getPlatformJointPose(joint: XRJointSpace, baseSpace: XRSpace): XRJointPose? =
    null

// Platform input detection
actual fun detectPlatformInputSources(): List<XRInputSource> = emptyList()

// Platform bounds and spatial functions
actual fun getBoundsFromPlatform(): List<Vector2> = emptyList()

actual fun calculateRelativePose(space: XRSpace, baseSpace: XRSpace): XRPose? = null

// Platform hit testing
actual fun getPlatformTransientHitTestResults(source: XRTransientInputHitTestSource): List<XRTransientInputHitTestResult> =
    emptyList()

// Platform lighting
actual fun getPlatformLightEstimate(lightProbe: XRLightProbe): XRLightEstimate? = null

// Platform transform utilities
actual fun combineTransforms(first: XRPose, second: XRPose): XRPose {
    // Simple implementation that combines transforms
    val firstTransform = first.transform
    val secondTransform = second.transform
    val combined = firstTransform.multiply(secondTransform)

    return DefaultXRPose(
        position = combined.getTranslation(),
        orientation = io.materia.core.math.Quaternion.IDENTITY // Simplified implementation
    )
}