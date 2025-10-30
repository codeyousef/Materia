/**
 * Torus knot geometry implementation following Three.js r180 API
 * Creates a torus knot using parametric equations
 */
package io.materia.geometry

import io.materia.core.math.Vector3
import kotlin.math.*

/**
 * Torus knot geometry with configurable parameters
 * A torus knot is a special kind of knot that lies on the surface of a torus
 *
 * @param radius Radius of the torus (default: 1)
 * @param tube Radius of the tube (default: 0.4)
 * @param tubularSegments Number of segments along the tube (default: 64)
 * @param radialSegments Number of segments around the tube cross-section (default: 8)
 * @param p Number of times the knot winds around the torus (default: 2)
 * @param q Number of times the knot winds through the torus hole (default: 3)
 */
class TorusKnotGeometry(
    radius: Float = 1f,
    tube: Float = 0.4f,
    tubularSegments: Int = 64,
    radialSegments: Int = 8,
    p: Int = 2,
    q: Int = 3
) : PrimitiveGeometry() {

    class TorusKnotParameters(
        var radius: Float,
        var tube: Float,
        var tubularSegments: Int,
        var radialSegments: Int,
        var p: Int,
        var q: Int
    ) : PrimitiveParameters() {

        fun set(
            radius: Float = this.radius,
            tube: Float = this.tube,
            tubularSegments: Int = this.tubularSegments,
            radialSegments: Int = this.radialSegments,
            p: Int = this.p,
            q: Int = this.q
        ) {
            if (this.radius != radius || this.tube != tube ||
                this.tubularSegments != tubularSegments || this.radialSegments != radialSegments ||
                this.p != p || this.q != q
            ) {

                this.radius = radius
                this.tube = tube
                this.tubularSegments = tubularSegments
                this.radialSegments = radialSegments
                this.p = p
                this.q = q
                markDirty()
            }
        }
    }

    override val parameters =
        TorusKnotParameters(radius, tube, tubularSegments, radialSegments, p, q)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Buffers for calculating curve
        val vertex = Vector3()
        val normal = Vector3()

        val P1 = Vector3()
        val P2 = Vector3()

        val B = Vector3()
        val T = Vector3()
        val N = Vector3()

        // Generate vertices, normals, and UVs
        for (i in 0..params.tubularSegments) {
            val u =
                i.toFloat() / params.tubularSegments.toFloat() * params.p.toFloat() * PI.toFloat() * 2f

            calculatePositionOnCurve(u, params.p, params.q, params.radius, P1)
            calculatePositionOnCurve(u + 0.01f, params.p, params.q, params.radius, P2)

            // Calculate Frenet frame
            T.copy(P2).subtract(P1)
            N.copy(P2).add(P1)
            B.crossVectors(T, N)
            N.crossVectors(B, T)

            // Normalize vectors with zero-length check
            val bLength = B.length()
            if (bLength > 0.001f) {
                B.normalize()
            } else {
                // Fallback to perpendicular vector
                B.set(0f, 1f, 0f)
            }

            val nLength = N.length()
            if (nLength > 0.001f) {
                N.normalize()
            } else {
                // Fallback to perpendicular vector
                N.set(1f, 0f, 0f)
            }

            for (j in 0..params.radialSegments) {
                val v = j.toFloat() / params.radialSegments.toFloat() * PI.toFloat() * 2f

                val cx = -params.tube * cos(v)
                val cy = params.tube * sin(v)

                // Vertex position
                vertex.x = P1.x + (cx * N.x + cy * B.x)
                vertex.y = P1.y + (cx * N.y + cy * B.y)
                vertex.z = P1.z + (cx * N.z + cy * B.z)

                vertices.addAll(listOf(vertex.x, vertex.y, vertex.z))

                // Normal (from center of tube to vertex)
                normal.copy(vertex).subtract(P1)
                val normalLength = normal.length()
                if (normalLength > 0.001f) {
                    normal.normalize()
                } else {
                    // Fallback to radial direction
                    normal.set(cos(v), sin(v), 0f)
                }
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                // UV coordinates
                uvs.addAll(
                    listOf(
                        i.toFloat() / params.tubularSegments.toFloat(),
                        j.toFloat() / params.radialSegments.toFloat()
                    )
                )
            }
        }

        // Generate indices
        for (j in 1..params.tubularSegments) {
            for (i in 1..params.radialSegments) {
                val a = (params.radialSegments + 1) * (j - 1) + (i - 1)
                val b = (params.radialSegments + 1) * j + (i - 1)
                val c = (params.radialSegments + 1) * j + i
                val d = (params.radialSegments + 1) * (j - 1) + i

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
     * Calculate position on the torus knot curve using parametric equations
     */
    private fun calculatePositionOnCurve(u: Float, p: Int, q: Int, radius: Float, target: Vector3) {
        val qu = q.toFloat() * u
        val pu = p.toFloat() * u
        // Safe division with check for zero p
        val quOverP = if (p != 0) qu / p.toFloat() else 0f

        val cs = cos(pu)
        val sn = sin(pu)
        val tx = radius * (2f + cs) * 0.5f
        val ty = radius * sn * 0.5f
        val tz = radius * sin(quOverP) * 0.5f

        target.x = tx * cos(qu)
        target.y = ty
        target.z = tx * sin(qu) + tz
    }

    /**
     * Update torus knot parameters
     */
    fun setParameters(
        radius: Float = parameters.radius,
        tube: Float = parameters.tube,
        tubularSegments: Int = parameters.tubularSegments,
        radialSegments: Int = parameters.radialSegments,
        p: Int = parameters.p,
        q: Int = parameters.q
    ) {
        parameters.set(radius, tube, tubularSegments, radialSegments, p, q)
        updateIfNeeded()
    }
}
