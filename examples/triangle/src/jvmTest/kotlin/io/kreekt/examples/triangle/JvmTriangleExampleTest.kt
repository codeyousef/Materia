package io.kreekt.examples.triangle

import io.kreekt.renderer.BackendType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JvmTriangleExampleTest {

    @Test
    fun bootProducesRendererLog() = runBlocking {
        val result = TriangleExample().boot()
        val log = result.log
        assertEquals(BackendType.WEBGPU, log.backend)
        assertNotNull(log.deviceName)
        assertEquals(2, log.meshCount)
    }
}
