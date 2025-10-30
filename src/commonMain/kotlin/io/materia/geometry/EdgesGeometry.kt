/**
 * Edges geometry implementation following Three.js r180 API
 * Extracts edges from a geometry based on angle threshold
 */
package io.materia.geometry

import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * Edges geometry that extracts edges from another geometry
 * Only edges where the angle between adjacent faces exceeds the threshold are included
 *
 * @param geometry Source geometry to extract edges from
 * @param thresholdAngle Angle threshold in degrees (default: 1, edges with angle > threshold are included)
 */
class EdgesGeometry(
    geometry: BufferGeometry,
    thresholdAngle: Float = 1f
) : BufferGeometry() {

    init {
        val thresholdDot = cos(thresholdAngle * PI.toFloat() / 180f)

        val positionAttr = geometry.getAttribute("position")
            ?: throw IllegalArgumentException("Geometry must have position attribute")

        val indexAttr = geometry.index
        val vertexCount = positionAttr.count

        // Build edge map to find shared edges
        val edgeMap = mutableMapOf<String, EdgeData>()

        // Process triangles
        if (indexAttr != null) {
            // Indexed geometry
            val indices = indexAttr.array
            for (i in indices.indices step 3) {
                val a = indices[i].toInt()
                val b = indices[i + 1].toInt()
                val c = indices[i + 2].toInt()

                processTriangle(a, b, c, positionAttr, edgeMap)
            }
        } else {
            // Non-indexed geometry
            for (i in 0 until vertexCount step 3) {
                processTriangle(i, i + 1, i + 2, positionAttr, edgeMap)
            }
        }

        // Extract edges that meet the threshold criteria
        val edges = mutableListOf<Float>()

        for ((_, edgeData) in edgeMap) {
            if (edgeData.faceCount == 1) {
                // Border edge (only one adjacent face) - always include
                edges.addAll(
                    listOf(
                        edgeData.v1.x, edgeData.v1.y, edgeData.v1.z,
                        edgeData.v2.x, edgeData.v2.y, edgeData.v2.z
                    )
                )
            } else if (edgeData.faceCount == 2) {
                // Shared edge - include if angle exceeds threshold
                val dot = edgeData.normal1.dot(edgeData.normal2)
                if (dot < thresholdDot) {
                    edges.addAll(
                        listOf(
                            edgeData.v1.x, edgeData.v1.y, edgeData.v1.z,
                            edgeData.v2.x, edgeData.v2.y, edgeData.v2.z
                        )
                    )
                }
            }
        }

        // Set position attribute (no index buffer for line segments)
        setAttribute("position", BufferAttribute(edges.toFloatArray(), 3))
    }

    private fun processTriangle(
        a: Int, b: Int, c: Int,
        positionAttr: BufferAttribute,
        edgeMap: MutableMap<String, EdgeData>
    ) {
        val vA = Vector3(positionAttr.getX(a), positionAttr.getY(a), positionAttr.getZ(a))
        val vB = Vector3(positionAttr.getX(b), positionAttr.getY(b), positionAttr.getZ(b))
        val vC = Vector3(positionAttr.getX(c), positionAttr.getY(c), positionAttr.getZ(c))

        // Calculate face normal
        val cb = Vector3().copy(vC).subtract(vB)
        val ab = Vector3().copy(vA).subtract(vB)
        cb.cross(ab)
        cb.normalize()

        // Process three edges
        addEdge(a, b, vA, vB, cb, edgeMap)
        addEdge(b, c, vB, vC, cb, edgeMap)
        addEdge(c, a, vC, vA, cb, edgeMap)
    }

    private fun addEdge(
        i1: Int, i2: Int,
        v1: Vector3, v2: Vector3,
        normal: Vector3,
        edgeMap: MutableMap<String, EdgeData>
    ) {
        // Create consistent edge key (order independent)
        val key = if (i1 < i2) "$i1,$i2" else "$i2,$i1"

        val existing = edgeMap[key]
        if (existing == null) {
            // First time seeing this edge
            edgeMap[key] = EdgeData(v1, v2, normal)
        } else {
            // Second time seeing this edge (shared between two faces)
            existing.normal2.copy(normal)
            existing.faceCount = 2
        }
    }

    private data class EdgeData(
        val v1: Vector3,
        val v2: Vector3,
        val normal1: Vector3
    ) {
        val normal2 = Vector3()
        var faceCount = 1
    }
}

/**
 * Wireframe geometry implementation following Three.js r180 API
 * Extracts all edges from a geometry (no angle threshold)
 */
class WireframeGeometry(
    geometry: BufferGeometry
) : BufferGeometry() {

    init {
        val positionAttr = geometry.getAttribute("position")
            ?: throw IllegalArgumentException("Geometry must have position attribute")

        val indexAttr = geometry.index
        val vertexCount = positionAttr.count

        // Use set to avoid duplicate edges
        val edges = mutableSetOf<Edge>()

        // Process triangles
        if (indexAttr != null) {
            // Indexed geometry
            val indices = indexAttr.array
            for (i in indices.indices step 3) {
                val a = indices[i].toInt()
                val b = indices[i + 1].toInt()
                val c = indices[i + 2].toInt()

                addEdges(a, b, c, edges)
            }
        } else {
            // Non-indexed geometry
            for (i in 0 until vertexCount step 3) {
                addEdges(i, i + 1, i + 2, edges)
            }
        }

        // Build position array from edges
        val positions = mutableListOf<Float>()
        for (edge in edges) {
            val v1 = Vector3(
                positionAttr.getX(edge.i1),
                positionAttr.getY(edge.i1),
                positionAttr.getZ(edge.i1)
            )
            val v2 = Vector3(
                positionAttr.getX(edge.i2),
                positionAttr.getY(edge.i2),
                positionAttr.getZ(edge.i2)
            )

            positions.addAll(listOf(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z))
        }

        // Set position attribute (no index buffer for line segments)
        setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))
    }

    private fun addEdges(a: Int, b: Int, c: Int, edges: MutableSet<Edge>) {
        edges.add(Edge(a, b))
        edges.add(Edge(b, c))
        edges.add(Edge(c, a))
    }

    /**
     * Edge class with consistent ordering for set operations
     */
    private data class Edge(val i1: Int, val i2: Int) {
        // Normalize order for consistent comparison
        init {
            require(i1 != i2) { "Edge indices must be different" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Edge) return false

            // Order-independent comparison
            return (i1 == other.i1 && i2 == other.i2) ||
                    (i1 == other.i2 && i2 == other.i1)
        }

        override fun hashCode(): Int {
            // Order-independent hash
            return i1 + i2
        }
    }
}
