package io.materia.shape

import io.materia.core.math.*
import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import kotlin.math.*

/**
 * LatheGeometry - Creates geometry by revolving 2D points around Y axis
 * Useful for creating vases, bottles, and other rotationally symmetric objects
 *
 * Based on Three.js LatheGeometry
 */
class LatheGeometry(
    points: List<Vector2>,
    segments: Int = 12,
    phiStart: Float = 0f,
    phiLength: Float = PI.toFloat() * 2f
) : BufferGeometry() {

    init {
        require(points.size >= 2) { "At least 2 points required for lathe geometry" }
        require(segments >= 1) { "Segments must be at least 1" }
        require(phiLength > 0f) { "Phi length must be positive" }

        generate(points, segments, phiStart, phiLength)
    }

    private fun generate(points: List<Vector2>, segments: Int, phiStart: Float, phiLength: Float) {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Pre-calculate segments
        val inverseSegments = 1f / segments

        // Generate vertices, normals and uvs
        for (i in 0..segments) {
            val phi = phiStart + i * inverseSegments * phiLength

            val sin = sin(phi)
            val cos = cos(phi)

            for (j in points.indices) {
                val point = points[j]

                // Vertex
                val x = point.x * sin
                val y = point.y
                val z = point.x * cos

                vertices.add(x)
                vertices.add(y)
                vertices.add(z)

                // UV
                val u = i.toFloat() / segments
                val v = j.toFloat() / (points.size - 1)
                uvs.add(u)
                uvs.add(v)

                // Normal calculation
                val normal = calculateNormal(points, j, sin, cos)
                normals.add(normal.x)
                normals.add(normal.y)
                normals.add(normal.z)
            }
        }

        // Generate indices
        for (i in 0 until segments) {
            for (j in 0 until points.size - 1) {
                val base = j + i * points.size

                val a = base
                val b = base + points.size
                val c = base + points.size + 1
                val d = base + 1

                // Create two triangles for each quad
                indices.add(a)
                indices.add(b)
                indices.add(d)

                indices.add(b)
                indices.add(c)
                indices.add(d)
            }
        }

        // Set geometry attributes
        setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    private fun calculateNormal(
        points: List<Vector2>,
        index: Int,
        sin: Float,
        cos: Float
    ): Vector3 {
        // Calculate tangent vector along the profile
        val tangent = if (index == 0) {
            // First point - use vector to next point
            Vector2(
                points[1].x - points[0].x,
                points[1].y - points[0].y
            )
        } else if (index == points.size - 1) {
            // Last point - use vector from previous point
            Vector2(
                points[index].x - points[index - 1].x,
                points[index].y - points[index - 1].y
            )
        } else {
            // Middle points - use average of vectors to neighbors
            Vector2(
                (points[index + 1].x - points[index - 1].x) * 0.5f,
                (points[index + 1].y - points[index - 1].y) * 0.5f
            )
        }

        // Normalize tangent
        val tangentLength = sqrt(tangent.x * tangent.x + tangent.y * tangent.y)
        if (tangentLength > 0f) {
            tangent.x /= tangentLength
            tangent.y /= tangentLength
        }

        // Normal is perpendicular to tangent in the profile plane
        // Rotate tangent 90 degrees: (x, y) -> (-y, x)
        val normalY = tangent.x
        val normalXZ = -tangent.y

        // Transform to 3D based on rotation angle
        val normal = Vector3(
            normalXZ * sin,
            normalY,
            normalXZ * cos
        )

        // Normalize
        val length = sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z)
        if (length > 0f) {
            normal.x /= length
            normal.y /= length
            normal.z /= length
        }

        return normal
    }

    companion object {
        /**
         * Create a vase-like lathe geometry
         */
        fun createVase(
            height: Float = 2f,
            bottomRadius: Float = 0.3f,
            topRadius: Float = 0.5f,
            neckRadius: Float = 0.2f,
            segments: Int = 32
        ): LatheGeometry {
            val points = listOf(
                Vector2(bottomRadius, 0f),
                Vector2(bottomRadius * 1.2f, height * 0.1f),
                Vector2(bottomRadius * 0.8f, height * 0.3f),
                Vector2(neckRadius, height * 0.6f),
                Vector2(neckRadius, height * 0.7f),
                Vector2(topRadius * 0.8f, height * 0.85f),
                Vector2(topRadius, height * 0.95f),
                Vector2(topRadius * 0.9f, height)
            )
            return LatheGeometry(points, segments)
        }

        /**
         * Create a wine glass lathe geometry
         */
        fun createWineGlass(
            height: Float = 3f,
            bowlRadius: Float = 0.6f,
            stemRadius: Float = 0.05f,
            baseRadius: Float = 0.4f,
            segments: Int = 32
        ): LatheGeometry {
            val stemHeight = height * 0.4f
            val bowlHeight = height * 0.5f
            val baseHeight = height * 0.1f

            val points = listOf(
                Vector2(baseRadius, 0f),
                Vector2(baseRadius * 0.8f, baseHeight * 0.5f),
                Vector2(stemRadius, baseHeight),
                Vector2(stemRadius, baseHeight + stemHeight),
                Vector2(stemRadius * 2f, baseHeight + stemHeight + bowlHeight * 0.1f),
                Vector2(bowlRadius * 0.8f, baseHeight + stemHeight + bowlHeight * 0.4f),
                Vector2(bowlRadius, baseHeight + stemHeight + bowlHeight * 0.7f),
                Vector2(bowlRadius * 0.9f, baseHeight + stemHeight + bowlHeight * 0.9f),
                Vector2(bowlRadius * 0.7f, height)
            )
            return LatheGeometry(points, segments)
        }

        /**
         * Create a simple cylinder using lathe
         */
        fun createCylinder(
            radius: Float = 0.5f,
            height: Float = 2f,
            segments: Int = 32
        ): LatheGeometry {
            val points = listOf(
                Vector2(radius, 0f),
                Vector2(radius, height)
            )
            return LatheGeometry(points, segments)
        }

        /**
         * Create a cone using lathe
         */
        fun createCone(
            radius: Float = 0.5f,
            height: Float = 2f,
            segments: Int = 32
        ): LatheGeometry {
            val points = listOf(
                Vector2(radius, 0f),
                Vector2(0f, height)
            )
            return LatheGeometry(points, segments)
        }
    }
}