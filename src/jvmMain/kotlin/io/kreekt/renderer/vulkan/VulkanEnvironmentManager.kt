package io.kreekt.renderer.vulkan

import io.kreekt.renderer.CubeFace
import io.kreekt.renderer.CubeTexture
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class VulkanEnvironmentManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val commandPool: Long,
    private val graphicsQueue: VkQueue
) {

    data class EnvironmentBinding(
        val descriptorSet: Long,
        val layout: Long,
        val mipCount: Int
    )

    private data class CubeResource(
        val image: Long,
        val memory: Long,
        val view: Long,
        val sampler: Long,
        val descriptorSet: Long,
        val mipLevels: Int
    ) {
        fun destroy(device: VkDevice) {
            vkDestroySampler(device, sampler, null)
            vkDestroyImageView(device, view, null)
            vkDestroyImage(device, image, null)
            vkFreeMemory(device, memory, null)
        }
    }

    private var descriptorSetLayout: Long = VK_NULL_HANDLE
    private var descriptorPool: Long = VK_NULL_HANDLE
    private var cubeResource: CubeResource? = null
    private var lastTextureId: Int = -1
    private var lastVersion: Int = -1
    private var mipCount: Int = 1

    fun prepare(cube: CubeTexture?): EnvironmentBinding? {
        cube ?: run {
            disposeCube()
            return null
        }

        if (descriptorSetLayout == VK_NULL_HANDLE) {
            createDescriptorSetLayout()
        }
        if (descriptorPool == VK_NULL_HANDLE) {
            createDescriptorPool()
        }

        val needsUpload = cube.id != lastTextureId || cube.version != lastVersion || cubeResource == null
        if (needsUpload) {
            disposeCube()
            cubeResource = uploadCubeTexture(cube)
            lastTextureId = cube.id
            lastVersion = cube.version
            mipCount = cubeResource?.mipLevels ?: 1
        }

        val resource = cubeResource ?: return null
        return EnvironmentBinding(
            descriptorSet = resource.descriptorSet,
            layout = descriptorSetLayout,
            mipCount = resource.mipLevels
        )
    }

    fun dispose() {
        disposeCube()
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null)
            descriptorPool = VK_NULL_HANDLE
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null)
            descriptorSetLayout = VK_NULL_HANDLE
        }
        lastTextureId = -1
        lastVersion = -1
        mipCount = 1
    }

    private fun disposeCube() {
        cubeResource?.destroy(device)
        cubeResource = null
        lastTextureId = -1
        lastVersion = -1
    }

    private fun createDescriptorSetLayout() {
        MemoryStack.stackPush().use { stack ->
            val bindings = VkDescriptorSetLayoutBinding.calloc(2, stack)
            bindings[0]
                .binding(0)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            bindings[1]
                .binding(1)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_SAMPLER)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)

            val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings)

            val pLayout = stack.mallocLong(1)
            check(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout) == VK_SUCCESS) {
                "Failed to create environment descriptor set layout"
            }
            descriptorSetLayout = pLayout[0]
        }
    }

    private fun createDescriptorPool() {
        MemoryStack.stackPush().use { stack ->
            val poolSizes = VkDescriptorPoolSize.calloc(2, stack)
            poolSizes[0]
                .type(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                .descriptorCount(MAX_ENV_DESCRIPTOR_SETS)
            poolSizes[1]
                .type(VK_DESCRIPTOR_TYPE_SAMPLER)
                .descriptorCount(MAX_ENV_DESCRIPTOR_SETS)

            val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .pPoolSizes(poolSizes)
                .maxSets(MAX_ENV_DESCRIPTOR_SETS)

            val pPool = stack.mallocLong(1)
            check(vkCreateDescriptorPool(device, poolInfo, null, pPool) == VK_SUCCESS) {
                "Failed to create environment descriptor pool"
            }
            descriptorPool = pPool[0]
        }
    }

    private fun uploadCubeTexture(cube: CubeTexture): CubeResource {
        val mipData = collectMipData(cube)
        val format = when (cube.format) {
            io.kreekt.renderer.TextureFormat.RGBA8,
            io.kreekt.renderer.TextureFormat.SRGB8_ALPHA8 -> VK_FORMAT_R8G8B8A8_UNORM
            io.kreekt.renderer.TextureFormat.RGBA16F -> VK_FORMAT_R16G16B16A16_SFLOAT
            io.kreekt.renderer.TextureFormat.RGBA32F -> VK_FORMAT_R32G32B32A32_SFLOAT
            else -> VK_FORMAT_R8G8B8A8_UNORM
        }

        val bytesPerFace = mipData.totalBytes
        MemoryStack.stackPush().use { stack ->
            val stagingBufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(bytesPerFace.toLong())
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val pStagingBuffer = stack.mallocLong(1)
            check(vkCreateBuffer(device, stagingBufferInfo, null, pStagingBuffer) == VK_SUCCESS) {
                "Failed to create staging buffer for cube texture"
            }
            val stagingBuffer = pStagingBuffer[0]

            val stagingRequirements = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device, stagingBuffer, stagingRequirements)

            val stagingAllocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(stagingRequirements.size())
                .memoryTypeIndex(findMemoryType(stagingRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))

            val pStagingMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, stagingAllocInfo, null, pStagingMemory) == VK_SUCCESS) {
                "Failed to allocate staging memory"
            }
            val stagingMemory = pStagingMemory[0]
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)

            val ppData = stack.mallocPointer(1)
            vkMapMemory(device, stagingMemory, 0, stagingRequirements.size(), 0, ppData)
            val mapped = ppData.getByteBuffer(0, stagingRequirements.size().toInt())
            mapped.put(mipData.allBytes).flip()
            vkUnmapMemory(device, stagingMemory)

            val imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .flags(VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT)
                .imageType(VK_IMAGE_TYPE_2D)
                .extent { it.width(cube.size).height(cube.size).depth(1) }
                .mipLevels(mipData.levelCount)
                .arrayLayers(6)
                .format(format)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(VK_SAMPLE_COUNT_1_BIT)

            val pImage = stack.mallocLong(1)
            check(vkCreateImage(device, imageInfo, null, pImage) == VK_SUCCESS) { "Failed to create cube image" }
            val image = pImage[0]

            val memRequirements = VkMemoryRequirements.malloc(stack)
            vkGetImageMemoryRequirements(device, image, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))

            val pMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, allocInfo, null, pMemory) == VK_SUCCESS) { "Failed to allocate cube image memory" }
            val imageMemory = pMemory[0]
            vkBindImageMemory(device, image, imageMemory, 0)

            executeSingleTimeCommands { cmd, innerStack ->
                transitionImageLayout(
                    cmd,
                    innerStack,
                    image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    mipData.levelCount,
                    0,
                    mipData.levelCount,
                    6
                )

                var bufferOffset = 0L
                val regions = VkBufferImageCopy.calloc(mipData.levelCount * 6, innerStack)
                for (level in 0 until mipData.levelCount) {
                    val mipSize = maxOf(1, cube.size shr level)
                    val faceSize = mipSize * mipSize * mipData.bytesPerTexel
                    for (face in 0 until 6) {
                        val regionIndex = level * 6 + face
                        regions[regionIndex]
                            .bufferOffset(bufferOffset)
                            .bufferRowLength(0)
                            .bufferImageHeight(0)
                            .imageSubresource { sub ->
                                sub.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                                    .mipLevel(level)
                                    .baseArrayLayer(face)
                                    .layerCount(1)
                            }
                            .imageOffset { it.x(0).y(0).z(0) }
                            .imageExtent { it.width(mipSize).height(mipSize).depth(1) }
                        bufferOffset += faceSize
                    }
                }

                vkCmdCopyBufferToImage(
                    cmd,
                    stagingBuffer,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    regions
                )

                transitionImageLayout(
                    cmd,
                    innerStack,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    mipData.levelCount,
                    0,
                    mipData.levelCount,
                    6
                )
            }

            vkDestroyBuffer(device, stagingBuffer, null)
            vkFreeMemory(device, stagingMemory, null)

            val imageView = createImageView(image, format, mipData.levelCount)
            val sampler = createSampler(cube, mipData.levelCount)
            val descriptorSet = allocateDescriptorSet()
            updateDescriptorSet(descriptorSet, imageView, sampler)

            return CubeResource(
                image = image,
                memory = imageMemory,
                view = imageView,
                sampler = sampler,
                descriptorSet = descriptorSet,
                mipLevels = mipData.levelCount
            )
        }
    }

    private fun allocateDescriptorSet(): Long = MemoryStack.stackPush().use { stack ->
        val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
            .descriptorPool(descriptorPool)
            .pSetLayouts(stack.longs(descriptorSetLayout))

        val pDescriptorSet = stack.mallocLong(1)
        check(vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet) == VK_SUCCESS) {
            "Failed to allocate environment descriptor set"
        }
        pDescriptorSet[0]
    }
    private fun updateDescriptorSet(descriptorSet: Long, imageView: Long, sampler: Long) {
        MemoryStack.stackPush().use { stack ->
            val textureInfo = VkDescriptorImageInfo.calloc(1, stack)
            textureInfo[0]
                .imageView(imageView)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)

            val samplerInfo = VkDescriptorImageInfo.calloc(1, stack)
            samplerInfo[0]
                .sampler(sampler)

            val writes = VkWriteDescriptorSet.calloc(2, stack)
            writes[0]
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                .pImageInfo(textureInfo)
            writes[1]
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_SAMPLER)
                .pImageInfo(samplerInfo)

            vkUpdateDescriptorSets(device, writes, null)
        }
    }

    private fun createImageView(image: Long, format: Int, mipLevels: Int): Long = MemoryStack.stackPush().use { stack ->
        val components = VkComponentMapping.calloc(stack)
            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
            .a(VK_COMPONENT_SWIZZLE_IDENTITY)

        val subresourceRange = VkImageSubresourceRange.calloc(stack)
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(mipLevels)
            .baseArrayLayer(0)
            .layerCount(6)

        val createInfo = VkImageViewCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
            .image(image)
            .viewType(VK_IMAGE_VIEW_TYPE_CUBE)
            .format(format)
            .components(components)
            .subresourceRange(subresourceRange)

        val pView = stack.mallocLong(1)
        check(vkCreateImageView(device, createInfo, null, pView) == VK_SUCCESS) { "Failed to create cube image view" }
        pView[0]
    }

    private fun createSampler(cube: CubeTexture, mipLevels: Int): Long = MemoryStack.stackPush().use { stack ->
        val createInfo = VkSamplerCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
            .magFilter(toVulkanFilter(cube.magFilter))
            .minFilter(toVulkanFilter(cube.minFilter))
            .mipmapMode(toVulkanMipmapMode(cube.minFilter))
            .addressModeU(toVulkanAddressMode(cube.wrapS))
            .addressModeV(toVulkanAddressMode(cube.wrapT))
            .addressModeW(toVulkanAddressMode(cube.wrapT))
            .anisotropyEnable(false)
            .maxAnisotropy(1.0f)
            .compareEnable(false)
            .compareOp(VK_COMPARE_OP_ALWAYS)
            .minLod(0f)
            .maxLod((mipLevels - 1).coerceAtLeast(0))
            .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
            .unnormalizedCoordinates(false)

        val pSampler = stack.mallocLong(1)
        check(vkCreateSampler(device, createInfo, null, pSampler) == VK_SUCCESS) { "Failed to create cube sampler" }
        pSampler[0]
    }

    private data class CubeMipData(
        val allBytes: ByteArray,
        val bytesPerTexel: Int,
        val levelCount: Int,
        val totalBytes: Int
    )

    private fun collectMipData(cube: CubeTexture): CubeMipData {
        val bytesPerTexel = when (cube.format) {
            io.kreekt.renderer.TextureFormat.RGBA16F -> 8
            io.kreekt.renderer.TextureFormat.RGBA32F -> 16
            else -> 4
        }

        val levelData = mutableListOf<ByteArray>()

        fun appendLevel(faces: Array<ByteArray>, size: Int) {
            // Flatten face data for this level
            faces.forEach { data ->
                require(data.size == size * size * bytesPerTexel) {
                    "Unexpected data size for cube mip level"
                }
                levelData.add(data)
            }
        }

        val baseFaces = Array(6) { faceIndex ->
            val face = CubeFace.values()[faceIndex]
            cube.getFaceData(face) ?: cube.getFaceFloatData(face)?.let { floats ->
                floats.toByteArray()
            } ?: throw IllegalStateException("Cube texture missing face data for ${face.name}")
        }
        appendLevel(baseFaces, cube.size)

        val mipmaps = cube.mipmaps
        if (mipmaps is Array<*>) {
            var size = cube.size
            mipmaps.forEach { level ->
                size = maxOf(1, size / 2)
                if (level is Array<*>) {
                    val faces = Array(6) { faceIndex ->
                        val element = level[faceIndex]
                        when (element) {
                            is ByteArray -> element
                            is FloatArray -> element.toByteArray()
                            else -> throw IllegalStateException("Unsupported mip data type: ${element?.javaClass}")
                        }
                    }
                    appendLevel(faces, size)
                }
            }
        }

        val totalBytes = levelData.sumOf { it.size }
        val merged = ByteArray(totalBytes)
        var offset = 0
        levelData.forEach { chunk ->
            System.arraycopy(chunk, 0, merged, offset, chunk.size)
            offset += chunk.size
        }
        return CubeMipData(
            allBytes = merged,
            bytesPerTexel = bytesPerTexel,
            levelCount = levelData.size / 6,
            totalBytes = totalBytes
        )
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val bytes = ByteArray(size * 4)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(this)
        return bytes
    }

    private fun findMemoryType(typeFilter: Int, properties: Int): Int {
        MemoryStack.stackPush().use { stack ->
            val memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack)
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)
            for (i in 0 until memProperties.memoryTypeCount()) {
                val hasType = (typeFilter and (1 shl i)) != 0
                val hasProperties = (memProperties.memoryTypes(i).propertyFlags() and properties) == properties
                if (hasType && hasProperties) {
                    return i
                }
            }
        }
        throw RuntimeException("Failed to find suitable memory type")
    }

    private fun transitionImageLayout(
        commandBuffer: VkCommandBuffer,
        stack: MemoryStack,
        image: Long,
        oldLayout: Int,
        newLayout: Int,
        mipLevels: Int,
        baseMip: Int,
        levelCount: Int,
        layerCount: Int
    ) {
        val barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image)
            .subresourceRange { range ->
                range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(baseMip)
                    .levelCount(levelCount)
                    .baseArrayLayer(0)
                    .layerCount(layerCount)
            }

        var sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        var destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT

        when {
            oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier.srcAccessMask(0)
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            }

            oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
            }

            else -> throw IllegalArgumentException("Unsupported layout transition: $oldLayout -> $newLayout")
        }

        vkCmdPipelineBarrier(
            commandBuffer,
            sourceStage,
            destinationStage,
            0,
            null,
            null,
            barrier
        )
    }

    private fun executeSingleTimeCommands(block: (VkCommandBuffer, MemoryStack) -> Unit) {
        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)

            val pCommandBuffer = stack.mallocPointer(1)
            check(vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer) == VK_SUCCESS) {
                "Failed to allocate command buffer"
            }
            val commandBuffer = VkCommandBuffer(pCommandBuffer[0], device)

            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            check(vkBeginCommandBuffer(commandBuffer, beginInfo) == VK_SUCCESS) {
                "Failed to begin command buffer"
            }

            block(commandBuffer, stack)

            check(vkEndCommandBuffer(commandBuffer) == VK_SUCCESS) {
                "Failed to record command buffer"
            }

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(commandBuffer.address()))

            check(vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE) == VK_SUCCESS) {
                "Failed to submit cube texture commands"
            }
            vkQueueWaitIdle(graphicsQueue)
            vkFreeCommandBuffers(device, commandPool, commandBuffer)
        }
    }

    private fun toVulkanFilter(filter: io.kreekt.renderer.TextureFilter): Int = when (filter) {
        io.kreekt.renderer.TextureFilter.NEAREST,
        io.kreekt.renderer.TextureFilter.NEAREST_MIPMAP_NEAREST,
        io.kreekt.renderer.TextureFilter.NEAREST_MIPMAP_LINEAR -> VK_FILTER_NEAREST
        else -> VK_FILTER_LINEAR
    }

    private fun toVulkanMipmapMode(filter: io.kreekt.renderer.TextureFilter): Int = when (filter) {
        io.kreekt.renderer.TextureFilter.NEAREST,
        io.kreekt.renderer.TextureFilter.LINEAR -> VK_SAMPLER_MIPMAP_MODE_NEAREST
        io.kreekt.renderer.TextureFilter.NEAREST_MIPMAP_NEAREST,
        io.kreekt.renderer.TextureFilter.LINEAR_MIPMAP_NEAREST -> VK_SAMPLER_MIPMAP_MODE_NEAREST
        else -> VK_SAMPLER_MIPMAP_MODE_LINEAR
    }

    private fun toVulkanAddressMode(wrap: io.kreekt.renderer.TextureWrap): Int = when (wrap) {
        io.kreekt.renderer.TextureWrap.REPEAT -> VK_SAMPLER_ADDRESS_MODE_REPEAT
        io.kreekt.renderer.TextureWrap.MIRRORED_REPEAT -> VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT
        io.kreekt.renderer.TextureWrap.CLAMP_TO_EDGE -> VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
    }

    companion object {
        private const val MAX_ENV_DESCRIPTOR_SETS = 4
    }
}
