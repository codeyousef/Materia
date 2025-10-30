/**
 * Plane geometry with configurable dimensions and subdivision
 */
package io.materia.geometry.primitives

import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters

class PlaneGeometry(
    width: Float = 1f,
    height: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1
) : PrimitiveGeometry() {

    init {
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
        require(widthSegments >= 1) { "widthSegments must be >= 1, got $widthSegments" }
        require(heightSegments >= 1) { "heightSegments must be >= 1, got $heightSegments" }
    }

    class PlaneParameters(
        var width: Float,
        var height: Float,
        var widthSegments: Int,
        var heightSegments: Int
    ) : PrimitiveParameters() {

        fun set(
            width: Float = this.width,
            height: Float = this.height,
            widthSegments: Int = this.widthSegments,
            heightSegments: Int = this.heightSegments
        ) {
            if (this.width != width || this.height != height ||
                this.widthSegments != widthSegments || this.heightSegments != heightSegments
            ) {

                this.width = width
                this.height = height
                this.widthSegments = widthSegments
                this.heightSegments = heightSegments
                markDirty()
            }
        }
    }

    override val parameters = PlaneParameters(width, height, widthSegments, heightSegments)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val widthHalf = params.width / 2f
        val heightHalf = params.height / 2f

        val gridX = params.widthSegments
        val gridY = params.heightSegments

        val gridX1 = gridX + 1
        val gridY1 = gridY + 1

        val segmentWidth = params.width / gridX
        val segmentHeight = params.height / gridY

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()

        for (iy in 0 until gridY1) {
            val y = iy * segmentHeight - heightHalf

            for (ix in 0 until gridX1) {
                val x = ix * segmentWidth - widthHalf

                vertices.addAll(listOf(x, -y, 0f))
                normals.addAll(listOf(0f, 0f, 1f))
                uvs.addAll(listOf(ix.toFloat() / gridX, 1f - (iy.toFloat() / gridY)))
            }
        }

        val indices = mutableListOf<Int>()

        for (iy in 0 until gridY) {
            for (ix in 0 until gridX) {
                val a = ix + gridX1 * iy
                val b = ix + gridX1 * (iy + 1)
                val c = (ix + 1) + gridX1 * (iy + 1)
                val d = (ix + 1) + gridX1 * iy

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
        width: Float = parameters.width,
        height: Float = parameters.height,
        widthSegments: Int = parameters.widthSegments,
        heightSegments: Int = parameters.heightSegments
    ) {
        parameters.set(width, height, widthSegments, heightSegments)
        updateIfNeeded()
    }
}
