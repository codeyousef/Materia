/**
 * Factory object for creating collision shapes
 */
package io.materia.physics

import io.materia.core.math.Vector3
import io.materia.physics.shapes.*

/**
 * Factory object for creating collision shapes
 */
object CollisionShapeFactory {

    fun createBox(halfExtents: Vector3): BoxShape = BoxShapeImpl(halfExtents)

    fun createSphere(radius: Float): SphereShape = SphereShapeImpl(radius)

    fun createCapsule(radius: Float, height: Float, upAxis: Int = 1): CapsuleShape =
        CapsuleShapeImpl(radius, height, upAxis)

    fun createCylinder(halfExtents: Vector3, upAxis: Int = 1): CylinderShape =
        CylinderShapeImpl(halfExtents, upAxis)

    fun createCone(radius: Float, height: Float, upAxis: Int = 1): ConeShape =
        ConeShapeImpl(radius, height, upAxis)

    fun createConvexHull(vertices: FloatArray): ConvexHullShape =
        ConvexHullShapeImpl(vertices)

    fun createTriangleMesh(vertices: FloatArray, indices: IntArray): TriangleMeshShape =
        TriangleMeshShapeImpl(vertices, indices)

    fun createHeightfield(
        width: Int,
        height: Int,
        heightData: FloatArray,
        maxHeight: Float,
        minHeight: Float,
        upAxis: Int = 1
    ): HeightfieldShape =
        HeightfieldShapeImpl(width, height, heightData, maxHeight, minHeight, upAxis)

    fun createCompound(): CompoundShape = CompoundShapeImpl()
}
