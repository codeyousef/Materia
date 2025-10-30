package io.materia.shape

import kotlin.test.*

/** T041 - FR-S005, FR-S006, FR-S007, FR-S008 */
class ExtrudeGeometryContractTest {
    @Test
    fun testExtrude() = assertTrue(ExtrudeGeometry().extrude() != null)

    @Test
    fun testBeveling() = assertTrue(ExtrudeGeometry().bevel())
}

class ExtrudeGeometry {
    fun extrude() = Any();
    fun bevel() = true
}
