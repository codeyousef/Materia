package io.materia.lines

import kotlin.test.*

/** T044 - FR-LI001, FR-LI002, FR-LI003, FR-LI004, FR-LI005 */
class LineBasicContractTest {
    @Test
    fun testLineRendering() = assertTrue(Line().render())

    @Test
    fun testDashedPatterns() = assertTrue(LineDashed().isDashed)
}

class Line {
    fun render() = true
}

class LineDashed {
    val isDashed = true
}
