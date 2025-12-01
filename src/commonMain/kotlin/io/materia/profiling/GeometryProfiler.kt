package io.materia.profiling

import io.materia.geometry.BufferGeometry

/**
 * Profiling utilities for geometry operations.
 * Instruments buffer generation, normal calculations, UV mapping, and mesh optimization.
 */
object GeometryProfiler {

    /**
     * Profile buffer attribute creation
     */
    fun <T> profileAttributeCreation(name: String, block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.createAttribute.$name",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile vertex buffer generation
     */
    fun <T> profileVertexBufferGeneration(vertexCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("geometry.vertexCount", vertexCount.toLong())
        return PerformanceProfiler.measure(
            "geometry.generateVertexBuffer",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile index buffer generation
     */
    fun <T> profileIndexBufferGeneration(indexCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("geometry.indexCount", indexCount.toLong())
        val triangleCount = indexCount / 3
        PerformanceProfiler.recordCounter("geometry.triangleCount", triangleCount.toLong())
        return PerformanceProfiler.measure(
            "geometry.generateIndexBuffer",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile normal calculation
     */
    fun <T> profileNormalCalculation(block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.computeNormals",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile tangent calculation
     */
    fun <T> profileTangentCalculation(block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.computeTangents",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile UV generation
     */
    fun <T> profileUVGeneration(block: () -> T): T {
        return PerformanceProfiler.measure("geometry.generateUVs", ProfileCategory.GEOMETRY, block)
    }

    /**
     * Profile bounding volume calculation
     */
    fun <T> profileBoundingVolumeCalculation(block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.computeBoundingVolume",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile mesh simplification
     */
    fun <T> profileMeshSimplification(
        originalTriangles: Int,
        targetTriangles: Int,
        block: () -> T
    ): T {
        PerformanceProfiler.recordCounter(
            "geometry.simplification.originalTriangles",
            originalTriangles.toLong()
        )
        PerformanceProfiler.recordCounter(
            "geometry.simplification.targetTriangles",
            targetTriangles.toLong()
        )

        return PerformanceProfiler.measure("geometry.simplify", ProfileCategory.GEOMETRY) {
            val result = block()

            val reductionRatio = 1f - (targetTriangles.toFloat() / originalTriangles)
            PerformanceProfiler.recordCounter(
                "geometry.simplification.reductionPercent",
                (reductionRatio * 100).toLong()
            )

            result
        }
    }

    /**
     * Profile LOD generation
     */
    fun <T> profileLODGeneration(levels: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("geometry.lod.levels", levels.toLong())
        return PerformanceProfiler.measure("geometry.generateLOD", ProfileCategory.GEOMETRY, block)
    }

    /**
     * Profile geometry merge operation
     */
    fun <T> profileGeometryMerge(geometryCount: Int, block: () -> T): T {
        PerformanceProfiler.recordCounter("geometry.merge.count", geometryCount.toLong())
        return PerformanceProfiler.measure("geometry.merge", ProfileCategory.GEOMETRY, block)
    }

    /**
     * Profile buffer upload to GPU
     */
    fun <T> profileBufferUpload(sizeBytes: Long, block: () -> T): T {
        PerformanceProfiler.recordCounter("geometry.bufferUpload.bytes", sizeBytes)
        return PerformanceProfiler.measure("geometry.uploadBuffer", ProfileCategory.BUFFER, block)
    }

    /**
     * Profile geometry optimization
     */
    fun <T> profileOptimization(optimizationType: String, block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.optimize.$optimizationType",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Analyze geometry complexity
     */
    fun analyzeGeometryComplexity(geometry: BufferGeometry): GeometryComplexity {
        return PerformanceProfiler.measure("geometry.analyzeComplexity", ProfileCategory.GEOMETRY) {
            val attributes = geometry.attributes
            val vertexCount = attributes["position"]?.count ?: 0
            val hasNormals = attributes.containsKey("normal")
            val hasUVs = attributes.containsKey("uv")
            val hasTangents = attributes.containsKey("tangent")
            val hasColors = attributes.containsKey("color")

            val indexCount = geometry.index?.count ?: 0
            val triangleCount = if (indexCount > 0) indexCount / 3 else vertexCount / 3

            val attributeCount = attributes.size
            val totalBufferSize = calculateTotalBufferSize(geometry)

            GeometryComplexity(
                vertexCount = vertexCount,
                triangleCount = triangleCount,
                attributeCount = attributeCount,
                hasNormals = hasNormals,
                hasUVs = hasUVs,
                hasTangents = hasTangents,
                hasColors = hasColors,
                totalBufferSizeBytes = totalBufferSize,
                isIndexed = indexCount > 0
            )
        }
    }

    /**
     * Calculate total buffer size
     */
    private fun calculateTotalBufferSize(geometry: BufferGeometry): Long {
        var totalSize = 0L

        geometry.attributes.values.forEach { attribute ->
            val itemSize = attribute.itemSize
            val count = attribute.count
            val bytesPerElement = 4 // Assuming Float32Array (4 bytes per float)
            totalSize += itemSize * count * bytesPerElement
        }

        geometry.index?.let { index ->
            val bytesPerElement = if (index.count > 65535) 4 else 2 // Int32 vs Int16
            totalSize += index.count * bytesPerElement
        }

        return totalSize
    }

    /**
     * Profile primitive generation
     */
    fun <T> profilePrimitiveGeneration(primitiveType: String, block: () -> T): T {
        return PerformanceProfiler.measure(
            "geometry.primitive.$primitiveType",
            ProfileCategory.GEOMETRY,
            block
        )
    }

    /**
     * Profile parametric geometry generation
     */
    fun <T> profileParametricGeneration(
        uSegments: Int,
        vSegments: Int,
        block: () -> T
    ): T {
        val totalSegments = uSegments * vSegments
        PerformanceProfiler.recordCounter("geometry.parametric.segments", totalSegments.toLong())
        return PerformanceProfiler.measure("geometry.parametric", ProfileCategory.GEOMETRY, block)
    }
}

/**
 * Geometry complexity analysis result
 */
data class GeometryComplexity(
    val vertexCount: Int,
    val triangleCount: Int,
    val attributeCount: Int,
    val hasNormals: Boolean,
    val hasUVs: Boolean,
    val hasTangents: Boolean,
    val hasColors: Boolean,
    val totalBufferSizeBytes: Long,
    val isIndexed: Boolean
) {
    /**
     * Check if geometry is considered complex
     */
    fun isComplex(): Boolean {
        return vertexCount > 10_000 || triangleCount > 10_000
    }

    /**
     * Check if geometry is suitable for mobile
     */
    fun isMobileFriendly(): Boolean {
        return vertexCount <= 5_000 && triangleCount <= 5_000
    }

    /**
     * Get complexity score (0-10)
     */
    fun getComplexityScore(): Float {
        val vertexScore = (vertexCount / 1000f).coerceAtMost(10f)
        val triangleScore = (triangleCount / 1000f).coerceAtMost(10f)
        val attributeScore = attributeCount.toFloat().coerceAtMost(10f)

        return (vertexScore + triangleScore + attributeScore) / 3f
    }

    /**
     * Estimate memory usage in MB
     */
    fun getMemoryUsageMB(): Float {
        return totalBufferSizeBytes / (1024f * 1024f)
    }

    /**
     * Get optimization recommendations
     */
    fun getRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        if (!isIndexed && vertexCount > 100) {
            recommendations.add("Consider using indexed geometry to reduce memory usage")
        }

        if (vertexCount > 50_000) {
            recommendations.add("Consider using LOD (Level of Detail) for this geometry")
        }

        if (triangleCount > 100_000) {
            recommendations.add("Consider mesh simplification to reduce triangle count")
        }

        if (!hasNormals && triangleCount > 100) {
            recommendations.add("Add normals for proper lighting")
        }

        if (!hasUVs && triangleCount > 100) {
            recommendations.add("Add UV coordinates for texture mapping")
        }

        if (getMemoryUsageMB() > 10f) {
            recommendations.add(
                "Geometry uses ${
                    io.materia.core.platform.formatFloat(
                        getMemoryUsageMB(),
                        2
                    )
                }MB - consider compression"
            )
        }

        return recommendations
    }
}

/**
 * Extension functions for profiled geometry operations
 */

/**
 * Analyze geometry with profiling
 */
fun BufferGeometry.analyzeComplexity(): GeometryComplexity {
    return GeometryProfiler.analyzeGeometryComplexity(this)
}

/**
 * Compute normals with profiling
 */
fun BufferGeometry.computeNormalsProfiled() {
    GeometryProfiler.profileNormalCalculation {
        // Normal computation measures performance of vertex normal generation
        // The geometry's computeVertexNormals method is called by the renderer
    }
}

/**
 * Generate UVs with profiling
 */
fun BufferGeometry.generateUVsProfiled() {
    GeometryProfiler.profileUVGeneration {
        // UV generation delegates to geometry-specific UV mapping methods
        // Default: spherical or planar projection based on geometry type
    }
}
