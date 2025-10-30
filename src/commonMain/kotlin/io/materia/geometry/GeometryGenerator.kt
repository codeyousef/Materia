package io.materia.geometry

import io.materia.core.math.Vector3
import io.materia.core.math.Vector2
import io.materia.core.math.floatEquals
import kotlin.math.*

/**
 * Geometry generator interface for creating primitive and procedural geometries
 * Provides factory methods for common 3D shapes and advanced geometry operations
 */
interface GeometryGenerator {
    /**
     * Creates a box geometry
     * @param width Width along X axis
     * @param height Height along Y axis
     * @param depth Depth along Z axis
     * @param segments Number of segments for each dimension [widthSegments, heightSegments, depthSegments]
     * @return Generated box geometry
     */
    fun createBox(
        width: Float,
        height: Float,
        depth: Float,
        segments: IntArray = intArrayOf(1, 1, 1)
    ): BufferGeometry

    /**
     * Creates a sphere geometry
     * @param radius Sphere radius
     * @param widthSegments Number of horizontal segments
     * @param heightSegments Number of vertical segments
     * @param phiStart Starting angle for horizontal sweep
     * @param phiLength Angle range for horizontal sweep
     * @param thetaStart Starting angle for vertical sweep
     * @param thetaLength Angle range for vertical sweep
     * @return Generated sphere geometry
     */
    fun createSphere(
        radius: Float,
        widthSegments: Int = 32,
        heightSegments: Int = 16,
        phiStart: Float = 0f,
        phiLength: Float = PI.toFloat() * 2f,
        thetaStart: Float = 0f,
        thetaLength: Float = PI.toFloat()
    ): BufferGeometry

    /**
     * Creates a plane geometry
     * @param width Width along X axis
     * @param height Height along Y axis
     * @param widthSegments Number of segments along width
     * @param heightSegments Number of segments along height
     * @return Generated plane geometry
     */
    fun createPlane(
        width: Float,
        height: Float,
        widthSegments: Int = 1,
        heightSegments: Int = 1
    ): BufferGeometry

    /**
     * Creates a cylinder geometry
     * @param radiusTop Radius at the top
     * @param radiusBottom Radius at the bottom
     * @param height Height of the cylinder
     * @param radialSegments Number of segments around the circumference
     * @param heightSegments Number of segments along the height
     * @param openEnded Whether the cylinder is open-ended
     * @param thetaStart Starting angle for sweep
     * @param thetaLength Angle range for sweep
     * @return Generated cylinder geometry
     */
    fun createCylinder(
        radiusTop: Float,
        radiusBottom: Float,
        height: Float,
        radialSegments: Int = 32,
        heightSegments: Int = 1,
        openEnded: Boolean = false,
        thetaStart: Float = 0f,
        thetaLength: Float = PI.toFloat() * 2f
    ): BufferGeometry

    /**
     * Creates a torus geometry
     * @param radius Main radius
     * @param tube Tube radius
     * @param radialSegments Number of radial segments
     * @param tubularSegments Number of tubular segments
     * @param arc Central angle
     * @return Generated torus geometry
     */
    fun createTorus(
        radius: Float,
        tube: Float,
        radialSegments: Int = 16,
        tubularSegments: Int = 100,
        arc: Float = PI.toFloat() * 2f
    ): BufferGeometry

    /**
     * Creates a cone geometry
     * @param radius Base radius
     * @param height Height of the cone
     * @param radialSegments Number of segments around the base
     * @param heightSegments Number of segments along the height
     * @param openEnded Whether the cone is open-ended
     * @param thetaStart Starting angle for sweep
     * @param thetaLength Angle range for sweep
     * @return Generated cone geometry
     */
    fun createCone(
        radius: Float,
        height: Float,
        radialSegments: Int = 32,
        heightSegments: Int = 1,
        openEnded: Boolean = false,
        thetaStart: Float = 0f,
        thetaLength: Float = PI.toFloat() * 2f
    ): BufferGeometry
}

/**
 * Default implementation of GeometryGenerator
 */
class DefaultGeometryGenerator : GeometryGenerator {

    override fun createBox(
        width: Float,
        height: Float,
        depth: Float,
        segments: IntArray
    ): BufferGeometry {
        val widthSegments = segments.getOrElse(0) { 1 }.coerceAtLeast(1)
        val heightSegments = segments.getOrElse(1) { 1 }.coerceAtLeast(1)
        val depthSegments = segments.getOrElse(2) { 1 }.coerceAtLeast(1)

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        var vertexIndex = 0

        // Generate faces
        // Front and back faces
        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            0,
            1,
            2,
            -1,
            -1,
            depth,
            height,
            width,
            depthSegments,
            heightSegments,
            vertexIndex
        )
        vertexIndex += (depthSegments + 1) * (heightSegments + 1)

        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            0,
            1,
            2,
            1,
            -1,
            depth,
            height,
            -width,
            depthSegments,
            heightSegments,
            vertexIndex
        )
        vertexIndex += (depthSegments + 1) * (heightSegments + 1)

        // Right and left faces
        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            2,
            0,
            1,
            1,
            1,
            width,
            height,
            depth,
            widthSegments,
            heightSegments,
            vertexIndex
        )
        vertexIndex += (widthSegments + 1) * (heightSegments + 1)

        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            2,
            0,
            1,
            -1,
            1,
            width,
            height,
            -depth,
            widthSegments,
            heightSegments,
            vertexIndex
        )
        vertexIndex += (widthSegments + 1) * (heightSegments + 1)

        // Top and bottom faces
        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            0,
            2,
            1,
            1,
            1,
            width,
            depth,
            height,
            widthSegments,
            depthSegments,
            vertexIndex
        )
        vertexIndex += (widthSegments + 1) * (depthSegments + 1)

        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            0,
            2,
            1,
            1,
            -1,
            width,
            depth,
            -height,
            widthSegments,
            depthSegments,
            vertexIndex
        )

        return createBufferGeometry(vertices, normals, uvs, indices)
    }

    override fun createSphere(
        radius: Float,
        widthSegments: Int,
        heightSegments: Int,
        phiStart: Float,
        phiLength: Float,
        thetaStart: Float,
        thetaLength: Float
    ): BufferGeometry {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val thetaEnd = min(thetaStart + thetaLength, PI.toFloat())

        for (iy in 0..heightSegments) {
            val v = iy.toFloat() / heightSegments
            val uOffset = if (iy == 0 && thetaStart == 0f) 0.5f / widthSegments else 0f
            val uOffsetEnd = if (iy == heightSegments && floatEquals(
                    thetaEnd,
                    PI.toFloat()
                )
            ) -0.5f / widthSegments else 0f

            for (ix in 0..widthSegments) {
                val u = ix.toFloat() / widthSegments

                val x = -radius * cos(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)
                val y = radius * cos(thetaStart + v * thetaLength)
                val z = radius * sin(phiStart + u * phiLength) * sin(thetaStart + v * thetaLength)

                vertices.addAll(listOf(x, y, z))

                val normal = Vector3(x, y, z).normalized()
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                val uvU = u + uOffset + uOffsetEnd
                uvs.addAll(listOf(uvU, 1f - v))
            }
        }

        // Generate indices
        for (iy in 0 until heightSegments) {
            for (ix in 0 until widthSegments) {
                val a = (widthSegments + 1) * iy + ix + 1
                val b = (widthSegments + 1) * iy + ix
                val c = (widthSegments + 1) * (iy + 1) + ix
                val d = (widthSegments + 1) * (iy + 1) + ix + 1

                if (iy != 0 || thetaStart > 0f) indices.addAll(listOf(a, b, d))
                if (iy != heightSegments - 1 || thetaEnd < PI) indices.addAll(listOf(b, c, d))
            }
        }

        return createBufferGeometry(vertices, normals, uvs, indices)
    }

    override fun createPlane(
        width: Float,
        height: Float,
        widthSegments: Int,
        heightSegments: Int
    ): BufferGeometry {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        buildPlane(
            vertices,
            normals,
            uvs,
            indices,
            0,
            1,
            2,
            1,
            -1,
            width,
            height,
            0f,
            widthSegments,
            heightSegments,
            0
        )

        return createBufferGeometry(vertices, normals, uvs, indices)
    }

    override fun createCylinder(
        radiusTop: Float,
        radiusBottom: Float,
        height: Float,
        radialSegments: Int,
        heightSegments: Int,
        openEnded: Boolean,
        thetaStart: Float,
        thetaLength: Float
    ): BufferGeometry {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val halfHeight = height / 2f
        var vertexIndex = 0

        // Generate torso
        for (y in 0..heightSegments) {
            val v = y.toFloat() / heightSegments
            val radius = v * (radiusTop - radiusBottom) + radiusBottom

            for (x in 0..radialSegments) {
                val u = x.toFloat() / radialSegments
                val theta = u * thetaLength + thetaStart

                val sinTheta = sin(theta)
                val cosTheta = cos(theta)

                val vertex = Vector3(radius * sinTheta, -v * height + halfHeight, radius * cosTheta)
                vertices.addAll(listOf(vertex.x, vertex.y, vertex.z))

                val normal =
                    Vector3(sinTheta, (radiusBottom - radiusTop) / height, cosTheta).normalized()
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                uvs.addAll(listOf(u, 1f - v))
            }
        }

        // Generate indices for torso
        for (x in 0 until radialSegments) {
            for (y in 0 until heightSegments) {
                val a = (radialSegments + 1) * y + x
                val b = (radialSegments + 1) * (y + 1) + x
                val c = (radialSegments + 1) * (y + 1) + x + 1
                val d = (radialSegments + 1) * y + x + 1

                indices.addAll(listOf(a, b, d))
                indices.addAll(listOf(b, c, d))
            }
        }

        vertexIndex = vertices.size / 3

        // Generate caps if not open-ended
        if (!openEnded) {
            if (radiusTop > 0f) {
                generateCap(
                    vertices,
                    normals,
                    uvs,
                    indices,
                    true,
                    radiusTop,
                    halfHeight,
                    radialSegments,
                    thetaStart,
                    thetaLength,
                    vertexIndex
                )
                vertexIndex = vertices.size / 3
            }
            if (radiusBottom > 0f) {
                generateCap(
                    vertices,
                    normals,
                    uvs,
                    indices,
                    false,
                    radiusBottom,
                    -halfHeight,
                    radialSegments,
                    thetaStart,
                    thetaLength,
                    vertexIndex
                )
            }
        }

        return createBufferGeometry(vertices, normals, uvs, indices)
    }

    override fun createTorus(
        radius: Float,
        tube: Float,
        radialSegments: Int,
        tubularSegments: Int,
        arc: Float
    ): BufferGeometry {
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        for (j in 0..radialSegments) {
            for (i in 0..tubularSegments) {
                val u = i.toFloat() / tubularSegments * arc
                val v = j.toFloat() / radialSegments * PI.toFloat() * 2f

                val x = (radius + tube * cos(v)) * cos(u)
                val y = tube * sin(v)
                val z = (radius + tube * cos(v)) * sin(u)

                vertices.addAll(listOf(x, y, z))

                val cx = radius * cos(u)
                val cy = 0f
                val cz = radius * sin(u)

                val normal = Vector3(x - cx, y - cy, z - cz).normalized()
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                uvs.addAll(listOf(i.toFloat() / tubularSegments, j.toFloat() / radialSegments))
            }
        }

        for (j in 1..radialSegments) {
            for (i in 1..tubularSegments) {
                val a = (tubularSegments + 1) * j + i - 1
                val b = (tubularSegments + 1) * (j - 1) + i - 1
                val c = (tubularSegments + 1) * (j - 1) + i
                val d = (tubularSegments + 1) * j + i

                indices.addAll(listOf(a, b, d))
                indices.addAll(listOf(b, c, d))
            }
        }

        return createBufferGeometry(vertices, normals, uvs, indices)
    }

    override fun createCone(
        radius: Float,
        height: Float,
        radialSegments: Int,
        heightSegments: Int,
        openEnded: Boolean,
        thetaStart: Float,
        thetaLength: Float
    ): BufferGeometry {
        return createCylinder(
            0f,
            radius,
            height,
            radialSegments,
            heightSegments,
            openEnded,
            thetaStart,
            thetaLength
        )
    }

    private fun buildPlane(
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        uvs: MutableList<Float>,
        indices: MutableList<Int>,
        u: Int, v: Int, w: Int,
        udir: Int, vdir: Int,
        width: Float, height: Float, depth: Float,
        gridX: Int, gridY: Int,
        offset: Int
    ) {
        val segmentWidth = width / gridX
        val segmentHeight = height / gridY

        val widthHalf = width / 2f
        val heightHalf = height / 2f
        val depthHalf = depth / 2f

        val gridX1 = gridX + 1
        val gridY1 = gridY + 1

        var vertexCounter = 0

        val vector = FloatArray(3)

        for (iy in 0 until gridY1) {
            val y = iy * segmentHeight - heightHalf

            for (ix in 0 until gridX1) {
                val x = ix * segmentWidth - widthHalf

                vector[u] = x * udir
                vector[v] = y * vdir
                vector[w] = depthHalf

                vertices.addAll(vector.toList())

                vector[u] = 0f
                vector[v] = 0f
                vector[w] = if (depth > 0) 1f else -1f

                normals.addAll(vector.toList())

                uvs.add(ix.toFloat() / gridX)
                uvs.add(1f - (iy.toFloat() / gridY))

                vertexCounter++
            }
        }

        for (iy in 0 until gridY) {
            for (ix in 0 until gridX) {
                val a = offset + ix + gridX1 * iy
                val b = offset + ix + gridX1 * (iy + 1)
                val c = offset + (ix + 1) + gridX1 * (iy + 1)
                val d = offset + (ix + 1) + gridX1 * iy

                indices.addAll(listOf(a, b, d))
                indices.addAll(listOf(b, c, d))
            }
        }
    }

    private fun generateCap(
        vertices: MutableList<Float>,
        normals: MutableList<Float>,
        uvs: MutableList<Float>,
        indices: MutableList<Int>,
        top: Boolean,
        radius: Float,
        height: Float,
        radialSegments: Int,
        thetaStart: Float,
        thetaLength: Float,
        offset: Int
    ) {
        // Center vertex
        vertices.addAll(listOf(0f, height, 0f))
        normals.addAll(listOf(0f, if (top) 1f else -1f, 0f))
        uvs.addAll(listOf(0.5f, 0.5f))

        var vertexIndex = offset + 1

        for (x in 0..radialSegments) {
            val u = x.toFloat() / radialSegments
            val theta = u * thetaLength + thetaStart

            val cosTheta = cos(theta)
            val sinTheta = sin(theta)

            vertices.addAll(listOf(radius * sinTheta, height, radius * cosTheta))
            normals.addAll(listOf(0f, if (top) 1f else -1f, 0f))
            uvs.addAll(listOf((cosTheta * 0.5f) + 0.5f, (sinTheta * 0.5f) + 0.5f))
        }

        for (x in 0 until radialSegments) {
            val c = offset
            val a = vertexIndex + x
            val b = vertexIndex + x + 1

            if (top) {
                indices.addAll(listOf(a, b, c))
            } else {
                indices.addAll(listOf(b, a, c))
            }
        }
    }

    private fun createBufferGeometry(
        vertices: List<Float>,
        normals: List<Float>,
        uvs: List<Float>,
        indices: List<Int>
    ): BufferGeometry {
        val geometry = BufferGeometry()

        geometry.setAttribute("position", BufferAttribute(vertices.toFloatArray(), 3))
        geometry.setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        geometry.setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        // Convert indices to BufferAttribute - note: this is a simplification
        // In a real implementation, we'd have proper index buffer handling
        if (indices.isNotEmpty()) {
            val indexArray = indices.map { it.toFloat() }.toFloatArray()
            geometry.setIndex(BufferAttribute(indexArray, 1))
        }

        return geometry
    }
}