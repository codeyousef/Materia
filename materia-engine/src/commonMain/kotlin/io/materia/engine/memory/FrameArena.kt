package io.materia.engine.memory

/**
 * Frame-scoped linear allocator for transient float data.
 *
 * Provides zero-allocation slice access for building uniform buffers, vertex data,
 * or other per-frame payloads. Allocations return [FloatSlice] views into a single
 * backing array. Call [reset] at frame boundaries to reclaim all space.
 *
 * Thread-safety: Not thread-safe. Use one arena per thread or synchronize externally.
 *
 * @param capacity Maximum number of floats this arena can hold.
 * @throws IllegalArgumentException If capacity is not positive.
 */
class FrameArena(private val capacity: Int) {
    init {
        require(capacity > 0) { "FrameArena capacity must be positive" }
    }

    private val storage = FloatArray(capacity)
    private var cursor = 0

    /** Number of floats currently allocated. */
    val used: Int
        get() = cursor

    /** Number of floats available for allocation. */
    val remaining: Int
        get() = capacity - cursor

    /**
     * Resets the arena, making all capacity available again.
     *
     * Existing slices become invalid after this call.
     */
    fun reset() {
        cursor = 0
    }

    /**
     * Allocates a contiguous slice of floats.
     *
     * @param count Number of floats to allocate.
     * @return A [FloatSlice] view into the arena's backing array.
     * @throws IllegalArgumentException If count is negative or exceeds remaining capacity.
     */
    fun allocate(count: Int): FloatSlice {
        require(count >= 0) { "Allocation size must be non-negative (was $count)" }
        require(cursor + count <= capacity) {
            "FrameArena overflow: requested $count floats with only $remaining remaining (capacity=$capacity)"
        }
        val slice = FloatSlice(storage, cursor, count)
        cursor += count
        return slice
    }
}

/**
 * Zero-copy view into a region of a [FloatArray].
 *
 * Provides indexed read/write access to a contiguous slice without allocating.
 * Mutations directly modify the underlying storage owned by the arena or buffer.
 *
 * @property data The backing float array.
 * @property offset Start index within the backing array.
 * @property length Number of floats in this slice.
 */
class FloatSlice internal constructor(
    internal val data: FloatArray,
    internal val offset: Int,
    val length: Int
) {
    init {
        require(length >= 0) { "Slice length must be non-negative (was $length)" }
        require(offset >= 0) { "Slice offset must be non-negative (was $offset)" }
        require(offset + length <= data.size) {
            "Slice exceeds backing array (offset=$offset length=$length capacity=${data.size})"
        }
    }

    operator fun get(index: Int): Float {
        require(index in 0 until length) { "Index $index out of bounds (length=$length)" }
        return data[offset + index]
    }

    operator fun set(index: Int, value: Float) {
        require(index in 0 until length) { "Index $index out of bounds (length=$length)" }
        data[offset + index] = value
    }

    /**
     * Fills the entire slice with a constant value.
     *
     * @param value The value to write to all elements.
     */
    fun fill(value: Float) {
        for (i in 0 until length) {
            data[offset + i] = value
        }
    }

    /**
     * Copies this slice's contents into a target array.
     *
     * @param target Destination array.
     * @param targetOffset Starting index in the target array.
     * @throws IllegalArgumentException If target is too small.
     */
    fun copyInto(target: FloatArray, targetOffset: Int = 0) {
        require(targetOffset >= 0) { "Target offset must be non-negative (was $targetOffset)" }
        require(targetOffset + length <= target.size) {
            "Target array too small (targetOffset=$targetOffset length=$length capacity=${target.size})"
        }
        data.copyInto(target, targetOffset, offset, offset + length)
    }

    /**
     * Creates a new [FloatArray] containing a copy of this slice's data.
     *
     * @return A new array with the slice contents.
     */
    fun toFloatArray(): FloatArray = data.copyOfRange(offset, offset + length)
}
