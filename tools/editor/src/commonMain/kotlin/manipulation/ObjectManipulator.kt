@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.materia.tools.editor.manipulation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/**
 * ObjectManipulator - Handles 3D object manipulation operations in the scene editor
 *
 * This class provides comprehensive object manipulation capabilities including:
 * - Transform operations (translate, rotate, scale)
 * - Multi-object selection and group operations
 * - Snap-to-grid and constraint-based movement
 * - Gizmo-based visual manipulation
 * - Undo/redo support for all operations
 * - Collision detection and bounds checking
 * - Hierarchical transformations with parent-child relationships
 */
class ObjectManipulator {

    // State management
    private val _selectedObjects = MutableStateFlow<List<String>>(emptyList())
    val selectedObjects: StateFlow<List<String>> = _selectedObjects.asStateFlow()

    private val _transformMode = MutableStateFlow(TransformMode.GLOBAL)
    val transformMode: StateFlow<TransformMode> = _transformMode.asStateFlow()

    private val _activeGizmo = MutableStateFlow<GizmoType?>(null)
    val activeGizmo: StateFlow<GizmoType?> = _activeGizmo.asStateFlow()

    private val _isManipulating = MutableStateFlow(false)
    val isManipulating: StateFlow<Boolean> = _isManipulating.asStateFlow()

    // Configuration
    var snapToGrid: Boolean = false
    var gridSize: Float = 1.0f
    var enableCollisionDetection: Boolean = false
    var enableConstraints: Boolean = true

    // Internal state
    private var manipulationStart: ManipulationStartState? = null
    private var currentProject: SceneEditorProject? = null
    private val transformHistory = mutableListOf<TransformOperation>()
    private var historyIndex = -1

    // Callbacks
    var onObjectsChanged: ((List<SerializedObject3D>) -> Unit)? = null
    var onSelectionChanged: ((List<String>) -> Unit)? = null
    var onTransformStarted: ((ManipulationType) -> Unit)? = null
    var onTransformCompleted: ((TransformOperation) -> Unit)? = null

    /**
     * Initialize with a scene project
     */
    fun initialize(project: SceneEditorProject) {
        currentProject = project
        clearSelection()
    }

    // Selection Management

    /**
     * Select a single object
     */
    fun selectObject(objectId: String) {
        selectObjects(listOf(objectId))
    }

    /**
     * Select multiple objects
     */
    fun selectObjects(objectIds: List<String>) {
        val validIds = objectIds.filter { id ->
            currentProject?.scene?.objects?.any { it.id == id } == true
        }

        _selectedObjects.value = validIds
        onSelectionChanged?.invoke(validIds)

        // Update gizmo position
        updateGizmoPosition()
    }

    /**
     * Add objects to current selection
     */
    fun addToSelection(objectIds: List<String>) {
        val currentSelection = _selectedObjects.value.toMutableList()
        val newIds = objectIds.filter { it !in currentSelection }
        currentSelection.addAll(newIds)
        selectObjects(currentSelection)
    }

    /**
     * Remove objects from current selection
     */
    fun removeFromSelection(objectIds: List<String>) {
        val currentSelection = _selectedObjects.value.toMutableList()
        currentSelection.removeAll(objectIds.toSet())
        selectObjects(currentSelection)
    }

    /**
     * Clear selection
     */
    fun clearSelection() {
        _selectedObjects.value = emptyList()
        _activeGizmo.value = null
        onSelectionChanged?.invoke(emptyList())
    }

    /**
     * Select all objects in the scene
     */
    fun selectAll() {
        val allIds = currentProject?.scene?.objects?.map { it.id } ?: emptyList()
        selectObjects(allIds)
    }

    /**
     * Invert current selection
     */
    fun invertSelection() {
        val allIds = currentProject?.scene?.objects?.map { it.id } ?: emptyList()
        val currentSelection = _selectedObjects.value
        val invertedSelection = allIds.filter { it !in currentSelection }
        selectObjects(invertedSelection)
    }

    // Transform Operations

    /**
     * Start transformation operation
     */
    fun startTransformation(
        type: ManipulationType,
        startPosition: Vector3? = null,
        constraintAxis: Axis? = null
    ): Result<Unit> {
        if (_selectedObjects.value.isEmpty()) {
            return Result.failure(IllegalStateException("No objects selected"))
        }

        val selectedObjs = getSelectedObjects()
        if (selectedObjs.isEmpty()) {
            return Result.failure(IllegalStateException("Selected objects not found"))
        }

        manipulationStart = ManipulationStartState(
            type = type,
            objects = selectedObjs.map { it.id to it.transform }.toMap(),
            pivot = calculatePivotPoint(selectedObjs),
            startPosition = startPosition ?: Vector3.ZERO,
            constraintAxis = constraintAxis
        )

        _isManipulating.value = true
        onTransformStarted?.invoke(type)

        return Result.success(Unit)
    }

    /**
     * Update transformation during manipulation
     */
    fun updateTransformation(
        currentPosition: Vector3,
        delta: Vector3,
        modifier: TransformModifier = TransformModifier.NONE
    ): Result<List<SerializedObject3D>> {
        val startState = manipulationStart
            ?: return Result.failure(IllegalStateException("No transformation in progress"))

        val transformedObjects = mutableListOf<SerializedObject3D>()

        for (objId in _selectedObjects.value) {
            val originalTransform = startState.objects[objId]
                ?: continue

            val newTransform = when (startState.type) {
                ManipulationType.TRANSLATE -> {
                    calculateTranslation(
                        originalTransform,
                        delta,
                        startState.constraintAxis,
                        modifier
                    )
                }
                ManipulationType.ROTATE -> {
                    calculateRotation(
                        originalTransform,
                        delta,
                        startState.pivot,
                        startState.constraintAxis,
                        modifier
                    )
                }
                ManipulationType.SCALE -> {
                    calculateScale(
                        originalTransform,
                        delta,
                        startState.pivot,
                        startState.constraintAxis,
                        modifier
                    )
                }
            }

            // Apply constraints and snapping
            val constrainedTransform = applyConstraints(newTransform, startState.constraintAxis)
            val snappedTransform = if (snapToGrid) applyGridSnapping(constrainedTransform) else constrainedTransform

            // Find and update the object
            val obj = currentProject?.scene?.objects?.find { it.id == objId }
            if (obj != null) {
                val updatedObj = obj.copy(transform = snappedTransform)
                transformedObjects.add(updatedObj)
            }
        }

        // Check for collisions if enabled
        if (enableCollisionDetection) {
            val collisions = detectCollisions(transformedObjects)
            if (collisions.isNotEmpty()) {
                return Result.failure(CollisionException("Objects would collide: ${collisions.joinToString()}"))
            }
        }

        return Result.success(transformedObjects)
    }

    /**
     * Complete transformation operation
     */
    fun completeTransformation(): Result<TransformOperation> {
        val startState = manipulationStart
            ?: return Result.failure(IllegalStateException("No transformation in progress"))

        val finalObjects = getSelectedObjects()
        val operation = TransformOperation(
            type = startState.type,
            objectIds = _selectedObjects.value,
            beforeTransforms = startState.objects,
            afterTransforms = finalObjects.associate { it.id to it.transform },
            timestamp = kotlinx.datetime.Clock.System.now()
        )

        // Add to history
        addToHistory(operation)

        // Update project
        updateObjectsInProject(finalObjects)

        // Clean up
        manipulationStart = null
        _isManipulating.value = false

        onTransformCompleted?.invoke(operation)

        return Result.success(operation)
    }

    /**
     * Cancel current transformation
     */
    fun cancelTransformation(): Result<Unit> {
        val startState = manipulationStart
            ?: return Result.failure(IllegalStateException("No transformation in progress"))

        // Restore original transforms
        val restoredObjects = mutableListOf<SerializedObject3D>()
        for (objId in _selectedObjects.value) {
            val originalTransform = startState.objects[objId] ?: continue
            val obj = currentProject?.scene?.objects?.find { it.id == objId }
            if (obj != null) {
                restoredObjects.add(obj.copy(transform = originalTransform))
            }
        }

        updateObjectsInProject(restoredObjects)

        // Clean up
        manipulationStart = null
        _isManipulating.value = false

        return Result.success(Unit)
    }

    // Specific Transform Operations

    /**
     * Translate objects by a delta vector
     */
    fun translateObjects(
        objectIds: List<String>,
        delta: Vector3,
        relativeTo: TransformMode = _transformMode.value
    ): Result<List<SerializedObject3D>> {
        return executeTransformOperation(objectIds) { obj ->
            val adjustedDelta = when (relativeTo) {
                TransformMode.GLOBAL -> delta
                TransformMode.LOCAL -> transformVectorToLocal(delta, obj.transform.rotation)
                TransformMode.VIEW -> delta // View space would need camera info
            }

            obj.copy(
                transform = obj.transform.copy(
                    position = obj.transform.position + adjustedDelta
                )
            )
        }
    }

    /**
     * Rotate objects by Euler angles
     */
    fun rotateObjects(
        objectIds: List<String>,
        rotation: Vector3,
        pivot: Vector3? = null,
        relativeTo: TransformMode = _transformMode.value
    ): Result<List<SerializedObject3D>> {
        val effectivePivot = pivot ?: calculatePivotPoint(getObjectsById(objectIds))

        return executeTransformOperation(objectIds) { obj ->
            val adjustedRotation = when (relativeTo) {
                TransformMode.GLOBAL -> rotation
                TransformMode.LOCAL -> rotation
                TransformMode.VIEW -> rotation
            }

            // Rotate around pivot
            val newTransform = rotateAroundPivot(obj.transform, adjustedRotation, effectivePivot)
            obj.copy(transform = newTransform)
        }
    }

    /**
     * Scale objects by a scale factor
     */
    fun scaleObjects(
        objectIds: List<String>,
        scale: Vector3,
        pivot: Vector3? = null,
        uniform: Boolean = false
    ): Result<List<SerializedObject3D>> {
        val effectivePivot = pivot ?: calculatePivotPoint(getObjectsById(objectIds))
        val effectiveScale = if (uniform) {
            val avgScale = (scale.x + scale.y + scale.z) / 3f
            Vector3(avgScale, avgScale, avgScale)
        } else scale

        return executeTransformOperation(objectIds) { obj ->
            val newTransform = scaleAroundPivot(obj.transform, effectiveScale, effectivePivot)
            obj.copy(transform = newTransform)
        }
    }

    /**
     * Set absolute position for objects
     */
    fun setObjectsPosition(objectIds: List<String>, position: Vector3): Result<List<SerializedObject3D>> {
        return executeTransformOperation(objectIds) { obj ->
            obj.copy(transform = obj.transform.copy(position = position))
        }
    }

    /**
     * Set absolute rotation for objects
     */
    fun setObjectsRotation(objectIds: List<String>, rotation: Vector3): Result<List<SerializedObject3D>> {
        return executeTransformOperation(objectIds) { obj ->
            obj.copy(transform = obj.transform.copy(rotation = rotation))
        }
    }

    /**
     * Set absolute scale for objects
     */
    fun setObjectsScale(objectIds: List<String>, scale: Vector3): Result<List<SerializedObject3D>> {
        return executeTransformOperation(objectIds) { obj ->
            obj.copy(transform = obj.transform.copy(scale = scale))
        }
    }

    // Utility Operations

    /**
     * Duplicate selected objects
     */
    fun duplicateSelectedObjects(offset: Vector3 = Vector3(1f, 0f, 0f)): Result<List<String>> {
        val selectedObjs = getSelectedObjects()
        if (selectedObjs.isEmpty()) {
            return Result.failure(IllegalStateException("No objects selected"))
        }

        val duplicatedObjects = mutableListOf<SerializedObject3D>()
        val newIds = mutableListOf<String>()

        for (obj in selectedObjs) {
            val newId = generateUniqueId()
            val newObj = obj.copy(
                id = newId,
                name = "${obj.name} Copy",
                transform = obj.transform.copy(
                    position = obj.transform.position + offset
                )
            )
            duplicatedObjects.add(newObj)
            newIds.add(newId)
        }

        // Add to project
        val updatedObjects = (currentProject?.scene?.objects ?: emptyList()) + duplicatedObjects
        updateProjectObjects(updatedObjects)

        // Select the new objects
        selectObjects(newIds)

        return Result.success(newIds)
    }

    /**
     * Delete selected objects
     */
    fun deleteSelectedObjects(): Result<List<String>> {
        val selectedIds = _selectedObjects.value
        if (selectedIds.isEmpty()) {
            return Result.failure(IllegalStateException("No objects selected"))
        }

        val remainingObjects = currentProject?.scene?.objects?.filter { it.id !in selectedIds } ?: emptyList()
        updateProjectObjects(remainingObjects)

        clearSelection()

        return Result.success(selectedIds)
    }

    /**
     * Group selected objects
     */
    fun groupSelectedObjects(groupName: String = "Group"): Result<String> {
        val selectedObjs = getSelectedObjects()
        if (selectedObjs.size < 2) {
            return Result.failure(IllegalStateException("Need at least 2 objects to group"))
        }

        val groupId = generateUniqueId()
        val groupPivot = calculatePivotPoint(selectedObjs)

        // Create group object
        val groupObject = SerializedObject3D(
            id = groupId,
            name = groupName,
            type = ObjectType.GROUP,
            transform = Transform3D(groupPivot, Vector3.ZERO, Vector3.ONE),
            children = selectedObjs,
            geometryData = GeometryData(GeometryType.CUSTOM)
        )

        // Remove original objects from scene and add group
        val remainingObjects = currentProject?.scene?.objects?.filter { it.id !in _selectedObjects.value } ?: emptyList()
        val updatedObjects = remainingObjects + groupObject

        updateProjectObjects(updatedObjects)
        selectObject(groupId)

        return Result.success(groupId)
    }

    /**
     * Ungroup a group object
     */
    fun ungroupObject(groupId: String): Result<List<String>> {
        val groupObj = getObjectById(groupId)
            ?: return Result.failure(IllegalArgumentException("Group object not found"))

        if (groupObj.type != ObjectType.GROUP) {
            return Result.failure(IllegalArgumentException("Object is not a group"))
        }

        val childIds = groupObj.children.map { it.id }

        // Remove group and add children to scene
        val remainingObjects = currentProject?.scene?.objects?.filter { it.id != groupId } ?: emptyList()
        val updatedObjects = remainingObjects + groupObj.children

        updateProjectObjects(updatedObjects)
        selectObjects(childIds)

        return Result.success(childIds)
    }

    /**
     * Reset transform of selected objects
     */
    fun resetTransform(resetPosition: Boolean = true, resetRotation: Boolean = true, resetScale: Boolean = true): Result<List<SerializedObject3D>> {
        return executeTransformOperation(_selectedObjects.value) { obj ->
            val newTransform = Transform3D(
                position = if (resetPosition) Vector3.ZERO else obj.transform.position,
                rotation = if (resetRotation) Vector3.ZERO else obj.transform.rotation,
                scale = if (resetScale) Vector3.ONE else obj.transform.scale
            )
            obj.copy(transform = newTransform)
        }
    }

    // Gizmo Management

    /**
     * Set transform mode (Global/Local/View)
     */
    fun setTransformMode(mode: TransformMode) {
        _transformMode.value = mode
        updateGizmoPosition()
    }

    /**
     * Show manipulation gizmo
     */
    fun showGizmo(type: GizmoType) {
        _activeGizmo.value = type
        updateGizmoPosition()
    }

    /**
     * Hide manipulation gizmo
     */
    fun hideGizmo() {
        _activeGizmo.value = null
    }

    // History Management

    /**
     * Undo last transformation
     */
    fun undo(): Result<Unit> {
        if (historyIndex < 0) {
            return Result.failure(IllegalStateException("Nothing to undo"))
        }

        val operation = transformHistory[historyIndex]

        // Restore previous transforms
        val restoredObjects = mutableListOf<SerializedObject3D>()
        for (objId in operation.objectIds) {
            val beforeTransform = operation.beforeTransforms[objId] ?: continue
            val obj = getObjectById(objId)
            if (obj != null) {
                restoredObjects.add(obj.copy(transform = beforeTransform))
            }
        }

        updateObjectsInProject(restoredObjects)
        historyIndex--

        return Result.success(Unit)
    }

    /**
     * Redo next transformation
     */
    fun redo(): Result<Unit> {
        if (historyIndex + 1 >= transformHistory.size) {
            return Result.failure(IllegalStateException("Nothing to redo"))
        }

        historyIndex++
        val operation = transformHistory[historyIndex]

        // Restore after transforms
        val restoredObjects = mutableListOf<SerializedObject3D>()
        for (objId in operation.objectIds) {
            val afterTransform = operation.afterTransforms[objId] ?: continue
            val obj = getObjectById(objId)
            if (obj != null) {
                restoredObjects.add(obj.copy(transform = afterTransform))
            }
        }

        updateObjectsInProject(restoredObjects)

        return Result.success(Unit)
    }

    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = historyIndex >= 0

    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = historyIndex + 1 < transformHistory.size

    /**
     * Clear transformation history
     */
    fun clearHistory() {
        transformHistory.clear()
        historyIndex = -1
    }

    // Private helper methods

    private fun getSelectedObjects(): List<SerializedObject3D> {
        return _selectedObjects.value.mapNotNull { id ->
            currentProject?.scene?.objects?.find { it.id == id }
        }
    }

    private fun getObjectById(id: String): SerializedObject3D? {
        return currentProject?.scene?.objects?.find { it.id == id }
    }

    private fun getObjectsById(ids: List<String>): List<SerializedObject3D> {
        return ids.mapNotNull { getObjectById(it) }
    }

    private fun executeTransformOperation(
        objectIds: List<String>,
        transform: (SerializedObject3D) -> SerializedObject3D
    ): Result<List<SerializedObject3D>> {
        val objects = getObjectsById(objectIds)
        if (objects.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid objects found"))
        }

        val transformedObjects = objects.map(transform)
        updateObjectsInProject(transformedObjects)

        return Result.success(transformedObjects)
    }

    private fun calculatePivotPoint(objects: List<SerializedObject3D>): Vector3 {
        if (objects.isEmpty()) return Vector3.ZERO

        val sum = objects.fold(Vector3.ZERO) { acc, obj ->
            acc + obj.transform.position
        }

        return Vector3(
            sum.x / objects.size,
            sum.y / objects.size,
            sum.z / objects.size
        )
    }

    private fun calculateTranslation(
        originalTransform: Transform3D,
        delta: Vector3,
        constraintAxis: Axis?,
        modifier: TransformModifier
    ): Transform3D {
        val adjustedDelta = when (constraintAxis) {
            Axis.X -> Vector3(delta.x, 0f, 0f)
            Axis.Y -> Vector3(0f, delta.y, 0f)
            Axis.Z -> Vector3(0f, 0f, delta.z)
            null -> delta
        }

        val finalDelta = when (modifier) {
            TransformModifier.PRECISION -> adjustedDelta * 0.1f
            TransformModifier.FAST -> adjustedDelta * 10f
            TransformModifier.NONE -> adjustedDelta
        }

        return originalTransform.copy(
            position = originalTransform.position + finalDelta
        )
    }

    private fun calculateRotation(
        originalTransform: Transform3D,
        delta: Vector3,
        pivot: Vector3,
        constraintAxis: Axis?,
        modifier: TransformModifier
    ): Transform3D {
        val rotationDelta = when (constraintAxis) {
            Axis.X -> Vector3(delta.x, 0f, 0f)
            Axis.Y -> Vector3(0f, delta.y, 0f)
            Axis.Z -> Vector3(0f, 0f, delta.z)
            null -> delta
        }

        val adjustedDelta = when (modifier) {
            TransformModifier.PRECISION -> rotationDelta * 0.1f
            TransformModifier.FAST -> rotationDelta * 2f
            TransformModifier.NONE -> rotationDelta
        }

        return rotateAroundPivot(originalTransform, adjustedDelta, pivot)
    }

    private fun calculateScale(
        originalTransform: Transform3D,
        delta: Vector3,
        pivot: Vector3,
        constraintAxis: Axis?,
        modifier: TransformModifier
    ): Transform3D {
        val scaleDelta = when (constraintAxis) {
            Axis.X -> Vector3(delta.x, 0f, 0f)
            Axis.Y -> Vector3(0f, delta.y, 0f)
            Axis.Z -> Vector3(0f, 0f, delta.z)
            null -> delta
        }

        val adjustedDelta = when (modifier) {
            TransformModifier.PRECISION -> scaleDelta * 0.01f
            TransformModifier.FAST -> scaleDelta * 0.1f
            TransformModifier.NONE -> scaleDelta * 0.05f
        }

        val newScale = Vector3(
            max(0.01f, originalTransform.scale.x + adjustedDelta.x),
            max(0.01f, originalTransform.scale.y + adjustedDelta.y),
            max(0.01f, originalTransform.scale.z + adjustedDelta.z)
        )

        return scaleAroundPivot(originalTransform, newScale, pivot)
    }

    private fun rotateAroundPivot(transform: Transform3D, rotation: Vector3, pivot: Vector3): Transform3D {
        // Euler-based rotation around pivot point
        val newRotation = transform.rotation + rotation

        // Translate position relative to pivot rotation
        val relativePos = transform.position - pivot
        // Apply rotation to relative position (simplified)
        val newRelativePos = relativePos // Would apply rotation matrix here
        val newPosition = pivot + newRelativePos

        return transform.copy(
            position = newPosition,
            rotation = newRotation
        )
    }

    private fun scaleAroundPivot(transform: Transform3D, newScale: Vector3, pivot: Vector3): Transform3D {
        val relativePos = transform.position - pivot
        val scaleFactor = Vector3(
            newScale.x / transform.scale.x,
            newScale.y / transform.scale.y,
            newScale.z / transform.scale.z
        )
        val newRelativePos = Vector3(
            relativePos.x * scaleFactor.x,
            relativePos.y * scaleFactor.y,
            relativePos.z * scaleFactor.z
        )
        val newPosition = pivot + newRelativePos

        return transform.copy(
            position = newPosition,
            scale = newScale
        )
    }

    private fun transformVectorToLocal(vector: Vector3, rotation: Vector3): Vector3 {
        // Convert Euler angles to rotation matrix and transform vector
        // Simplified implementation
        return vector
    }

    private fun applyConstraints(transform: Transform3D, constraintAxis: Axis?): Transform3D {
        if (!enableConstraints || constraintAxis == null) return transform

        // Apply axis constraints
        return when (constraintAxis) {
            Axis.X -> transform.copy(
                position = Vector3(transform.position.x, 0f, 0f)
            )
            Axis.Y -> transform.copy(
                position = Vector3(0f, transform.position.y, 0f)
            )
            Axis.Z -> transform.copy(
                position = Vector3(0f, 0f, transform.position.z)
            )
        }
    }

    private fun applyGridSnapping(transform: Transform3D): Transform3D {
        val snappedPosition = Vector3(
            round(transform.position.x / gridSize) * gridSize,
            round(transform.position.y / gridSize) * gridSize,
            round(transform.position.z / gridSize) * gridSize
        )

        return transform.copy(position = snappedPosition)
    }

    private fun detectCollisions(objects: List<SerializedObject3D>): List<String> {
        // Simplified collision detection - would use proper bounding volumes
        val collisions = mutableListOf<String>()

        for (i in objects.indices) {
            for (j in i + 1 until objects.size) {
                val obj1 = objects[i]
                val obj2 = objects[j]

                val distance = distance(obj1.transform.position, obj2.transform.position)
                val minDistance = 1.0f // Simplified minimum distance

                if (distance < minDistance) {
                    collisions.add("${obj1.name} and ${obj2.name}")
                }
            }
        }

        return collisions
    }

    private fun distance(pos1: Vector3, pos2: Vector3): Float {
        val dx = pos1.x - pos2.x
        val dy = pos1.y - pos2.y
        val dz = pos1.z - pos2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun updateGizmoPosition() {
        if (_selectedObjects.value.isEmpty()) {
            hideGizmo()
            return
        }

        val selectedObjs = getSelectedObjects()
        if (selectedObjs.isNotEmpty()) {
            val pivot = calculatePivotPoint(selectedObjs)
            // Update gizmo position (would interface with rendering system)
        }
    }

    private fun updateObjectsInProject(objects: List<SerializedObject3D>) {
        currentProject?.let { project ->
            val updatedObjects = project.scene.objects.toMutableList()

            for (obj in objects) {
                val index = updatedObjects.indexOfFirst { it.id == obj.id }
                if (index >= 0) {
                    updatedObjects[index] = obj
                }
            }

            val updatedScene = project.scene.copy(objects = updatedObjects)
            currentProject = project.copy(scene = updatedScene)

            onObjectsChanged?.invoke(objects)
        }
    }

    private fun updateProjectObjects(allObjects: List<SerializedObject3D>) {
        currentProject?.let { project ->
            val updatedScene = project.scene.copy(objects = allObjects)
            currentProject = project.copy(scene = updatedScene)

            onObjectsChanged?.invoke(allObjects)
        }
    }

    private fun addToHistory(operation: TransformOperation) {
        // Remove any operations after current index (for when we made changes after undoing)
        if (historyIndex + 1 < transformHistory.size) {
            transformHistory.removeAll(transformHistory.subList(historyIndex + 1, transformHistory.size))
        }

        transformHistory.add(operation)
        historyIndex = transformHistory.size - 1

        // Limit history size
        if (transformHistory.size > 100) {
            transformHistory.removeAt(0)
            historyIndex--
        }
    }

    private fun generateUniqueId(): String {
        return "obj_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}_${(0..999).random()}"
    }
}

// Supporting data classes and enums

/**
 * Manipulation types
 */
enum class ManipulationType {
    TRANSLATE, ROTATE, SCALE
}

/**
 * Gizmo types for visual manipulation
 */
enum class GizmoType {
    TRANSLATE, ROTATE, SCALE, UNIVERSAL
}

/**
 * Constraint axes
 */
enum class Axis {
    X, Y, Z
}

/**
 * Transform modifiers for precision/speed
 */
enum class TransformModifier {
    NONE, PRECISION, FAST
}

/**
 * Manipulation start state
 */
data class ManipulationStartState(
    val type: ManipulationType,
    val objects: Map<String, Transform3D>,
    val pivot: Vector3,
    val startPosition: Vector3,
    val constraintAxis: Axis?
)

/**
 * Transform operation for history
 */
data class TransformOperation(
    val type: ManipulationType,
    val objectIds: List<String>,
    val beforeTransforms: Map<String, Transform3D>,
    val afterTransforms: Map<String, Transform3D>,
    val timestamp: kotlinx.datetime.Instant
)

/**
 * Collision exception
 */
class CollisionException(message: String) : Exception(message)

/**
 * Vector3 extension functions
 */
operator fun Vector3.plus(other: Vector3): Vector3 {
    return Vector3(x + other.x, y + other.y, z + other.z)
}

operator fun Vector3.minus(other: Vector3): Vector3 {
    return Vector3(x - other.x, y - other.y, z - other.z)
}

operator fun Vector3.times(scalar: Float): Vector3 {
    return Vector3(x * scalar, y * scalar, z * scalar)
}

/**
 * Utility functions
 */
fun Vector3.normalized(): Vector3 {
    val length = sqrt(x * x + y * y + z * z)
    return if (length > 0) Vector3(x / length, y / length, z / length) else Vector3.ZERO
}

fun Vector3.magnitude(): Float {
    return sqrt(x * x + y * y + z * z)
}

fun lerp(a: Float, b: Float, t: Float): Float {
    return a + (b - a) * t
}

fun Vector3.lerp(other: Vector3, t: Float): Vector3 {
    return Vector3(
        lerp(x, other.x, t),
        lerp(y, other.y, t),
        lerp(z, other.z, t)
    )
}