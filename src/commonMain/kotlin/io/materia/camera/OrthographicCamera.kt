package io.materia.camera

import io.materia.core.math.Box3
import io.materia.core.math.Ray
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D

/**
 * Orthographic camera with parallel projection.
 * Compatible with Three.js OrthographicCamera API.
 *
 * In orthographic projection, objects appear the same size regardless of distance.
 * Commonly used for technical drawings, CAD applications, and 2D-style games.
 */
class OrthographicCamera(
    /**
     * Left edge of the view frustum
     */
    left: Float = -1f,

    /**
     * Right edge of the view frustum
     */
    right: Float = 1f,

    /**
     * Top edge of the view frustum
     */
    top: Float = 1f,

    /**
     * Bottom edge of the view frustum
     */
    bottom: Float = -1f,

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
     * Left edge of the view frustum
     */
    override var left: Float = left
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Right edge of the view frustum
     */
    override var right: Float = right
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Top edge of the view frustum
     */
    override var top: Float = top
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Bottom edge of the view frustum
     */
    override var bottom: Float = bottom
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * Zoom factor for scaling the view
     */
    override var zoom: Float = 1f
        set(value) {
            field = value
            updateProjectionMatrix()
        }

    /**
     * View offset for advanced rendering techniques
     */
    private var viewOffset: ViewOffset? = null

    init {
        this.near = near
        this.far = far
        name = "OrthographicCamera"
        updateProjectionMatrix()
    }

    /**
     * Updates the projection matrix based on current parameters
     */
    override fun updateProjectionMatrix() {
        val dx = (right - left) / ((2f * zoom))
        val dy = (top - bottom) / ((2f * zoom))
        val cx = (right + left) / 2f
        val cy = (top + bottom) / 2f

        var left = cx - dx
        var right = cx + dx
        var top = cy + dy
        var bottom = cy - dy

        val view = viewOffset
        if (view != null && view.enabled) {
            val scaleW = (this.right - this.left) / view.fullWidth
            val scaleH = (this.top - this.bottom) / view.fullHeight

            left = left + scaleW * view.offsetX
            right = left + scaleW * view.width
            top = top - scaleH * view.offsetY
            bottom = top - scaleH * view.height
        }

        projectionMatrix.makeOrthographic(left, right, top, bottom, near, far)
        projectionMatrixNeedsUpdate = false
    }

    /**
     * Sets view offset for tiled rendering
     */
    override fun setViewOffset(
        fullWidth: Int,
        fullHeight: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
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
     * Sets the camera frustum to show a specific area
     */
    fun setViewBounds(left: Float, right: Float, top: Float, bottom: Float) {
        this.left = left
        this.right = right
        this.top = top
        this.bottom = bottom
        updateProjectionMatrix()
    }

    /**
     * Sets the camera to show a centered area with given size
     */
    fun setViewSize(width: Float, height: Float) {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        setViewBounds(-halfWidth, halfWidth, halfHeight, -halfHeight)
    }

    /**
     * Sets the camera bounds from aspect ratio and size
     */
    fun setFromAspectAndSize(aspect: Float, size: Float) {
        val width = size * aspect
        val height = size
        setViewSize(width, height)
    }

    /**
     * Gets the width of the view
     */
    fun getViewWidth(): Float = (right - left) / zoom

    /**
     * Gets the height of the view
     */
    fun getViewHeight(): Float = (top - bottom) / zoom

    /**
     * Gets the aspect ratio of the view
     */
    fun getAspectRatio(): Float = getViewWidth() / getViewHeight()

    /**
     * Gets the center of the view
     */
    fun getViewCenter(target: Vector2 = Vector2()): Vector2 {
        return target.set((left + right) / 2f, (top + bottom) / 2f)
    }

    /**
     * Creates a ray from camera through normalized device coordinates
     */
    fun createRay(ndcX: Float, ndcY: Float): Ray {
        val direction = Vector3(0f, 0f, -1f)
        direction.applyQuaternion(quaternion)

        val x = left + (right - left) * (ndcX + 1f) / 2f
        val y = bottom + (top - bottom) * (ndcY + 1f) / 2f

        val origin = Vector3(x, y, 0f)
        origin.applyMatrix4(matrixWorld)

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
     * Fits the camera view to contain a bounding box
     */
    fun fitToBox(box: Box3, padding: Float = 1.1f) {
        val size = box.getSize()
        val center = box.getCenter()

        val paddedWidth = size.x * padding
        val paddedHeight = size.y * padding

        setViewBounds(
            center.x - paddedWidth / 2f,
            center.x + paddedWidth / 2f,
            center.y + paddedHeight / 2f,
            center.y - paddedHeight / 2f
        )

        // Position camera to look at the center
        position.copy(center)
        position.z = position.z + maxOf(size.z / 2f + 1f, 1f)
    }

    /**
     * Fits the camera view to contain a sphere
     */
    fun fitToSphere(center: Vector3, radius: Float, padding: Float = 1.1f) {
        val size = radius * 2f * padding
        setViewSize(size, size)

        position.copy(center)
        position.z = position.z + radius + 1f
    }

    /**
     * Converts world coordinates to view coordinates
     */
    fun worldToView(worldPosition: Vector3, target: Vector3 = Vector3()): Vector3 {
        target.copy(worldPosition)
        target.applyMatrix4(matrixWorldInverse)
        return target
    }

    /**
     * Converts view coordinates to world coordinates
     */
    fun viewToWorld(viewPosition: Vector3, target: Vector3 = Vector3()): Vector3 {
        target.copy(viewPosition)
        target.applyMatrix4(matrixWorld)
        return target
    }

    /**
     * Gets the world bounds of the view at a specific Z distance
     */
    fun getWorldBounds(z: Float = 0f, target: Box3 = Box3()): Box3 {
        val leftBottom = Vector3(left / zoom, bottom / zoom, z)
        val rightTop = Vector3(right / zoom, top / zoom, z)

        leftBottom.applyMatrix4(matrixWorld)
        rightTop.applyMatrix4(matrixWorld)

        target.min.copy(leftBottom)
        target.max.copy(rightTop)

        return target
    }

    /**
     * Creates a copy of this camera
     */
    override fun clone(recursive: Boolean): OrthographicCamera {
        return OrthographicCamera(left, right, top, bottom, near, far)
            .copy(this, recursive) as OrthographicCamera
    }

    /**
     * Copies properties from another camera
     */
    override fun copy(source: Object3D, recursive: Boolean): OrthographicCamera {
        super.copy(source, recursive)

        if (source is OrthographicCamera) {
            left = source.left
            right = source.right
            top = source.top
            bottom = source.bottom
            zoom = source.zoom
            viewOffset = source.viewOffset
        }

        return this
    }

    /**
     * Exports camera parameters to JSON
     */
    fun toJSON(): Map<String, Any> {
        val result = mutableMapOf<String, Any>(
            "type" to "OrthographicCamera",
            "left" to left,
            "right" to right,
            "top" to top,
            "bottom" to bottom,
            "near" to near,
            "far" to far,
            "zoom" to zoom
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
        return "OrthographicCamera(left=$left, right=$right, top=$top, bottom=$bottom, near=$near, far=$far)"
    }
}

/**
 * Builder for creating orthographic cameras with DSL syntax
 */
class OrthographicCameraBuilder {
    private var left: Float = -1f
    private var right: Float = 1f
    private var top: Float = 1f
    private var bottom: Float = -1f
    private var near: Float = 0.1f
    private var far: Float = 2000f
    private var position: Vector3 = Vector3()
    private var target: Vector3 = Vector3(0f, 0f, -1f)
    private var zoom: Float = 1f

    fun bounds(left: Float, right: Float, top: Float, bottom: Float) {
        this.left = left
        this.right = right
        this.top = top
        this.bottom = bottom
    }

    fun size(width: Float, height: Float) {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        bounds(-halfWidth, halfWidth, halfHeight, -halfHeight)
    }

    fun aspectAndSize(aspect: Float, size: Float) {
        val width = size * aspect
        size(width, size)
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

    internal fun build(): OrthographicCamera {
        val camera = OrthographicCamera(left, right, top, bottom, near, far)
        camera.position.copy(position)
        camera.zoom = zoom
        camera.lookAt(target)
        return camera
    }
}

/**
 * DSL function for creating orthographic cameras
 */
fun orthographicCamera(block: OrthographicCameraBuilder.() -> Unit): OrthographicCamera {
    return OrthographicCameraBuilder().apply(block).build()
}

/**
 * Camera type for 2D rendering
 */
fun camera2D(width: Float, height: Float, zoom: Float = 1f): OrthographicCamera {
    return orthographicCamera {
        size(width, height)
        zoom(zoom)
        position(0f, 0f, 1f)
        lookAt(0f, 0f, 0f)
    }
}

/**
 * Extension functions for OrthographicCamera
 */

/**
 * Sets camera bounds from viewport dimensions
 */
fun OrthographicCamera.setFromViewport(width: Int, height: Int, pixelsPerUnit: Float = 1f) {
    val worldWidth = width / pixelsPerUnit
    val worldHeight = height / pixelsPerUnit
    setViewSize(worldWidth, worldHeight)
}

/**
 * Pans the camera by a given offset
 */
fun OrthographicCamera.pan(deltaX: Float, deltaY: Float) {
    left = left + deltaX
    right = right + deltaX
    top = top + deltaY
    bottom = bottom + deltaY
    updateProjectionMatrix()
}

/**
 * Centers the camera on a specific point
 */
fun OrthographicCamera.centerOn(point: Vector2) {
    val width = getViewWidth()
    val height = getViewHeight()
    setViewBounds(
        point.x - width / 2f,
        point.x + width / 2f,
        point.y + height / 2f,
        point.y - height / 2f
    )
}

/**
 * Gets the scale factor for converting pixels to world units
 */
fun OrthographicCamera.getPixelsPerUnit(screenHeight: Int): Float {
    return screenHeight / getViewHeight()
}