package io.materia.renderer.vulkan

import io.materia.renderer.CubeFace
import io.materia.renderer.CubeTextureImpl
import io.materia.renderer.Texture2D
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.material.MaterialBinding
import io.materia.renderer.material.MaterialBindingSource
import io.materia.renderer.material.MaterialBindingType
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK12.VK_ACCESS_SHADER_READ_BIT
import org.lwjgl.vulkan.VK12.VK_ACCESS_TRANSFER_WRITE_BIT
import org.lwjgl.vulkan.VK12.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE
import org.lwjgl.vulkan.VK12.VK_BORDER_COLOR_INT_OPAQUE_BLACK
import org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_TRANSFER_SRC_BIT
import org.lwjgl.vulkan.VK12.VK_COMMAND_BUFFER_LEVEL_PRIMARY
import org.lwjgl.vulkan.VK12.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT
import org.lwjgl.vulkan.VK12.VK_COMPARE_OP_ALWAYS
import org.lwjgl.vulkan.VK12.VK_COMPONENT_SWIZZLE_IDENTITY
import org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
import org.lwjgl.vulkan.VK12.VK_DESCRIPTOR_TYPE_SAMPLER
import org.lwjgl.vulkan.VK12.VK_FILTER_LINEAR
import org.lwjgl.vulkan.VK12.VK_FILTER_NEAREST
import org.lwjgl.vulkan.VK12.VK_FORMAT_R16G16B16A16_SFLOAT
import org.lwjgl.vulkan.VK12.VK_FORMAT_R16G16_SFLOAT
import org.lwjgl.vulkan.VK12.VK_FORMAT_R32G32B32A32_SFLOAT
import org.lwjgl.vulkan.VK12.VK_FORMAT_R32G32_SFLOAT
import org.lwjgl.vulkan.VK12.VK_FORMAT_R8G8B8A8_UNORM
import org.lwjgl.vulkan.VK12.VK_IMAGE_ASPECT_COLOR_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
import org.lwjgl.vulkan.VK12.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
import org.lwjgl.vulkan.VK12.VK_IMAGE_LAYOUT_UNDEFINED
import org.lwjgl.vulkan.VK12.VK_IMAGE_TILING_OPTIMAL
import org.lwjgl.vulkan.VK12.VK_IMAGE_TYPE_2D
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_SAMPLED_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_USAGE_TRANSFER_DST_BIT
import org.lwjgl.vulkan.VK12.VK_IMAGE_VIEW_TYPE_2D
import org.lwjgl.vulkan.VK12.VK_IMAGE_VIEW_TYPE_CUBE
import org.lwjgl.vulkan.VK12.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
import org.lwjgl.vulkan.VK12.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
import org.lwjgl.vulkan.VK12.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
import org.lwjgl.vulkan.VK12.VK_NULL_HANDLE
import org.lwjgl.vulkan.VK12.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT
import org.lwjgl.vulkan.VK12.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
import org.lwjgl.vulkan.VK12.VK_PIPELINE_STAGE_TRANSFER_BIT
import org.lwjgl.vulkan.VK12.VK_QUEUE_FAMILY_IGNORED
import org.lwjgl.vulkan.VK12.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE
import org.lwjgl.vulkan.VK12.VK_SAMPLER_MIPMAP_MODE_LINEAR
import org.lwjgl.vulkan.VK12.VK_SAMPLER_MIPMAP_MODE_NEAREST
import org.lwjgl.vulkan.VK12.VK_SAMPLE_COUNT_1_BIT
import org.lwjgl.vulkan.VK12.VK_SHADER_STAGE_FRAGMENT_BIT
import org.lwjgl.vulkan.VK12.VK_SHARING_MODE_EXCLUSIVE
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_SUBMIT_INFO
import org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET
import org.lwjgl.vulkan.VK12.VK_SUCCESS
import org.lwjgl.vulkan.VK12.vkAllocateCommandBuffers
import org.lwjgl.vulkan.VK12.vkAllocateDescriptorSets
import org.lwjgl.vulkan.VK12.vkAllocateMemory
import org.lwjgl.vulkan.VK12.vkBeginCommandBuffer
import org.lwjgl.vulkan.VK12.vkBindBufferMemory
import org.lwjgl.vulkan.VK12.vkBindImageMemory
import org.lwjgl.vulkan.VK12.vkCmdCopyBufferToImage
import org.lwjgl.vulkan.VK12.vkCmdPipelineBarrier
import org.lwjgl.vulkan.VK12.vkCreateBuffer
import org.lwjgl.vulkan.VK12.vkCreateDescriptorPool
import org.lwjgl.vulkan.VK12.vkCreateDescriptorSetLayout
import org.lwjgl.vulkan.VK12.vkCreateImage
import org.lwjgl.vulkan.VK12.vkCreateImageView
import org.lwjgl.vulkan.VK12.vkCreateSampler
import org.lwjgl.vulkan.VK12.vkDestroyBuffer
import org.lwjgl.vulkan.VK12.vkDestroyDescriptorPool
import org.lwjgl.vulkan.VK12.vkDestroyDescriptorSetLayout
import org.lwjgl.vulkan.VK12.vkDestroyImage
import org.lwjgl.vulkan.VK12.vkDestroyImageView
import org.lwjgl.vulkan.VK12.vkDestroySampler
import org.lwjgl.vulkan.VK12.vkEndCommandBuffer
import org.lwjgl.vulkan.VK12.vkFreeCommandBuffers
import org.lwjgl.vulkan.VK12.vkFreeMemory
import org.lwjgl.vulkan.VK12.vkGetBufferMemoryRequirements
import org.lwjgl.vulkan.VK12.vkGetImageMemoryRequirements
import org.lwjgl.vulkan.VK12.vkGetPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VK12.vkMapMemory
import org.lwjgl.vulkan.VK12.vkQueueSubmit
import org.lwjgl.vulkan.VK12.vkQueueWaitIdle
import org.lwjgl.vulkan.VK12.vkUnmapMemory
import org.lwjgl.vulkan.VK12.vkUpdateDescriptorSets
import org.lwjgl.vulkan.VkBufferCreateInfo
import org.lwjgl.vulkan.VkBufferImageCopy
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkComponentMapping
import org.lwjgl.vulkan.VkDescriptorImageInfo
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo
import org.lwjgl.vulkan.VkDescriptorPoolSize
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkImageCreateInfo
import org.lwjgl.vulkan.VkImageMemoryBarrier
import org.lwjgl.vulkan.VkImageSubresourceRange
import org.lwjgl.vulkan.VkImageViewCreateInfo
import org.lwjgl.vulkan.VkMemoryAllocateInfo
import org.lwjgl.vulkan.VkMemoryRequirements
import org.lwjgl.vulkan.VkPhysicalDevice
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkSamplerCreateInfo
import org.lwjgl.vulkan.VkSubmitInfo
import org.lwjgl.vulkan.VkWriteDescriptorSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import io.materia.renderer.CubeTexture as RendererCubeTexture

internal class VulkanEnvironmentManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val commandPool: Long,
    private val graphicsQueue: VkQueue,
    private val bindingLayout: List<MaterialBinding>
) {

    data class EnvironmentBinding(
        val descriptorSet: Long,
        val layout: Long,
        val mipCount: Int,
        val usingFallbackEnvironment: Boolean,
        val usingFallbackBrdf: Boolean
    )

    private data class CubeResource(
        val image: Long,
        val memory: Long,
        val view: Long,
        val sampler: Long,
        val mipLevels: Int
    ) {
        fun destroy(device: VkDevice) {
            vkDestroySampler(device, sampler, null)
            vkDestroyImageView(device, view, null)
            vkDestroyImage(device, image, null)
            vkFreeMemory(device, memory, null)
        }
    }

    private data class BrdfResource(
        val image: Long,
        val memory: Long,
        val view: Long,
        val sampler: Long
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
    private var brdfResource: BrdfResource? = null
    private var descriptorSet: Long = VK_NULL_HANDLE
    private var lastTextureId: Int = -1
    private var lastVersion: Int = -1
    private var mipCount: Int = 1
    private var lastBrdfTextureId: Int = -1
    private var lastBrdfVersion: Int = -1
    private var fallbackCubeTexture: CubeTextureImpl? = null
    private var fallbackBrdfTexture: Texture2D? = null
    private var fallbackCubeResource: CubeResource? = null
    private var fallbackBrdfResource: BrdfResource? = null

    fun prepare(cube: RendererCubeTexture?, brdfLut: Texture2D?): EnvironmentBinding? {
        if (descriptorSetLayout == VK_NULL_HANDLE) {
            createDescriptorSetLayout()
        }
        if (descriptorPool == VK_NULL_HANDLE) {
            createDescriptorPool()
        }

        if (descriptorSet == VK_NULL_HANDLE) {
            descriptorSet = allocateDescriptorSet()
        }

        val dataCube = cube as? CubeTextureImpl
        val usingFallbackCube = dataCube == null
        val effectiveCube = dataCube ?: ensureFallbackCube()

        val sourceId = dataCube?.id ?: FALLBACK_TEXTURE_ID
        val sourceVersion = dataCube?.version ?: FALLBACK_VERSION

        val needsUpload = sourceId != lastTextureId ||
                sourceVersion != lastVersion ||
                cubeResource == null
        if (needsUpload) {
            disposeCube()
            cubeResource = if (usingFallbackCube) {
                fallbackCubeResource
                    ?: uploadCubeTexture(effectiveCube).also { fallbackCubeResource = it }
            } else {
                uploadCubeTexture(effectiveCube)
            }
            lastTextureId = sourceId
            lastVersion = sourceVersion
            mipCount = cubeResource?.mipLevels ?: 1
        } else if (usingFallbackCube && cubeResource == null) {
            cubeResource = fallbackCubeResource
            mipCount = cubeResource?.mipLevels ?: 1
        }

        val dataBrdf = brdfLut as? Texture2D
        val brdfSourceId = dataBrdf?.id ?: FALLBACK_BRDF_ID
        val brdfSourceVersion = dataBrdf?.version ?: FALLBACK_VERSION

        val needsBrdfUpload = brdfSourceId != lastBrdfTextureId ||
                brdfSourceVersion != lastBrdfVersion ||
                brdfResource == null
        if (needsBrdfUpload) {
            brdfResource?.let { resource ->
                if (resource !== fallbackBrdfResource) {
                    resource.destroy(device)
                }
            }
            brdfResource = if (dataBrdf == null) {
                fallbackBrdfResource
                    ?: uploadBrdfTexture(ensureFallbackBrdf()).also { fallbackBrdfResource = it }
            } else {
                uploadBrdfTexture(dataBrdf)
            }
            lastBrdfTextureId = brdfSourceId
            lastBrdfVersion = brdfSourceVersion
        }

        val resource = cubeResource ?: fallbackCubeResource ?: return null
        val brdf = brdfResource ?: fallbackBrdfResource ?: return null
        updateDescriptorSet(descriptorSet, resource, brdf)

        if (dataCube != null) {
            dataCube.needsUpdate = false
        }
        if (dataBrdf != null) {
            dataBrdf.needsUpdate = false
        }

        return EnvironmentBinding(
            descriptorSet = descriptorSet,
            layout = descriptorSetLayout,
            mipCount = resource.mipLevels,
            usingFallbackEnvironment = usingFallbackCube,
            usingFallbackBrdf = dataBrdf == null
        )
    }

    fun dispose() {
        disposeCube()
        disposeBrdf()
        fallbackCubeResource?.destroy(device)
        fallbackCubeResource = null
        fallbackBrdfResource?.destroy(device)
        fallbackBrdfResource = null
        fallbackCubeTexture = null
        fallbackBrdfTexture = null
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
        lastBrdfTextureId = -1
        lastBrdfVersion = -1
        descriptorSet = VK_NULL_HANDLE
    }

    private fun disposeCube() {
        cubeResource?.let { resource ->
            if (resource !== fallbackCubeResource) {
                resource.destroy(device)
            }
        }
        cubeResource = null
        lastTextureId = -1
        lastVersion = -1
    }

    private fun disposeBrdf() {
        brdfResource?.let { resource ->
            if (resource !== fallbackBrdfResource) {
                resource.destroy(device)
            }
        }
        brdfResource = null
        lastBrdfTextureId = -1
        lastBrdfVersion = -1
    }

    private fun createDescriptorSetLayout() {
        MemoryStack.stackPush().use { stack ->
            val bindings = VkDescriptorSetLayoutBinding.calloc(bindingLayout.size, stack)

            bindingLayout.forEachIndexed { index, binding ->
                bindings[index]
                    .binding(binding.binding)
                    .descriptorCount(1)
                    .descriptorType(binding.type.toVulkanDescriptorType())
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
            }

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
            val sampledCount = bindingLayout.count { it.type != MaterialBindingType.SAMPLER }
            val samplerCount = bindingLayout.count { it.type == MaterialBindingType.SAMPLER }
            val poolSizeCount = (if (sampledCount > 0) 1 else 0) + (if (samplerCount > 0) 1 else 0)

            val poolSizes = VkDescriptorPoolSize.calloc(poolSizeCount, stack)
            var poolIndex = 0
            if (sampledCount > 0) {
                poolSizes[poolIndex]
                    .type(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                    .descriptorCount(MAX_ENV_DESCRIPTOR_SETS * sampledCount)
                poolIndex += 1
            }
            if (samplerCount > 0) {
                poolSizes[poolIndex]
                    .type(VK_DESCRIPTOR_TYPE_SAMPLER)
                    .descriptorCount(MAX_ENV_DESCRIPTOR_SETS * samplerCount)
            }

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

    private fun uploadCubeTexture(cube: CubeTextureImpl): CubeResource {
        val mipData = collectMipData(cube)
        val format = when (cube.format) {
            io.materia.renderer.TextureFormat.RGBA8,
            io.materia.renderer.TextureFormat.SRGB8_ALPHA8 -> VK_FORMAT_R8G8B8A8_UNORM

            io.materia.renderer.TextureFormat.RGBA16F -> VK_FORMAT_R16G16B16A16_SFLOAT
            io.materia.renderer.TextureFormat.RGBA32F -> VK_FORMAT_R32G32B32A32_SFLOAT
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
                .memoryTypeIndex(
                    findMemoryType(
                        stagingRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                )

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
            check(
                vkCreateImage(
                    device,
                    imageInfo,
                    null,
                    pImage
                ) == VK_SUCCESS
            ) { "Failed to create cube image" }
            val image = pImage[0]

            val memRequirements = VkMemoryRequirements.malloc(stack)
            vkGetImageMemoryRequirements(device, image, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        memRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    )
                )

            val pMemory = stack.mallocLong(1)
            check(
                vkAllocateMemory(
                    device,
                    allocInfo,
                    null,
                    pMemory
                ) == VK_SUCCESS
            ) { "Failed to allocate cube image memory" }
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
            return CubeResource(
                image = image,
                memory = imageMemory,
                view = imageView,
                sampler = sampler,
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

    private fun updateDescriptorSet(descriptorSet: Long, cube: CubeResource, brdf: BrdfResource) {
        MemoryStack.stackPush().use { stack ->
            fun sampledCube(resource: CubeResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .imageView(resource.view)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .sampler(VK_NULL_HANDLE)
                return info
            }

            fun sampledBrdf(resource: BrdfResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .imageView(resource.view)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .sampler(VK_NULL_HANDLE)
                return info
            }

            fun samplerCube(resource: CubeResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .sampler(resource.sampler)
                    .imageView(VK_NULL_HANDLE)
                    .imageLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                return info
            }

            fun samplerBrdf(resource: BrdfResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .sampler(resource.sampler)
                    .imageView(VK_NULL_HANDLE)
                    .imageLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                return info
            }

            val writes = VkWriteDescriptorSet.calloc(bindingLayout.size, stack)
            bindingLayout.forEachIndexed { index, binding ->
                val descriptorType = binding.type.toVulkanDescriptorType()
                val imageInfo = when (binding.source) {
                    MaterialBindingSource.ENVIRONMENT_PREFILTER -> when (binding.type) {
                        MaterialBindingType.SAMPLER -> samplerCube(cube)
                        else -> sampledCube(cube)
                    }

                    MaterialBindingSource.ENVIRONMENT_BRDF -> when (binding.type) {
                        MaterialBindingType.SAMPLER -> samplerBrdf(brdf)
                        else -> sampledBrdf(brdf)
                    }

                    else -> throw IllegalStateException("Unsupported environment binding source: ${binding.source}")
                }

                writes[index]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(binding.binding)
                    .descriptorType(descriptorType)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo)
            }

            vkUpdateDescriptorSets(device, writes, null)
        }
    }

    private fun createImageView(image: Long, format: Int, mipLevels: Int): Long =
        MemoryStack.stackPush().use { stack ->
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
            check(
                vkCreateImageView(
                    device,
                    createInfo,
                    null,
                    pView
                ) == VK_SUCCESS
            ) { "Failed to create cube image view" }
            pView[0]
        }

    private fun createSampler(cube: CubeTextureImpl, mipLevels: Int): Long =
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(toVulkanFilter(cube.filter))
                .minFilter(toVulkanFilter(cube.filter))
                .mipmapMode(toVulkanMipmapMode(cube.filter))
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .compareEnable(false)
                .compareOp(VK_COMPARE_OP_ALWAYS)
                .minLod(0f)
                .maxLod((mipLevels - 1).coerceAtLeast(0).toFloat())
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)

            val pSampler = stack.mallocLong(1)
            check(
                vkCreateSampler(
                    device,
                    createInfo,
                    null,
                    pSampler
                ) == VK_SUCCESS
            ) { "Failed to create cube sampler" }
            pSampler[0]
        }

    private data class CubeMipData(
        val allBytes: ByteArray,
        val bytesPerTexel: Int,
        val levelCount: Int,
        val totalBytes: Int
    )

    private fun collectMipData(cube: CubeTextureImpl): CubeMipData {
        val format = cube.format
        val bytesPerTexel = format.bytesPerTexel()
        val componentsPerTexel = format.componentCount()
        val faces = CubeFace.values()
        val converter = format.byteConverter()

        val levelData = mutableListOf<ByteArray>()

        fun appendLevel(level: Int, size: Int) {
            faces.forEach { face ->
                val floats = cube.getFaceFloatData(face, level)
                    ?: throw IllegalStateException("Cube texture missing face data for ${face.name} at mip $level")
                val expectedFloatCount = size * size * componentsPerTexel
                require(floats.size == expectedFloatCount) {
                    "Unexpected float count for cube mip level (expected $expectedFloatCount, got ${floats.size})"
                }
                val bytes = converter(floats)
                require(bytes.size == size * size * bytesPerTexel) {
                    "Unexpected byte size for cube mip level (expected ${size * size * bytesPerTexel}, got ${bytes.size})"
                }
                levelData.add(bytes)
            }
        }

        appendLevel(level = 0, size = cube.size)

        val maxMipLevel = cube.maxMipLevel()
        for (level in 1..maxMipLevel) {
            val size = maxOf(1, cube.size shr level)
            appendLevel(level, size)
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
            levelCount = maxOf(1, maxMipLevel + 1),
            totalBytes = totalBytes
        )
    }

    private fun TextureFormat.componentCount(): Int = when (this) {
        TextureFormat.RGBA8, TextureFormat.RGBA16F, TextureFormat.RGBA32F,
        TextureFormat.RGBA8UI, TextureFormat.RGBA16UI, TextureFormat.RGBA32UI,
        TextureFormat.SRGB8_ALPHA8 -> 4

        TextureFormat.RGB8, TextureFormat.RGB16F, TextureFormat.RGB32F,
        TextureFormat.RGB8UI, TextureFormat.RGB16UI, TextureFormat.RGB32UI,
        TextureFormat.SRGB8 -> 3

        TextureFormat.RG8, TextureFormat.RG16F, TextureFormat.RG32F,
        TextureFormat.RG8UI, TextureFormat.RG16UI, TextureFormat.RG32UI -> 2

        else -> 1
    }

    private fun TextureFormat.bytesPerComponent(): Int = when (this) {
        TextureFormat.RGBA16F, TextureFormat.RGB16F, TextureFormat.RG16F,
        TextureFormat.R16F -> 2

        TextureFormat.RGBA32F, TextureFormat.RGB32F, TextureFormat.RG32F,
        TextureFormat.R32F -> 4

        TextureFormat.RGBA16UI, TextureFormat.RGB16UI, TextureFormat.RG16UI,
        TextureFormat.R16UI -> 2

        TextureFormat.RGBA32UI, TextureFormat.RGB32UI, TextureFormat.RG32UI,
        TextureFormat.R32UI -> 4

        else -> 1
    }

    private fun TextureFormat.bytesPerTexel(): Int = componentCount() * bytesPerComponent()

    private fun TextureFormat.byteConverter(): (FloatArray) -> ByteArray = when (this) {
        TextureFormat.RGBA16F, TextureFormat.RGB16F, TextureFormat.RG16F,
        TextureFormat.R16F -> { data -> data.toHalfByteArray() }

        TextureFormat.RGBA32F, TextureFormat.RGB32F, TextureFormat.RG32F,
        TextureFormat.R32F -> { data -> data.toFloat32ByteArray() }

        TextureFormat.RGBA8, TextureFormat.RGB8, TextureFormat.RG8, TextureFormat.R8,
        TextureFormat.SRGB8, TextureFormat.SRGB8_ALPHA8 -> { data -> data.toUnormByteArray() }

        else -> { data -> data.toFloat32ByteArray() }
    }

    private fun uploadBrdfTexture(texture: Texture2D): BrdfResource {
        val format = when (texture.format) {
            TextureFormat.RG32F -> VK_FORMAT_R32G32_SFLOAT
            TextureFormat.RGBA8, TextureFormat.SRGB8_ALPHA8 -> VK_FORMAT_R8G8B8A8_UNORM
            TextureFormat.RGBA16F -> VK_FORMAT_R16G16B16A16_SFLOAT
            TextureFormat.RGBA32F -> VK_FORMAT_R32G32B32A32_SFLOAT
            TextureFormat.RG16F -> VK_FORMAT_R16G16_SFLOAT
            else -> VK_FORMAT_R32G32_SFLOAT
        }

        val pixelData = texture.getData()?.let { floats ->
            val buffer = MemoryUtil.memAlloc(floats.size * 4)
            buffer.asFloatBuffer().put(floats).flip()
            buffer
        } ?: throw IllegalStateException("BRDF texture has no data to upload")

        val imageSize = pixelData.remaining().toLong()

        MemoryStack.stackPush().use { stack ->
            val stagingBufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(imageSize)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val pStagingBuffer = stack.mallocLong(1)
            check(vkCreateBuffer(device, stagingBufferInfo, null, pStagingBuffer) == VK_SUCCESS) {
                "Failed to create BRDF staging buffer"
            }
            val stagingBuffer = pStagingBuffer[0]

            val stagingRequirements = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device, stagingBuffer, stagingRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(stagingRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        stagingRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                    )
                )

            val pStagingMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, allocInfo, null, pStagingMemory) == VK_SUCCESS) {
                "Failed to allocate BRDF staging memory"
            }
            val stagingMemory = pStagingMemory[0]
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)

            val ppData = stack.mallocPointer(1)
            vkMapMemory(device, stagingMemory, 0, imageSize, 0, ppData)
            val mapped = ppData.getByteBuffer(0, imageSize.toInt())
            mapped.put(pixelData).flip()
            vkUnmapMemory(device, stagingMemory)
            MemoryUtil.memFree(pixelData)

            val imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .extent { it.width(texture.width).height(texture.height).depth(1) }
                .mipLevels(1)
                .arrayLayers(1)
                .format(format)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(VK_IMAGE_USAGE_SAMPLED_BIT or VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(VK_SAMPLE_COUNT_1_BIT)

            val pImage = stack.mallocLong(1)
            check(
                vkCreateImage(
                    device,
                    imageInfo,
                    null,
                    pImage
                ) == VK_SUCCESS
            ) { "Failed to create BRDF image" }
            val image = pImage[0]

            val memRequirements = VkMemoryRequirements.malloc(stack)
            vkGetImageMemoryRequirements(device, image, memRequirements)

            val imageAllocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    findMemoryType(
                        memRequirements.memoryTypeBits(),
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
                    )
                )

            val pImageMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, imageAllocInfo, null, pImageMemory) == VK_SUCCESS) {
                "Failed to allocate BRDF image memory"
            }
            val imageMemory = pImageMemory[0]
            vkBindImageMemory(device, image, imageMemory, 0)

            executeSingleTimeCommands { cmd, innerStack ->
                transitionImageLayout(
                    cmd,
                    innerStack,
                    image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    1,
                    0,
                    1,
                    1
                )

                val region = VkBufferImageCopy.calloc(1, innerStack)
                region[0]
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource { sub ->
                        sub.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0)
                            .baseArrayLayer(0)
                            .layerCount(1)
                    }
                    .imageOffset { it.x(0).y(0).z(0) }
                    .imageExtent { it.width(texture.width).height(texture.height).depth(1) }

                vkCmdCopyBufferToImage(
                    cmd,
                    stagingBuffer,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region
                )

                transitionImageLayout(
                    cmd,
                    innerStack,
                    image,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    1,
                    0,
                    1,
                    1
                )
            }

            vkDestroyBuffer(device, stagingBuffer, null)
            vkFreeMemory(device, stagingMemory, null)

            val view = MemoryStack.stackPush().use { viewStack ->
                val subresourceRange = VkImageSubresourceRange.calloc(viewStack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1)

                val createInfo = VkImageViewCreateInfo.calloc(viewStack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format)
                    .subresourceRange(subresourceRange)

                val pView = viewStack.mallocLong(1)
                check(
                    vkCreateImageView(
                        device,
                        createInfo,
                        null,
                        pView
                    ) == VK_SUCCESS
                ) { "Failed to create BRDF image view" }
                pView[0]
            }

            val sampler = MemoryStack.stackPush().use { samplerStack ->
                val samplerInfo = VkSamplerCreateInfo.calloc(samplerStack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .anisotropyEnable(false)
                    .maxAnisotropy(1.0f)
                    .compareEnable(false)
                    .compareOp(VK_COMPARE_OP_ALWAYS)
                    .minLod(0f)
                    .maxLod(0f)
                    .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                    .unnormalizedCoordinates(false)

                val pSampler = samplerStack.mallocLong(1)
                check(
                    vkCreateSampler(
                        device,
                        samplerInfo,
                        null,
                        pSampler
                    ) == VK_SUCCESS
                ) { "Failed to create BRDF sampler" }
                pSampler[0]
            }

            return BrdfResource(
                image = image,
                memory = imageMemory,
                view = view,
                sampler = sampler
            )
        }
    }

    private fun MaterialBindingType.toVulkanDescriptorType(): Int = when (this) {
        MaterialBindingType.TEXTURE_2D,
        MaterialBindingType.TEXTURE_CUBE -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE

        MaterialBindingType.SAMPLER -> VK_DESCRIPTOR_TYPE_SAMPLER
    }

    private fun ensureFallbackCube(): CubeTextureImpl {
        fallbackCubeTexture?.let { return it }

        val cube = CubeTextureImpl(
            size = 1,
            format = TextureFormat.RGBA8,
            filter = TextureFilter.LINEAR,
            textureName = "FallbackEnvironment"
        )
        val faceData = FloatArray(4) { index -> if (index == 3) 1f else 0f }
        CubeFace.values().forEach { face ->
            cube.setFaceData(face, faceData.copyOf())
        }
        cube.needsUpdate = false
        cube.version = FALLBACK_VERSION
        fallbackCubeTexture = cube
        return cube
    }

    private fun ensureFallbackBrdf(): Texture2D {
        fallbackBrdfTexture?.let { return it }
        val fallbackWidth = 32
        val fallbackHeight = 32
        val texture = Texture2D(
            width = fallbackWidth,
            height = fallbackHeight,
            format = TextureFormat.RG32F,
            filter = TextureFilter.LINEAR,
            generateMipmaps = false,
            textureName = "FallbackBrdfLut"
        )
        val data = FloatArray(fallbackWidth * fallbackHeight * 2)
        for (i in data.indices step 2) {
            data[i] = 0f
            data[i + 1] = 1f
        }
        texture.setData(data)
        texture.needsUpdate = false
        texture.version = FALLBACK_VERSION
        fallbackBrdfTexture = texture
        return texture
    }

    private fun FloatArray.toFloat32ByteArray(): ByteArray {
        val bytes = ByteArray(size * 4)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        byteBuffer.asFloatBuffer().put(this)
        return bytes
    }

    private fun FloatArray.toHalfByteArray(): ByteArray {
        val bytes = ByteArray(size * 2)
        var index = 0
        for (value in this) {
            val half = value.toHalfBits().toInt() and 0xFFFF
            bytes[index++] = (half and 0xFF).toByte()
            bytes[index++] = ((half ushr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun FloatArray.toUnormByteArray(): ByteArray {
        val bytes = ByteArray(size)
        for (i in indices) {
            val intValue = (this[i].coerceIn(0f, 1f) * 255f + 0.5f).roundToInt()
            bytes[i] = (intValue and 0xFF).toByte()
        }
        return bytes
    }

    private fun Float.toHalfBits(): Short {
        val bits = java.lang.Float.floatToIntBits(this)
        val sign = (bits ushr 16) and 0x8000
        var exponent = (bits ushr 23) and 0xFF
        var mantissa = bits and 0x7FFFFF

        return when {
            exponent == 0xFF -> {
                if (mantissa == 0) {
                    (sign or 0x7C00).toShort()
                } else {
                    (sign or 0x7C00 or ((mantissa ushr 13) and 0x3FF)).toShort()
                }
            }

            exponent > 0x70 -> {
                (sign or 0x7C00).toShort()
            }

            exponent < 0x71 -> {
                if (exponent < 0x67) {
                    sign.toShort()
                } else {
                    mantissa = mantissa or 0x00800000
                    val shift = 0x71 - exponent
                    var halfMantissa = mantissa ushr (shift + 13)
                    if (((mantissa ushr (shift + 12)) and 0x1) != 0) {
                        halfMantissa += 1
                    }
                    (sign or (halfMantissa and 0x03FF)).toShort()
                }
            }

            else -> {
                val halfExponent = exponent - 0x70
                var halfMantissa = mantissa ushr 13
                if ((mantissa and 0x00001000) != 0) {
                    halfMantissa += 1
                    if (halfMantissa == 0x0400) {
                        val incremented = ((sign or ((halfExponent + 1) shl 10)) and 0xFFFF)
                        return incremented.toShort()
                    }
                }
                (sign or (halfExponent shl 10) or (halfMantissa and 0x03FF)).toShort()
            }
        }
    }

    private fun findMemoryType(typeFilter: Int, properties: Int): Int {
        MemoryStack.stackPush().use { stack ->
            val memProperties = VkPhysicalDeviceMemoryProperties.malloc(stack)
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties)
            for (i in 0 until memProperties.memoryTypeCount()) {
                val hasType = (typeFilter and (1 shl i)) != 0
                val hasProperties =
                    (memProperties.memoryTypes(i).propertyFlags() and properties) == properties
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

    private fun toVulkanFilter(filter: io.materia.renderer.TextureFilter): Int = when (filter) {
        io.materia.renderer.TextureFilter.NEAREST,
        io.materia.renderer.TextureFilter.NEAREST_MIPMAP_NEAREST,
        io.materia.renderer.TextureFilter.NEAREST_MIPMAP_LINEAR -> VK_FILTER_NEAREST

        else -> VK_FILTER_LINEAR
    }

    private fun toVulkanMipmapMode(filter: io.materia.renderer.TextureFilter): Int = when (filter) {
        io.materia.renderer.TextureFilter.NEAREST,
        io.materia.renderer.TextureFilter.LINEAR -> VK_SAMPLER_MIPMAP_MODE_NEAREST

        io.materia.renderer.TextureFilter.NEAREST_MIPMAP_NEAREST,
        io.materia.renderer.TextureFilter.LINEAR_MIPMAP_NEAREST -> VK_SAMPLER_MIPMAP_MODE_NEAREST

        else -> VK_SAMPLER_MIPMAP_MODE_LINEAR
    }

    companion object {
        private const val MAX_ENV_DESCRIPTOR_SETS = 4
        private const val FALLBACK_TEXTURE_ID = -2
        private const val FALLBACK_VERSION = -1
        private const val FALLBACK_BRDF_ID = -3
    }
}
