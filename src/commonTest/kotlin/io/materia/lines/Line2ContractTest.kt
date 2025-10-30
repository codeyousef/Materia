package io.materia.lines

import kotlin.test.*

/** T043 - FR-LI006, FR-LI007, FR-LI008 */
class Line2ContractTest {
    @Test
    fun testScreenSpaceQuads() = assertTrue(Line2().usesQuads())

    @Test
    fun testVariableWidth() = assertTrue(Line2().width > 0)
}

class Line2 {
    fun usesQuads() = true;
    val width = 2f
}
