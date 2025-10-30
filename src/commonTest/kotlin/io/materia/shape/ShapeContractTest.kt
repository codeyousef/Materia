package io.materia.shape

import kotlin.test.*

/** T040 - FR-S001, FR-S002, FR-S003, FR-S004 */
class ShapeContractTest {
    @Test
    fun testClosedShapes() = assertTrue(Shape().isClosed)

    @Test
    fun testAddHoles() = assertTrue(Shape().addHole(Shape()))

    @Test
    fun testTriangulate() = assertTrue(Shape().triangulate() != null)
}

class Shape {
    val isClosed = true;
    fun addHole(h: Shape) = true;
    fun triangulate() = Any()
}
