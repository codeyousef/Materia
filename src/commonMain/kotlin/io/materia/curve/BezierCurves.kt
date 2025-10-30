/**
 * Bézier Curves - Cubic and Quadratic Bézier curve implementations
 *
 * Implements parametric Bézier curves using de Casteljau's algorithm.
 */
package io.materia.curve

import io.materia.core.math.Vector3
import io.materia.core.math.MathUtils

/**
 * Cubic Bézier curve defined by 4 control points
 *
 * @property v0 Start point
 * @property v1 First control point
 * @property v2 Second control point
 * @property v3 End point
 */
class CubicBezierCurve3(
    val v0: Vector3,
    val v1: Vector3,
    val v2: Vector3,
    val v3: Vector3
) : Curve() {

    override fun getPoint(t: Float, optionalTarget: Vector3): Vector3 {
        val point = optionalTarget

        val tClamp = t.coerceIn(0f, 1f)
        val k = 1f - tClamp

        // Cubic Bézier formula:
        // B(t) = (1-t)³P₀ + 3(1-t)²tP₁ + 3(1-t)t²P₂ + t³P₃
        val k3 = k * k * k
        val k2t = 3f * k * k * tClamp
        val kt2 = 3f * k * tClamp * tClamp
        val t3 = tClamp * tClamp * tClamp

        point.x = k3 * v0.x + k2t * v1.x + kt2 * v2.x + t3 * v3.x
        point.y = k3 * v0.y + k2t * v1.y + kt2 * v2.y + t3 * v3.y
        point.z = k3 * v0.z + k2t * v1.z + kt2 * v2.z + t3 * v3.z

        return point
    }

    /**
     * Get tangent vector at parameter t
     */
    override fun getTangent(t: Float, optionalTarget: Vector3): Vector3 {
        val tangent = optionalTarget ?: Vector3()

        val tClamp = t.coerceIn(0f, 1f)
        val k = 1f - tClamp

        // Derivative of cubic Bézier:
        // B'(t) = 3(1-t)²(P₁-P₀) + 6(1-t)t(P₂-P₁) + 3t²(P₃-P₂)
        val k2 = k * k
        val kt2 = 2f * k * tClamp
        val t2 = tClamp * tClamp

        tangent.x = 3f * (k2 * (v1.x - v0.x) + kt2 * (v2.x - v1.x) + t2 * (v3.x - v2.x))
        tangent.y = 3f * (k2 * (v1.y - v0.y) + kt2 * (v2.y - v1.y) + t2 * (v3.y - v2.y))
        tangent.z = 3f * (k2 * (v1.z - v0.z) + kt2 * (v2.z - v1.z) + t2 * (v3.z - v2.z))

        return tangent.normalize()
    }

    /**
     * Clone this curve
     */
    fun clone(): CubicBezierCurve3 {
        return CubicBezierCurve3(
            v0.clone(),
            v1.clone(),
            v2.clone(),
            v3.clone()
        )
    }

    /**
     * Evaluate using de Casteljau's algorithm (for reference)
     */
    fun getPointDeCasteljau(t: Float, optionalTarget: Vector3 = Vector3()): Vector3 {
        val point = optionalTarget

        // First level interpolations
        val p01 = Vector3().lerpVectors(v0, v1, t)
        val p12 = Vector3().lerpVectors(v1, v2, t)
        val p23 = Vector3().lerpVectors(v2, v3, t)

        // Second level interpolations
        val p012 = Vector3().lerpVectors(p01, p12, t)
        val p123 = Vector3().lerpVectors(p12, p23, t)

        // Final interpolation
        return point.lerpVectors(p012, p123, t)
    }
}

/**
 * Quadratic Bézier curve defined by 3 control points
 *
 * @property v0 Start point
 * @property v1 Control point
 * @property v2 End point
 */
class QuadraticBezierCurve3(
    val v0: Vector3,
    val v1: Vector3,
    val v2: Vector3
) : Curve() {

    override fun getPoint(t: Float, optionalTarget: Vector3): Vector3 {
        val point = optionalTarget

        val tClamp = t.coerceIn(0f, 1f)
        val k = 1f - tClamp

        // Quadratic Bézier formula:
        // B(t) = (1-t)²P₀ + 2(1-t)tP₁ + t²P₂
        val k2 = k * k
        val kt2 = 2f * k * tClamp
        val t2 = tClamp * tClamp

        point.x = k2 * v0.x + kt2 * v1.x + t2 * v2.x
        point.y = k2 * v0.y + kt2 * v1.y + t2 * v2.y
        point.z = k2 * v0.z + kt2 * v1.z + t2 * v2.z

        return point
    }

    /**
     * Get tangent vector at parameter t
     */
    override fun getTangent(t: Float, optionalTarget: Vector3): Vector3 {
        val tangent = optionalTarget ?: Vector3()

        val tClamp = t.coerceIn(0f, 1f)

        // Derivative of quadratic Bézier:
        // B'(t) = 2(1-t)(P₁-P₀) + 2t(P₂-P₁)
        val k = 1f - tClamp

        tangent.x = 2f * (k * (v1.x - v0.x) + tClamp * (v2.x - v1.x))
        tangent.y = 2f * (k * (v1.y - v0.y) + tClamp * (v2.y - v1.y))
        tangent.z = 2f * (k * (v1.z - v0.z) + tClamp * (v2.z - v1.z))

        return tangent.normalize()
    }

    /**
     * Clone this curve
     */
    fun clone(): QuadraticBezierCurve3 {
        return QuadraticBezierCurve3(
            v0.clone(),
            v1.clone(),
            v2.clone()
        )
    }

    /**
     * Evaluate using de Casteljau's algorithm (for reference)
     */
    fun getPointDeCasteljau(t: Float, optionalTarget: Vector3 = Vector3()): Vector3 {
        val point = optionalTarget

        // First level interpolations
        val p01 = Vector3().lerpVectors(v0, v1, t)
        val p12 = Vector3().lerpVectors(v1, v2, t)

        // Final interpolation
        return point.lerpVectors(p01, p12, t)
    }
}

/**
 * Utilities for Bézier curves
 */
object BezierUtils {

    /**
     * Compute binomial coefficient
     */
    fun binomial(n: Int, k: Int): Int {
        if (k > n || k < 0) return 0
        if (k == 0 || k == n) return 1

        // Use Long to prevent overflow for larger values
        var result = 1L
        for (i in 0 until k) {
            result = (result * (n - i)) / (i + 1)
        }
        return result.toInt()
    }

    /**
     * Evaluate Bernstein polynomial
     */
    fun bernstein(n: Int, k: Int, t: Float): Float {
        return binomial(n, k).toFloat() *
                MathUtils.pow(t.toDouble(), k.toDouble()).toFloat() *
                MathUtils.pow((1 - t).toDouble(), (n - k).toDouble()).toFloat()
    }

    /**
     * General Bézier evaluation for any degree
     */
    fun evaluateBezier(
        points: List<Vector3>,
        t: Float,
        optionalTarget: Vector3 = Vector3()
    ): Vector3 {
        val n = points.size - 1
        val point = optionalTarget
        point.set(0f, 0f, 0f)

        for (i in 0..n) {
            val weight = bernstein(n, i, t)
            point.x += weight * points[i].x
            point.y += weight * points[i].y
            point.z += weight * points[i].z
        }

        return point
    }

    /**
     * Subdivide cubic Bézier at parameter t
     */
    fun subdivideCubic(
        v0: Vector3, v1: Vector3, v2: Vector3, v3: Vector3,
        t: Float
    ): Pair<List<Vector3>, List<Vector3>> {
        // de Casteljau subdivision
        val p01 = Vector3().lerpVectors(v0, v1, t)
        val p12 = Vector3().lerpVectors(v1, v2, t)
        val p23 = Vector3().lerpVectors(v2, v3, t)

        val p012 = Vector3().lerpVectors(p01, p12, t)
        val p123 = Vector3().lerpVectors(p12, p23, t)

        val p0123 = Vector3().lerpVectors(p012, p123, t)

        // First segment: v0, p01, p012, p0123
        val first = listOf(v0, p01, p012, p0123)

        // Second segment: p0123, p123, p23, v3
        val second = listOf(p0123, p123, p23, v3)

        return first to second
    }

    /**
     * Get arc length of cubic Bézier (approximation)
     */
    fun getCubicArcLength(
        v0: Vector3, v1: Vector3, v2: Vector3, v3: Vector3,
        samples: Int = 100
    ): Float {
        var length = 0f
        var lastPoint = v0

        for (i in 1..samples) {
            val t = i.toFloat() / samples
            val curve = CubicBezierCurve3(v0, v1, v2, v3)
            val point = curve.getPoint(t)
            length += point.distanceTo(lastPoint)
            lastPoint = point
        }

        return length
    }
}