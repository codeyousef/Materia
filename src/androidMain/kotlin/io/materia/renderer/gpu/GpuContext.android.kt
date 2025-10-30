package io.materia.renderer.gpu

import android.os.Build
import io.materia.BuildConfig

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
private const val VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT = 0x00000020

private const val VK_FILTER_NEAREST = 0
private const val VK_FILTER_LINEAR = 1

private const val VK_IMAGE_VIEW_TYPE_2D = 1
private const val VK_FORMAT_UNDEFINED = -1

private const val DEFAULT_QUEUE_FAMILY_INDEX = 0

private val DEFAULT_GPU_LIMITS = GpuLimits(
    maxTextureDimension1D = 8192,
    maxTextureDimension2D = 8192,
    maxTextureDimension3D = 2048,
    maxTextureArrayLayers = 256,
    maxBindGroups = 4,
    maxUniformBuffersPerStage = 12,
    maxStorageBuffersPerStage = 8,
    maxBufferSize = 256L * 1024L * 1024L
)

private object VulkanBridgeProxy {
    private val clazz = runCatching { Class.forName("io.materia.gpu.bridge.VulkanBridge") }
        .getOrElse { cause ->
            throw IllegalStateException(
                "Android Vulkan bridge not available. Ensure :materia-gpu module is on the classpath.",
                cause
            )
        }
    private val instance = clazz.getDeclaredField("INSTANCE").get(null)

    private val vkInitMethod = clazz.getDeclaredMethod(
        "vkInit",
        String::class.java,
        java.lang.Boolean.TYPE
    )
    private val vkCreateDeviceMethod = clazz.getDeclaredMethod(
        "vkCreateDevice",
        java.lang.Long.TYPE
    )
    private val vkCreateBufferMethod = clazz.getDeclaredMethod(
        "vkCreateBuffer",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE
    )
    private val vkWriteBufferMethod = clazz.getDeclaredMethod(
        "vkWriteBuffer",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        ByteArray::class.java,
        java.lang.Integer.TYPE
    )
    private val vkCreateCommandEncoderMethod = clazz.getDeclaredMethod(
        "vkCreateCommandEncoder",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE
    )
    private val vkCommandEncoderFinishMethod = clazz.getDeclaredMethod(
        "vkCommandEncoderFinish",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE
    )
    private val vkDestroyCommandEncoderMethod = clazz.getDeclaredMethod(
        "vkDestroyCommandEncoder",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE
    )
    private val vkCreateTextureMethod = clazz.getDeclaredMethod(
        "vkCreateTexture",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE
    )
    private val vkCreateTextureViewMethod = clazz.getDeclaredMethod(
        "vkCreateTextureView",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE
    )
    private val vkCreateSamplerMethod = clazz.getDeclaredMethod(
        "vkCreateSampler",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Integer.TYPE,
        java.lang.Integer.TYPE
    )
    private val vkCreateBindGroupLayoutMethod = clazz.getDeclaredMethod(
        "vkCreateBindGroupLayout",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        IntArray::class.java,
        IntArray::class.java,
        IntArray::class.java
    )
    private val vkCreateBindGroupMethod = clazz.getDeclaredMethod(
        "vkCreateBindGroup",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        IntArray::class.java,
        LongArray::class.java,
        LongArray::class.java,
        LongArray::class.java,
        LongArray::class.java,
        LongArray::class.java
    )
    private val vkCreatePipelineLayoutMethod = clazz.getDeclaredMethod(
        "vkCreatePipelineLayout",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        LongArray::class.java
    )
    private val vkQueueSubmitMethod = clazz.getDeclaredMethod(
        "vkQueueSubmit",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Boolean.TYPE,
        java.lang.Integer.TYPE
    )
    private val vkDestroyCommandBufferMethod = clazz.getDeclaredMethod(
        "vkDestroyCommandBuffer",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE,
        java.lang.Long.TYPE
    )
    private val vkDestroyDeviceMethod = clazz.getDeclaredMethod(
        "vkDestroyDevice",
        java.lang.Long.TYPE,
        java.lang.Long.TYPE
    )

    private fun invokeLong(method: java.lang.reflect.Method, vararg args: Any?): Long =
        (method.invoke(instance, *args) as Number).toLong()

    fun vkInit(appName: String, enableValidation: Boolean): Long =
        invokeLong(vkInitMethod, appName, enableValidation)

    fun vkCreateDevice(instanceId: Long): Long =
        invokeLong(vkCreateDeviceMethod, instanceId)

    fun vkCreateBuffer(
        instanceId: Long,
        deviceId: Long,
        size: Long,
        usage: Int,
        memoryProperties: Int
    ): Long = invokeLong(
        vkCreateBufferMethod,
        instanceId,
        deviceId,
        size,
        usage,
        memoryProperties
    )

    fun vkWriteBuffer(
        instanceId: Long,
        deviceId: Long,
        bufferId: Long,
        data: ByteArray,
        offset: Int
    ) {
        vkWriteBufferMethod.invoke(instance, instanceId, deviceId, bufferId, data, offset)
    }

    fun vkCreateCommandEncoder(instanceId: Long, deviceId: Long): Long =
        invokeLong(vkCreateCommandEncoderMethod, instanceId, deviceId)

    fun vkCommandEncoderFinish(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long
    ): Long = invokeLong(vkCommandEncoderFinishMethod, instanceId, deviceId, encoderId)

    fun vkDestroyCommandEncoder(
        instanceId: Long,
        deviceId: Long,
        encoderId: Long
    ) {
        vkDestroyCommandEncoderMethod.invoke(instance, instanceId, deviceId, encoderId)
    }

    fun vkCreateTexture(
        instanceId: Long,
        deviceId: Long,
        format: Int,
        width: Int,
        height: Int,
        usageFlags: Int
    ): Long = invokeLong(
        vkCreateTextureMethod,
        instanceId,
        deviceId,
        format,
        width,
        height,
        usageFlags
    )

    fun vkCreateTextureView(
        instanceId: Long,
        deviceId: Long,
        textureId: Long,
        viewType: Int,
        overrideFormat: Int
    ): Long = invokeLong(
        vkCreateTextureViewMethod,
        instanceId,
        deviceId,
        textureId,
        viewType,
        overrideFormat
    )

    fun vkCreateSampler(
        instanceId: Long,
        deviceId: Long,
        minFilter: Int,
        magFilter: Int
    ): Long = invokeLong(
        vkCreateSamplerMethod,
        instanceId,
        deviceId,
        minFilter,
        magFilter
    )

    fun vkCreateBindGroupLayout(
        instanceId: Long,
        deviceId: Long,
        bindings: IntArray,
        resourceTypes: IntArray,
        visibility: IntArray
    ): Long = invokeLong(
        vkCreateBindGroupLayoutMethod,
        instanceId,
        deviceId,
        bindings,
        resourceTypes,
        visibility
    )

    fun vkCreateBindGroup(
        instanceId: Long,
        deviceId: Long,
        layoutId: Long,
        bindings: IntArray,
        buffers: LongArray,
        offsets: LongArray,
        sizes: LongArray,
        textureViews: LongArray,
        samplers: LongArray
    ): Long = invokeLong(
        vkCreateBindGroupMethod,
        instanceId,
        deviceId,
        layoutId,
        bindings,
        buffers,
        offsets,
        sizes,
        textureViews,
        samplers
    )

    fun vkCreatePipelineLayout(
        instanceId: Long,
        deviceId: Long,
        layouts: LongArray
    ): Long = invokeLong(
        vkCreatePipelineLayoutMethod,
        instanceId,
        deviceId,
        layouts
    )

    fun vkQueueSubmit(
        instanceId: Long,
        deviceId: Long,
        commandBufferId: Long,
        hasSwapchain: Boolean,
        imageIndex: Int
    ) {
        vkQueueSubmitMethod.invoke(
            instance,
            instanceId,
            deviceId,
            commandBufferId,
            hasSwapchain,
            imageIndex
        )
    }

    fun vkDestroyCommandBuffer(
        instanceId: Long,
        deviceId: Long,
        commandBufferId: Long
    ) {
        vkDestroyCommandBufferMethod.invoke(instance, instanceId, deviceId, commandBufferId)
    }

    fun vkDestroyDevice(instanceId: Long, deviceId: Long) {
        vkDestroyDeviceMethod.invoke(instance, instanceId, deviceId)
    }
}

private data class TextureFormatInfo(
    val nativeFormat: Int,
    val isDepth: Boolean
)

private fun String.normaliseFormat(): String = lowercase().replace("-", "")

private fun String.toTextureFormatInfo(): TextureFormatInfo = when (normaliseFormat()) {
    "rgba8unorm", "rgba8" -> TextureFormatInfo(nativeFormat = 0, isDepth = false)
    "bgra8unorm", "bgra8" -> TextureFormatInfo(nativeFormat = 1, isDepth = false)
    "rgba16float", "rgba16f" -> TextureFormatInfo(nativeFormat = 2, isDepth = false)
    "depth24plus", "depth24plusstencil8", "depth24plusstencil" -> TextureFormatInfo(
        nativeFormat = 3,
        isDepth = true
    )

    else -> error("Unsupported texture format '$this' for Android Vulkan backend.")
}

private fun Int.hasBufferUsage(usage: GpuBufferUsage): Boolean = (this and usage.bits) != 0

private fun Int.toNativeBufferUsage(): Int {
    var flags = 0
    if (hasBufferUsage(GpuBufferUsage.COPY_SRC)) flags = flags or VK_BUFFER_USAGE_TRANSFER_SRC_BIT
    if (hasBufferUsage(GpuBufferUsage.COPY_DST)) flags = flags or VK_BUFFER_USAGE_TRANSFER_DST_BIT
    if (hasBufferUsage(GpuBufferUsage.UNIFORM)) flags = flags or VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT
    if (hasBufferUsage(GpuBufferUsage.STORAGE)) flags = flags or VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
    if (hasBufferUsage(GpuBufferUsage.INDEX)) flags = flags or VK_BUFFER_USAGE_INDEX_BUFFER_BIT
    if (hasBufferUsage(GpuBufferUsage.VERTEX)) flags = flags or VK_BUFFER_USAGE_VERTEX_BUFFER_BIT
    if (hasBufferUsage(GpuBufferUsage.INDIRECT)) flags =
        flags or VK_BUFFER_USAGE_INDIRECT_BUFFER_BIT
    return flags
}

private fun Int.hasTextureUsage(usage: GpuTextureUsage): Boolean = (this and usage.bits) != 0

private fun GpuTextureDescriptor.nativeUsageFlags(formatInfo: TextureFormatInfo): Int {
    var flags = 0
    if (usage.hasTextureUsage(GpuTextureUsage.COPY_SRC)) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
    if (usage.hasTextureUsage(GpuTextureUsage.COPY_DST)) flags =
        flags or VK_IMAGE_USAGE_TRANSFER_DST_BIT
    if (usage.hasTextureUsage(GpuTextureUsage.TEXTURE_BINDING)) flags =
        flags or VK_IMAGE_USAGE_SAMPLED_BIT
    if (usage.hasTextureUsage(GpuTextureUsage.STORAGE_BINDING)) flags =
        flags or VK_IMAGE_USAGE_STORAGE_BIT
    if (usage.hasTextureUsage(GpuTextureUsage.RENDER_ATTACHMENT)) {
        flags = flags or if (formatInfo.isDepth) {
            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
        } else {
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        }
    }
    require(flags != 0) { "Texture usage mask must include at least one usage flag." }
    return flags
}

private fun GpuSamplerFilter.toNativeFilter(): Int = when (this) {
    GpuSamplerFilter.NEAREST -> VK_FILTER_NEAREST
    GpuSamplerFilter.LINEAR -> VK_FILTER_LINEAR
}

private fun GpuBindGroupLayoutEntry.toNativeResourceType(): Int = when {
    buffer != null -> when (buffer.type) {
        GpuBufferBindingType.UNIFORM -> 0
        GpuBufferBindingType.STORAGE,
        GpuBufferBindingType.READ_ONLY_STORAGE -> 1
    }

    texture != null -> 2
    sampler != null -> 3
    else -> error("Bind group layout entry must declare a buffer, texture, or sampler resource.")
}

private fun GpuTextureDescriptor.ensureSupported() {
    require(dimension == GpuTextureDimension.D2) { "Android Vulkan backend currently supports 2D textures only." }
    require(depthOrArrayLayers == 1) { "Texture arrays are not yet supported on Android Vulkan backend." }
    require(sampleCount == 1) { "Multisampled textures are not yet supported on Android Vulkan backend." }
}

private fun GpuTextureViewDimension?.toNativeViewType(baseDimension: GpuTextureDimension): Int {
    val resolved = this ?: when (baseDimension) {
        GpuTextureDimension.D1 -> error("1D textures are not supported on Android Vulkan backend.")
        GpuTextureDimension.D2 -> GpuTextureViewDimension.D2
        GpuTextureDimension.D3 -> error("3D textures are not supported on Android Vulkan backend.")
    }
    return when (resolved) {
        GpuTextureViewDimension.D2 -> VK_IMAGE_VIEW_TYPE_2D
        GpuTextureViewDimension.D2_ARRAY -> error("Texture array views are not supported on Android Vulkan backend.")
        GpuTextureViewDimension.D1,
        GpuTextureViewDimension.CUBE,
        GpuTextureViewDimension.CUBE_ARRAY,
        GpuTextureViewDimension.D3 -> error("Texture view dimension $resolved is not supported on Android Vulkan backend.")
    }
}

actual class GpuDevice internal constructor(
    actual val backend: GpuBackend,
    actual val info: GpuDeviceInfo,
    actual val limits: GpuLimits,
    internal val instanceHandle: Long,
    internal val deviceHandle: Long,
    internal val queueFamilyIndex: Int = DEFAULT_QUEUE_FAMILY_INDEX
) {
    private var destroyed = false

    private fun ensureActive() {
        check(!destroyed) { "GpuDevice has been destroyed." }
    }

    actual fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray?): GpuBuffer {
        ensureActive()
        val usageFlags = descriptor.usage.toNativeBufferUsage()
        val handle = VulkanBridgeProxy.vkCreateBuffer(
            instanceHandle,
            deviceHandle,
            descriptor.size,
            usageFlags,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
        )
        if (data != null && data.isNotEmpty()) {
            VulkanBridgeProxy.vkWriteBuffer(instanceHandle, deviceHandle, handle, data, 0)
        }
        return GpuBuffer(this, handle, descriptor.size, descriptor.usage)
    }

    actual fun createCommandEncoder(label: String?): GpuCommandEncoder {
        ensureActive()
        val handle = VulkanBridgeProxy.vkCreateCommandEncoder(instanceHandle, deviceHandle)
        return GpuCommandEncoder(this, handle, label)
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        ensureActive()
        descriptor.ensureSupported()
        val formatInfo = descriptor.format.toTextureFormatInfo()
        val usageFlags = descriptor.nativeUsageFlags(formatInfo)
        val textureHandle = VulkanBridgeProxy.vkCreateTexture(
            instanceHandle,
            deviceHandle,
            formatInfo.nativeFormat,
            descriptor.width,
            descriptor.height,
            usageFlags
        )
        return GpuTexture(
            device = this,
            handle = textureHandle,
            width = descriptor.width,
            height = descriptor.height,
            depth = descriptor.depthOrArrayLayers,
            format = descriptor.format,
            mipLevelCount = descriptor.mipLevelCount,
            dimension = descriptor.dimension
        )
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        ensureActive()
        val handle = VulkanBridgeProxy.vkCreateSampler(
            instanceHandle,
            deviceHandle,
            descriptor.minFilter.toNativeFilter(),
            descriptor.magFilter.toNativeFilter()
        )
        return GpuSampler(this, handle, descriptor)
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        ensureActive()
        val entries = descriptor.entries
        require(entries.isNotEmpty()) { "Bind group layout must declare at least one entry." }

        val bindings = IntArray(entries.size)
        val resourceTypes = IntArray(entries.size)
        val visibility = IntArray(entries.size)

        entries.forEachIndexed { index, entry ->
            bindings[index] = entry.binding
            resourceTypes[index] = entry.toNativeResourceType()
            visibility[index] = entry.visibility
        }

        val handle = VulkanBridgeProxy.vkCreateBindGroupLayout(
            instanceHandle,
            deviceHandle,
            bindings,
            resourceTypes,
            visibility
        )
        return GpuBindGroupLayout(this, handle, descriptor)
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        ensureActive()
        val entries = descriptor.entries
        require(entries.isNotEmpty()) { "Bind group must declare at least one resource." }

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
                    val buffer = resource.buffer as? GpuBuffer
                        ?: error("GpuBindingResource.Buffer requires an Android GPU buffer instance.")
                    buffers[index] = buffer.handle
                    offsets[index] = resource.offset
                    sizes[index] = resource.size ?: (buffer.size - resource.offset)
                }

                is GpuBindingResource.Texture -> {
                    val view = resource.textureView as? GpuTextureView
                        ?: error("GpuBindingResource.Texture requires an Android GPU texture view.")
                    textureViews[index] = view.handle
                }

                is GpuBindingResource.Sampler -> {
                    val sampler = resource.sampler as? GpuSampler
                        ?: error("GpuBindingResource.Sampler requires an Android GPU sampler.")
                    samplers[index] = sampler.handle
                }
            }
        }

        val layoutHandle = (descriptor.layout as? GpuBindGroupLayout)?.handle
            ?: error("Bind group descriptor references an incompatible layout instance.")

        val handle = VulkanBridgeProxy.vkCreateBindGroup(
            instanceHandle,
            deviceHandle,
            layoutHandle,
            bindings,
            buffers,
            offsets,
            sizes,
            textureViews,
            samplers
        )
        return GpuBindGroup(descriptor.layout, descriptor, handle)
    }

    actual fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout {
        ensureActive()
        val layoutHandles = descriptor.bindGroupLayouts.map { layout ->
            (layout as? GpuBindGroupLayout)?.handle
                ?: error("Pipeline layout descriptor references an incompatible bind group layout.")
        }
        val handle = VulkanBridgeProxy.vkCreatePipelineLayout(
            instanceHandle,
            deviceHandle,
            layoutHandles.toLongArray()
        )
        return GpuPipelineLayout(this, handle, descriptor)
    }

    internal fun destroy() {
        if (destroyed) return
        VulkanBridgeProxy.vkDestroyDevice(instanceHandle, deviceHandle)
        destroyed = true
    }
}

actual class GpuQueue internal constructor(
    actual val backend: GpuBackend,
    internal val device: GpuDevice
) {
    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        val instance = device.instanceHandle
        val deviceHandle = device.deviceHandle

        commandBuffers.forEach { buffer ->
            val handle = buffer.takeHandle()
            try {
                VulkanBridgeProxy.vkQueueSubmit(
                    instance,
                    deviceHandle,
                    handle,
                    buffer.hasSwapchain,
                    buffer.swapchainImageIndex
                )
            } finally {
                VulkanBridgeProxy.vkDestroyCommandBuffer(instance, deviceHandle, handle)
            }
        }
    }
}

actual class GpuBuffer internal constructor(
    internal val device: GpuDevice,
    internal val handle: Long,
    actual val size: Long,
    actual val usage: Int
)

actual class GpuCommandEncoder internal constructor(
    internal val device: GpuDevice,
    internal var handle: Long,
    internal val label: String?
) {
    private var finished = false

    actual fun finish(): GpuCommandBuffer {
        check(!finished) { "Command encoder has already been finished." }
        val commandHandle = VulkanBridgeProxy.vkCommandEncoderFinish(
            device.instanceHandle,
            device.deviceHandle,
            handle
        )

        VulkanBridgeProxy.vkDestroyCommandEncoder(
            device.instanceHandle,
            device.deviceHandle,
            handle
        )
        finished = true
        handle = 0L

        return GpuCommandBuffer(
            device = device,
            handle = commandHandle,
            label = label,
            hasSwapchain = false,
            swapchainImageIndex = -1
        )
    }
}

actual class GpuCommandBuffer internal constructor(
    internal val device: GpuDevice,
    internal var handle: Long,
    internal val label: String?,
    internal val hasSwapchain: Boolean,
    internal val swapchainImageIndex: Int
) {
    internal fun takeHandle(): Long {
        val current = handle
        require(current != 0L) { "Command buffer handle is no longer valid." }
        handle = 0L
        return current
    }
}

actual class GpuTexture internal constructor(
    internal val device: GpuDevice,
    internal val handle: Long,
    actual val width: Int,
    actual val height: Int,
    actual val depth: Int,
    actual val format: String,
    actual val mipLevelCount: Int,
    private val dimension: GpuTextureDimension
) {
    private var destroyed = false

    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        check(!destroyed) { "Cannot create view from a destroyed texture." }
        val viewType = descriptor?.dimension.toNativeViewType(dimension)
        val overrideFormat =
            descriptor?.format?.let { it.toTextureFormatInfo().nativeFormat } ?: VK_FORMAT_UNDEFINED

        val handle = VulkanBridgeProxy.vkCreateTextureView(
            device.instanceHandle,
            device.deviceHandle,
            this.handle,
            viewType,
            overrideFormat
        )
        return GpuTextureView(device, this, handle, descriptor)
    }

    actual fun destroy() {
        destroyed = true
        // Native resources are released when the device is destroyed.
    }
}

actual class GpuSampler internal constructor(
    internal val device: GpuDevice,
    internal val handle: Long,
    internal val descriptor: GpuSamplerDescriptor
)

actual class GpuBindGroupLayout internal constructor(
    internal val device: GpuDevice,
    internal val handle: Long,
    internal val descriptor: GpuBindGroupLayoutDescriptor
)

actual class GpuBindGroup internal constructor(
    internal val layout: GpuBindGroupLayout,
    internal val descriptor: GpuBindGroupDescriptor,
    internal val handle: Long
)

actual class GpuPipelineLayout internal constructor(
    internal val device: GpuDevice,
    internal val handle: Long,
    internal val descriptor: GpuPipelineLayoutDescriptor
)

actual class GpuTextureView internal constructor(
    internal val device: GpuDevice,
    internal val texture: GpuTexture,
    internal val handle: Long,
    internal val descriptor: GpuTextureViewDescriptor?
)

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext {
        val backend = when (config.preferredBackend) {
            GpuBackend.WEBGPU -> GpuBackend.VULKAN
            else -> config.preferredBackend
        }

        val instanceHandle = VulkanBridgeProxy.vkInit(
            config.label ?: "Materia",
            BuildConfig.VK_ENABLE_VALIDATION
        )
        val deviceHandle = VulkanBridgeProxy.vkCreateDevice(instanceHandle)

        val info = GpuDeviceInfo(
            name = Build.MODEL ?: "Android Device",
            vendor = Build.MANUFACTURER,
            driverVersion = Build.VERSION.RELEASE,
            architecture = Build.HARDWARE
        )

        val device = GpuDevice(
            backend = backend,
            info = info,
            limits = DEFAULT_GPU_LIMITS,
            instanceHandle = instanceHandle,
            deviceHandle = deviceHandle
        )
        val queue = GpuQueue(
            backend = backend,
            device = device
        )
        return GpuContext(device, queue)
    }
}

actual fun GpuDevice.unwrapHandle(): Any? = deviceHandle

actual fun GpuDevice.unwrapPhysicalHandle(): Any? = null

actual fun GpuQueue.unwrapHandle(): Any? = null

actual fun GpuBuffer.unwrapHandle(): Any? = handle

actual fun GpuCommandEncoder.unwrapHandle(): Any? = handle.takeIf { it != 0L }

actual fun GpuCommandBuffer.unwrapHandle(): Any? = handle.takeIf { it != 0L }

actual fun GpuTexture.unwrapHandle(): Any? = handle

actual fun GpuSampler.unwrapHandle(): Any? = handle

actual fun GpuDevice.unwrapInstance(): Any? = instanceHandle

actual fun GpuDevice.unwrapDescriptorPool(): Any? = null

actual fun GpuDevice.queueFamilyIndex(): Int = queueFamilyIndex

actual fun GpuDevice.commandPoolHandle(): Long = 0L

actual fun GpuQueue.queueFamilyIndex(): Int = device.queueFamilyIndex

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle

actual fun GpuBindGroup.unwrapHandle(): Any? = handle

actual fun GpuPipelineLayout.unwrapHandle(): Any? = handle

actual fun GpuTextureView.unwrapHandle(): Any? = handle
