package io.kreekt.engine.memory

/**
 * Fixed-size ring buffer tailored for uniform data uploads. Each call to
 * [beginFrame] yields a [FrameContext] that hands out contiguous [FloatSlice]s
 * within the frame bucket. When the frame advances past [frameCount],
 * allocations wrap around and reuse previous buckets.
 */
class UniformRingBuffer(
    val frameCapacity: Int,
    val frameCount: Int
) {
    init {
        require(frameCapacity > 0) { "Frame capacity must be positive" }
        require(frameCount > 0) { "Frame count must be positive" }
    }

    private val storage = FloatArray(frameCapacity * frameCount)
    private var activeFrameIndex = -1
    private var cursor = 0
    private var generation = 0

    val totalCapacity: Int
        get() = storage.size

    fun beginFrame(): FrameContext {
        activeFrameIndex = (activeFrameIndex + 1).takeIf { it >= 0 } ?: 0
        if (activeFrameIndex >= frameCount) {
            activeFrameIndex = 0
        }
        cursor = 0
        generation++
        return FrameContext(activeFrameIndex, generation)
    }

    fun backingArray(): FloatArray = storage

    private fun allocateInternal(count: Int, contextGeneration: Int): FloatSlice {
        require(contextGeneration == generation) {
            "Cannot allocate using an outdated FrameContext (expected generation=$generation, was=$contextGeneration)"
        }
        require(activeFrameIndex >= 0) { "Call beginFrame() before allocating uniform data" }
        require(count >= 0) { "Allocation size must be non-negative (was $count)" }
        require(cursor + count <= frameCapacity) {
            "UniformRingBuffer overflow: requested $count floats with only ${frameCapacity - cursor} remaining (frameCapacity=$frameCapacity)"
        }

        val base = activeFrameIndex * frameCapacity + cursor
        cursor += count
        return FloatSlice(storage, base, count)
    }

    inner class FrameContext internal constructor(
        val frameIndex: Int,
        private val contextGeneration: Int
    ) {
        val frameOffset: Int
            get() = frameIndex * frameCapacity

        val remaining: Int
            get() = frameCapacity - cursor

        fun allocate(count: Int): FloatSlice = allocateInternal(count, contextGeneration)
    }
}
