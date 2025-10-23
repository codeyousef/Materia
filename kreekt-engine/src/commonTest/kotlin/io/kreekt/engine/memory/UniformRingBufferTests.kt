package io.kreekt.engine.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UniformRingBufferTests {
    @Test
    fun wrapsAfterFrameCount() {
        val ring = UniformRingBuffer(frameCapacity = 4, frameCount = 2)

        val frame0 = ring.beginFrame()
        val slice0 = frame0.allocate(4)
        slice0.fill(0f)
        slice0[0] = 1f

        val frame1 = ring.beginFrame()
        val slice1 = frame1.allocate(4)
        slice1.fill(0f)
        slice1[0] = 2f

        val frame2 = ring.beginFrame()
        val slice2 = frame2.allocate(4)
        slice2.fill(0f)
        slice2[0] = 3f

        val backing = ring.backingArray()
        // Frame 2 should have overwritten frame 0 (wrap around)
        assertEquals(3f, backing[frame2.frameOffset])
        assertEquals(2f, backing[frame1.frameOffset])
    }

    @Test
    fun preventsOverflowWithinFrame() {
        val ring = UniformRingBuffer(frameCapacity = 4, frameCount = 1)
        val frame = ring.beginFrame()
        frame.allocate(3)

        assertFailsWith<IllegalArgumentException> {
            frame.allocate(2)
        }
    }

    @Test
    fun detectsOutdatedFrameContext() {
        val ring = UniformRingBuffer(frameCapacity = 4, frameCount = 2)
        val frame0 = ring.beginFrame()
        ring.beginFrame() // Advance to next frame

        assertFailsWith<IllegalArgumentException> {
            frame0.allocate(1)
        }
    }
}
