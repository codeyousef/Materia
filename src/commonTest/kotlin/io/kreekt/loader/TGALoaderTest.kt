package io.kreekt.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class TGALoaderTest {
    @Test
    fun testTGALoaderCreation() = runTest {
        val loader = TGALoader()
        assertNotNull(loader)
    }
}
