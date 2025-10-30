/**
 * Enhanced BufferGeometry implementation with advanced 3D features
 * Extends the basic geometry system with morph targets, instancing, and LOD support
 */
package io.materia.geometry

import io.materia.core.math.*
import io.materia.core.platform.currentTimeMillis
import io.materia.morph.MorphTargetGeometry
import kotlinx.serialization.Serializable

/**
 * Advanced buffer geometry with morph targets and instancing support
 * Compatible with Three.js BufferGeometry patterns
 */
open class BufferGeometry : MorphTargetGeometry {
    // Core attributes
    private val _attributes = mutableMapOf<String, BufferAttribute>()
    private var _index: BufferAttribute? = null

    // Morph targets - implementing MorphTargetGeometry interface
    override val morphAttributes = mutableMapOf<String, List<BufferAttribute>>()
    override var morphTargetsRelative: Boolean = false
    override val morphTargetDictionary = mutableMapOf<String, Int>()

    // Instancing
    private val _instancedAttributes = mutableMapOf<String, BufferAttribute>()
    private var _instanceCount: Int = 0

    // Geometry groups for multi-material support
    private val _groups = mutableListOf<GeometryGroup>()

    // Bounding volumes (lazy computed)
    private var _boundingBox: Box3? = null
    private var _boundingSphere: Sphere? = null
    private var _boundingBoxNeedsUpdate = true
    private var _boundingSphereNeedsUpdate = true

    // LOD support
    private val _lodLevels = mutableListOf<LodLevel>()
    private var _activeLodLevel: Int = 0

    // Metadata and state
    var uuid: String =
        "geometry-${currentTimeMillis()}-${(kotlin.random.Random.nextDouble() * 1000000).toInt()}"
        private set
    var name: String = ""

    // Events
    private val _onDisposeCallbacks = mutableListOf<() -> Unit>()

    /**
     * Core attribute management
     */
    fun setAttribute(name: String, attribute: BufferAttribute): BufferGeometry {
        _attributes[name] = attribute
        _markBoundingVolumesNeedUpdate()
        return this
    }

    fun getAttribute(name: String): BufferAttribute? = _attributes[name]

    fun deleteAttribute(name: String): BufferGeometry {
        _attributes.remove(name)
        _markBoundingVolumesNeedUpdate()
        return this
    }

    fun hasAttribute(name: String): Boolean = _attributes.containsKey(name)

    val attributes: Map<String, BufferAttribute> get() = _attributes.toMap()

    /**
     * Index buffer management
     */
    fun setIndex(index: BufferAttribute?): BufferGeometry {
        _index = index
        return this
    }

    val index: BufferAttribute? get() = _index

    /**
     * Morph target management - backward compatible methods
     */
    fun setMorphAttribute(name: String, targets: Array<BufferAttribute>): BufferGeometry {
        morphAttributes[name] = targets.toList()
        return this
    }

    fun getMorphAttribute(name: String): List<BufferAttribute>? = morphAttributes[name]

    fun deleteMorphAttribute(name: String): BufferGeometry {
        morphAttributes.remove(name)
        return this
    }

    /**
     * Morph targets for backward compatibility
     * Returns the position morph attributes if they exist
     */
    var morphTargets: List<BufferAttribute>?
        get() = morphAttributes["position"]
        set(value) {
            if (value != null) {
                morphAttributes["position"] = value
            } else {
                morphAttributes.remove("position")
            }
        }

    /**
     * Compute bounding box including morph targets
     */
    override fun computeMorphedBoundingBox() {
        val positionAttribute = getAttribute("position") ?: return
        val morphPositions = morphAttributes["position"] ?: return

        val box = Box3()
        val tempVector = Vector3()

        // Include base positions
        box.setFromBufferAttribute(positionAttribute)

        // Expand box to include all morph target positions
        for (morphTarget in morphPositions) {
            for (i in 0 until morphTarget.count) {
                tempVector.fromBufferAttribute(morphTarget, i)
                box.expandByPoint(tempVector)
            }
        }

        _boundingBox = box
        _boundingBoxNeedsUpdate = false
    }

    /**
     * Compute bounding sphere including morph targets
     */
    override fun computeMorphedBoundingSphere() {
        val positionAttribute = getAttribute("position") ?: return
        val morphPositions = morphAttributes["position"] ?: return

        val sphere = Sphere()
        val tempVector = Vector3()
        val points = mutableListOf<Vector3>()

        // Collect base positions
        for (i in 0 until positionAttribute.count) {
            points.add(Vector3().fromBufferAttribute(positionAttribute, i))
        }

        // Collect all morph target positions
        for (morphTarget in morphPositions) {
            for (i in 0 until morphTarget.count) {
                points.add(Vector3().fromBufferAttribute(morphTarget, i))
            }
        }

        sphere.setFromPoints(points)
        _boundingSphere = sphere
        _boundingSphereNeedsUpdate = false
    }

    /**
     * Instancing support
     */
    fun setInstancedAttribute(name: String, attribute: BufferAttribute): BufferGeometry {
        _instancedAttributes[name] = attribute
        return this
    }

    fun getInstancedAttribute(name: String): BufferAttribute? = _instancedAttributes[name]

    fun deleteInstancedAttribute(name: String): BufferGeometry {
        _instancedAttributes.remove(name)
        return this
    }

    var instanceCount: Int
        get() = _instanceCount
        set(value) {
            _instanceCount = maxOf(0, value)
        }

    val instancedAttributes: Map<String, BufferAttribute> get() = _instancedAttributes.toMap()

    val isInstanced: Boolean get() = _instanceCount > 0

    /**
     * Geometry groups for multi-material rendering
     */
    fun addGroup(start: Int, count: Int, materialIndex: Int = 0): BufferGeometry {
        _groups.add(GeometryGroup(start, count, materialIndex))
        return this
    }

    fun clearGroups(): BufferGeometry {
        _groups.clear()
        return this
    }

    val groups: List<GeometryGroup> get() = _groups.toList()

    /**
     * Bounding volumes with lazy computation
     */
    fun computeBoundingBox(): Box3 {
        if (_boundingBox == null || _boundingBoxNeedsUpdate) {
            val positionAttribute = getAttribute("position")
            if (positionAttribute != null) {
                _boundingBox = Box3.fromBufferAttribute(positionAttribute)
                _boundingBoxNeedsUpdate = false
            } else {
                _boundingBox = Box3()
            }
        }
        return _boundingBox ?: Box3() // Safe fallback
    }

    fun computeBoundingSphere(): Sphere {
        if (_boundingSphere == null || _boundingSphereNeedsUpdate) {
            val positionAttribute = getAttribute("position")
            if (positionAttribute != null) {
                _boundingSphere = Sphere.fromBufferAttribute(positionAttribute)
                _boundingSphereNeedsUpdate = false
            } else {
                _boundingSphere = Sphere()
            }
        }
        return _boundingSphere ?: Sphere() // Safe fallback
    }

    val boundingBox: Box3? get() = if (_boundingBoxNeedsUpdate) null else _boundingBox
    val boundingSphere: Sphere? get() = if (_boundingSphereNeedsUpdate) null else _boundingSphere

    /**
     * LOD (Level of Detail) management
     */
    fun addLodLevel(distance: Float, geometry: BufferGeometry): BufferGeometry {
        val triangleCount = geometry.getTriangleCount()
        _lodLevels.add(LodLevel(distance, geometry, triangleCount))
        _lodLevels.sortBy { it.distance }
        return this
    }

    fun getLodLevel(distance: Float): BufferGeometry? {
        for (level in _lodLevels) {
            if (distance <= level.distance) {
                return level.geometry
            }
        }
        return _lodLevels.lastOrNull()?.geometry
    }

    var activeLodLevel: Int
        get() = _activeLodLevel
        set(value) {
            _activeLodLevel = value.coerceIn(0, _lodLevels.size - 1)
        }

    val lodLevels: List<LodLevel> get() = _lodLevels.toList()

    /**
     * Utility methods
     */
    fun getTriangleCount(): Int {
        val index = _index
        return if (index != null) {
            index.count / 3
        } else {
            val position = getAttribute("position")
            if (position != null) {
                position.count / 3
            } else {
                0
            }
        }
    }

    fun getVertexCount(): Int {
        val position = getAttribute("position") ?: return 0
        return position.count / position.itemSize
    }

    fun isEmpty(): Boolean = getVertexCount() == 0

    /**
     * Geometry operations
     */
    fun translate(x: Float, y: Float, z: Float): BufferGeometry {
        val position = getAttribute("position")
        if (position != null) {
            for (i in 0 until position.count step 3) {
                position.setX(i / 3, position.getX(i / 3) + x)
                position.setY(i / 3, position.getY(i / 3) + y)
                position.setZ(i / 3, position.getZ(i / 3) + z)
            }
            position.needsUpdate = true
            _markBoundingVolumesNeedUpdate()
        }
        return this
    }

    fun scale(x: Float, y: Float, z: Float): BufferGeometry {
        val position = getAttribute("position")
        if (position != null) {
            for (i in 0 until position.count step 3) {
                position.setX(i / 3, position.getX(i / 3) * x)
                position.setY(i / 3, position.getY(i / 3) * y)
                position.setZ(i / 3, position.getZ(i / 3) * z)
            }
            position.needsUpdate = true
            _markBoundingVolumesNeedUpdate()
        }
        return this
    }

    fun rotateX(angle: Float): BufferGeometry {
        val matrix = Matrix4.rotationX(angle)
        return applyMatrix4(matrix)
    }

    fun rotateY(angle: Float): BufferGeometry {
        val matrix = Matrix4.rotationY(angle)
        return applyMatrix4(matrix)
    }

    fun rotateZ(angle: Float): BufferGeometry {
        val matrix = Matrix4.rotationZ(angle)
        return applyMatrix4(matrix)
    }

    fun applyMatrix4(matrix: Matrix4): BufferGeometry {
        val position = getAttribute("position")
        if (position != null) {
            position.applyMatrix4(matrix)
            _markBoundingVolumesNeedUpdate()
        }

        val normal = getAttribute("normal")
        if (normal != null) {
            val normalMatrix = Matrix3.normalMatrix(matrix)
            normal.applyNormalMatrix(normalMatrix)
        }

        return this
    }

    /**
     * Serialization and cloning
     */
    fun clone(): BufferGeometry {
        val cloned = BufferGeometry()

        // Clone attributes
        _attributes.forEach { (name, attribute) ->
            cloned.setAttribute(name, attribute.clone())
        }

        // Clone index
        _index?.let { cloned.setIndex(it.clone()) }

        // Clone morph attributes
        morphAttributes.forEach { (name, targets) ->
            cloned.setMorphAttribute(name, targets.map { it.clone() }.toTypedArray())
        }

        // Clone instanced attributes
        _instancedAttributes.forEach { (name, attribute) ->
            cloned.setInstancedAttribute(name, attribute.clone())
        }

        // Copy properties
        cloned.morphTargetsRelative = morphTargetsRelative
        cloned.instanceCount = instanceCount
        cloned.name = name

        // Clone groups
        _groups.forEach { group ->
            cloned.addGroup(group.start, group.count, group.materialIndex)
        }

        // Clone LOD levels
        _lodLevels.forEach { level ->
            cloned.addLodLevel(level.distance, level.geometry.clone())
        }

        return cloned
    }

    /**
     * Resource disposal
     */
    fun dispose() {
        _onDisposeCallbacks.forEach { it() }
        _onDisposeCallbacks.clear()
    }

    fun onDispose(callback: () -> Unit) {
        _onDisposeCallbacks.add(callback)
    }

    private fun _markBoundingVolumesNeedUpdate() {
        _boundingBoxNeedsUpdate = true
        _boundingSphereNeedsUpdate = true
    }
}

/**
 * Geometry group for multi-material support
 */
@Serializable
data class GeometryGroup(
    val start: Int,
    val count: Int,
    val materialIndex: Int = 0
)

/**
 * LOD level definition
 */
data class LodLevel(
    val distance: Float,
    val geometry: BufferGeometry,
    val triangleCount: Int
)

/**
 * Buffer attribute for storing vertex data
 */
open class BufferAttribute(
    open val array: FloatArray,
    open val itemSize: Int,
    open val normalized: Boolean = false
) {
    open var needsUpdate: Boolean = true // Default to true - new attributes need GPU upload
    open var updateRange: IntRange = IntRange.EMPTY

    open val count: Int get() = array.size / itemSize

    open fun getX(index: Int): Float = array[(index * itemSize)]
    open fun getY(index: Int): Float = array[index * itemSize + 1]
    open fun getZ(index: Int): Float = array[index * itemSize + 2]
    open fun getW(index: Int): Float = array[index * itemSize + 3]

    open fun setX(index: Int, value: Float) {
        array[(index * itemSize)] = value
    }

    open fun setY(index: Int, value: Float) {
        array[index * itemSize + 1] = value
    }

    open fun setZ(index: Int, value: Float) {
        array[index * itemSize + 2] = value
    }

    open fun setW(index: Int, value: Float) {
        array[index * itemSize + 3] = value
    }

    open fun setXY(index: Int, x: Float, y: Float) {
        val offset = index * itemSize
        array[offset] = x
        array[offset + 1] = y
    }

    open fun setXYZ(index: Int, x: Float, y: Float, z: Float) {
        val offset = index * itemSize
        array[offset] = x
        array[offset + 1] = y
        array[offset + 2] = z
    }

    open fun setXYZW(index: Int, x: Float, y: Float, z: Float, w: Float) {
        val offset = index * itemSize
        array[offset] = x
        array[offset + 1] = y
        array[offset + 2] = z
        array[offset + 3] = w
    }

    open fun clone(): BufferAttribute {
        return BufferAttribute(array.copyOf(), itemSize, normalized).apply {
            needsUpdate = this@BufferAttribute.needsUpdate
            updateRange = this@BufferAttribute.updateRange
        }
    }

    open fun applyMatrix4(matrix: Matrix4) {
        if (itemSize == 3) {
            for (i in 0 until count) {
                val vector = Vector3(getX(i), getY(i), getZ(i))
                vector.applyMatrix4(matrix)
                setXYZ(i, vector.x, vector.y, vector.z)
            }
        }
        needsUpdate = true
    }

    open fun applyNormalMatrix(matrix: Matrix3) {
        if (itemSize == 3) {
            for (i in 0 until count) {
                val vector = Vector3(getX(i), getY(i), getZ(i))
                vector.applyMatrix3(matrix).normalize()
                setXYZ(i, vector.x, vector.y, vector.z)
            }
        }
        needsUpdate = true
    }
}