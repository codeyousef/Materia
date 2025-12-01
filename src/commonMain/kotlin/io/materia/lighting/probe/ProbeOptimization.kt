/**
 * Light probe network optimization
 * Merges redundant probes and improves coverage
 */
package io.materia.lighting.probe

import io.materia.core.math.Vector3
import io.materia.lighting.LightProbe
import io.materia.lighting.LightProbeImpl

/**
 * Optimizes probe networks for better performance and coverage
 */
class ProbeNetworkOptimizer {
    /**
     * Optimize probe network by merging nearby probes
     */
    fun optimizeProbeNetwork(probes: List<LightProbe>): List<LightProbe> {
        val optimizedProbes = mutableListOf<LightProbe>()
        val processed = mutableSetOf<LightProbe>()

        for (probe in probes) {
            if (probe in processed) continue

            // Find nearby probes
            val nearby = probes.filter { other ->
                other != probe && probe.position.distanceTo(other.position) < probe.distance * 0.5f
            }

            if (nearby.isNotEmpty()) {
                // Merge nearby probes
                val mergedPosition = (probe.position + nearby.fold(Vector3.ZERO) { acc, p -> acc + p.position }) / (nearby.size + 1f)
                val mergedDistance = (probe.distance + nearby.sumOf { it.distance.toDouble() }) / (nearby.size + 1f)

                optimizedProbes.add(LightProbeImpl(mergedPosition, mergedDistance.toFloat()))
                processed.add(probe)
                processed.addAll(nearby)
            } else {
                optimizedProbes.add(probe)
                processed.add(probe)
            }
        }

        return optimizedProbes
    }

    /**
     * Find the nearest probe to a position
     */
    fun findNearestProbe(probes: List<LightProbe>, position: Vector3): LightProbe {
        return probes.minByOrNull { it.position.distanceTo(position) }
            ?: throw IllegalStateException("No probes available")
    }

    /**
     * Calculate average spacing between probes
     */
    fun calculateAverageSpacing(probes: List<LightProbe>): Vector3 {
        if (probes.size < 2) return Vector3(1f, 1f, 1f)

        var totalSpacing = Vector3()
        var count = 0

        for (i in probes.indices) {
            for (j in (i + 1) until probes.size) {
                val spacing = probes[j].position.clone().subtract(probes[i].position)
                totalSpacing.add(spacing)
                count++
            }
        }

        return totalSpacing.divideScalar(count.toFloat())
    }
}
