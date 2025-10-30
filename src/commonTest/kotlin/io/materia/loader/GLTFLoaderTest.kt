package io.materia.loader

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest

class GLTFLoaderTest {
    @Test
    fun testGLTFLoaderCreation() = runTest {
        val loader = GLTFLoader()
        assertNotNull(loader)
    }
}
