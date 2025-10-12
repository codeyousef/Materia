package io.kreekt.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class EXRLoaderTest {
    @Test
    fun testEXRLoaderCreation() = runTest {
        val loader = EXRLoader()
        assertNotNull(loader)
    }
}
