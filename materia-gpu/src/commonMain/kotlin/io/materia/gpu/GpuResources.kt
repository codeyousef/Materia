package io.materia.gpu


/**
 * Buffer usage flags mirror WebGPU semantics.
 */
enum class GpuBufferUsage(internal val mask: Int) {
    MAP_READ(0x0001),
    MAP_WRITE(0x0002),
    COPY_SRC(0x0004),
    COPY_DST(0x0008),
    INDEX(0x0010),
    VERTEX(0x0020),
    UNIFORM(0x0040),
    STORAGE(0x0080),
    INDIRECT(0x0100)
}

typealias GpuBufferUsageFlags = Int

fun gpuBufferUsage(vararg usages: GpuBufferUsage): GpuBufferUsageFlags =
    usages.fold(0) { acc, usage -> acc or usage.mask }

data class GpuBufferDescriptor(
    val label: String? = null,
    val size: Long,
    val usage: GpuBufferUsageFlags,
    val mappedAtCreation: Boolean = false
)

/**
 * Texture formats supported across backends. This is a trimmed subset for MVP.
 */
enum class GpuTextureFormat {
    RGBA8_UNORM,
    BGRA8_UNORM,
    RGBA16_FLOAT,
    DEPTH24_PLUS
}

enum class GpuTextureDimension {
    D1,
    D2,
    D3
}

enum class GpuTextureUsage(internal val mask: Int) {
    COPY_SRC(0x01),
    COPY_DST(0x02),
    TEXTURE_BINDING(0x04),
    STORAGE_BINDING(0x08),
    RENDER_ATTACHMENT(0x10)
}

typealias GpuTextureUsageFlags = Int

fun gpuTextureUsage(vararg usages: GpuTextureUsage): GpuTextureUsageFlags =
    usages.fold(0) { acc, usage -> acc or usage.mask }

data class GpuTextureDescriptor(
    val label: String? = null,
    val size: Triple<Int, Int, Int>,
    val mipLevelCount: Int = 1,
    val sampleCount: Int = 1,
    val dimension: GpuTextureDimension = GpuTextureDimension.D2,
    val format: GpuTextureFormat,
    val usage: GpuTextureUsageFlags
)

data class GpuTextureViewDescriptor(
    val label: String? = null,
    val format: GpuTextureFormat? = null,
    val baseMipLevel: Int = 0,
    val mipLevelCount: Int? = null,
    val baseArrayLayer: Int = 0,
    val arrayLayerCount: Int? = null
)

enum class GpuFilterMode { NEAREST, LINEAR }
enum class GpuMipmapFilterMode { NEAREST, LINEAR }
enum class GpuAddressMode { CLAMP_TO_EDGE, REPEAT, MIRROR_REPEAT }

data class GpuSamplerDescriptor(
    val label: String? = null,
    val addressModeU: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val addressModeV: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val addressModeW: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val magFilter: GpuFilterMode = GpuFilterMode.LINEAR,
    val minFilter: GpuFilterMode = GpuFilterMode.LINEAR,
    val mipmapFilter: GpuMipmapFilterMode = GpuMipmapFilterMode.NEAREST,
    val lodMinClamp: Float = 0f,
    val lodMaxClamp: Float = 32f
)

data class GpuSurfaceConfiguration(
    val format: GpuTextureFormat,
    val usage: GpuTextureUsageFlags,
    val width: Int,
    val height: Int,
    val presentMode: String = "fifo"
)

expect class GpuBuffer internal constructor(
    device: GpuDevice,
    descriptor: GpuBufferDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuBufferDescriptor

    fun write(data: ByteArray, offset: Int = 0)
    fun writeFloats(data: FloatArray, offset: Int = 0)
    fun destroy()
}

expect class GpuTexture internal constructor(
    device: GpuDevice,
    descriptor: GpuTextureDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuTextureDescriptor

    fun createView(descriptor: GpuTextureViewDescriptor = GpuTextureViewDescriptor()): GpuTextureView
    fun destroy()
}

expect class GpuTextureView internal constructor(
    texture: GpuTexture,
    descriptor: GpuTextureViewDescriptor
) {
    val texture: GpuTexture
    val descriptor: GpuTextureViewDescriptor
}

expect class GpuSampler internal constructor(
    device: GpuDevice,
    descriptor: GpuSamplerDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuSamplerDescriptor
}
