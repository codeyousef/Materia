package io.materia.renderer.gpu

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuResourceRegistryTest {

    @Test
    fun disposeAllInvokesRegisteredCallbacksOnce() {
        val registry = GpuResourceRegistry()
        var trace = 0

        registry.register { trace += 1 }
        registry.register { trace += 10 }

        registry.disposeAll()
        assertEquals(11, trace, "Expected both disposers to be invoked")

        registry.reset()
        registry.register { trace += 100 }
        registry.disposeAll()
        assertEquals(111, trace, "Registry should be reusable after reset")
    }
}
