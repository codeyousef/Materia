package io.kreekt.helpers

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull

class HemisphereHelperTest {
    @Test
    fun testHemisphereHelperCreation() {
        val helper = HemisphereHelper()
        assertNotNull(helper)
    }
}
