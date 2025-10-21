package io.kreekt.examples.triangle

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JvmTriangleExampleTest {

    @Test
    fun bootProducesPipelineLog() = runBlocking {
        val log = TriangleExample().boot()
        assertEquals("triangle-pipeline", log.pipelineLabel)
        assertNotNull(log.adapterName)
    }
}
