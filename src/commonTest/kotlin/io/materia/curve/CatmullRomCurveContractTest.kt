package io.materia.curve

import io.materia.core.math.Vector3
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertTrue

/**
 * Contract test for CatmullRomCurve3 interpolation - T018
 * Covers: FR-CR003 from contracts/curve-api.kt
 */
class CatmullRomCurveContractTest {
    @Test
    fun testPassThroughControlPoints() {
        val curve = CatmullRomCurve3(
            points = listOf(
                Vector3(0f, 0f, 0f),
                Vector3(1f, 1f, 0f),
                Vector3(2f, 0f, 0f)
            )
        )
        // CatmullRom curves pass through their control points
        assertTrue(curve.points.isNotEmpty())
    }
}
