/**
 * Triangle mesh collision shape implementation (for static geometry)
 */
package io.materia.physics.shapes

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Triangle mesh collision shape implementation (for static geometry)
 */
class TriangleMeshShapeImpl(
    override val vertices: FloatArray,
    override val indices: IntArray
) : CollisionShapeImpl(), TriangleMeshShape {

    override val shapeType: ShapeType = ShapeType.TRIANGLE_MESH
    override val triangleCount: Int = indices.size / 3

    private var _bvh: MeshBVH? = null

    init {
        require(vertices.size % 3 == 0) { "Vertices array size must be multiple of 3" }
        require(indices.size % 3 == 0) { "Indices array size must be multiple of 3" }
        require(indices.all { it >= 0 && it < vertices.size / 3 }) { "All indices must be valid vertex indices" }

        calculateBoundingBox()
    }

    override fun getTriangle(index: Int): io.materia.physics.Triangle {
        require(index in 0 until triangleCount) { "Triangle index out of range" }

        val baseIndex = index * 3
        val v0Index = indices[baseIndex] * 3
        val v1Index = indices[baseIndex + 1] * 3
        val v2Index = indices[baseIndex + 2] * 3

        return io.materia.physics.Triangle(
            Vector3(vertices[v0Index], vertices[v0Index + 1], vertices[v0Index + 2]) * localScaling,
            Vector3(vertices[v1Index], vertices[v1Index + 1], vertices[v1Index + 2]) * localScaling,
            Vector3(vertices[v2Index], vertices[v2Index + 1], vertices[v2Index + 2]) * localScaling
        )
    }

    override fun processAllTriangles(
        callback: TriangleCallback,
        aabbMin: Vector3,
        aabbMax: Vector3
    ) {
        for (i in 0 until triangleCount) {
            val triangle = getTriangle(i)

            // Simple AABB test
            val triangleBounds = Box3().apply {
                expandByPoint(triangle.vertex0)
                expandByPoint(triangle.vertex1)
                expandByPoint(triangle.vertex2)
            }

            if (triangleBounds.intersectsBox(Box3(aabbMin, aabbMax))) {
                callback.processTriangle(triangle, 0, i)
            }
        }
    }

    override fun buildBVH(): MeshBVH {
        if (_bvh == null) {
            _bvh = buildBVHRecursive(0, triangleCount)
        }
        return _bvh ?: buildBVHRecursive(0, triangleCount)
    }

    private fun buildBVHRecursive(startTriangle: Int, triangleCount: Int): MeshBVH {
        // Simplified BVH construction
        val triangles = (startTriangle until startTriangle + triangleCount).map { getTriangle(it) }

        // Calculate bounding box for all triangles
        val bounds = Box3()
        triangles.forEach { triangle ->
            bounds.expandByPoint(triangle.vertex0)
            bounds.expandByPoint(triangle.vertex1)
            bounds.expandByPoint(triangle.vertex2)
        }

        // Create leaf node for small triangle counts
        if (triangleCount <= 4) {
            val node = BVHNode(
                bounds = bounds,
                leftChild = -1,
                rightChild = -1,
                triangleOffset = startTriangle,
                triangleCount = triangleCount
            )
            return MeshBVH(listOf(node), triangles)
        }

        // For larger counts, this would implement proper spatial partitioning
        // For now, create a simple single-node BVH
        val node = BVHNode(
            bounds = bounds,
            leftChild = -1,
            rightChild = -1,
            triangleOffset = startTriangle,
            triangleCount = triangleCount
        )

        return MeshBVH(listOf(node), triangles)
    }

    override fun getVolume(): Float {
        // Triangle meshes are typically hollow, so volume calculation is complex
        // Return bounding box volume as approximation
        val size = boundingBox.max - boundingBox.min
        return size.x * size.y * size.z
    }

    override fun getSurfaceArea(): Float {
        var totalArea = 0f
        for (i in 0 until triangleCount) {
            val triangle = getTriangle(i)
            val edge1 = triangle.vertex1 - triangle.vertex0
            val edge2 = triangle.vertex2 - triangle.vertex0
            val cross = edge1.cross(edge2)
            totalArea = totalArea + cross.length() * 0.5f
        }
        return totalArea
    }

    override fun isConvex(): Boolean = false
    override fun isCompound(): Boolean = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        // Check all vertices
        for (i in 0 until vertices.size / 3) {
            val vertex = Vector3(
                vertices[(i * 3)] * localScaling.x,
                vertices[i * 3 + 1] * localScaling.y,
                vertices[i * 3 + 2] * localScaling.z
            )
            val dot = vertex.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                supportVertex = vertex
            }
        }

        return supportVertex
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        return localGetSupportingVertex(direction)
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        // Triangle mesh shapes are typically static, so return zero inertia
        return Vector3.ZERO
    }

    override fun calculateBoundingBox() {
        if (vertices.isEmpty()) {
            _boundingBox = Box3()
            return
        }

        var min = Vector3(vertices[0], vertices[1], vertices[2]) * localScaling
        var max = min

        for (i in 1 until vertices.size / 3) {
            val vertex = Vector3(
                vertices[(i * 3)] * localScaling.x,
                vertices[i * 3 + 1] * localScaling.y,
                vertices[i * 3 + 2] * localScaling.z
            )
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

        _boundingBox = Box3(min, max)
    }

    override fun clone(): CollisionShape =
        TriangleMeshShapeImpl(vertices.copyOf(), indices.copyOf()).apply {
            margin = this@TriangleMeshShapeImpl.margin
            localScaling = this@TriangleMeshShapeImpl.localScaling
        }
}
