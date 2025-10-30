package io.materia.xr

import com.google.ar.core.*
import io.materia.core.math.*

/**
 * Wrapper implementations to bridge ARCore types with Materia XR interfaces
 */

/**
 * ARCore Pose wrapper implementing XRPose
 */
class ARCorePose(private val arPose: Pose) : XRPose {
    override val transform: XRRigidTransform
        get() {
            val translation = arPose.translation
            val rotation = arPose.rotationQuaternion
            return XRRigidTransform(
                position = Vector3(translation[0], translation[1], translation[2]),
                orientation = Quaternion(rotation[1], rotation[2], rotation[3], rotation[0])
            )
        }

    override val emulatedPosition: Boolean = false
    override val linearVelocity: Vector3? = null
    override val angularVelocity: Vector3? = null
}

/**
 * ARCore Space wrapper implementing XRSpace
 */
class ARCoreSpace(override val spaceId: String) : XRSpace {
    override val transform: Matrix4? = null
}

/**
 * ARCore Reference Space wrapper implementing XRReferenceSpace
 */
class ARCoreReferenceSpace(
    override val type: XRReferenceSpaceType,
    spaceId: String = "arcore_ref_${type.name}"
) : XRReferenceSpace {
    override val spaceId: String = spaceId
    override val transform: Matrix4? = null

    override fun getOffsetReferenceSpace(originOffset: Matrix4): XRReferenceSpace {
        return ARCoreReferenceSpace(type, "${spaceId}_offset")
    }
}

/**
 * ARCore Anchor wrapper implementing XRAnchor
 */
class ARCoreAnchor(
    private val arAnchor: Anchor,
    override val anchorId: String = "anchor_${arAnchor.hashCode()}"
) : XRAnchor {

    override val anchorSpace: XRSpace = ARCoreSpace("anchor_space_$anchorId")

    override fun delete() {
        arAnchor.detach()
    }

    fun getPose(): Pose = arAnchor.pose

    fun getTrackingState(): TrackingState = arAnchor.trackingState
}

/**
 * ARCore Plane wrapper implementing XRPlane
 */
class ARCorePlane(private val arPlane: com.google.ar.core.Plane) : XRPlane {

    override val planeSpace: XRSpace = ARCoreSpace("plane_${arPlane.hashCode()}")

    override val polygon: List<Vector3>
        get() {
            val buffer = arPlane.getPolygon()
            val points = mutableListOf<Vector3>()
            var i = 0
            while (i + 1 < buffer.remaining()) {  // Guard against buffer overflow
                val x = buffer.get(i)
                val z = buffer.get(i + 1)
                points.add(Vector3(x, 0f, z))
                i += 2
            }
            return points
        }

    override val orientation: PlaneOrientation
        get() = when (arPlane.getType()) {
            com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING -> PlaneOrientation.HORIZONTAL_UP
            com.google.ar.core.Plane.Type.HORIZONTAL_DOWNWARD_FACING -> PlaneOrientation.HORIZONTAL_DOWN
            com.google.ar.core.Plane.Type.VERTICAL -> PlaneOrientation.VERTICAL
            else -> PlaneOrientation.UNKNOWN
        }

    override val semanticLabel: String? = null // ARCore doesn't provide semantic labels for planes

    fun getCenterPose(): Pose = arPlane.getCenterPose()

    fun getExtentX(): Float = arPlane.getExtentX()

    fun getExtentZ(): Float = arPlane.getExtentZ()
}

/**
 * ARCore Face wrapper implementing XRFace
 */
class ARCoreFace(private val arFace: AugmentedFace) : XRFace {

    override val faceSpace: XRSpace = ARCoreSpace("face_${arFace.hashCode()}")

    override val landmarks: Map<String, Vector3>
        get() {
            val landmarksMap = mutableMapOf<String, Vector3>()

            // Map ARCore face regions to landmark names
            val regionToName = mapOf(
                AugmentedFace.RegionType.FOREHEAD_LEFT to "forehead_left",
                AugmentedFace.RegionType.FOREHEAD_RIGHT to "forehead_right",
                AugmentedFace.RegionType.NOSE_TIP to "nose_tip"
            )

            regionToName.forEach { (region, name) ->
                val pose = arFace.getRegionPose(region)
                val translation = pose.translation
                landmarksMap[name] = Vector3(translation[0], translation[1], translation[2])
            }

            return landmarksMap
        }

    override val mesh: XRMesh?
        get() = ARCoreFaceMesh(arFace)

    fun getCenterPose(): Pose = arFace.centerPose

    fun getTrackingState(): TrackingState = arFace.trackingState
}

/**
 * ARCore Face Mesh wrapper implementing XRMesh
 */
class ARCoreFaceMesh(private val arFace: AugmentedFace) : XRMesh {

    override val meshSpace: XRSpace = ARCoreSpace("face_mesh_${arFace.hashCode()}")

    override val vertices: List<Vector3>
        get() {
            val vertexBuffer = arFace.meshVertices
            val verticesList = mutableListOf<Vector3>()
            var i = 0
            while (i < vertexBuffer.remaining()) {
                val x = vertexBuffer.get(i)
                val y = vertexBuffer.get(i + 1)
                val z = vertexBuffer.get(i + 2)
                verticesList.add(Vector3(x, y, z))
                i += 3
            }
            return verticesList
        }

    override val indices: List<Int>
        get() {
            val indexBuffer = arFace.meshTriangleIndices
            val indicesList = mutableListOf<Int>()
            while (indexBuffer.hasRemaining()) {
                indicesList.add(indexBuffer.get().toInt())
            }
            return indicesList
        }

    override val semanticLabel: String = "face"
}

/**
 * ARCore Image wrapper implementing XRImageTarget
 */
class ARCoreImageTarget(
    private val augmentedImageDatabase: AugmentedImageDatabase,
    private val index: Int
) : XRImageTarget {

    override val image: Any = augmentedImageDatabase

    override val widthInMeters: Float = 0.1f // Default 10cm, should be configurable
}

/**
 * ARCore Tracked Image wrapper implementing XRTrackedImage
 */
class ARCoreTrackedImage(
    private val arImage: AugmentedImage,
    override val image: XRImageTarget
) : XRTrackedImage {

    override val trackingState: XRTrackingState
        get() = when (arImage.trackingState) {
            TrackingState.TRACKING -> XRTrackingState.TRACKING
            TrackingState.PAUSED -> XRTrackingState.PAUSED
            TrackingState.STOPPED -> XRTrackingState.STOPPED
            else -> XRTrackingState.NOT_TRACKING
        }

    override val measuredWidthInMeters: Float
        get() = arImage.extentX

    override val imageSpace: XRSpace = ARCoreSpace("image_${arImage.hashCode()}")

    override val emulatedPosition: Boolean = false

    fun getCenterPose(): Pose = arImage.centerPose

    fun getExtentZ(): Float = arImage.extentZ
}

/**
 * ARCore Hit Test Result wrapper implementing XRHitTestResult
 */
class ARCoreHitTestResult(
    private val arHitResult: HitResult,
    private val session: Session
) : XRHitTestResult {

    override fun getPose(baseSpace: XRSpace): XRPose {
        return ARCorePose(arHitResult.hitPose)
    }

    override suspend fun createAnchor(): XRAnchor? {
        return try {
            val anchor = arHitResult.createAnchor()
            ARCoreAnchor(anchor)
        } catch (e: Exception) {
            null
        }
    }

    fun getDistance(): Float = arHitResult.distance

    fun getTrackable(): Trackable = arHitResult.trackable
}

/**
 * ARCore Camera wrapper
 */
class ARCoreCamera(private val arCamera: Camera) : XRCamera {

    override val cameraImage: Any? = null // Requires CameraImage access

    override val intrinsics: CameraIntrinsics?
        get() {
            val arIntrinsics = arCamera.textureIntrinsics
            val imageDimensions = arIntrinsics.imageDimensions
            return CameraIntrinsics(
                focalLength = Vector2(arIntrinsics.focalLength[0], arIntrinsics.focalLength[1]),
                principalPoint = Vector2(
                    arIntrinsics.principalPoint[0],
                    arIntrinsics.principalPoint[1]
                ),
                imageSize = Vector2(
                    imageDimensions[0].toFloat(),
                    imageDimensions[1].toFloat()
                )
            )
        }

    fun getPose(): Pose = arCamera.pose

    fun getTrackingState(): TrackingState = arCamera.trackingState

    fun getProjectionMatrix(near: Float, far: Float): FloatArray {
        val projection = FloatArray(16)
        arCamera.getProjectionMatrix(projection, 0, near, far)
        return projection
    }

    fun getViewMatrix(): FloatArray {
        val view = FloatArray(16)
        arCamera.getViewMatrix(view, 0)
        return view
    }
}

/**
 * ARCore Light Estimate wrapper implementing XRLightEstimate
 */
class ARCoreLightEstimate(private val lightEstimate: LightEstimate) : XRLightEstimate {

    override val sphericalHarmonicsCoefficients: List<Vector3>
        get() {
            val harmonics =
                lightEstimate.environmentalHdrAmbientSphericalHarmonics ?: return emptyList()
            val coefficients = mutableListOf<Vector3>()

            // Convert ARCore spherical harmonics to Vector3 list
            var i = 0
            while (i < harmonics.size) {
                coefficients.add(
                    Vector3(
                    harmonics[i],
                    harmonics.getOrElse(i + 1) { 0f },
                    harmonics.getOrElse(i + 2) { 0f }
                ))
                i += 3
            }

            return coefficients
        }

    override val primaryLightDirection: Vector3
        get() {
            val direction =
                lightEstimate.environmentalHdrMainLightDirection ?: floatArrayOf(0f, -1f, 0f)
            return Vector3(direction[0], direction[1], direction[2])
        }

    override val primaryLightIntensity: Float
        get() {
            val intensity =
                lightEstimate.environmentalHdrMainLightIntensity ?: floatArrayOf(1f, 1f, 1f)
            return (intensity[0] + intensity[1] + intensity[2]) / 3f
        }

    fun getPixelIntensity(): Float = lightEstimate.pixelIntensity

    fun getColorCorrection(): FloatArray {
        val colorCorrection = FloatArray(4)
        lightEstimate.getColorCorrection(colorCorrection, 0)
        return colorCorrection
    }
}

/**
 * Convert ARCore tracking state to XR tracking state
 */
fun TrackingState.toXRTrackingState(): XRTrackingState {
    return when (this) {
        TrackingState.TRACKING -> XRTrackingState.TRACKING
        TrackingState.PAUSED -> XRTrackingState.PAUSED
        TrackingState.STOPPED -> XRTrackingState.STOPPED
        else -> XRTrackingState.NOT_TRACKING
    }
}

/**
 * Convert ARCore matrix to Materia Matrix4
 */
fun FloatArray.toMatrix4(): Matrix4 {
    require(this.size >= 16) { "Float array must have at least 16 elements for Matrix4" }
    return Matrix4(this.copyOf(16))
}