/**
 * XR Core Types and Interfaces
 * Core XR system types, sessions, and basic interfaces
 */
package io.materia.xr

import io.materia.core.math.*

/**
 * XR Result sealed class for handling XR operation results
 */
sealed class XRResult<T> {
    data class Success<T>(val value: T) : XRResult<T>()
    data class Error<T>(val exception: XRException) : XRResult<T>()
}

/**
 * XR Exception sealed class for XR-specific errors
 */
sealed class XRException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidParameters(message: String) : XRException(message)
    class InvalidState(message: String) : XRException(message)
    class NotSupported(message: String) : XRException(message)
    class FeatureNotAvailable(feature: XRFeature) : XRException("Feature not available: $feature")
    class SessionNotActive(message: String = "XR session is not active") : XRException(message)
    class DeviceNotFound(message: String = "XR device not found") : XRException(message)
    class PermissionDenied(feature: XRFeature) :
        XRException("Permission denied for feature: $feature")
}

/**
 * Represents an XR device
 */
data class XRDevice(
    val id: String,
    val name: String,
    val capabilities: Set<XRFeature>
)

/**
 * XR System interface for managing XR capabilities
 */
interface XRSystem {
    fun isSupported(): Boolean
    fun getSupportedSessionModes(): List<XRSessionMode>
    fun getSupportedFeatures(mode: XRSessionMode): List<XRFeature>
    fun checkPermissions(features: List<XRFeature>): Map<XRFeature, PermissionState>
    suspend fun requestSession(
        mode: XRSessionMode,
        features: List<XRFeature>
    ): XRResult<XRSession>
}

/**
 * XR Space interface representing a coordinate system
 */
interface XRSpace {
    val spaceId: String
}

/**
 * XR Reference Space interface
 */
interface XRReferenceSpace : XRSpace {
    val type: XRReferenceSpaceType
    fun getOffsetReferenceSpace(originOffset: Matrix4): XRReferenceSpace
}

/**
 * XR Bounded Reference Space interface
 */
interface XRBoundedReferenceSpace : XRReferenceSpace {
    val boundsGeometry: List<Vector2>
}

/**
 * XR Layer interface for rendering layers
 */
interface XRLayer {
    val layerId: String
}

/**
 * XR WebGL Layer interface
 */
interface XRWebGLLayer : XRLayer {
    val antialias: Boolean
    val ignoreDepthValues: Boolean
    val framebufferWidth: Int
    val framebufferHeight: Int
}

/**
 * XR Pose interface representing position and orientation
 */
interface XRPose {
    val transform: Matrix4
    val emulatedPosition: Boolean
    val linearVelocity: Vector3?
    val angularVelocity: Vector3?
}

/**
 * XR Viewer Pose interface
 */
interface XRViewerPose : XRPose {
    val views: List<XRView>
}

/**
 * XR View interface representing a single view (eye)
 */
interface XRView {
    val eye: XREye
    val projectionMatrix: Matrix4
    val viewMatrix: Matrix4
    val recommendedViewportScale: Float?
}

/**
 * Default implementation of XRSystem
 */
open class DefaultXRSystem : XRSystem {
    override fun isSupported(): Boolean = false
    override fun getSupportedSessionModes(): List<XRSessionMode> = emptyList()
    override fun getSupportedFeatures(mode: XRSessionMode): List<XRFeature> = emptyList()
    override fun checkPermissions(features: List<XRFeature>): Map<XRFeature, PermissionState> =
        features.associateWith { PermissionState.DENIED }

    override suspend fun requestSession(
        mode: XRSessionMode,
        features: List<XRFeature>
    ): XRResult<XRSession> =
        XRResult.Error(XRException.NotSupported("XR not supported in this implementation"))
}

/**
 * Default implementation of XRPose interface
 */
data class DefaultXRPose(
    val position: Vector3 = Vector3.ZERO,
    val orientation: Quaternion = Quaternion.IDENTITY,
    override val linearVelocity: Vector3? = null,
    override val angularVelocity: Vector3? = null,
    override val emulatedPosition: Boolean = false
) : XRPose {
    override val transform: Matrix4 = Matrix4().compose(position, orientation, Vector3(1f, 1f, 1f))
}

/**
 * Default implementation of XRSpace interface
 */
class DefaultXRSpace(
    override val spaceId: String = "default_space_${kotlin.random.Random.nextInt()}"
) : XRSpace

/**
 * Utility function to create XRPose from position and orientation
 */
fun createXRPose(
    position: Vector3,
    orientation: Quaternion,
    linearVelocity: Vector3? = null,
    angularVelocity: Vector3? = null,
    emulatedPosition: Boolean = false
): XRPose = DefaultXRPose(position, orientation, linearVelocity, angularVelocity, emulatedPosition)
