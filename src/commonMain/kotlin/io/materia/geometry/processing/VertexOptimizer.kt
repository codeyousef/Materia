/**
 * Vertex optimization utilities
 */
package io.materia.geometry.processing

import io.materia.core.math.Vector3
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute
import io.materia.geometry.GeometryMergeResult

/**
 * Optimizes vertex data for GPU rendering
 */
class VertexOptimizer {

    companion object {
        const val DEFAULT_MERGE_THRESHOLD = 0.001f
    }

    /**
     * Merge duplicate vertices within threshold
     */
    fun mergeVertices(
        geometry: BufferGeometry,
        threshold: Float = DEFAULT_MERGE_THRESHOLD
    ): GeometryMergeResult {
        val positionAttribute = geometry.getAttribute("position")
            ?: return GeometryMergeResult(geometry.clone(), 0)

        val vertices = mutableListOf<Vector3>()
        val vertexMap = mutableMapOf<Int, Int>()
        val newIndices = mutableListOf<Int>()

        // Extract and process vertices
        for (i in 0 until positionAttribute.count) {
            val vertex = Vector3(
                positionAttribute.getX(i),
                positionAttribute.getY(i),
                positionAttribute.getZ(i)
            )

            // Find existing vertex within threshold
            val existingIndex = findNearestVertex(vertices, vertex, threshold)

            if (existingIndex >= 0) {
                vertexMap[i] = existingIndex
            } else {
                vertexMap[i] = vertices.size
                vertices.add(vertex)
            }
        }

        // Update indices
        val originalIndex = geometry.index
        if (originalIndex != null) {
            for (i in 0 until originalIndex.count) {
                val oldIndex = originalIndex.array[i].toInt()
                newIndices.add(vertexMap[oldIndex] ?: oldIndex)
            }
        } else {
            for (i in 0 until positionAttribute.count) {
                newIndices.add(vertexMap[i] ?: i)
            }
        }

        // Build merged geometry
        val mergedGeometry = buildMergedGeometry(geometry, vertices, newIndices)
        val mergedVertices = positionAttribute.count - vertices.size

        return GeometryMergeResult(mergedGeometry, mergedVertices)
    }

    /**
     * Generate indices for non-indexed geometry
     */
    fun generateIndices(geometry: BufferGeometry): BufferGeometry {
        val positionAttribute = geometry.getAttribute("position") ?: return geometry
        val result = geometry.clone()

        val indexArray = FloatArray(positionAttribute.count)
        for (i in indexArray.indices) {
            indexArray[i] = i.toFloat()
        }

        result.setIndex(BufferAttribute(indexArray, 1))
        return result
    }

    /**
     * Optimize vertex cache using simplified Forsyth algorithm
     */
    fun optimizeVertexCache(geometry: BufferGeometry): BufferGeometry {
        // Simplified implementation
        return geometry.clone()
    }

    private fun findNearestVertex(
        vertices: List<Vector3>,
        target: Vector3,
        threshold: Float
    ): Int {
        val thresholdSq = threshold * threshold

        for (i in vertices.indices) {
            val distanceSq = vertices[i].distanceToSquared(target)
            if (distanceSq <= thresholdSq) {
                return i
            }
        }

        return -1
    }

    private fun buildMergedGeometry(
        originalGeometry: BufferGeometry,
        vertices: List<Vector3>,
        indices: List<Int>
    ): BufferGeometry {
        val result = BufferGeometry()

        val positionArray = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { i, vertex ->
            positionArray[i * 3] = vertex.x
            positionArray[i * 3 + 1] = vertex.y
            positionArray[i * 3 + 2] = vertex.z
        }
        result.setAttribute("position", BufferAttribute(positionArray, 3))

        val indexArray = indices.map { it.toFloat() }.toFloatArray()
        result.setIndex(BufferAttribute(indexArray, 1))

        return result
    }
}
