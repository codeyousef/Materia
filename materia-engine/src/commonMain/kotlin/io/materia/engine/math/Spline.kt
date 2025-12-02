package io.materia.engine.math

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Catmull-Rom spline for smooth interpolation through control points.
 *
 * Commonly used for camera rail paths, animation curves, and procedural
 * geometry. The spline passes through all control points (unlike Bézier),
 * making it intuitive for path authoring.
 *
 * Control points are copied on construction, so the caller may reuse
 * input vectors. Sampling methods accept optional output vectors to
 * avoid allocation in hot paths.
 *
 * @param controlPoints At least two points defining the spline path.
 * @param closed If true, the spline loops back to the first point.
 * @param tension Controls curve tightness (0.5 = standard Catmull-Rom).
 * @throws IllegalArgumentException If fewer than two control points are provided.
 */
class CatmullRomSpline(
    controlPoints: List<Vec3>,
    private val closed: Boolean = false,
    private val tension: Float = 0.5f
) {
    init {
        require(controlPoints.size >= 2) { "CatmullRomSpline requires at least two control points" }
    }

    private val points: List<Vec3> = controlPoints.map { it.copy() }
    private val tmp0 = vec3()
    private val tmp1 = vec3()
    private val tmp2 = vec3()
    private val tmp3 = vec3()

    /**
     * Samples a position on the spline.
     *
     * @param t Parameter in [0, 1] where 0 is the first point and 1 is the last.
     * @param out Optional pre-allocated vector for the result.
     * @return The interpolated position.
     */
    fun point(t: Float, out: Vec3 = vec3()): Vec3 {
        if (points.size == 1) return out.set(points[0])
        val clampedT = t.coerceIn(0f, 1f)
        val segmentCount = if (closed) points.size else points.size - 1
        val scaledT = clampedT * segmentCount
        var segment = scaledT.toInt()
        var localT = scaledT - segment

        if (segment >= segmentCount) {
            segment = segmentCount - 1
            localT = 1f
        }

        val p0 = fetchPoint(segment - 1, tmp0)
        val p1 = fetchPoint(segment, tmp1)
        val p2 = fetchPoint(segment + 1, tmp2)
        val p3 = fetchPoint(segment + 2, tmp3)
        return catmullRom(out, p0, p1, p2, p3, localT)
    }

    /**
     * Computes the normalized tangent direction at a point on the spline.
     *
     * Uses numerical differentiation with a small delta.
     *
     * @param t Parameter in [0, 1].
     * @param out Optional pre-allocated vector for the result.
     * @return The unit tangent vector.
     */
    fun tangent(t: Float, out: Vec3 = vec3()): Vec3 {
        val delta = 1e-3f
        val prev = point((t - delta).coerceIn(0f, 1f), tmp0)
        val next = point((t + delta).coerceIn(0f, 1f), tmp1)
        return out.set(
            next.x - prev.x,
            next.y - prev.y,
            next.z - prev.z
        ).normalize()
    }

    /**
     * Samples the spline at evenly spaced parameter values.
     *
     * Useful for generating polyline approximations or placing objects along a path.
     *
     * @param count Number of samples to generate.
     * @param out Optional list to populate (will be cleared).
     * @return List of sampled positions.
     * @throws IllegalArgumentException If count is not positive.
     */
    fun sample(count: Int, out: MutableList<Vec3> = mutableListOf()): List<Vec3> {
        require(count > 0) { "Sample count must be positive" }
        out.clear()
        val step = 1f / (count - 1).coerceAtLeast(1)
        var t = 0f
        repeat(count) { index ->
            val target = if (index == count - 1) 1f else t
            out += point(target, vec3())
            t += step
        }
        return out
    }

    /**
     * Approximates the arc length of the spline.
     *
     * Uses linear segments between samples; higher sample counts yield more accurate results.
     *
     * @param samples Number of subdivisions for length calculation.
     * @return Approximate total length of the spline.
     * @throws IllegalArgumentException If samples is not positive.
     */
    fun length(samples: Int = 64): Float {
        require(samples > 0) { "Samples must be positive" }
        var total = 0f
        var prev = point(0f, tmp0).copy()
        val step = 1f / samples
        var t = step
        repeat(samples) {
            val current = point(t.coerceAtMost(1f), tmp1)
            val dx = current.x - prev.x
            val dy = current.y - prev.y
            val dz = current.z - prev.z
            total += sqrt(dx * dx + dy * dy + dz * dz)
            prev = current.copy()
            t += step
        }
        return total
    }

    private fun fetchPoint(index: Int, reuse: Vec3): Vec3 {
        val size = points.size
        val wrapped = when {
            closed -> ((index % size) + size) % size
            index < 0 -> 0
            index >= size -> size - 1
            else -> index
        }
        return reuse.set(points[wrapped])
    }

    private fun catmullRom(out: Vec3, p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t
        val v0x = (p2.x - p0.x) * tension
        val v0y = (p2.y - p0.y) * tension
        val v0z = (p2.z - p0.z) * tension

        val v1x = (p3.x - p1.x) * tension
        val v1y = (p3.y - p1.y) * tension
        val v1z = (p3.z - p1.z) * tension

        val x = (2f * p1.x - 2f * p2.x + v0x + v1x) * t3 +
                (-3f * p1.x + 3f * p2.x - 2f * v0x - v1x) * t2 +
                v0x * t + p1.x

        val y = (2f * p1.y - 2f * p2.y + v0y + v1y) * t3 +
                (-3f * p1.y + 3f * p2.y - 2f * v0y - v1y) * t2 +
                v0y * t + p1.y

        val z = (2f * p1.z - 2f * p2.z + v0z + v1z) * t3 +
                (-3f * p1.z + 3f * p2.z - 2f * v0z - v1z) * t2 +
                v0z * t + p1.z

        return out.set(x, y, z)
    }
}

/**
 * Common easing functions for animation and interpolation.
 *
 * All functions accept a linear progress value `t` in [0, 1] and return
 * a remapped value in [0, 1]. Use with splines to control animation pacing.
 */
object Easing {
    /** Linear interpolation (no easing). */
    fun linear(t: Float): Float = t.coerceIn(0f, 1f)

    /** Quadratic ease-in (starts slow). */
    fun easeInQuad(t: Float): Float {
        val clamped = linear(t)
        return clamped * clamped
    }

    /** Quadratic ease-out (ends slow). */
    fun easeOutQuad(t: Float): Float {
        val clamped = linear(t)
        val inv = 1f - clamped
        return 1f - inv * inv
    }

    /** Cubic ease-in-out (slow at both ends). */
    fun easeInOutCubic(t: Float): Float {
        val clamped = linear(t)
        return if (clamped < 0.5f) {
            4f * clamped * clamped * clamped
        } else {
            val p = -2f * clamped + 2f
            1f - p * p * p / 2f
        }
    }

    /** Sinusoidal ease-in-out (smooth, natural feel). */
    fun easeInOutSine(t: Float): Float {
        val clamped = linear(t)
        return (1f - cos(clamped * PI.toFloat())) * 0.5f
    }
}
