/**
 * Parametric geometry implementation following Three.js r180 API
 * Creates geometry from a parametric function
 */
package io.materia.geometry

import io.materia.core.math.Vector3

/**
 * Parametric geometry from a mathematical function
 * The function maps (u, v) coordinates in [0,1] to 3D positions
 *
 * @param func Parametric function (u, v, target) -> target where u,v are in [0,1]
 * @param slices Number of divisions in the u direction (default: 8)
 * @param stacks Number of divisions in the v direction (default: 8)
 */
class ParametricGeometry(
    func: (u: Float, v: Float, target: Vector3) -> Vector3,
    slices: Int = 8,
    stacks: Int = 8
) : PrimitiveGeometry() {

    class ParametricParameters(
        val func: (u: Float, v: Float, target: Vector3) -> Vector3,
        var slices: Int,
        var stacks: Int
    ) : PrimitiveParameters() {

        fun set(
            slices: Int = this.slices,
            stacks: Int = this.stacks
        ) {
            if (this.slices != slices || this.stacks != stacks) {
                this.slices = slices
                this.stacks = stacks
                markDirty()
            }
        }
    }

    override val parameters = ParametricParameters(func, slices, stacks)

    init {
        generate()
    }

    override fun generate() {
        val params = parameters

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val uvs = mutableListOf<Float>()
        val indices = mutableListOf<Int>()

        val sliceCount = params.slices + 1

        // Pre-calculate positions for normal computation
        val positions = Array(sliceCount) { Array(params.stacks + 1) { Vector3() } }

        // Generate positions with safe division
        for (i in 0..params.slices) {
            val u = if (params.slices > 0) i.toFloat() / params.slices.toFloat() else 0f

            for (j in 0..params.stacks) {
                val v = if (params.stacks > 0) j.toFloat() / params.stacks.toFloat() else 0f

                val target = Vector3()
                params.func(u, v, target)
                positions[i][j] = target
            }
        }

        // Generate vertices, normals, and UVs
        val pu = Vector3()
        val pv = Vector3()
        val normal = Vector3()

        for (i in 0..params.slices) {
            val u = if (params.slices > 0) i.toFloat() / params.slices.toFloat() else 0f

            for (j in 0..params.stacks) {
                val v = if (params.stacks > 0) j.toFloat() / params.stacks.toFloat() else 0f

                val p = positions[i][j]
                vertices.addAll(listOf(p.x, p.y, p.z))

                // Calculate normal using partial derivatives
                if (i == 0) {
                    pu.copy(positions[i + 1][j]).subtract(p)
                } else if (i == params.slices) {
                    pu.copy(p).subtract(positions[i - 1][j])
                } else {
                    pu.copy(positions[i + 1][j]).subtract(positions[i - 1][j])
                }

                if (j == 0) {
                    pv.copy(positions[i][j + 1]).subtract(p)
                } else if (j == params.stacks) {
                    pv.copy(p).subtract(positions[i][j - 1])
                } else {
                    pv.copy(positions[i][j + 1]).subtract(positions[i][j - 1])
                }

                // Normal is cross product of partial derivatives
                normal.crossVectors(pu, pv)
                val normalLength = normal.length()
                if (normalLength > 0.001f) {
                    normal.normalize()
                } else {
                    // Fallback to up direction for degenerate surfaces
                    normal.set(0f, 1f, 0f)
                }
                normals.addAll(listOf(normal.x, normal.y, normal.z))

                // UV coordinates
                uvs.addAll(listOf(u, v))
            }
        }

        // Generate indices
        for (i in 0 until params.slices) {
            for (j in 0 until params.stacks) {
                val a = i * (params.stacks + 1) + j
                val b = i * (params.stacks + 1) + j + 1
                val c = (i + 1) * (params.stacks + 1) + j + 1
                val d = (i + 1) * (params.stacks + 1) + j

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
     * Update parametric parameters
     */
    fun setParameters(
        slices: Int = parameters.slices,
        stacks: Int = parameters.stacks
    ) {
        parameters.set(slices, stacks)
        updateIfNeeded()
    }
}

/**
 * Common parametric functions for convenience
 */
object ParametricFunctions {

    /**
     * Parametric sphere
     */
    fun sphere(radius: Float): (Float, Float, Vector3) -> Vector3 = { u, v, target ->
        val theta = u * kotlin.math.PI.toFloat() * 2f
        val phi = v * kotlin.math.PI.toFloat()

        target.x = radius * kotlin.math.sin(phi) * kotlin.math.cos(theta)
        target.y = radius * kotlin.math.cos(phi)
        target.z = radius * kotlin.math.sin(phi) * kotlin.math.sin(theta)
        target
    }

    /**
     * Parametric torus
     */
    fun torus(radius: Float, tube: Float): (Float, Float, Vector3) -> Vector3 = { u, v, target ->
        val uAngle = u * kotlin.math.PI.toFloat() * 2f
        val vAngle = v * kotlin.math.PI.toFloat() * 2f

        target.x = (radius + tube * kotlin.math.cos(vAngle)) * kotlin.math.cos(uAngle)
        target.y = (radius + tube * kotlin.math.cos(vAngle)) * kotlin.math.sin(uAngle)
        target.z = tube * kotlin.math.sin(vAngle)
        target
    }

    /**
     * Parametric plane
     */
    fun plane(width: Float, height: Float): (Float, Float, Vector3) -> Vector3 = { u, v, target ->
        target.x = (u - 0.5f) * width
        target.y = 0f
        target.z = (v - 0.5f) * height
        target
    }

    /**
     * Klein bottle (4D surface embedded in 3D)
     */
    fun kleinBottle(radius: Float): (Float, Float, Vector3) -> Vector3 = { u, v, target ->
        val uAngle = u * kotlin.math.PI.toFloat() * 2f
        val vAngle = v * kotlin.math.PI.toFloat() * 2f

        val r = 4f * (1f - kotlin.math.cos(uAngle) / 2f)

        if (uAngle < kotlin.math.PI) {
            target.x = 6f * kotlin.math.cos(uAngle) * (1f + kotlin.math.sin(uAngle)) +
                    r * kotlin.math.cos(uAngle) * kotlin.math.cos(vAngle)
            target.z =
                16f * kotlin.math.sin(uAngle) + r * kotlin.math.sin(uAngle) * kotlin.math.cos(vAngle)
        } else {
            target.x = 6f * kotlin.math.cos(uAngle) * (1f + kotlin.math.sin(uAngle)) +
                    r * kotlin.math.cos(vAngle + kotlin.math.PI.toFloat())
            target.z = 16f * kotlin.math.sin(uAngle)
        }

        target.y = r * kotlin.math.sin(vAngle)
        target.multiplyScalar(radius / 20f)
        target
    }

    /**
     * Möbius strip
     */
    fun mobiusStrip(radius: Float, width: Float): (Float, Float, Vector3) -> Vector3 =
        { u, v, target ->
            val uAngle = u * kotlin.math.PI.toFloat() * 2f
            val halfAngle = uAngle / 2f

            val majorRadius = radius + (v - 0.5f) * width * kotlin.math.cos(halfAngle)

            target.x = majorRadius * kotlin.math.cos(uAngle)
            target.y = majorRadius * kotlin.math.sin(uAngle)
            target.z = (v - 0.5f) * width * kotlin.math.sin(halfAngle)
            target
        }
}
