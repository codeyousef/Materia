package io.materia.curve

import io.materia.core.math.Vector3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract test for Base Curve parametric evaluation - T017
 * Covers: FR-CR001, FR-CR002 from contracts/curve-api.kt
 */
class CurveContractTest {
    @Test
    fun testGetPoint() {
        val curve = TestCurve()
        val point = curve.getPoint(0.5f)
        assertEquals(0.5f, point.x)
        assertEquals(0.5f, point.y)
        assertEquals(0.5f, point.z)
    }

    @Test
    fun testGetTangent() {
        val curve = TestCurve()
        val tangent = curve.getTangent(0.5f)
        assertTrue(tangent.length() > 0f)
    }
}

/**
 * Concrete curve implementation for testing
 */
class TestCurve : Curve() {
    override fun getPoint(t: Float, optionalTarget: Vector3): Vector3 {
        return optionalTarget.set(t, t, t)
    }
}
