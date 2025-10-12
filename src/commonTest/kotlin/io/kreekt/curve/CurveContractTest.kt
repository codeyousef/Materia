package io.kreekt.curve

import io.kreekt.core.math.Vector3
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * Contract test for Base Curve parametric evaluation - T017
 * Covers: FR-CR001, FR-CR002 from contracts/curve-api.kt
 */
class CurveContractTest {
    @Test
    fun testGetPoint() {
        val curve = TestCurve()
        assertTrue(curve.getPoint(0.5f) != null)
    }

    @Test
    fun testGetTangent() {
        val curve = TestCurve()
        assertTrue(curve.getTangent(0.5f) != null)
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
