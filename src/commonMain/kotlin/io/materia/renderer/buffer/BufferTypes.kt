package io.materia.renderer.buffer

import io.materia.renderer.RendererResult

/**
 * Buffer types
 */
enum class BufferType {
    VERTEX,
    INDEX,
    UNIFORM,
    STORAGE,
    STAGING,
    INDIRECT
}

/**
 * Buffer usage patterns
 */
enum class BufferUsage {
    STATIC,   // Data uploaded once, rarely changes
    DYNAMIC,  // Data changes frequently
    STREAM    // Data changes every frame
}

/**
 * Buffer access patterns
 */
enum class BufferAccess {
    READ_ONLY,
    WRITE_ONLY,
    READ_WRITE
}

/**
 * Vertex attribute formats
 */
enum class AttributeFormat(val size: Int, val componentCount: Int) {
    FLOAT(4, 1),
    FLOAT2(8, 2),
    FLOAT3(12, 3),
    FLOAT4(16, 4),
    INT(4, 1),
    INT2(8, 2),
    INT3(12, 3),
    INT4(16, 4),
    BYTE4_NORM(4, 4),
    UBYTE4_NORM(4, 4),
    SHORT2_NORM(4, 2),
    USHORT2_NORM(4, 2)
}

/**
 * Index formats
 */
enum class IndexType(val size: Int) {
    UINT16(2),
    UINT32(4)
}

/**
 * Vertex attribute description
 */
data class VertexAttribute(
    val location: Int,
    val format: AttributeFormat,
    val offset: Int,
    val name: String = ""
)

/**
 * Buffer allocation result
 */
sealed class BufferAllocationResult {
    data class Success(val buffer: Buffer) : BufferAllocationResult()
    data class Error(val message: String) : BufferAllocationResult()
}

/**
 * Buffer statistics
 */
data class BufferStats(
    val totalBuffers: Int = 0,
    val activeBuffers: Int = 0,
    val pooledBuffers: Int = 0,
    val totalMemory: Long = 0,
    val usedMemory: Long = 0,
    val peakMemory: Long = 0,
    val allocations: Int = 0,
    val deallocations: Int = 0
)

/**
 * Generic GPU buffer interface
 */
interface Buffer {
    val id: Int
    val type: BufferType
    val size: Long
    val usage: BufferUsage
    val access: BufferAccess
    var needsUpdate: Boolean

    fun uploadData(data: ByteArray, offset: Long = 0): RendererResult<Unit>
    fun uploadFloatData(data: FloatArray, offset: Long = 0): RendererResult<Unit>
    fun uploadIntData(data: IntArray, offset: Long = 0): RendererResult<Unit>
    fun map(access: BufferAccess): ByteArray?
    fun unmap()
    fun copyFrom(source: Buffer, srcOffset: Long = 0, dstOffset: Long = 0, size: Long = -1): RendererResult<Unit>
    fun dispose()
}

/**
 * Vertex buffer for geometry data
 */
interface VertexBuffer : Buffer {
    override val type: BufferType get() = BufferType.VERTEX
    val stride: Int
    val count: Int

    fun setAttributes(attributes: List<VertexAttribute>)
    fun bind(): RendererResult<Unit>
}

/**
 * Index buffer for triangle indices
 */
interface IndexBuffer : Buffer {
    override val type: BufferType get() = BufferType.INDEX
    val indexType: IndexType
    val count: Int

    fun bind(): RendererResult<Unit>
}

/**
 * Uniform buffer for shader uniforms
 */
interface UniformBuffer : Buffer {
    override val type: BufferType get() = BufferType.UNIFORM
    val binding: Int

    fun setUniform(name: String, value: Any): RendererResult<Unit>
    fun bind(bindingPoint: Int): RendererResult<Unit>
}

/**
 * Storage buffer for compute shaders
 */
interface StorageBuffer : Buffer {
    override val type: BufferType get() = BufferType.STORAGE
    val binding: Int

    fun bind(bindingPoint: Int): RendererResult<Unit>
}
