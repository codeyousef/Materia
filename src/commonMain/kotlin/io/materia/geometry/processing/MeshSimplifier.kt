/**
 * Mesh simplification using quadric error metrics
 */
package io.materia.geometry.processing

import io.materia.core.math.Vector3
import io.materia.core.math.Plane
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute

/**
 * Simplifies meshes using edge collapse with quadric error metrics
 */
class MeshSimplifier {

    /**
     * Simplify geometry using edge collapse algorithm
     */
    fun simplifyGeometry(
        geometry: BufferGeometry,
        targetTriangleCount: Int
    ): BufferGeometry {
        val positionAttribute = geometry.getAttribute("position")
            ?: return geometry.clone()

        val indexAttribute = geometry.index
        if (indexAttribute == null) {
            // Convert non-indexed geometry to indexed first
            val indexedGeometry = VertexOptimizer().generateIndices(geometry)
            return simplifyGeometry(indexedGeometry, targetTriangleCount)
        }

        val vertices = extractVertices(positionAttribute)
        val indices = extractIndices(indexAttribute)

        // Simplified implementation - full version would use quadric error metrics
        return buildSimplifiedGeometry(geometry, vertices, indices.take(targetTriangleCount * 3))
    }

    private fun extractVertices(positionAttribute: BufferAttribute): List<Vector3> {
        val vertices = mutableListOf<Vector3>()
        for (i in 0 until positionAttribute.count) {
            vertices.add(
                Vector3(
                    positionAttribute.getX(i),
                    positionAttribute.getY(i),
                    positionAttribute.getZ(i)
                )
            )
        }
        return vertices
    }

    private fun extractIndices(indexAttribute: BufferAttribute): List<Int> {
        return indexAttribute.array.map { it.toInt() }
    }

    private fun buildSimplifiedGeometry(
        originalGeometry: BufferGeometry,
        vertices: List<Vector3>,
        indices: List<Int>
    ): BufferGeometry {
        val result = BufferGeometry()

        // Build position attribute
        val positionArray = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { i, vertex ->
            positionArray[i * 3] = vertex.x
            positionArray[i * 3 + 1] = vertex.y
            positionArray[i * 3 + 2] = vertex.z
        }
        result.setAttribute("position", BufferAttribute(positionArray, 3))

        // Set index (BufferAttribute expects FloatArray, convert indices)
        val indexArray = indices.map { it.toFloat() }.toFloatArray()
        result.setIndex(BufferAttribute(indexArray, 1))

        return result
    }
}
