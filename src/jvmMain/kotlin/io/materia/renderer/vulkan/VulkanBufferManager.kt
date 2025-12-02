/**
 * T005-T006: VulkanBufferManager Implementation
 * Feature: 020-go-from-mvp
 *
 * Vulkan buffer management for vertex, index, and uniform buffers.
 */

package io.materia.renderer.vulkan

import io.materia.renderer.feature020.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*

/**
 * Vulkan buffer manager implementation.
 *
 * Manages GPU buffer lifecycle using VkBuffer and VkDeviceMemory.
 * Uses manual memory allocation with HOST_VISIBLE | HOST_COHERENT properties.
 *
 * @property device Vulkan logical device
 * @property physicalDevice Vulkan physical device (for memory type queries)
 */
class VulkanBufferManager(
    private val device: VkDevice,
    private val physicalDevice: VkPhysicalDevice
) : BufferManager {

    // Track destroyed buffers to prevent double-free
    private val destroyedBuffers = mutableSetOf<Long>()

    /**
     * Create vertex buffer from float array.
     *
     * Process:
     * 1. Create VkBuffer with VERTEX_BUFFER_BIT usage
     * 2. Query memory requirements
     * 3. Find HOST_VISIBLE | HOST_COHERENT memory type
     * 4. Allocate VkDeviceMemory
     * 5. Bind buffer to memory
     * 6. Map memory, copy data, unmap
     *
     * @param data Vertex data (interleaved attributes, any stride)
     * @return Buffer handle with VkBuffer
     * @throws IllegalArgumentException if data is empty
     * @throws OutOfMemoryException if allocation fails
     */
    override fun createVertexBuffer(data: FloatArray): BufferHandle {
        if (data.isEmpty()) {
            throw IllegalArgumentException("Vertex data cannot be empty")
        }

        val sizeBytes = data.size * Float.SIZE_BYTES

        return try {
            MemoryStack.stackPush().use { stack ->
                // 1. Create VkBuffer
                val bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes.toLong())
                    .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

                val pBuffer = stack.mallocLong(1)
                val result = vkCreateBuffer(device, bufferInfo, null, pBuffer)
                if (result != VK_SUCCESS) {
                    throw OutOfMemoryException("Failed to create vertex buffer: VkResult=$result")
                }
                val buffer = pBuffer.get(0)

                // 2. Query memory requirements
                val memRequirements = VkMemoryRequirements.malloc(stack)
                vkGetBufferMemoryRequirements(device, buffer, memRequirements)

                // 3. Find memory type (HOST_VISIBLE | HOST_COHERENT)
                val memoryTypeIndex = findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                )

                // 4. Allocate memory
                val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex)

                val pMemory = stack.mallocLong(1)
                val allocResult = vkAllocateMemory(device, allocInfo, null, pMemory)
                if (allocResult != VK_SUCCESS) {
                    vkDestroyBuffer(device, buffer, null)
                    throw OutOfMemoryException("Failed to allocate vertex buffer memory: VkResult=$allocResult")
                }
                val memory = pMemory.get(0)

                // 5. Bind buffer to memory
                vkBindBufferMemory(device, buffer, memory, 0)

                // 6. Map memory, copy data, unmap
                val ppData = stack.mallocPointer(1)
                vkMapMemory(device, memory, 0, sizeBytes.toLong(), 0, ppData)
                val mappedData = ppData.getByteBuffer(0, sizeBytes)
                mappedData.asFloatBuffer().put(data)
                vkUnmapMemory(device, memory)

                // Return handle with both VkBuffer and VkDeviceMemory
                BufferHandle(
                    handle = VulkanBufferHandleData(buffer, memory),
                    size = sizeBytes,
                    usage = BufferUsage.VERTEX
                )
            }
        } catch (e: OutOfMemoryException) {
            throw e
        } catch (e: Exception) {
            throw OutOfMemoryException("Unexpected error creating vertex buffer: ${e.message}")
        }
    }

    /**
     * Create index buffer from int array.
     *
     * @param data Triangle indices (must be multiple of 3)
     * @return Buffer handle with VkBuffer
     * @throws IllegalArgumentException if data is empty or not triangles
     */
    override fun createIndexBuffer(data: IntArray): BufferHandle {
        if (data.isEmpty()) {
            throw IllegalArgumentException("Index data cannot be empty")
        }

        if (data.size % 3 != 0) {
            throw IllegalArgumentException(
                "indexData.size must be multiple of 3 (triangles), got ${data.size}"
            )
        }

        val sizeBytes = data.size * Int.SIZE_BYTES

        return try {
            MemoryStack.stackPush().use { stack ->
                // Create VkBuffer with INDEX_BUFFER_BIT
                val bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes.toLong())
                    .usage(VK_BUFFER_USAGE_INDEX_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

                val pBuffer = stack.mallocLong(1)
                val result = vkCreateBuffer(device, bufferInfo, null, pBuffer)
                if (result != VK_SUCCESS) {
                    throw OutOfMemoryException("Failed to create index buffer: VkResult=$result")
                }
                val buffer = pBuffer.get(0)

                // Query memory requirements
                val memRequirements = VkMemoryRequirements.malloc(stack)
                vkGetBufferMemoryRequirements(device, buffer, memRequirements)

                // Find memory type
                val memoryTypeIndex = findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                )

                // Allocate memory
                val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex)

                val pMemory = stack.mallocLong(1)
                val allocResult = vkAllocateMemory(device, allocInfo, null, pMemory)
                if (allocResult != VK_SUCCESS) {
                    vkDestroyBuffer(device, buffer, null)
                    throw OutOfMemoryException("Failed to allocate index buffer memory: VkResult=$allocResult")
                }
                val memory = pMemory.get(0)

                // Bind buffer to memory
                vkBindBufferMemory(device, buffer, memory, 0)

                // Map memory, copy data, unmap
                val ppData = stack.mallocPointer(1)
                vkMapMemory(device, memory, 0, sizeBytes.toLong(), 0, ppData)
                val mappedData = ppData.getByteBuffer(0, sizeBytes)
                mappedData.asIntBuffer().put(data)
                vkUnmapMemory(device, memory)

                BufferHandle(
                    handle = VulkanBufferHandleData(buffer, memory),
                    size = sizeBytes,
                    usage = BufferUsage.INDEX
                )
            }
        } catch (e: OutOfMemoryException) {
            throw e
        } catch (e: Exception) {
            throw OutOfMemoryException("Unexpected error creating index buffer: ${e.message}")
        }
    }

    /**
     * Create uniform buffer with fixed size.
     *
     * @param sizeBytes Buffer size in bytes (minimum 64 for mat4x4)
     * @return Buffer handle with VkBuffer
     * @throws IllegalArgumentException if sizeBytes < 64
     */
    override fun createUniformBuffer(sizeBytes: Int): BufferHandle {
        if (sizeBytes < 64) {
            throw IllegalArgumentException(
                "uniformBuffer.sizeBytes must be at least 64 bytes (mat4x4), got $sizeBytes"
            )
        }

        return try {
            MemoryStack.stackPush().use { stack ->
                // Create VkBuffer with UNIFORM_BUFFER_BIT
                val bufferInfo = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(sizeBytes.toLong())
                    .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)

                val pBuffer = stack.mallocLong(1)
                val result = vkCreateBuffer(device, bufferInfo, null, pBuffer)
                if (result != VK_SUCCESS) {
                    throw OutOfMemoryException("Failed to create uniform buffer: VkResult=$result")
                }
                val buffer = pBuffer.get(0)

                // Query memory requirements
                val memRequirements = VkMemoryRequirements.malloc(stack)
                vkGetBufferMemoryRequirements(device, buffer, memRequirements)

                // Find memory type
                val memoryTypeIndex = findMemoryType(
                    memRequirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                )

                // Allocate memory
                val allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(memoryTypeIndex)

                val pMemory = stack.mallocLong(1)
                val allocResult = vkAllocateMemory(device, allocInfo, null, pMemory)
                if (allocResult != VK_SUCCESS) {
                    vkDestroyBuffer(device, buffer, null)
                    throw OutOfMemoryException("Failed to allocate uniform buffer memory: VkResult=$allocResult")
                }
                val memory = pMemory.get(0)

                // Bind buffer to memory
                vkBindBufferMemory(device, buffer, memory, 0)

                BufferHandle(
                    handle = VulkanBufferHandleData(buffer, memory),
                    size = sizeBytes,
                    usage = BufferUsage.UNIFORM
                )
            }
        } catch (e: OutOfMemoryException) {
            throw e
        } catch (e: Exception) {
            throw OutOfMemoryException("Unexpected error creating uniform buffer: ${e.message}")
        }
    }

    /**
     * Update uniform buffer data (transformation matrices).
     *
     * @param handle Buffer handle from createUniformBuffer()
     * @param data Matrix data as byte array (64 bytes for mat4x4)
     * @param offset Write offset in bytes (must be 16-byte aligned)
     * @throws InvalidBufferException if handle is invalid or destroyed
     * @throws IllegalArgumentException if offset not aligned or data too large
     */
    override fun updateUniformBuffer(handle: BufferHandle, data: ByteArray, offset: Int) {
        // Validate handle
        if (!handle.isValid()) {
            throw InvalidBufferException("Buffer handle is invalid (null handle or zero size)")
        }

        val bufferData = handle.handle as? VulkanBufferHandleData
            ?: throw InvalidBufferException("Buffer handle is not a VulkanBufferHandleData")

        // Check if destroyed
        if (destroyedBuffers.contains(bufferData.buffer)) {
            throw InvalidBufferException("Buffer has been destroyed")
        }

        // Validate offset alignment (16-byte for mat4x4)
        if (offset % 16 != 0) {
            throw IllegalArgumentException("offset must be 16-byte aligned, got $offset")
        }

        // Validate data size
        if (offset + data.size > handle.size) {
            throw IllegalArgumentException(
                "data too large: offset=$offset + size=${data.size} > buffer.size=${handle.size}"
            )
        }

        try {
            MemoryStack.stackPush().use { stack ->
                // Map memory
                val ppData = stack.mallocPointer(1)
                vkMapMemory(
                    device,
                    bufferData.memory,
                    offset.toLong(),
                    data.size.toLong(),
                    0,
                    ppData
                )
                val mappedData = ppData.getByteBuffer(0, data.size)

                // Copy data
                mappedData.put(data)

                // Unmap memory
                vkUnmapMemory(device, bufferData.memory)
            }
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to update uniform buffer: ${e.message}")
        }
    }

    /**
     * Destroy buffer and release GPU memory.
     *
     * @param handle Buffer handle to destroy
     * @throws InvalidBufferException if handle already destroyed
     */
    override fun destroyBuffer(handle: BufferHandle) {
        val bufferData = handle.handle as? VulkanBufferHandleData
            ?: throw InvalidBufferException("Buffer handle is not a VulkanBufferHandleData")

        // Check if already destroyed
        if (destroyedBuffers.contains(bufferData.buffer)) {
            throw InvalidBufferException("Buffer has already been destroyed")
        }

        try {
            // Destroy buffer
            vkDestroyBuffer(device, bufferData.buffer, null)

            // Free memory
            vkFreeMemory(device, bufferData.memory, null)

            // Mark as destroyed
            destroyedBuffers.add(bufferData.buffer)
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to destroy buffer: ${e.message}")
        }
    }

    /**
     * Find suitable memory type from memory type bits.
     *
     * @param typeFilter Memory type bits from VkMemoryRequirements
     * @param properties Required memory properties
     * @return Memory type index
     * @throws OutOfMemoryException if no suitable memory type found
     */
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

        throw OutOfMemoryException(
            "Failed to find suitable memory type (typeFilter=$typeFilter, properties=$properties)"
        )
    }
}

/**
 * Vulkan buffer handle data containing both VkBuffer and VkDeviceMemory.
 */
data class VulkanBufferHandleData(
    val buffer: Long,
    val memory: Long
)
