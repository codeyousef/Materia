package io.materia.helpers

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class BoxHelperTest {
    @Test
    fun testBoxHelperCreation() {
        val helper = BoxHelper()
        assertNotNull(helper)
    }
}
