/**
 * Normal generation algorithms for geometry
 */
package io.materia.geometry.processing

import io.materia.core.math.Vector3
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute

/**
 * Generates smooth and flat normals for geometry
 */
class NormalGenerator {

    companion object {
        const val DEFAULT_ANGLE_THRESHOLD = 0.866f // ~30 degrees
    }

    /**
     * Generate smooth normals using vertex merging
     */
    fun generateSmoothNormals(
        geometry: BufferGeometry,
        angleThreshold: Float = DEFAULT_ANGLE_THRESHOLD
    ): BufferGeometry {
        val result = geometry.clone()
        val positionAttribute = result.getAttribute("position") ?: return result
        val indexAttribute = result.index

        val vertexCount = positionAttribute.count
        val normals = Array(vertexCount) { Vector3() }
        val normalCounts = IntArray(vertexCount)

        // Calculate face normals and accumulate vertex normals
        indexAttribute?.let { index ->
            calculateIndexedNormals(positionAttribute, index, normals, normalCounts)
        } ?: run {
            calculateNonIndexedNormals(positionAttribute, normals, normalCounts)
        }

        // Normalize accumulated normals
        for (i in normals.indices) {
            if (normalCounts[i] > 0) {
                normals[i].divideScalar(normalCounts[i].toFloat()).normalize()
            }
        }

        // Apply angle threshold for sharp edges
        applyAngleThreshold(result, normals, angleThreshold)

        // Set normal attribute
        val normalArray = FloatArray(vertexCount * 3)
        for (i in normals.indices) {
            normalArray[i * 3] = normals[i].x
            normalArray[i * 3 + 1] = normals[i].y
            normalArray[i * 3 + 2] = normals[i].z
        }

        result.setAttribute("normal", BufferAttribute(normalArray, 3))
        return result
    }

    /**
     * Calculate normals for indexed geometry
     */
    private fun calculateIndexedNormals(
        positionAttribute: BufferAttribute,
        indexAttribute: BufferAttribute,
        normals: Array<Vector3>,
        normalCounts: IntArray
    ) {
        for (i in indexAttribute.array.indices step 3) {
            val i0 = indexAttribute.array[i].toInt()
            val i1 = indexAttribute.array[i + 1].toInt()
            val i2 = indexAttribute.array[i + 2].toInt()

            val v0 = Vector3(
                positionAttribute.getX(i0),
                positionAttribute.getY(i0),
                positionAttribute.getZ(i0)
            )
            val v1 = Vector3(
                positionAttribute.getX(i1),
                positionAttribute.getY(i1),
                positionAttribute.getZ(i1)
            )
            val v2 = Vector3(
                positionAttribute.getX(i2),
                positionAttribute.getY(i2),
                positionAttribute.getZ(i2)
            )

            // Calculate face normal
            val edge1 = v1.clone().subtract(v0)
            val edge2 = v2.clone().subtract(v0)
            val faceNormal = edge1.cross(edge2)

            // Accumulate to vertex normals
            normals[i0].add(faceNormal)
            normals[i1].add(faceNormal)
            normals[i2].add(faceNormal)

            normalCounts[i0]++
            normalCounts[i1]++
            normalCounts[i2]++
        }
    }

    /**
     * Calculate normals for non-indexed geometry
     */
    private fun calculateNonIndexedNormals(
        positionAttribute: BufferAttribute,
        normals: Array<Vector3>,
        normalCounts: IntArray
    ) {
        for (i in 0 until positionAttribute.count step 3) {
            val v0 = Vector3(
                positionAttribute.getX(i),
                positionAttribute.getY(i),
                positionAttribute.getZ(i)
            )
            val v1 = Vector3(
                positionAttribute.getX(i + 1),
                positionAttribute.getY(i + 1),
                positionAttribute.getZ(i + 1)
            )
            val v2 = Vector3(
                positionAttribute.getX(i + 2),
                positionAttribute.getY(i + 2),
                positionAttribute.getZ(i + 2)
            )

            // Calculate face normal
            val edge1 = v1.clone().subtract(v0)
            val edge2 = v2.clone().subtract(v0)
            val faceNormal = edge1.cross(edge2)

            // Assign to all three vertices
            normals[i].add(faceNormal)
            normals[i + 1].add(faceNormal)
            normals[i + 2].add(faceNormal)

            normalCounts[i]++
            normalCounts[i + 1]++
            normalCounts[i + 2]++
        }
    }

    /**
     * Apply angle threshold for sharp edges
     */
    private fun applyAngleThreshold(
        geometry: BufferGeometry,
        normals: Array<Vector3>,
        angleThreshold: Float
    ) {
        val indexAttribute = geometry.index ?: return

        // Build edge-to-face map
        val edgeFaces = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
        for (i in indexAttribute.array.indices step 3) {
            val i0 = indexAttribute.array[i].toInt()
            val i1 = indexAttribute.array[i + 1].toInt()
            val i2 = indexAttribute.array[i + 2].toInt()

            val faceIndex = i / 3

            // Add edges (sorted to ensure consistency)
            addEdgeToFace(edgeFaces, i0, i1, faceIndex)
            addEdgeToFace(edgeFaces, i1, i2, faceIndex)
            addEdgeToFace(edgeFaces, i2, i0, faceIndex)
        }

        // Check edges for sharp angles
        edgeFaces.forEach { (edge, faces) ->
            if (faces.size == 2) {
                // Get normals for adjacent faces
                val n1 = normals[edge.first]
                val n2 = normals[edge.second]

                // If angle exceeds threshold, split normals
                val cosAngle = n1.clone().normalize().dot(n2.clone().normalize())
                if (cosAngle < angleThreshold) {
                    // Mark as sharp edge - in a full implementation,
                    // we would duplicate vertices along sharp edges
                }
            }
        }
    }

    private fun addEdgeToFace(
        edgeFaces: MutableMap<Pair<Int, Int>, MutableList<Int>>,
        v1: Int,
        v2: Int,
        faceIndex: Int
    ) {
        val edge = if (v1 < v2) Pair(v1, v2) else Pair(v2, v1)
        edgeFaces.getOrPut(edge) { mutableListOf() }.add(faceIndex)
    }
}
