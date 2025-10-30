/**
 * Box geometry with configurable dimensions and subdivision
 */
package io.materia.geometry.primitives

import io.materia.geometry.BufferAttribute
import io.materia.geometry.PrimitiveGeometry
import io.materia.geometry.PrimitiveParameters

class BoxGeometry(
    width: Float = 1f,
    height: Float = 1f,
    depth: Float = 1f,
    widthSegments: Int = 1,
    heightSegments: Int = 1,
    depthSegments: Int = 1
) : PrimitiveGeometry() {

    init {
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
        require(depth > 0) { "depth must be > 0, got $depth" }
        require(widthSegments >= 1) { "widthSegments must be >= 1, got $widthSegments" }
        require(heightSegments >= 1) { "heightSegments must be >= 1, got $heightSegments" }
        require(depthSegments >= 1) { "depthSegments must be >= 1, got $depthSegments" }
    }

    class BoxParameters(
        var width: Float,
        var height: Float,
        var depth: Float,
        var widthSegments: Int,
        var heightSegments: Int,
        var depthSegments: Int
    ) : PrimitiveParameters() {

        fun set(
            width: Float = this.width,
            height: Float = this.height,
            depth: Float = this.depth,
            widthSegments: Int = this.widthSegments,
            heightSegments: Int = this.heightSegments,
            depthSegments: Int = this.depthSegments
        ) {
            if (this.width != width || this.height != height || this.depth != depth ||
                this.widthSegments != widthSegments || this.heightSegments != heightSegments ||
                this.depthSegments != depthSegments
            ) {

                this.width = width
                this.height = height
                this.depth = depth
                this.widthSegments = widthSegments
                this.heightSegments = heightSegments
                this.depthSegments = depthSegments
                markDirty()
            }
        }
    }

    override val parameters =
        BoxParameters(width, height, depth, widthSegments, heightSegments, depthSegments)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        var numberOfVertices = 0

        fun buildPlane(
            u: String, v: String, w: String,
            udir: Float, vdir: Float,
            width: Float, height: Float, depth: Float,
            gridX: Int, gridY: Int
        ) {
            val segmentWidth = width / gridX
            val segmentHeight = height / gridY

            val widthHalf = width / 2
            val heightHalf = height / 2
            val depthHalf = depth / 2

            val gridX1 = gridX + 1
            val gridY1 = gridY + 1

            var vertexCounter = 0

            for (iy in 0 until gridY1) {
                val y = iy * segmentHeight - heightHalf

                for (ix in 0 until gridX1) {
                    val x = ix * segmentWidth - widthHalf

                    // Build vertex position
                    val vertex = mutableMapOf<String, Float>()
                    vertex[u] = x * udir
                    vertex[v] = y * vdir
                    vertex[w] = depthHalf

                    vertices.addAll(listOf(vertex["x"] ?: 0f, vertex["y"] ?: 0f, vertex["z"] ?: 0f))

                    // Build normal
                    val normal = mutableMapOf<String, Float>()
                    normal[u] = 0f
                    normal[v] = 0f
                    normal[w] = if (depth > 0) 1f else -1f

                    normals.addAll(listOf(normal["x"] ?: 0f, normal["y"] ?: 0f, normal["z"] ?: 0f))

                    // Build UV
                    uvs.addAll(listOf(ix.toFloat() / gridX, 1f - (iy.toFloat() / gridY)))

                    vertexCounter++
                }
            }

            // Build indices
            for (iy in 0 until gridY) {
                for (ix in 0 until gridX) {
                    val a = numberOfVertices + ix + gridX1 * iy
                    val b = numberOfVertices + ix + gridX1 * (iy + 1)
                    val c = numberOfVertices + (ix + 1) + gridX1 * (iy + 1)
                    val d = numberOfVertices + (ix + 1) + gridX1 * iy

                    indices.addAll(listOf(a, b, d))
                    indices.addAll(listOf(b, c, d))
                }
            }

            numberOfVertices = numberOfVertices + vertexCounter
        }

        // Build all six faces
        buildPlane(
            "z",
            "y",
            "x",
            -1f,
            -1f,
            params.depth,
            params.height,
            params.width,
            params.depthSegments,
            params.heightSegments
        ) // px
        buildPlane(
            "z",
            "y",
            "x",
            1f,
            -1f,
            params.depth,
            params.height,
            -params.width,
            params.depthSegments,
            params.heightSegments
        ) // nx
        buildPlane(
            "x",
            "z",
            "y",
            1f,
            1f,
            params.width,
            params.depth,
            params.height,
            params.widthSegments,
            params.depthSegments
        ) // py
        buildPlane(
            "x",
            "z",
            "y",
            1f,
            -1f,
            params.width,
            params.depth,
            -params.height,
            params.widthSegments,
            params.depthSegments
        ) // ny
        buildPlane(
            "x",
            "y",
            "z",
            1f,
            -1f,
            params.width,
            params.height,
            params.depth,
            params.widthSegments,
            params.heightSegments
        ) // pz
        buildPlane(
            "x",
            "y",
            "z",
            -1f,
            -1f,
            params.width,
            params.height,
            -params.depth,
            params.widthSegments,
            params.heightSegments
        ) // nz

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
        depth: Float = parameters.depth,
        widthSegments: Int = parameters.widthSegments,
        heightSegments: Int = parameters.heightSegments,
        depthSegments: Int = parameters.depthSegments
    ) {
        parameters.set(width, height, depth, widthSegments, heightSegments, depthSegments)
        updateIfNeeded()
    }
}
