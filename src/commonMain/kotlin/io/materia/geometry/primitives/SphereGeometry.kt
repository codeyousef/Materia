/**
 * Sphere geometry with configurable radius and subdivision
 */
package io.materia.geometry.primitives

import io.materia.core.math.floatEquals
import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters
import kotlin.math.*

class SphereGeometry(
    radius: Float = 1f,
    widthSegments: Int = 32,
    heightSegments: Int = 16,
    phiStart: Float = 0f,
    phiLength: Float = PI.toFloat() * 2f,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat()
) : PrimitiveGeometry() {

    init {
        require(radius > 0) { "radius must be > 0, got $radius" }
        require(widthSegments >= 3) { "widthSegments must be >= 3, got $widthSegments" }
        require(heightSegments >= 2) { "heightSegments must be >= 2, got $heightSegments" }
    }

    class SphereParameters(
        var radius: Float,
        var widthSegments: Int,
        var heightSegments: Int,
        var phiStart: Float,
        var phiLength: Float,
        var thetaStart: Float,
        var thetaLength: Float
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            widthSegments: Int = this.widthSegments,
            heightSegments: Int = this.heightSegments,
            phiStart: Float = this.phiStart,
            phiLength: Float = this.phiLength,
            thetaStart: Float = this.thetaStart,
            thetaLength: Float = this.thetaLength
        ) {
            if (this.radius != radius || this.widthSegments != widthSegments ||
                this.heightSegments != heightSegments || this.phiStart != phiStart ||
                this.phiLength != phiLength || this.thetaStart != thetaStart ||
                this.thetaLength != thetaLength
            ) {

                this.radius = radius
                this.widthSegments = widthSegments
                this.heightSegments = heightSegments
                this.phiStart = phiStart
                this.phiLength = phiLength
                this.thetaStart = thetaStart
                this.thetaLength = thetaLength
                markDirty()
            }
        }
    }

    override val parameters = SphereParameters(
        radius, widthSegments, heightSegments, phiStart, phiLength, thetaStart, thetaLength
    )

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        // Validate parameters
        val widthSegs = max(3, params.widthSegments)
        val heightSegs = max(2, params.heightSegments)

        val thetaEnd = min(params.thetaStart + params.thetaLength, PI.toFloat())

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        var index = 0
        val grid = mutableListOf<MutableList<Int>>()

        // Generate vertices, normals, and UVs
        for (iy in 0..heightSegs) {
            val verticesRow = mutableListOf<Int>()
            val v = iy.toFloat() / heightSegs.toFloat()

            // Special handling for poles
            var uOffset = 0f
            if (iy == 0 && params.thetaStart == 0f) {
                uOffset = 0.5f / widthSegs.toFloat()
            } else if (iy == heightSegs && floatEquals(thetaEnd, PI.toFloat())) {
                uOffset = -0.5f / widthSegs.toFloat()
            }

            for (ix in 0..widthSegs) {
                val u = ix.toFloat() / widthSegs.toFloat()

                // Vertex position
                val x = -params.radius * cos(params.phiStart + u * params.phiLength) *
                        sin(params.thetaStart + v * params.thetaLength)
                val y = params.radius * cos(params.thetaStart + v * params.thetaLength)
                val z = params.radius * sin(params.phiStart + u * params.phiLength) *
                        sin(params.thetaStart + v * params.thetaLength)

                vertices.addAll(listOf(x, y, z))

                // Normal (for a sphere, the normal is position / radius)
                normals.addAll(listOf(x / params.radius, y / params.radius, z / params.radius))

                // UV coordinates
                uvs.addAll(listOf(u + uOffset, 1f - v))

                verticesRow.add(index++)
            }

            grid.add(verticesRow)
        }

        // Generate indices
        for (iy in 0 until heightSegs) {
            for (ix in 0 until widthSegs) {
                val a = grid[iy][ix + 1]
                val b = grid[iy][ix]
                val c = grid[iy + 1][ix]
                val d = grid[iy + 1][ix + 1]

                if (iy != 0 || params.thetaStart > 0f) {
                    indices.addAll(listOf(a, b, d))
                }
                if (iy != heightSegs - 1 || !floatEquals(thetaEnd, PI.toFloat())) {
                    indices.addAll(listOf(b, c, d))
                }
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
        widthSegments: Int = parameters.widthSegments,
        heightSegments: Int = parameters.heightSegments,
        phiStart: Float = parameters.phiStart,
        phiLength: Float = parameters.phiLength,
        thetaStart: Float = parameters.thetaStart,
        thetaLength: Float = parameters.thetaLength
    ) {
        parameters.set(
            radius,
            widthSegments,
            heightSegments,
            phiStart,
            phiLength,
            thetaStart,
            thetaLength
        )
        updateIfNeeded()
    }
}
