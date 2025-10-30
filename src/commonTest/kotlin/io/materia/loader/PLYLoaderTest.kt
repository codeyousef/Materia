package io.materia.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class PLYLoaderTest {
    @Test
    fun testPLYLoaderCreation() = runTest {
        val loader = PLYLoader()
        assertNotNull(loader)
    }
}
