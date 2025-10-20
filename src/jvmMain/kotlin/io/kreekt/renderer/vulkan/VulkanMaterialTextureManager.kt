package io.kreekt.renderer.vulkan

import io.kreekt.core.math.Color
import io.kreekt.core.scene.Material
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.material.MeshStandardMaterial
import io.kreekt.renderer.feature020.OutOfMemoryException
import io.kreekt.renderer.material.MaterialBinding
import io.kreekt.renderer.material.MaterialBindingSource
import io.kreekt.renderer.material.MaterialBindingType
import io.kreekt.renderer.material.MaterialDescriptor
import io.kreekt.texture.Texture
import io.kreekt.texture.Texture2D
import kotlin.math.log2
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.*

internal class VulkanMaterialTextureManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice,
    private val commandPool: Long,
    private val graphicsQueue: VkQueue,
    private val layoutBindings: List<MaterialBinding>
) {

    data class MaterialTextureBinding(
        val descriptorSet: Long
    )

    private data class VulkanTextureResource(
        val image: Long,
        val memory: Long,
        val imageView: Long,
        val sampler: Long,
        val textureId: Int,
        val version: Int
    ) {
        fun destroy(device: VkDevice) {
            vkDestroySampler(device, sampler, null)
            vkDestroyImageView(device, imageView, null)
            vkDestroyImage(device, image, null)
            vkFreeMemory(device, memory, null)
        }
    }

    private data class MaterialBindingState(
        val descriptorSet: Long,
        var albedoTextureId: Int,
        var albedoVersion: Int,
        var normalTextureId: Int,
        var normalVersion: Int,
        var roughnessTextureId: Int,
        var roughnessVersion: Int,
        var metalnessTextureId: Int,
        var metalnessVersion: Int,
        var aoTextureId: Int,
        var aoVersion: Int
    )

    private val textureCache = mutableMapOf<Int, VulkanTextureResource>()
    private val materialBindings = mutableMapOf<Int, MaterialBindingState>()

    private val fallbackAlbedo = createFallbackTexture(Texture2D.solidColor(Color.WHITE).apply { needsUpdate = false })
    private val fallbackNormal = createFallbackTexture(
        Texture2D.solidColor(Color(0.5f, 0.5f, 1f)).apply { needsUpdate = false }
    )
    private val fallbackRoughness = createFallbackTexture(Texture2D.solidColor(Color.WHITE).apply { needsUpdate = false })
    private val fallbackMetalness = createFallbackTexture(Texture2D.solidColor(Color.BLACK).apply { needsUpdate = false })
    private val fallbackAo = createFallbackTexture(Texture2D.solidColor(Color.WHITE).apply { needsUpdate = false })

    fun prepare(
        material: Material,
        descriptor: MaterialDescriptor,
        descriptorPool: Long,
        descriptorSetLayout: Long
    ): MaterialTextureBinding? {
        val materialId = material.id
        val bindingState = materialBindings.getOrPut(materialId) {
            val descriptorSet = allocateDescriptorSet(descriptorPool, descriptorSetLayout)
            MaterialBindingState(
                descriptorSet = descriptorSet,
                albedoTextureId = fallbackAlbedo.textureId,
                albedoVersion = fallbackAlbedo.version,
                normalTextureId = fallbackNormal.textureId,
                normalVersion = fallbackNormal.version,
                roughnessTextureId = fallbackRoughness.textureId,
                roughnessVersion = fallbackRoughness.version,
                metalnessTextureId = fallbackMetalness.textureId,
                metalnessVersion = fallbackMetalness.version,
                aoTextureId = fallbackAo.textureId,
                aoVersion = fallbackAo.version
            )
        }

        val albedoTexture = extractAlbedoTexture(material)
        val albedoResource = ensureTextureResource(albedoTexture, fallbackAlbedo)

        val requiresNormal = descriptor.bindings.any { it.source == MaterialBindingSource.NORMAL_MAP && it.type == MaterialBindingType.TEXTURE_2D }
        val normalTexture = if (requiresNormal) extractNormalTexture(material) else null
        val normalResource = ensureTextureResource(normalTexture, fallbackNormal)

        val requiresRoughness = descriptor.bindings.any {
            it.source == MaterialBindingSource.ROUGHNESS_MAP && it.type == MaterialBindingType.TEXTURE_2D
        }
        val roughnessTexture = if (requiresRoughness) extractRoughnessTexture(material) else null
        val roughnessResource = ensureTextureResource(roughnessTexture, fallbackRoughness)

        val requiresMetalness = descriptor.bindings.any {
            it.source == MaterialBindingSource.METALNESS_MAP && it.type == MaterialBindingType.TEXTURE_2D
        }
        val metalnessTexture = if (requiresMetalness) extractMetalnessTexture(material) else null
        val metalnessResource = ensureTextureResource(metalnessTexture, fallbackMetalness)

        val requiresAo = descriptor.bindings.any {
            it.source == MaterialBindingSource.AO_MAP && it.type == MaterialBindingType.TEXTURE_2D
        }
        val aoTexture = if (requiresAo) extractAoTexture(material) else null
        val aoResource = ensureTextureResource(aoTexture, fallbackAo)

        val texturesChanged =
            bindingState.albedoTextureId != albedoResource.textureId ||
                bindingState.albedoVersion != albedoResource.version ||
                bindingState.normalTextureId != normalResource.textureId ||
                bindingState.normalVersion != normalResource.version ||
                bindingState.roughnessTextureId != roughnessResource.textureId ||
                bindingState.roughnessVersion != roughnessResource.version ||
                bindingState.metalnessTextureId != metalnessResource.textureId ||
                bindingState.metalnessVersion != metalnessResource.version ||
                bindingState.aoTextureId != aoResource.textureId ||
                bindingState.aoVersion != aoResource.version

        if (texturesChanged) {
            updateDescriptorSet(
                bindingState.descriptorSet,
                albedoResource,
                normalResource,
                roughnessResource,
                metalnessResource,
                aoResource
            )

            bindingState.albedoTextureId = albedoResource.textureId
            bindingState.albedoVersion = albedoResource.version
            bindingState.normalTextureId = normalResource.textureId
            bindingState.normalVersion = normalResource.version
            bindingState.roughnessTextureId = roughnessResource.textureId
            bindingState.roughnessVersion = roughnessResource.version
            bindingState.metalnessTextureId = metalnessResource.textureId
            bindingState.metalnessVersion = metalnessResource.version
            bindingState.aoTextureId = aoResource.textureId
            bindingState.aoVersion = aoResource.version
        }

        return MaterialTextureBinding(bindingState.descriptorSet)
    }

    fun dispose() {
        textureCache.values.forEach { it.destroy(device) }
        textureCache.clear()
        fallbackAlbedo.destroy(device)
        fallbackNormal.destroy(device)
        fallbackRoughness.destroy(device)
        fallbackMetalness.destroy(device)
        fallbackAo.destroy(device)
        materialBindings.clear()
    }

    private fun extractAlbedoTexture(material: Material): Texture2D? = when (material) {
        is MeshBasicMaterial -> material.map as? Texture2D
        is MeshStandardMaterial -> material.map
        else -> null
    }

    private fun extractNormalTexture(material: Material): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.normalMap
        else -> null
    }

    private fun extractRoughnessTexture(material: Material): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.roughnessMap
        else -> null
    }

    private fun extractMetalnessTexture(material: Material): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.metalnessMap
        else -> null
    }

    private fun extractAoTexture(material: Material): Texture2D? = when (material) {
        is MeshStandardMaterial -> material.aoMap
        else -> null
    }

    private fun ensureTextureResource(texture: Texture?, fallback: VulkanTextureResource): VulkanTextureResource {
        val texture2D = texture as? Texture2D ?: return fallback

        val cached = textureCache[texture2D.id]
        if (cached != null && cached.version == texture2D.version && !texture2D.needsUpdate) {
            return cached
        }

        cached?.destroy(device)
        val resource = createTextureResource(texture2D)
        textureCache[texture2D.id] = resource
        texture2D.needsUpdate = false
        return resource
    }

    private fun createFallbackTexture(texture: Texture2D): VulkanTextureResource {
        texture.version = -1
        texture.needsUpdate = false
        return createTextureResource(texture)
    }

    private fun createTextureResource(texture: Texture2D): VulkanTextureResource {
        val format = when (texture.format) {
            io.kreekt.renderer.TextureFormat.RGBA8,
            io.kreekt.renderer.TextureFormat.SRGB8_ALPHA8 -> VK_FORMAT_R8G8B8A8_UNORM
            io.kreekt.renderer.TextureFormat.RGBA32F -> VK_FORMAT_R32G32B32A32_SFLOAT
            else -> VK_FORMAT_R8G8B8A8_UNORM
        }

        val pixelSize = when (format) {
            VK_FORMAT_R8G8B8A8_UNORM -> 4
            VK_FORMAT_R32G32B32A32_SFLOAT -> 16
            else -> 4
        }

        val rawData = acquireTextureData(texture, pixelSize)
        val imageSize = rawData.remaining().toLong()

        MemoryStack.stackPush().use { stack ->
            val stagingBufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(imageSize)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

            val pStagingBuffer = stack.mallocLong(1)
            check(vkCreateBuffer(device, stagingBufferInfo, null, pStagingBuffer) == VK_SUCCESS) {
                "Failed to create staging buffer"
            }
            val stagingBuffer = pStagingBuffer[0]

            val stagingMemRequirements = VkMemoryRequirements.malloc(stack)
            vkGetBufferMemoryRequirements(device, stagingBuffer, stagingMemRequirements)

            val stagingAllocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(stagingMemRequirements.size())
                .memoryTypeIndex(findMemoryType(stagingMemRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))

            val pStagingMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, stagingAllocInfo, null, pStagingMemory) == VK_SUCCESS) {
                "Failed to allocate staging buffer memory"
            }
            val stagingMemory = pStagingMemory[0]
            vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)

            val ppData = stack.mallocPointer(1)
            vkMapMemory(device, stagingMemory, 0, imageSize, 0, ppData)
            val mapped = ppData.getByteBuffer(0, rawData.remaining())
            mapped.put(rawData).flip()
            vkUnmapMemory(device, stagingMemory)
            MemoryUtil.memFree(rawData)

            val mipLevels = calculateMipLevels(texture)
            val allowLinearFilter = supportsLinearFilter(format)
            val generateMipmaps = mipLevels > 1 && allowLinearFilter

            val imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .extent { it.width(texture.width).height(texture.height).depth(1) }
                .mipLevels(mipLevels)
                .arrayLayers(1)
                .format(format)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .usage(
                    VK_IMAGE_USAGE_SAMPLED_BIT or
                        VK_IMAGE_USAGE_TRANSFER_DST_BIT or
                        if (generateMipmaps) VK_IMAGE_USAGE_TRANSFER_SRC_BIT else 0
                )
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .samples(VK_SAMPLE_COUNT_1_BIT)

            val pImage = stack.mallocLong(1)
            check(vkCreateImage(device, imageInfo, null, pImage) == VK_SUCCESS) {
                "Failed to create image"
            }
            val image = pImage[0]

            val memRequirements = VkMemoryRequirements.malloc(stack)
            vkGetImageMemoryRequirements(device, image, memRequirements)

            val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(findMemoryType(memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))

            val pMemory = stack.mallocLong(1)
            check(vkAllocateMemory(device, allocInfo, null, pMemory) == VK_SUCCESS) {
                "Failed to allocate image memory"
            }
            val imageMemory = pMemory[0]
            vkBindImageMemory(device, image, imageMemory, 0)

            executeSingleTimeCommands { cmd, innerStack ->
                transitionImageLayout(
                    cmd,
                    innerStack,
                    image,
                    VK_IMAGE_LAYOUT_UNDEFINED,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    mipLevels,
                    0,
                    1
                )

                val region = VkBufferImageCopy.calloc(1, innerStack)
                region.bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource { sub ->
                        sub.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        sub.mipLevel(0)
                        sub.baseArrayLayer(0)
                        sub.layerCount(1)
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

                if (generateMipmaps) {
                    generateMipmaps(
                        cmd,
                        innerStack,
                        image,
                        texture.width,
                        texture.height,
                        mipLevels
                    )
                } else {
                    transitionImageLayout(
                        cmd,
                        innerStack,
                        image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                        VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                        mipLevels,
                        0,
                        mipLevels
                    )
                }
            }

            vkDestroyBuffer(device, stagingBuffer, null)
            vkFreeMemory(device, stagingMemory, null)

            val imageView = createImageView(image, format, mipLevels)
            val sampler = createSampler(texture, mipLevels)

            return VulkanTextureResource(
                image = image,
                memory = imageMemory,
                imageView = imageView,
                sampler = sampler,
                textureId = texture.id,
                version = texture.version
            )
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
                .layerCount(1)

            val createInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(image)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format)
                .components(components)
                .subresourceRange(subresourceRange)

            val pView = stack.mallocLong(1)
            check(vkCreateImageView(device, createInfo, null, pView) == VK_SUCCESS) { "Failed to create texture image view" }
            pView[0]
        }

    private fun createSampler(texture: Texture2D, mipLevels: Int): Long =
        MemoryStack.stackPush().use { stack ->
            val createInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(toVulkanFilter(texture.magFilter))
                .minFilter(toVulkanFilter(texture.minFilter))
                .mipmapMode(toVulkanMipmapMode(texture.minFilter))
                .addressModeU(toVulkanAddressMode(texture.wrapS))
                .addressModeV(toVulkanAddressMode(texture.wrapT))
                .addressModeW(toVulkanAddressMode(texture.wrapT))
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .compareEnable(false)
                .compareOp(VK_COMPARE_OP_ALWAYS)
                .minLod(0f)
            .maxLod((mipLevels - 1).coerceAtLeast(0).toFloat())
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                .unnormalizedCoordinates(false)

            val pSampler = stack.mallocLong(1)
            check(vkCreateSampler(device, createInfo, null, pSampler) == VK_SUCCESS) { "Failed to create texture sampler" }
            pSampler[0]
        }

    private fun acquireTextureData(texture: Texture2D, pixelSize: Int): java.nio.ByteBuffer {
        val width = texture.width
        val height = texture.height
        val rowSize = width * pixelSize
        val data = texture.getData()
        return if (data != null) {
            val prepared = if (texture.flipY) {
                val flipped = ByteArray(data.size)
                for (y in 0 until height) {
                    val srcIndex = y * rowSize
                    val dstIndex = (height - 1 - y) * rowSize
                    System.arraycopy(data, srcIndex, flipped, dstIndex, rowSize)
                }
                flipped
            } else {
                data
            }
            MemoryUtil.memAlloc(prepared.size).put(prepared).flip() as java.nio.ByteBuffer
        } else {
            val floatData = texture.getFloatData()
                ?: throw IllegalArgumentException("Texture ${texture.name} has no data to upload")
            val buffer = MemoryUtil.memAlloc(floatData.size * 4)
            buffer.asFloatBuffer().put(floatData).flip()
            buffer
        }
    }

    private fun updateDescriptorSet(
        descriptorSet: Long,
        albedoResource: VulkanTextureResource,
        normalResource: VulkanTextureResource,
        roughnessResource: VulkanTextureResource,
        metalnessResource: VulkanTextureResource,
        aoResource: VulkanTextureResource
    ) {
        MemoryStack.stackPush().use { stack ->
            val resourcesBySource = mapOf(
                MaterialBindingSource.ALBEDO_MAP to albedoResource,
                MaterialBindingSource.NORMAL_MAP to normalResource,
                MaterialBindingSource.ROUGHNESS_MAP to roughnessResource,
                MaterialBindingSource.METALNESS_MAP to metalnessResource,
                MaterialBindingSource.AO_MAP to aoResource
            )

            fun sampled(resource: VulkanTextureResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .sampler(VK_NULL_HANDLE)
                    .imageView(resource.imageView)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                return info
            }

            fun sampler(resource: VulkanTextureResource): VkDescriptorImageInfo.Buffer {
                val info = VkDescriptorImageInfo.calloc(1, stack)
                info[0]
                    .sampler(resource.sampler)
                    .imageView(VK_NULL_HANDLE)
                    .imageLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                return info
            }

            val writes = VkWriteDescriptorSet.calloc(layoutBindings.size, stack)
            layoutBindings.forEachIndexed { index, binding ->
                val resource = resourcesBySource[binding.source] ?: albedoResource
                val imageInfo = when (binding.type) {
                    MaterialBindingType.SAMPLER -> sampler(resource)
                    MaterialBindingType.TEXTURE_2D,
                    MaterialBindingType.TEXTURE_CUBE -> sampled(resource)
                }

                writes[index]
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(binding.binding)
                    .descriptorCount(1)
                    .descriptorType(binding.type.toVulkanDescriptorType())
                    .pImageInfo(imageInfo)
            }

            vkUpdateDescriptorSets(device, writes, null)
        }
    }

    private fun MaterialDescriptor.bindingFor(
        source: MaterialBindingSource,
        type: MaterialBindingType
    ) = bindings.firstOrNull { it.source == source && it.type == type }

    private fun allocateDescriptorSet(descriptorPool: Long, descriptorSetLayout: Long): Long =
        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout))

            val pDescriptorSet = stack.mallocLong(1)
            check(vkAllocateDescriptorSets(device, allocInfo, pDescriptorSet) == VK_SUCCESS) {
                "Failed to allocate material texture descriptor set"
            }
            pDescriptorSet[0]
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

    private fun calculateMipLevels(texture: Texture2D): Int {
        if (!texture.generateMipmaps) return 1
        val maxDim = maxOf(texture.width, texture.height)
        if (maxDim <= 0) return 1
        return 1 + log2(maxDim.toDouble()).toInt()
    }

    private fun MaterialBindingType.toVulkanDescriptorType(): Int = when (this) {
        MaterialBindingType.TEXTURE_2D,
        MaterialBindingType.TEXTURE_CUBE -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
        MaterialBindingType.SAMPLER -> VK_DESCRIPTOR_TYPE_SAMPLER
    }

    private fun supportsLinearFilter(format: Int): Boolean =
        MemoryStack.stackPush().use { stack ->
            val properties = VkFormatProperties.calloc(stack)
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, properties)
            (properties.optimalTilingFeatures() and VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT) != 0
        }

    private fun transitionImageLayout(
        commandBuffer: VkCommandBuffer,
        stack: MemoryStack,
        image: Long,
        oldLayout: Int,
        newLayout: Int,
        mipLevels: Int,
        baseMip: Int,
        levelCount: Int
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
                    .layerCount(1)
            }

        var sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
        var destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT

        when {
            oldLayout == VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL -> {
                barrier.srcAccessMask(0)
                barrier.dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT
            }

            oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL -> {
                barrier.srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                barrier.dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT
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

    private fun generateMipmaps(
        commandBuffer: VkCommandBuffer,
        stack: MemoryStack,
        image: Long,
        width: Int,
        height: Int,
        mipLevels: Int
    ) {
        var mipWidth = width
        var mipHeight = height

        for (level in 1 until mipLevels) {
            val barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .image(image)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .subresourceRange { range ->
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(level - 1)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }

            barrier.oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)

            vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                0,
                null,
                null,
                barrier
            )

            val blit = VkImageBlit.calloc(1, stack)
            blit.srcOffsets(0).set(0, 0, 0)
            blit.srcOffsets(1).set(mipWidth, mipHeight, 1)
            blit.srcSubresource { sub ->
                sub.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(level - 1)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

            val nextWidth = if (mipWidth > 1) mipWidth / 2 else 1
            val nextHeight = if (mipHeight > 1) mipHeight / 2 else 1

            blit.dstOffsets(0).set(0, 0, 0)
            blit.dstOffsets(1).set(nextWidth, nextHeight, 1)
            blit.dstSubresource { sub ->
                sub.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(level)
                    .baseArrayLayer(0)
                    .layerCount(1)
            }

            vkCmdBlitImage(
                commandBuffer,
                image,
                VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                image,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                blit,
                VK_FILTER_LINEAR
            )

            val barrierToShader = VkImageMemoryBarrier.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .image(image)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .subresourceRange { range ->
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(level - 1)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1)
                }
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)

            vkCmdPipelineBarrier(
                commandBuffer,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                0,
                null,
                null,
                barrierToShader
            )

            mipWidth = nextWidth
            mipHeight = nextHeight
        }

        transitionImageLayout(
            commandBuffer,
            stack,
            image,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            mipLevels,
            mipLevels - 1,
            1
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

            check(vkEndCommandBuffer(commandBuffer) == VK_SUCCESS) { "Failed to record command buffer" }

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(stack.pointers(commandBuffer.address()))

            check(vkQueueSubmit(graphicsQueue, submitInfo, VK_NULL_HANDLE) == VK_SUCCESS) {
                "Failed to submit texture commands"
            }
            vkQueueWaitIdle(graphicsQueue)
            vkFreeCommandBuffers(device, commandPool, commandBuffer)
        }
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
        throw OutOfMemoryException("Failed to find suitable memory type")
    }
}
