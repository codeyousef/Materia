package io.materia.helper

import io.materia.core.math.Color
import io.materia.core.math.Vector3
import io.materia.core.scene.Group
import io.materia.core.scene.Object3D
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.geometry.primitives.CylinderGeometry
import io.materia.material.LineBasicMaterial
import io.materia.material.MeshBasicMaterial
import kotlin.math.acos

/**
 * ArrowHelper - Displays an arrow for visualizing directions.
 *
 * An arrow object consisting of a line (shaft) and a cone (head).
 * Useful for visualizing normals, directions, velocities, or forces.
 *
 * ## Usage
 *
 * ```kotlin
 * // Arrow pointing in +Y direction
 * val arrow = ArrowHelper(
 *     dir = Vector3(0f, 1f, 0f),
 *     origin = Vector3(0f, 0f, 0f),
 *     length = 5f,
 *     color = Color.RED
 * )
 * scene.add(arrow)
 * ```
 *
 * ## Updating Direction
 *
 * ```kotlin
 * // Change direction dynamically
 * arrow.setDirection(Vector3(1f, 0f, 0f))
 * arrow.setLength(10f)
 * arrow.setColor(Color.GREEN)
 * ```
 *
 * @param dir Direction vector (will be normalized).
 * @param origin Position of the arrow's base.
 * @param length Total length of the arrow.
 * @param color Arrow color.
 * @param headLength Length of the cone head.
 * @param headWidth Width of the cone head.
 */
class ArrowHelper(
    dir: Vector3 = Vector3(0f, 0f, 1f),
    origin: Vector3 = Vector3(0f, 0f, 0f),
    length: Float = 1f,
    color: Color = Color(0xffff00),
    headLength: Float = length * 0.2f,
    headWidth: Float = headLength * 0.4f
) : Object3D() {

    private var _dir = dir.clone().normalize()
    private var _length = length
    private var _color = color.clone()
    private var _headLength = headLength
    private var _headWidth = headWidth

    private val line: Group
    private val cone: Group

    private val lineMaterial: LineBasicMaterial
    private val coneMaterial: MeshBasicMaterial

    init {
        name = "ArrowHelper"

        // Create line (shaft)
        val lineGeometry = BufferGeometry()
        lineGeometry.setAttribute(
            "position",
            BufferAttribute(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f), 3)
        )

        lineMaterial = LineBasicMaterial(color = color.clone(), toneMapped = false)

        line = Group()
        line.name = "ArrowHelper_Line"
        line.userData["geometry"] = lineGeometry
        line.userData["material"] = lineMaterial
        add(line)

        // Create cone (head)
        val coneGeometry = CylinderGeometry(
            radiusTop = 0f,
            radiusBottom = 0.5f,
            height = 1f,
            radialSegments = 8,
            heightSegments = 1
        )
        // Translate cone so tip is at origin
        coneGeometry.translate(0f, -0.5f, 0f)

        coneMaterial = MeshBasicMaterial().apply {
            this.color = color.clone()
            fog = false
        }

        cone = Group()
        cone.name = "ArrowHelper_Cone"
        add(cone)

        // Set initial position
        position.copy(origin)

        // Set initial direction and length
        setDirection(dir)
        setLength(length, headLength, headWidth)
    }

    /**
     * Sets the direction of the arrow.
     *
     * @param dir Direction vector (will be normalized).
     */
    fun setDirection(dir: Vector3) {
        _dir = dir.clone().normalize()

        // Rotate to point in direction
        if (dir.y > 0.99999f) {
            quaternion.set(0f, 0f, 0f, 1f)
        } else if (dir.y < -0.99999f) {
            quaternion.set(1f, 0f, 0f, 0f)
        } else {
            val axis = Vector3(0f, 1f, 0f).cross(dir).normalize()
            val angle = acos(dir.y.coerceIn(-1f, 1f))
            quaternion.setFromAxisAngle(axis, angle)
        }
    }

    /**
     * Sets the length of the arrow.
     *
     * @param length Total length.
     * @param headLength Length of the cone head.
     * @param headWidth Width of the cone head.
     */
    fun setLength(
        length: Float,
        headLength: Float = this._headLength,
        headWidth: Float = this._headWidth
    ) {
        _length = length
        _headLength = headLength
        _headWidth = headWidth

        // Scale line (shaft goes from 0 to length - headLength)
        line.scale.set(1f, maxOf(0.0001f, length - headLength), 1f)
        line.updateMatrix()

        // Position and scale cone (head)
        cone.position.y = length - headLength
        cone.scale.set(headWidth, headLength, headWidth)
        cone.updateMatrix()
    }

    /**
     * Sets the color of the arrow.
     *
     * @param color New color.
     */
    fun setColor(color: Color) {
        _color = color.clone()
        lineMaterial.color = color.clone()
        coneMaterial.color = color.clone()
    }

    /**
     * Gets the current direction vector.
     */
    fun getDirection(): Vector3 = _dir.clone()

    /**
     * Gets the current length.
     */
    fun getLength(): Float = _length

    /**
     * Gets the current color.
     */
    fun getColor(): Color = _color.clone()

    /**
     * Copies properties from another ArrowHelper.
     */
    fun copy(source: ArrowHelper): ArrowHelper {
        super.copy(source, true)

        setDirection(source._dir)
        setLength(source._length, source._headLength, source._headWidth)
        setColor(source._color)

        return this
    }

    /**
     * Disposes of the arrow's resources.
     */
    fun dispose() {
        // Dispose geometries stored in userData
        (line.userData["geometry"] as? BufferGeometry)?.dispose()
        (cone.userData["geometry"] as? BufferGeometry)?.dispose()

        // Remove children to allow garbage collection
        remove(line)
        remove(cone)
    }

    companion object {
        /**
         * Creates an arrow between two points.
         *
         * @param from Start point.
         * @param to End point.
         * @param color Arrow color.
         * @return ArrowHelper pointing from start to end.
         */
        fun fromPoints(from: Vector3, to: Vector3, color: Color = Color(0xffff00)): ArrowHelper {
            val dir = to.clone().sub(from)
            val length = dir.length()
            return ArrowHelper(
                dir = dir.normalize(),
                origin = from,
                length = length,
                color = color
            )
        }

        /**
         * Creates a coordinate axis arrow.
         *
         * @param axis "x", "y", or "z".
         * @param length Arrow length.
         * @return ArrowHelper pointing along the specified axis.
         */
        fun axis(axis: String, length: Float = 1f): ArrowHelper {
            return when (axis.lowercase()) {
                "x" -> ArrowHelper(Vector3(1f, 0f, 0f), Vector3.ZERO, length, Color.RED)
                "y" -> ArrowHelper(Vector3(0f, 1f, 0f), Vector3.ZERO, length, Color.GREEN)
                "z" -> ArrowHelper(Vector3(0f, 0f, 1f), Vector3.ZERO, length, Color.BLUE)
                else -> throw IllegalArgumentException("Axis must be 'x', 'y', or 'z'")
            }
        }
    }
}

/**
 * PlaneHelper - Displays a plane with its normal.
 *
 * Visualizes a plane using a grid and an arrow showing the normal direction.
 *
 * ## Usage
 *
 * ```kotlin
 * val plane = Plane(Vector3(0f, 1f, 0f), 0f)
 * val helper = PlaneHelper(plane, 10f, Color.CYAN)
 * scene.add(helper)
 * ```
 *
 * @param plane The plane to visualize.
 * @param size Size of the plane visualization.
 * @param color Color of the plane grid.
 */
class PlaneHelper(
    private var _plane: Plane = Plane(Vector3(0f, 1f, 0f), 0f),
    private var _size: Float = 1f,
    color: Color = Color(0xffff00)
) : Object3D() {

    private var _color = color.clone()
    private val material: LineBasicMaterial
    private var geometry: BufferGeometry

    init {
        name = "PlaneHelper"

        // Create plane grid geometry
        geometry = createPlaneGeometry(_size)

        material = LineBasicMaterial(color = color.clone(), toneMapped = false)

        // Orient to match plane normal
        updateOrientation()
    }

    private fun createPlaneGeometry(size: Float): BufferGeometry {
        val halfSize = size / 2f
        val geometry = BufferGeometry()

        // Create grid vertices
        val positions = mutableListOf<Float>()

        // Outer rectangle
        positions.addAll(listOf(-halfSize, 0f, -halfSize))
        positions.addAll(listOf(halfSize, 0f, -halfSize))

        positions.addAll(listOf(halfSize, 0f, -halfSize))
        positions.addAll(listOf(halfSize, 0f, halfSize))

        positions.addAll(listOf(halfSize, 0f, halfSize))
        positions.addAll(listOf(-halfSize, 0f, halfSize))

        positions.addAll(listOf(-halfSize, 0f, halfSize))
        positions.addAll(listOf(-halfSize, 0f, -halfSize))

        // Cross through center
        positions.addAll(listOf(-halfSize, 0f, 0f))
        positions.addAll(listOf(halfSize, 0f, 0f))

        positions.addAll(listOf(0f, 0f, -halfSize))
        positions.addAll(listOf(0f, 0f, halfSize))

        // Normal indicator
        positions.addAll(listOf(0f, 0f, 0f))
        positions.addAll(listOf(0f, size * 0.5f, 0f))

        geometry.setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))
        return geometry
    }

    private fun updateOrientation() {
        // Position at closest point to origin on plane
        position.copy(_plane.normal).multiplyScalar(-_plane.constant)

        // Rotate to match plane normal
        val up = Vector3(0f, 1f, 0f)
        if (kotlin.math.abs(_plane.normal.dot(up)) < 0.99999f) {
            quaternion.setFromUnitVectors(up, _plane.normal)
        } else if (_plane.normal.y < 0) {
            quaternion.set(1f, 0f, 0f, 0f)
        }
    }

    /**
     * Updates the helper to match a new plane.
     */
    fun update(plane: Plane) {
        _plane = plane.clone()
        updateOrientation()
    }

    /**
     * Sets the helper color.
     */
    fun setColor(color: Color) {
        _color = color.clone()
        material.color = color.clone()
    }

    /**
     * Sets the helper size.
     */
    fun setSize(size: Float) {
        _size = size
        // Regenerate geometry with new size
        geometry.dispose()
        geometry = createPlaneGeometry(_size)
    }

    /**
     * Disposes of resources.
     */
    fun dispose() {
        geometry.dispose()
    }
}

/**
 * Simple plane representation for PlaneHelper.
 */
data class Plane(
    val normal: Vector3 = Vector3(0f, 1f, 0f),
    val constant: Float = 0f
) {
    fun clone() = Plane(normal.clone(), constant)
}
