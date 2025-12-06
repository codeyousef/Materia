package io.materia.gpu

import io.materia.renderer.RenderSurface

/**
 * Supported GPU backend APIs.
 *
 * The engine abstracts over multiple graphics APIs, selecting the appropriate
 * backend at runtime based on platform and availability.
 */
enum class GpuBackend {
    /** WebGPU API, available in browsers and via wgpu-native. */
    WEBGPU,
    /** Vulkan API for desktop and Android. */
    VULKAN,
    /** MoltenVK translation layer for Vulkan on Apple platforms. */
    MOLTENVK
}

/**
 * Power/performance preference when selecting a GPU adapter.
 */
enum class GpuPowerPreference {
    /** Prefer integrated GPU for battery efficiency. */
    LOW_POWER,
    /** Prefer discrete GPU for maximum performance. */
    HIGH_PERFORMANCE
}

/**
 * Configuration for creating a GPU instance.
 *
 * @property preferredBackends Ordered list of backends to try.
 * @property label Optional debug label.
 */
data class GpuInstanceDescriptor(
    val preferredBackends: List<GpuBackend> = listOf(GpuBackend.WEBGPU),
    val label: String? = null
)

/**
 * Options for requesting a GPU adapter from an instance.
 *
 * @property powerPreference Performance vs battery preference.
 * @property compatibleSurface Optional surface the adapter must support.
 * @property forceFallbackAdapter If true, request a software/fallback adapter.
 * @property label Optional debug label.
 */
data class GpuRequestAdapterOptions(
    val powerPreference: GpuPowerPreference = GpuPowerPreference.HIGH_PERFORMANCE,
    val compatibleSurface: GpuSurface? = null,
    val forceFallbackAdapter: Boolean = false,
    val label: String? = null
)

/**
 * Hardware metadata exposed by a GPU adapter.
 *
 * @property name Human-readable device name (e.g., "NVIDIA GeForce RTX 4090").
 * @property vendor GPU vendor identifier.
 * @property architecture GPU architecture name.
 * @property driverVersion Driver version string.
 */
data class GpuAdapterInfo(
    val name: String,
    val vendor: String? = null,
    val architecture: String? = null,
    val driverVersion: String? = null
)

/**
 * Configuration for creating a logical GPU device.
 *
 * @property requiredFeatures Set of feature names the device must support.
 * @property requiredLimits Map of limit names to minimum required values.
 * @property label Optional debug label.
 */
data class GpuDeviceDescriptor(
    val requiredFeatures: Set<String> = emptySet(),
    val requiredLimits: Map<String, Long> = emptyMap(),
    val label: String? = null
)

/**
 * Creates a platform-specific GPU instance.
 *
 * This is the entry point for GPU initialization. The instance is used to
 * enumerate and request adapters.
 *
 * @param descriptor Configuration for instance creation.
 * @return A new GPU instance.
 */
expect suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor = GpuInstanceDescriptor()): GpuInstance

/**
 * Top-level GPU context for adapter enumeration.
 *
 * Represents the connection to the GPU subsystem. Use [requestAdapter] to obtain
 * a handle to physical GPU hardware.
 */
expect class GpuInstance internal constructor(
    descriptor: GpuInstanceDescriptor
) {
    val descriptor: GpuInstanceDescriptor
    suspend fun requestAdapter(options: GpuRequestAdapterOptions = GpuRequestAdapterOptions()): GpuAdapter
    fun dispose()
}

/**
 * Handle to a physical GPU or graphics adapter.
 *
 * An adapter represents a specific piece of GPU hardware. Use [requestDevice]
 * to create a logical device for issuing commands.
 */
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

/**
 * Logical GPU device for resource creation and command submission.
 *
 * A device is the primary interface for creating buffers, textures, pipelines,
 * and other GPU resources. Commands are submitted via [queue].
 */
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
    fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout
    fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup
    fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor? = null): GpuCommandEncoder
    fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule
    fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline
    fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline
    fun destroy()
}

/**
 * Command submission queue for a GPU device.
 *
 * All GPU work is submitted through the queue. Commands are executed in
 * submission order with respect to other submissions.
 */
expect class GpuQueue internal constructor(
    label: String?
) {
    val label: String?
    fun submit(commandBuffers: List<GpuCommandBuffer>)
}

/**
 * Presentation surface for rendering to a window or canvas.
 *
 * A surface represents the platform's drawable area. Configure it with a device
 * and format, then acquire frames for rendering.
 */
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

/**
 * A frame acquired from a surface for rendering.
 *
 * @property texture The texture backing the frame.
 * @property view A view into the frame texture for use as a render target.
 */
data class GpuSurfaceFrame(
    val texture: GpuTexture,
    val view: GpuTextureView
)

/**
 * Pre-initializes the GPU context from a render surface.
 * 
 * This must be called BEFORE creating a GpuInstance or requesting an adapter
 * on platforms that use wgpu4k-toolkit (JVM, Android). On JS/Browser, this
 * is a no-op as the browser's WebGPU handles context creation differently.
 *
 * Usage:
 * ```kotlin
 * val surface = SurfaceFactory.create(window)
 * initializeGpuContext(surface)  // Pre-initialize wgpu4k context
 * val instance = createGpuInstance()
 * val adapter = instance.requestAdapter()
 * ```
 *
 * @param surface The render surface to initialize the GPU context from.
 */
expect suspend fun initializeGpuContext(surface: RenderSurface)

expect fun GpuSurface.attachRenderSurface(surface: RenderSurface)

expect fun GpuBindGroupLayout.unwrapHandle(): Any?

expect fun GpuBindGroup.unwrapHandle(): Any?
