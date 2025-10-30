/**
 * Cylinder geometry with configurable radii, height, and subdivision
 */
package io.materia.geometry.primitives

import io.materia.core.math.Vector3
import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters
import kotlin.math.*

open class CylinderGeometry(
    radiusTop: Float = 1f,
    radiusBottom: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = PI.toFloat() * 2f
) : PrimitiveGeometry() {

    init {
        require(radiusTop >= 0) { "radiusTop must be >= 0, got $radiusTop" }
        require(radiusBottom >= 0) { "radiusBottom must be >= 0, got $radiusBottom" }
        require(height > 0) { "height must be > 0, got $height" }
        require(radialSegments >= 3) { "radialSegments must be >= 3, got $radialSegments" }
        require(heightSegments >= 1) { "heightSegments must be >= 1, got $heightSegments" }
    }

    class CylinderParameters(
        var radiusTop: Float,
        var radiusBottom: Float,
        var height: Float,
        var radialSegments: Int,
        var heightSegments: Int,
        var openEnded: Boolean,
        var thetaStart: Float,
        var thetaLength: Float
    ) : PrimitiveParameters() {

        fun set(
            radiusTop: Float = this.radiusTop,
            radiusBottom: Float = this.radiusBottom,
            height: Float = this.height,
            radialSegments: Int = this.radialSegments,
            heightSegments: Int = this.heightSegments,
            openEnded: Boolean = this.openEnded,
            thetaStart: Float = this.thetaStart,
            thetaLength: Float = this.thetaLength
        ) {
            if (this.radiusTop != radiusTop || this.radiusBottom != radiusBottom ||
                this.height != height || this.radialSegments != radialSegments ||
                this.heightSegments != heightSegments || this.openEnded != openEnded ||
                this.thetaStart != thetaStart || this.thetaLength != thetaLength
            ) {

                this.radiusTop = radiusTop
                this.radiusBottom = radiusBottom
                this.height = height
                this.radialSegments = radialSegments
                this.heightSegments = heightSegments
                this.openEnded = openEnded
                this.thetaStart = thetaStart
                this.thetaLength = thetaLength
                markDirty()
            }
        }
    }

    override val parameters = CylinderParameters(
        radiusTop, radiusBottom, height, radialSegments, heightSegments,
        openEnded, thetaStart, thetaLength
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
        val indexArray = mutableListOf<MutableList<Int>>()
        val halfHeight = params.height / 2f

        fun generateTorso() {
            val normal = Vector3()
            val vertex = Vector3()

            // Generate vertices, normals and uvs
            for (y in 0..params.heightSegments) {
                val indexRow = mutableListOf<Int>()
                val v = y.toFloat() / params.heightSegments.toFloat()

                // Calculate radius of current row
                val radius = v * (params.radiusBottom - params.radiusTop) + params.radiusTop

                for (x in 0..params.radialSegments) {
                    val u = x.toFloat() / params.radialSegments.toFloat()
                    val theta = u * params.thetaLength + params.thetaStart

                    val sinTheta = sin(theta)
                    val cosTheta = cos(theta)

                    // Vertex
                    vertex.x = radius * sinTheta
                    vertex.y = -v * params.height + halfHeight
                    vertex.z = radius * cosTheta
                    vertices.addAll(listOf(vertex.x, vertex.y, vertex.z))

                    // Normal
                    normal.set(
                        sinTheta,
                        (params.radiusBottom - params.radiusTop) / params.height,
                        cosTheta
                    )
                    normal.normalize()
                    normals.addAll(listOf(normal.x, normal.y, normal.z))

                    // UV
                    uvs.addAll(listOf(u, 1f - v))

                    indexRow.add(index++)
                }

                indexArray.add(indexRow)
            }

            // Generate indices
            for (x in 0 until params.radialSegments) {
                for (y in 0 until params.heightSegments) {
                    val a = indexArray[y][x]
                    val b = indexArray[y + 1][x]
                    val c = indexArray[y + 1][x + 1]
                    val d = indexArray[y][x + 1]

                    indices.addAll(listOf(a, b, d))
                    indices.addAll(listOf(b, c, d))
                }
            }
        }

        fun generateCap(top: Boolean) {
            val centerIndexStart = index
            val radius = if (top) params.radiusTop else params.radiusBottom
            val sign = if (top) 1f else -1f

            // Generate center vertex
            vertices.addAll(listOf(0f, (halfHeight * sign), 0f))
            normals.addAll(listOf(0f, sign, 0f))
            uvs.addAll(listOf(0.5f, 0.5f))
            index++

            // Generate surrounding vertices
            for (x in 0..params.radialSegments) {
                val u = x.toFloat() / params.radialSegments.toFloat()
                val theta = u * params.thetaLength + params.thetaStart

                val cosTheta = cos(theta)
                val sinTheta = sin(theta)

                // Vertex
                vertices.addAll(
                    listOf(
                        (radius * sinTheta),
                        (halfHeight * sign),
                        (radius * cosTheta)
                    )
                )

                // Normal
                normals.addAll(listOf(0f, sign, 0f))

                // UV
                uvs.addAll(listOf((cosTheta * 0.5f) + 0.5f, (sinTheta * 0.5f * sign) + 0.5f))

                index++
            }

            // Generate indices
            for (x in 0 until params.radialSegments) {
                val c = centerIndexStart
                val i = centerIndexStart + 1 + x

                if (top) {
                    indices.addAll(listOf(i, i + 1, c))
                } else {
                    indices.addAll(listOf(i + 1, i, c))
                }
            }
        }

        generateTorso()

        if (!params.openEnded) {
            if (params.radiusTop > 0f) generateCap(true)
            if (params.radiusBottom > 0f) generateCap(false)
        }

        // Set attributes
        setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        setIndex(BufferAttribute(indices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    fun setParameters(
        radiusTop: Float = parameters.radiusTop,
        radiusBottom: Float = parameters.radiusBottom,
        height: Float = parameters.height,
        radialSegments: Int = parameters.radialSegments,
        heightSegments: Int = parameters.heightSegments,
        openEnded: Boolean = parameters.openEnded,
        thetaStart: Float = parameters.thetaStart,
        thetaLength: Float = parameters.thetaLength
    ) {
        parameters.set(
            radiusTop,
            radiusBottom,
            height,
            radialSegments,
            heightSegments,
            openEnded,
            thetaStart,
            thetaLength
        )
        updateIfNeeded()
    }
}
