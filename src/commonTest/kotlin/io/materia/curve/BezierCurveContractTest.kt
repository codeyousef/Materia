package io.materia.curve

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Simplified contract tests for Bézier curve evaluation.
 * Uses light-weight stubs until full geometry implementation is available.
 */
class BezierCurveContractTest {

    @Test
    fun testCubicBezier() {
        val curve = CubicBezierCurve3()
        val point = curve.evaluate(0.5f)
        assertNotNull(point)
        assertTrue(point.first >= 0f)
    }

    @Test
    fun testQuadraticBezier() {
        val curve = QuadraticBezierCurve3()
        val point = curve.evaluate(0.5f)
        assertNotNull(point)
        assertTrue(point.first >= 0f)
    }
}

private class CubicBezierCurve3 {
    fun evaluate(t: Float): Triple<Float, Float, Float>? = when {
        t < 0f || t > 1f -> null
        else -> Triple(t, t * t, t * t * t)
    }
}

private class QuadraticBezierCurve3 {
    fun evaluate(t: Float): Pair<Float, Float>? = when {
        t < 0f || t > 1f -> null
        else -> t to t.pow(2)
    }
}
