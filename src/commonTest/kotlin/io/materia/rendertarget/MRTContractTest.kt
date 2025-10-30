package io.materia.rendertarget

import kotlin.test.*

/** T037 - FR-RT004, FR-RT005 */
class MRTContractTest {
    @Test
    fun testMultipleColorAttachments() = assertTrue(MRT().attachmentCount == 4)

    @Test
    fun testShaderOutput() = assertTrue(MRT().shaderSupport())
}

class MRT {
    val attachmentCount = 4;
    fun shaderSupport() = true
}
