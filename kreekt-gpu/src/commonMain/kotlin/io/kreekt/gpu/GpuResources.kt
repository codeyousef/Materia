package io.kreekt.gpu

import kotlinx.serialization.Serializable

/**
 * Buffer usage flags mirror WebGPU semantics.
 */
enum class GpuBufferUsage {
    MAP_READ,
    MAP_WRITE,
    COPY_SRC,
    COPY_DST,
    INDEX,
    VERTEX,
    UNIFORM,
    STORAGE,
    INDIRECT
}

@Serializable
data class GpuBufferDescriptor(
    val label: String? = null,
    val size: Long,
    val usage: Set<GpuBufferUsage>,
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

enum class GpuTextureUsage {
    COPY_SRC,
    COPY_DST,
    TEXTURE_BINDING,
    STORAGE_BINDING,
    RENDER_ATTACHMENT
}

@Serializable
data class GpuTextureDescriptor(
    val label: String? = null,
    val size: Triple<Int, Int, Int>,
    val mipLevelCount: Int = 1,
    val sampleCount: Int = 1,
    val dimension: GpuTextureDimension = GpuTextureDimension.D2,
    val format: GpuTextureFormat,
    val usage: Set<GpuTextureUsage>
)

@Serializable
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

@Serializable
data class GpuSamplerDescriptor(
    val label: String? = null,
    val addressModeU: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val addressModeV: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val addressModeW: GpuAddressMode = GpuAddressMode.CLAMP_TO_EDGE,
    val magFilter: GpuFilterMode = GpuFilterMode.LINEAR,
    val minFilter: GpuFilterMode = GpuFilterMode.LINEAR,
    val mipmapFilter: GpuMipmapFilterMode = GpuMipmapFilterMode.NEAREST
)

@Serializable
data class GpuSurfaceConfiguration(
    val format: GpuTextureFormat,
    val usage: Set<GpuTextureUsage>,
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
