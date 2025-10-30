package io.materia.camera

import io.materia.core.math.*
import io.materia.core.scene.Object3D
import kotlin.math.*

/**
 * Perspective camera with field of view projection.
 * Compatible with Three.js PerspectiveCamera API.
 *
 * Simulates human eye perspective where distant objects appear smaller.
 * Most commonly used camera type for 3D scenes.
 */
class PerspectiveCamera(
    /**
     * Field of view in degrees
     */
    var fov: Float = 50f,

    /**
     * Aspect ratio (width / height)
     */
    var aspect: Float = 1f,

    /**
     * Near clipping plane distance
     */
    near: Float = 0.1f,

    /**
     * Far clipping plane distance
     */
    far: Float = 2000f
) : Camera() {

    /**
     * Focus distance for depth of field effects
     */
    var focus: Float = 10f

    /**
     * Zoom factor
     */
    override var zoom: Float = 1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Film gauge in millimeters (used for focal length calculations)
     */
    var filmGauge: Float = 35f

    /**
     * Film offset for lens shift effects
     */
    var filmOffset: Float = 0f

    /**
     * View offset for advanced rendering techniques
     */
    private var viewOffset: ViewOffset? = null

    init {
        this.near = near
        this.far = far
        name = "PerspectiveCamera"
        updateProjectionMatrix()
    }

    /**
     * Updates the projection matrix based on current parameters
     */
    override fun updateProjectionMatrix() {
        val near = this.near
        var top = near * tan(fov * PI.toFloat() / 360f) / zoom
        var height = 2f * top
        var width = aspect * height
        var left = -0.5f * width

        val view = viewOffset
        if (view != null && view.enabled) {
            val fullWidth = view.fullWidth.toFloat()
            val fullHeight = view.fullHeight.toFloat()
            left = left + view.offsetX * width / fullWidth
            top = top - view.offsetY * height / fullHeight
            width = width * view.width / fullWidth
            height = height * view.height / fullHeight
        }

        val skew = filmOffset
        if (skew != 0f) left = left + near * skew / getFilmWidth()

        // T021: Use WebGPU projection (Z ∈ [0, 1]) for correct depth handling
        // WebGPU and modern graphics APIs use 0..1 depth range, not -1..1 like OpenGL
        projectionMatrix.makePerspectiveWebGPU(
            left, left + width,
            top, top - height,
            near, far
        )

        projectionMatrixNeedsUpdate = false
    }

    /**
     * Sets view offset for tiled rendering or VR
     */
    override fun setViewOffset(
        fullWidth: Int,
        fullHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        aspect = fullWidth.toFloat() / fullHeight.toFloat()
        viewOffset = ViewOffset(
            enabled = true,
            fullWidth = fullWidth,
            fullHeight = fullHeight,
            offsetX = x,
            offsetY = y,
            width = width,
            height = height
        )
        updateProjectionMatrix()
    }

    /**
     * Clears view offset
     */
    override fun clearViewOffset() {
        viewOffset = null
        updateProjectionMatrix()
    }

    /**
     * Gets the effective field of view
     */
    override fun getEffectiveFOV(): Float {
        return fov / zoom
    }

    /**
     * Gets the film width
     */
    override fun getFilmWidth(): Float {
        return filmGauge * min(1f, aspect)
    }

    /**
     * Gets the film height
     */
    override fun getFilmHeight(): Float {
        return filmGauge / max(1f, aspect)
    }

    /**
     * Sets the field of view and updates projection
     */
    override fun setFieldOfView(fov: Float) {
        this.fov = fov
        updateProjectionMatrix()
    }

    /**
     * Sets the focal length in millimeters
     */
    override fun setFocalLength(focalLength: Float) {
        val vExtentSlope = 0.5f * getFilmHeight() / focalLength
        fov = atan(vExtentSlope) * 360f / PI.toFloat()
        updateProjectionMatrix()
    }

    /**
     * Gets the focal length in millimeters
     */
    override fun getFocalLength(): Float {
        val vExtentSlope = tan(fov * PI.toFloat() / 360f)
        return 0.5f * getFilmHeight() / vExtentSlope
    }

    /**
     * Sets lens parameters for realistic camera simulation
     */
    fun setLens(focalLength: Float, filmGauge: Float = 35f) {
        this.filmGauge = filmGauge
        setFocalLength(focalLength)
    }

    /**
     * Gets the current viewport bounds
     */
    fun getViewBounds(distance: Float, target: Box3 = Box3()): Box3 {
        val vFOV = fov * PI.toFloat() / 180f
        val height = 2f * tan(vFOV / 2f) * distance
        val width = height * aspect

        target.min.set(-width / 2f, -height / 2f, distance)
        target.max.set(width / 2f, height / 2f, distance)

        return target
    }

    /**
     * Gets view size at a specific distance
     */
    fun getViewSize(distance: Float, target: Vector2 = Vector2()): Vector2 {
        val vFOV = fov * PI.toFloat() / 180f
        val height = 2f * tan(vFOV / 2f) * distance
        val width = height * aspect

        return target.set(width, height)
    }

    /**
     * Converts world coordinates to normalized device coordinates
     */
    override fun worldToNDC(worldPosition: Vector3, target: Vector3): Vector3 {
        return super.worldToNDC(worldPosition, target)
    }

    /**
     * Creates a ray from camera through normalized device coordinates
     */
    fun createRay(ndcX: Float, ndcY: Float): Ray {
        val origin = Vector3()
        val direction = Vector3(ndcX, ndcY, 0.5f)

        // Transform from NDC to world space
        val inverseProjectionMatrix = Matrix4().copy(projectionMatrix).invert()
        direction.applyMatrix4(inverseProjectionMatrix)
        direction.applyMatrix4(matrixWorld)
        direction.sub(position).normalize()

        getWorldPosition(origin)
        return Ray(origin, direction)
    }

    /**
     * Creates a ray from camera through screen coordinates
     */
    fun createRayFromScreen(
        screenX: Float,
        screenY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Ray {
        val ndcX = (screenX / screenWidth) * 2f - 1f
        val ndcY = -(screenY / screenHeight) * 2f + 1f
        return createRay(ndcX, ndcY)
    }

    /**
     * Calculates distance to fit a sphere in view
     */
    fun getDistanceToFitSphere(radius: Float, padding: Float = 1.1f): Float {
        val vFOV = fov * PI.toFloat() / 180f
        val distance = ((radius * padding)) / tan(vFOV / 2f)
        return maxOf(distance, near + radius)
    }

    /**
     * Calculates distance to fit a box in view
     */
    fun getDistanceToFitBox(box: Box3, padding: Float = 1.1f): Float {
        val size = box.getSize()
        val maxDimension = maxOf(size.x, size.y, size.z)
        return getDistanceToFitSphere(maxDimension / 2f, padding)
    }

    /**
     * Adjusts camera to frame an object
     */
    fun frameObject(
        targetPosition: Vector3,
        objectSize: Float,
        padding: Float = 1.1f,
        direction: Vector3 = Vector3(0f, 0f, 1f)
    ) {
        val distance = getDistanceToFitSphere(objectSize / 2f, padding)
        val offset = Vector3().copy(direction).normalize().multiplyScalar(distance)

        position.copy(targetPosition).add(offset)
        lookAt(targetPosition)
        updateMatrixWorld()
    }

    /**
     * Creates a copy of this camera
     */
    override fun clone(recursive: Boolean): PerspectiveCamera {
        return PerspectiveCamera(fov, aspect, near, far).copy(this, recursive) as PerspectiveCamera
    }

    /**
     * Copies properties from another camera
     */
    override fun copy(source: Object3D, recursive: Boolean): PerspectiveCamera {
        super.copy(source, recursive)

        if (source is PerspectiveCamera) {
            fov = source.fov
            aspect = source.aspect
            zoom = source.zoom
            focus = source.focus
            filmGauge = source.filmGauge
            filmOffset = source.filmOffset
            viewOffset = source.viewOffset
        }

        return this
    }

    /**
     * Exports camera parameters to JSON
     */
    fun toJSON(): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "type" to "PerspectiveCamera",
            "fov" to fov,
            "aspect" to aspect,
            "near" to near,
            "far" to far,
            "zoom" to zoom,
            "focus" to focus,
            "filmGauge" to filmGauge,
            "filmOffset" to filmOffset
        )

        viewOffset?.let { vo ->
            if (vo.enabled) {
                result["viewOffset"] = mapOf(
                    "fullWidth" to vo.fullWidth,
                    "fullHeight" to vo.fullHeight,
                    "offsetX" to vo.offsetX,
                    "offsetY" to vo.offsetY,
                    "width" to vo.width,
                    "height" to vo.height
                )
            }
        }

        return result
    }

    override fun toString(): String {
        return "PerspectiveCamera(fov=$fov, aspect=$aspect, near=$near, far=$far)"
    }
}

/**
 * Builder for creating perspective cameras with DSL syntax
 */
class PerspectiveCameraBuilder {
    private var fov: Float = 50f
    private var aspect: Float = 1f
    private var near: Float = 0.1f
    private var far: Float = 2000f
    private var position: Vector3 = Vector3()
    private var target: Vector3 = Vector3(0f, 0f, -1f)
    private var zoom: Float = 1f

    fun fov(fov: Float) {
        this.fov = fov
    }

    fun aspect(aspect: Float) {
        this.aspect = aspect
    }

    fun near(near: Float) {
        this.near = near
    }

    fun far(far: Float) {
        this.far = far
    }

    fun zoom(zoom: Float) {
        this.zoom = zoom
    }

    fun position(x: Float, y: Float, z: Float) {
        position.set(x, y, z)
    }

    fun position(position: Vector3) {
        this.position.copy(position)
    }

    fun lookAt(x: Float, y: Float, z: Float) {
        target.set(x, y, z)
    }

    fun lookAt(target: Vector3) {
        this.target.copy(target)
    }

    internal fun build(): PerspectiveCamera {
        val camera = PerspectiveCamera(fov, aspect, near, far)
        camera.position.copy(position)
        camera.zoom = zoom
        camera.lookAt(target)
        return camera
    }
}

/**
 * DSL function for creating perspective cameras
 */
fun perspectiveCamera(block: PerspectiveCameraBuilder.() -> Unit): PerspectiveCamera {
    return PerspectiveCameraBuilder().apply(block).build()
}

/**
 * Extension functions for PerspectiveCamera
 */

/**
 * Sets camera aspect ratio from viewport dimensions
 */
fun PerspectiveCamera.setAspectFromViewport(width: Int, height: Int) {
    aspect = width.toFloat() / height.toFloat()
    updateProjectionMatrix()
}

/**
 * Adjusts FOV to maintain the same view at a different distance
 */
fun PerspectiveCamera.adjustFOVForDistance(currentDistance: Float, newDistance: Float) {
    val currentVFOV = fov * PI.toFloat() / 180f
    val currentHeight = 2f * tan(currentVFOV / 2f) * currentDistance
    val newVFOV = 2f * atan(currentHeight / ((2f * newDistance)))
    fov = newVFOV * 180f / PI.toFloat()
    updateProjectionMatrix()
}

/**
 * Gets the horizontal field of view
 */
fun PerspectiveCamera.getHorizontalFOV(): Float {
    val vFOV = fov * PI.toFloat() / 180f
    val hFOV = 2f * atan(tan(vFOV / 2f) * aspect)
    return hFOV * 180f / PI.toFloat()
}