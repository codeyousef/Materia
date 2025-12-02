package io.materia.engine.memory

/**
 * Multi-frame ring buffer for GPU uniform data.
 *
 * Divides a single backing array into [frameCount] buckets, each with [frameCapacity]
 * floats. Each frame advances to the next bucket via [beginFrame], which returns a
 * [FrameContext] for allocating slices within that frame's region.
 *
 * This design allows CPU writes to proceed while the GPU consumes previous frames,
 * avoiding synchronization stalls in double/triple-buffered rendering.
 *
 * @param frameCapacity Maximum floats available per frame.
 * @param frameCount Number of frame buckets (typically 2 or 3 for buffering).
 * @throws IllegalArgumentException If either parameter is not positive.
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

    /** Total floats across all frame buckets. */
    val totalCapacity: Int
        get() = storage.size

    /**
     * Advances to the next frame bucket and returns a context for allocations.
     *
     * Must be called once per frame before allocating uniform data.
     *
     * @return A [FrameContext] bound to the new frame.
     */
    fun beginFrame(): FrameContext {
        activeFrameIndex = (activeFrameIndex + 1).takeIf { it >= 0 } ?: 0
        if (activeFrameIndex >= frameCount) {
            activeFrameIndex = 0
        }
        cursor = 0
        generation++
        return FrameContext(activeFrameIndex, generation)
    }

    /**
     * Returns the underlying storage array.
     *
     * Useful for bulk GPU uploads covering multiple frames.
     */
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

    /**
     * Allocation context for a single frame within the ring buffer.
     *
     * Invalidated when [beginFrame] is called again. Attempting to allocate
     * with an outdated context throws an exception.
     *
     * @property frameIndex The bucket index for this frame.
     */
    inner class FrameContext internal constructor(
        val frameIndex: Int,
        private val contextGeneration: Int
    ) {
        /** Byte offset to the start of this frame's bucket in the backing array. */
        val frameOffset: Int
            get() = frameIndex * frameCapacity

        /** Number of floats still available in this frame's bucket. */
        val remaining: Int
            get() = frameCapacity - cursor

        /**
         * Allocates a slice of floats from this frame's bucket.
         *
         * @param count Number of floats to allocate.
         * @return A [FloatSlice] view into the frame's region.
         * @throws IllegalArgumentException If count exceeds remaining capacity.
         * @throws IllegalStateException If this context is outdated.
         */
        fun allocate(count: Int): FloatSlice = allocateInternal(count, contextGeneration)
    }
}
