/**
 * Data types for geometry processing
 * T024 - Geometry processing data models
 */
package io.materia.geometry

import io.materia.core.math.Box3
import io.materia.core.math.Sphere
import io.materia.core.math.Vector3

/**
 * Vertex merge result
 */
data class GeometryMergeResult(
    val geometry: BufferGeometry,
    val mergedVertices: Int
)

/**
 * Geometry optimization result
 */
data class GeometryOptimizationResult(
    val geometry: BufferGeometry,
    val optimizations: List<String>,
    val memorySavings: Int,
    val compressionRatio: Float
)

/**
 * Bounding volume calculation result
 */
data class BoundingVolumeResult(
    val aabb: Box3,
    val boundingSphere: Sphere,
    val obb: OrientedBoundingBox = OrientedBoundingBox()
)

/**
 * Oriented bounding box
 */
class OrientedBoundingBox {
    var center: Vector3 = Vector3()
    var axes: Array<Vector3> =
        arrayOf(Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f), Vector3(0f, 0f, 1f))
    var extents: Vector3 = Vector3()
}

/**
 * GPU optimization configuration
 */
data class GpuOptimizationOptions(
    val mergeVertices: Boolean = true,
    val mergeThreshold: Float = 0.001f,
    val generateIndices: Boolean = true,
    val optimizeVertexCache: Boolean = true,
    val generateNormals: Boolean = true,
    val generateTangents: Boolean = true
)

/**
 * Quality tier enumeration for adaptive performance
 */
enum class QualityTier {
    LOW, MEDIUM, HIGH, ULTRA
}

/**
 * Quality settings for different performance targets
 */
data class QualitySettings(
    val lodLevels: Int,
    val reductionFactor: Float,
    val mergeThreshold: Float,
    val generateTangents: Boolean
)
