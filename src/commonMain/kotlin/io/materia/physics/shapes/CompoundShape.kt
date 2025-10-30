/**
 * Compound collision shape implementation (combines multiple shapes)
 */
package io.materia.physics.shapes

import io.materia.core.math.*
import io.materia.physics.*

/**
 * Compound collision shape implementation (combines multiple shapes)
 */
class CompoundShapeImpl : CollisionShapeImpl(), CompoundShape {

    override val shapeType: ShapeType = ShapeType.COMPOUND

    private val _childShapes = mutableListOf<ChildShape>()
    override val childShapes: List<ChildShape> get() = _childShapes.toList()

    override fun addChildShape(transform: Matrix4, shape: CollisionShape): PhysicsResult<Unit> {
        return try {
            _childShapes.add(ChildShape(transform, shape))
            invalidateBoundingBox()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.ShapeCreationFailed(
                    "Failed to add child shape",
                    e
                )
            )
        }
    }

    override fun removeChildShape(shape: CollisionShape): PhysicsResult<Unit> {
        return try {
            _childShapes.removeAll { it.shape == shape }
            invalidateBoundingBox()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.ShapeCreationFailed(
                    "Failed to remove child shape",
                    e
                )
            )
        }
    }

    override fun removeChildShapeByIndex(index: Int): PhysicsResult<Unit> {
        return try {
            require(index in 0 until _childShapes.size) { "Child shape index out of range" }
            _childShapes.removeAt(index)
            invalidateBoundingBox()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.ShapeCreationFailed(
                    "Failed to remove child shape by index",
                    e
                )
            )
        }
    }

    override fun updateChildTransform(index: Int, transform: Matrix4): PhysicsResult<Unit> {
        return try {
            require(index in 0 until _childShapes.size) { "Child shape index out of range" }
            val childShape = _childShapes[index]
            _childShapes[index] = ChildShape(transform, childShape.shape)
            invalidateBoundingBox()
            PhysicsOperationResult.Success(Unit)
        } catch (e: Exception) {
            PhysicsOperationResult.Error(
                PhysicsException.ShapeCreationFailed(
                    "Failed to update child transform",
                    e
                )
            )
        }
    }

    override fun recalculateLocalAabb() {
        invalidateBoundingBox()
    }

    override fun getVolume(): Float {
        return _childShapes.sumOf { it.shape.getVolume().toDouble() }.toFloat()
    }

    override fun getSurfaceArea(): Float {
        return _childShapes.sumOf { it.shape.getSurfaceArea().toDouble() }.toFloat()
    }

    override fun isConvex(): Boolean = false
    override fun isCompound(): Boolean = true

    override fun localGetSupportingVertex(direction: Vector3): Vector3 {
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        for (childShape in _childShapes) {
            // Transform direction to child's local space
            val localDirection = childShape.transform.inverse().transformDirection(direction)
            val localSupport = childShape.shape.localGetSupportingVertex(localDirection)

            // Transform support vertex to compound's local space
            val worldSupport = childShape.transform.transformPoint(localSupport)

            val dot = worldSupport.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                supportVertex = worldSupport
            }
        }

        return supportVertex
    }

    override fun localGetSupportingVertexWithoutMargin(direction: Vector3): Vector3 {
        var maxDot = Float.NEGATIVE_INFINITY
        var supportVertex = Vector3.ZERO

        for (childShape in _childShapes) {
            // Transform direction to child's local space
            val localDirection = childShape.transform.inverse().transformDirection(direction)
            val localSupport =
                childShape.shape.localGetSupportingVertexWithoutMargin(localDirection)

            // Transform support vertex to compound's local space
            val worldSupport = childShape.transform.transformPoint(localSupport)

            val dot = worldSupport.dot(direction)
            if (dot > maxDot) {
                maxDot = dot
                supportVertex = worldSupport
            }
        }

        return supportVertex
    }

    override fun calculateLocalInertia(mass: Float): Vector3 {
        if (_childShapes.isEmpty()) return Vector3.ZERO

        // Distribute mass among child shapes based on their volume
        val totalVolume = getVolume()
        if (totalVolume <= 0f) return Vector3.ZERO

        var totalInertia = Matrix3.ZERO

        for (childShape in _childShapes) {
            val childVolume = childShape.shape.getVolume()
            val childMass = mass * (childVolume / totalVolume)
            val childInertia = childShape.shape.calculateInertia(childMass)

            // Transform inertia tensor to compound's coordinate system
            val rotation = childShape.transform.getRotation()
            val rotationMatrix = Matrix3.fromQuaternion(rotation)
            val transformedInertia = rotationMatrix * childInertia * rotationMatrix.transpose()

            totalInertia = totalInertia + transformedInertia
        }

        return Vector3(totalInertia.m00, totalInertia.m11, totalInertia.m22)
    }

    override fun calculateBoundingBox() {
        if (_childShapes.isEmpty()) {
            _boundingBox = Box3()
            return
        }

        _boundingBox = Box3()

        for (childShape in _childShapes) {
            val childBounds = childShape.shape.boundingBox

            // Transform child bounding box to compound's coordinate system
            val corners = listOf(
                Vector3(childBounds.min.x, childBounds.min.y, childBounds.min.z),
                Vector3(childBounds.max.x, childBounds.min.y, childBounds.min.z),
                Vector3(childBounds.min.x, childBounds.max.y, childBounds.min.z),
                Vector3(childBounds.max.x, childBounds.max.y, childBounds.min.z),
                Vector3(childBounds.min.x, childBounds.min.y, childBounds.max.z),
                Vector3(childBounds.max.x, childBounds.min.y, childBounds.max.z),
                Vector3(childBounds.min.x, childBounds.max.y, childBounds.max.z),
                Vector3(childBounds.max.x, childBounds.max.y, childBounds.max.z)
            )

            for (corner in corners) {
                val transformedCorner = childShape.transform.transformPoint(corner)
                _boundingBox.expandByPoint(transformedCorner)
            }
        }

        // Apply local scaling and margin
        val marginVec = Vector3(margin, margin, margin)
        _boundingBox = Box3(
            (_boundingBox.min - marginVec) * localScaling,
            (_boundingBox.max + marginVec) * localScaling
        )
    }

    override fun clone(): CollisionShape = CompoundShapeImpl().apply {
        margin = this@CompoundShapeImpl.margin
        localScaling = this@CompoundShapeImpl.localScaling
        for (childShape in this@CompoundShapeImpl._childShapes) {
            addChildShape(childShape.transform, childShape.shape.clone())
        }
    }
}
