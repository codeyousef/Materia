/**
 * Light probe volume for spatial queries and interpolation
 * 3D grid structure for efficient probe lookups
 */
package io.materia.lighting.probe

import io.materia.core.math.Box3
import io.materia.core.math.Vector3
import io.materia.lighting.LightProbe
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 3D volume containing light probes for efficient spatial queries
 */
class ProbeVolume(
    val bounds: Box3,
    val probes: List<LightProbe>,
    val spacing: Vector3
) {
    private val gridProbes: Array<Array<Array<LightProbe>>>

    init {
        // Calculate grid dimensions
        val size = bounds.getSize()
        val gridSizeX = ceil(size.x / spacing.x).toInt() + 1
        val gridSizeY = ceil(size.y / spacing.y).toInt() + 1
        val gridSizeZ = ceil(size.z / spacing.z).toInt() + 1

        // Initialize grid
        gridProbes = Array(gridSizeX) { x ->
            Array(gridSizeY) { y ->
                Array(gridSizeZ) { z ->
                    val pos = bounds.min + Vector3(
                        x * spacing.x,
                        y * spacing.y,
                        z * spacing.z
                    )
                    findNearestProbe(probes, pos)
                }
            }
        }
    }

    /**
     * Get interpolated lighting at a position using trilinear interpolation
     */
    fun getInterpolatedLighting(position: Vector3): ProbeInfluence {
        // Convert position to grid coordinates
        val relativePos = position.clone().subtract(bounds.min)
        val gridPos = Vector3(
            relativePos.x / spacing.x,
            relativePos.y / spacing.y,
            relativePos.z / spacing.z
        )

        // Get grid cell indices
        val x = floor(gridPos.x).toInt().coerceIn(0, gridProbes.size - 2)
        val y = floor(gridPos.y).toInt().coerceIn(0, gridProbes[0].size - 2)
        val z = floor(gridPos.z).toInt().coerceIn(0, gridProbes[0][0].size - 2)

        // Calculate interpolation weights
        val fx = gridPos.x - x
        val fy = gridPos.y - y
        val fz = gridPos.z - z

        // Get 8 corner probes
        val cornerProbes = listOf(
            gridProbes[x][y][z],
            gridProbes[x + 1][y][z],
            gridProbes[x][y + 1][z],
            gridProbes[x + 1][y + 1][z],
            gridProbes[x][y][z + 1],
            gridProbes[x + 1][y][z + 1],
            gridProbes[x][y + 1][z + 1],
            gridProbes[x + 1][y + 1][z + 1]
        )

        // Calculate trilinear weights
        val weights = floatArrayOf(
            (1f - fx) * (1f - fy) * (1f - fz),
            fx * (1f - fy) * (1f - fz),
            (1f - fx) * fy * (1f - fz),
            fx * fy * (1f - fz),
            (1f - fx) * (1f - fy) * fz,
            fx * (1f - fy) * fz,
            (1f - fx) * (fy * fz),
            fx * (fy * fz)
        )

        // Find the probe with the highest weight for trilinear interpolation
        var maxWeight = 0f
        var bestProbe = cornerProbes[0]
        var totalWeight = 0f

        for (i in cornerProbes.indices) {
            totalWeight += weights[i]
            if (weights[i] > maxWeight) {
                maxWeight = weights[i]
                bestProbe = cornerProbes[i]
            }
        }

        return ProbeInfluence(bestProbe, totalWeight / cornerProbes.size)
    }

    private fun findNearestProbe(probes: List<LightProbe>, position: Vector3): LightProbe {
        return probes.minByOrNull { it.position.distanceTo(position) }
            ?: throw IllegalStateException("No probes available")
    }
}

/**
 * Probe influence result
 */
data class ProbeInfluence(
    val probe: LightProbe,
    val weight: Float
)

/**
 * Generates probe volumes from probe lists
 */
class ProbeVolumeGenerator {
    fun generateProbeVolume(probes: List<LightProbe>): ProbeVolume {
        // Calculate bounds of probe network
        val bounds = calculateProbeBounds(probes)

        // Determine grid resolution based on probe density
        val averageSpacing = ProbeNetworkOptimizer().calculateAverageSpacing(probes)

        return ProbeVolume(bounds, probes, averageSpacing)
    }

    private fun calculateProbeBounds(probes: List<LightProbe>): Box3 {
        val bounds = Box3()
        probes.forEach { bounds.expandByPoint(it.position) }
        return bounds
    }
}
