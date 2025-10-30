/**
 * Platform-specific AR function declarations
 * Defines expect declarations for AR-specific functionality
 */
package io.materia.xr

import io.materia.core.math.Vector3
import io.materia.core.math.Matrix4
import io.materia.core.math.Color
import io.materia.renderer.Texture

// People occlusion support
internal expect fun isPeopleOcclusionSupported(): Boolean

// Plane detection
internal expect fun enablePlatformPlaneDetection(enabled: Boolean)
internal expect suspend fun getPlatformDetectedPlanes(): List<PlaneData>

// Image tracking
internal expect suspend fun registerImageTargetWithPlatform(target: XRImageTarget): Boolean
internal expect fun unregisterImageTargetFromPlatform(target: XRImageTarget)
internal expect suspend fun trackImageWithPlatform(target: XRImageTarget): ImageTrackingResult?

// Object tracking
internal expect suspend fun registerObjectTargetWithPlatform(target: XRObjectTarget): Boolean
internal expect fun unregisterObjectTargetFromPlatform(target: XRObjectTarget)
internal expect suspend fun trackObjectWithPlatform(target: XRObjectTarget): ObjectTrackingResult?

// Environment probes
internal expect fun enablePlatformEnvironmentProbes(enabled: Boolean)
internal expect suspend fun getPlatformEnvironmentProbes(): List<EnvironmentProbeData>

// Light estimation
internal expect suspend fun getPlatformLightEstimation(): LightEstimationData?

// Occlusion
internal expect fun enablePlatformOcclusion(enabled: Boolean)
internal expect suspend fun getPlatformOcclusionTexture(): Texture?
internal expect fun enablePlatformPeopleOcclusion(enabled: Boolean)

// Hit testing
internal expect suspend fun cancelPlatformHitTestSource(source: DefaultXRHitTestSource)
internal expect suspend fun cancelPlatformTransientHitTestSource(source: DefaultXRTransientInputHitTestSource)
internal expect suspend fun performPlatformHitTest(
    origin: Vector3,
    direction: Vector3,
    hitTestType: XRHitTestType
): List<XRHitTestResult>

internal expect suspend fun performPlatformTransientHitTest(
    inputSource: XRInputSource,
    hitTestType: XRHitTestType
): List<XRTransientInputHitTestResult>

// Environment probe analysis
internal expect fun calculateSphericalHarmonics(probe: XREnvironmentProbe): FloatArray?
internal expect fun estimatePrimaryLightDirection(probe: XREnvironmentProbe): Vector3?
internal expect fun estimatePrimaryLightIntensity(probe: XREnvironmentProbe): Color?

// Coordinate transformations
internal expect fun performCoordinateTransform(
    pose: XRPose,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace,
    transform: Matrix4
): XRPose

// Hand tracking
internal expect suspend fun getPlatformHandJointPoses(
    inputSource: XRInputSource,
    hand: XRHand,
    baseSpace: XRReferenceSpace
): List<XRJointPose>