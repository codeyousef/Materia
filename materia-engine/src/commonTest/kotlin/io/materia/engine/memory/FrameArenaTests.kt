package io.materia.engine.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FrameArenaTests {
    @Test
    fun allocateAndReset() {
        val arena = FrameArena(16)
        val first = arena.allocate(8)
        first.fill(1f)
        assertEquals(8, arena.used)
        assertEquals(8, arena.remaining)

        arena.reset()
        val second = arena.allocate(16)
        second.fill(2f)
        assertEquals(16, arena.used)
        assertEquals(0, arena.remaining)
        assertEquals(2f, second[15])
    }

    @Test
    fun guardAgainstOverflow() {
        val arena = FrameArena(8)
        arena.allocate(8)

        assertFailsWith<IllegalArgumentException> {
            arena.allocate(1)
        }
    }
}
