package io.kreekt.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class DRACOLoaderTest {
    @Test
    fun testDRACOLoaderCreation() = runTest {
        val loader = DRACOLoader()
        assertNotNull(loader)
    }
}
