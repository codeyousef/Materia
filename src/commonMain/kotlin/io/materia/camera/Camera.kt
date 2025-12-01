package io.materia.camera

import io.materia.core.math.*
import io.materia.core.scene.Object3D
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.tan

/**
 * Abstract base class for all cameras.
 * Compatible with Three.js Camera API.
 *
 * Cameras define the viewpoint and projection for rendering.
 * They extend Object3D to inherit transformation capabilities.
 */
abstract class Camera : Object3D() {

    /**
     * Projection matrix for this camera
     */
    val projectionMatrix: Matrix4 = Matrix4()

    /**
     * Inverse of the projection matrix
     */
    val projectionMatrixInverse: Matrix4 = Matrix4()

    /**
     * Inverse of the world matrix (view matrix)
     */
    val matrixWorldInverse: Matrix4 = Matrix4()

    /**
     * Alias for matrixWorldInverse
     */
    val viewMatrix: Matrix4
        get() = matrixWorldInverse

    /**
     * Near clipping plane distance
     */
    var near: Float = 0.1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Far clipping plane distance
     */
    var far: Float = 2000f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Zoom factor (for compatibility - overridden in subclasses)
     */
    open var zoom: Float = 1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    // Orthographic camera properties (default values for compatibility)
    open var left: Float = -1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    open var right: Float = 1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    open var top: Float = 1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    open var bottom: Float = -1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Whether the projection matrix needs updating
     */
    protected var projectionMatrixNeedsUpdate: Boolean = true

    init {
        name = "Camera"
    }

    /**
     * Updates the projection matrix
     * Must be implemented by subclasses
     */
    abstract fun updateProjectionMatrix()

    /**
     * Sets view offset for advanced rendering techniques
     */
    abstract fun setViewOffset(
        fullWidth: Int,
        fullHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    )

    /**
     * Clears view offset
     */
    abstract fun clearViewOffset()

    /**
     * Gets the effective field of view (for perspective cameras)
     */
    open fun getEffectiveFOV(): Float = 50f

    /**
     * Gets the film gauge
     */
    open fun getFilmWidth(): Float = 35f

    /**
     * Gets the film height
     */
    open fun getFilmHeight(): Float = 35f

    /**
     * Gets the focal length in millimeters
     */
    open fun getFocalLength(): Float {
        val vExtentSlope = tan(getEffectiveFOV() * PI.toFloat() / 360f)
        return getFilmHeight() / ((2f * vExtentSlope))
    }

    /**
     * Sets the focal length
     */
    open fun setFocalLength(focalLength: Float) {
        val vExtentSlope = getFilmHeight() / ((2f * focalLength))
        setFieldOfView(atan(vExtentSlope) * 360f / PI.toFloat())
    }

    /**
     * Sets the field of view (for compatible cameras)
     */
    open fun setFieldOfView(fov: Float) {
        // Default implementation - override in subclasses
    }

    /**
     * Updates the view matrix (inverse world matrix)
     */
    override fun updateMatrixWorld(force: Boolean) {
        super.updateMatrixWorld(force)
        matrixWorldInverse.copy(matrixWorld).invert()
    }

    /**
     * Creates a ray from the camera through a point in normalized device coordinates
     */
    override fun getWorldDirection(target: Vector3): Vector3 {
        updateMatrixWorld()
        val e = matrixWorld.elements
        return target.set(-e[8], -e[9], -e[10]).normalize()
    }

    /**
     * Converts world coordinates to normalized device coordinates
     */
    open fun worldToNDC(worldPosition: Vector3, target: Vector3 = Vector3()): Vector3 {
        updateMatrixWorld()
        updateProjectionMatrix()

        target.copy(worldPosition)
        target.applyMatrix4(matrixWorldInverse)
        target.applyMatrix4(projectionMatrix)

        return target
    }

    /**
     * Converts normalized device coordinates to world coordinates
     */
    fun ndcToWorld(ndcPosition: Vector3, target: Vector3 = Vector3()): Vector3 {
        updateMatrixWorld()
        updateProjectionMatrix()

        val inverseProjection = Matrix4().copy(projectionMatrix).invert()
        target.copy(ndcPosition)
        target.applyMatrix4(inverseProjection)
        target.applyMatrix4(matrixWorld)

        return target
    }

    /**
     * Creates a copy of this camera
     */
    override fun clone(recursive: Boolean): Camera {
        throw NotImplementedError("Clone must be implemented by camera subclass")
    }

    /**
     * Copies properties from another camera
     */
    override fun copy(source: Object3D, recursive: Boolean): Camera {
        super.copy(source, recursive)

        if (source is Camera) {
            projectionMatrix.copy(source.projectionMatrix)
            matrixWorldInverse.copy(source.matrixWorldInverse)
            near = source.near
            far = source.far
        }

        return this
    }
}

/**
 * Viewport information for cameras
 */
data class Viewport(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int,
    val height: Int
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
    val center: Vector2 get() = Vector2(x + width / 2f, y + height / 2f)
}

/**
 * View offset for advanced rendering
 */
data class ViewOffset(
    val enabled: Boolean = false,
    val fullWidth: Int = 0,
    val fullHeight: Int = 0,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Camera utilities
 */
object CameraUtils {

    /**
     * Calculates the distance needed for an object to fill the camera view
     */
    fun getDistanceToFitObject(
        camera: Camera,
        objectSize: Float,
        aspectRatio: Float = 1f
    ): Float {
        return when (camera) {
            is PerspectiveCamera -> {
                val fov = camera.fov * PI.toFloat() / 180f
                val distance = objectSize / (2f * tan(fov / 2f))
                distance / aspectRatio
            }

            is OrthographicCamera -> {
                // Orthographic cameras don't have distance-based sizing
                camera.position.length()
            }

            else -> 10f
        }
    }

    /**
     * Fits camera to show a bounding box
     */
    fun fitCameraToBox(camera: Camera, box: Box3, padding: Float = 1.1f) {
        val size = box.getSize()
        val center = box.getCenter()

        val maxSize = maxOf(size.x, size.y, size.z)
        val distance = getDistanceToFitObject(camera, (maxSize * padding))

        camera.position.copy(center)
        camera.position.z = camera.position.z + distance
        camera.lookAt(center)
        camera.updateMatrixWorld()
    }

    /**
     * Creates a ray from camera through screen coordinates
     */
    fun createRay(
        camera: Camera,
        screenX: Float,
        screenY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Ray {
        val ndc = Vector3(
            (screenX / screenWidth) * 2f - 1f,
            -(screenY / screenHeight) * 2f + 1f,
            -1f
        )

        val worldDirection = camera.ndcToWorld(ndc)
        worldDirection.sub(camera.position).normalize()

        return Ray(camera.position.clone(), worldDirection)
    }

    /**
     * Converts world coordinates to screen coordinates
     */
    fun worldToScreen(
        worldPosition: Vector3,
        camera: Camera,
        screenWidth: Float,
        screenHeight: Float,
        target: Vector2 = Vector2()
    ): Vector2 {
        val ndc = camera.worldToNDC(worldPosition)

        target.x = (ndc.x + 1f) * screenWidth / 2f
        target.y = (-ndc.y + 1f) * screenHeight / 2f

        return target
    }

    /**
     * Checks if a point is visible to the camera
     */
    fun isPointVisible(
        worldPosition: Vector3,
        camera: Camera
    ): Boolean {
        val ndc = camera.worldToNDC(worldPosition)
        return ndc.x >= -1f && ndc.x <= 1f &&
                ndc.y >= -1f && ndc.y <= 1f &&
                ndc.z >= -1f && ndc.z <= 1f
    }

    /**
     * Gets the frustum corners in world space
     */
    fun getFrustumCorners(camera: Camera): Array<Vector3> {
        val corners = arrayOf(
            Vector3(-1f, -1f, -1f), Vector3(1f, -1f, -1f),
            Vector3(1f, 1f, -1f), Vector3(-1f, 1f, -1f),
            Vector3(-1f, -1f, 1f), Vector3(1f, -1f, 1f),
            Vector3(1f, 1f, 1f), Vector3(-1f, 1f, 1f)
        )

        return corners.map { camera.ndcToWorld(it) }.toTypedArray()
    }
}

/**
 * Camera controls interface
 */
interface CameraControls {
    val camera: Camera
    val target: Vector3
    var enabled: Boolean

    fun update(deltaTime: Float)
    fun reset()
    fun dispose()
}

/**
 * Basic camera animation system
 */
class CameraAnimator {
    private var isAnimating = false
    private var startPosition = Vector3()
    private var endPosition = Vector3()
    private var startTarget = Vector3()
    private var endTarget = Vector3()
    private var duration = 1f
    private var elapsed = 0f
    private var onComplete: (() -> Unit)? = null

    /**
     * Animates camera to a new position and target
     */
    fun animateTo(
        camera: Camera,
        position: Vector3,
        target: Vector3,
        duration: Float = 1f,
        onComplete: (() -> Unit)? = null
    ) {
        this.startPosition.copy(camera.position)
        this.endPosition.copy(position)
        this.startTarget.copy(target) // Would need actual target tracking
        this.endTarget.copy(target)
        this.duration = duration
        this.elapsed = 0f
        this.onComplete = onComplete
        this.isAnimating = true
    }

    /**
     * Updates animation
     */
    fun update(camera: Camera, deltaTime: Float) {
        if (!isAnimating) return

        elapsed = elapsed + deltaTime
        val t = (elapsed / duration).coerceIn(0f, 1f)

        // Smooth interpolation
        val smoothT = t * t * (3f - (2f * t))

        camera.position.copy(startPosition).lerp(endPosition, smoothT)

        val currentTarget = Vector3().copy(startTarget).lerp(endTarget, smoothT)
        camera.lookAt(currentTarget)

        if (t >= 1f) {
            isAnimating = false
            onComplete?.invoke()
        }
    }

    /**
     * Stops current animation
     */
    fun stop() {
        isAnimating = false
    }

    /**
     * Checks if animation is playing
     */
    fun isPlaying(): Boolean = isAnimating
}