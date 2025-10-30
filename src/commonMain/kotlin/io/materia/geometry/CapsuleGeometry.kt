/**
 * Capsule geometry implementation following Three.js r180 API
 * A capsule is a cylinder with hemispherical caps at both ends
 */
package io.materia.geometry

import kotlin.math.*

/**
 * Capsule geometry with hemispherical ends
 *
 * @param radius Radius of the capsule (default: 1)
 * @param length Length of the cylindrical section (default: 1)
 * @param capSegments Number of segments for the hemispherical caps (default: 4)
 * @param radialSegments Number of segments around the circumference (default: 8)
 */
class CapsuleGeometry(
    radius: Float = 1f,
    length: Float = 1f,
    capSegments: Int = 4,
    radialSegments: Int = 8
) : PrimitiveGeometry() {

    class CapsuleParameters(
        var radius: Float,
        var length: Float,
        var capSegments: Int,
        var radialSegments: Int
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            length: Float = this.length,
            capSegments: Int = this.capSegments,
            radialSegments: Int = this.radialSegments
        ) {
            if (this.radius != radius || this.length != length ||
                this.capSegments != capSegments || this.radialSegments != radialSegments
            ) {

                this.radius = radius
                this.length = length
                this.capSegments = capSegments
                this.radialSegments = radialSegments
                markDirty()
            }
        }
    }

    override val parameters = CapsuleParameters(radius, length, capSegments, radialSegments)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val halfLength = params.length / 2f
        var index = 0
        val indexArray = mutableListOf<MutableList<Int>>()

        // Generate capsule (top hemisphere + cylinder + bottom hemisphere)

        // Top hemisphere
        for (iy in 0..params.capSegments) {
            val indexRow = mutableListOf<Int>()
            val v = iy.toFloat() / params.capSegments.toFloat()
            val theta = v * PI.toFloat() / 2f  // 0 to π/2 for hemisphere

            for (ix in 0..params.radialSegments) {
                val u = ix.toFloat() / params.radialSegments.toFloat()
                val phi = u * PI.toFloat() * 2f

                // Vertex position (top hemisphere)
                val x = -params.radius * cos(phi) * sin(theta)
                val y = params.radius * cos(theta) + halfLength
                val z = params.radius * sin(phi) * sin(theta)

                vertices.addAll(listOf(x, y, z))

                // Normal (normalized position vector for sphere portion)
                val nx = -cos(phi) * sin(theta)
                val ny = cos(theta)
                val nz = sin(phi) * sin(theta)
                normals.addAll(listOf(nx, ny, nz))

                // UV coordinates
                uvs.addAll(listOf(u, v * 0.5f))  // Top half of UV space

                indexRow.add(index++)
            }
            indexArray.add(indexRow)
        }

        // Cylinder section (just top and bottom rings)
        val cylinderIndexStart = indexArray.size

        // Top ring of cylinder
        val topRing = mutableListOf<Int>()
        for (ix in 0..params.radialSegments) {
            val u = ix.toFloat() / params.radialSegments.toFloat()
            val phi = u * PI.toFloat() * 2f

            val x = -params.radius * cos(phi)
            val y = halfLength
            val z = params.radius * sin(phi)

            vertices.addAll(listOf(x, y, z))

            val nx = -cos(phi)
            val nz = sin(phi)
            normals.addAll(listOf(nx, 0f, nz))

            uvs.addAll(listOf(u, 0.5f))

            topRing.add(index++)
        }
        indexArray.add(topRing)

        // Bottom ring of cylinder
        val bottomRing = mutableListOf<Int>()
        for (ix in 0..params.radialSegments) {
            val u = ix.toFloat() / params.radialSegments.toFloat()
            val phi = u * PI.toFloat() * 2f

            val x = -params.radius * cos(phi)
            val y = -halfLength
            val z = params.radius * sin(phi)

            vertices.addAll(listOf(x, y, z))

            val nx = -cos(phi)
            val nz = sin(phi)
            normals.addAll(listOf(nx, 0f, nz))

            uvs.addAll(listOf(u, 0.5f))

            bottomRing.add(index++)
        }
        indexArray.add(bottomRing)

        // Bottom hemisphere
        for (iy in 1..params.capSegments) {
            val indexRow = mutableListOf<Int>()
            val v = iy.toFloat() / params.capSegments.toFloat()
            val theta = v * PI.toFloat() / 2f  // 0 to π/2 for hemisphere

            for (ix in 0..params.radialSegments) {
                val u = ix.toFloat() / params.radialSegments.toFloat()
                val phi = u * PI.toFloat() * 2f

                // Vertex position (bottom hemisphere, inverted)
                val x = -params.radius * cos(phi) * sin(theta)
                val y = -params.radius * cos(theta) - halfLength
                val z = params.radius * sin(phi) * sin(theta)

                vertices.addAll(listOf(x, y, z))

                // Normal (normalized position vector for sphere portion)
                val nx = -cos(phi) * sin(theta)
                val ny = -cos(theta)
                val nz = sin(phi) * sin(theta)
                normals.addAll(listOf(nx, ny, nz))

                // UV coordinates
                uvs.addAll(listOf(u, 0.5f + v * 0.5f))  // Bottom half of UV space

                indexRow.add(index++)
            }
            indexArray.add(indexRow)
        }

        // Generate indices for all sections
        for (iy in 0 until indexArray.size - 1) {
            for (ix in 0 until params.radialSegments) {
                val a = indexArray[iy][ix]
                val b = indexArray[iy + 1][ix]
                val c = indexArray[iy + 1][ix + 1]
                val d = indexArray[iy][ix + 1]

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
     * Update capsule parameters
     */
    fun setParameters(
        radius: Float = parameters.radius,
        length: Float = parameters.length,
        capSegments: Int = parameters.capSegments,
        radialSegments: Int = parameters.radialSegments
    ) {
        parameters.set(radius, length, capSegments, radialSegments)
        updateIfNeeded()
    }
}
