/**
 * Ring geometry with configurable inner/outer radius and subdivision
 */
package io.materia.geometry.primitives

import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters
import kotlin.math.*

class RingGeometry(
    innerRadius: Float = 0.5f,
    outerRadius: Float = 1f,
    thetaSegments: Int = 32,
    phiSegments: Int = 1,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry() {

    init {
        require(innerRadius >= 0) { "innerRadius must be >= 0, got $innerRadius" }
        require(outerRadius > 0) { "outerRadius must be > 0, got $outerRadius" }
        require(outerRadius > innerRadius) { "outerRadius must be > innerRadius" }
        require(thetaSegments >= 3) { "thetaSegments must be >= 3, got $thetaSegments" }
        require(phiSegments >= 1) { "phiSegments must be >= 1, got $phiSegments" }
    }

    class RingParameters(
        var innerRadius: Float,
        var outerRadius: Float,
        var thetaSegments: Int,
        var phiSegments: Int,
        var thetaStart: Float,
        var thetaLength: Float
    ) : PrimitiveParameters() {

        fun set(
            innerRadius: Float = this.innerRadius,
            outerRadius: Float = this.outerRadius,
            thetaSegments: Int = this.thetaSegments,
            phiSegments: Int = this.phiSegments,
            thetaStart: Float = this.thetaStart,
            thetaLength: Float = this.thetaLength
        ) {
            if (this.innerRadius != innerRadius || this.outerRadius != outerRadius ||
                this.thetaSegments != thetaSegments || this.phiSegments != phiSegments ||
                this.thetaStart != thetaStart || this.thetaLength != thetaLength
            ) {

                this.innerRadius = innerRadius
                this.outerRadius = outerRadius
                this.thetaSegments = thetaSegments
                this.phiSegments = phiSegments
                this.thetaStart = thetaStart
                this.thetaLength = thetaLength
                markDirty()
            }
        }
    }

    override val parameters = RingParameters(
        innerRadius,
        outerRadius,
        thetaSegments,
        phiSegments,
        thetaStart,
        thetaLength
    )

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        var index = 0

        // Generate vertices, normals and uvs
        for (j in 0..params.phiSegments) {
            for (i in 0..params.thetaSegments) {
                val segment =
                    params.thetaStart + (i.toFloat() / params.thetaSegments.toFloat()) * params.thetaLength

                // Calculate radius for this row
                val radius =
                    params.innerRadius + (j.toFloat() / params.phiSegments.toFloat()) * (params.outerRadius - params.innerRadius)

                // Vertex
                val x = radius * cos(segment)
                val y = radius * sin(segment)

                vertices.addAll(listOf(x, y, 0f))

                // Normal
                normals.addAll(listOf(0f, 0f, 1f))

                // UV
                val u = (x / params.outerRadius + 1f) / 2f
                val v = (y / params.outerRadius + 1f) / 2f
                uvs.addAll(listOf(u, v))

                index++
            }
        }

        // Generate indices
        for (j in 0 until params.phiSegments) {
            val thetaSegmentLevel = j * (params.thetaSegments + 1)

            for (i in 0 until params.thetaSegments) {
                val segment = i + thetaSegmentLevel

                val a = segment
                val b = segment + params.thetaSegments + 1
                val c = segment + params.thetaSegments + 1 + 1
                val d = segment + 1

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
        innerRadius: Float = parameters.innerRadius,
        outerRadius: Float = parameters.outerRadius,
        thetaSegments: Int = parameters.thetaSegments,
        phiSegments: Int = parameters.phiSegments,
        thetaStart: Float = parameters.thetaStart,
        thetaLength: Float = parameters.thetaLength
    ) {
        parameters.set(
            innerRadius,
            outerRadius,
            thetaSegments,
            phiSegments,
            thetaStart,
            thetaLength
        )
        updateIfNeeded()
    }
}
