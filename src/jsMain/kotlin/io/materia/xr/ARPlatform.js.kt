package io.materia.xr

import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3
import io.materia.renderer.Texture

/**
 * JS implementation of AR platform functions
 * Uses WebXR AR capabilities where available
 */

// People occlusion support
internal actual fun isPeopleOcclusionSupported(): Boolean = false

// Plane detection
internal actual fun enablePlatformPlaneDetection(enabled: Boolean) {
    // No-op for JS until WebXR plane detection is implemented
}

internal actual suspend fun getPlatformDetectedPlanes(): List<PlaneData> = emptyList()

// Image tracking
internal actual suspend fun registerImageTargetWithPlatform(target: XRImageTarget): Boolean = false

internal actual fun unregisterImageTargetFromPlatform(target: XRImageTarget) {
    // No-op for JS
}

internal actual suspend fun trackImageWithPlatform(target: XRImageTarget): ImageTrackingResult? =
    null

// Object tracking
internal actual suspend fun registerObjectTargetWithPlatform(target: XRObjectTarget): Boolean =
    false

internal actual fun unregisterObjectTargetFromPlatform(target: XRObjectTarget) {
    // No-op for JS
}

internal actual suspend fun trackObjectWithPlatform(target: XRObjectTarget): ObjectTrackingResult? =
    null

// Environment probes
internal actual fun enablePlatformEnvironmentProbes(enabled: Boolean) {
    // No-op for JS
}

internal actual suspend fun getPlatformEnvironmentProbes(): List<EnvironmentProbeData> = emptyList()

// Light estimation
internal actual suspend fun getPlatformLightEstimation(): LightEstimationData? = null

// Occlusion
internal actual fun enablePlatformOcclusion(enabled: Boolean) {
    // No-op for JS
}

internal actual suspend fun getPlatformOcclusionTexture(): Texture? = null

internal actual fun enablePlatformPeopleOcclusion(enabled: Boolean) {
    // No-op for JS
}

// Hit testing
internal actual suspend fun cancelPlatformHitTestSource(source: DefaultXRHitTestSource) {
    // No-op for JS
}

internal actual suspend fun cancelPlatformTransientHitTestSource(source: DefaultXRTransientInputHitTestSource) {
    // No-op for JS
}

internal actual suspend fun performPlatformHitTest(
    origin: Vector3,
    direction: Vector3,
    hitTestType: XRHitTestType
): List<XRHitTestResult> = emptyList()

internal actual suspend fun performPlatformTransientHitTest(
    inputSource: XRInputSource,
    hitTestType: XRHitTestType
): List<XRTransientInputHitTestResult> = emptyList()

// Environment probe analysis
internal actual fun calculateSphericalHarmonics(probe: XREnvironmentProbe): FloatArray? = null

internal actual fun estimatePrimaryLightDirection(probe: XREnvironmentProbe): Vector3? = null

internal actual fun estimatePrimaryLightIntensity(probe: XREnvironmentProbe): Color? = null

// Coordinate transformations (ARPlatform version)
internal actual fun performCoordinateTransform(
    pose: XRPose,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace,
    transform: Matrix4
): XRPose = pose

// Hand tracking (ARPlatform version)
internal actual suspend fun getPlatformHandJointPoses(
    inputSource: XRInputSource,
    hand: XRHand,
    baseSpace: XRReferenceSpace
): List<XRJointPose> = emptyList()