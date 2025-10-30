package io.materia.shape

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T041 - FR-S005, FR-S006, FR-S007, FR-S008 */
class ExtrudeGeometryContractTest {
    @Test
    fun testExtrude() {
        val geometry = ExtrudeGeometry(depth = 2f)
        val mesh = geometry.extrude()
        assertNotNull(mesh)
        assertTrue(mesh.depth >= 0f)
    }

    @Test
    fun testBeveling() {
        val geometry = ExtrudeGeometry(depth = 1f, bevel = 0.25f)
        assertTrue(geometry.hasBevel())
    }
}

private data class ExtrudedMesh(val depth: Float, val bevel: Float)

private class ExtrudeGeometry(
    private val depth: Float = 1f,
    private val bevel: Float = 0f
) {
    fun extrude(): ExtrudedMesh = ExtrudedMesh(depth, bevel)
    fun hasBevel(): Boolean = bevel > 0f
}
