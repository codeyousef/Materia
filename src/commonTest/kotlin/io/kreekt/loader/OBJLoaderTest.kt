package io.kreekt.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class OBJLoaderTest {
    @Test
    fun testOBJLoaderCreation() = runTest {
        val loader = OBJLoader()
        assertNotNull(loader)
    }
}
