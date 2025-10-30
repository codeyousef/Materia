/**
 * Tangent vector generation for normal mapping
 */
package io.materia.geometry.processing

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.BufferGeometry
import io.materia.geometry.BufferAttribute

/**
 * Generates tangent vectors using Lengyel's method
 */
class TangentGenerator {

    /**
     * Generate tangent vectors for normal mapping
     */
    fun generateTangents(geometry: BufferGeometry): BufferGeometry {
        val result = geometry.clone()
        val positionAttribute = result.getAttribute("position") ?: return result
        val normalAttribute = result.getAttribute("normal") ?: run {
            // Generate normals first if they don't exist
            return generateTangents(NormalGenerator().generateSmoothNormals(result))
        }
        val uvAttribute = result.getAttribute("uv") ?: return result

        val vertexCount = positionAttribute.count
        val tangents = Array(vertexCount) { Vector3() }
        val bitangents = Array(vertexCount) { Vector3() }

        // Calculate tangents using UV derivatives
        calculateTangentVectors(
            positionAttribute, normalAttribute, uvAttribute,
            result.index, tangents, bitangents
        )

        // Orthogonalize tangents using Gram-Schmidt process
        orthogonalizeTangents(normalAttribute, tangents, bitangents)

        // Set tangent attribute (w component stores handedness)
        val tangentArray = FloatArray(vertexCount * 4)
        for (i in 0 until vertexCount) {
            val normal = Vector3(
                normalAttribute.getX(i),
                normalAttribute.getY(i),
                normalAttribute.getZ(i)
            )
            val tangent = tangents[i]
            val bitangent = bitangents[i]

            // Calculate handedness
            val handedness = if (normal.clone().cross(tangent).dot(bitangent) < 0f) -1f else 1f

            tangentArray[i * 4] = tangent.x
            tangentArray[i * 4 + 1] = tangent.y
            tangentArray[i * 4 + 2] = tangent.z
            tangentArray[i * 4 + 3] = handedness
        }

        result.setAttribute("tangent", BufferAttribute(tangentArray, 4))
        return result
    }

    /**
     * Calculate tangent vectors using UV derivatives (Lengyel's method)
     */
    private fun calculateTangentVectors(
        positionAttribute: BufferAttribute,
        normalAttribute: BufferAttribute,
        uvAttribute: BufferAttribute,
        indexAttribute: BufferAttribute?,
        tangents: Array<Vector3>,
        bitangents: Array<Vector3>
    ) {
        val processTriangle = { i0: Int, i1: Int, i2: Int ->
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

            val uv0 = Vector2(uvAttribute.getX(i0), uvAttribute.getY(i0))
            val uv1 = Vector2(uvAttribute.getX(i1), uvAttribute.getY(i1))
            val uv2 = Vector2(uvAttribute.getX(i2), uvAttribute.getY(i2))

            // Edge vectors
            val edge1 = v1.clone().subtract(v0)
            val edge2 = v2.clone().subtract(v0)

            // UV deltas
            val deltaUV1 = uv1.clone().sub(uv0)
            val deltaUV2 = uv2.clone().sub(uv0)

            // Calculate tangent and bitangent
            // Check determinant to avoid division by zero
            val determinant = deltaUV1.x * deltaUV2.y - deltaUV2.x * deltaUV1.y

            // If determinant is too small, skip this triangle to avoid numerical instability
            if (kotlin.math.abs(determinant) >= io.materia.core.math.EPSILON) {
                val f = 1.0f / determinant

                val tangent = Vector3(
                    f * (deltaUV2.y * edge1.x - deltaUV1.y * edge2.x),
                    f * (deltaUV2.y * edge1.y - deltaUV1.y * edge2.y),
                    f * (deltaUV2.y * edge1.z - deltaUV1.y * edge2.z)
                )

                val bitangent = Vector3(
                    f * (-deltaUV2.x * edge1.x + deltaUV1.x * edge2.x),
                    f * (-deltaUV2.x * edge1.y + deltaUV1.x * edge2.y),
                    f * (-deltaUV2.x * edge1.z + deltaUV1.x * edge2.z)
                )

                // Accumulate
                tangents[i0].add(tangent)
                tangents[i1].add(tangent)
                tangents[i2].add(tangent)

                bitangents[i0].add(bitangent)
                bitangents[i1].add(bitangent)
                bitangents[i2].add(bitangent)
            }
        }

        // Process triangles
        if (indexAttribute != null) {
            for (i in indexAttribute.array.indices step 3) {
                processTriangle(
                    indexAttribute.array[i].toInt(),
                    indexAttribute.array[i + 1].toInt(),
                    indexAttribute.array[i + 2].toInt()
                )
            }
        } else {
            for (i in 0 until positionAttribute.count step 3) {
                processTriangle(i, i + 1, i + 2)
            }
        }
    }

    /**
     * Orthogonalize tangents using Gram-Schmidt process
     */
    private fun orthogonalizeTangents(
        normalAttribute: BufferAttribute,
        tangents: Array<Vector3>,
        bitangents: Array<Vector3>
    ) {
        for (i in tangents.indices) {
            val normal = Vector3(
                normalAttribute.getX(i),
                normalAttribute.getY(i),
                normalAttribute.getZ(i)
            )

            val tangent = tangents[i]

            // Gram-Schmidt orthogonalization
            // t' = t - (n · t) * n
            val dotProduct = normal.dot(tangent)
            tangent.sub(normal.clone().multiplyScalar(dotProduct))
            tangent.normalize()

            // Ensure orthogonality for bitangent
            val bitangent = bitangents[i]
            val dotProductB = normal.dot(bitangent)
            bitangent.sub(normal.clone().multiplyScalar(dotProductB))

            val dotProductT = tangent.dot(bitangent)
            bitangent.sub(tangent.clone().multiplyScalar(dotProductT))
            bitangent.normalize()
        }
    }
}
