package io.kreekt.renderer.gpu

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK10.*

actual class GpuDevice internal constructor(
    actual val backend: GpuBackend,
    actual val info: GpuDeviceInfo,
    actual val limits: GpuLimits,
    internal val handle: Any?,
    internal val physicalDeviceHandle: VkPhysicalDevice? = null,
    internal val instanceHandle: org.lwjgl.vulkan.VkInstance? = null,
    internal val descriptorPoolHandle: Long? = null,
    internal val queueFamilyIndex: Int = 0,
    internal val commandPoolRawHandle: Long? = null
) {
    actual fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray?): GpuBuffer {
        val vkDevice = handle as? VkDevice ?: error("Vulkan device handle unavailable")
        val physicalDevice = physicalDeviceHandle ?: error("Vulkan physical device unavailable")

        MemoryStack.stackPush().use { stack ->
            val bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(descriptor.size)
                .usage(descriptor.usage)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val pBuffer = stack.mallocLong(1)
            val createResult = vkCreateBuffer(vkDevice, bufferInfo, null, pBuffer)
            if (createResult != VK_SUCCESS) {
                throw RuntimeException("Failed to create Vulkan buffer (vkCreateBuffer=$createResult)")
            }
            val bufferHandle = pBuffer[0]

            val memRequirements = VkMemoryRequirements.calloc(stack)
            vkGetBufferMemoryRequirements(vkDevice, bufferHandle, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        physicalDevice,
                        memRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                )

            val pMemory = stack.mallocLong(1)
            val allocResult = vkAllocateMemory(vkDevice, allocInfo, null, pMemory)
            if (allocResult != VK_SUCCESS) {
                vkDestroyBuffer(vkDevice, bufferHandle, null)
                throw RuntimeException("Failed to allocate Vulkan buffer memory (vkAllocateMemory=$allocResult)")
            }
            val memoryHandle = pMemory[0]

            vkBindBufferMemory(vkDevice, bufferHandle, memoryHandle, 0)

            if (data != null && data.isNotEmpty()) {
                val ppData = stack.mallocPointer(1)
                vkMapMemory(vkDevice, memoryHandle, 0, descriptor.size, 0, ppData)
                val mapped = ppData[0]
                val target = MemoryUtil.memByteBuffer(mapped, descriptor.size.toInt())
                target.put(data)
                target.flip()
                vkUnmapMemory(vkDevice, memoryHandle)
            }

            return GpuBuffer(bufferHandle, memoryHandle, descriptor.size, descriptor.usage)
        }
    }

    actual fun createCommandEncoder(label: String?): GpuCommandEncoder {
        throw UnsupportedOperationException("Vulkan command encoder abstraction pending")
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val vkDevice = handle as? VkDevice ?: error("Vulkan device handle unavailable")
        val physicalDevice = physicalDeviceHandle ?: error("Vulkan physical device unavailable")

        val formatInfo = descriptor.format.toVulkanFormatInfo()
        val usageFlags = descriptor.usage.toVulkanUsageFlags(formatInfo)
        val imageType = descriptor.dimension.toVulkanImageType()
        val sampleCount = descriptor.sampleCount.toVulkanSampleCount()
        val (extentDepth, arrayLayers) = descriptor.extentDepthAndLayers()

        MemoryStack.stackPush().use { stack ->
            val createInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(imageType)
                .mipLevels(descriptor.mipLevelCount)
                .arrayLayers(arrayLayers)
                .format(formatInfo.vkFormat)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(usageFlags)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(sampleCount)

            createInfo.extent()
                .width(descriptor.width)
                .height(descriptor.height)
                .depth(extentDepth)

            val pImage = stack.mallocLong(1)
            val createResult = vkCreateImage(vkDevice, createInfo, null, pImage)
            if (createResult != VK_SUCCESS) {
                throw IllegalStateException("Failed to create Vulkan image (vkCreateImage=$createResult)")
            }
            val imageHandle = pImage[0]

            val memoryRequirements = VkMemoryRequirements.malloc(stack)
            vkGetImageMemoryRequirements(vkDevice, imageHandle, memoryRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memoryRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        physicalDevice,
                        memoryRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    )
                )

            val pMemory = stack.mallocLong(1)
            val allocResult = vkAllocateMemory(vkDevice, allocInfo, null, pMemory)
            if (allocResult != VK_SUCCESS) {
                vkDestroyImage(vkDevice, imageHandle, null)
                throw IllegalStateException("Failed to allocate Vulkan image memory (vkAllocateMemory=$allocResult)")
            }
            val memoryHandle = pMemory[0]

            val bindResult = vkBindImageMemory(vkDevice, imageHandle, memoryHandle, 0)
            if (bindResult != VK_SUCCESS) {
                vkDestroyImage(vkDevice, imageHandle, null)
                vkFreeMemory(vkDevice, memoryHandle, null)
                throw IllegalStateException("Failed to bind Vulkan image memory (vkBindImageMemory=$bindResult)")
            }

            return GpuTexture(
                width = descriptor.width,
                height = descriptor.height,
                depth = descriptor.depthOrArrayLayers,
                format = descriptor.format,
                mipLevelCount = descriptor.mipLevelCount,
                device = vkDevice,
                image = imageHandle,
                memory = memoryHandle,
                baseAspectMask = formatInfo.aspectMask,
                baseDimension = descriptor.dimension,
                arrayLayers = arrayLayers
            )
        }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val vkDevice = handle as? VkDevice ?: error("Vulkan device handle unavailable")
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(descriptor.magFilter.toVkFilter())
                .minFilter(descriptor.minFilter.toVkFilter())
                .mipmapMode(descriptor.mipmapFilter.toVkMipmapMode())
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .mipLodBias(0f)
                .anisotropyEnable(false)
                .compareEnable(false)
                .minLod(descriptor.lodMinClamp)
                .maxLod(descriptor.lodMaxClamp)
                .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)

            val pSampler = stack.mallocLong(1)
            val result = vkCreateSampler(vkDevice, createInfo, null, pSampler)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("Failed to create Vulkan sampler (vkCreateSampler=$result)")
            }

            return GpuSampler(
                device = vkDevice,
                handle = pSampler[0],
                descriptor = descriptor
            )
        }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        throw UnsupportedOperationException("Vulkan bind group layout abstraction pending")
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        throw UnsupportedOperationException("Vulkan bind group abstraction pending")
    }

    actual fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout {
        throw UnsupportedOperationException("Vulkan pipeline layout abstraction pending")
    }
}

actual class GpuQueue internal constructor(
    actual val backend: GpuBackend,
    internal val handle: Any?,
    internal val queueFamilyIndex: Int = 0,
    internal val deviceHandle: VkDevice? = null
) {
    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        throw UnsupportedOperationException("Vulkan queue submission via abstraction pending")
    }
}

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext {
        val result = VulkanBootstrap.createContext(config)
        val gpuDevice = GpuDevice(
            backend = GpuBackend.VULKAN,
            info = result.info,
            limits = result.limits,
            handle = result.device,
            physicalDeviceHandle = result.physicalDevice,
            instanceHandle = result.instance,
            descriptorPoolHandle = result.descriptorPool,
            queueFamilyIndex = result.queueFamilyIndex,
            commandPoolRawHandle = result.commandPool
        )
        val gpuQueue = GpuQueue(
            backend = GpuBackend.VULKAN,
            handle = result.queue,
            queueFamilyIndex = result.queueFamilyIndex,
            deviceHandle = result.device
        )
        return GpuContext(
            device = gpuDevice,
            queue = gpuQueue
        )
    }
}

actual fun GpuDevice.unwrapHandle(): Any? = handle

actual fun GpuDevice.unwrapPhysicalHandle(): Any? = physicalDeviceHandle

actual fun GpuQueue.unwrapHandle(): Any? = handle

actual class GpuBuffer internal constructor(
    val buffer: Long,
    val memory: Long,
    actual val size: Long,
    actual val usage: Int
)

actual fun GpuBuffer.unwrapHandle(): Any? = buffer

actual class GpuCommandEncoder internal constructor() {
    actual fun finish(): GpuCommandBuffer {
        throw UnsupportedOperationException("Vulkan command buffer finishing via abstraction pending")
    }
}

actual fun GpuCommandEncoder.unwrapHandle(): Any? = null

actual class GpuCommandBuffer internal constructor()

actual fun GpuCommandBuffer.unwrapHandle(): Any? = null

actual class GpuTexture internal constructor(
    actual val width: Int,
    actual val height: Int,
    actual val depth: Int,
    actual val format: String,
    actual val mipLevelCount: Int,
    private val device: VkDevice,
    internal val image: Long,
    private val memory: Long,
    private val baseAspectMask: Int,
    internal val baseDimension: GpuTextureDimension,
    internal val arrayLayers: Int
) {
    private val views = mutableListOf<GpuTextureView>()
    private var destroyed = false

    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        check(!destroyed) { "Cannot create view from destroyed texture" }

        val params = descriptor ?: GpuTextureViewDescriptor()
        val viewFormat = params.format ?: format
        val viewFormatInfo = viewFormat.toVulkanFormatInfo()
        val aspect = if (viewFormatInfo.aspectMask != 0) viewFormatInfo.aspectMask else baseAspectMask
        val viewType = params.dimension.toVulkanImageViewType(this)
        val baseMip = params.baseMipLevel
        val mipCount = params.mipLevelCount ?: (mipLevelCount - baseMip)
        val baseLayer = params.baseArrayLayer
        val layerCount = params.arrayLayerCount ?: (effectiveArrayLayers() - baseLayer)

        MemoryStack.stackPush().use { stack ->
            val components = VkComponentMapping.calloc(stack)
                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK_COMPONENT_SWIZZLE_IDENTITY)

            val subresource = VkImageSubresourceRange.calloc(stack)
                .aspectMask(aspect)
                .baseMipLevel(baseMip)
                .levelCount(mipCount)
                .baseArrayLayer(baseLayer)
                .layerCount(layerCount)

            val createInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(viewType)
                .format(viewFormatInfo.vkFormat)
                .components(components)
                .subresourceRange(subresource)

            val pView = stack.mallocLong(1)
            val result = vkCreateImageView(device, createInfo, null, pView)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("Failed to create Vulkan image view (vkCreateImageView=$result)")
            }

            return GpuTextureView(
                device = device,
                handle = pView[0],
                aspectMask = aspect
            ).also { views += it }
        }
    }

    actual fun destroy() {
        if (destroyed) return
        views.forEach { it.destroy() }
        views.clear()
        vkDestroyImage(device, image, null)
        vkFreeMemory(device, memory, null)
        destroyed = true
    }

    private fun effectiveArrayLayers(): Int =
        when (baseDimension) {
            GpuTextureDimension.D3 -> 1
            else -> arrayLayers
        }

    internal fun defaultViewDimension(): GpuTextureViewDimension = when (baseDimension) {
        GpuTextureDimension.D1 -> GpuTextureViewDimension.D1
        GpuTextureDimension.D2 -> if (arrayLayers > 1) GpuTextureViewDimension.D2_ARRAY else GpuTextureViewDimension.D2
        GpuTextureDimension.D3 -> GpuTextureViewDimension.D3
    }
}

actual fun GpuTexture.unwrapHandle(): Any? = image

actual class GpuSampler internal constructor(
    internal val device: VkDevice,
    internal val handle: Long,
    internal val descriptor: GpuSamplerDescriptor
) {
    internal fun destroy() {
        vkDestroySampler(device, handle, null)
    }
}

actual fun GpuSampler.unwrapHandle(): Any? = handle

actual class GpuBindGroupLayout internal constructor(
    internal val handle: Any? = null
)

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle

actual class GpuBindGroup internal constructor(
    internal val handle: Any? = null
)

actual fun GpuBindGroup.unwrapHandle(): Any? = handle

actual class GpuPipelineLayout internal constructor(
    internal val handle: Any? = null
)

actual fun GpuPipelineLayout.unwrapHandle(): Any? = handle

actual class GpuTextureView internal constructor(
    private val device: VkDevice,
    internal val handle: Long,
    internal val aspectMask: Int
) {
    private var destroyed = false

    internal fun destroy() {
        if (destroyed) return
        vkDestroyImageView(device, handle, null)
        destroyed = true
    }
}

actual fun GpuTextureView.unwrapHandle(): Any? = handle

actual fun GpuDevice.unwrapInstance(): Any? = instanceHandle

actual fun GpuDevice.unwrapDescriptorPool(): Any? = descriptorPoolHandle

actual fun GpuDevice.queueFamilyIndex(): Int = queueFamilyIndex

actual fun GpuQueue.queueFamilyIndex(): Int = queueFamilyIndex

actual fun GpuDevice.commandPoolHandle(): Long = commandPoolRawHandle ?: VK_NULL_HANDLE

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

private data class VulkanFormatInfo(
    val vkFormat: Int,
    val aspectMask: Int
)

private fun String.toVulkanFormatInfo(): VulkanFormatInfo {
    return when (lowercase()) {
        "rgba8unorm" -> VulkanFormatInfo(VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT)
        "bgra8unorm" -> VulkanFormatInfo(VK_FORMAT_B8G8R8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT)
        "rgba16float" -> VulkanFormatInfo(VK_FORMAT_R16G16B16A16_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT)
        "depth24plus" -> VulkanFormatInfo(
            VK_FORMAT_D24_UNORM_S8_UINT,
            VK_IMAGE_ASPECT_DEPTH_BIT or VK_IMAGE_ASPECT_STENCIL_BIT
        )
        else -> throw IllegalArgumentException("Unsupported texture format '$this'")
    }
}

private fun Int.toVulkanUsageFlags(formatInfo: VulkanFormatInfo): Int {
    var flags = 0
    if (hasUsage(GpuTextureUsage.COPY_SRC)) {
        flags = flags or VK_IMAGE_USAGE_TRANSFER_SRC_BIT
    }
    if (hasUsage(GpuTextureUsage.COPY_DST)) {
        flags = flags or VK_IMAGE_USAGE_TRANSFER_DST_BIT
    }
    if (hasUsage(GpuTextureUsage.TEXTURE_BINDING)) {
        flags = flags or VK_IMAGE_USAGE_SAMPLED_BIT
    }
    if (hasUsage(GpuTextureUsage.STORAGE_BINDING)) {
        flags = flags or VK_IMAGE_USAGE_STORAGE_BIT
    }
    if (hasUsage(GpuTextureUsage.RENDER_ATTACHMENT)) {
        flags = flags or if (formatInfo.aspectMask.isDepthFormat()) {
            VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
        } else {
            VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
        }
    }
    require(flags != 0) { "Texture usage mask must include at least one usage flag" }
    return flags
}

private fun Int.hasUsage(usage: GpuTextureUsage): Boolean = (this and usage.bits) != 0

private fun Int.isDepthFormat(): Boolean = (this and VK_IMAGE_ASPECT_DEPTH_BIT) != 0

private fun GpuTextureDescriptor.extentDepthAndLayers(): Pair<Int, Int> {
    return when (dimension) {
        GpuTextureDimension.D1 -> 1 to depthOrArrayLayers
        GpuTextureDimension.D2 -> 1 to depthOrArrayLayers
        GpuTextureDimension.D3 -> depthOrArrayLayers to 1
    }
}

private fun GpuTextureDimension.toVulkanImageType(): Int = when (this) {
    GpuTextureDimension.D1 -> VK_IMAGE_TYPE_1D
    GpuTextureDimension.D2 -> VK_IMAGE_TYPE_2D
    GpuTextureDimension.D3 -> VK_IMAGE_TYPE_3D
}

private fun Int.toVulkanSampleCount(): Int = when (this) {
    1 -> VK_SAMPLE_COUNT_1_BIT
    2 -> VK_SAMPLE_COUNT_2_BIT
    4 -> VK_SAMPLE_COUNT_4_BIT
    8 -> VK_SAMPLE_COUNT_8_BIT
    16 -> VK_SAMPLE_COUNT_16_BIT
    else -> throw IllegalArgumentException("Unsupported sample count $this")
}

private fun GpuTextureViewDimension?.toVulkanImageViewType(texture: GpuTexture): Int {
    return when (this ?: texture.defaultViewDimension()) {
        GpuTextureViewDimension.D1 -> VK_IMAGE_VIEW_TYPE_1D
        GpuTextureViewDimension.D2 -> VK_IMAGE_VIEW_TYPE_2D
        GpuTextureViewDimension.D2_ARRAY -> VK_IMAGE_VIEW_TYPE_2D_ARRAY
        GpuTextureViewDimension.D3 -> VK_IMAGE_VIEW_TYPE_3D
        GpuTextureViewDimension.CUBE,
        GpuTextureViewDimension.CUBE_ARRAY -> throw IllegalArgumentException("Cube image views are not yet supported")
    }
}

private fun GpuSamplerFilter.toVkFilter(): Int = when (this) {
    GpuSamplerFilter.NEAREST -> VK_FILTER_NEAREST
    GpuSamplerFilter.LINEAR -> VK_FILTER_LINEAR
}

private fun GpuSamplerFilter.toVkMipmapMode(): Int = when (this) {
    GpuSamplerFilter.NEAREST -> VK_SAMPLER_MIPMAP_MODE_NEAREST
    GpuSamplerFilter.LINEAR -> VK_SAMPLER_MIPMAP_MODE_LINEAR
}
