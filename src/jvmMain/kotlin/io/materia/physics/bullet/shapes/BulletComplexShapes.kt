/**
 * Bullet Physics Complex Shape Implementations
 * Provides advanced collision shapes (ConvexHull, TriangleMesh, Heightfield, Compound)
 */
package io.materia.physics.bullet.shapes

import io.materia.core.math.*
import io.materia.physics.*
import io.materia.physics.Triangle as PhysicsTriangle

/**
 * Bullet Convex Hull collision shape
 */
internal class BulletConvexHullShape(
    override val vertices: FloatArray
) : ConvexHullShape {
    override val shapeType = ShapeType.CONVEX_HULL
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val numVertices = vertices.size / 3
    override val boundingBox: Box3

    init {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE

        for (i in vertices.indices step 3) {
            minX = minOf(minX, vertices[i])
            minY = minOf(minY, vertices[i + 1])
            minZ = minOf(minZ, vertices[i + 2])
            maxX = maxOf(maxX, vertices[i])
            maxY = maxOf(maxY, vertices[i + 1])
            maxZ = maxOf(maxZ, vertices[i + 2])
        }

        boundingBox = Box3(
            Vector3(minX, minY, minZ),
            Vector3(maxX, maxY, maxZ)
        )
    }

    override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {}

    override fun getScaledPoint(index: Int): Vector3 {
        val i = index * 3
        return Vector3(vertices[i], vertices[i + 1], vertices[i + 2])
    }

    override fun getUnscaledPoints(): List<Vector3> {
        val points = mutableListOf<Vector3>()
        for (i in vertices.indices step 3) {
            points.add(Vector3(vertices[i], vertices[i + 1], vertices[i + 2]))
        }
        return points
    }

    override fun optimizeConvexHull() {}

    override fun calculateInertia(mass: Float): Matrix3 {
        val size = boundingBox.getSize()
        return Matrix3(
            floatArrayOf(
                mass * (size.y * size.y + size.z * size.z) / 12f, 0f, 0f,
                0f, mass * (size.x * size.x + size.z * size.z) / 12f, 0f,
                0f, 0f, mass * (size.x * size.x + size.y * size.y) / 12f
            )
        )
    }

    override fun getVolume(): Float {
        val size = boundingBox.getSize()
        return size.x * size.y * size.z * 0.5f
    }

    override fun getSurfaceArea(): Float {
        val size = boundingBox.getSize()
        return 2f * (size.x * size.y + size.y * size.z + size.x * size.z)
    }

    override fun isConvex() = true
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        var maxDot = Float.MIN_VALUE
        var support = Vector3.ZERO

        for (i in vertices.indices step 3) {
            val v = Vector3(vertices[i], vertices[i + 1], vertices[i + 2])
            val dot = v.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                support = v
            }
        }

        return support.add(direction.normalize().multiply(margin))
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        var maxDot = Float.MIN_VALUE
        var support = Vector3.ZERO

        for (i in vertices.indices step 3) {
            val v = Vector3(vertices[i], vertices[i + 1], vertices[i + 2])
            val dot = v.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                support = v
            }
        }

        return support
    }

    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletConvexHullShape(vertices.copyOf())
}

/**
 * Bullet Triangle Mesh collision shape
 */
internal class BulletTriangleMeshShape(
    override val vertices: FloatArray,
    override val indices: IntArray
) : TriangleMeshShape {
    override val shapeType = ShapeType.TRIANGLE_MESH
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val triangleCount = indices.size / 3
    override val boundingBox: Box3

    init {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var maxZ = Float.MIN_VALUE

        for (i in vertices.indices step 3) {
            minX = minOf(minX, vertices[i])
            minY = minOf(minY, vertices[i + 1])
            minZ = minOf(minZ, vertices[i + 2])
            maxX = maxOf(maxX, vertices[i])
            maxY = maxOf(maxY, vertices[i + 1])
            maxZ = maxOf(maxZ, vertices[i + 2])
        }

        boundingBox = Box3(
            Vector3(minX, minY, minZ),
            Vector3(maxX, maxY, maxZ)
        )
    }

    override fun getTriangle(index: Int): PhysicsTriangle {
        val i = index * 3
        val i1 = indices[i] * 3
        val i2 = indices[i + 1] * 3
        val i3 = indices[i + 2] * 3

        return PhysicsTriangle(
            Vector3(vertices[i1], vertices[i1 + 1], vertices[i1 + 2]),
            Vector3(vertices[i2], vertices[i2 + 1], vertices[i2 + 2]),
            Vector3(vertices[i3], vertices[i3 + 1], vertices[i3 + 2])
        )
    }

    override fun processAllTriangles(
        callback: TriangleCallback,
        aabbMin: Vector3,
        aabbMax: Vector3
    ) {
        for (i in 0 until triangleCount) {
            val triangle = getTriangle(i)
            callback.processTriangle(triangle, 0, i)
        }
    }

    override fun buildBVH(): MeshBVH = MeshBVH(
        nodes = emptyList(),
        triangles = emptyList()
    )

    override fun calculateInertia(mass: Float): Matrix3 {
        val size = boundingBox.getSize()
        return Matrix3(
            floatArrayOf(
                mass * (size.y * size.y + size.z * size.z) / 12f, 0f, 0f,
                0f, mass * (size.x * size.x + size.z * size.z) / 12f, 0f,
                0f, 0f, mass * (size.x * size.x + size.y * size.y) / 12f
            )
        )
    }

    override fun getVolume(): Float {
        var volume = 0f
        for (i in 0 until triangleCount) {
            val tri = getTriangle(i)
            volume += tri.vertex0.dot(tri.vertex1.cross(tri.vertex2)) / 6f
        }
        return kotlin.math.abs(volume)
    }

    override fun getSurfaceArea(): Float {
        var area = 0f
        for (i in 0 until triangleCount) {
            val tri = getTriangle(i)
            val ab = tri.vertex1.subtract(tri.vertex0)
            val ac = tri.vertex2.subtract(tri.vertex0)
            area += ab.cross(ac).length() * 0.5f
        }
        return area
    }

    override fun isConvex() = false
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletTriangleMeshShape(vertices.copyOf(), indices.copyOf())
}

/**
 * Bullet Heightfield collision shape
 */
internal class BulletHeightfieldShape(
    override val width: Int,
    override val height: Int,
    override val heightData: FloatArray
) : HeightfieldShape {
    override val shapeType = ShapeType.HEIGHTFIELD
    override val margin = 0.04f
    override val localScaling = Vector3.ONE
    override val upAxis = 1
    override val maxHeight = heightData.maxOrNull() ?: 0f
    override val minHeight = heightData.minOrNull() ?: 0f
    override val boundingBox = Box3(
        Vector3(0f, minHeight, 0f),
        Vector3(width.toFloat(), maxHeight, height.toFloat())
    )

    override fun getHeightAtPoint(x: Float, z: Float): Float {
        val ix = x.toInt().coerceIn(0, width - 1)
        val iz = z.toInt().coerceIn(0, height - 1)
        return heightData[iz * width + ix]
    }

    override fun setHeightValue(x: Int, z: Int, height: Float) {
        if (x in 0 until width && z in 0 until this.height) {
            heightData[z * width + x] = height
        }
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        val size = boundingBox.getSize()
        return Matrix3(
            floatArrayOf(
                mass * (size.y * size.y + size.z * size.z) / 12f, 0f, 0f,
                0f, mass * (size.x * size.x + size.z * size.z) / 12f, 0f,
                0f, 0f, mass * (size.x * size.x + size.y * size.y) / 12f
            )
        )
    }

    override fun getVolume(): Float {
        val avgHeight = (maxHeight + minHeight) / 2f
        return width.toFloat() * height.toFloat() * avgHeight
    }

    override fun getSurfaceArea(): Float {
        return width.toFloat() * height.toFloat()
    }

    override fun isConvex() = false
    override fun isCompound() = false

    override fun localGetSupportingVertex(direction: Vector3) = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletHeightfieldShape(width, height, heightData.copyOf())
}

/**
 * Bullet Compound collision shape
 */
internal class BulletCompoundShape : CompoundShape {
    override val shapeType = ShapeType.COMPOUND
    override val margin = 0f
    override val localScaling = Vector3.ONE
    override val childShapes = mutableListOf<ChildShape>()
    override var boundingBox = Box3(Vector3.ZERO, Vector3.ZERO)
        private set

    override fun addChildShape(
        transform: Matrix4,
        shape: CollisionShape
    ): PhysicsOperationResult<Unit> {
        return try {
            childShapes.add(ChildShape(transform, shape))
            recalculateLocalAabb()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to add child shape"))
        }
    }

    override fun removeChildShape(shape: CollisionShape): PhysicsOperationResult<Unit> {
        return try {
            childShapes.removeAll { it.shape == shape }
            recalculateLocalAabb()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to remove child shape"))
        }
    }

    override fun removeChildShapeByIndex(index: Int): PhysicsOperationResult<Unit> {
        return try {
            if (index in childShapes.indices) {
                childShapes.removeAt(index)
                recalculateLocalAabb()
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to remove child shape"))
        }
    }

    override fun updateChildTransform(
        index: Int,
        transform: Matrix4
    ): PhysicsOperationResult<Unit> {
        return try {
            if (index in childShapes.indices) {
                childShapes[index] = ChildShape(transform, childShapes[index].shape)
                recalculateLocalAabb()
            }
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(PhysicsException.InvalidOperation("Failed to update child transform"))
        }
    }

    override fun recalculateLocalAabb() {
        if (childShapes.isEmpty()) {
            boundingBox = Box3(Vector3.ZERO, Vector3.ZERO)
            return
        }

        var min = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        for (child in childShapes) {
            val childBounds = child.shape.boundingBox
            val transformedMin = child.transform.transformPoint(childBounds.min)
            val transformedMax = child.transform.transformPoint(childBounds.max)

            min = min.min(transformedMin)
            max = max.max(transformedMax)
        }

        boundingBox = Box3(min, max)
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        var totalInertia = Matrix3.identity()
        val massPerChild = mass / childShapes.size.toFloat()

        for (child in childShapes) {
            val childInertia = child.shape.calculateInertia(massPerChild)
            totalInertia = totalInertia.add(childInertia)
        }

        return totalInertia
    }

    override fun getVolume(): Float {
        return childShapes.sumOf { it.shape.getVolume().toDouble() }.toFloat()
    }

    override fun getSurfaceArea(): Float {
        return childShapes.sumOf { it.shape.getSurfaceArea().toDouble() }.toFloat()
    }

    override fun isConvex() = false
    override fun isCompound() = true

    override fun localGetSupportingVertex(direction: Vector3) = Vector3.ZERO
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3) = Vector3.ZERO
    override fun calculateLocalInertia(mass: Float) = calculateInertia(mass).getDiagonal()

    override fun serialize() = ByteArray(0)
    override fun clone() = BulletCompoundShape().apply {
        for (child in this@BulletCompoundShape.childShapes) {
            addChildShape(child.transform, child.shape.clone())
        }
    }
}
