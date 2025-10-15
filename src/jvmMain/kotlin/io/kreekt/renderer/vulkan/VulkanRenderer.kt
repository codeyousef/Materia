/**
 * T017-T019: VulkanRenderer Implementation with Feature 020 Managers
 * Feature: 020-go-from-mvp
 *
 * Vulkan-based renderer for JVM platform using LWJGL 3.3.6.
 * Integrates BufferManager, RenderPassManager, and SwapchainManager.
 */

package io.kreekt.renderer.vulkan

import io.kreekt.camera.Camera
import io.kreekt.core.scene.Background
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.core.math.Matrix4
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.renderer.*
import io.kreekt.renderer.feature020.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.KHRSwapchain.*
import kotlin.system.measureTimeMillis
import kotlin.math.roundToInt

/**
 * Vulkan renderer implementation for JVM platform.
 *
 * Uses LWJGL 3.3.6 Vulkan bindings to implement the Renderer interface.
 * Targets Vulkan 1.1+ for broad compatibility.
 *
 * Lifecycle:
 * 1. Create VulkanRenderer instance
 * 2. Call initialize(config) - creates VkInstance, device, pipeline
 * 3. Call render(scene, camera) repeatedly
 * 4. Call dispose() on shutdown
 *
 * @property surface VulkanSurface wrapping GLFW window
 * @property config Renderer configuration
 */
class VulkanRenderer(
    private val surface: RenderSurface,
    private val config: RendererConfig
) : Renderer {

    // Vulkan handles
    private var instance: VkInstance? = null
    private var physicalDevice: VkPhysicalDevice? = null
    private var device: VkDevice? = null
    private var graphicsQueue: VkQueue? = null
    private var commandPool: Long = VK_NULL_HANDLE
    private var commandBuffer: VkCommandBuffer? = null
    private var renderPass: Long = VK_NULL_HANDLE

    // Feature 020 Managers (T017-T019)
    private var bufferManager: VulkanBufferManager? = null
    private var renderPassManager: VulkanRenderPassManager? = null
    private var swapchainManager: VulkanSwapchain? = null
    private var pipeline: VulkanPipeline? = null
    private var descriptorSetLayout: Long = VK_NULL_HANDLE
    private var descriptorPool: Long = VK_NULL_HANDLE
    private var descriptorSet: Long = VK_NULL_HANDLE
    private var uniformBuffer: BufferHandle? = null
    private var swapchainFramebuffers: List<VulkanFramebufferData> = emptyList()

    private val meshBuffers: MutableMap<Int, VulkanMeshBuffers> = mutableMapOf()

    // Renderer state
    override var backend: BackendType = BackendType.VULKAN
        private set

    override var capabilities: RendererCapabilities = RendererCapabilities()
        private set

    override var stats: RenderStats = RenderStats(0.0, 0.0, 0, 0)
        private set

    private var initialized = false
    private var frameCount = 0
    private var lastFrameTime = System.currentTimeMillis()
    private var fpsAccumulator = 0.0
    private var fpsFrameCount = 0

    // T033: Debug flag for verbose frame logging (default off to avoid spam)
    var enableFrameLogging: Boolean = false

    /**
     * Initialize Vulkan renderer.
     *
     * Process:
     * 1. Create VkInstance with validation layers (if config.enableValidation)
     * 2. Select physical device (prefer discrete GPU)
     * 3. Create logical device with graphics queue
     * 4. Create command pool and buffers
     * 5. Query capabilities
     *
     * @param config Renderer configuration
     * @return Success or RendererError on failure
     */
    override suspend fun initialize(config: RendererConfig): io.kreekt.core.Result<Unit> {
        println("T033: Starting Vulkan renderer initialization...")
        val startTime = System.currentTimeMillis()
        return try {
                // 1. Create VkInstance
                println("T033: Creating VkInstance (validation=${config.enableValidation})...")
                instance = createInstance(config.enableValidation)
                    ?: return io.kreekt.core.Result.Error(
                        "Failed to create VkInstance",
                        io.kreekt.renderer.RendererInitializationException.AdapterRequestFailedException(
                            BackendType.VULKAN,
                            "Failed to create VkInstance"
                        )
                    )
                println("T033: VkInstance created successfully")

            // 1.5. Create surface (T019: Required before swapchain creation)
            println("T033: Creating Vulkan surface...")
            if (surface is VulkanSurface) {
                surface.createSurface(instance!!)
            }
            println("T033: Surface created successfully")

            // 2. Select physical device
            println("T033: Selecting physical device...")
            physicalDevice = selectPhysicalDevice(instance!!)
                ?: return io.kreekt.core.Result.Error(
                    "No suitable Vulkan physical device found",
                    io.kreekt.renderer.RendererInitializationException.AdapterRequestFailedException(
                        BackendType.VULKAN,
                        "No suitable Vulkan physical device found"
                    )
                )
            val deviceInfo = getPhysicalDeviceInfo(physicalDevice!!)
            println("T033: Selected physical device: $deviceInfo")

            // 3. Create logical device
            println("T033: Creating logical device...")
            device = createLogicalDevice(physicalDevice!!, config)
                ?: return io.kreekt.core.Result.Error(
                    "Failed to create logical device",
                    io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        getPhysicalDeviceInfo(physicalDevice!!),
                        "Failed to create logical device"
                    )
                )
            println("T033: Logical device created successfully")

            // 4. Get graphics queue
            println("T033: Getting graphics queue...")
            MemoryStack.stackPush().use { stack ->
                val pQueue = stack.callocPointer(1)
                vkGetDeviceQueue(device!!, 0, 0, pQueue)
                graphicsQueue = VkQueue(pQueue.get(0), device!!)
            }
            println("T033: Graphics queue obtained")

            // 5. Create command pool
            println("T033: Creating command pool...")
            commandPool = createCommandPool(device!!)
            if (commandPool == VK_NULL_HANDLE) {
                return io.kreekt.core.Result.Error(
                    "Failed to create command pool",
                    io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        getPhysicalDeviceInfo(physicalDevice!!),
                        "Failed to create command pool"
                    )
                )
            }
            println("T033: Command pool created successfully")

            // 6. Allocate command buffer
            println("T033: Allocating command buffer...")
            commandBuffer = allocateCommandBuffer(device!!, commandPool)
            println("T033: Command buffer allocated successfully")

            // 7. Create render pass
            println("T033: Creating render pass...")
            val imageFormat = swapchainManager!!.getImageFormat()
            renderPass = createRenderPass(device!!, imageFormat)
            if (renderPass == VK_NULL_HANDLE) {
                return io.kreekt.core.Result.Error(
                    "Failed to create render pass",
                    io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        getPhysicalDeviceInfo(physicalDevice!!),
                        "Failed to create render pass"
                    )
                )
            }
            println("T033: Render pass created successfully")

            // 8. Initialize Feature 020 Managers (T017-T019)

            // T017: Initialize BufferManager
            println("T033: Initializing BufferManager...")
            bufferManager = VulkanBufferManager(device!!, physicalDevice!!)
            println("T033: BufferManager initialized successfully")

            // T019: Initialize SwapchainManager
            println("T033: Initializing SwapchainManager...")
            val vulkanSurface = extractVulkanSurface(surface)
            swapchainManager = VulkanSwapchain(device!!, physicalDevice!!, vulkanSurface)
            println("T033: SwapchainManager initialized successfully")

            // T018: Initialize RenderPassManager
            println("T033: Initializing RenderPassManager...")
            renderPassManager = VulkanRenderPassManager(device!!, commandBuffer!!, renderPass)
            println("T033: RenderPassManager initialized successfully")

            println("T033: Creating swapchain framebuffers...")
            swapchainFramebuffers = swapchainManager!!.createFramebuffers(renderPass)
            println("T033: Framebuffers created (${swapchainFramebuffers.size} images)")

        println("T033: Creating descriptor resources...")
        createDescriptorResources()
        println("T033: Descriptor resources ready")

        println("T033: Creating graphics pipeline...")
        pipeline = VulkanPipeline(device!!)
        val extent = swapchainManager!!.getExtent()
        if (pipeline!!.createPipeline(renderPass, extent.first, extent.second, descriptorSetLayout)) {
            println("T033: Graphics pipeline created successfully")
        } else {
            return io.kreekt.core.Result.Error(
                "Failed to create graphics pipeline",
                io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                    BackendType.VULKAN,
                    getPhysicalDeviceInfo(physicalDevice!!),
                    "Failed to create graphics pipeline"
                )
            )
        }

            // 9. Query capabilities
            println("T033: Querying device capabilities...")
            capabilities = queryCapabilities(physicalDevice!!)
            println("T033: Capabilities detected: maxTextureSize=${capabilities.maxTextureSize}, maxSamples=${capabilities.maxSamples}")

            initialized = true
            val totalInitTime = System.currentTimeMillis() - startTime
            println("T033: Vulkan renderer initialization completed in ${totalInitTime}ms")
            io.kreekt.core.Result.Success(Unit)
        } catch (e: Exception) {
            // Clean up partial initialization
            println("T033: ERROR during initialization: ${e.message}")
            println("T033: Stack trace: ${e.stackTraceToString()}")
            dispose()
            io.kreekt.core.Result.Error(
                "Initialization failed at stage: ${e.message}",
                io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                    BackendType.VULKAN,
                    physicalDevice?.let { getPhysicalDeviceInfo(it) } ?: "Unknown device",
                    "Initialization failed: ${e.message}"
                )
            )
        }
    }

    /**
     * Render scene from camera perspective.
     */
    override fun render(scene: Scene, camera: Camera) {
        if (!initialized) {
            throw IllegalStateException("Renderer not initialized. Call initialize() first.")
        }

        val swapchain = swapchainManager ?: return
        val pipelineInstance = pipeline
        val renderPassMgr = renderPassManager
        if (swapchainFramebuffers.isEmpty() || pipelineInstance == null || renderPassMgr == null) {
            recreateSwapchainResources()
            return
        }

        if (descriptorSetLayout == VK_NULL_HANDLE || descriptorSet == VK_NULL_HANDLE || uniformBuffer == null) {
            createDescriptorResources()
            if (descriptorSetLayout == VK_NULL_HANDLE || descriptorSet == VK_NULL_HANDLE || uniformBuffer == null) {
                return
            }
        }

        scene.updateMatrixWorld(true)
        camera.updateMatrixWorld(false)
        camera.updateProjectionMatrix()

        val viewProjectionMatrix = Matrix4()
        viewProjectionMatrix.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse)

        val drawInfos = mutableListOf<MeshDrawInfo>()
        val retainedIds = mutableSetOf<Int>()

        scene.traverseVisible { node ->
            if (node is Mesh && node.visible) {
                ensureMeshBuffers(node)?.let { buffers ->
                    drawInfos += MeshDrawInfo(node, buffers)
                    retainedIds += node.id
                }
            }
        }

        val staleIds = meshBuffers.keys - retainedIds
        staleIds.forEach { id ->
            meshBuffers.remove(id)?.let { destroyMeshBuffers(it) }
        }

        val clearColor = determineClearColor(scene)
        var drawCalls = 0
        var triangles = 0

        val frameTime = measureTimeMillis {
            val image = try {
                swapchain.acquireNextImage()
            } catch (ex: SwapchainException) {
                if (ex.message?.contains("out of date", ignoreCase = true) == true) {
                    recreateSwapchainResources()
                    return@measureTimeMillis
                } else {
                    throw RuntimeException("Failed to acquire swapchain image", ex)
                }
            }

            if (image.index >= swapchainFramebuffers.size) {
                recreateSwapchainResources()
                return@measureTimeMillis
            }

            MemoryStack.stackPush().use { stack ->
                val command = commandBuffer ?: return@measureTimeMillis
                val queue = graphicsQueue ?: return@measureTimeMillis

                vkResetCommandBuffer(command, 0)

                val beginInfo = org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

                val beginResult = vkBeginCommandBuffer(command, beginInfo)
                if (beginResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to begin command buffer: VkResult=$beginResult")
                }

                val framebufferHandle = FramebufferHandle(swapchainFramebuffers[image.index])
                renderPassMgr.beginRenderPass(clearColor, framebufferHandle)
                renderPassMgr.bindPipeline(PipelineHandle(pipelineInstance.getPipelineHandle()))

                val descriptor = descriptorSet
                val pipelineLayout = pipelineInstance.getPipelineLayout()

                for (drawInfo in drawInfos) {
                    val meshesIndexBuffer = drawInfo.buffers.indexBuffer ?: continue

                    val mvp = Matrix4()
                    mvp.multiplyMatrices(viewProjectionMatrix, drawInfo.mesh.matrixWorld)
                    updateUniformBuffer(mvp)

                    val descriptorSets = stack.longs(descriptor)
                    vkCmdBindDescriptorSets(
                        command,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        descriptorSets,
                        null
                    )

                    renderPassMgr.bindVertexBuffer(drawInfo.buffers.vertexBuffer)
                    renderPassMgr.bindIndexBuffer(meshesIndexBuffer)
                    renderPassMgr.drawIndexed(drawInfo.buffers.indexCount)
                    drawCalls++
                    triangles += drawInfo.buffers.triangleCount
                }

                renderPassMgr.endRenderPass()

                val endResult = vkEndCommandBuffer(command)
                if (endResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to record command buffer: VkResult=$endResult")
                }

                val waitSemaphores = stack.longs(swapchain.getImageAvailableSemaphore())
                val waitStages = stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                val signalSemaphores = stack.longs(swapchain.getRenderFinishedSemaphore())
                val commandBuffers = stack.pointers(command.address())

                val submitInfo = org.lwjgl.vulkan.VkSubmitInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                    .pWaitSemaphores(waitSemaphores)
                    .pWaitDstStageMask(waitStages)
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores)

                val submitResult = vkQueueSubmit(queue, submitInfo, VK_NULL_HANDLE)
                if (submitResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to submit draw command buffer: VkResult=$submitResult")
                }

                val presentInfo = org.lwjgl.vulkan.VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(signalSemaphores)
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain.getSwapchainHandle()))
                    .pImageIndices(stack.ints(image.index))

                val presentResult = vkQueuePresentKHR(queue, presentInfo)
                if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
                    vkQueueWaitIdle(queue)
                    recreateSwapchainResources()
                    return@measureTimeMillis
                } else if (presentResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to present swapchain image: VkResult=$presentResult")
                }

                vkQueueWaitIdle(queue)
            }

            frameCount++
        }

        updateStats(frameTime, drawCalls, triangles)
    }

    /**
     * Resize render targets.
     *
     * T019: Use SwapchainManager to recreate swapchain with new dimensions.
     */
    override fun resize(width: Int, height: Int) {
        if (!initialized) {
            throw IllegalStateException("Renderer not initialized. Call initialize() first.")
        }

        recreateSwapchainResources(width, height)
    }

    private fun recreateSwapchainResources(newWidth: Int? = null, newHeight: Int? = null) {
        val deviceHandle = device ?: return
        val command = commandBuffer ?: return
        val swapchain = swapchainManager ?: return

        vkDeviceWaitIdle(deviceHandle)

        pipeline?.dispose()
        pipeline = null

        renderPassManager = null
        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(deviceHandle, renderPass, null)
            renderPass = VK_NULL_HANDLE
        }

        val width = newWidth ?: surface.width.coerceAtLeast(1)
        val height = newHeight ?: surface.height.coerceAtLeast(1)
        swapchain.recreateSwapchain(width, height)

        val imageFormat = swapchain.getImageFormat()
        renderPass = createRenderPass(deviceHandle, imageFormat)
        renderPassManager = VulkanRenderPassManager(deviceHandle, command, renderPass)
        swapchainFramebuffers = swapchain.createFramebuffers(renderPass)

        createDescriptorResources()

        val extent = swapchain.getExtent()
        pipeline = VulkanPipeline(deviceHandle)
        if (descriptorSetLayout == VK_NULL_HANDLE || !pipeline!!.createPipeline(renderPass, extent.first, extent.second, descriptorSetLayout)) {
            throw RuntimeException("Failed to create Vulkan graphics pipeline")
        }
    }

    private fun createDescriptorResources() {
        val deviceHandle = device ?: return
        val bufferMgr = bufferManager ?: return

        if (descriptorSetLayout == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val layoutBinding = VkDescriptorSetLayoutBinding.calloc(1, stack)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .pImmutableSamplers(null)

                val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(layoutBinding)

                val pLayout = stack.mallocLong(1)
                val layoutResult = vkCreateDescriptorSetLayout(deviceHandle, layoutInfo, null, pLayout)
                if (layoutResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to create descriptor set layout: VkResult=$layoutResult")
                }
                descriptorSetLayout = pLayout[0]
            }
        }

        if (descriptorPool == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .descriptorCount(1)

                val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSize)
                    .maxSets(1)

                val pPool = stack.mallocLong(1)
                val poolResult = vkCreateDescriptorPool(deviceHandle, poolInfo, null, pPool)
                if (poolResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to create descriptor pool: VkResult=$poolResult")
                }
                descriptorPool = pPool[0]
            }
        }

        if (descriptorSet == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout))

                val pDescriptorSet = stack.mallocLong(1)
                val allocResult = vkAllocateDescriptorSets(deviceHandle, allocInfo, pDescriptorSet)
                if (allocResult != VK_SUCCESS) {
                    throw RuntimeException("Failed to allocate descriptor set: VkResult=$allocResult")
                }
                descriptorSet = pDescriptorSet[0]
            }
        }

        uniformBuffer?.let { existing ->
            bufferMgr.destroyBuffer(existing)
            uniformBuffer = null
        }

        uniformBuffer = bufferMgr.createUniformBuffer(UNIFORM_BUFFER_SIZE)
        val bufferData = uniformBuffer?.handle as? VulkanBufferHandleData
            ?: throw RuntimeException("Failed to obtain uniform buffer handle")

        MemoryStack.stackPush().use { stack ->
            val bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                .buffer(bufferData.buffer)
                .offset(0)
                .range(UNIFORM_BUFFER_SIZE.toLong())

            val descriptorWrite = VkWriteDescriptorSet.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                .pBufferInfo(bufferInfo)

            vkUpdateDescriptorSets(deviceHandle, descriptorWrite, null)
        }

        updateUniformBuffer(Matrix4())
    }

    private fun destroyDescriptorResources() {
        uniformBuffer?.let {
            try {
                bufferManager?.destroyBuffer(it)
            } catch (_: Exception) {
            }
        }
        uniformBuffer = null

        device?.let { deviceHandle ->
            if (descriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(deviceHandle, descriptorPool, null)
                descriptorPool = VK_NULL_HANDLE
            }

            if (descriptorSetLayout != VK_NULL_HANDLE) {
                vkDestroyDescriptorSetLayout(deviceHandle, descriptorSetLayout, null)
                descriptorSetLayout = VK_NULL_HANDLE
            }
        }

        descriptorSet = VK_NULL_HANDLE
    }

    private fun ensureMeshBuffers(mesh: Mesh): VulkanMeshBuffers? {
        val bufferMgr = bufferManager ?: return null
        val geometry = mesh.geometry
        val position = geometry.getAttribute("position") ?: return null
        if (position.itemSize < POSITION_COMPONENTS || position.count == 0) return null

        val colorAttribute = geometry.getAttribute("color")
        val existing = meshBuffers[mesh.id]
        val needsUpdate = existing == null || position.needsUpdate || (colorAttribute?.needsUpdate == true) || (geometry.index?.needsUpdate == true)

        if (!needsUpdate) {
            return existing
        }

        meshBuffers.remove(mesh.id)?.let { destroyMeshBuffers(it) }

        val vertexCount = position.count
        val vertexData = FloatArray(vertexCount * (POSITION_COMPONENTS + COLOR_COMPONENTS))
        var writeIndex = 0
        val materialColor = (mesh.material as? MeshBasicMaterial)?.color ?: io.kreekt.core.math.Color.WHITE

        for (i in 0 until vertexCount) {
            vertexData[writeIndex++] = position.getX(i)
            vertexData[writeIndex++] = position.getY(i)
            vertexData[writeIndex++] = position.getZ(i)

            if (colorAttribute != null && colorAttribute.itemSize >= COLOR_COMPONENTS) {
                vertexData[writeIndex++] = colorAttribute.getX(i)
                vertexData[writeIndex++] = colorAttribute.getY(i)
                vertexData[writeIndex++] = colorAttribute.getZ(i)
            } else {
                vertexData[writeIndex++] = materialColor.r
                vertexData[writeIndex++] = materialColor.g
                vertexData[writeIndex++] = materialColor.b
            }
        }

        var indexArray = if (geometry.index != null && geometry.index!!.count > 0) {
            IntArray(geometry.index!!.count) { geometry.index!!.getX(it).roundToInt() }
        } else {
            IntArray(vertexCount) { it }
        }

        if (indexArray.isEmpty()) return null
        if (indexArray.size % 3 != 0) {
            indexArray = indexArray.copyOf(indexArray.size - (indexArray.size % 3))
            if (indexArray.isEmpty()) return null
        }

        val vertexBuffer = bufferMgr.createVertexBuffer(vertexData)
        val indexBuffer = bufferMgr.createIndexBuffer(indexArray)
        val triangles = indexArray.size / 3

        position.needsUpdate = false
        colorAttribute?.needsUpdate = false
        geometry.index?.let { it.needsUpdate = false }

        val buffers = VulkanMeshBuffers(vertexBuffer, indexBuffer, indexArray.size, triangles)
        meshBuffers[mesh.id] = buffers
        return buffers
    }

    private fun destroyMeshBuffers(buffers: VulkanMeshBuffers) {
        try {
            bufferManager?.destroyBuffer(buffers.vertexBuffer)
        } catch (_: Exception) {
        }

        buffers.indexBuffer?.let {
            try {
                bufferManager?.destroyBuffer(it)
            } catch (_: Exception) {
            }
        }
    }

    private fun updateUniformBuffer(matrix: Matrix4) {
        val bufferMgr = bufferManager ?: return
        val buffer = uniformBuffer ?: return
        val bytes = ByteArray(UNIFORM_BUFFER_SIZE)
        for (i in 0 until 16) {
            val value = matrix.elements[i]
            val bits = java.lang.Float.floatToIntBits(value)
            val base = i * 4
            bytes[base] = (bits and 0xFF).toByte()
            bytes[base + 1] = ((bits ushr 8) and 0xFF).toByte()
            bytes[base + 2] = ((bits ushr 16) and 0xFF).toByte()
            bytes[base + 3] = ((bits ushr 24) and 0xFF).toByte()
        }
        bufferMgr.updateUniformBuffer(buffer, bytes)
    }

    private data class VulkanMeshBuffers(
        val vertexBuffer: BufferHandle,
        val indexBuffer: BufferHandle?,
        val indexCount: Int,
        val triangleCount: Int
    )

    private data class MeshDrawInfo(
        val mesh: Mesh,
        val buffers: VulkanMeshBuffers
    )

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val UNIFORM_BUFFER_SIZE = 64
    }

    private fun determineClearColor(scene: Scene): Color {
        val defaultColor = Color(0.02f, 0.02f, 0.05f, 1.0f)
        val background = scene.background ?: return defaultColor

        return when (background) {
            is Background.Color -> {
                val c = background.color
                Color(c.r, c.g, c.b, c.a)
            }

            is Background.Gradient -> {
                val top = background.top
                val bottom = background.bottom
                Color(
                    (top.r + bottom.r) * 0.5f,
                    (top.g + bottom.g) * 0.5f,
                    (top.b + bottom.b) * 0.5f,
                    1.0f
                )
            }

            else -> defaultColor
        }
    }

    /**
     * Dispose Vulkan resources.
     *
     * Must be called before application exit to prevent resource leaks.
     * T017-T019: Clean up all managers.
     */
    override fun dispose() {
        if (!initialized) {
            println("T033: Dispose called but renderer not initialized, skipping")
            return
        }

        println("T033: Starting Vulkan renderer disposal...")

        // Wait for device to finish
        println("T033: Waiting for device idle...")
        device?.let { vkDeviceWaitIdle(it) }
        println("T033: Device idle")

        pipeline?.dispose()
        pipeline = null

        meshBuffers.values.forEach { destroyMeshBuffers(it) }
        meshBuffers.clear()
        swapchainFramebuffers = emptyList()

        destroyDescriptorResources()

        // T019: Dispose SwapchainManager
        println("T033: Disposing SwapchainManager...")
        swapchainManager?.dispose()
        swapchainManager = null
        println("T033: SwapchainManager disposed")

        // T018: RenderPassManager doesn't need explicit disposal (uses command buffer)
        println("T033: Cleaning up RenderPassManager...")
        renderPassManager = null

        // T017: BufferManager doesn't need explicit disposal (buffers disposed individually)
        println("T033: Cleaning up BufferManager...")
        bufferManager = null

        // Destroy render pass
        if (renderPass != VK_NULL_HANDLE && device != null) {
            println("T033: Destroying render pass...")
            vkDestroyRenderPass(device!!, renderPass, null)
            renderPass = VK_NULL_HANDLE
            println("T033: Render pass destroyed")
        }

        // Destroy command pool (also frees command buffers)
        if (commandPool != VK_NULL_HANDLE && device != null) {
            println("T033: Destroying command pool...")
            vkDestroyCommandPool(device!!, commandPool, null)
            commandPool = VK_NULL_HANDLE
            println("T033: Command pool destroyed")
        }

        // Destroy logical device
        device?.let {
            println("T033: Destroying logical device...")
            vkDestroyDevice(it, null)
            device = null
            println("T033: Logical device destroyed")
        }

        // T019: Destroy surface
        if (surface is VulkanSurface) {
            println("T033: Destroying surface...")
            surface.destroySurface()
            println("T033: Surface destroyed")
        }

        // Destroy instance
        instance?.let {
            println("T033: Destroying VkInstance...")
            vkDestroyInstance(it, null)
            instance = null
            println("T033: VkInstance destroyed")
        }

        initialized = false
        println("T033: Vulkan renderer disposal completed")
    }

    // Note: getStats() removed - use 'stats' property directly to avoid JVM signature clash

    // === Private Helper Methods ===

    private fun createInstance(enableValidation: Boolean): VkInstance? {
        return MemoryStack.stackPush().use { stack ->
            // Application info
            val appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("KreeKt Application"))
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(stack.UTF8("KreeKt"))
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_API_VERSION_1_1)

            // Get required extensions from GLFW (T019: Required for surface creation)
            val glfwExtensions = org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions()
                ?: return null

            // Instance create info
            val createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .ppEnabledExtensionNames(glfwExtensions)

            // Validation layers (if enabled)
            if (enableValidation) {
                val layers = stack.callocPointer(1)
                layers.put(0, stack.UTF8("VK_LAYER_KHRONOS_validation"))
                createInfo.ppEnabledLayerNames(layers)
            }

            // Create instance
            val pInstance = stack.callocPointer(1)
            val result = vkCreateInstance(createInfo, null, pInstance)
            if (result != VK_SUCCESS) {
                null
            } else {
                VkInstance(pInstance.get(0), createInfo)
            }
        }
    }

    private fun selectPhysicalDevice(instance: VkInstance): VkPhysicalDevice? {
        return MemoryStack.stackPush().use { stack ->
            // Enumerate physical devices
            val pPhysicalDeviceCount = stack.callocInt(1)
            vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, null)
            val deviceCount = pPhysicalDeviceCount.get(0)
            if (deviceCount == 0) return null

            val pPhysicalDevices = stack.callocPointer(deviceCount)
            vkEnumeratePhysicalDevices(instance, pPhysicalDeviceCount, pPhysicalDevices)

            // Prefer discrete GPU, fallback to first device
            var selectedDevice: VkPhysicalDevice? = null
            for (i in 0 until deviceCount) {
                val device = VkPhysicalDevice(pPhysicalDevices.get(i), instance)
                val properties = VkPhysicalDeviceProperties.calloc(stack)
                vkGetPhysicalDeviceProperties(device, properties)

                if (properties.deviceType() == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) {
                    selectedDevice = device
                    break
                }
                if (selectedDevice == null) {
                    selectedDevice = device
                }
            }
            selectedDevice
        }
    }

    private fun createLogicalDevice(physicalDevice: VkPhysicalDevice, config: RendererConfig): VkDevice? {
        return MemoryStack.stackPush().use { stack ->
            // Queue create info (graphics queue family 0)
            val queuePriority = stack.callocFloat(1).put(0, 1.0f)
            val queueCreateInfo = VkDeviceQueueCreateInfo.calloc(1, stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(0)
                .pQueuePriorities(queuePriority)

            // Device features
            val deviceFeatures = VkPhysicalDeviceFeatures.calloc(stack)

            // T019: Enable swapchain extension for presentation
            val extensions = stack.callocPointer(1)
            extensions.put(0, stack.UTF8(org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME))

            // Device create info
            val createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfo)
                .pEnabledFeatures(deviceFeatures)
                .ppEnabledExtensionNames(extensions)

            // Create device
            val pDevice = stack.callocPointer(1)
            val result = vkCreateDevice(physicalDevice, createInfo, null, pDevice)
            if (result != VK_SUCCESS) {
                null
            } else {
                VkDevice(pDevice.get(0), physicalDevice, createInfo)
            }
        }
    }

    private fun createCommandPool(device: VkDevice): Long {
        return MemoryStack.stackPush().use { stack ->
            val createInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(0)

            val pCommandPool = stack.callocLong(1)
            val result = vkCreateCommandPool(device, createInfo, null, pCommandPool)
            if (result != VK_SUCCESS) VK_NULL_HANDLE else pCommandPool.get(0)
        }
    }

    private fun allocateCommandBuffer(device: VkDevice, commandPool: Long): VkCommandBuffer? {
        return MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)

            val pCommandBuffer = stack.callocPointer(1)
            val result = vkAllocateCommandBuffers(device, allocInfo, pCommandBuffer)
            if (result != VK_SUCCESS) null else VkCommandBuffer(pCommandBuffer.get(0), device)
        }
    }

    private fun queryCapabilities(physicalDevice: VkPhysicalDevice): RendererCapabilities {
        return MemoryStack.stackPush().use { stack ->
            val properties = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(physicalDevice, properties)

            val deviceNameBytes = ByteArray(VK_MAX_PHYSICAL_DEVICE_NAME_SIZE)
            properties.deviceName().get(deviceNameBytes)
            val deviceName = String(deviceNameBytes).trim('\u0000')

            val limits = properties.limits()

            RendererCapabilities(
                backend = BackendType.VULKAN,
                deviceName = deviceName,
                driverVersion = properties.driverVersion().toString(),
                supportsCompute = true,
                supportsRayTracing = false, // Would need to query extensions
                supportsMultisampling = true,
                maxTextureSize = limits.maxImageDimension2D(),
                maxSamples = getSampleCountFromVulkanSampleCount(limits.framebufferColorSampleCounts()),
                maxUniformBufferBindings = limits.maxPerStageDescriptorUniformBuffers(),
                depthTextures = true,
                floatTextures = true,
                instancedRendering = true,
                extensions = emptySet() // TODO: Query actual extensions
            )
        }
    }

    private fun getSampleCountFromVulkanSampleCount(sampleCountFlags: Int): Int {
        return when {
            (sampleCountFlags and VK_SAMPLE_COUNT_64_BIT) != 0 -> 64
            (sampleCountFlags and VK_SAMPLE_COUNT_32_BIT) != 0 -> 32
            (sampleCountFlags and VK_SAMPLE_COUNT_16_BIT) != 0 -> 16
            (sampleCountFlags and VK_SAMPLE_COUNT_8_BIT) != 0 -> 8
            (sampleCountFlags and VK_SAMPLE_COUNT_4_BIT) != 0 -> 4
            (sampleCountFlags and VK_SAMPLE_COUNT_2_BIT) != 0 -> 2
            else -> 1
        }
    }

    private fun getPhysicalDeviceInfo(physicalDevice: VkPhysicalDevice): String {
        return MemoryStack.stackPush().use { stack ->
            val properties = VkPhysicalDeviceProperties.calloc(stack)
            vkGetPhysicalDeviceProperties(physicalDevice, properties)

            val deviceNameBytes = ByteArray(VK_MAX_PHYSICAL_DEVICE_NAME_SIZE)
            properties.deviceName().get(deviceNameBytes)
            val deviceName = String(deviceNameBytes).trim('\u0000')

            "$deviceName (Vulkan ${VK_VERSION_MAJOR(properties.apiVersion())}.${VK_VERSION_MINOR(properties.apiVersion())}.${
                VK_VERSION_PATCH(
                    properties.apiVersion()
                )
            })"
        }
    }

    /**
     * Create render pass for rendering.
     * T018: Used by RenderPassManager.
     */
    private fun createRenderPass(device: VkDevice, imageFormat: Int): Long {
        return MemoryStack.stackPush().use { stack ->
            // Color attachment description
            val colorAttachment = VkAttachmentDescription.calloc(1, stack)
                .format(imageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)

            // Color attachment reference
            val colorAttachmentRef = VkAttachmentReference.calloc(1, stack)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)

            // Subpass description
            val subpass = VkSubpassDescription.calloc(1, stack)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .pColorAttachments(colorAttachmentRef)

            // Render pass create info
            val renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(colorAttachment)
                .pSubpasses(subpass)

            val pRenderPass = stack.mallocLong(1)
            val result = vkCreateRenderPass(device, renderPassInfo, null, pRenderPass)
            if (result != VK_SUCCESS) VK_NULL_HANDLE else pRenderPass.get(0)
        }
    }

    /**
     * Extract VkSurfaceKHR from RenderSurface.
     * T019: Used for SwapchainManager initialization.
     */
    private fun extractVulkanSurface(surface: RenderSurface): Long {
        // VulkanSurface should have a method to get the VkSurfaceKHR handle
        // For now, we'll use reflection or expect a specific type
        return when (surface) {
            is VulkanSurface -> surface.getSurfaceHandle()
            else -> throw IllegalArgumentException("RenderSurface must be VulkanSurface for Vulkan renderer")
        }
    }

    private fun updateStats(frameTimeMs: Long, drawCalls: Int, triangleCount: Int) {
        val currentTime = System.currentTimeMillis()
        lastFrameTime = currentTime

        val instantFps = if (frameTimeMs > 0) 1000.0 / frameTimeMs else 0.0
        fpsAccumulator += instantFps
        fpsFrameCount++

        val averageFps = fpsAccumulator / fpsFrameCount

        val bufferMemory = meshBuffers.values.sumOf {
            it.vertexBuffer.size.toLong() + (it.indexBuffer?.size?.toLong() ?: 0L)
        }

        stats = RenderStats(
            fps = averageFps,
            frameTime = frameTimeMs.toDouble(),
            triangles = triangleCount,
            drawCalls = drawCalls,
            textureMemory = 0L,
            bufferMemory = bufferMemory,
            timestamp = currentTime
        )

        if (fpsFrameCount >= 60) {
            fpsAccumulator = 0.0
            fpsFrameCount = 0
        }
    }
}
