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
        throw UnsupportedOperationException("Vulkan texture abstraction pending")
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        throw UnsupportedOperationException("Vulkan sampler abstraction pending")
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
    actual val mipLevelCount: Int
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        throw UnsupportedOperationException("Vulkan texture view abstraction pending")
    }

    actual fun destroy() {
        // Vulkan textures are currently stubs; nothing to dispose.
    }
}

actual fun GpuTexture.unwrapHandle(): Any? = null

actual class GpuSampler internal constructor()

actual fun GpuSampler.unwrapHandle(): Any? = null

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
    internal val handle: Any? = null
)

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
