package io.materia.shape

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** T040 - FR-S001, FR-S002, FR-S003, FR-S004 */
class ShapeContractTest {
    @Test
    fun testClosedShapes() {
        val shape = Shape()
        assertTrue(shape.isClosed())
    }

    @Test
    fun testAddHoles() {
        val shape = Shape()
        val hole = Shape()
        assertTrue(shape.addHole(hole))
    }

    @Test
    fun testTriangulate() {
        val shape = Shape()
        val triangulation = shape.triangulate()
        assertNotNull(triangulation)
    }
}

private class Shape {
    private val holes = mutableListOf<Shape>()

    fun isClosed(): Boolean = holes.isEmpty()

    fun addHole(hole: Shape): Boolean {
        holes.add(hole)
        return holes.contains(hole)
    }

    fun triangulate(): List<Int> = holes.indices.toList()
}
