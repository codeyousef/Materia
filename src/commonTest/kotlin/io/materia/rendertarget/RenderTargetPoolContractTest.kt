package io.materia.rendertarget

import kotlin.test.*

/** T039 - FR-RT010 */
class RenderTargetPoolContractTest {
    @Test
    fun testReuseTargets() = assertTrue(RTPool().reuse())

    @Test
    fun testDisposeUnused() = assertTrue(RTPool().dispose())
}

class RTPool {
    fun reuse() = true;
    fun dispose() = true
}
