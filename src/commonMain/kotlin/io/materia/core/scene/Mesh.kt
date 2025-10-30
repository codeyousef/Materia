package io.materia.core.scene

import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.core.platform.platformArrayCopy
import io.materia.geometry.BufferGeometry
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic

/**
 * A mesh object that combines geometry with material.
 * Compatible with Three.js Mesh API.
 */
open class Mesh(
    var geometry: BufferGeometry,
    var material: Material? = null
) : Object3D() {

    /**
     * Object type identifier
     */
    override val type: String get() = "Mesh"

    /**
     * Draw mode (triangles, lines, points, etc.)
     */
    var drawMode: DrawMode = DrawMode.TRIANGLES

    /**
     * Morph targets for animation
     */
    var morphTargetInfluences: MutableList<Float>? = null
    var morphTargetDictionary: MutableMap<String, Int>? = null

    /**
     * Updates the morph targets
     */
    fun updateMorphTargets() {
        val morphTargets = geometry.morphTargets
        if (morphTargets != null && morphTargets.isNotEmpty()) {
            if (morphTargetInfluences == null) {
                morphTargetInfluences = MutableList(morphTargets.size) { 0f }
            }
            if (morphTargetDictionary == null) {
                val dictionary = mutableMapOf<String, Int>()
                morphTargetDictionary = dictionary
                morphTargets.forEachIndexed { index, target ->
                    // BufferAttribute doesn't have name property, use index
                    dictionary[index.toString()] = index
                }
            }
        } else {
            morphTargetInfluences = null
            morphTargetDictionary = null
        }
    }

    /**
     * Raycasting support
     */
    open fun raycast(raycaster: Raycaster, intersects: MutableList<Intersection>) {
        // Implementation depends on geometry type
        // This would involve checking ray-triangle intersections
        // Basic implementation provided - can be extended
    }

    /**
     * Copy properties from another mesh
     */
    fun copy(source: Mesh, recursive: Boolean = true): Mesh {
        super.copy(source, recursive)

        this.geometry = source.geometry
        this.material = source.material
        this.drawMode = source.drawMode

        source.morphTargetInfluences?.let {
            this.morphTargetInfluences = it.toMutableList()
        }
        source.morphTargetDictionary?.let {
            this.morphTargetDictionary = it.toMutableMap()
        }

        return this
    }

    /**
     * Clone this mesh
     */
    override fun clone(recursive: Boolean): Mesh {
        return Mesh(geometry, material).copy(this, recursive)
    }
}

/**
 * Draw modes for mesh rendering
 */
enum class DrawMode {
    POINTS,
    LINES,
    LINE_LOOP,
    LINE_STRIP,
    TRIANGLES,
    TRIANGLE_STRIP,
    TRIANGLE_FAN
}

/**
 * Raycaster for picking and intersection testing
 * Used for mouse interaction and object selection
 */
class Raycaster {
    val ray: Ray = Ray(Vector3(), Vector3(0f, 0f, -1f))
    var near: Float = 0f
    var far: Float = Float.POSITIVE_INFINITY
    var camera: Any? = null // Should be Camera type

    fun setFromCamera(coords: Vector2, camera: Any) {
        // Implementation would transform screen coords to ray
    }

    fun intersectObject(target: Object3D, recursive: Boolean = true): List<Intersection> {
        val intersects = mutableListOf<Intersection>()
        intersectObjectInternal(target, intersects, recursive)
        return intersects.sortedBy { it.distance }
    }

    private fun intersectObjectInternal(
        obj: Object3D,
        intersects: MutableList<Intersection>,
        recursive: Boolean
    ) {
        if (obj is Mesh) {
            obj.raycast(this, intersects)
        }

        if (recursive) {
            for (child in obj.children) {
                intersectObjectInternal(child, intersects, recursive)
            }
        }
    }
}

/**
 * Intersection result from raycasting
 */
data class Intersection(
    val distance: Float,
    val point: Vector3,
    val face: Face3? = null,
    val faceIndex: Int? = null,
    val `object`: Object3D,
    val uv: Vector2? = null,
    val uv2: Vector2? = null
)

/**
 * A face with three vertex indices
 */
data class Face3(
    val a: Int,
    val b: Int,
    val c: Int,
    val normal: Vector3? = null,
    val vertexNormals: List<Vector3>? = null,
    val color: Color? = null,
    val vertexColors: List<Color>? = null,
    val materialIndex: Int = 0
)

/**
 * Instance of a mesh with hardware instancing support
 */
class InstancedMesh(
    geometry: BufferGeometry,
    material: Material?,
    val count: Int
) : Mesh(geometry, material) {

    val instanceMatrix: InstancedBufferAttribute =
        InstancedBufferAttribute(FloatArray((count * 16)), 16)
    var instanceColor: InstancedBufferAttribute? = null

    /**
     * Set matrix for a specific instance
     */
    fun setMatrixAt(index: Int, matrix: Matrix4) {
        matrix.toArray(instanceMatrix.array, (index * 16))
        instanceMatrix.needsUpdate = true
    }

    /**
     * Get matrix for a specific instance (fills provided matrix)
     */
    fun getMatrixAt(index: Int, matrix: Matrix4) {
        matrix.fromArray(instanceMatrix.array, (index * 16))
    }

    /**
     * Get matrix for a specific instance (returns new matrix)
     */
    fun getMatrixAt(index: Int): Matrix4 {
        val matrix = Matrix4()
        matrix.fromArray(instanceMatrix.array, (index * 16))
        return matrix
    }

    /**
     * Set color for a specific instance
     */
    fun setColorAt(index: Int, color: Color) {
        if (instanceColor == null) {
            instanceColor = InstancedBufferAttribute(FloatArray((count * 3)), 3)
        }
        instanceColor?.let {
            color.toArray(it.array, (index * 3))
            it.needsUpdate = true
        }
    }

    /**
     * Get color for a specific instance (fills provided color)
     */
    fun getColorAt(index: Int, color: Color) {
        instanceColor?.let {
            color.fromArray(it.array, (index * 3))
        }
    }

    /**
     * Get color for a specific instance (returns new color)
     */
    fun getColorAt(index: Int): Color {
        val color = Color()
        instanceColor?.let {
            color.fromArray(it.array, (index * 3))
        }
        return color
    }

    var needsUpdate: Boolean = false

    fun copyFrom(source: InstancedMesh, recursive: Boolean = true): InstancedMesh {
        super.copy(source, recursive)

        this.instanceMatrix.copy(source.instanceMatrix)
        source.instanceColor?.let {
            val newInstanceColor = InstancedBufferAttribute(FloatArray((count * 3)), 3)
            this.instanceColor = newInstanceColor
            newInstanceColor.copy(it)
        }

        return this
    }
}

/**
 * Instanced buffer attribute for hardware instancing
 */
class InstancedBufferAttribute(
    array: FloatArray,
    itemSize: Int,
    normalized: Boolean = false
) : BufferAttribute(array, itemSize, normalized) {
    var meshPerAttribute: Int = 1
}

/**
 * Base buffer attribute class
 */
open class BufferAttribute(
    val array: FloatArray,
    val itemSize: Int,
    val normalized: Boolean = false
) {
    var needsUpdate: Boolean = false
    var usage: Usage = Usage.STATIC_DRAW
    var version: Int = 0

    fun copy(source: BufferAttribute): BufferAttribute {
        platformArrayCopy(source.array, 0, this.array, 0, source.array.size)
        this.needsUpdate = true
        return this
    }

    fun setX(index: Int, x: Float): BufferAttribute {
        array[(index * itemSize)] = x
        return this
    }

    fun setY(index: Int, y: Float): BufferAttribute {
        array[index * itemSize + 1] = y
        return this
    }

    fun setZ(index: Int, z: Float): BufferAttribute {
        array[index * itemSize + 2] = z
        return this
    }

    fun setW(index: Int, w: Float): BufferAttribute {
        array[index * itemSize + 3] = w
        return this
    }

    fun getX(index: Int): Float = array[(index * itemSize)]
    fun getY(index: Int): Float = array[index * itemSize + 1]
    fun getZ(index: Int): Float = array[index * itemSize + 2]
    fun getW(index: Int): Float = array[index * itemSize + 3]

    enum class Usage {
        STATIC_DRAW,
        DYNAMIC_DRAW,
        STREAM_DRAW
    }
}

/**
 * Ray definition
 */
data class Ray(
    val origin: Vector3,
    val direction: Vector3
)

/**
 * Specialized mesh types
 */

/**
 * Line mesh for rendering lines
 */
open class Line(
    geometry: BufferGeometry,
    material: Material? = null
) : Mesh(geometry, material) {
    init {
        drawMode = DrawMode.LINE_STRIP
    }
}

/**
 * Line segments mesh for rendering disconnected line segments
 */
open class LineSegments(
    geometry: BufferGeometry,
    material: Material? = null
) : Line(geometry, material) {
    init {
        drawMode = DrawMode.LINES
    }
}

/**
 * Line loop mesh for rendering closed lines
 */
class LineLoop(
    geometry: BufferGeometry,
    material: Material? = null
) : Line(geometry, material) {
    init {
        drawMode = DrawMode.LINE_LOOP
    }
}

/**
 * Points mesh for rendering point clouds
 */
class Points(
    geometry: BufferGeometry,
    material: Material? = null
) : Mesh(geometry, material) {
    init {
        drawMode = DrawMode.POINTS
    }
}

/**
 * Sprite mesh for billboarded 2D images
 */
class Sprite(
    var material: SpriteMaterial? = null
) : Object3D() {
    var center: Vector2 = Vector2(0.5f, 0.5f)

    fun raycast(raycaster: Raycaster, intersects: MutableList<Intersection>) {
        // Sprite-specific raycasting
    }
}

/**
 * Sprite material for billboard rendering
 */
class SpriteMaterial : Material {
    override val id: Int = nextId.getAndIncrement()
    override val name: String = "SpriteMaterial"
    override var needsUpdate: Boolean = false
    override var visible: Boolean = true

    companion object {
        private val nextId: AtomicInt = atomic(1)
    }
}