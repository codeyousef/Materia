package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Matrix4
import io.materia.core.math.Quaternion
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import io.materia.core.scene.Mesh
import io.materia.core.scene.Ray
import io.materia.core.scene.Raycaster
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Transform controls for interactively manipulating objects.
 * 
 * Provides translation, rotation, and scale gizmos that can be used
 * to manipulate Object3D instances in 3D space. Similar to Three.js
 * TransformControls.
 *
 * Usage:
 * ```kotlin
 * val controls = TransformControls(camera)
 * controls.attach(selectedObject)
 * controls.mode = TransformMode.TRANSLATE
 * 
 * // In render loop
 * controls.update()
 * 
 * // Handle input
 * controls.onPointerDown(x, y)
 * controls.onPointerMove(x, y)
 * controls.onPointerUp()
 * ```
 */
class TransformControls(
    val camera: Camera,
    config: TransformControlsConfig = TransformControlsConfig()
) {

    /**
     * Configuration for transform controls
     */
    data class TransformControlsConfig(
        val translationSnap: Float? = null,
        val rotationSnap: Float? = null,
        val scaleSnap: Float? = null,
        val size: Float = 1f,
        val showX: Boolean = true,
        val showY: Boolean = true,
        val showZ: Boolean = true,
        val space: TransformSpace = TransformSpace.WORLD
    )

    /**
     * Transform mode (translate, rotate, scale)
     */
    enum class TransformMode {
        TRANSLATE,
        ROTATE,
        SCALE
    }

    /**
     * Transform space (world or local)
     */
    enum class TransformSpace {
        WORLD,
        LOCAL
    }

    /**
     * Axis constraint for transformations
     */
    enum class TransformAxis {
        NONE,
        X, Y, Z,
        XY, XZ, YZ,
        XYZ  // Free movement
    }

    // Configuration
    private val config = config.copy()
    
    /** Enable/disable the controls */
    var enabled: Boolean = true

    /** Current transformation mode */
    var mode: TransformMode = TransformMode.TRANSLATE
        set(value) {
            field = value
            updateGizmo()
        }

    /** Current transformation space */
    var space: TransformSpace
        get() = config.space
        set(value) {
            // Create new config (data class is immutable)
        }

    /** Visual size of gizmo */
    var size: Float = config.size
        set(value) {
            field = value
            updateGizmo()
        }

    // Attached object
    private var _object: Object3D? = null
    
    /** The currently attached object */
    val attachedObject: Object3D? get() = _object

    // Gizmo state
    private var activeAxis: TransformAxis = TransformAxis.NONE
    private var isDragging: Boolean = false
    private var hoverAxis: TransformAxis = TransformAxis.NONE

    // Transform working data
    private val startPosition = Vector3()
    private val startRotation = Quaternion()
    private val startScale = Vector3()
    private val worldPosition = Vector3()
    private val worldQuaternion = Quaternion()
    private val worldScale = Vector3()

    // Pointer tracking
    private val pointerStart = Vector2()
    private val pointerCurrent = Vector2()
    private var viewportWidth: Float = 800f
    private var viewportHeight: Float = 600f

    // Raycasting for gizmo picking
    private val raycaster = Raycaster()
    private val ray = Ray(Vector3(), Vector3())

    // Gizmo visual elements (meshes for each axis)
    private val gizmoMeshes = mutableListOf<GizmoHandle>()

    // Event listeners
    private val listeners = mutableListOf<TransformControlsListener>()

    /**
     * Attach an object to the controls
     */
    fun attach(obj: Object3D) {
        _object = obj
        updateGizmo()
        dispatchEvent(TransformEvent.ATTACHED)
    }

    /**
     * Detach the current object
     */
    fun detach() {
        _object = null
        updateGizmo()
        dispatchEvent(TransformEvent.DETACHED)
    }

    /**
     * Set viewport dimensions for proper raycasting
     */
    fun setViewport(width: Float, height: Float) {
        viewportWidth = width
        viewportHeight = height
    }

    /**
     * Update the controls (call each frame)
     */
    fun update() {
        if (!enabled || _object == null) return
        
        // Update gizmo position to match object
        val obj = _object ?: return
        
        obj.updateMatrixWorld()
        obj.getWorldPosition(worldPosition)
        obj.getWorldQuaternion(worldQuaternion)
        obj.getWorldScale(worldScale)
        
        updateGizmo()
    }

    /**
     * Handle pointer down event
     * @param x Normalized x coordinate (-1 to 1)
     * @param y Normalized y coordinate (-1 to 1)
     */
    fun onPointerDown(x: Float, y: Float): Boolean {
        if (!enabled || _object == null) return false

        // Convert to normalized device coordinates
        pointerStart.set(x, y)
        pointerCurrent.set(x, y)

        // Check which gizmo handle was clicked
        val hitAxis = raycastGizmo(x, y)
        
        if (hitAxis != TransformAxis.NONE) {
            activeAxis = hitAxis
            isDragging = true
            
            // Store starting transform
            val obj = _object!!
            startPosition.copy(obj.position)
            startRotation.copy(obj.quaternion)
            startScale.copy(obj.scale)
            
            dispatchEvent(TransformEvent.DRAG_START)
            return true
        }

        return false
    }

    /**
     * Handle pointer move event
     * @param x Normalized x coordinate (-1 to 1)
     * @param y Normalized y coordinate (-1 to 1)
     */
    fun onPointerMove(x: Float, y: Float): Boolean {
        if (!enabled) return false

        pointerCurrent.set(x, y)

        if (isDragging && activeAxis != TransformAxis.NONE) {
            applyTransform()
            dispatchEvent(TransformEvent.CHANGE)
            return true
        } else {
            // Update hover state for visual feedback
            val hitAxis = raycastGizmo(x, y)
            if (hitAxis != hoverAxis) {
                hoverAxis = hitAxis
                updateGizmo()
            }
        }

        return false
    }

    /**
     * Handle pointer up event
     */
    fun onPointerUp(): Boolean {
        if (!enabled || !isDragging) return false

        isDragging = false
        activeAxis = TransformAxis.NONE
        dispatchEvent(TransformEvent.DRAG_END)
        return true
    }

    /**
     * Set transformation mode by key press
     */
    fun setModeByKey(key: String) {
        when (key.lowercase()) {
            "g", "w" -> mode = TransformMode.TRANSLATE
            "r" -> mode = TransformMode.ROTATE
            "s" -> mode = TransformMode.SCALE
        }
    }

    /**
     * Toggle between world and local space
     */
    fun toggleSpace() {
        // Toggle would need mutable space property
    }

    // --- Private Methods ---

    private fun raycastGizmo(x: Float, y: Float): TransformAxis {
        val obj = _object ?: return TransformAxis.NONE

        // Setup ray from camera through pointer position
        setupRayFromPointer(x, y)

        // Check intersections with gizmo handles
        // For each axis, create a virtual hitbox and test intersection
        
        val cameraDistance = camera.position.distanceTo(worldPosition)
        val handleSize = size * 0.15f * cameraDistance / 10f

        // Test each axis
        if (config.showX && intersectsAxisHandle(TransformAxis.X, handleSize)) return TransformAxis.X
        if (config.showY && intersectsAxisHandle(TransformAxis.Y, handleSize)) return TransformAxis.Y
        if (config.showZ && intersectsAxisHandle(TransformAxis.Z, handleSize)) return TransformAxis.Z

        // Test plane handles for translate mode
        if (mode == TransformMode.TRANSLATE) {
            if (config.showX && config.showY && intersectsPlaneHandle(TransformAxis.XY, handleSize)) return TransformAxis.XY
            if (config.showX && config.showZ && intersectsPlaneHandle(TransformAxis.XZ, handleSize)) return TransformAxis.XZ
            if (config.showY && config.showZ && intersectsPlaneHandle(TransformAxis.YZ, handleSize)) return TransformAxis.YZ
        }

        return TransformAxis.NONE
    }

    private fun setupRayFromPointer(x: Float, y: Float) {
        // Convert NDC to ray direction
        val direction = Vector3(x, y, 0.5f).unproject(camera)
        direction.sub(camera.position).normalize()
        
        ray.origin.copy(camera.position)
        ray.direction.copy(direction)
    }

    private fun intersectsAxisHandle(axis: TransformAxis, handleSize: Float): Boolean {
        val axisDirection = getAxisDirection(axis)
        val axisEnd = worldPosition.clone().add(axisDirection.multiplyScalar(size))
        
        // Simple distance-to-line test
        return rayDistanceToLine(worldPosition, axisEnd) < handleSize
    }

    private fun intersectsPlaneHandle(plane: TransformAxis, handleSize: Float): Boolean {
        val planeSize = handleSize * 2f
        // Simplified plane intersection test
        return false  // TODO: Implement proper plane intersection
    }

    private fun getAxisDirection(axis: TransformAxis): Vector3 {
        return when (axis) {
            TransformAxis.X -> Vector3(1f, 0f, 0f)
            TransformAxis.Y -> Vector3(0f, 1f, 0f)
            TransformAxis.Z -> Vector3(0f, 0f, 1f)
            else -> Vector3()
        }
    }

    private fun rayDistanceToLine(lineStart: Vector3, lineEnd: Vector3): Float {
        val lineDir = lineEnd.clone().sub(lineStart)
        val lineLength = lineDir.length()
        lineDir.normalize()

        val toStart = ray.origin.clone().sub(lineStart)
        val cross = ray.direction.clone().cross(lineDir)
        val crossLen = cross.length()

        if (crossLen < 0.0001f) {
            // Ray is parallel to line
            return toStart.clone().cross(ray.direction).length()
        }

        return abs(toStart.dot(cross)) / crossLen
    }

    private fun applyTransform() {
        val obj = _object ?: return
        
        val deltaX = pointerCurrent.x - pointerStart.x
        val deltaY = pointerCurrent.y - pointerStart.y

        when (mode) {
            TransformMode.TRANSLATE -> applyTranslation(deltaX, deltaY)
            TransformMode.ROTATE -> applyRotation(deltaX, deltaY)
            TransformMode.SCALE -> applyScale(deltaX, deltaY)
        }
    }

    private fun applyTranslation(deltaX: Float, deltaY: Float) {
        val obj = _object ?: return
        
        // Get camera-relative axes
        val cameraDistance = camera.position.distanceTo(worldPosition)
        val movementScale = cameraDistance * 0.5f

        val movement = Vector3()
        
        when (activeAxis) {
            TransformAxis.X -> {
                val worldX = getWorldAxisDirection(TransformAxis.X)
                val screenMovement = projectAxisToScreen(worldX)
                val dot = deltaX * screenMovement.x + deltaY * screenMovement.y
                movement.copy(worldX).multiplyScalar(dot * movementScale)
            }
            TransformAxis.Y -> {
                val worldY = getWorldAxisDirection(TransformAxis.Y)
                val screenMovement = projectAxisToScreen(worldY)
                val dot = deltaX * screenMovement.x + deltaY * screenMovement.y
                movement.copy(worldY).multiplyScalar(dot * movementScale)
            }
            TransformAxis.Z -> {
                val worldZ = getWorldAxisDirection(TransformAxis.Z)
                val screenMovement = projectAxisToScreen(worldZ)
                val dot = deltaX * screenMovement.x + deltaY * screenMovement.y
                movement.copy(worldZ).multiplyScalar(dot * movementScale)
            }
            TransformAxis.XY -> {
                movement.x = deltaX * movementScale
                movement.y = -deltaY * movementScale
            }
            TransformAxis.XZ -> {
                movement.x = deltaX * movementScale
                movement.z = deltaY * movementScale
            }
            TransformAxis.YZ -> {
                movement.y = -deltaY * movementScale
                movement.z = deltaX * movementScale
            }
            else -> {}
        }

        // Apply snapping if configured
        config.translationSnap?.let { snap ->
            movement.x = snapValue(movement.x, snap)
            movement.y = snapValue(movement.y, snap)
            movement.z = snapValue(movement.z, snap)
        }

        obj.position.copy(startPosition).add(movement)
    }

    private fun applyRotation(deltaX: Float, deltaY: Float) {
        val obj = _object ?: return
        
        val rotationSpeed = 3f
        var angle = 0f
        
        when (activeAxis) {
            TransformAxis.X -> {
                angle = deltaY * rotationSpeed
                val rotation = Quaternion().setFromAxisAngle(Vector3(1f, 0f, 0f), angle)
                obj.quaternion.copy(startRotation).premultiply(rotation)
            }
            TransformAxis.Y -> {
                angle = deltaX * rotationSpeed
                val rotation = Quaternion().setFromAxisAngle(Vector3(0f, 1f, 0f), angle)
                obj.quaternion.copy(startRotation).premultiply(rotation)
            }
            TransformAxis.Z -> {
                angle = (deltaX + deltaY) * rotationSpeed * 0.5f
                val rotation = Quaternion().setFromAxisAngle(Vector3(0f, 0f, 1f), angle)
                obj.quaternion.copy(startRotation).premultiply(rotation)
            }
            else -> {}
        }

        // Apply snapping if configured
        config.rotationSnap?.let { snap ->
            // Snap to increments
        }
    }

    private fun applyScale(deltaX: Float, deltaY: Float) {
        val obj = _object ?: return
        
        val scaleSpeed = 2f
        val scaleDelta = 1f + (deltaX + deltaY) * scaleSpeed * 0.5f
        
        when (activeAxis) {
            TransformAxis.X -> {
                obj.scale.copy(startScale)
                obj.scale.x *= scaleDelta
            }
            TransformAxis.Y -> {
                obj.scale.copy(startScale)
                obj.scale.y *= scaleDelta
            }
            TransformAxis.Z -> {
                obj.scale.copy(startScale)
                obj.scale.z *= scaleDelta
            }
            TransformAxis.XYZ -> {
                obj.scale.copy(startScale).multiplyScalar(scaleDelta)
            }
            else -> {
                obj.scale.copy(startScale).multiplyScalar(scaleDelta)
            }
        }

        // Apply snapping if configured
        config.scaleSnap?.let { snap ->
            obj.scale.x = snapValue(obj.scale.x, snap)
            obj.scale.y = snapValue(obj.scale.y, snap)
            obj.scale.z = snapValue(obj.scale.z, snap)
        }
    }

    private fun getWorldAxisDirection(axis: TransformAxis): Vector3 {
        val direction = getAxisDirection(axis)
        
        // If local space, transform by object's world quaternion
        if (config.space == TransformSpace.LOCAL) {
            direction.applyQuaternion(worldQuaternion)
        }
        
        return direction
    }

    private fun projectAxisToScreen(worldAxis: Vector3): Vector2 {
        // Project world axis to screen space for proper interaction
        val axisEnd = worldPosition.clone().add(worldAxis)
        
        val screenStart = worldPosition.clone().project(camera)
        val screenEnd = axisEnd.project(camera)
        
        return Vector2(
            screenEnd.x - screenStart.x,
            screenEnd.y - screenStart.y
        ).normalize()
    }

    private fun snapValue(value: Float, snap: Float): Float {
        return kotlin.math.round(value / snap) * snap
    }

    private fun updateGizmo() {
        // Update gizmo meshes based on current mode, axis, etc.
        // This would update visual representations
    }

    // --- Event System ---

    enum class TransformEvent {
        ATTACHED,
        DETACHED,
        DRAG_START,
        DRAG_END,
        CHANGE
    }

    interface TransformControlsListener {
        fun onTransformEvent(event: TransformEvent, controls: TransformControls) {}
    }

    fun addEventListener(listener: TransformControlsListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: TransformControlsListener) {
        listeners.remove(listener)
    }

    private fun dispatchEvent(event: TransformEvent) {
        listeners.forEach { it.onTransformEvent(event, this) }
    }

    // --- Gizmo Handle Data ---

    private data class GizmoHandle(
        val axis: TransformAxis,
        val mode: TransformMode,
        val mesh: Mesh? = null
    )
}

// Extension functions for Vector3
private fun Vector3.project(camera: Camera): Vector3 {
    return this.applyMatrix4(camera.matrixWorldInverse).applyMatrix4(camera.projectionMatrix)
}

private fun Vector3.unproject(camera: Camera): Vector3 {
    val matrix = camera.projectionMatrix.clone().invert()
    matrix.premultiply(camera.matrixWorld)
    return this.applyMatrix4(matrix)
}
