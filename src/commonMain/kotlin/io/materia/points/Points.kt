package io.materia.points

import io.materia.core.scene.Object3D
import io.materia.geometry.BufferGeometry
import io.materia.core.scene.Material
import io.materia.raycaster.Raycaster
import io.materia.raycaster.Intersection
import io.materia.core.math.Vector3
import io.materia.core.math.Matrix4
import io.materia.core.math.Ray
import io.materia.core.math.Sphere

/**
 * Points - Particle system for rendering point clouds
 * T091 - Render point clouds with per-point colors/sizes
 *
 * Efficient rendering of large numbers of points, commonly used for:
 * - Point clouds from 3D scanners
 * - Particle effects
 * - Star fields
 * - Scientific visualizations
 */
open class Points(
    var geometry: BufferGeometry,
    var material: Material? = null
) : Object3D() {

    override val type = "Points"

    /**
     * Morph target influences for animated points
     */
    var morphTargetInfluences: MutableList<Float>? = null
    var morphTargetDictionary: MutableMap<String, Int>? = null

    /**
     * Updates morph targets to match geometry
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
                morphTargets.forEachIndexed { index, _ ->
                    dictionary[index.toString()] = index
                }
            }
        } else {
            morphTargetInfluences = null
            morphTargetDictionary = null
        }
    }

    /**
     * Raycast against points
     * Tests if ray intersects with any points considering their size
     */
    fun raycast(raycaster: Raycaster, intersects: MutableList<Intersection>) {
        val ray = raycaster.ray
        val threshold = raycaster.params.points.threshold

        val worldMatrix = this.matrixWorld
        val inverseMatrix = Matrix4().copy(worldMatrix).invert()

        // Transform ray to local space
        val localRay = ray.clone().applyMatrix4(inverseMatrix)

        // Check bounding sphere first
        geometry.computeBoundingSphere()
        val boundingSphere = geometry.boundingSphere
        if (boundingSphere != null && !localRay.intersectsSphere(boundingSphere)) {
            return
        }

        // Get positions attribute
        val positions = geometry.attributes["position"] ?: return
        val positionArray = positions.array

        // Per-point colors and sizes if available
        val colors = geometry.attributes["color"]
        val sizes = geometry.attributes["size"]

        val testPoint = Vector3()

        // Test each point
        for (i in 0 until positions.count) {
            val index = i * 3
            testPoint.set(
                positionArray[index],
                positionArray[index + 1],
                positionArray[index + 2]
            )

            // Get point size
            val pointSize = sizes?.array?.get(i) ?: (material as? PointsMaterial)?.size ?: 1f
            val scaledThreshold = threshold * pointSize

            // Distance from ray to point
            val rayPointDistanceSq = localRay.distanceSqToPoint(testPoint)

            if (rayPointDistanceSq < scaledThreshold * scaledThreshold) {
                // Hit! Transform back to world space
                testPoint.applyMatrix4(worldMatrix)

                val distance = raycaster.ray.origin.distanceTo(testPoint)

                if (distance < raycaster.near || distance > raycaster.far) continue

                intersects.add(
                    Intersection(
                        distance = distance,
                        point = testPoint.clone(),
                        index = i,
                        `object` = this
                    )
                )
            }
        }

        // Sort by distance
        intersects.sortBy { it.distance }
    }

    /**
     * Copy from another Points object
     */
    fun copy(source: Points, recursive: Boolean = true): Points {
        super.copy(source, recursive)

        this.geometry = source.geometry
        this.material = source.material

        source.morphTargetInfluences?.let {
            this.morphTargetInfluences = it.toMutableList()
        }
        source.morphTargetDictionary?.let {
            this.morphTargetDictionary = it.toMutableMap()
        }

        return this
    }

    /**
     * Clone this Points object
     */
    override fun clone(recursive: Boolean): Points {
        return Points(geometry, material).copy(this, recursive)
    }
}

/**
 * Default raycaster parameters for Points
 */
data class PointsRaycastParams(
    var threshold: Float = 1f  // Default threshold for point picking
)