/**
 * Level of Detail (LOD) generation for progressive mesh simplification
 */
package io.materia.geometry.processing

import io.materia.geometry.BufferGeometry
import kotlin.math.pow

/**
 * Generates multiple LOD levels for geometry
 */
class LODGenerator {

    companion object {
        const val DEFAULT_LOD_LEVELS = 5
        const val DEFAULT_REDUCTION_FACTOR = 0.5f
    }

    /**
     * Generate multiple LOD levels for a geometry
     */
    fun generateLodLevels(
        geometry: BufferGeometry,
        options: LodGenerationOptions = LodGenerationOptions()
    ): LodResult {
        val originalTriangleCount = geometry.getTriangleCount()
        val lodLevels = mutableListOf<LodLevel>()

        // Add original as LOD 0
        lodLevels.add(
            LodLevel(
                distance = 0f,
                geometry = geometry.clone(),
                triangleCount = originalTriangleCount
            )
        )

        var currentGeometry = geometry.clone()
        var currentReduction = options.initialReduction

        // Generate progressive LOD levels
        for (level in 1 until options.lodLevels) {
            val targetTriangleCount = (originalTriangleCount * currentReduction).toInt()

            if (targetTriangleCount < options.minimumTriangles) {
                break
            }

            val simplifiedGeometry =
                MeshSimplifier().simplifyGeometry(currentGeometry, targetTriangleCount)
            val distance = calculateLodDistance(level, options.baseLodDistance)

            lodLevels.add(
                LodLevel(
                    distance = distance,
                    geometry = simplifiedGeometry,
                    triangleCount = simplifiedGeometry.getTriangleCount()
                )
            )

            currentGeometry = simplifiedGeometry
            currentReduction = currentReduction * options.reductionFactor
        }

        return LodResult(
            levels = lodLevels.toList(),
            originalTriangleCount = originalTriangleCount,
            totalReduction = if (lodLevels.size > 1 && originalTriangleCount > 0) {
                1f - (lodLevels.last().triangleCount.toFloat() / originalTriangleCount)
            } else {
                0f
            }
        )
    }

    /**
     * Calculate LOD distance with exponential scaling
     */
    private fun calculateLodDistance(level: Int, baseDistance: Float): Float {
        return baseDistance * (1 shl level).toFloat() // Exponential distance scaling
    }
}

/**
 * LOD generation configuration
 */
data class LodGenerationOptions(
    val lodLevels: Int = LODGenerator.DEFAULT_LOD_LEVELS,
    val reductionFactor: Float = LODGenerator.DEFAULT_REDUCTION_FACTOR,
    val initialReduction: Float = 0.8f,
    val baseLodDistance: Float = 10f,
    val minimumTriangles: Int = 100
)

/**
 * LOD generation result
 */
data class LodResult(
    val levels: List<LodLevel>,
    val originalTriangleCount: Int,
    val totalReduction: Float
)

/**
 * Single LOD level
 */
data class LodLevel(
    val distance: Float,
    val geometry: BufferGeometry,
    val triangleCount: Int
)
