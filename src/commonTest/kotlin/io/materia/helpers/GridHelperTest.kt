package io.materia.helpers

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class GridHelperTest {
    @Test
    fun testGridHelperCreation() {
        val helper = GridHelper()
        assertNotNull(helper)
    }
}
