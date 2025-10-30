package io.materia.xr

import io.materia.core.math.Vector3
import io.materia.core.math.Matrix4

/**
 * Native implementation of XR anchor functions
 */

internal actual suspend fun savePersistentAnchor(handle: String, pose: XRPose) {
    // No-op for Native
}

internal actual suspend fun removePersistentAnchor(handle: String) {
    // No-op for Native
}

internal actual suspend fun loadPersistentAnchorsFromPlatform(): List<PersistentAnchorData> =
    emptyList()

internal actual suspend fun getPlatformAnchorPose(anchorId: String): XRPose? = null

internal actual suspend fun uploadAnchorToPlatformCloud(
    anchor: XRAnchor,
    metadata: Map<String, Any>
): String = ""

internal actual suspend fun downloadAnchorFromPlatformCloud(cloudId: String): PersistentAnchorData =
    PersistentAnchorData(
        id = cloudId,
        handle = "",
        pose = DefaultXRPose(Vector3.ZERO, io.materia.core.math.Quaternion.IDENTITY),
        createdTime = 0L,
        metadata = emptyMap()
    )

internal actual suspend fun listAnchorsFromPlatformCloud(filter: Map<String, Any>): List<CloudAnchor> =
    emptyList()

internal actual suspend fun deleteAnchorFromPlatformCloud(cloudId: String) {
    // No-op for Native
}

internal actual suspend fun checkPlatformTrackingState(anchorId: String): XRTrackingState =
    XRTrackingState.NOT_TRACKING

internal actual fun recalculatePoseFromOrigin(
    pose: XRPose,
    originTransform: io.materia.core.math.Matrix4
): XRPose = pose

internal actual fun evaluatePlatformTrackingQuality(): TrackingQuality = TrackingQuality.NONE

internal actual fun getPlatformSpaceTransform(
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): io.materia.core.math.Matrix4 = io.materia.core.math.Matrix4.IDENTITY

internal actual fun performCoordinateTransform(
    position: Vector3,
    fromSpace: XRReferenceSpace,
    toSpace: XRReferenceSpace
): Vector3? = null