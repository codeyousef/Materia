package io.kreekt.engine.memory

/**
 * Frame-scoped arena for transient float allocations. Useful for building
 * uniform payloads without generating garbage every frame.
 *
 * Backed by a single [FloatArray]; allocations return lightweight [FloatSlice]
 * views into the buffer. Call [reset] after every frame to reclaim space.
 */
class FrameArena(private val capacity: Int) {
    init {
        require(capacity > 0) { "FrameArena capacity must be positive" }
    }

    private val storage = FloatArray(capacity)
    private var cursor = 0

    val used: Int
        get() = cursor

    val remaining: Int
        get() = capacity - cursor

    fun reset() {
        cursor = 0
    }

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
 * Lightweight view into a [FloatArray] without copying. Mutations directly edit
 * the underlying storage owned by the arena/buffer that produced it.
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

    fun fill(value: Float) {
        for (i in 0 until length) {
            data[offset + i] = value
        }
    }

    fun copyInto(target: FloatArray, targetOffset: Int = 0) {
        require(targetOffset >= 0) { "Target offset must be non-negative (was $targetOffset)" }
        require(targetOffset + length <= target.size) {
            "Target array too small (targetOffset=$targetOffset length=$length capacity=${target.size})"
        }
        data.copyInto(target, targetOffset, offset, offset + length)
    }

    fun toFloatArray(): FloatArray = data.copyOfRange(offset, offset + length)
}
