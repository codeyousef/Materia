/**
 * Tube geometry implementation following Three.js r180 API
 * Creates a tube that extrudes along a 3D curve path
 */
package io.materia.geometry

import io.materia.core.math.Vector3
import io.materia.curve.Curve3
import kotlin.math.*

/**
 * Tube geometry extruded along a 3D curve
 *
 * @param path The 3D curve to extrude along
 * @param tubularSegments Number of segments along the path (default: 64)
 * @param radius Radius of the tube (default: 1)
 * @param radialSegments Number of segments around the tube (default: 8)
 * @param closed Whether the tube is closed (default: false)
 */
class TubeGeometry(
    path: Curve3,
    tubularSegments: Int = 64,
    radius: Float = 1f,
    radialSegments: Int = 8,
    closed: Boolean = false
) : PrimitiveGeometry() {

    class TubeParameters(
        val path: Curve3,
        var tubularSegments: Int,
        var radius: Float,
        var radialSegments: Int,
        var closed: Boolean
    ) : PrimitiveParameters() {

        fun set(
            tubularSegments: Int = this.tubularSegments,
            radius: Float = this.radius,
            radialSegments: Int = this.radialSegments,
            closed: Boolean = this.closed
        ) {
            if (this.tubularSegments != tubularSegments || this.radius != radius ||
                this.radialSegments != radialSegments || this.closed != closed
            ) {

                this.tubularSegments = tubularSegments
                this.radius = radius
                this.radialSegments = radialSegments
                this.closed = closed
                markDirty()
            }
        }
    }

    override val parameters = TubeParameters(path, tubularSegments, radius, radialSegments, closed)

    // Frenet frames for the path
    private val frames = FrenetFrames()

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        // Generate Frenet frames for the path
        frames.computeFrenetFrames(params.path, params.tubularSegments, params.closed)

        val vertex = Vector3()
        val normal = Vector3()

        val P = Vector3()
        val N = Vector3()
        val B = Vector3()

        // Generate vertices, normals, and UVs
        for (i in 0..params.tubularSegments) {
            val u = i.toFloat() / params.tubularSegments.toFloat()

            // Get point on path
            P.copy(params.path.getPointAt(u))

            // Get Frenet frame
            N.copy(frames.normals[i])
            B.copy(frames.binormals[i])

            // Generate circle of vertices around the path point
            for (j in 0..params.radialSegments) {
                val v = j.toFloat() / params.radialSegments.toFloat()
                val angle = v * PI.toFloat() * 2f

                val sin = sin(angle)
                val cos = -cos(angle)

                // Calculate normal (perpendicular to path)
                normal.x = (cos * N.x + sin * B.x)
                normal.y = (cos * N.y + sin * B.y)
                normal.z = (cos * N.z + sin * B.z)

                // Safe normalization with zero-length check
                val normalLength = normal.length()
                if (normalLength > 0.001f) {
                    normal.normalize()
                } else {
                    // Default to radial direction if degenerate
                    normal.set(cos, sin, 0f)
                }

                normals.addAll(listOf(normal.x, normal.y, normal.z))

                // Calculate vertex position
                vertex.x = P.x + params.radius * normal.x
                vertex.y = P.y + params.radius * normal.y
                vertex.z = P.z + params.radius * normal.z

                vertices.addAll(listOf(vertex.x, vertex.y, vertex.z))

                // UV coordinates
                uvs.addAll(listOf(u, v))
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
     * Update tube parameters (excluding path which is immutable)
     */
    fun setParameters(
        tubularSegments: Int = parameters.tubularSegments,
        radius: Float = parameters.radius,
        radialSegments: Int = parameters.radialSegments,
        closed: Boolean = parameters.closed
    ) {
        parameters.set(tubularSegments, radius, radialSegments, closed)
        updateIfNeeded()
    }

    /**
     * Frenet frames calculator for curve path
     */
    private class FrenetFrames {
        val tangents = mutableListOf<Vector3>()
        val normals = mutableListOf<Vector3>()
        val binormals = mutableListOf<Vector3>()

        fun computeFrenetFrames(path: Curve3, segments: Int, closed: Boolean) {
            tangents.clear()
            normals.clear()
            binormals.clear()

            val normal = Vector3()
            val vec = Vector3()
            val mat = Array(3) { FloatArray(3) }

            val epsilon = 0.0001f
            val smallest = 0.0001f

            // Compute tangents
            for (i in 0..segments) {
                val u = i.toFloat() / segments.toFloat()
                val tangent = path.getTangentAt(u)
                val tangentLength = tangent.length()

                // Safe normalization with fallback to forward direction
                if (tangentLength > 0.001f) {
                    tangents.add(tangent.normalize())
                } else {
                    // Fallback to forward direction if tangent is degenerate
                    tangents.add(Vector3(0f, 0f, 1f))
                }
            }

            // Initial normal (perpendicular to first tangent)
            normals.add(Vector3())
            binormals.add(Vector3())

            var min = Float.POSITIVE_INFINITY
            val tx = abs(tangents[0].x)
            val ty = abs(tangents[0].y)
            val tz = abs(tangents[0].z)

            if (tx <= min) {
                min = tx
                normal.set(1f, 0f, 0f)
            }

            if (ty <= min) {
                min = ty
                normal.set(0f, 1f, 0f)
            }

            if (tz <= min) {
                normal.set(0f, 0f, 1f)
            }

            vec.crossVectors(tangents[0], normal)
            val vecLength = vec.length()
            if (vecLength > 0.001f) {
                vec.normalize()
            } else {
                // Fallback to perpendicular vector
                vec.set(0f, 1f, 0f)
            }

            normals[0].crossVectors(tangents[0], vec)
            binormals[0].crossVectors(tangents[0], normals[0])

            // Compute normal and binormal for each frame
            for (i in 1..segments) {
                normals.add(normals[i - 1].clone())
                binormals.add(binormals[i - 1].clone())

                vec.crossVectors(tangents[i - 1], tangents[i])
                val crossLength = vec.length()

                if (crossLength > epsilon) {
                    vec.normalize()
                    val dotProduct = tangents[i - 1].dot(tangents[i]).coerceIn(-1f, 1f)
                    val theta = acos(dotProduct)

                    normals[i].applyAxisAngle(vec, theta)
                }

                binormals[i].crossVectors(tangents[i], normals[i])
            }

            // If closed, adjust frames to be continuous
            if (closed) {
                var theta = acos(normals[0].dot(normals[segments]).coerceIn(-1f, 1f))
                theta /= segments.toFloat()

                if (tangents[0].dot(vec.crossVectors(normals[0], normals[segments])) > 0) {
                    theta = -theta
                }

                for (i in 1..segments) {
                    normals[i].applyAxisAngle(tangents[i], theta * i)
                    binormals[i].crossVectors(tangents[i], normals[i])
                }
            }
        }
    }
}

/**
 * Extension function for Vector3 to apply axis-angle rotation
 */
private fun Vector3.applyAxisAngle(axis: Vector3, angle: Float): Vector3 {
    val cos = cos(angle)
    val sin = sin(angle)
    val oneMinusCos = 1f - cos

    val xx = axis.x * axis.x
    val yy = axis.y * axis.y
    val zz = axis.z * axis.z
    val xy = axis.x * axis.y
    val xz = axis.x * axis.z
    val yz = axis.y * axis.z
    val xs = axis.x * sin
    val ys = axis.y * sin
    val zs = axis.z * sin

    val m11 = xx * oneMinusCos + cos
    val m12 = xy * oneMinusCos - zs
    val m13 = xz * oneMinusCos + ys

    val m21 = xy * oneMinusCos + zs
    val m22 = yy * oneMinusCos + cos
    val m23 = yz * oneMinusCos - xs

    val m31 = xz * oneMinusCos - ys
    val m32 = yz * oneMinusCos + xs
    val m33 = zz * oneMinusCos + cos

    val tx = this.x
    val ty = this.y
    val tz = this.z

    this.x = m11 * tx + m12 * ty + m13 * tz
    this.y = m21 * tx + m22 * ty + m23 * tz
    this.z = m31 * tx + m32 * ty + m33 * tz

    return this
}
