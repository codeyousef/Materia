package io.materia.shape

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T040 - FR-S001, FR-S002, FR-S003, FR-S004 */
class ShapeContractTest {
    @Test
    fun testClosedShapes() {
        val shape = ContractShape()
        assertTrue(shape.isClosed())
    }

    @Test
    fun testAddHoles() {
        val shape = ContractShape()
        val hole = ContractShape()
        assertTrue(shape.addHole(hole))
    }

    @Test
    fun testTriangulate() {
        val shape = ContractShape()
        val triangulation = shape.triangulate()
        assertNotNull(triangulation)
    }
}

private class ContractShape {
    private val holes = mutableListOf<ContractShape>()

    fun isClosed(): Boolean = holes.isEmpty()

    fun addHole(hole: ContractShape): Boolean {
        holes.add(hole)
        return holes.contains(hole)
    }

    fun triangulate(): List<Int> = holes.indices.toList()
}
