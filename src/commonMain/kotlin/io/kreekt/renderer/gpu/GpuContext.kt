package io.kreekt.renderer.gpu

/**
 * Backends available for GPU rendering.
 */
enum class GpuBackend {
    WEBGPU,
    VULKAN
}

/**
 * Summary information about the active GPU device/adapter.
 */
data class GpuDeviceInfo(
    val name: String,
    val vendor: String? = null,
    val driverVersion: String? = null,
    val architecture: String? = null
)

/**
 * Common limits exposed by the GPU device.
 */
data class GpuLimits(
    val maxTextureDimension1D: Int = 0,
    val maxTextureDimension2D: Int = 0,
    val maxTextureDimension3D: Int = 0,
    val maxTextureArrayLayers: Int = 0,
    val maxBindGroups: Int = 0,
    val maxUniformBuffersPerStage: Int = 0,
    val maxStorageBuffersPerStage: Int = 0,
    val maxBufferSize: Long = 0L
) {
    companion object {
        val Empty = GpuLimits()
    }
}

/**
 * Request configuration for acquiring a GPU context.
 */
data class GpuRequestConfig(
    val preferredBackend: GpuBackend = GpuBackend.WEBGPU,
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val forceFallbackAdapter: Boolean = false,
    val label: String? = null
)

enum class GpuBufferUsage(val bits: Int) {
    MAP_READ(0x0001),
    MAP_WRITE(0x0002),
    COPY_SRC(0x0004),
    COPY_DST(0x0008),
    INDEX(0x0010),
    VERTEX(0x0020),
    UNIFORM(0x0040),
    STORAGE(0x0080),
    INDIRECT(0x0100),
    QUERY_RESOLVE(0x0200)
}

fun combineUsage(vararg usages: GpuBufferUsage): Int = usages.fold(0) { acc, usage -> acc or usage.bits }

data class GpuBufferDescriptor(
    val size: Long,
    val usage: Int,
    val mappedAtCreation: Boolean = false,
    val label: String? = null
)

enum class GpuTextureUsage(val bits: Int) {
    COPY_SRC(0x01),
    COPY_DST(0x02),
    TEXTURE_BINDING(0x04),
    STORAGE_BINDING(0x08),
    RENDER_ATTACHMENT(0x10)
}

data class GpuTextureDescriptor(
    val width: Int,
    val height: Int,
    val depthOrArrayLayers: Int = 1,
    val mipLevelCount: Int = 1,
    val sampleCount: Int = 1,
    val dimension: GpuTextureDimension = GpuTextureDimension.D2,
    val format: String,
    val usage: Int,
    val label: String? = null
)

enum class GpuTextureDimension { D1, D2, D3 }

enum class GpuSamplerFilter { NEAREST, LINEAR }

data class GpuSamplerDescriptor(
    val magFilter: GpuSamplerFilter = GpuSamplerFilter.LINEAR,
    val minFilter: GpuSamplerFilter = GpuSamplerFilter.LINEAR,
    val mipmapFilter: GpuSamplerFilter = GpuSamplerFilter.LINEAR,
    val lodMinClamp: Float = 0f,
    val lodMaxClamp: Float = 32f,
    val label: String? = null
)

enum class GpuShaderStage(val bits: Int) {
    VERTEX(0x1),
    FRAGMENT(0x2),
    COMPUTE(0x4)
}

enum class GpuBufferBindingType { UNIFORM, STORAGE, READ_ONLY_STORAGE }

data class GpuBufferBindingLayout(
    val type: GpuBufferBindingType = GpuBufferBindingType.UNIFORM,
    val hasDynamicOffset: Boolean = false,
    val minBindingSize: Long = 0L
)

enum class GpuSamplerBindingType { FILTERING, NON_FILTERING, COMPARISON }

data class GpuSamplerBindingLayout(
    val type: GpuSamplerBindingType = GpuSamplerBindingType.FILTERING
)

enum class GpuTextureSampleType { FLOAT, UNFILTERABLE_FLOAT, DEPTH, SINT, UINT }

enum class GpuTextureViewDimension { D1, D2, D2_ARRAY, CUBE, CUBE_ARRAY, D3 }

data class GpuTextureBindingLayout(
    val sampleType: GpuTextureSampleType = GpuTextureSampleType.FLOAT,
    val viewDimension: GpuTextureViewDimension = GpuTextureViewDimension.D2,
    val multisampled: Boolean = false
)

data class GpuBindGroupLayoutEntry(
    val binding: Int,
    val visibility: Int,
    val buffer: GpuBufferBindingLayout? = null,
    val sampler: GpuSamplerBindingLayout? = null,
    val texture: GpuTextureBindingLayout? = null
)

data class GpuBindGroupLayoutDescriptor(
    val entries: List<GpuBindGroupLayoutEntry>,
    val label: String? = null
)

sealed class GpuBindingResource {
    data class Buffer(
        val buffer: GpuBuffer,
        val offset: Long = 0L,
        val size: Long? = null
    ) : GpuBindingResource()

    data class Sampler(val sampler: GpuSampler) : GpuBindingResource()

    data class Texture(val textureView: GpuTextureView) : GpuBindingResource()
}

data class GpuBindGroupEntry(
    val binding: Int,
    val resource: GpuBindingResource
)

data class GpuBindGroupDescriptor(
    val layout: GpuBindGroupLayout,
    val entries: List<GpuBindGroupEntry>,
    val label: String? = null
)

data class GpuPipelineLayoutDescriptor(
    val bindGroupLayouts: List<GpuBindGroupLayout>,
    val label: String? = null
)

data class GpuTextureViewDescriptor(
    val label: String? = null,
    val format: String? = null,
    val dimension: GpuTextureViewDimension? = null,
    val aspect: String? = null,
    val baseMipLevel: Int = 0,
    val mipLevelCount: Int? = null,
    val baseArrayLayer: Int = 0,
    val arrayLayerCount: Int? = null
)

/**
 * Power preference hint for device selection.
 */
enum class GpuPowerPreference(val rawValue: String) {
    HIGH_PERFORMANCE("high-performance"),
    LOW_POWER("low-power")
}

/**
 * Logical device wrapper (backend agnostic).
 */
expect class GpuDevice {
    val backend: GpuBackend
    val info: GpuDeviceInfo
    val limits: GpuLimits

    fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray? = null): GpuBuffer
    fun createCommandEncoder(label: String? = null): GpuCommandEncoder
    fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture
    fun createSampler(descriptor: GpuSamplerDescriptor = GpuSamplerDescriptor()): GpuSampler
    fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout
    fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup
    fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout
}

/**
 * Command submission queue wrapper.
 */
expect class GpuQueue {
    val backend: GpuBackend

    fun submit(commandBuffers: List<GpuCommandBuffer>)
}

/**
 * Aggregated GPU context containing device and default queue.
 */
data class GpuContext(
    val device: GpuDevice,
    val queue: GpuQueue
)

/** Buffer handle wrapper */
expect class GpuBuffer {
    val size: Long
    val usage: Int
}

/** Command encoder wrapper */
expect class GpuCommandEncoder {
    fun finish(): GpuCommandBuffer
}

/** Command buffer wrapper */
expect class GpuCommandBuffer

/** Texture wrapper */
expect class GpuTexture {
    val width: Int
    val height: Int
    val depth: Int
    val format: String
    val mipLevelCount: Int
    fun createView(descriptor: GpuTextureViewDescriptor? = null): GpuTextureView
    fun destroy()
}

/** Sampler wrapper */
expect class GpuSampler

/** Bind group layout wrapper */
expect class GpuBindGroupLayout

/** Bind group wrapper */
expect class GpuBindGroup

/** Pipeline layout wrapper */
expect class GpuPipelineLayout

/** Texture view wrapper */
expect class GpuTextureView

/**
 * Factory responsible for providing GPU contexts for the active platform.
 */
expect object GpuDeviceFactory {
    suspend fun requestContext(config: GpuRequestConfig = GpuRequestConfig()): GpuContext
}

/**
 * Provides access to the underlying platform handle for advanced integrations.
 */
expect fun GpuDevice.unwrapHandle(): Any?

expect fun GpuDevice.unwrapPhysicalHandle(): Any?

/**
 * Provides access to the underlying queue handle for advanced integrations.
 */
expect fun GpuQueue.unwrapHandle(): Any?

/** Provides raw handle for buffer interop */
expect fun GpuBuffer.unwrapHandle(): Any?

/** Provides raw handle for command encoder */
expect fun GpuCommandEncoder.unwrapHandle(): Any?

/** Provides raw handle for command buffer */
expect fun GpuCommandBuffer.unwrapHandle(): Any?

fun GpuQueue.submit(vararg buffers: GpuCommandBuffer) = submit(buffers.toList())

/** Provides raw handle for texture */
expect fun GpuTexture.unwrapHandle(): Any?

/** Provides raw handle for sampler */
expect fun GpuSampler.unwrapHandle(): Any?

/** Provides raw handle for instance */
expect fun GpuDevice.unwrapInstance(): Any?

/** Provides raw descriptor pool handle if available */
expect fun GpuDevice.unwrapDescriptorPool(): Any?

/** Retrieves the queue family index associated with this device */
expect fun GpuDevice.queueFamilyIndex(): Int

/** Retrieves the queue family index associated with this queue */
expect fun GpuQueue.queueFamilyIndex(): Int

/** Provides raw handle for bind group layout */
expect fun GpuBindGroupLayout.unwrapHandle(): Any?

/** Provides raw handle for bind group */
expect fun GpuBindGroup.unwrapHandle(): Any?

/** Provides raw handle for pipeline layout */
expect fun GpuPipelineLayout.unwrapHandle(): Any?

/** Provides raw handle for texture view */
expect fun GpuTextureView.unwrapHandle(): Any?
