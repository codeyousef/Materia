package io.materia.xr

import io.materia.core.math.Matrix4
import io.materia.core.math.Vector2

/**
 * Native implementation of XRDepthInfo for depth sensing
 * Uses the common XRDepthInfo interface defined in XRTypes.kt
 */
class NativeXRDepthInfo(
    override val width: Int,
    override val height: Int,
    override val normDepthBufferFromNormView: Matrix4,
    override val rawValueToMeters: Float
) : XRDepthInfo {
    override fun getDepthInMeters(x: Float, y: Float): Float {
        // Native implementation would require platform-specific depth buffer access
        // via OpenXR or platform-specific AR/VR APIs
        return 0.0f
    }
}

/**
 * Native implementation of XR platform functions
 */

actual fun isPlatformXRSupported(): Boolean = false

actual fun isWebXRSupported(): Boolean = false

actual fun isImmersiveVRSupported(): Boolean = false

actual fun isImmersiveARSupported(): Boolean = false

actual fun getPlatformXRDevices(): List<XRDevice> = emptyList()

actual fun checkCameraPermission(): PermissionState = PermissionState.DENIED

actual fun checkEyeTrackingPermission(): PermissionState = PermissionState.DENIED

actual fun checkHandTrackingPermission(): PermissionState = PermissionState.DENIED

actual suspend fun getPlatformViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose? = null

actual suspend fun getPlatformInputPose(
    inputSource: XRInputSource,
    referenceSpace: XRReferenceSpace
): XRPose? = null

actual suspend fun getPlatformJointPose(joint: XRJointSpace, baseSpace: XRSpace): XRJointPose? =
    null

actual fun detectPlatformInputSources(): List<XRInputSource> = emptyList()

actual fun getBoundsFromPlatform(): List<Vector2> = emptyList()

actual fun calculateRelativePose(space: XRSpace, baseSpace: XRSpace): XRPose? = null

actual fun getPlatformTransientHitTestResults(source: XRTransientInputHitTestSource): List<XRTransientInputHitTestResult> =
    emptyList()

actual fun getPlatformLightEstimate(lightProbe: XRLightProbe): XRLightEstimate? = null

actual fun combineTransforms(first: XRPose, second: XRPose): XRPose = first