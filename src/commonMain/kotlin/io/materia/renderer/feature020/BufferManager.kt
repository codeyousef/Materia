package io.materia.renderer.feature020

/**
 * Buffer manager for creating and managing GPU buffers.
 * Feature 020 - Production-Ready Renderer
 *
 * Provides cross-platform buffer creation for vertex data, index data,
 * and uniform data (transformation matrices).
 */
interface BufferManager {
    /**
     * Create vertex buffer from float array (interleaved position + color).
     *
     * Data layout: [x, y, z, r, g, b, x, y, z, r, g, b, ...]
     * Stride: 24 bytes (6 floats × 4 bytes)
     *
     * @param data Vertex data (position + color, interleaved)
     * @return Buffer handle for GPU buffer
     * @throws IllegalArgumentException if data is empty or invalid stride
     */
    fun createVertexBuffer(data: FloatArray): BufferHandle

    /**
     * Create index buffer from int array.
     *
     * @param data Triangle indices (must be multiple of 3)
     * @return Buffer handle for GPU buffer
     * @throws IllegalArgumentException if data is empty or not triangles
     */
    fun createIndexBuffer(data: IntArray): BufferHandle

    /**
     * Create uniform buffer with fixed size.
     *
     * Size must be at least 64 bytes (1× mat4x4) for MVP matrix.
     *
     * @param sizeBytes Buffer size in bytes (minimum 64)
     * @return Buffer handle for GPU buffer
     * @throws IllegalArgumentException if sizeBytes < 64
     */
    fun createUniformBuffer(sizeBytes: Int): BufferHandle

    /**
     * Update uniform buffer data (transformation matrices).
     *
     * @param handle Buffer handle from createUniformBuffer()
     * @param data Matrix data as byte array (64 bytes for mat4x4)
     * @param offset Write offset in bytes (must be 16-byte aligned)
     * @throws InvalidBufferException if handle is invalid
     * @throws IllegalArgumentException if offset not aligned or data too large
     */
    fun updateUniformBuffer(handle: BufferHandle, data: ByteArray, offset: Int = 0)

    /**
     * Destroy buffer and release GPU memory.
     *
     * @param handle Buffer handle to destroy
     * @throws InvalidBufferException if handle already destroyed
     */
    fun destroyBuffer(handle: BufferHandle)
}

/**
 * Helper extension for ByteArray to write Float at specific offset.
 */
fun ByteArray.putFloat(offset: Int, value: Float) {
    val bits = value.toRawBits()
    this[offset] = (bits and 0xFF).toByte()
    this[offset + 1] = ((bits shr 8) and 0xFF).toByte()
    this[offset + 2] = ((bits shr 16) and 0xFF).toByte()
    this[offset + 3] = ((bits shr 24) and 0xFF).toByte()
}
