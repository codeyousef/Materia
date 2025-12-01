package io.materia.controls

import io.materia.camera.Camera
import io.materia.core.math.Plane
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.scene.Intersection
import io.materia.core.scene.Object3D
import io.materia.core.scene.Raycaster

/**
 * Drag controls for interactively moving objects with mouse/touch.
 * 
 * Allows picking and dragging Object3D instances in 3D space. The dragging
 * occurs on a plane parallel to the camera. Similar to Three.js DragControls.
 *
 * Usage:
 * ```kotlin
 * val controls = DragControls(objects, camera)
 * 
 * // Handle events
 * controls.addEventListener(object : DragControlsListener {
 *     override fun onDragStart(obj: Object3D) { println("Started dragging: ${obj.name}") }
 *     override fun onDrag(obj: Object3D) { println("Dragging: ${obj.position}") }
 *     override fun onDragEnd(obj: Object3D) { println("Stopped dragging") }
 * })
 * 
 * // In event handlers
 * controls.onPointerDown(event.x, event.y)
 * controls.onPointerMove(event.x, event.y)
 * controls.onPointerUp()
 * ```
 */
class DragControls(
    private val objects: List<Object3D>,
    private val camera: Camera,
    config: DragControlsConfig = DragControlsConfig()
) {

    /**
     * Configuration for drag controls
     */
    data class DragControlsConfig(
        val transformGroup: Boolean = false,
        val recursive: Boolean = true,
        val mode: DragMode = DragMode.CAMERA_PLANE
    )

    /**
     * Drag mode determines how dragging is calculated
     */
    enum class DragMode {
        /** Drag on a plane parallel to camera (default) */
        CAMERA_PLANE,
        /** Drag on the XZ plane (horizontal) */
        HORIZONTAL,
        /** Drag on the XY plane (vertical, facing camera) */
        VERTICAL
    }

    // Configuration
    private val config = config

    /** Enable/disable the controls */
    var enabled: Boolean = true

    /** Whether any object is currently being dragged */
    val isDragging: Boolean get() = _selected != null

    /** Currently hovered object */
    val hoveredObject: Object3D? get() = _hovered

    /** Currently selected/dragged object */
    val selectedObject: Object3D? get() = _selected

    // State
    private var _hovered: Object3D? = null
    private var _selected: Object3D? = null
    
    // Raycasting
    private val raycaster = Raycaster()
    private val pointer = Vector2()
    
    // Drag calculation
    private val plane = Plane()
    private val offset = Vector3()
    private val intersection = Vector3()
    private val worldPosition = Vector3()
    
    // Event listeners
    private val listeners = mutableListOf<DragControlsListener>()

    /**
     * Activate drag controls and set up initial state
     */
    fun activate() {
        enabled = true
    }

    /**
     * Deactivate drag controls
     */
    fun deactivate() {
        enabled = false
        _hovered = null
        _selected = null
    }

    /**
     * Get list of draggable objects
     */
    fun getObjects(): List<Object3D> = objects

    /**
     * Handle pointer down event
     * @param x Normalized x coordinate (-1 to 1)
     * @param y Normalized y coordinate (-1 to 1)
     */
    fun onPointerDown(x: Float, y: Float): Boolean {
        if (!enabled) return false

        pointer.set(x, y)
        updateRaycaster()

        val intersects = raycaster.intersectObject(
            objects.first().parent ?: objects.first(),
            config.recursive
        ).filter { objects.contains(it.`object`) || isDescendant(it.`object`, objects) }

        if (intersects.isNotEmpty()) {
            val intersect = intersects.first()
            
            // Find the actual draggable object
            _selected = if (config.transformGroup) {
                findDraggableAncestor(intersect.`object`) ?: intersect.`object`
            } else {
                intersect.`object`
            }

            // Set up drag plane
            setupDragPlane()
            
            // Calculate offset from intersection point to object center
            if (raycastPlane(intersection)) {
                offset.copy(intersection).sub(_selected!!.position)
            }

            dispatchEvent(DragEvent.DRAG_START)
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

        pointer.set(x, y)
        updateRaycaster()

        if (_selected != null) {
            // Dragging
            if (raycastPlane(intersection)) {
                _selected!!.position.copy(intersection.sub(offset))
            }
            dispatchEvent(DragEvent.DRAG)
            return true
        } else {
            // Hovering
            val intersects = raycaster.intersectObject(
                objects.first().parent ?: objects.first(),
                config.recursive
            ).filter { objects.contains(it.`object`) || isDescendant(it.`object`, objects) }

            val newHovered = if (intersects.isNotEmpty()) {
                if (config.transformGroup) {
                    findDraggableAncestor(intersects.first().`object`) ?: intersects.first().`object`
                } else {
                    intersects.first().`object`
                }
            } else {
                null
            }

            if (newHovered != _hovered) {
                _hovered?.let { dispatchHoverEvent(it, false) }
                _hovered = newHovered
                _hovered?.let { dispatchHoverEvent(it, true) }
            }
        }

        return false
    }

    /**
     * Handle pointer up event
     */
    fun onPointerUp(): Boolean {
        if (!enabled || _selected == null) return false

        dispatchEvent(DragEvent.DRAG_END)
        _selected = null
        return true
    }

    // --- Private Methods ---

    private fun updateRaycaster() {
        raycaster.setFromCamera(pointer, camera)
    }

    private fun setupDragPlane() {
        val selected = _selected ?: return
        
        when (config.mode) {
            DragMode.CAMERA_PLANE -> {
                // Create plane facing camera at object position
                selected.getWorldPosition(worldPosition)
                val normal = camera.getWorldDirection(Vector3())
                plane.setFromNormalAndCoplanarPoint(normal, worldPosition)
            }
            DragMode.HORIZONTAL -> {
                // XZ plane (horizontal ground plane)
                selected.getWorldPosition(worldPosition)
                plane.setFromNormalAndCoplanarPoint(Vector3(0f, 1f, 0f), worldPosition)
            }
            DragMode.VERTICAL -> {
                // XY plane facing camera
                selected.getWorldPosition(worldPosition)
                val normal = Vector3(0f, 0f, 1f)
                plane.setFromNormalAndCoplanarPoint(normal, worldPosition)
            }
        }
    }

    private fun raycastPlane(target: Vector3): Boolean {
        val ray = raycaster.ray
        val distance = ray.distanceToPlane(plane)
        
        if (distance == null || distance < 0) {
            return false
        }

        target.copy(ray.direction).multiplyScalar(distance).add(ray.origin)
        return true
    }

    private fun isDescendant(obj: Object3D, potentialAncestors: List<Object3D>): Boolean {
        var current: Object3D? = obj.parent
        while (current != null) {
            if (potentialAncestors.contains(current)) return true
            current = current.parent
        }
        return false
    }

    private fun findDraggableAncestor(obj: Object3D): Object3D? {
        var current: Object3D? = obj
        while (current != null) {
            if (objects.contains(current)) return current
            current = current.parent
        }
        return null
    }

    // --- Event System ---

    enum class DragEvent {
        DRAG_START,
        DRAG,
        DRAG_END,
        HOVER_ON,
        HOVER_OFF
    }

    interface DragControlsListener {
        fun onDragStart(obj: Object3D) {}
        fun onDrag(obj: Object3D) {}
        fun onDragEnd(obj: Object3D) {}
        fun onHoverOn(obj: Object3D) {}
        fun onHoverOff(obj: Object3D) {}
    }

    fun addEventListener(listener: DragControlsListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: DragControlsListener) {
        listeners.remove(listener)
    }

    private fun dispatchEvent(event: DragEvent) {
        val obj = _selected ?: return
        when (event) {
            DragEvent.DRAG_START -> listeners.forEach { it.onDragStart(obj) }
            DragEvent.DRAG -> listeners.forEach { it.onDrag(obj) }
            DragEvent.DRAG_END -> listeners.forEach { it.onDragEnd(obj) }
            else -> {}
        }
    }

    private fun dispatchHoverEvent(obj: Object3D, isOn: Boolean) {
        if (isOn) {
            listeners.forEach { it.onHoverOn(obj) }
        } else {
            listeners.forEach { it.onHoverOff(obj) }
        }
    }

    /**
     * Dispose of the controls
     */
    fun dispose() {
        deactivate()
        listeners.clear()
    }
}

// Extension functions for Raycaster
private fun Raycaster.setFromCamera(coords: Vector2, camera: Camera) {
    // Set up ray from camera through the given screen coordinates
    val origin = camera.position.clone()
    val direction = Vector3(coords.x, coords.y, 0.5f)
        .unproject(camera)
        .sub(origin)
        .normalize()
    
    ray.origin.copy(origin)
    ray.direction.copy(direction)
}

// Extension for Vector3 unproject
private fun Vector3.unproject(camera: Camera): Vector3 {
    val matrix = camera.projectionMatrix.clone().invert()
    matrix.premultiply(camera.matrixWorld)
    return this.applyMatrix4(matrix)
}

// Extension for ray distance to plane
private fun io.materia.core.scene.Ray.distanceToPlane(plane: Plane): Float? {
    val denominator = plane.normal.dot(direction)
    if (kotlin.math.abs(denominator) < 0.0001f) {
        // Ray is parallel to plane
        return null
    }
    
    val t = -(origin.dot(plane.normal) + plane.constant) / denominator
    return if (t >= 0) t else null
}
