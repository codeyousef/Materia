package io.materia.geometry.text

import io.materia.core.math.Vector2
import io.materia.core.math.Vector3
import io.materia.geometry.BufferGeometry

/**
 * Geometry merging utilities for text geometry
 */
object GeometryMerger {

    /**
     * Merge geometry into target lists
     */
    fun merge(
        sourceGeometry: BufferGeometry,
        targetVertices: MutableList<Vector3>,
        targetNormals: MutableList<Vector3>,
        targetUVs: MutableList<Vector2>,
        targetIndices: MutableList<Int>
    ) {
        // Extract vertices from source geometry with null checking
        val positionAttribute = sourceGeometry.getAttribute("position")
            ?: throw IllegalArgumentException("Source geometry missing 'position' attribute")
        val normalAttribute = sourceGeometry.getAttribute("normal")
            ?: throw IllegalArgumentException("Source geometry missing 'normal' attribute")
        val uvAttribute = sourceGeometry.getAttribute("uv")
            ?: throw IllegalArgumentException("Source geometry missing 'uv' attribute")
        val indexAttribute = sourceGeometry.index
            ?: throw IllegalArgumentException("Source geometry missing index attribute")

        val startVertexIndex = targetVertices.size

        // Add vertices
        for (i in 0 until positionAttribute.count) {
            val x = positionAttribute.getX(i)
            val y = positionAttribute.getY(i)
            val z = positionAttribute.getZ(i)
            targetVertices.add(Vector3(x, y, z))
        }

        // Add normals
        for (i in 0 until normalAttribute.count) {
            val x = normalAttribute.getX(i)
            val y = normalAttribute.getY(i)
            val z = normalAttribute.getZ(i)
            targetNormals.add(Vector3(x, y, z))
        }

        // Add UVs
        for (i in 0 until uvAttribute.count) {
            val x = uvAttribute.getX(i)
            val y = uvAttribute.getY(i)
            targetUVs.add(Vector2(x, y))
        }

        // Add indices
        for (i in 0 until indexAttribute.count) {
            targetIndices.add(indexAttribute.getX(i).toInt() + startVertexIndex)
        }
    }
}
