/**
 * Light probe placement strategies
 * Automatic and manual probe positioning
 */
package io.materia.lighting.probe

import io.materia.core.math.Box3
import io.materia.core.math.Vector3
import io.materia.core.scene.Scene
import io.materia.lighting.LightProbe
import io.materia.lighting.LightProbeImpl
import kotlin.math.min
import kotlin.math.pow

/**
 * Handles automatic and manual probe placement
 */
class ProbePlacementStrategy {
    /**
     * Automatically place probes based on scene geometry and density
     */
    suspend fun autoPlaceProbes(scene: Scene, density: Float): List<LightProbe> {
        val sceneBounds = calculateSceneBounds(scene)
        val size = sceneBounds.getSize()
        val volume = size.x * size.y * size.z
        val spacing = (volume / density).pow(1f / 3f)

        return placeProbesOnGrid(sceneBounds, Vector3(spacing, spacing, spacing))
    }

    /**
     * Place probes on a regular grid
     */
    suspend fun placeProbesOnGrid(bounds: Box3, spacing: Vector3): List<LightProbe> {
        val probes = mutableListOf<LightProbe>()

        val start = bounds.min
        val end = bounds.max

        var x = start.x
        while (x <= end.x) {
            var y = start.y
            while (y <= end.y) {
                var z = start.z
                while (z <= end.z) {
                    val position = Vector3(x, y, z)
                    val distance = min(spacing.x, min(spacing.y, spacing.z)) * 1.5f

                    probes.add(LightProbeImpl(position, distance))

                    z += spacing.z
                }
                y += spacing.y
            }
            x += spacing.x
        }

        return probes
    }

    /**
     * Place probes at manual positions
     */
    suspend fun placeProbesManual(positions: List<Vector3>): List<LightProbe> {
        return positions.map { position ->
            LightProbeImpl(position, 10.0f) // Default distance
        }
    }

    private fun calculateSceneBounds(scene: Scene): Box3 {
        val bounds = Box3()

        // Iterate through all objects in the scene hierarchy
        scene.traverse { obj ->
            // Skip lights and non-renderable objects
            if (obj.type == "Mesh" || obj.type == "Group") {
                // Get object bounds in world space and transform to world space
                val objBounds = obj.getBoundingBox()
                objBounds.applyMatrix4(obj.matrixWorld)
                bounds.union(objBounds)
            }
        }

        // If no bounds found, use default
        if (bounds.isEmpty()) {
            bounds.min.set(-10f, -10f, -10f)
            bounds.max.set(10f, 10f, 10f)
        }

        return bounds
    }
}
