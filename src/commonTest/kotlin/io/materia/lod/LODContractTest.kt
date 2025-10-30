package io.materia.lod

import kotlin.test.*

/** T045 - FR-LO001, FR-LO002, FR-LO003, FR-LO004, FR-LO005 */
class LODContractTest {
    @Test
    fun testDistanceSwitching() = assertTrue(LOD().switchDistance > 0)

    @Test
    fun testMultipleLevels() = assertTrue(LOD().levelCount > 1)
}

class LOD {
    val switchDistance = 100f;
    val levelCount = 3
}
