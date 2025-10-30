/**
 * Torus geometry with configurable major/minor radius and subdivision
 */
package io.materia.geometry.primitives

import io.materia.core.math.Vector3
import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters
import kotlin.math.*

class TorusGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    radialSegments: Int = 12,
    tubularSegments: Int = 48,
    arc: Float = PI.toFloat() * 2f
) : PrimitiveGeometry() {

    init {
        require(radius > 0) { "radius must be > 0, got $radius" }
        require(tube > 0) { "tube must be > 0, got $tube" }
        require(radialSegments >= 3) { "radialSegments must be >= 3, got $radialSegments" }
        require(tubularSegments >= 3) { "tubularSegments must be >= 3, got $tubularSegments" }
    }

    class TorusParameters(
        var radius: Float,
        var tube: Float,
        var radialSegments: Int,
        var tubularSegments: Int,
        var arc: Float
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            tube: Float = this.tube,
            radialSegments: Int = this.radialSegments,
            tubularSegments: Int = this.tubularSegments,
            arc: Float = this.arc
        ) {
            if (this.radius != radius || this.tube != tube ||
                this.radialSegments != radialSegments || this.tubularSegments != tubularSegments ||
                this.arc != arc
            ) {

                this.radius = radius
                this.tube = tube
                this.radialSegments = radialSegments
                this.tubularSegments = tubularSegments
                this.arc = arc
                markDirty()
            }
        }
    }

    override val parameters = TorusParameters(radius, tube, radialSegments, tubularSegments, arc)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val center = Vector3()
        val vertex = Vector3()
        val normal = Vector3()

        // Generate vertices, normals and uvs
        for (j in 0..params.radialSegments) {
            for (i in 0..params.tubularSegments) {
                val u = i.toFloat() / params.tubularSegments.toFloat() * params.arc
                val v = j.toFloat() / params.radialSegments.toFloat() * PI.toFloat() * 2f

                // Vertex
                vertex.x = (params.radius + params.tube * cos(v)) * cos(u)
                vertex.y = (params.radius + params.tube * cos(v)) * sin(u)
                vertex.z = params.tube * sin(v)

                vertices.addAll(listOf(vertex.x, vertex.y, vertex.z))

                // Normal
                center.x = params.radius * cos(u)
                center.y = params.radius * sin(u)
                normal.copy(vertex).subtract(center).normalize()

                normals.addAll(listOf(normal.x, normal.y, normal.z))

                // UV
                uvs.addAll(
                    listOf(
                        i.toFloat() / params.tubularSegments,
                        j.toFloat() / params.radialSegments
                    )
                )
            }
        }

        // Generate indices
        for (j in 1..params.radialSegments) {
            for (i in 1..params.tubularSegments) {
                val a = (params.tubularSegments + 1) * j + i - 1
                val b = (params.tubularSegments + 1) * (j - 1) + i - 1
                val c = (params.tubularSegments + 1) * (j - 1) + i
                val d = (params.tubularSegments + 1) * j + i

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

    fun setParameters(
        radius: Float = parameters.radius,
        tube: Float = parameters.tube,
        radialSegments: Int = parameters.radialSegments,
        tubularSegments: Int = parameters.tubularSegments,
        arc: Float = parameters.arc
    ) {
        parameters.set(radius, tube, radialSegments, tubularSegments, arc)
        updateIfNeeded()
    }
}
