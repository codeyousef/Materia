package io.kreekt.gpu

import io.kreekt.renderer.RenderSurface

/**
 * Primary GPU backend selector. Mirrors WebGPU semantics while allowing Vulkan/MoltenVK adapters.
 */
enum class GpuBackend {
    WEBGPU,
    VULKAN,
    MOLTENVK
}

/**
 * Preferred power profile when selecting adapters/devices.
 */
enum class GpuPowerPreference {
    LOW_POWER,
    HIGH_PERFORMANCE
}

/**
 * Descriptor used when instantiating a GPU instance.
 */
data class GpuInstanceDescriptor(
    val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    val label: String? = null
)

/**
 * Options supplied when requesting an adapter from an instance.
 */
data class GpuRequestAdapterOptions(
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val compatibleSurface: GpuSurface? = null,
    val forceFallbackAdapter: Boolean = false,
    val label: String? = null
)

/**
 * Hardware metadata surfaced by the adapter.
 */
data class GpuAdapterInfo(
    val name: String,
    val vendor: String? = null,
    val architecture: String? = null,
    val driverVersion: String? = null
)

/**
 * Descriptor used when creating a logical device.
 */
data class GpuDeviceDescriptor(
    val requiredFeatures: Set<String> = emptySet(),
    val requiredLimits: Map<String, Long> = emptyMap(),
    val label: String? = null
)

/**
 * Factory for instantiating GPU instances across platforms.
 */
expect suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor = GpuInstanceDescriptor()): GpuInstance

expect class GpuInstance internal constructor(
    descriptor: GpuInstanceDescriptor
) {
    val descriptor: GpuInstanceDescriptor
    suspend fun requestAdapter(options: GpuRequestAdapterOptions = GpuRequestAdapterOptions()): GpuAdapter
    fun dispose()
}

expect class GpuAdapter internal constructor(
    backend: GpuBackend,
    options: GpuRequestAdapterOptions,
    info: GpuAdapterInfo
) {
    val backend: GpuBackend
    val options: GpuRequestAdapterOptions
    val info: GpuAdapterInfo
    suspend fun requestDevice(descriptor: GpuDeviceDescriptor = GpuDeviceDescriptor()): GpuDevice
}

expect class GpuDevice internal constructor(
    adapter: GpuAdapter,
    descriptor: GpuDeviceDescriptor
) {
    val adapter: GpuAdapter
    val descriptor: GpuDeviceDescriptor
    val queue: GpuQueue

    fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer
    fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture
    fun createSampler(descriptor: GpuSamplerDescriptor = GpuSamplerDescriptor()): GpuSampler
    fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor? = null): GpuCommandEncoder
    fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule
    fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline
    fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline
    fun destroy()
}

expect class GpuQueue internal constructor(
    label: String?
) {
    val label: String?
    fun submit(commandBuffers: List<GpuCommandBuffer>)
}

expect class GpuSurface constructor(
    label: String?
) {
    val label: String?
    fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration)
    fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat
    fun acquireFrame(): GpuSurfaceFrame
    fun present(frame: GpuSurfaceFrame)
    fun resize(width: Int, height: Int)
}

data class GpuSurfaceFrame(
    val texture: GpuTexture,
    val view: GpuTextureView
)

expect fun GpuSurface.attachRenderSurface(surface: RenderSurface)
