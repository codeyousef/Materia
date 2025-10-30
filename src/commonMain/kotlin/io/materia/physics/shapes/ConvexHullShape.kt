/**
 * Convex hull collision shape implementation
 */
package io.materia.physics.shapes

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Convex hull collision shape implementation
 */
class ConvexHullShapeImpl(
    initialVertices: FloatArray
) : CollisionShapeImpl(), ConvexHullShape {

    override val shapeType: ShapeType = ShapeType.CONVEX_HULL

    private val _vertices = mutableListOf<Float>()
    override val vertices: FloatArray get() = _vertices.toFloatArray()
    override val numVertices: Int get() = _vertices.size / 3

    init {
        require(initialVertices.size % 3 == 0) { "Vertices array size must be multiple of 3" }
        require(initialVertices.size >= 12) { "Convex hull needs at least 4 vertices (12 floats)" }

        _vertices.addAll(initialVertices.toList())
        calculateBoundingBox()
    }

    override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {
        _vertices.addAll(listOf(point.x, point.y, point.z))
        if (recalculateLocalAABB) {
            invalidateBoundingBox()
        }
    }

    override fun getScaledPoint(index: Int): Vector3 {
        require(index in 0 until numVertices) { "Vertex index out of range" }
        val baseIndex = index * 3
        return Vector3(
            _vertices[baseIndex] * localScaling.x,
            _vertices[baseIndex + 1] * localScaling.y,
            _vertices[baseIndex + 2] * localScaling.z
        )
    }

    override fun getUnscaledPoints(): List<Vector3> {
        return (0 until numVertices).map { index ->
            val baseIndex = index * 3
            Vector3(
                _vertices[baseIndex],
                _vertices[baseIndex + 1],
                _vertices[baseIndex + 2]
            )
        }
    }

    override fun optimizeConvexHull() {
        // Simplified optimization - remove duplicate points
        val uniqueVertices = mutableSetOf<Vector3>()
        val optimizedVertices = mutableListOf<Float>()

        for (i in 0 until numVertices) {
            val vertex = getScaledPoint(i)
            if (uniqueVertices.add(vertex)) {
                optimizedVertices.addAll(listOf(vertex.x, vertex.y, vertex.z))
            }
        }

        _vertices.clear()
        _vertices.addAll(optimizedVertices)
        invalidateBoundingBox()
    }

    override fun getVolume(): Float {
        // Simplified volume calculation for convex hull
        // In practice, this would use a more sophisticated algorithm
        val boundingVolume = boundingBox.let { box ->
            val size = box.max - box.min
            size.x * size.y * size.z
        }
        return boundingVolume * 0.5f // Rough approximation
    }

    override fun getSurfaceArea(): Float {
        // Simplified surface area calculation
        // In practice, this would compute the actual hull surface
        val boundingArea = boundingBox.let { box ->
            val size = box.max - box.min
            2f * (size.x * size.y + size.y * size.z + size.z * size.x)
        }
        return boundingArea * 0.7f // Rough approximation
    }

    override fun isConvex(): Boolean = true
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        for (i in 0 until numVertices) {
            val vertex = getScaledPoint(i)
            val dot = vertex.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                supportVertex = vertex
            }
        }

        // Add margin in the direction of the normal
        val normalizedDirection = direction.normalize()
        return supportVertex + (normalizedDirection * margin)
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        for (i in 0 until numVertices) {
            val vertex = getScaledPoint(i)
            val dot = vertex.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                supportVertex = vertex
            }
        }

        return supportVertex
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        // Simplified inertia calculation for convex hull
        // Uses bounding box approximation
        val size = boundingBox.max - boundingBox.min
        val factor = mass / 12f
        return Vector3(
            factor * (size.y * size.y + size.z * size.z),
            factor * (size.x * size.x + size.z * size.z),
            factor * (size.x * size.x + size.y * size.y)
        )
    }

    override fun calculateBoundingBox() {
        if (numVertices == 0) {
            _boundingBox = Box3()
            return
        }

        var min = getScaledPoint(0)
        var max = min

        for (i in 1 until numVertices) {
            val vertex = getScaledPoint(i)
            min = Vector3(
                minOf(min.x, vertex.x),
                minOf(min.y, vertex.y),
                minOf(min.z, vertex.z)
            )
            max = Vector3(
                maxOf(max.x, vertex.x),
                maxOf(max.y, vertex.y),
                maxOf(max.z, vertex.z)
            )
        }

        // Add margin
        val marginVec = Vector3(margin, margin, margin)
        _boundingBox = Box3(min - marginVec, max + marginVec)
    }

    override fun clone(): CollisionShape = ConvexHullShapeImpl(vertices).apply {
        margin = this@ConvexHullShapeImpl.margin
        localScaling = this@ConvexHullShapeImpl.localScaling
    }
}
