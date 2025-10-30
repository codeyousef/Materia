/**
 * Polyhedron geometry implementation following Three.js r180 API
 * Base class for platonic solids and custom polyhedra
 */
package io.materia.geometry

import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * Polyhedron geometry from vertices and indices with subdivision support
 *
 * @param vertices Flat array of vertex coordinates [x1,y1,z1, x2,y2,z2, ...]
 * @param indices Flat array of triangle indices [i1,i2,i3, i4,i5,i6, ...]
 * @param radius Radius of the circumscribed sphere (default: 1)
 * @param detail Number of subdivision levels (default: 0, increases vertex count exponentially)
 */
open class PolyhedronGeometry(
    vertices: FloatArray,
    indices: IntArray,
    radius: Float = 1f,
    detail: Int = 0
) : PrimitiveGeometry() {

    class PolyhedronParameters(
        val vertices: FloatArray,
        val indices: IntArray,
        var radius: Float,
        var detail: Int
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            detail: Int = this.detail
        ) {
            if (this.radius != radius || this.detail != detail) {
                this.radius = radius
                this.detail = detail
                markDirty()
            }
        }
    }

    override val parameters = PolyhedronParameters(vertices, indices, radius, detail)

    // Vertex cache for subdivision
    private val vertexCache = mutableMapOf<Long, Int>()
    private val tempVertices = mutableListOf<Vector3>()
    private val tempIndices = mutableListOf<Int>()

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        vertexCache.clear()
        tempVertices.clear()
        tempIndices.clear()

        // Initialize temp vertices from parameters
        for (i in params.vertices.indices step 3) {
            tempVertices.add(
                Vector3(
                    params.vertices[i],
                    params.vertices[i + 1],
                    params.vertices[i + 2]
                )
            )
        }

        // Subdivide faces
        for (i in params.indices.indices step 3) {
            subdivide(
                params.indices[i],
                params.indices[i + 1],
                params.indices[i + 2],
                params.detail
            )
        }

        // Project vertices onto sphere and build final arrays
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()

        for (vertex in tempVertices) {
            // Normalize to sphere surface
            vertex.normalize().multiplyScalar(params.radius)

            positions.addAll(listOf(vertex.x, vertex.y, vertex.z))

            // Normal is the normalized vertex position (for sphere)
            val normal = vertex.clone().normalize()
            normals.addAll(listOf(normal.x, normal.y, normal.z))

            // UV mapping using azimuthal equidistant projection
            val u = azimuth(vertex) / (2f * PI.toFloat()) + 0.5f
            val v = inclination(vertex) / PI.toFloat()
            uvs.addAll(listOf(u, v))
        }

        // Correct UV seam
        correctUVs(uvs)

        // Set attributes
        setAttribute("position", BufferAttribute(positions.toFloatArray(), 3))
        setAttribute("normal", BufferAttribute(normals.toFloatArray(), 3))
        setAttribute("uv", BufferAttribute(uvs.toFloatArray(), 2))
        setIndex(BufferAttribute(tempIndices.map { it.toFloat() }.toFloatArray(), 1))

        computeBoundingSphere()
    }

    /**
     * Recursively subdivide triangle into smaller triangles
     */
    private fun subdivide(a: Int, b: Int, c: Int, detail: Int) {
        val cols = detail + 1

        // Create subdivision grid
        val v = mutableListOf<MutableList<Int>>()

        for (i in 0..cols) {
            v.add(mutableListOf())
            val aj = tempVertices[a].clone().lerp(tempVertices[c], i.toFloat() / cols)
            val bj = tempVertices[b].clone().lerp(tempVertices[c], i.toFloat() / cols)
            val rows = cols - i

            for (j in 0..rows) {
                if (j == 0 && i == cols) {
                    v[i].add(getCachedVertex(aj))
                } else {
                    val vertex = aj.clone().lerp(bj, if (rows == 0) 0f else j.toFloat() / rows)
                    v[i].add(getCachedVertex(vertex))
                }
            }
        }

        // Generate faces from grid
        for (i in 0 until cols) {
            for (j in 0 until 2 * (cols - i) - 1) {
                val k = j / 2

                if (j % 2 == 0) {
                    tempIndices.add(v[i][k + 1])
                    tempIndices.add(v[i + 1][k])
                    tempIndices.add(v[i][k])
                } else {
                    tempIndices.add(v[i][k + 1])
                    tempIndices.add(v[i + 1][k + 1])
                    tempIndices.add(v[i + 1][k])
                }
            }
        }
    }

    /**
     * Get or create cached vertex
     */
    private fun getCachedVertex(vertex: Vector3): Int {
        val key = vertexKey(vertex)
        return vertexCache.getOrPut(key) {
            tempVertices.add(vertex.clone())
            tempVertices.size - 1
        }
    }

    /**
     * Generate unique key for vertex (for caching)
     */
    private fun vertexKey(vertex: Vector3): Long {
        val precision = 10000f
        val x = (vertex.x * precision).toLong()
        val y = (vertex.y * precision).toLong()
        val z = (vertex.z * precision).toLong()
        return (x shl 32) or (y shl 16) or z
    }

    /**
     * Calculate azimuth angle for UV mapping
     */
    private fun azimuth(vertex: Vector3): Float {
        return atan2(vertex.z, -vertex.x)
    }

    /**
     * Calculate inclination angle for UV mapping
     */
    private fun inclination(vertex: Vector3): Float {
        return atan2(-vertex.y, sqrt(vertex.x * vertex.x + vertex.z * vertex.z))
    }

    /**
     * Correct UV coordinates at seam (where u wraps from 1 to 0)
     */
    private fun correctUVs(uvs: MutableList<Float>) {
        val indices = index?.array ?: return

        for (i in indices.indices step 3) {
            val a = indices[i].toInt()
            val b = indices[i + 1].toInt()
            val c = indices[i + 2].toInt()

            val uvA = Pair(uvs[a * 2], uvs[a * 2 + 1])
            val uvB = Pair(uvs[b * 2], uvs[b * 2 + 1])
            val uvC = Pair(uvs[c * 2], uvs[c * 2 + 1])

            val centroidU = (uvA.first + uvB.first + uvC.first) / 3f

            // Correct UVs at seam (when triangle crosses u=0/u=1 boundary)
            if (uvA.first < 0.1f && centroidU > 0.9f) uvs[a * 2] += 1f
            if (uvB.first < 0.1f && centroidU > 0.9f) uvs[b * 2] += 1f
            if (uvC.first < 0.1f && centroidU > 0.9f) uvs[c * 2] += 1f
        }
    }

    /**
     * Update polyhedron parameters
     */
    fun setParameters(
        radius: Float = parameters.radius,
        detail: Int = parameters.detail
    ) {
        parameters.set(radius, detail)
        updateIfNeeded()
    }
}
