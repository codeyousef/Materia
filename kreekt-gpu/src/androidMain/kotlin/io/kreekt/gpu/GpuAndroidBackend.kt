package io.kreekt.gpu

import android.content.Context
import android.content.res.AssetManager
import io.kreekt.gpu.bridge.VulkanBridge
import io.kreekt.renderer.RenderSurface

private const val VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT = 0x00000002
private const val VK_MEMORY_PROPERTY_HOST_COHERENT_BIT = 0x00000004

private const val VK_BUFFER_USAGE_TRANSFER_SRC_BIT = 0x00000001
private const val VK_BUFFER_USAGE_TRANSFER_DST_BIT = 0x00000002
private const val VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT = 0x00000010
private const val VK_BUFFER_USAGE_STORAGE_BUFFER_BIT = 0x00000020
private const val VK_BUFFER_USAGE_INDEX_BUFFER_BIT = 0x00000040
private const val VK_BUFFER_USAGE_VERTEX_BUFFER_BIT = 0x00000080
private const val VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT = 0x00000100

private const val VK_IMAGE_USAGE_TRANSFER_SRC_BIT = 0x00000001
private const val VK_IMAGE_USAGE_TRANSFER_DST_BIT = 0x00000002
private const val VK_IMAGE_USAGE_SAMPLED_BIT = 0x00000004
private const val VK_IMAGE_USAGE_STORAGE_BIT = 0x00000008
private const val VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT = 0x00000010

private const val VK_FILTER_NEAREST = 0
private const val VK_FILTER_LINEAR = 1
private const val VK_IMAGE_VIEW_TYPE_2D = 1

private fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("Android Vulkan backend not yet implemented ($feature)")

/**
 * Utility object so Kotlin actuals can load SPIR-V assets on Android.
 */
object AndroidVulkanAssets {
    @Volatile
    private var assetManager: AssetManager? = null

    fun initialise(context: Context) {
        assetManager = context.assets
    }

    fun loadShader(label: String): ByteArray {
        val manager = assetManager ?: error("AndroidVulkanAssets not initialised")
        val path = "shaders/${label}.main.spv"
        return manager.open(path).use { it.readBytes() }
    }
}

private fun GpuBufferUsageFlags.toNativeUsage(): Int {
    var flags = 0
    if (this and GpuBufferUsage.COPY_SRC.mask != 0) flags =
        flags or VK_BUFFER_USAGE_TRANSFER_SRC_BIT
    if (this and GpuBufferUsage.COPY_DST.mask != 0) flags =
        flags or VK_BUFFER_USAGE_TRANSFER_DST_BIT
    if (this and GpuBufferUsage.UNIFORM.mask != 0) flags =
        flags or VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
    if (this and GpuBufferUsage.STORAGE.mask != 0) flags =
        flags or VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
    if (this and GpuBufferUsage.INDEX.mask != 0) flags = flags or VK_BUFFER_USAGE_INDEX_BUFFER_BIT
    if (this and GpuBufferUsage.VERTEX.mask != 0) flags = flags or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
    if (this and GpuBufferUsage.INDIRECT.mask != 0) flags =
        flags or VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
    return flags
}

private fun GpuTextureUsageFlags.toNativeUsage(): Int {
    var flags = 0
    if (this and GpuTextureUsage.COPY_SRC.mask != 0) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
    if (this and GpuTextureUsage.COPY_DST.mask != 0) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_DST_BIT
    if (this and GpuTextureUsage.TEXTURE_BINDING.mask != 0) flags =
        flags or VK_IMAGE_USAGE_SAMPLED_BIT
    if (this and GpuTextureUsage.STORAGE_BINDING.mask != 0) flags =
        flags or VK_IMAGE_USAGE_STORAGE_BIT
    if (this and GpuTextureUsage.RENDER_ATTACHMENT.mask != 0) flags =
        flags or VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
    return flags
}

private fun GpuTextureFormat.toNativeFormat(): Int = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> 0
    GpuTextureFormat.BGRA8_UNORM -> 1
    GpuTextureFormat.RGBA16_FLOAT -> 2
    GpuTextureFormat.DEPTH24_PLUS -> 3
}

private fun GpuFilterMode.toNative(): Int = when (this) {
    GpuFilterMode.NEAREST -> VK_FILTER_NEAREST
    GpuFilterMode.LINEAR -> VK_FILTER_LINEAR
}

private fun Set<GpuShaderStage>.toVisibilityMask(): Int = fold(0) { acc, stage ->
    acc or when (stage) {
        GpuShaderStage.VERTEX -> 0x1
        GpuShaderStage.FRAGMENT -> 0x2
        GpuShaderStage.COMPUTE -> 0x4
    }
}

private fun GpuBindingResourceType.toNativeDescriptorType(): Int = when (this) {
    GpuBindingResourceType.UNIFORM_BUFFER -> 0
    GpuBindingResourceType.STORAGE_BUFFER -> 1
    GpuBindingResourceType.TEXTURE -> 2
    GpuBindingResourceType.SAMPLER -> 3
}

private fun GpuVertexFormat.toNative(): Int = when (this) {
    GpuVertexFormat.FLOAT32 -> 0
    GpuVertexFormat.FLOAT32x2 -> 1
    GpuVertexFormat.FLOAT32x3 -> 2
    GpuVertexFormat.FLOAT32x4 -> 3
    else -> error("Vertex format $this not supported on Android Vulkan")
}

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance =
    GpuInstance(descriptor)

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    internal val handle: Long = VulkanBridge.vkInit(descriptor.label ?: "KreeKt", false)
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter {
        ensureActive()
        val info = GpuAdapterInfo(
            name = android.os.Build.MODEL ?: "Android Device",
            vendor = android.os.Build.MANUFACTURER,
            architecture = android.os.Build.HARDWARE,
            driverVersion = android.os.Build.VERSION.RELEASE
        )
        return GpuAdapter(GpuBackend.VULKAN, options, info).also { adapter ->
            adapter.instanceHandle = handle
            adapter.ownerInstance = this
        }
    }

    actual fun dispose() {
        if (!disposed) {
            VulkanBridge.vkDestroyInstance(handle)
            disposed = true
        }
    }

    private fun ensureActive() {
        check(!disposed) { "GpuInstance has been disposed." }
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    internal lateinit var ownerInstance: GpuInstance
    internal var instanceHandle: Long = 0L

    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice {
        val deviceHandle = VulkanBridge.vkCreateDevice(instanceHandle)
        return GpuDevice(this, descriptor).also { device ->
            device.instanceHandle = instanceHandle
            device.handle = deviceHandle
        }
    }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    internal var instanceHandle: Long = 0L
    internal var handle: Long = 0L

    actual val queue: GpuQueue = GpuQueue(descriptor.label).also { it.ownerDevice = this }

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        val usage = descriptor.usage.toNativeUsage()
        val bufferHandle = VulkanBridge.vkCreateBuffer(
            instanceHandle,
            handle,
            descriptor.size,
            usage,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        return GpuBuffer(this, descriptor).also { it.handle = bufferHandle }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val textureHandle = VulkanBridge.vkCreateTexture(
            instanceHandle,
            handle,
            descriptor.format.toNativeFormat(),
            descriptor.size.first,
            descriptor.size.second,
            descriptor.usage.toNativeUsage()
        )
        return GpuTexture(this, descriptor).also { it.handle = textureHandle }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val samplerHandle = VulkanBridge.vkCreateSampler(
            instanceHandle,
            handle,
            descriptor.minFilter.toNative(),
            descriptor.magFilter.toNative()
        )
        return GpuSampler(this, descriptor).also { it.handle = samplerHandle }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        val bindings = descriptor.entries.map { it.binding }.toIntArray()
        val resourceTypes =
            descriptor.entries.map { it.resourceType.toNativeDescriptorType() }.toIntArray()
        val visibility = descriptor.entries.map { it.visibility.toVisibilityMask() }.toIntArray()
        val layoutHandle = VulkanBridge.vkCreateBindGroupLayout(
            instanceHandle,
            handle,
            bindings,
            resourceTypes,
            visibility
        )
        return GpuBindGroupLayout(this, descriptor).also { it.handle = layoutHandle }
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        val entries = descriptor.entries
        val bindings = IntArray(entries.size)
        val buffers = LongArray(entries.size)
        val offsets = LongArray(entries.size)
        val sizes = LongArray(entries.size)
        val textureViews = LongArray(entries.size)
        val samplers = LongArray(entries.size)

        entries.forEachIndexed { index, entry ->
            bindings[index] = entry.binding
            when (val resource = entry.resource) {
                is GpuBindingResource.Buffer -> {
                    val buffer = resource.buffer as GpuBuffer
                    buffers[index] = buffer.handle
                    offsets[index] = resource.offset
                    sizes[index] = resource.size ?: buffer.descriptor.size
                }

                is GpuBindingResource.Texture -> {
                    val view = resource.textureView as GpuTextureView
                    textureViews[index] = view.handle
                }

                is GpuBindingResource.Sampler -> {
                    val samplerObject = resource.sampler as GpuSampler
                    samplers[index] = samplerObject.handle
                }
            }
        }

        val layoutHandle = (descriptor.layout as GpuBindGroupLayout).handle
        val bindGroupHandle = VulkanBridge.vkCreateBindGroup(
            instanceHandle,
            handle,
            layoutHandle,
            bindings,
            buffers,
            offsets,
            sizes,
            textureViews,
            samplers
        )
        return GpuBindGroup(descriptor.layout, descriptor).also { it.handle = bindGroupHandle }
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder =
        unsupported("createCommandEncoder")

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        val label = descriptor.label ?: error("Shader module label required on Android Vulkan")
        val spirv = AndroidVulkanAssets.loadShader(label)
        val handle = VulkanBridge.vkCreateShaderModule(instanceHandle, handle, spirv)
        return GpuShaderModule(this, descriptor).also { it.handle = handle }
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline =
        unsupported("createRenderPipeline")

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline =
        unsupported("createComputePipeline")

    actual fun destroy() {
        // Native destruction will be handled once resource wiring is complete.
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    internal lateinit var ownerDevice: GpuDevice

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) = unsupported("queue.submit")
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) =
        unsupported("surface.configure")

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat =
        GpuTextureFormat.BGRA8_UNORM

    actual fun acquireFrame(): GpuSurfaceFrame = unsupported("surface.acquireFrame")
    actual fun present(frame: GpuSurfaceFrame) = unsupported("surface.present")
    actual fun resize(width: Int, height: Int) {}
}

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle
actual fun GpuBindGroup.unwrapHandle(): Any? = handle

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    internal var handle: Long = 0L

    actual fun write(data: ByteArray, offset: Int) {
        val androidDevice = device as GpuDevice
        VulkanBridge.vkWriteBuffer(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            data,
            offset
        )
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val androidDevice = device as GpuDevice
        VulkanBridge.vkWriteBufferFloats(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            data,
            offset
        )
    }

    actual fun destroy() {}
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    internal var handle: Long = 0L

    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val androidDevice = device as GpuDevice
        val formatOverride = descriptor.format?.toNativeFormat() ?: -1
        val viewHandle = VulkanBridge.vkCreateTextureView(
            androidDevice.instanceHandle,
            androidDevice.handle,
            handle,
            VK_IMAGE_VIEW_TYPE_2D,
            formatOverride
        )
        return GpuTextureView(this, descriptor).also {
            it.handle = viewHandle
        }
    }

    actual fun destroy() {}
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
) {
    internal var handle: Long = 0L
    internal var imageIndex: Int = -1
}

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuPipelineLayout internal constructor()

actual class GpuCommandEncoder actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    actual fun finish(label: String?): GpuCommandBuffer = unsupported("commandEncoder.finish")
    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder =
        unsupported("commandEncoder.beginRenderPass")
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
)

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    actual fun setPipeline(pipeline: GpuRenderPipeline) = unsupported("renderPass.setPipeline")
    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) =
        unsupported("renderPass.setVertexBuffer")

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) =
        unsupported("renderPass.setIndexBuffer")

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) =
        unsupported("renderPass.setBindGroup")

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) =
        unsupported("renderPass.draw")

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) = unsupported("renderPass.drawIndexed")

    actual fun end() {}
}

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
) {
    internal var handle: Long = 0L
}

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
) {
    internal var handle: Long = 0L
    internal var pipelineLayoutHandle: Long = 0L
}

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
)

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext =
        unsupported("GpuDeviceFactory.requestContext")
}

actual fun GpuDevice.unwrapHandle(): Any? = handle
actual fun GpuDevice.unwrapPhysicalHandle(): Any? = null
actual fun GpuQueue.unwrapHandle(): Any? = null
actual fun GpuBuffer.unwrapHandle(): Any? = handle
actual fun GpuCommandEncoder.unwrapHandle(): Any? = null
actual fun GpuCommandBuffer.unwrapHandle(): Any? = null
actual fun GpuTexture.unwrapHandle(): Any? = handle
actual fun GpuSampler.unwrapHandle(): Any? = handle
actual fun GpuDevice.unwrapInstance(): Any? = instanceHandle
actual fun GpuDevice.unwrapDescriptorPool(): Any? = null
actual fun GpuDevice.queueFamilyIndex(): Int = 0
actual fun GpuDevice.commandPoolHandle(): Long = 0L
actual fun GpuQueue.queueFamilyIndex(): Int = 0
actual fun GpuPipelineLayout.unwrapHandle(): Any? = null
actual fun GpuTextureView.unwrapHandle(): Any? = handle
