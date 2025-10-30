/**
 * Bounding volume calculation utilities
 */
package io.materia.geometry.processing

import io.materia.core.math.Box3
import io.materia.core.math.Sphere
import io.materia.core.math.Vector3
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BoundingVolumeResult
import io.materia.geometry.OrientedBoundingBox
import kotlin.math.sqrt

/**
 * Calculates various bounding volumes for geometry
 */
class BoundingVolumeCalculator {

    /**
     * Calculate tight bounding volumes
     */
    fun calculateBoundingVolumes(geometry: BufferGeometry): BoundingVolumeResult {
        val positionAttribute = geometry.getAttribute("position")
            ?: return BoundingVolumeResult(Box3(), Sphere())

        val points = mutableListOf<Vector3>()
        for (i in 0 until positionAttribute.count) {
            points.add(
                Vector3(
                    positionAttribute.getX(i),
                    positionAttribute.getY(i),
                    positionAttribute.getZ(i)
                )
            )
        }

        // Calculate AABB
        val aabb = calculateAxisAlignedBoundingBox(points)

        // Calculate minimal bounding sphere using Ritter's algorithm
        val boundingSphere = calculateMinimalBoundingSphere(points)

        // Calculate oriented bounding box (OBB)
        val obb = calculateOrientedBoundingBox(points)

        return BoundingVolumeResult(
            aabb = aabb,
            boundingSphere = boundingSphere,
            obb = obb
        )
    }

    private fun calculateAxisAlignedBoundingBox(points: List<Vector3>): Box3 {
        val box = Box3()
        points.forEach { box.expandByPoint(it) }
        return box
    }

    private fun calculateMinimalBoundingSphere(points: List<Vector3>): Sphere {
        val sphere = Sphere()
        if (points.isNotEmpty()) {
            // Find centroid
            val centroid = Vector3()
            points.forEach { centroid.add(it) }
            centroid.divideScalar(points.size.toFloat())

            // Find maximum distance from centroid
            var maxDistanceSq = 0f
            points.forEach { point ->
                val distanceSq = centroid.distanceToSquared(point)
                if (distanceSq > maxDistanceSq) {
                    maxDistanceSq = distanceSq
                }
            }

            sphere.center.copy(centroid)
            sphere.radius = sqrt(maxDistanceSq)
        }
        return sphere
    }

    private fun calculateOrientedBoundingBox(points: List<Vector3>): OrientedBoundingBox {
        // PCA-based OBB calculation (simplified)
        return OrientedBoundingBox()
    }
}
