package io.kreekt.gpu

import io.kreekt.renderer.RenderSurface
import io.kreekt.renderer.feature020.SwapchainImage
import io.kreekt.renderer.gpu.GpuBuffer as RendererGpuBuffer
import io.kreekt.renderer.gpu.GpuContext as RendererGpuContext
import io.kreekt.renderer.gpu.GpuDevice as RendererGpuDevice
import io.kreekt.renderer.gpu.GpuDeviceFactory as RendererGpuDeviceFactory
import io.kreekt.renderer.gpu.GpuQueue as RendererGpuQueue
import io.kreekt.renderer.gpu.GpuTexture as RendererGpuTexture
import io.kreekt.renderer.gpu.GpuTextureView as RendererGpuTextureView
import io.kreekt.renderer.gpu.GpuSampler as RendererGpuSampler
import io.kreekt.renderer.gpu.commandPoolHandle
import io.kreekt.renderer.gpu.unwrapDescriptorPool
import io.kreekt.renderer.gpu.unwrapHandle
import io.kreekt.renderer.gpu.unwrapInstance
import io.kreekt.renderer.gpu.unwrapPhysicalHandle
import io.kreekt.renderer.vulkan.VulkanSurface
import io.kreekt.renderer.vulkan.VulkanSwapchain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VK12.VK_FORMAT_D24_UNORM_S8_UINT
import org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
import org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR
import org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR
import org.lwjgl.vulkan.VK12.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
import org.lwjgl.vulkan.VK12.VK_IMAGE_TILING_OPTIMAL
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_SAMPLED_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_STORAGE_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_TRANSFER_DST_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VkAttachmentDescription
import org.lwjgl.vulkan.VkAttachmentReference
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkFenceCreateInfo
import org.lwjgl.vulkan.VkFramebufferCreateInfo
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkInstance
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkPresentInfoKHR
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkRect2D
import org.lwjgl.vulkan.VkRenderPassBeginInfo
import org.lwjgl.vulkan.VkRenderPassCreateInfo
import org.lwjgl.vulkan.VkSamplerCreateInfo
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.lwjgl.vulkan.VkSubmitInfo
import org.lwjgl.vulkan.VkSubpassDependency
import org.lwjgl.vulkan.VkSubpassDescription
import org.lwjgl.vulkan.VkViewport
import org.lwjgl.vulkan.VkVertexInputAttributeDescription
import org.lwjgl.vulkan.VkVertexInputBindingDescription
import org.lwjgl.vulkan.VkDescriptorBufferInfo
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance =
    withContext(Dispatchers.Default) {
        GpuInstance(descriptor)
    }

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter =
        ensureActive {
            val config = descriptor.toRendererConfig(options)
            val rendererContext = RendererGpuDeviceFactory.requestContext(config)
            val adapterInfo = rendererContext.device.info.toAdapterInfo()
            val adapter = GpuAdapter(
                backend = rendererContext.device.backend.toGpuBackend(),
                options = options,
                info = adapterInfo
            )
            adapter.attachContext(rendererContext)
            adapter
        }

    actual fun dispose() {
        disposed = true
    }

    private inline fun <T> ensureActive(block: () -> T): T {
        check(!disposed) { "GpuInstance has been disposed." }
        return block()
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    private lateinit var rendererContext: RendererGpuContext

    internal fun attachContext(context: RendererGpuContext) {
        rendererContext = context
    }

    internal fun context(): RendererGpuContext =
        if (::rendererContext.isInitialized) rendererContext
        else error("Renderer context not attached to adapter")

    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice =
        GpuDevice(this, descriptor).also { it.attachContext(context()) }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    private lateinit var rendererContext: RendererGpuContext
    private val renderPassCache = mutableMapOf<RenderPassKey, Long>()
    private val trackedPipelines = mutableSetOf<GpuRenderPipeline>()
    private val trackedBindGroupLayouts = mutableSetOf<GpuBindGroupLayout>()
    private val trackedBindGroups = mutableSetOf<GpuBindGroup>()

    internal val rendererDevice: RendererGpuDevice
        get() = rendererContext.device

    internal var commandPool: Long = VK_NULL_HANDLE
    internal var descriptorPoolHandle: Long = VK_NULL_HANDLE

    internal fun attachContext(context: RendererGpuContext) {
        rendererContext = context
        commandPool = context.device.commandPoolHandle()
        descriptorPoolHandle = context.device.unwrapDescriptorPool() as? Long ?: VK_NULL_HANDLE
        queue.attachQueue(context.queue, this)
    }

    actual val queue: GpuQueue = GpuQueue(descriptor.label)

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        val rendererDescriptor = descriptor.toRendererDescriptor()
        val rendererBuffer = rendererDevice.createBuffer(rendererDescriptor, null)
        return GpuBuffer(this, descriptor).apply { attach(rendererBuffer) }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val rendererDescriptor = descriptor.toRendererDescriptor()
        val rendererTexture = rendererDevice.createTexture(rendererDescriptor)
        return GpuTexture(this, descriptor).apply { attach(rendererTexture) }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val rendererDescriptor = descriptor.toRendererDescriptor()
        val rendererSampler = rendererDevice.createSampler(rendererDescriptor)
        return GpuSampler(this, descriptor).apply { attach(rendererSampler) }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout =
        GpuBindGroupLayout(this, descriptor).also { registerBindGroupLayout(it) }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup =
        GpuBindGroup(descriptor.layout, descriptor).also { registerBindGroup(it) }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder =
        GpuCommandEncoder(this, descriptor)

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule =
        GpuShaderModule(this, descriptor)

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline =
        GpuRenderPipeline(this, descriptor).also { registerPipeline(it) }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline =
        GpuComputePipeline(this, descriptor)

    actual fun destroy() {
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice
        val groupsSnapshot = trackedBindGroups.toList()
        groupsSnapshot.forEach { group ->
            group.destroy()
        }
        trackedBindGroups.clear()
        val layoutsSnapshot = trackedBindGroupLayouts.toList()
        layoutsSnapshot.forEach { layout ->
            layout.destroy()
        }
        trackedBindGroupLayouts.clear()
        val pipelinesSnapshot = trackedPipelines.toList()
        pipelinesSnapshot.forEach { pipeline ->
            pipeline.destroyInternal()
        }
        trackedPipelines.clear()
        if (deviceHandle != null) {
            renderPassCache.values.forEach { handle ->
                vkDestroyRenderPass(deviceHandle, handle, null)
            }
        }
        renderPassCache.clear()
        // Renderer context lifecycle is managed externally (RendererFactory) for device/queue.
    }

    internal fun mapAndCopy(buffer: RendererGpuBuffer, data: ByteArray, byteOffset: Long) {
        val handle = rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for buffer writes")
        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(1)
            val result = vkMapMemory(handle, buffer.memory, byteOffset, data.size.toLong(), 0, pointerBuffer)
            check(result == VK_SUCCESS) { "vkMapMemory failed with error code $result" }
            val mappedPtr = pointerBuffer[0]
            val target = MemoryUtil.memByteBuffer(mappedPtr, data.size)
            target.put(data)
            target.flip()
            vkUnmapMemory(handle, buffer.memory)
        }
    }

    internal fun destroyBuffer(buffer: RendererGpuBuffer) {
        val handle = rendererDevice.unwrapHandle() as? VkDevice
            ?: return
        vkDestroyBuffer(handle, buffer.buffer, null)
        vkFreeMemory(handle, buffer.memory, null)
    }

    internal fun allocateCommandBuffer(): VkCommandBuffer {
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for command buffer allocation")
        check(commandPool != VK_NULL_HANDLE) { "Command pool not initialised for device" }

        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)

            val pCommandBuffer = stack.mallocPointer(1)
            val result = vkAllocateCommandBuffers(deviceHandle, allocInfo, pCommandBuffer)
            check(result == VK_SUCCESS) { "vkAllocateCommandBuffers failed with error code $result" }

            return VkCommandBuffer(pCommandBuffer[0], deviceHandle)
        }
    }

    internal fun freeCommandBuffers(buffers: List<VkCommandBuffer>) {
        if (buffers.isEmpty()) return
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice ?: return
        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(buffers.size)
            buffers.forEach { buffer ->
                pointerBuffer.put(buffer.address())
            }
            pointerBuffer.flip()
            vkFreeCommandBuffers(deviceHandle, commandPool, pointerBuffer)
        }
    }

    internal fun obtainRenderPass(key: RenderPassKey): Long {
        renderPassCache[key]?.let { return it }
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for render pass creation")
        MemoryStack.stackPush().use { stack ->
            val attachmentDescriptions = VkAttachmentDescription.calloc(key.colorAttachments.size, stack)
            key.colorAttachments.forEachIndexed { index, attachment ->
                attachmentDescriptions[index]
                    .format(attachment.format.toVulkan())
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(attachment.loadOp.toVulkan())
                    .storeOp(attachment.storeOp.toVulkan())
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
            }

            val colorReferences = VkAttachmentReference.calloc(key.colorAttachments.size, stack)
            key.colorAttachments.forEachIndexed { index, _ ->
                colorReferences[index]
                    .attachment(index)
                    .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
            }

            val subpasses = VkSubpassDescription.calloc(1, stack)
            subpasses[0]
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(colorReferences.capacity())
                .pColorAttachments(colorReferences)

            val dependencies = VkSubpassDependency.calloc(1, stack)
            dependencies[0]
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)

            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachmentDescriptions)
                .pSubpasses(subpasses)
                .pDependencies(dependencies)

            val pRenderPass = stack.mallocLong(1)
            val result = vkCreateRenderPass(deviceHandle, renderPassInfo, null, pRenderPass)
            check(result == VK_SUCCESS) { "vkCreateRenderPass failed with error code $result" }
            val handle = pRenderPass[0]
            renderPassCache[key] = handle
            return handle
        }
    }

    internal fun registerPipeline(pipeline: GpuRenderPipeline) {
        trackedPipelines += pipeline
    }

    internal fun unregisterPipeline(pipeline: GpuRenderPipeline) {
        trackedPipelines -= pipeline
    }

    internal fun unregisterBindGroupLayout(layout: GpuBindGroupLayout) {
        trackedBindGroupLayouts -= layout
    }

    internal fun unregisterBindGroup(bindGroup: GpuBindGroup) {
        trackedBindGroups -= bindGroup
    }

    internal fun registerBindGroupLayout(layout: GpuBindGroupLayout) {
        trackedBindGroupLayouts += layout
    }

    internal fun registerBindGroup(bindGroup: GpuBindGroup) {
        trackedBindGroups += bindGroup
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    private lateinit var rendererQueue: RendererGpuQueue
    private var vkQueue: VkQueue? = null
    private var device: GpuDevice? = null

    internal fun attachQueue(queue: RendererGpuQueue, device: GpuDevice) {
        rendererQueue = queue
        vkQueue = queue.unwrapHandle() as? VkQueue
        this.device = device
    }

    internal val vkQueueHandle: VkQueue?
        get() = vkQueue

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        val queueHandle = vkQueue ?: error("Vulkan queue handle unavailable for submission")
        val deviceRef = device ?: error("GpuDevice reference unavailable for queue submission")
        val deviceHandle = deviceRef.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for queue submission")

        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(commandBuffers.size)
            val vkBuffers = commandBuffers.map { it.handle }
            vkBuffers.forEach { buffer ->
                pointerBuffer.put(buffer.address())
            }
            pointerBuffer.flip()

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(pointerBuffer)

            val fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)

            val pFence = stack.mallocLong(1)
            val createFenceResult = vkCreateFence(deviceHandle, fenceInfo, null, pFence)
            check(createFenceResult == VK_SUCCESS) { "vkCreateFence failed with error code $createFenceResult" }

            val fence = pFence[0]

            val result = vkQueueSubmit(queueHandle, submitInfo, fence)
            check(result == VK_SUCCESS) { "vkQueueSubmit failed with error code $result" }

            val waitResult = vkWaitForFences(deviceHandle, fence, true, Long.MAX_VALUE)
            check(waitResult == VK_SUCCESS) { "vkWaitForFences failed with error code $waitResult" }

            vkDestroyFence(deviceHandle, fence, null)
            deviceRef.freeCommandBuffers(vkBuffers)
        }
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    private var configuration: GpuSurfaceConfiguration? = null
    internal var configuredDevice: GpuDevice? = null
    internal var renderSurface: RenderSurface? = null
    private var swapchain: VulkanSwapchain? = null
    private var lastImage: SwapchainImage? = null
    private var preferredFormat: GpuTextureFormat = GpuTextureFormat.BGRA8_UNORM
    private var extent: Pair<Int, Int> = 640 to 480
    private var frameCounter: Long = 0L

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        configuredDevice = device
        val surface = renderSurface
        if (surface == null) {
            swapchain?.dispose()
            swapchain = null
            extent = configuration.width to configuration.height
            preferredFormat = configuration.format
            frameCounter = 0L
            this.configuration = configuration
            return
        }

        val vulkanSurface = surface as? VulkanSurface
            ?: error("RenderSurface must be VulkanSurface on JVM targets")

        val vkInstance = device.rendererDevice.unwrapInstance() as? VkInstance
            ?: error("Vulkan instance handle unavailable for surface configuration")
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for surface configuration")
        val vkPhysicalDevice = device.rendererDevice.unwrapPhysicalHandle() as? VkPhysicalDevice
            ?: error("Vulkan physical device handle unavailable for surface configuration")

        val surfaceHandle = vulkanSurface.createSurface(vkInstance)
        swapchain?.dispose()

        val swapchainManager = VulkanSwapchain(vkDevice, vkPhysicalDevice, surfaceHandle)
        val (frameWidth, frameHeight) = vulkanSurface.getFramebufferSize()
        val resolvedWidth = if (configuration.width > 0) configuration.width else frameWidth
        val resolvedHeight = if (configuration.height > 0) configuration.height else frameHeight
        swapchainManager.recreateSwapchain(resolvedWidth, resolvedHeight)
        swapchain = swapchainManager

        extent = swapchainManager.getExtent()
        val actualFormat = swapchainManager.getImageFormat().toGpuTextureFormat()
        preferredFormat = actualFormat
        frameCounter = 0L
        this.configuration = configuration.copy(
            width = resolvedWidth,
            height = resolvedHeight,
            format = actualFormat
        )
    }

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat {
        @Suppress("UNUSED_PARAMETER")
        val unused = adapter
        return preferredFormat
    }

    actual fun acquireFrame(): GpuSurfaceFrame {
        val device = configuredDevice ?: error("GpuSurface not configured with a device")
        val config = configuration ?: error("Surface configuration missing.")
        val swapchainManager = swapchain

        if (swapchainManager == null) {
            val descriptor = GpuTextureDescriptor(
                label = "${label ?: "surface"}-frame-${frameCounter++}",
                size = Triple(extent.first, extent.second, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = preferredFormat,
                usage = config.usage
            )
            val texture = device.createTexture(descriptor)
            val view = texture.createView()
            return GpuSurfaceFrame(texture, view)
        }

        val image = try {
            swapchainManager.acquireNextImage()
        } catch (error: Exception) {
            throw IllegalStateException("Failed to acquire swapchain image", error)
        }

        lastImage = image
        extent = swapchainManager.getExtent()

        val textureDescriptor = GpuTextureDescriptor(
            label = "${label ?: "surface"}-frame-${image.index}",
            size = Triple(extent.first, extent.second, 1),
            mipLevelCount = 1,
            sampleCount = 1,
            dimension = GpuTextureDimension.D2,
            format = preferredFormat,
            usage = config.usage
        )

        val swapchainImageHandle = (image.handle as? Long)
            ?: error("Swapchain image handle is not a Long (actual=${image.handle})")
        val texture = GpuTexture(device, textureDescriptor).apply {
            wrapExternalImage(swapchainImageHandle)
        }

        val imageViewHandle = swapchainManager.getImageView(image.index)
        val view = texture.attachExternalView(
            imageViewHandle,
            GpuTextureViewDescriptor(label = "${textureDescriptor.label}-view")
        )

        return GpuSurfaceFrame(texture, view)
    }

    actual fun present(frame: GpuSurfaceFrame) {
        val swapchainManager = swapchain
        if (swapchainManager == null) {
            frame.texture.destroy()
            return
        }

        val device = configuredDevice ?: error("GpuSurface not configured with a device")
        val image = lastImage ?: error("No swapchain image acquired before present()")
        val queueHandle = device.queue.vkQueueHandle
            ?: error("Vulkan queue handle unavailable for presentation")

        MemoryStack.stackPush().use { stack ->
            val presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchainManager.getSwapchainHandle()))
                .pImageIndices(stack.ints(image.index))

            when (val result = vkQueuePresentKHR(queueHandle, presentInfo)) {
                VK_SUCCESS -> Unit
                VK_SUBOPTIMAL_KHR, VK_ERROR_OUT_OF_DATE_KHR -> {
                    val (width, height) = extent
                    swapchainManager.recreateSwapchain(width, height)
                    extent = swapchainManager.getExtent()
                    val actualFormat = swapchainManager.getImageFormat().toGpuTextureFormat()
                    preferredFormat = actualFormat
                    configuration = configuration?.copy(
                        width = extent.first,
                        height = extent.second,
                        format = actualFormat
                    )
                }
                else -> error("vkQueuePresentKHR failed with error code $result")
            }
        }

        vkQueueWaitIdle(queueHandle)
        lastImage = null
        frame.texture.destroy()
    }

    actual fun resize(width: Int, height: Int) {
        val swapchainManager = swapchain
        if (swapchainManager != null) {
            swapchainManager.recreateSwapchain(width, height)
            extent = swapchainManager.getExtent()
            val actualFormat = swapchainManager.getImageFormat().toGpuTextureFormat()
            preferredFormat = actualFormat
            configuration = configuration?.copy(width = width, height = height, format = actualFormat)
        } else {
            configuration = configuration?.copy(width = width, height = height)
            extent = width to height
        }
    }
}

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    private lateinit var rendererBuffer: RendererGpuBuffer

    actual fun write(data: ByteArray, offset: Int) {
        check(::rendererBuffer.isInitialized) { "Renderer buffer not attached" }
        require(offset >= 0) { "Offset must be >= 0" }
        val byteOffset = offset.toLong()
        device.mapAndCopy(rendererBuffer, data, byteOffset)
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val byteArray = ByteArray(data.size * Float.SIZE_BYTES)
        var position = 0
        data.forEach { value ->
            val bits = value.toRawBits()
            byteArray[position++] = (bits and 0xFF).toByte()
            byteArray[position++] = ((bits shr 8) and 0xFF).toByte()
            byteArray[position++] = ((bits shr 16) and 0xFF).toByte()
            byteArray[position++] = ((bits shr 24) and 0xFF).toByte()
        }
        write(byteArray, offset * Float.SIZE_BYTES)
    }

    actual fun destroy() {
        if (::rendererBuffer.isInitialized) {
            device.destroyBuffer(rendererBuffer)
        }
    }

    internal fun attach(buffer: RendererGpuBuffer) {
        rendererBuffer = buffer
    }

    internal fun nativeBuffer(): RendererGpuBuffer {
        check(::rendererBuffer.isInitialized) { "Renderer buffer not attached" }
        return rendererBuffer
    }
}

private data class ImageViewRecord(
    val handle: Long,
    val owned: Boolean
)

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    private lateinit var rendererTexture: RendererGpuTexture
    private var destroyed = false
    private var externalImageHandle: Long? = null
    private val externalViews = mutableListOf<ImageViewRecord>()
    private var ownsImage = true

    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val renderer = rendererTextureOrThrow()
        val rendererDescriptor = descriptor.toRendererDescriptor()
        val rendererView = renderer.createView(rendererDescriptor)
        return GpuTextureView(this, descriptor).apply { attach(rendererView) }
    }

    actual fun destroy() {
        if (destroyed) return
        if (::rendererTexture.isInitialized && ownsImage) {
            rendererTexture.destroy()
        }
        externalViews.clear()
        destroyed = true
    }

    internal fun attach(texture: RendererGpuTexture) {
        rendererTexture = texture
        ownsImage = true
    }

    internal fun rendererTextureOrThrow(): RendererGpuTexture {
        check(::rendererTexture.isInitialized) { "Renderer texture not attached" }
        return rendererTexture
    }

    internal fun wrapExternalImage(imageHandle: Long) {
        externalImageHandle = imageHandle
        ownsImage = false
    }

    internal fun attachExternalView(viewHandle: Long, descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val view = GpuTextureView(this, descriptor).apply { attachExternal(viewHandle) }
        externalViews += ImageViewRecord(viewHandle, owned = false)
        return view
    }
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
) {
    private lateinit var rendererView: RendererGpuTextureView
    internal var handle: Long = VK_NULL_HANDLE
        private set

    internal fun attach(view: RendererGpuTextureView) {
        rendererView = view
        handle = (view.unwrapHandle() as? Long) ?: VK_NULL_HANDLE
    }

    internal fun rendererViewOrNull(): RendererGpuTextureView? =
        if (::rendererView.isInitialized) rendererView else null

    internal fun attachExternal(viewHandle: Long) {
        handle = viewHandle
    }
}

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
) {
    private lateinit var rendererSampler: RendererGpuSampler

    internal fun attach(sampler: RendererGpuSampler) {
        rendererSampler = sampler
    }

    internal val samplerHandle: Long
        get() = (rendererSampler.unwrapHandle() as? Long) ?: VK_NULL_HANDLE
}

actual class GpuCommandEncoder actual internal constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    private val commandBuffer: VkCommandBuffer = device.allocateCommandBuffer()
    private var finished = false

    init {
        MemoryStack.stackPush().use { stack ->
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            val result = vkBeginCommandBuffer(commandBuffer, beginInfo)
            check(result == VK_SUCCESS) { "vkBeginCommandBuffer failed with error code $result" }
        }
    }

    actual fun finish(label: String?): GpuCommandBuffer {
        check(!finished) { "Command encoder already finished" }
        val result = vkEndCommandBuffer(commandBuffer)
        check(result == VK_SUCCESS) { "vkEndCommandBuffer failed with error code $result" }
        finished = true
        return GpuCommandBuffer(device, label ?: descriptor?.label).also {
            it.handle = commandBuffer
        }
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder {
        check(!finished) { "Cannot begin render pass after encoder has been finished" }
        return GpuRenderPassEncoder(this, descriptor)
    }

    internal fun vkCommandBuffer(): VkCommandBuffer = commandBuffer
}

actual class GpuCommandBuffer actual internal constructor(
    actual val device: GpuDevice,
    actual val label: String?
) {
    internal lateinit var handle: VkCommandBuffer
}

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    internal val handle: Long = createShaderModule(device, descriptor)

    private fun createShaderModule(device: GpuDevice, descriptor: GpuShaderModuleDescriptor): Long {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for shader module creation")

        val spirvBytes = loadSpirvBytes(descriptor)
        require(spirvBytes.size % 4 == 0) {
            "SPIR-V module size must be a multiple of 4 bytes (label=${descriptor.label})"
        }

        MemoryStack.stackPush().use { stack ->
            val codeBuffer = stack.malloc(spirvBytes.size)
            codeBuffer.put(spirvBytes)
            codeBuffer.flip()

            val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(codeBuffer)

            val pShaderModule = stack.mallocLong(1)
            val result = vkCreateShaderModule(vkDevice, createInfo, null, pShaderModule)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("vkCreateShaderModule failed with error code $result (label=${descriptor.label})")
            }

            return pShaderModule[0]
        }
    }

    private fun loadSpirvBytes(descriptor: GpuShaderModuleDescriptor): ByteArray {
        val label = descriptor.label
            ?: error("Shader module descriptor requires a label to locate compiled SPIR-V")

        val resourceName = "/shaders/${label}.main.spv"
        val stream = GpuShaderModule::class.java.getResourceAsStream(resourceName)
            ?: error("Compiled SPIR-V resource not found for shader label '$label' at '$resourceName'. Did you run ./gradlew compileShaders?")

        return stream.use { it.readBytes() }
    }

    internal fun destroy() {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice ?: return
        vkDestroyShaderModule(vkDevice, handle, null)
    }
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
) {
    internal val handle: Long

    init {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for bind group layout creation")
        MemoryStack.stackPush().use { stack ->
            val bindings = VkDescriptorSetLayoutBinding.calloc(descriptor.entries.size, stack)
            descriptor.entries.forEachIndexed { index, entry ->
                bindings[index]
                    .binding(entry.binding)
                    .descriptorType(entry.resourceType.toVulkanDescriptorType())
                    .descriptorCount(1)
                    .stageFlags(entry.visibility.toVulkanStageFlags())
                    .pImmutableSamplers(null)
            }

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings)

            val pLayout = stack.mallocLong(1)
            val result = vkCreateDescriptorSetLayout(vkDevice, layoutInfo, null, pLayout)
            check(result == VK_SUCCESS) { "vkCreateDescriptorSetLayout failed with error code $result" }
            handle = pLayout[0]
        }
        device.registerBindGroupLayout(this)
    }

    internal fun destroy() {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice ?: return
        if (handle != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(vkDevice, handle, null)
        }
        device.unregisterBindGroupLayout(this)
    }
}

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
) {
    internal var descriptorSet: Long = VK_NULL_HANDLE

    init {
        allocateDescriptorSet()
        layout.device.registerBindGroup(this)
    }

    private fun allocateDescriptorSet() {
        val vkDevice = layout.device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for bind group allocation")
        val descriptorPool = layout.device.descriptorPoolHandle
        check(descriptorPool != VK_NULL_HANDLE) { "Descriptor pool not available for bind group allocation" }

        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(layout.handle))

            val pSet = stack.mallocLong(1)
            val allocResult = vkAllocateDescriptorSets(vkDevice, allocInfo, pSet)
            check(allocResult == VK_SUCCESS) { "vkAllocateDescriptorSets failed with error code $allocResult" }
            descriptorSet = pSet[0]

            val writes = VkWriteDescriptorSet.calloc(descriptor.entries.size, stack)
            descriptor.entries.forEachIndexed { index, entry ->
                val layoutEntry = layout.descriptor.entries.firstOrNull { it.binding == entry.binding }
                    ?: error("No bind group layout entry for binding ${entry.binding}")
                val write = writes[index]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(entry.binding)
                    .descriptorCount(1)
                    .descriptorType(layoutEntry.resourceType.toVulkanDescriptorType())

                when (layoutEntry.resourceType) {
                    GpuBindingResourceType.UNIFORM_BUFFER,
                    GpuBindingResourceType.STORAGE_BUFFER -> {
                        val resource = entry.resource as? GpuBindingResource.Buffer
                            ?: error("Bind group entry ${entry.binding} expected buffer resource")
                        val rendererBuffer = resource.buffer.nativeBuffer()
                        val bufferInfos = VkDescriptorBufferInfo.calloc(1, stack)
                        bufferInfos[0]
                            .buffer(rendererBuffer.buffer)
                            .offset(resource.offset)
                            .range(resource.size ?: rendererBuffer.size)
                        write.pBufferInfo(bufferInfos)
                    }
                    GpuBindingResourceType.TEXTURE -> {
                        val resource = entry.resource as? GpuBindingResource.Texture
                            ?: error("Bind group entry ${entry.binding} expected texture resource")
                        val imageInfos = VkDescriptorImageInfo.calloc(1, stack)
                        imageInfos[0]
                            .sampler(VK_NULL_HANDLE)
                            .imageView(resource.textureView.handle)
                            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                        write.pImageInfo(imageInfos)
                    }
                    GpuBindingResourceType.SAMPLER -> {
                        val resource = entry.resource as? GpuBindingResource.Sampler
                            ?: error("Bind group entry ${entry.binding} expected sampler resource")
                        val imageInfos = VkDescriptorImageInfo.calloc(1, stack)
                        imageInfos[0]
                            .sampler(resource.sampler.samplerHandle)
                            .imageView(VK_NULL_HANDLE)
                            .imageLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                        write.pImageInfo(imageInfos)
                    }
                }
            }

            vkUpdateDescriptorSets(vkDevice, writes, null)
        }
    }

    internal fun destroy() {
        val vkDevice = layout.device.rendererDevice.unwrapHandle() as? VkDevice ?: return
        val descriptorPool = layout.device.descriptorPoolHandle
        if (descriptorSet != VK_NULL_HANDLE && descriptorPool != VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val pSets = stack.longs(descriptorSet)
                vkFreeDescriptorSets(vkDevice, descriptorPool, pSets)
            }
            descriptorSet = VK_NULL_HANDLE
        }
        layout.device.unregisterBindGroup(this)
    }
}

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
) {
    internal val renderPassKey: RenderPassKey
    internal val renderPassHandle: Long
    internal val pipelineHandle: Long
    internal val pipelineLayoutHandle: Long

    init {
        val colorKeys = descriptor.colorFormats.ifEmpty {
            listOf(GpuTextureFormat.BGRA8_UNORM)
        }.map { format ->
            ColorAttachmentKey(format, GpuLoadOp.CLEAR, GpuStoreOp.STORE)
        }
        renderPassKey = RenderPassKey(colorKeys, descriptor.depthStencilFormat)
        renderPassHandle = device.obtainRenderPass(renderPassKey)
        val deviceHandle = device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for pipeline creation")
        MemoryStack.stackPush().use { stack ->
            val layoutHandles = if (descriptor.bindGroupLayouts.isNotEmpty()) {
                val buffer = stack.mallocLong(descriptor.bindGroupLayouts.size)
                descriptor.bindGroupLayouts.forEachIndexed { index, layout ->
                    buffer.put(index, layout.handle)
                }
                buffer
            } else null

            val pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(layoutHandles)
                .pPushConstantRanges(null)

            val pPipelineLayout = stack.mallocLong(1)
            val layoutResult = vkCreatePipelineLayout(deviceHandle, pipelineLayoutInfo, null, pPipelineLayout)
            check(layoutResult == VK_SUCCESS) { "vkCreatePipelineLayout failed with error code $layoutResult" }
            pipelineLayoutHandle = pPipelineLayout[0]

            val stages = VkPipelineShaderStageCreateInfo.calloc(2, stack)
            stages[0]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(descriptor.vertexShader.handle)
                .pName(stack.UTF8("main"))
            val fragmentShader = descriptor.fragmentShader
                ?: error("Fragment shader required for Vulkan render pipeline")
            stages[1]
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShader.handle)
                .pName(stack.UTF8("main"))

            val vertexLayouts = descriptor.vertexBuffers
            val bindingDescriptions = if (vertexLayouts.isNotEmpty()) {
                VkVertexInputBindingDescription.calloc(vertexLayouts.size, stack).also { bindings ->
                    vertexLayouts.forEachIndexed { bindingIndex, layout ->
                        bindings[bindingIndex]
                            .binding(bindingIndex)
                            .stride(layout.arrayStride)
                            .inputRate(layout.stepMode.toVulkanInputRate())
                    }
                }
            } else {
                VkVertexInputBindingDescription.calloc(1, stack).also { bindings ->
                    bindings[0]
                        .binding(0)
                        .stride(3 * Float.SIZE_BYTES)
                        .inputRate(VK_VERTEX_INPUT_RATE_VERTEX)
                }
            }

            val attributeDescriptions = if (vertexLayouts.isNotEmpty()) {
                val totalAttributes = vertexLayouts.sumOf { it.attributes.size }
                if (totalAttributes > 0) {
                    VkVertexInputAttributeDescription.calloc(totalAttributes, stack).also { attributes ->
                        var attributeIndex = 0
                        vertexLayouts.forEachIndexed { bindingIndex, layout ->
                            layout.attributes.forEach { attribute ->
                                attributes[attributeIndex]
                                    .binding(bindingIndex)
                                    .location(attribute.shaderLocation)
                                    .format(attribute.format.toVulkanFormat())
                                    .offset(attribute.offset)
                                attributeIndex += 1
                            }
                        }
                    }
                } else null
            } else {
                VkVertexInputAttributeDescription.calloc(1, stack).also { attributes ->
                    attributes[0]
                        .binding(0)
                        .location(0)
                        .format(VK_FORMAT_R32G32B32_SFLOAT)
                        .offset(0)
                }
            }

            val vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescriptions)

            attributeDescriptions?.let { vertexInputInfo.pVertexAttributeDescriptions(it) }

            val inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(descriptor.primitiveTopology.toVulkan())
                .primitiveRestartEnable(false)

            val viewportState = org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .scissorCount(1)

            val rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .cullMode(descriptor.cullMode.toVulkan())
                .frontFace(descriptor.frontFace.toVulkan())
                .lineWidth(1f)

            val multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT)

            val colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
            colorBlendAttachment[0]
                .colorWriteMask(
                    VK_COLOR_COMPONENT_R_BIT or
                        VK_COLOR_COMPONENT_G_BIT or
                        VK_COLOR_COMPONENT_B_BIT or
                        VK_COLOR_COMPONENT_A_BIT
                ).apply {
                    descriptor.blendMode.applyTo(this)
                }

            val colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .pAttachments(colorBlendAttachment)

            val depthStateInfo = descriptor.depthState?.let { depth ->
                VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(depth.depthWriteEnabled)
                    .depthCompareOp(depth.depthCompare.toVulkan())
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false)
                    .minDepthBounds(0f)
                    .maxDepthBounds(1f)
            }

            val dynamicStates = stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR)
            val dynamicState = VkPipelineDynamicStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                .pDynamicStates(dynamicStates)

            val pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(stages)
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pColorBlendState(colorBlending)
                .pDynamicState(dynamicState)
                .layout(pipelineLayoutHandle)
                .renderPass(renderPassHandle)
                .subpass(0)

            if (depthStateInfo != null) {
                pipelineInfo.pDepthStencilState(depthStateInfo)
            }

            val pPipeline = stack.mallocLong(1)
            val pipelineResult = vkCreateGraphicsPipelines(deviceHandle, VK_NULL_HANDLE, pipelineInfo, null, pPipeline)
            check(pipelineResult == VK_SUCCESS) { "vkCreateGraphicsPipelines failed with error code $pipelineResult" }
            pipelineHandle = pPipeline[0]
        }
    }

    internal fun destroyInternal() {
        val deviceHandle = device.rendererDevice.unwrapHandle() as? VkDevice ?: return
        if (pipelineHandle != VK_NULL_HANDLE) {
            vkDestroyPipeline(deviceHandle, pipelineHandle, null)
        }
        if (pipelineLayoutHandle != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(deviceHandle, pipelineLayoutHandle, null)
        }
        device.unregisterPipeline(this)
    }
}

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
)

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    private val commandBuffer: VkCommandBuffer = encoder.vkCommandBuffer()
    private val device: GpuDevice = encoder.device
    private val deviceHandle: VkDevice =
        device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for render pass encoding")
    private val renderPassKey: RenderPassKey
    private var framebufferHandle: Long = VK_NULL_HANDLE
    private var activePipeline: GpuRenderPipeline? = null
    private var ended = false

    init {
        if (descriptor.colorAttachments.isEmpty()) {
            error("GpuRenderPassDescriptor requires at least one color attachment")
        }

        val colorKeys = descriptor.colorAttachments.map { attachment ->
            ColorAttachmentKey(
                format = attachment.view.texture.descriptor.format,
                loadOp = attachment.loadOp,
                storeOp = attachment.storeOp
            )
        }
        renderPassKey = RenderPassKey(colorKeys, null)
        val renderPassHandle = device.obtainRenderPass(renderPassKey)

        MemoryStack.stackPush().use { stack ->
            val attachments = stack.mallocLong(descriptor.colorAttachments.size)
            descriptor.colorAttachments.forEachIndexed { index, attachment ->
                attachments.put(index, attachment.view.handle)
            }

            val firstAttachment = descriptor.colorAttachments.first()
            val width = firstAttachment.view.texture.descriptor.size.first
            val height = firstAttachment.view.texture.descriptor.size.second

            val framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                .renderPass(renderPassHandle)
                .pAttachments(attachments)
                .width(width)
                .height(height)
                .layers(1)

            val pFramebuffer = stack.mallocLong(1)
            val framebufferResult = vkCreateFramebuffer(deviceHandle, framebufferInfo, null, pFramebuffer)
            check(framebufferResult == VK_SUCCESS) { "vkCreateFramebuffer failed with error code $framebufferResult" }
            framebufferHandle = pFramebuffer[0]

            val clearValues = VkClearValue.calloc(descriptor.colorAttachments.size, stack)
            descriptor.colorAttachments.forEachIndexed { index, attachment ->
                val clear = attachment.clearColor
                clearValues[index].color()
                    .float32(0, clear.getOrElse(0) { 0f })
                    .float32(1, clear.getOrElse(1) { 0f })
                    .float32(2, clear.getOrElse(2) { 0f })
                    .float32(3, clear.getOrElse(3) { 1f })
            }

            val renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPassHandle)
                .framebuffer(framebufferHandle)
                .renderArea { area ->
                    area.offset().set(0, 0)
                    area.extent().set(width, height)
                }
                .pClearValues(clearValues)

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)

            val viewport = VkViewport.calloc(1, stack)
            viewport[0]
                .x(0f)
                .y(0f)
                .width(width.toFloat())
                .height(height.toFloat())
                .minDepth(0f)
                .maxDepth(1f)
            vkCmdSetViewport(commandBuffer, 0, viewport)

            val scissor = VkRect2D.calloc(1, stack)
            scissor[0].offset().set(0, 0)
            scissor[0].extent().set(width, height)
            vkCmdSetScissor(commandBuffer, 0, scissor)
        }
    }

    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        check(!ended) { "Render pass already ended" }
        require(pipeline.renderPassKey == renderPassKey) {
            "Pipeline render pass configuration is incompatible with the active render pass"
        }
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipelineHandle)
        activePipeline = pipeline
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        check(!ended) { "Render pass already ended" }
        val native = buffer.nativeBuffer()
        MemoryStack.stackPush().use { stack ->
            val buffers = stack.mallocLong(1).put(0, native.buffer)
            val offsets = stack.mallocLong(1).put(0, 0L)
            vkCmdBindVertexBuffers(commandBuffer, slot, buffers, offsets)
        }
    }

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) {
        check(!ended) { "Render pass already ended" }
        val native = buffer.nativeBuffer()
        val indexType = when (format) {
            GpuIndexFormat.UINT16 -> VK_INDEX_TYPE_UINT16
            GpuIndexFormat.UINT32 -> VK_INDEX_TYPE_UINT32
        }
        vkCmdBindIndexBuffer(commandBuffer, native.buffer, offset, indexType)
    }

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) {
        check(!ended) { "Render pass already ended" }
        val pipeline = activePipeline ?: error("No pipeline bound before setBindGroup() call")
        val descriptorSet = bindGroup.descriptorSet
        check(descriptorSet != VK_NULL_HANDLE) { "Bind group descriptor set is not allocated" }

        MemoryStack.stackPush().use { stack ->
            val pSets = stack.longs(descriptorSet)
            vkCmdBindDescriptorSets(
                commandBuffer,
                VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline.pipelineLayoutHandle,
                index,
                pSets,
                null
            )
        }
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        check(!ended) { "Render pass already ended" }
        check(activePipeline != null) { "No pipeline bound before draw() call" }
        vkCmdDraw(commandBuffer, vertexCount, instanceCount, firstVertex, firstInstance)
    }

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) {
        check(!ended) { "Render pass already ended" }
        check(activePipeline != null) { "No pipeline bound before drawIndexed() call" }
        vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
    }

    actual fun end() {
        if (ended) return
        vkCmdEndRenderPass(commandBuffer)
        vkDestroyFramebuffer(deviceHandle, framebufferHandle, null)
        framebufferHandle = VK_NULL_HANDLE
        ended = true
    }
}

internal data class RenderPassKey(
    val colorAttachments: List<ColorAttachmentKey>,
    val depthFormat: GpuTextureFormat?
)

internal data class ColorAttachmentKey(
    val format: GpuTextureFormat,
    val loadOp: GpuLoadOp,
    val storeOp: GpuStoreOp
)

private fun GpuTextureFormat.toVulkan(): Int = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> VK_FORMAT_R8G8B8A8_UNORM
    GpuTextureFormat.BGRA8_UNORM -> VK_FORMAT_B8G8R8A8_UNORM
    GpuTextureFormat.RGBA16_FLOAT -> VK_FORMAT_R16G16B16A16_SFLOAT
    GpuTextureFormat.DEPTH24_PLUS -> VK_FORMAT_D24_UNORM_S8_UINT
}

private fun GpuPrimitiveTopology.toVulkan(): Int = when (this) {
    GpuPrimitiveTopology.POINT_LIST -> VK_PRIMITIVE_TOPOLOGY_POINT_LIST
    GpuPrimitiveTopology.LINE_LIST -> VK_PRIMITIVE_TOPOLOGY_LINE_LIST
    GpuPrimitiveTopology.LINE_STRIP -> VK_PRIMITIVE_TOPOLOGY_LINE_STRIP
    GpuPrimitiveTopology.TRIANGLE_LIST -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST
    GpuPrimitiveTopology.TRIANGLE_STRIP -> VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP
}

private fun GpuCullMode.toVulkan(): Int = when (this) {
    GpuCullMode.NONE -> VK_CULL_MODE_NONE
    GpuCullMode.FRONT -> VK_CULL_MODE_FRONT_BIT
    GpuCullMode.BACK -> VK_CULL_MODE_BACK_BIT
}

private fun GpuFrontFace.toVulkan(): Int = when (this) {
    GpuFrontFace.CCW -> VK_FRONT_FACE_COUNTER_CLOCKWISE
    GpuFrontFace.CW -> VK_FRONT_FACE_CLOCKWISE
}

private fun GpuVertexStepMode.toVulkanInputRate(): Int = when (this) {
    GpuVertexStepMode.VERTEX -> VK_VERTEX_INPUT_RATE_VERTEX
    GpuVertexStepMode.INSTANCE -> VK_VERTEX_INPUT_RATE_INSTANCE
}

private fun GpuVertexFormat.toVulkanFormat(): Int = when (this) {
    GpuVertexFormat.FLOAT32 -> VK_FORMAT_R32_SFLOAT
    GpuVertexFormat.FLOAT32x2 -> VK_FORMAT_R32G32_SFLOAT
    GpuVertexFormat.FLOAT32x3 -> VK_FORMAT_R32G32B32_SFLOAT
    GpuVertexFormat.FLOAT32x4 -> VK_FORMAT_R32G32B32A32_SFLOAT
    GpuVertexFormat.UINT32 -> VK_FORMAT_R32_UINT
    GpuVertexFormat.UINT32x2 -> VK_FORMAT_R32G32_UINT
    GpuVertexFormat.UINT32x3 -> VK_FORMAT_R32G32B32_UINT
    GpuVertexFormat.UINT32x4 -> VK_FORMAT_R32G32B32A32_UINT
    GpuVertexFormat.SINT32 -> VK_FORMAT_R32_SINT
    GpuVertexFormat.SINT32x2 -> VK_FORMAT_R32G32_SINT
    GpuVertexFormat.SINT32x3 -> VK_FORMAT_R32G32B32_SINT
    GpuVertexFormat.SINT32x4 -> VK_FORMAT_R32G32B32A32_SINT
}

private fun GpuCompareFunction.toVulkan(): Int = when (this) {
    GpuCompareFunction.ALWAYS -> VK_COMPARE_OP_ALWAYS
    GpuCompareFunction.LESS -> VK_COMPARE_OP_LESS
    GpuCompareFunction.LESS_EQUAL -> VK_COMPARE_OP_LESS_OR_EQUAL
}

private fun GpuBlendMode.applyTo(attachment: VkPipelineColorBlendAttachmentState): VkPipelineColorBlendAttachmentState = when (this) {
    GpuBlendMode.DISABLED -> attachment.blendEnable(false)
    GpuBlendMode.ALPHA -> attachment
        .blendEnable(true)
        .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
        .colorBlendOp(VK_BLEND_OP_ADD)
        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
        .alphaBlendOp(VK_BLEND_OP_ADD)
    GpuBlendMode.ADDITIVE -> attachment
        .blendEnable(true)
        .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
        .dstColorBlendFactor(VK_BLEND_FACTOR_ONE)
        .colorBlendOp(VK_BLEND_OP_ADD)
        .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
        .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
        .alphaBlendOp(VK_BLEND_OP_ADD)
}

private fun Int.toGpuTextureFormat(): GpuTextureFormat = when (this) {
    VK_FORMAT_R8G8B8A8_UNORM -> GpuTextureFormat.RGBA8_UNORM
    VK_FORMAT_B8G8R8A8_UNORM -> GpuTextureFormat.BGRA8_UNORM
    VK_FORMAT_R16G16B16A16_SFLOAT -> GpuTextureFormat.RGBA16_FLOAT
    VK_FORMAT_D24_UNORM_S8_UINT -> GpuTextureFormat.DEPTH24_PLUS
    else -> GpuTextureFormat.BGRA8_UNORM
}

private fun GpuTextureUsageFlags.toVulkanImageUsage(): Int {
    var usage = 0
    if (this and GpuTextureUsage.COPY_SRC.mask != 0) usage = usage or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
    if (this and GpuTextureUsage.COPY_DST.mask != 0) usage = usage or VK_IMAGE_USAGE_TRANSFER_DST_BIT
    if (this and GpuTextureUsage.TEXTURE_BINDING.mask != 0) usage = usage or VK_IMAGE_USAGE_SAMPLED_BIT
    if (this and GpuTextureUsage.STORAGE_BINDING.mask != 0) usage = usage or VK_IMAGE_USAGE_STORAGE_BIT
    if (this and GpuTextureUsage.RENDER_ATTACHMENT.mask != 0) usage = usage or VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
    return usage
}

private fun GpuLoadOp.toVulkan(): Int = when (this) {
    GpuLoadOp.LOAD -> VK_ATTACHMENT_LOAD_OP_LOAD
    GpuLoadOp.CLEAR -> VK_ATTACHMENT_LOAD_OP_CLEAR
}

private fun GpuStoreOp.toVulkan(): Int = when (this) {
    GpuStoreOp.STORE -> VK_ATTACHMENT_STORE_OP_STORE
    GpuStoreOp.DISCARD -> VK_ATTACHMENT_STORE_OP_DONT_CARE
}

private fun GpuFilterMode.toVulkan(): Int = when (this) {
    GpuFilterMode.NEAREST -> VK_FILTER_NEAREST
    GpuFilterMode.LINEAR -> VK_FILTER_LINEAR
}

private fun GpuMipmapFilterMode.toVulkan(): Int = when (this) {
    GpuMipmapFilterMode.NEAREST -> VK_SAMPLER_MIPMAP_MODE_NEAREST
    GpuMipmapFilterMode.LINEAR -> VK_SAMPLER_MIPMAP_MODE_LINEAR
}

private fun GpuAddressMode.toVulkan(): Int = when (this) {
    GpuAddressMode.CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
    GpuAddressMode.REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT
    GpuAddressMode.MIRROR_REPEAT -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT
}

private fun findMemoryType(
    physicalDevice: VkPhysicalDevice,
    typeFilter: Int,
    properties: Int
): Int {
    MemoryStack.stackPush().use { stack ->
        val memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack)
        vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)

        for (i in 0 until memProperties.memoryTypeCount()) {
            val suitable = (typeFilter and (1 shl i)) != 0
            val hasProps = (memProperties.memoryTypes(i).propertyFlags() and properties) == properties
            if (suitable && hasProps) {
                return i
            }
        }
    }
    throw IllegalStateException("Failed to find suitable Vulkan memory type")
}

private fun Set<GpuShaderStage>.toVulkanStageFlags(): Int {
    var flags = 0
    if (contains(GpuShaderStage.VERTEX)) flags = flags or VK_SHADER_STAGE_VERTEX_BIT
    if (contains(GpuShaderStage.FRAGMENT)) flags = flags or VK_SHADER_STAGE_FRAGMENT_BIT
    if (contains(GpuShaderStage.COMPUTE)) flags = flags or VK_SHADER_STAGE_COMPUTE_BIT
    return flags
}

private fun GpuBindingResourceType.toVulkanDescriptorType(): Int = when (this) {
    GpuBindingResourceType.UNIFORM_BUFFER -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER
    GpuBindingResourceType.STORAGE_BUFFER -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER
    GpuBindingResourceType.SAMPLER -> VK_DESCRIPTOR_TYPE_SAMPLER
    GpuBindingResourceType.TEXTURE -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle

actual fun GpuBindGroup.unwrapHandle(): Any? = descriptorSet

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {
    renderSurface = surface
}
