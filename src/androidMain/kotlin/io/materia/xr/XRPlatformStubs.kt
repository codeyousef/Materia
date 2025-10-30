package io.materia.xr

import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.renderer.Texture

// Platform support checks
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
actual fun combineTransforms(first: XRPose, second: XRPose): XRPose = first

// Anchor persistence stubs
internal actual suspend fun savePersistentAnchor(handle: String, pose: XRPose) {}
internal actual suspend fun removePersistentAnchor(handle: String) {}
internal actual suspend fun loadPersistentAnchorsFromPlatform(): List<PersistentAnchorData> =
    emptyList()

internal actual suspend fun checkPlatformTrackingState(anchorId: String): XRTrackingState =
    XRTrackingState.NOT_TRACKING

internal actual suspend fun getPlatformAnchorPose(anchorId: String): XRPose? = null
internal actual fun performCoordinateTransform(
    position: Vector3,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): Vector3? = null

internal actual fun recalculatePoseFromOrigin(pose: XRPose, originTransform: Matrix4): XRPose = pose
internal actual fun evaluatePlatformTrackingQuality(): TrackingQuality = TrackingQuality.LIMITED
internal actual suspend fun uploadAnchorToPlatformCloud(
    anchor: XRAnchor,
    metadata: Map<String, Any>
): String = ""

internal actual suspend fun downloadAnchorFromPlatformCloud(cloudId: String): PersistentAnchorData =
    PersistentAnchorData(
        id = cloudId,
        handle = cloudId,
        pose = createXRPose(Vector3.ZERO, Quaternion.IDENTITY),
        createdTime = 0L
    )

internal actual suspend fun listAnchorsFromPlatformCloud(filter: Map<String, Any>): List<CloudAnchor> =
    emptyList()

internal actual suspend fun deleteAnchorFromPlatformCloud(cloudId: String) {}
internal actual fun getPlatformSpaceTransform(
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): Matrix4 = Matrix4().identity()

// AR specific stubs
internal actual fun isPeopleOcclusionSupported(): Boolean = false
internal actual fun enablePlatformPlaneDetection(enabled: Boolean) {}
internal actual suspend fun getPlatformDetectedPlanes(): List<PlaneData> = emptyList()
internal actual suspend fun registerImageTargetWithPlatform(target: XRImageTarget): Boolean = false
internal actual fun unregisterImageTargetFromPlatform(target: XRImageTarget) {}
internal actual suspend fun trackImageWithPlatform(target: XRImageTarget): ImageTrackingResult? =
    null

internal actual suspend fun registerObjectTargetWithPlatform(target: XRObjectTarget): Boolean =
    false

internal actual fun unregisterObjectTargetFromPlatform(target: XRObjectTarget) {}
internal actual suspend fun trackObjectWithPlatform(target: XRObjectTarget): ObjectTrackingResult? =
    null

internal actual fun enablePlatformEnvironmentProbes(enabled: Boolean) {}
internal actual suspend fun getPlatformEnvironmentProbes(): List<EnvironmentProbeData> = emptyList()
internal actual suspend fun getPlatformLightEstimation(): LightEstimationData? = null
internal actual fun enablePlatformOcclusion(enabled: Boolean) {}
internal actual suspend fun getPlatformOcclusionTexture(): Texture? = null
internal actual fun enablePlatformPeopleOcclusion(enabled: Boolean) {}
internal actual suspend fun cancelPlatformHitTestSource(source: DefaultXRHitTestSource) {}
internal actual suspend fun cancelPlatformTransientHitTestSource(source: DefaultXRTransientInputHitTestSource) {}
internal actual suspend fun performPlatformHitTest(
    origin: Vector3,
    direction: Vector3,
    hitTestType: XRHitTestType
): List<XRHitTestResult> = emptyList()

internal actual suspend fun performPlatformTransientHitTest(
    inputSource: XRInputSource,
    hitTestType: XRHitTestType
): List<XRTransientInputHitTestResult> = emptyList()

internal actual fun calculateSphericalHarmonics(probe: XREnvironmentProbe): FloatArray? = null
internal actual fun estimatePrimaryLightDirection(probe: XREnvironmentProbe): Vector3? = null
internal actual fun estimatePrimaryLightIntensity(probe: XREnvironmentProbe): Color? = null
internal actual fun performCoordinateTransform(
    pose: XRPose,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace,
    transform: Matrix4
): XRPose = pose

internal actual suspend fun getPlatformHandJointPoses(
    inputSource: XRInputSource,
    hand: XRHand,
    baseSpace: XRReferenceSpace
): List<XRJointPose> = emptyList()
