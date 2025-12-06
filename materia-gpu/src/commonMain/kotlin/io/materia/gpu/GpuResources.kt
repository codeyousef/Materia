package io.materia.gpu


/**
 * Flags indicating how a GPU buffer will be used.
 *
 * Multiple usages can be combined with [gpuBufferUsage].
 */
enum class GpuBufferUsage(internal val mask: Int) {
    /** Buffer can be mapped for reading by the CPU. */
    MAP_READ(0x0001),
    /** Buffer can be mapped for writing by the CPU. */
    MAP_WRITE(0x0002),
    /** Buffer can be used as a copy source. */
    COPY_SRC(0x0004),
    /** Buffer can be used as a copy destination. */
    COPY_DST(0x0008),
    /** Buffer can be used as an index buffer. */
    INDEX(0x0010),
    /** Buffer can be used as a vertex buffer. */
    VERTEX(0x0020),
    /** Buffer can be used as a uniform buffer. */
    UNIFORM(0x0040),
    /** Buffer can be used as a storage buffer. */
    STORAGE(0x0080),
    /** Buffer can be used for indirect draw/dispatch commands. */
    INDIRECT(0x0100)
}

typealias GpuBufferUsageFlags = Int

/**
 * Combines multiple [GpuBufferUsage] flags into a single bitmask.
 *
 * @param usages The usage flags to combine.
 * @return A bitmask representing all specified usages.
 */
fun gpuBufferUsage(vararg usages: GpuBufferUsage): GpuBufferUsageFlags =
    usages.fold(0) { acc, usage -> acc or usage.mask }

/**
 * Configuration for creating a GPU buffer.
 *
 * @property label Optional debug label.
 * @property size Buffer size in bytes.
 * @property usage Bitmask of allowed buffer usages.
 * @property mappedAtCreation If true, buffer is created in mapped state.
 */
data class GpuBufferDescriptor(
    val label: String? = null,
    val size: Long,
    val usage: GpuBufferUsageFlags,
    val mappedAtCreation: Boolean = false
)

/**
 * Supported texture pixel formats.
 *
 * This is a subset of formats commonly supported across all backends.
 */
enum class GpuTextureFormat {
    /** 8-bit RGBA, unsigned normalized. */
    RGBA8_UNORM,
    /** 8-bit BGRA, unsigned normalized (common swap chain format). */
    BGRA8_UNORM,
    /** 16-bit RGBA float for HDR rendering. */
    RGBA16_FLOAT,
    /** 24-bit depth buffer. */
    DEPTH24_PLUS
}

/** Texture dimensionality. */
enum class GpuTextureDimension {
    /** 1D texture. */
    D1,
    /** 2D texture (most common). */
    D2,
    /** 3D volume texture. */
    D3
}

/**
 * Flags indicating how a texture will be used.
 */
enum class GpuTextureUsage(internal val mask: Int) {
    /** Texture can be used as a copy source. */
    COPY_SRC(0x01),
    /** Texture can be used as a copy destination. */
    COPY_DST(0x02),
    /** Texture can be sampled in shaders. */
    TEXTURE_BINDING(0x04),
    /** Texture can be used as a storage texture in compute shaders. */
    STORAGE_BINDING(0x08),
    /** Texture can be used as a render target. */
    RENDER_ATTACHMENT(0x10)
}

typealias GpuTextureUsageFlags = Int

/**
 * Combines multiple [GpuTextureUsage] flags into a single bitmask.
 *
 * @param usages The usage flags to combine.
 * @return A bitmask representing all specified usages.
 */
fun gpuTextureUsage(vararg usages: GpuTextureUsage): GpuTextureUsageFlags =
    usages.fold(0) { acc, usage -> acc or usage.mask }

/**
 * Configuration for creating a GPU texture.
 *
 * @property label Optional debug label.
 * @property size Texture dimensions (width, height, depth/layers).
 * @property mipLevelCount Number of mipmap levels.
 * @property sampleCount MSAA sample count.
 * @property dimension Texture dimensionality.
 * @property format Pixel format.
 * @property usage Bitmask of allowed texture usages.
 */
data class GpuTextureDescriptor(
    val label: String? = null,
    val size: Triple<Int, Int, Int>,
    val mipLevelCount: Int = 1,
    val sampleCount: Int = 1,
    val dimension: GpuTextureDimension = GpuTextureDimension.D2,
    val format: GpuTextureFormat,
    val usage: GpuTextureUsageFlags
)

/**
 * Configuration for creating a texture view.
 *
 * @property label Optional debug label.
 * @property format Override format (null = inherit from texture).
 * @property baseMipLevel First mip level accessible.
 * @property mipLevelCount Number of mip levels accessible.
 * @property baseArrayLayer First array layer accessible.
 * @property arrayLayerCount Number of array layers accessible.
 */
data class GpuTextureViewDescriptor(
    val label: String? = null,
    val format: GpuTextureFormat? = null,
    val baseMipLevel: Int = 0,
    val mipLevelCount: Int? = null,
    val baseArrayLayer: Int = 0,
    val arrayLayerCount: Int? = null
)

/** Texture filtering modes for magnification and minification. */
enum class GpuFilterMode {
    /** Nearest-neighbor (blocky) filtering. */
    NEAREST,
    /** Linear (smooth) filtering. */
    LINEAR
}

/** Mipmap filtering mode when sampling between mip levels. */
enum class GpuMipmapFilterMode {
    /** Select nearest mip level. */
    NEAREST,
    /** Blend between adjacent mip levels. */
    LINEAR
}

/** Texture coordinate wrapping modes. */
enum class GpuAddressMode {
    /** Clamp coordinates to [0, 1]. */
    CLAMP_TO_EDGE,
    /** Repeat the texture. */
    REPEAT,
    /** Mirror and repeat. */
    MIRROR_REPEAT
}

/**
 * Configuration for creating a texture sampler.
 *
 * @property label Optional debug label.
 * @property addressModeU Wrapping mode for U coordinate.
 * @property addressModeV Wrapping mode for V coordinate.
 * @property addressModeW Wrapping mode for W coordinate.
 * @property magFilter Filter when magnifying.
 * @property minFilter Filter when minifying.
 * @property mipmapFilter Filter between mip levels.
 * @property lodMinClamp Minimum level of detail.
 * @property lodMaxClamp Maximum level of detail.
 */
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

/** Alpha compositing mode for surface presentation. */
enum class GpuCompositeAlphaMode {
    /** Automatic mode selection. */
    AUTO,
    /** Opaque compositing (ignore alpha). */
    OPAQUE,
    /** Premultiplied alpha. */
    PREMULTIPLIED,
    /** Unpremultiplied alpha. */
    UNPREMULTIPLIED,
    /** Inherit from parent. */
    INHERIT
}

/**
 * Configuration for a presentation surface.
 *
 * @property format Pixel format for the swap chain textures.
 * @property usage Texture usage flags for swap chain images.
 * @property width Surface width in pixels.
 * @property height Surface height in pixels.
 * @property presentMode Presentation mode (e.g., "fifo" for vsync).
 * @property alphaMode Alpha compositing mode.
 */
data class GpuSurfaceConfiguration(
    val format: GpuTextureFormat,
    val usage: GpuTextureUsageFlags,
    val width: Int,
    val height: Int,
    val presentMode: String = "fifo",
    val alphaMode: GpuCompositeAlphaMode = GpuCompositeAlphaMode.OPAQUE
)

/**
 * GPU buffer for storing vertex, index, uniform, or storage data.
 *
 * Data can be uploaded via [write] (bytes) or [writeFloats] (floats).
 * Call [destroy] to release GPU resources when no longer needed.
 */
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

/**
 * GPU texture for storing image data.
 *
 * Create views via [createView] for use in shaders or as render targets.
 * Call [destroy] to release GPU resources.
 */
expect class GpuTexture internal constructor(
    device: GpuDevice,
    descriptor: GpuTextureDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuTextureDescriptor

    fun createView(descriptor: GpuTextureViewDescriptor = GpuTextureViewDescriptor()): GpuTextureView
    fun destroy()
}

/**
 * View into a GPU texture for shader binding or render target use.
 */
expect class GpuTextureView internal constructor(
    texture: GpuTexture,
    descriptor: GpuTextureViewDescriptor
) {
    val texture: GpuTexture
    val descriptor: GpuTextureViewDescriptor
}

/**
 * Texture sampler defining filtering and address modes for shader sampling.
 */
expect class GpuSampler internal constructor(
    device: GpuDevice,
    descriptor: GpuSamplerDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuSamplerDescriptor
}
