package io.materia.physics

import io.materia.core.math.Box3
import io.materia.core.math.Matrix3
import io.materia.core.math.Matrix4
import io.materia.core.math.Vector3

/**
 * JVM implementations of physics shapes
 */

// Base collision shape implementation
abstract class JvmCollisionShape : CollisionShape {
    override val margin: Float = 0.04f
    override val localScaling: Vector3 = Vector3.ONE
    protected var _boundingBox: Box3 = Box3()
    override val boundingBox: Box3
        get() = _boundingBox

    override fun getVolume(): Float = boundingBox.getSize().let { it.x * it.y * it.z }
    override fun getSurfaceArea(): Float =
        boundingBox.getSize().let { 2f * (it.x * it.y + it.y * it.z + it.z * it.x) }

    override fun isConvex(): Boolean =
        shapeType != ShapeType.TRIANGLE_MESH && shapeType != ShapeType.HEIGHTFIELD

    override fun isCompound(): Boolean = shapeType == ShapeType.COMPOUND

    override fun localGetSupportingVertex(direction: Vector3): Vector3 = direction
    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 = direction
    override fun calculateLocalInertia(mass: Float): Vector3 {
        val inertia = calculateInertia(mass)
        return Vector3(inertia.elements[0], inertia.elements[4], inertia.elements[8])
    }

    override fun serialize(): ByteArray = ByteArray(0) // Basic serialization stub
    override fun clone(): CollisionShape = this // Should be overridden in subclasses
}

// Box shape implementation
class JvmBoxShape(override val halfExtents: Vector3) : JvmCollisionShape(), BoxShape {
    override val shapeType: ShapeType = ShapeType.BOX

    init {
        _boundingBox = Box3(halfExtents.times(-1f), halfExtents)
    }

    override fun getHalfExtentsWithMargin(): Vector3 = halfExtents + Vector3(margin, margin, margin)
    override fun getHalfExtentsWithoutMargin(): Vector3 = halfExtents

    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateBoxInertia(mass, halfExtents * 2f)

    override fun getVolume(): Float = 8f * halfExtents.x * halfExtents.y * halfExtents.z
    override fun getSurfaceArea(): Float =
        8f * (halfExtents.x * halfExtents.y + halfExtents.y * halfExtents.z + halfExtents.z * halfExtents.x)
}

// Sphere shape implementation
class JvmSphereShape(override val radius: Float) : JvmCollisionShape(), SphereShape {
    override val shapeType: ShapeType = ShapeType.SPHERE

    init {
        val r = Vector3(radius, radius, radius)
        _boundingBox = Box3(r.times(-1f), r)
    }

    override fun getRadiusWithMargin(): Float = radius + margin
    override fun getRadiusWithoutMargin(): Float = radius

    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateSphereInertia(mass, radius)

    override fun getVolume(): Float =
        (4f / 3f) * kotlin.math.PI.toFloat() * radius * radius * radius

    override fun getSurfaceArea(): Float = 4f * kotlin.math.PI.toFloat() * radius * radius
}

// Capsule shape implementation
class JvmCapsuleShape(
    override val radius: Float,
    override val height: Float
) : JvmCollisionShape(), CapsuleShape {
    override val shapeType: ShapeType = ShapeType.CAPSULE
    override val upAxis: Int = 1 // Y-axis

    init {
        val halfHeight = height / 2f + radius
        _boundingBox =
            Box3(Vector3(-radius, -halfHeight, -radius), Vector3(radius, halfHeight, radius))
    }

    override fun getHalfHeight(): Float = height / 2f

    override fun calculateInertia(mass: Float): Matrix3 {
        // Approximate capsule as cylinder for inertia calculation
        return PhysicsUtils.calculateCylinderInertia(mass, radius, height)
    }
}

// Cylinder shape implementation
class JvmCylinderShape(override val halfExtents: Vector3) : JvmCollisionShape(), CylinderShape {
    override val shapeType: ShapeType = ShapeType.CYLINDER
    override val upAxis: Int = 1 // Y-axis

    init {
        _boundingBox = Box3(halfExtents.times(-1f), halfExtents)
    }

    override fun getRadius(): Float = maxOf(halfExtents.x, halfExtents.z)
    override fun getHalfHeight(): Float = halfExtents.y

    override fun calculateInertia(mass: Float): Matrix3 =
        PhysicsUtils.calculateCylinderInertia(mass, getRadius(), halfExtents.y * 2f)
}

// Cone shape implementation
class JvmConeShape(
    override val radius: Float,
    override val height: Float
) : JvmCollisionShape(), ConeShape {
    override val shapeType: ShapeType = ShapeType.CONE
    override val upAxis: Int = 1 // Y-axis

    init {
        val halfHeight = height / 2f
        _boundingBox =
            Box3(Vector3(-radius, -halfHeight, -radius), Vector3(radius, halfHeight, radius))
    }

    override fun getConeRadius(): Float = radius
    override fun getConeHeight(): Float = height

    override fun calculateInertia(mass: Float): Matrix3 {
        // Cone inertia formula
        val r2 = radius * radius
        val h2 = height * height
        return Matrix3(
            floatArrayOf(
                mass * (0.15f * h2 + 0.3f * r2), 0f, 0f,
                0f, mass * 0.3f * r2, 0f,
                0f, 0f, mass * (0.15f * h2 + 0.3f * r2)
            )
        )
    }
}

// Convex hull shape implementation
class JvmConvexHullShape(vertices: FloatArray) : JvmCollisionShape(), ConvexHullShape {
    override val shapeType: ShapeType = ShapeType.CONVEX_HULL
    override val vertices: FloatArray = vertices.copyOf()
    override val numVertices: Int = vertices.size / 3

    private val points = mutableListOf<Vector3>()

    init {
        for (i in vertices.indices step 3) {
            if (i + 2 < vertices.size) {
                points.add(Vector3(vertices[i], vertices[i + 1], vertices[i + 2]))
            }
        }
        recalculateBounds()
    }

    override fun addPoint(point: Vector3, recalculateLocalAABB: Boolean) {
        points.add(point)
        if (recalculateLocalAABB) {
            recalculateBounds()
        }
    }

    override fun getScaledPoint(index: Int): Vector3 =
        if (index < points.size) points[index] * localScaling else Vector3.ZERO

    override fun getUnscaledPoints(): List<Vector3> = points.toList()

    override fun optimizeConvexHull() {
        // Basic optimization: remove duplicate points
        val uniquePoints = points.distinct()
        points.clear()
        points.addAll(uniquePoints)
        recalculateBounds()
    }

    private fun recalculateBounds() {
        if (points.isEmpty()) {
            _boundingBox = Box3(Vector3.ZERO, Vector3.ZERO)
            return
        }

        var min = points[0].copy()
        var max = points[0].copy()

        points.forEach { point ->
            min = min.min(point)
            max = max.max(point)
        }

        _boundingBox = Box3(min, max)
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        // Approximate as box for inertia
        val size = boundingBox.getSize()
        return PhysicsUtils.calculateBoxInertia(mass, size)
    }
}

// Triangle mesh shape implementation
class JvmTriangleMeshShape(
    override val vertices: FloatArray,
    override val indices: IntArray
) : JvmCollisionShape(), TriangleMeshShape {
    override val shapeType: ShapeType = ShapeType.TRIANGLE_MESH
    override val triangleCount: Int = indices.size / 3

    private val triangles = mutableListOf<Triangle>()

    init {
        // Build triangles from vertices and indices
        for (i in indices.indices step 3) {
            if (i + 2 < indices.size) {
                val i0 = indices[i] * 3
                val i1 = indices[i + 1] * 3
                val i2 = indices[i + 2] * 3

                if (i0 + 2 < vertices.size && i1 + 2 < vertices.size && i2 + 2 < vertices.size) {
                    triangles.add(
                        Triangle(
                            Vector3(vertices[i0], vertices[i0 + 1], vertices[i0 + 2]),
                            Vector3(vertices[i1], vertices[i1 + 1], vertices[i1 + 2]),
                            Vector3(vertices[i2], vertices[i2 + 1], vertices[i2 + 2])
                        )
                    )
                }
            }
        }
        recalculateBounds()
    }

    override fun getTriangle(index: Int): Triangle =
        if (index < triangles.size) triangles[index] else Triangle(
            Vector3.ZERO,
            Vector3.ZERO,
            Vector3.ZERO
        )

    override fun processAllTriangles(
        callback: TriangleCallback,
        aabbMin: Vector3,
        aabbMax: Vector3
    ) {
        triangles.forEachIndexed { index, triangle ->
            // Check if triangle intersects AABB
            if (triangleIntersectsAABB(triangle, aabbMin, aabbMax)) {
                callback.processTriangle(triangle, 0, index)
            }
        }
    }

    override fun buildBVH(): MeshBVH {
        // Simple BVH stub - would need proper implementation for production
        return MeshBVH(emptyList(), triangles)
    }

    private fun triangleIntersectsAABB(
        triangle: Triangle,
        aabbMin: Vector3,
        aabbMax: Vector3
    ): Boolean {
        // Simple check: if any vertex is inside AABB or triangle bbox overlaps AABB
        val triMin = triangle.vertex0.min(triangle.vertex1).min(triangle.vertex2)
        val triMax = triangle.vertex0.max(triangle.vertex1).max(triangle.vertex2)

        return !(triMax.x < aabbMin.x || triMin.x > aabbMax.x ||
                triMax.y < aabbMin.y || triMin.y > aabbMax.y ||
                triMax.z < aabbMin.z || triMin.z > aabbMax.z)
    }

    private fun recalculateBounds() {
        if (vertices.isEmpty()) {
            _boundingBox = Box3(Vector3.ZERO, Vector3.ZERO)
            return
        }

        var min = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        for (i in vertices.indices step 3) {
            if (i + 2 < vertices.size) {
                val v = Vector3(vertices[i], vertices[i + 1], vertices[i + 2])
                min = min.min(v)
                max = max.max(v)
            }
        }

        _boundingBox = Box3(min, max)
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        // Triangle mesh usually used for static bodies
        return Matrix3.IDENTITY
    }
}

// Heightfield shape implementation
class JvmHeightfieldShape(
    override val width: Int,
    override val height: Int,
    override val heightData: FloatArray
) : JvmCollisionShape(), HeightfieldShape {
    override val shapeType: ShapeType = ShapeType.HEIGHTFIELD
    override val maxHeight: Float = heightData.maxOrNull() ?: 0f
    override val minHeight: Float = heightData.minOrNull() ?: 0f
    override val upAxis: Int = 1 // Y-axis

    init {
        val halfWidth = width * 0.5f
        val halfDepth = height * 0.5f
        _boundingBox = Box3(
            Vector3(-halfWidth, minHeight, -halfDepth),
            Vector3(halfWidth, maxHeight, halfDepth)
        )
    }

    override fun getHeightAtPoint(x: Float, z: Float): Float {
        val xi = ((x + width * 0.5f).toInt()).coerceIn(0, width - 1)
        val zi = ((z + height * 0.5f).toInt()).coerceIn(0, height - 1)
        return heightData[zi * width + xi]
    }

    override fun setHeightValue(x: Int, z: Int, height: Float) {
        if (x in 0 until width && z in 0 until this.height) {
            heightData[z * width + x] = height
        }
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        // Heightfield usually used for static terrain
        return Matrix3.IDENTITY
    }
}

// Compound shape implementation
class JvmCompoundShape : JvmCollisionShape(), CompoundShape {
    override val shapeType: ShapeType = ShapeType.COMPOUND
    override val childShapes: MutableList<ChildShape> = mutableListOf()

    override fun addChildShape(transform: Matrix4, shape: CollisionShape): PhysicsResult<Unit> {
        childShapes.add(ChildShape(transform, shape))
        recalculateLocalAabb()
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit> {
        childShapes.removeAll { it.shape == shape }
        recalculateLocalAabb()
        return PhysicsOperationResult.Success(Unit)
    }

    override fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit> {
        if (index in childShapes.indices) {
            childShapes.removeAt(index)
            recalculateLocalAabb()
            return PhysicsOperationResult.Success(Unit)
        }
        return PhysicsOperationResult.Error<Unit>(PhysicsException.InvalidOperation("Index out of bounds"))
    }

    override fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit> {
        if (index in childShapes.indices) {
            childShapes[index] = ChildShape(transform, childShapes[index].shape)
            recalculateLocalAabb()
            return PhysicsOperationResult.Success(Unit)
        }
        return PhysicsOperationResult.Error<Unit>(PhysicsException.InvalidOperation("Index out of bounds"))
    }

    override fun recalculateLocalAabb() {
        if (childShapes.isEmpty()) {
            _boundingBox = Box3(Vector3.ZERO, Vector3.ZERO)
            return
        }

        var min = Vector3(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        var max = Vector3(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

        childShapes.forEach { child ->
            val childBox = child.shape.boundingBox
            val transformedMin = child.transform.multiplyPoint3(childBox.min)
            val transformedMax = child.transform.multiplyPoint3(childBox.max)

            min = min.min(transformedMin)
            max = max.max(transformedMax)
        }

        _boundingBox = Box3(min, max)
    }

    override fun calculateInertia(mass: Float): Matrix3 {
        // Calculate combined inertia of all child shapes
        var totalInertia = Matrix3.ZERO

        childShapes.forEach { child ->
            val childMass = mass / childShapes.size // Simple uniform distribution
            val childInertia = child.shape.calculateInertia(childMass)

            // Apply parallel axis theorem
            val offset = child.transform.getTranslation()
            val offsetSquared = offset.lengthSquared()
            val parallelAxisInertia = Matrix3.IDENTITY.multiplyScalar(childMass * offsetSquared)

            totalInertia = totalInertia + childInertia + parallelAxisInertia
        }

        return totalInertia
    }
}