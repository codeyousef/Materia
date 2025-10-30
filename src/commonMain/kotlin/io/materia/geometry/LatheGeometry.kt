/**
 * Lathe geometry implementation following Three.js r180 API
 * Creates a surface of revolution by rotating a 2D curve around the Y axis
 */
package io.materia.geometry

import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * Lathe geometry from a path of 2D points
 * Rotates a 2D shape around the Y axis to create a 3D surface
 *
 * @param points Array of Vector3 points defining the profile curve
 * @param segments Number of segments around the Y axis (default: 12)
 * @param phiStart Start angle in radians (default: 0)
 * @param phiLength Sweep angle in radians (default: 2π)
 */
class LatheGeometry(
    points: List<Vector3>,
    segments: Int = 12,
    phiStart: Float = 0f,
    phiLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry() {

    class LatheParameters(
        val points: List<Vector3>,
        var segments: Int,
        var phiStart: Float,
        var phiLength: Float
    ) : PrimitiveParameters() {

        fun set(
            segments: Int = this.segments,
            phiStart: Float = this.phiStart,
            phiLength: Float = this.phiLength
        ) {
            if (this.segments != segments || this.phiStart != phiStart || this.phiLength != phiLength) {
                this.segments = segments
                this.phiStart = phiStart
                this.phiLength = phiLength
                markDirty()
            }
        }
    }

    override val parameters = LatheParameters(points, segments, phiStart, phiLength)

    init {
        require(points.size >= 2) { "LatheGeometry requires at least 2 points" }
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Validate segments
        val segs = max(1, params.segments)

        // Precompute inverse normals for the profile curve
        val inversePoint = Vector3()
        val normal = Vector3()
        val tangent = Vector3()

        // Generate vertices, normals, and UVs
        for (i in 0..segs) {
            val phi =
                params.phiStart + (if (segs > 0) i.toFloat() / segs.toFloat() else 0f) * params.phiLength

            val sin = sin(phi)
            val cos = cos(phi)

            for (j in params.points.indices) {
                val point = params.points[j]

                // Vertex position (rotate point around Y axis)
                val x = point.x * sin
                val y = point.y
                val z = point.x * cos

                vertices.addAll(listOf(x, y, z))

                // Calculate normal
                if (j == 0) {
                    // First point: use direction to next point
                    tangent.copy(params.points[1]).subtract(params.points[0])
                } else if (j == params.points.size - 1) {
                    // Last point: use direction from previous point
                    tangent.copy(params.points[j]).subtract(params.points[j - 1])
                } else {
                    // Middle points: use average of adjacent segments
                    tangent.copy(params.points[j + 1]).subtract(params.points[j - 1])
                }

                // Normal is perpendicular to tangent in XZ plane
                normal.set(tangent.y * sin, -tangent.x, tangent.y * cos)
                val normalLength = normal.length()
                if (normalLength > 0.001f) {
                    normal.normalize()
                } else {
                    // Fallback to radial direction if degenerate
                    normal.set(sin, 0f, cos)
                }
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                // UV coordinates with safe divisions
                val u = if (segs > 0) i.toFloat() / segs.toFloat() else 0f
                val v =
                    if (params.points.size > 1) j.toFloat() / (params.points.size - 1).toFloat() else 0f
                uvs.addAll(listOf(u, v))
            }
        }

        // Generate indices
        for (i in 0 until segs) {
            for (j in 0 until params.points.size - 1) {
                val base = j + params.points.size * i

                val a = base
                val b = base + params.points.size
                val c = base + params.points.size + 1
                val d = base + 1

                indices.addAll(listOf(a, b, d))
                indices.addAll(listOf(b, c, d))
            }
        }

        // Set attributes
        setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    /**
     * Update lathe parameters (excluding points which are immutable)
     */
    fun setParameters(
        segments: Int = parameters.segments,
        phiStart: Float = parameters.phiStart,
        phiLength: Float = parameters.phiLength
    ) {
        parameters.set(segments, phiStart, phiLength)
        updateIfNeeded()
    }
}
