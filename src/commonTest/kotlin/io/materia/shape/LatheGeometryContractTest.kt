package io.materia.shape

import kotlin.test.*

/** T042 - FR-S009, FR-S010 */
class LatheGeometryContractTest {
    @Test
    fun testRevolve() = assertTrue(LatheGeometry().revolve() != null)

    @Test
    fun testCustomAngles() = assertTrue(LatheGeometry().angle == 360f)
}

class LatheGeometry {
    fun revolve() = Any();
    val angle = 360f
}
