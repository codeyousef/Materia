/**
 * XR Augmented Reality Types
 * AR-specific features including hit testing, anchors, planes, and environment tracking
 */
package io.materia.xr

import io.materia.core.math.*

/**
 * AR System interface extending XR System
 */
interface ARSystem : XRSystem {
    suspend fun requestHitTestSource(options: XRHitTestOptions): XRResult<XRHitTestSource>
    suspend fun requestHitTestSourceForTransientInput(
        options: XRTransientInputHitTestOptions
    ): XRResult<XRTransientInputHitTestSource>

    fun detectPlanes(): List<XRPlane>
    fun detectMeshes(): List<XRMesh>
    fun detectFaces(): List<XRFace>

    suspend fun createAnchor(pose: XRPose, space: XRSpace): XRResult<XRAnchor>
    fun loadAnchor(id: String): XRAnchor?
    fun getAllAnchors(): List<XRAnchor>

    fun trackImage(imageTarget: XRImageTarget): XRResult<XRTrackedImage>
    fun trackObject(objectTarget: XRObjectTarget): XRResult<XRTrackedObject>

    fun requestLightProbe(): XRLightProbe?
    fun requestEnvironmentProbe(position: Vector3): XREnvironmentProbe?

    suspend fun enableCameraAccess(): XRResult<XRCamera>
    suspend fun enableDepthSensing(options: XRDepthSensingOptions): XRResult<XRDepthSensor>
}

/**
 * XR Hit Test Options
 */
data class XRHitTestOptions(
    val space: XRSpace,
    val entityTypes: Set<XRHitTestEntityType> = setOf(XRHitTestEntityType.PLANE),
    val offsetRay: XRRay? = null
)

/**
 * XR Transient Input Hit Test Options
 */
data class XRTransientInputHitTestOptions(
    val profile: String,
    val entityTypes: Set<XRHitTestEntityType> = setOf(XRHitTestEntityType.PLANE),
    val offsetRay: XRRay? = null
)

/**
 * XR Ray for hit testing
 */
data class XRRay(
    val origin: Vector3,
    val direction: Vector3,
    val matrix: Matrix4 = Matrix4()
)

/**
 * XR Hit Test Source interface
 */
interface XRHitTestSource {
    fun cancel()
}

/**
 * XR Transient Input Hit Test Source interface
 */
interface XRTransientInputHitTestSource {
    fun cancel()
}

/**
 * XR Hit Test Result interface
 */
interface XRHitTestResult {
    fun getPose(baseSpace: XRSpace): XRPose?
    suspend fun createAnchor(): XRAnchor?
}

/**
 * XR Transient Input Hit Test Result interface
 */
interface XRTransientInputHitTestResult {
    val inputSource: XRInputSource
    val results: List<XRHitTestResult>
}

/**
 * XR Anchor interface
 */
interface XRAnchor {
    val anchorId: String
    val anchorSpace: XRSpace
    fun delete()
}

/**
 * XR Plane interface for plane detection
 */
interface XRPlane {
    val planeSpace: XRSpace
    val polygon: List<Vector3>
    val orientation: PlaneOrientation
    val semanticLabel: String?
}

/**
 * XR Mesh interface for mesh detection
 */
interface XRMesh {
    val meshSpace: XRSpace
    val vertices: List<Vector3>
    val indices: List<Int>
    val semanticLabel: String?
}

/**
 * XR Face interface for face tracking
 */
interface XRFace {
    val faceSpace: XRSpace
    val landmarks: Map<String, Vector3>
    val mesh: XRMesh?
}

/**
 * XR Light Estimate interface
 */
interface XRLightEstimate {
    val sphericalHarmonicsCoefficients: List<Vector3>
    val primaryLightDirection: Vector3
    val primaryLightIntensity: Float
}

/**
 * XR Environment Probe interface
 */
interface XREnvironmentProbe {
    val position: Vector3
    val size: Vector3
    val environmentMap: Any? // Platform-specific texture
}

/**
 * XR Image Target for image tracking
 */
interface XRImageTarget {
    val image: Any // Platform-specific image data
    val widthInMeters: Float
}

/**
 * XR Tracked Image interface
 */
interface XRTrackedImage {
    val image: XRImageTarget
    val trackingState: XRTrackingState
    val measuredWidthInMeters: Float
    val imageSpace: XRSpace
    val emulatedPosition: Boolean
}

/**
 * XR Object Target for object tracking
 */
interface XRObjectTarget {
    val targetObject: Any // Platform-specific object data
}

/**
 * XR Tracked Object interface
 */
interface XRTrackedObject {
    val target: XRObjectTarget
    val trackingState: XRTrackingState
    val objectSpace: XRSpace
}

/**
 * XR Camera interface for camera access
 */
interface XRCamera {
    val cameraImage: Any? // Platform-specific image
    val intrinsics: CameraIntrinsics?
}

/**
 * Camera Intrinsics
 */
data class CameraIntrinsics(
    val focalLength: Vector2,
    val principalPoint: Vector2,
    val imageSize: Vector2
)

/**
 * XR Depth Sensor interface
 */
interface XRDepthSensor {
    fun getDepthInformation(view: XRView): XRDepthInfo?
}

/**
 * XR Depth Information interface
 */
interface XRDepthInfo {
    val width: Int
    val height: Int
    val normDepthBufferFromNormView: Matrix4
    val rawValueToMeters: Float
    fun getDepthInMeters(x: Float, y: Float): Float
}

/**
 * XR Depth Sensing Options
 */
data class XRDepthSensingOptions(
    val usagePreference: Set<XRDepthUsage>,
    val dataFormatPreference: Set<XRDepthDataFormat>
)

/**
 * Platform-specific data classes for AR functionality
 */
data class PlaneData(
    val id: String,
    val centerPose: Matrix4,
    val extentX: Float,
    val extentZ: Float,
    val polygonVertices: List<Vector3>,
    val orientation: PlaneOrientation,
    val trackingState: XRTrackingState,
    val semanticLabel: String? = null
)

data class ImageTrackingResult(
    val target: XRImageTarget,
    val pose: Matrix4,
    val trackingState: XRTrackingState,
    val measuredWidth: Float,
    val confidence: Float
)

data class ObjectTrackingResult(
    val target: XRObjectTarget,
    val pose: Matrix4,
    val trackingState: XRTrackingState,
    val confidence: Float
)

data class EnvironmentProbeData(
    val position: Vector3,
    val size: Vector3,
    val environmentTexture: Any?,
    val irradianceCoefficients: FloatArray,
    val intensity: Float
)

data class LightEstimationData(
    val primaryLightDirection: Vector3,
    val primaryLightIntensity: Float,
    val ambientLightColor: Color,
    val ambientLightIntensity: Float,
    val sphericalHarmonicsCoefficients: FloatArray
)

/**
 * Default implementation of XRHitTestSource interface
 */
class DefaultXRHitTestSource(
    val space: XRSpace,
    val entityTypes: Set<String>
) : XRHitTestSource {
    override fun cancel() {
        // Default implementation - no-op
    }
}

/**
 * Default implementation of XRTransientInputHitTestSource interface
 */
class DefaultXRTransientInputHitTestSource(
    val profile: String,
    val entityTypes: Set<String>
) : XRTransientInputHitTestSource {
    override fun cancel() {
        // Default implementation - no-op
    }
}

class DefaultXRLightProbe(val position: Vector3) : XRLightProbe {
    override val probeSpace: XRSpace = DefaultXRSpace("light_probe_space")

    override fun addEventListener(type: String, listener: (Any) -> Unit) {
        // Default implementation - no-op
    }

    override fun removeEventListener(type: String, listener: (Any) -> Unit) {
        // Default implementation - no-op
    }
}
