package io.materia.core.scene

import io.materia.core.math.*

/**
 * # Object3D - Base Class for 3D Scene Objects
 *
 * The foundation of all 3D objects in Materia's scene graph. Object3D provides transformation,
 * hierarchy management, and lifecycle capabilities for meshes, lights, cameras, and groups.
 *
 * ## Overview
 *
 * Object3D is the base class for most objects in Materia and provides:
 * - **Transformation**: Position, rotation, scale in local and world space
 * - **Hierarchy**: Parent-child relationships for complex scene structures
 * - **Matrix Management**: Automatic matrix updates with dirty flagging for performance
 * - **Visibility Control**: Show/hide objects and control shadow casting
 * - **Layer System**: Selective rendering based on layer membership
 * - **Events**: Callbacks for before/after rendering
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Create and position an object
 * val obj = Mesh(geometry, material)
 * obj.position.set(10f, 0f, 0f)
 * obj.rotation.y = PI.toFloat() / 4f
 * obj.scale.set(2f, 2f, 2f)
 *
 * // Add to scene hierarchy
 * scene.add(obj)
 *
 * // Access world transform
 * val worldPos = obj.getWorldPosition()
 * val worldDir = obj.getWorldDirection()
 * ```
 *
 * ## Transformation Hierarchy
 *
 * Object3D maintains both local and world transformation:
 * - **Local**: Relative to parent ([position], [rotation], [scale], [quaternion])
 * - **World**: Absolute position in scene ([matrixWorld], [getWorldPosition])
 *
 * ```kotlin
 * val parent = Group()
 * parent.position.x = 100f
 *
 * val child = Mesh(geometry, material)
 * child.position.x = 50f
 * parent.add(child)
 *
 * // Child's local position is (50, 0, 0)
 * // Child's world position is (150, 0, 0)
 * val worldPos = child.getWorldPosition() // Vector3(150, 0, 0)
 * ```
 *
 * ## Performance Optimization
 *
 * Object3D uses dirty flagging to minimize matrix computations:
 * - Only recalculates matrices when transforms change
 * - Caches world matrix until invalidated
 * - Propagates updates efficiently through hierarchy
 *
 * Set [matrixAutoUpdate] to `false` for static objects to skip updates entirely.
 *
 * ## Architecture
 *
 * The implementation is split across multiple files for maintainability:
 * - **Object3DCore.kt**: Core functionality, layers, events
 * - **Object3DHierarchy.kt**: Hierarchy management (add, remove, traverse)
 * - **Object3DTransform.kt**: Transformation operations (rotate, translate, lookAt)
 * - **Object3DExtensions.kt**: Extension functions for supporting classes
 *
 * @property id Unique identifier automatically assigned to each instance
 * @property name Human-readable name for debugging and identification
 * @property position Local position relative to parent (default: origin)
 * @property rotation Local rotation as Euler angles in radians
 * @property scale Local scale factors (default: uniform scale of 1)
 * @property quaternion Local rotation as a quaternion (synced with [rotation])
 * @property matrix Local transformation matrix (position + rotation + scale)
 * @property matrixWorld World transformation matrix (accumulated from parent chain)
 * @property matrixAutoUpdate Whether to automatically update [matrix] each frame (default: true)
 * @property matrixWorldNeedsUpdate Flag indicating [matrixWorld] needs recomputation
 * @property visible Whether this object and its children are rendered (default: true)
 * @property castShadow Whether this object casts shadows (default: false)
 * @property receiveShadow Whether this object receives shadows (default: false)
 * @property parent Parent object in the scene hierarchy (null for root objects)
 * @property children List of child objects (read-only access)
 * @property layers Layer membership for selective rendering
 * @property userData Custom properties storage for application-specific data
 * @property onBeforeRender Optional callback invoked before rendering this object
 * @property onAfterRender Optional callback invoked after rendering this object
 *
 * @see Scene Root of the scene graph
 * @see Mesh Renderable 3D object combining geometry and material
 * @see Group Container for organizing objects in hierarchy
 * @see Camera Viewpoint for rendering the scene
 *
 * @since 1.0.0
 * @sample io.materia.samples.Object3DSamples.basicTransform
 * @sample io.materia.samples.Object3DSamples.hierarchyManagement
 */
abstract class Object3D {

    // Unique identifier
    val id: Int = Object3DIdGenerator.generateId()

    // Object name for debugging and identification
    var name: String = ""

    // Transformation properties
    val position: Vector3 = Vector3()
    val rotation: Euler = Euler()
    val scale: Vector3 = Vector3(1f, 1f, 1f)
    val quaternion: Quaternion = Quaternion()
    
    /** Up direction for lookAt operations. Default is positive Y. */
    val up: Vector3 = Vector3(0f, 1f, 0f)

    // Transformation matrices
    val matrix: Matrix4 = Matrix4()
    val matrixWorld: Matrix4 = Matrix4()

    // Auto-update behavior
    var matrixAutoUpdate: Boolean = true
    var matrixWorldNeedsUpdate: Boolean = false

    // Performance: Track if local transform has changed
    private var matrixNeedsUpdate: Boolean = true
    private var worldMatrixVersion: Int = 0
    private var localMatrixVersion: Int = 0

    // Visibility and shadow properties
    var visible: Boolean = true
    var castShadow: Boolean = false
    var receiveShadow: Boolean = false

    // Hierarchy
    var parent: Object3D? = null
        internal set
    internal val _children: MutableList<Object3D> = mutableListOf()
    val children: List<Object3D> get() = _children

    // Layers for selective rendering/raycasting
    val layers: Layers = Layers()

    // Custom user data
    val userData: MutableMap<String, Any> = mutableMapOf()

    // Event callbacks
    var onBeforeRender: ((Object3D) -> Unit)? = null
    var onAfterRender: ((Object3D) -> Unit)? = null

    // Object type for identification
    open val type: String get() = "Object3D"

    init {
        // Ensure rotation and quaternion stay in sync
        rotation.onChange = {
            updateQuaternionFromEuler()
            markTransformDirty()
        }
        quaternion.onChange = {
            updateEulerFromQuaternion()
            markTransformDirty()
        }
    }

    /**
     * Performance: Mark transform as dirty to trigger matrix updates
     */
    private fun markTransformDirty() {
        matrixNeedsUpdate = true
        matrixWorldNeedsUpdate = true
    }

    // Hierarchy operations (delegated to Object3DHierarchy.kt)
    fun add(vararg objects: Object3D): Object3D = addChildren(*objects)
    fun remove(vararg objects: Object3D): Object3D = removeChildren(*objects)
    fun removeFromParent(): Object3D = detachFromParent()
    fun clear(): Object3D = clearChildren()
    fun attach(object3d: Object3D): Object3D = attachChild(object3d)
    fun getObjectByName(name: String): Object3D? = findObjectByName(name)
    fun getObjectById(id: Int): Object3D? = findObjectById(id)
    fun getObjectByProperty(name: String, value: Any): Object3D? = findObjectByProperty(name, value)
    fun traverse(callback: (Object3D) -> Unit) = traverseAll(callback)
    fun traverseVisible(callback: (Object3D) -> Unit) = traverseOnlyVisible(callback)
    fun traverseAncestors(callback: (Object3D) -> Unit) = traverseParents(callback)

    // Transformation operations (delegated to Object3DTransform.kt)
    fun getWorldPosition(target: Vector3 = Vector3()): Vector3 = extractWorldPosition(target)
    fun getWorldQuaternion(target: Quaternion = Quaternion()): Quaternion =
        extractWorldQuaternion(target)

    fun getWorldScale(target: Vector3 = Vector3()): Vector3 = extractWorldScale(target)
    open fun getWorldDirection(target: Vector3 = Vector3()): Vector3 = extractWorldDirection(target)
    fun lookAt(target: Vector3) = setLookAt(target)
    fun lookAt(x: Float, y: Float, z: Float) = setLookAt(x, y, z)
    fun rotateOnAxis(axis: Vector3, angle: Float): Object3D = applyRotationOnAxis(axis, angle)
    fun rotateOnWorldAxis(axis: Vector3, angle: Float): Object3D =
        applyRotationOnWorldAxis(axis, angle)

    fun rotateX(angle: Float): Object3D = applyRotationX(angle)
    fun rotateY(angle: Float): Object3D = applyRotationY(angle)
    fun rotateZ(angle: Float): Object3D = applyRotationZ(angle)
    fun translateOnAxis(axis: Vector3, distance: Float): Object3D =
        applyTranslationOnAxis(axis, distance)

    fun translateX(distance: Float): Object3D = applyTranslationX(distance)
    fun translateY(distance: Float): Object3D = applyTranslationY(distance)
    fun translateZ(distance: Float): Object3D = applyTranslationZ(distance)
    fun localToWorld(vector: Vector3): Vector3 = convertLocalToWorld(vector)
    fun worldToLocal(vector: Vector3): Vector3 = convertWorldToLocal(vector)
    fun applyMatrix4(matrix: Matrix4): Object3D = applyMatrixTransform(matrix)
    fun updateMatrix() = updateLocalMatrix()
    fun updateWorldMatrix(updateParents: Boolean = false, updateChildren: Boolean = false) =
        updateWorldMatrixWithOptions(updateParents, updateChildren)

    /**
     * Computes the axis-aligned bounding box (AABB) of this object in local space.
     *
     * The bounding box encompasses all vertices of the object's geometry.
     * Override this method in subclasses to provide accurate bounds calculation.
     *
     * @return [Box3] representing the object's bounds in local coordinates
     * @see Box3
     * @since 1.0.0
     */
    open fun getBoundingBox(): Box3 {
        // Default implementation returns empty box
        return Box3()
    }

    /**
     * Updates the world transformation matrix for this object and optionally its children.
     *
     * This method:
     * - Recalculates the local matrix from position/rotation/scale if needed
     * - Combines with parent's world matrix to produce this object's world matrix
     * - Recursively updates children if the world matrix changed or [force] is true
     *
     * ## Performance Optimization
     *
     * Uses dirty flagging to skip unnecessary updates:
     * - Only processes when transforms changed or forced
     * - Tracks matrix versions to minimize recomputation
     * - Efficiently propagates updates through hierarchy
     *
     * Example:
     * ```kotlin
     * // Manually trigger world matrix update
     * obj.position.x = 10f
     * obj.updateMatrixWorld(force = true)
     *
     * // For static objects, disable auto-update
     * staticObj.matrixAutoUpdate = false
     * staticObj.updateMatrix() // Manual update when needed
     * ```
     *
     * @param force If true, forces update of this object and all descendants regardless of dirty flags
     * @see updateMatrix For updating only the local matrix
     * @see matrixAutoUpdate To control automatic updates
     * @since 1.0.0
     */
    open fun updateMatrixWorld(force: Boolean = false) {
        // Performance: Skip if nothing changed and not forced
        if (!force && !matrixWorldNeedsUpdate && !matrixNeedsUpdate) {
            return
        }

        if (matrixAutoUpdate && matrixNeedsUpdate) {
            updateMatrix()
            matrixNeedsUpdate = false
            localMatrixVersion++
        }

        var forceChildren = force
        if (matrixWorldNeedsUpdate || force) {
            val parentVersion = parent?.worldMatrixVersion ?: 0

            parent?.let { p ->
                matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
            } ?: matrixWorld.copy(matrix)

            matrixWorldNeedsUpdate = false
            worldMatrixVersion++
            forceChildren = true
        }

        // Performance: Only update children if this object's world matrix changed
        if (forceChildren) {
            // Take snapshot to avoid ConcurrentModificationException
            val childrenSnapshot = children.toList()
            for (child in childrenSnapshot) {
                child.updateMatrixWorld(forceChildren)
            }
        }
    }

    /**
     * Creates a copy of this object
     */
    open fun clone(recursive: Boolean = true): Object3D {
        throw NotImplementedError("Clone must be implemented by subclass")
    }

    /**
     * Copies properties from another object
     */
    open fun copy(source: Object3D, recursive: Boolean = true): Object3D {
        name = source.name

        position.copy(source.position)
        rotation.copy(source.rotation)
        scale.copy(source.scale)
        quaternion.copy(source.quaternion)

        matrix.copy(source.matrix)
        matrixWorld.copy(source.matrixWorld)

        matrixAutoUpdate = source.matrixAutoUpdate
        matrixWorldNeedsUpdate = source.matrixWorldNeedsUpdate

        visible = source.visible
        castShadow = source.castShadow
        receiveShadow = source.receiveShadow

        layers.mask = source.layers.mask

        userData.clear()
        userData.putAll(source.userData)

        if (recursive) {
            for (child in source.children) {
                add(child.clone(true))
            }
        }

        return this
    }

    internal fun dispatchEvent(event: Event) {
        // Event dispatcher system for lifecycle and transform notifications
    }

    private fun updateQuaternionFromEuler() {
        quaternion.setFromEuler(rotation)
    }

    private fun updateEulerFromQuaternion() {
        rotation.setFromQuaternion(quaternion)
    }

    override fun toString(): String {
        return "${this::class.simpleName}(id=$id, name='$name')"
    }
}
