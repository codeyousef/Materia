/**
 * GeometryProcessor for optimization and LOD generation
 * T024 - Advanced geometry processing with LOD, optimization, and quality control
 *
 * Provides algorithms for:
 * - Geometry simplification (triangle reduction)
 * - LOD level generation with automatic distance-based switching
 * - Vertex merging and deduplication
 * - Normal and tangent generation
 * - Bounding volume calculations
 * - Memory optimization techniques
 *
 * Refactored: Delegates to specialized processing modules
 */
package io.materia.geometry

import io.materia.geometry.processing.*

/**
 * Advanced geometry processor for real-time 3D applications
 * Coordinates specialized processing modules
 */
class GeometryProcessor {
    private val lodGenerator = LODGenerator()
    private val meshSimplifier = MeshSimplifier()
    private val vertexOptimizer = VertexOptimizer()
    private val normalGenerator = NormalGenerator()
    private val tangentGenerator = TangentGenerator()
    private val boundingVolumeCalculator = BoundingVolumeCalculator()

    /**
     * Generate multiple LOD levels for a geometry
     * Uses progressive mesh simplification to create lower detail versions
     */
    fun generateLodLevels(
        geometry: BufferGeometry,
        options: io.materia.geometry.processing.LodGenerationOptions = io.materia.geometry.processing.LodGenerationOptions()
    ): io.materia.geometry.processing.LodResult = lodGenerator.generateLodLevels(geometry, options)

    /**
     * Simplify geometry using edge collapse algorithm
     * Implements Quadric Error Metrics for optimal vertex removal
     */
    fun simplifyGeometry(
        geometry: BufferGeometry,
        targetTriangleCount: Int
    ): BufferGeometry = meshSimplifier.simplifyGeometry(geometry, targetTriangleCount)

    /**
     * Merge duplicate vertices within threshold
     * Reduces memory usage and improves cache coherency
     */
    fun mergeVertices(
        geometry: BufferGeometry,
        threshold: Float = VertexOptimizer.DEFAULT_MERGE_THRESHOLD
    ): GeometryMergeResult = vertexOptimizer.mergeVertices(geometry, threshold)

    /**
     * Generate smooth normals using vertex merging
     * Improves lighting quality for organic shapes
     */
    fun generateSmoothNormals(
        geometry: BufferGeometry,
        angleThreshold: Float = 0.866f // ~30 degrees
    ): BufferGeometry = normalGenerator.generateSmoothNormals(geometry, angleThreshold)

    /**
     * Generate tangent vectors for normal mapping
     * Implements Lengyel's method for consistent tangent calculation
     */
    fun generateTangents(geometry: BufferGeometry): BufferGeometry =
        tangentGenerator.generateTangents(geometry)

    /**
     * Optimize geometry for GPU rendering
     * Applies vertex cache optimization and memory layout improvements
     */
    fun optimizeForGpu(
        geometry: BufferGeometry,
        options: GpuOptimizationOptions = GpuOptimizationOptions()
    ): GeometryOptimizationResult {
        var result = geometry.clone()
        val optimizations = mutableListOf<String>()

        // Merge duplicate vertices
        if (options.mergeVertices) {
            val mergeResult = vertexOptimizer.mergeVertices(result, options.mergeThreshold)
            result = mergeResult.geometry
            if (mergeResult.mergedVertices > 0) {
                optimizations.add("Merged ${mergeResult.mergedVertices} duplicate vertices")
            }
        }

        // Generate indices if not present
        if (result.index == null && options.generateIndices) {
            result = vertexOptimizer.generateIndices(result)
            optimizations.add("Generated index buffer")
        }

        // Optimize vertex cache
        if (options.optimizeVertexCache) {
            result = vertexOptimizer.optimizeVertexCache(result)
            optimizations.add("Optimized vertex cache ordering")
        }

        // Generate missing attributes
        if (result.getAttribute("normal") == null && options.generateNormals) {
            result = normalGenerator.generateSmoothNormals(result)
            optimizations.add("Generated smooth normals")
        }

        if (result.getAttribute("tangent") == null && options.generateTangents) {
            result = tangentGenerator.generateTangents(result)
            optimizations.add("Generated tangent vectors")
        }

        // Calculate memory savings
        val originalMemory = calculateGeometryMemoryUsage(geometry)
        val optimizedMemory = calculateGeometryMemoryUsage(result)
        val memorySavings = originalMemory - optimizedMemory

        return GeometryOptimizationResult(
            geometry = result,
            optimizations = optimizations,
            memorySavings = memorySavings,
            compressionRatio = if (originalMemory > 0) {
                optimizedMemory.toFloat() / originalMemory
            } else 1f
        )
    }

    /**
     * Calculate tight bounding volumes
     * More accurate than basic min/max bounds
     */
    fun calculateBoundingVolumes(geometry: BufferGeometry): BoundingVolumeResult =
        boundingVolumeCalculator.calculateBoundingVolumes(geometry)

    private fun calculateGeometryMemoryUsage(geometry: BufferGeometry): Int {
        var totalBytes = 0

        // Calculate attribute memory usage
        geometry.attributes.values.forEach { attribute ->
            totalBytes += attribute.array.size * 4 // Float size
        }

        // Add index buffer if present
        geometry.index?.let { index ->
            totalBytes += index.array.size * 4
        }

        return totalBytes
    }

    companion object {
        const val DEFAULT_LOD_LEVELS = 5
        const val DEFAULT_REDUCTION_FACTOR = 0.5f
        const val DEFAULT_MERGE_THRESHOLD = 0.001f
        const val DEFAULT_NORMAL_THRESHOLD = 0.866f // ~30 degrees

        // Quality tier configuration
        val QUALITY_TIERS = mapOf(
            QualityTier.LOW to QualitySettings(
                lodLevels = 3,
                reductionFactor = 0.3f,
                mergeThreshold = 0.01f,
                generateTangents = false
            ),
            QualityTier.MEDIUM to QualitySettings(
                lodLevels = 4,
                reductionFactor = 0.5f,
                mergeThreshold = 0.005f,
                generateTangents = true
            ),
            QualityTier.HIGH to QualitySettings(
                lodLevels = 5,
                reductionFactor = 0.7f,
                mergeThreshold = 0.001f,
                generateTangents = true
            ),
            QualityTier.ULTRA to QualitySettings(
                lodLevels = 6,
                reductionFactor = 0.8f,
                mergeThreshold = 0.0005f,
                generateTangents = true
            )
        )
    }
}
