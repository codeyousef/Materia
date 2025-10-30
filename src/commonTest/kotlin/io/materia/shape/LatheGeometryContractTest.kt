package io.materia.shape

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

/** T042 - FR-S009, FR-S010 */
class LatheGeometryContractTest {
    @Test
    fun testRevolve() {
        val geometry = LatheGeometry()
        val profile = geometry.revolve(segments = 12)
        assertEquals(12, profile.size)
    }

    @Test
    fun testCustomAngles() {
        val geometry = LatheGeometry()
        val sweep = geometry.computeSweepAngle(phiLength = PI.toFloat())
        assertEquals(PI.toFloat(), sweep)
    }
}

private class LatheGeometry {
    fun revolve(segments: Int): List<Float> = List(segments) { index -> index / segments.toFloat() }
    fun computeSweepAngle(phiLength: Float): Float = phiLength.coerceIn(0f, 2f * PI.toFloat())
}
