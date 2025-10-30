/**
 * Circle geometry implementation following Three.js r180 API
 * Creates a 2D circle with triangular segments radiating from center
 */
package io.materia.geometry

import kotlin.math.*

/**
 * Circle geometry with configurable radius and segments
 *
 * @param radius Radius of the circle (default: 1)
 * @param segments Number of triangular segments (default: 32, minimum: 3)
 * @param thetaStart Start angle for first segment in radians (default: 0)
 * @param thetaLength Central angle of the circular sector in radians (default: 2π)
 */
class CircleGeometry(
    radius: Float = 1f,
    segments: Int = 32,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry() {

    class CircleParameters(
        var radius: Float,
        var segments: Int,
        var thetaStart: Float,
        var thetaLength: Float
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            segments: Int = this.segments,
            thetaStart: Float = this.thetaStart,
            thetaLength: Float = this.thetaLength
        ) {
            if (this.radius != radius || this.segments != segments ||
                this.thetaStart != thetaStart || this.thetaLength != thetaLength
            ) {

                this.radius = radius
                this.segments = segments
                this.thetaStart = thetaStart
                this.thetaLength = thetaLength
                markDirty()
            }
        }
    }

    override val parameters = CircleParameters(radius, segments, thetaStart, thetaLength)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        // Validate segments (minimum 3 for a triangle)
        val segs = max(3, params.segments)

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Center vertex
        vertices.addAll(listOf(0f, 0f, 0f))
        normals.addAll(listOf(0f, 0f, 1f))
        uvs.addAll(listOf(0.5f, 0.5f))

        // Generate vertices around the circumference
        for (s in 0..segs) {
            val segment = params.thetaStart + (s.toFloat() / segs.toFloat()) * params.thetaLength

            // Vertex position
            val x = params.radius * cos(segment)
            val y = params.radius * sin(segment)

            vertices.addAll(listOf(x, y, 0f))

            // Normal (pointing in +Z direction)
            normals.addAll(listOf(0f, 0f, 1f))

            // UV coordinates
            // Map from [-radius, radius] to [0, 1]
            val u = (x / params.radius + 1f) / 2f
            val v = (y / params.radius + 1f) / 2f
            uvs.addAll(listOf(u, v))
        }

        // Generate indices (triangles from center to circumference)
        for (i in 1..segs) {
            indices.addAll(listOf(i, i + 1, 0))
        }

        // Set attributes
        setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    /**
     * Update circle parameters
     */
    fun setParameters(
        radius: Float = parameters.radius,
        segments: Int = parameters.segments,
        thetaStart: Float = parameters.thetaStart,
        thetaLength: Float = parameters.thetaLength
    ) {
        parameters.set(radius, segments, thetaStart, thetaLength)
        updateIfNeeded()
    }
}
