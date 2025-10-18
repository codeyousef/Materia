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
import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.geometry.GeometryBuilder
import io.kreekt.renderer.geometry.GeometryBuildOptions
import io.kreekt.renderer.geometry.GeometryMetadata
import io.kreekt.renderer.geometry.buildGeometryOptions
import io.kreekt.renderer.webgpu.VertexAttribute
import io.kreekt.renderer.webgpu.VertexBufferLayout
import io.kreekt.renderer.webgpu.VertexFormat
import io.kreekt.renderer.webgpu.VertexStepMode
import io.kreekt.renderer.webgpu.PrimitiveTopology
import io.kreekt.renderer.webgpu.CullMode
import io.kreekt.renderer.webgpu.FrontFace
import io.kreekt.renderer.webgpu.TextureFormat
import io.kreekt.renderer.webgpu.ColorWriteMask
import io.kreekt.renderer.webgpu.BlendFactor
import io.kreekt.renderer.webgpu.BlendOperation
import io.kreekt.renderer.*
import io.kreekt.renderer.material.MaterialDescriptor
import io.kreekt.renderer.material.MaterialDescriptorRegistry
import io.kreekt.renderer.material.MaterialRenderState
import io.kreekt.renderer.feature020.*
import io.kreekt.renderer.gpu.GpuBackend
import io.kreekt.renderer.gpu.GpuContext
import io.kreekt.renderer.gpu.GpuDeviceFactory
import io.kreekt.renderer.gpu.GpuDiagnostics
import io.kreekt.renderer.gpu.GpuPowerPreference
import io.kreekt.renderer.gpu.GpuRequestConfig
import io.kreekt.renderer.gpu.queueFamilyIndex
import io.kreekt.renderer.gpu.unwrapDescriptorPool
import io.kreekt.renderer.gpu.unwrapHandle
import io.kreekt.renderer.gpu.unwrapInstance
import io.kreekt.renderer.gpu.unwrapPhysicalHandle
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.KHRSwapchain.*
import kotlin.system.measureTimeMillis
import kotlin.math.roundToInt
import kotlin.math.max

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
    private var queueFamilyIndex: Int = 0

    // Feature 020 Managers (T017-T019)
    private var bufferManager: VulkanBufferManager? = null
    private var renderPassManager: VulkanRenderPassManager? = null
    private var swapchainManager: VulkanSwapchain? = null

    private val pipelineCache = object : LinkedHashMap<PipelineCacheKey, VulkanPipeline>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PipelineCacheKey, VulkanPipeline>?): Boolean {
            if (size > MAX_PIPELINE_CACHE_SIZE) {
                eldest?.value?.dispose()
                return true
            }
            return false
        }
    }
    private var activePipelineKey: PipelineCacheKey? = null
    private val morphWarningMeshes = mutableSetOf<Int>()
    private var descriptorSetLayout: Long = VK_NULL_HANDLE
    private var descriptorPool: Long = VK_NULL_HANDLE
    private var descriptorSet: Long = VK_NULL_HANDLE
    private var materialTextureDescriptorSetLayout: Long = VK_NULL_HANDLE
    private var materialTextureDescriptorPool: Long = VK_NULL_HANDLE
    private var materialTextureManager: VulkanMaterialTextureManager? = null
    private var uniformBuffer: BufferHandle? = null
    private var swapchainFramebuffers: List<VulkanFramebufferData> = emptyList()
    private var gpuContext: GpuContext? = null

    private val meshBuffers: MutableMap<Int, VulkanMeshBuffers> = mutableMapOf()
    private var depthStateWarningIssued = false

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
            println("T033: Requesting GPU context via abstraction...")
            val request = GpuRequestConfig(
                preferredBackend = GpuBackend.VULKAN,
                powerPreference = when (config.powerPreference) {
                    PowerPreference.LOW_POWER -> GpuPowerPreference.LOW_POWER
                    PowerPreference.HIGH_PERFORMANCE -> GpuPowerPreference.HIGH_PERFORMANCE
                },
                forceFallbackAdapter = false,
                label = "VulkanRenderer"
            )
            val context = GpuDeviceFactory.requestContext(request)
            gpuContext = context

            val vkInstance = context.device.unwrapInstance() as? VkInstance
                ?: throw IllegalStateException("Vulkan instance handle unavailable from GPU context")
            val vkPhysicalDevice = context.device.unwrapPhysicalHandle() as? VkPhysicalDevice
                ?: throw IllegalStateException("Vulkan physical device handle unavailable from GPU context")
            val vkDevice = context.device.unwrapHandle() as? VkDevice
                ?: throw IllegalStateException("Vulkan logical device handle unavailable from GPU context")
            val vkQueue = context.queue.unwrapHandle() as? VkQueue
                ?: throw IllegalStateException("Vulkan queue handle unavailable from GPU context")

            instance = vkInstance
            physicalDevice = vkPhysicalDevice
            device = vkDevice
            graphicsQueue = vkQueue
            queueFamilyIndex = context.queue.queueFamilyIndex()
            descriptorPool = (context.device.unwrapDescriptorPool() as? Long) ?: VK_NULL_HANDLE

            println("T033: GPU context acquired for device '${context.device.info.name}' (queueFamily=$queueFamilyIndex)")

            // Create surface prior to swapchain setup
            if (surface is VulkanSurface) {
                println("T033: Creating Vulkan surface...")
                surface.createSurface(vkInstance)
                println("T033: Surface created successfully")
            }

            // Command resources
            println("T033: Creating command pool (queueFamily=$queueFamilyIndex)...")
            commandPool = createCommandPool(vkDevice, queueFamilyIndex)
            if (commandPool == VK_NULL_HANDLE) {
                return io.kreekt.core.Result.Error(
                    "Failed to create command pool",
                    io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        getPhysicalDeviceInfo(vkPhysicalDevice),
                        "Failed to create command pool"
                    )
                )
            }
            println("T033: Command pool created successfully")

            println("T033: Allocating command buffer...")
            commandBuffer = allocateCommandBuffer(vkDevice, commandPool)
            println("T033: Command buffer allocated successfully")

            // Swapchain + render pass
            println("T033: Initializing SwapchainManager...")
            val vulkanSurface = extractVulkanSurface(surface)
            swapchainManager = VulkanSwapchain(vkDevice, vkPhysicalDevice, vulkanSurface)
            println("T033: SwapchainManager initialized successfully")

            println("T033: Creating render pass...")
            val imageFormat = swapchainManager!!.getImageFormat()
            renderPass = createRenderPass(vkDevice, imageFormat)
            if (renderPass == VK_NULL_HANDLE) {
                return io.kreekt.core.Result.Error(
                    "Failed to create render pass",
                    io.kreekt.renderer.RendererInitializationException.DeviceCreationFailedException(
                        BackendType.VULKAN,
                        getPhysicalDeviceInfo(vkPhysicalDevice),
                        "Failed to create render pass"
                    )
                )
            }
            println("T033: Render pass created successfully")

            // Feature 020 managers
            println("T033: Initializing BufferManager...")
            bufferManager = VulkanBufferManager(vkDevice, vkPhysicalDevice)
            println("T033: BufferManager initialized successfully")

            println("T033: Initializing RenderPassManager...")
            renderPassManager = VulkanRenderPassManager(vkDevice, commandBuffer!!, renderPass)
            println("T033: RenderPassManager initialized successfully")

            println("T033: Creating swapchain framebuffers...")
            swapchainFramebuffers = swapchainManager!!.createFramebuffers(renderPass)
            println("T033: Framebuffers created (${swapchainFramebuffers.size} images)")

            println("T033: Creating descriptor resources...")
            createDescriptorResources()
            println("T033: Descriptor resources ready")

            println("T033: Creating material texture resources...")
            createMaterialTextureResources()
            materialTextureManager = VulkanMaterialTextureManager(
                vkDevice,
                vkPhysicalDevice,
                commandPool,
                vkQueue
            )
            println("T033: Material texture resources ready")

            println("T033: Preparing pipeline cache...")
            pipelineCache.values.forEach { it.dispose() }
            pipelineCache.clear()
            activePipelineKey = null
            println("T033: Pipeline cache reset; pipelines will be created on demand.")

            println("T033: Querying device capabilities...")
            capabilities = queryCapabilities(vkPhysicalDevice)
            println("T033: Capabilities detected: maxTextureSize=${capabilities.maxTextureSize}, maxSamples=${capabilities.maxSamples}")
            GpuDiagnostics.logContext(context, capabilities)

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
        if (swapchainFramebuffers.isEmpty() || renderPassManager == null) {
            recreateSwapchainResources()
            return
        }
        val renderPassMgr = renderPassManager!!

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
                val material = node.material ?: return@traverseVisible
                val descriptor = MaterialDescriptorRegistry.descriptorFor(material) ?: return@traverseVisible
                val buffers = ensureMeshBuffers(node, descriptor) ?: return@traverseVisible

                val textureBinding = if (
                    materialTextureDescriptorSetLayout != VK_NULL_HANDLE &&
                    materialTextureDescriptorPool != VK_NULL_HANDLE &&
                    materialTextureManager != null
                ) {
                    materialTextureManager!!.prepare(
                        material = material,
                        descriptor = descriptor,
                        descriptorPool = materialTextureDescriptorPool,
                        descriptorSetLayout = materialTextureDescriptorSetLayout
                    ) ?: return@traverseVisible
                } else {
                    VulkanMaterialTextureManager.MaterialTextureBinding(VK_NULL_HANDLE)
                }

                val environmentBindingForMesh = if (descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER)) {
                    environmentBindingForFrame ?: return@traverseVisible
                } else {
                    environmentBindingForFrame
                }

                drawInfos += MeshDrawInfo(node, descriptor, buffers, textureBinding, environmentBindingForMesh)
                retainedIds += node.id
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

                val descriptorHandle = descriptorSet
                val extent = swapchain.getExtent()
                val deviceHandle = device ?: return@measureTimeMillis

                for (drawInfo in drawInfos) {
                    val buffers = drawInfo.buffers
                    val materialDescriptor = drawInfo.descriptor

                    val hasMaterialSet = materialTextureDescriptorSetLayout != VK_NULL_HANDLE &&
                        drawInfo.textureBinding.descriptorSet != VK_NULL_HANDLE

                    val descriptorSetLayouts = if (hasMaterialSet) {
                        longArrayOf(descriptorSetLayout, materialTextureDescriptorSetLayout)
                    } else {
                        longArrayOf(descriptorSetLayout)
                    }

                    val pipelineKey = createPipelineKey(buffers.vertexLayouts, materialDescriptor.renderState)
                    val pipelineForDraw = pipelineCache.getOrPut(pipelineKey) {
                        warnDepthStateIfNeeded(materialDescriptor.renderState)
                        val newPipeline = VulkanPipeline(deviceHandle)
                        if (!newPipeline.createPipeline(
                                renderPass,
                                extent.first,
                                extent.second,
                                descriptorSetLayouts,
                                buffers.vertexLayouts,
                                materialDescriptor.renderState
                            )
                        ) {
                            newPipeline.dispose()
                            throw RuntimeException("Failed to create Vulkan graphics pipeline for vertex layout/state")
                        }
                        newPipeline
                    }

                    if (activePipelineKey != pipelineKey) {
                        renderPassMgr.bindPipeline(PipelineHandle(pipelineForDraw.getPipelineHandle()))
                        activePipelineKey = pipelineKey
                    }

                    val mvp = Matrix4()
                    mvp.multiplyMatrices(viewProjectionMatrix, drawInfo.mesh.matrixWorld)
                    updateUniformBuffer(mvp)

                    val pipelineLayout = pipelineForDraw.getPipelineLayout()
                    val descriptorSetsBuffer = if (hasMaterialSet) {
                        val buffer = stack.mallocLong(2)
                        buffer.put(0, descriptorHandle)
                        buffer.put(1, drawInfo.textureBinding.descriptorSet)
                        buffer
                    } else {
                        stack.longs(descriptorHandle)
                    }
                    vkCmdBindDescriptorSets(
                        command,
                        VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayout,
                        0,
                        descriptorSetsBuffer,
                        null
                    )

                    buffers.vertexBuffers.forEachIndexed { slot, buffer ->
                        renderPassMgr.bindVertexBuffer(buffer, slot)
                    }
                    buffers.indexBuffer?.let { renderPassMgr.bindIndexBuffer(it) }
                    renderPassMgr.drawIndexed(buffers.indexCount, 0, buffers.instanceCount)
                    drawCalls++
                    triangles += buffers.triangleCount
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

        materialTextureManager?.dispose()
        materialTextureManager = null

        pipelineCache.values.forEach { it.dispose() }
        pipelineCache.clear()
        activePipelineKey = null

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
        createMaterialTextureResources()
        if (physicalDevice != null && graphicsQueue != null) {
            materialTextureManager = VulkanMaterialTextureManager(
                deviceHandle,
                physicalDevice!!,
                commandPool,
                graphicsQueue!!
            )
            environmentManager = VulkanEnvironmentManager(
                deviceHandle,
                physicalDevice!!,
                commandPool,
                graphicsQueue!!
            )
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

    private fun createMaterialTextureResources() {
        val deviceHandle = device ?: return

        if (materialTextureDescriptorSetLayout == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val bindings = VkDescriptorSetLayoutBinding.calloc(2, stack)
                bindings[0]
                    .binding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                bindings[1]
                    .binding(1)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)

                val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings)

                val pLayout = stack.mallocLong(1)
                val result = vkCreateDescriptorSetLayout(deviceHandle, layoutInfo, null, pLayout)
                if (result != VK_SUCCESS) {
                    throw RuntimeException("Failed to create material texture descriptor set layout: VkResult=$result")
                }
                materialTextureDescriptorSetLayout = pLayout[0]
            }
        }

        if (materialTextureDescriptorPool == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val poolSize = VkDescriptorPoolSize.calloc(1, stack)
                    .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_MATERIAL_TEXTURE_SETS * 2)

                val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSize)
                    .maxSets(MAX_MATERIAL_TEXTURE_SETS)

                val pPool = stack.mallocLong(1)
                val result = vkCreateDescriptorPool(deviceHandle, poolInfo, null, pPool)
                if (result != VK_SUCCESS) {
                    throw RuntimeException("Failed to create material texture descriptor pool: VkResult=$result")
                }
                materialTextureDescriptorPool = pPool[0]
            }
        }
    }

    private fun ensureMeshBuffers(mesh: Mesh, descriptor: MaterialDescriptor): VulkanMeshBuffers? {
        val bufferMgr = bufferManager ?: return null
        val geometry = mesh.geometry

        val attributesNeedUpdate = geometry.attributes.values.any { it.needsUpdate }
        val indexNeedsUpdate = geometry.index?.needsUpdate == true
        val existing = meshBuffers[mesh.id]
        if (!attributesNeedUpdate && !indexNeedsUpdate && existing != null) {
            return existing
        }

        meshBuffers.remove(mesh.id)?.let { destroyMeshBuffers(it) }
        morphWarningMeshes.remove(mesh.id)

        val buildOptions = descriptor.buildGeometryOptions(geometry)
        val geometryBuffer = GeometryBuilder.build(geometry, buildOptions)
        val vertexStreams = geometryBuffer.streams
        if (vertexStreams.isEmpty()) {
            return null
        }

        val vertexBuffers = vertexStreams.map { stream ->
            bufferMgr.createVertexBuffer(stream.data)
        }
        val vertexLayouts = vertexStreams.map { it.layout }

        val indexArray = geometryBuffer.indexData ?: IntArray(geometryBuffer.vertexCount) { it }
        val indexBuffer = bufferMgr.createIndexBuffer(indexArray)
        val indexCount = indexArray.size
        val triangles = if (indexCount > 0) indexCount / 3 else 0
        val instanceCount = if (geometryBuffer.instanceCount > 0) geometryBuffer.instanceCount else 1

        if (geometryBuffer.metadata.hasMorphTargets && morphWarningMeshes.add(mesh.id)) {
            println("Warning: VulkanRenderer detected morph targets on mesh ${mesh.name}; falling back to base geometry")
        }

        geometry.attributes.values.forEach { it.needsUpdate = false }
        geometry.index?.needsUpdate = false

        val buffers = VulkanMeshBuffers(
            vertexBuffers = vertexBuffers,
            indexBuffer = indexBuffer,
            indexCount = indexCount,
            triangleCount = triangles,
            vertexLayouts = vertexLayouts,
            metadata = geometryBuffer.metadata,
            instanceCount = instanceCount
        )
        meshBuffers[mesh.id] = buffers
        return buffers
    }

    private fun defaultVertexLayouts(): List<VertexBufferLayout> = listOf(
        VertexBufferLayout(
            arrayStride = (POSITION_COMPONENTS + COLOR_COMPONENTS) * 4,
            stepMode = VertexStepMode.VERTEX,
            attributes = listOf(
                VertexAttribute(
                    format = VertexFormat.FLOAT32X3,
                    offset = 0,
                    shaderLocation = 0
                ),
                VertexAttribute(
                    format = VertexFormat.FLOAT32X3,
                    offset = POSITION_COMPONENTS * 4,
                    shaderLocation = 1
                )
            )
        )
    )

    private fun destroyMeshBuffers(buffers: VulkanMeshBuffers) {
        buffers.vertexBuffers.forEach { buffer ->
            try {
                bufferManager?.destroyBuffer(buffer)
            } catch (_: Exception) {
            }
        }

        buffers.indexBuffer?.let { buffer ->
            try {
                bufferManager?.destroyBuffer(buffer)
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
        val vertexBuffers: List<BufferHandle>,
        val indexBuffer: BufferHandle?,
        val indexCount: Int,
        val triangleCount: Int,
        val vertexLayouts: List<VertexBufferLayout>,
        val metadata: GeometryMetadata,
        val instanceCount: Int
    )

    private data class MeshDrawInfo(
        val mesh: Mesh,
        val descriptor: MaterialDescriptor,
        val buffers: VulkanMeshBuffers,
        val textureBinding: VulkanMaterialTextureManager.MaterialTextureBinding
    )

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val UNIFORM_BUFFER_SIZE = 64
        private const val MAX_PIPELINE_CACHE_SIZE = 32
        private const val MAX_MATERIAL_TEXTURE_SETS = 256
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

        pipelineCache.values.forEach { it.dispose() }
        pipelineCache.clear()
        activePipelineKey = null

        materialTextureManager?.dispose()
        materialTextureManager = null

        if (materialTextureDescriptorPool != VK_NULL_HANDLE && device != null) {
            vkDestroyDescriptorPool(device!!, materialTextureDescriptorPool, null)
            materialTextureDescriptorPool = VK_NULL_HANDLE
        }

        if (materialTextureDescriptorSetLayout != VK_NULL_HANDLE && device != null) {
            vkDestroyDescriptorSetLayout(device!!, materialTextureDescriptorSetLayout, null)
            materialTextureDescriptorSetLayout = VK_NULL_HANDLE
        }

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
        gpuContext = null

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

    private fun createCommandPool(device: VkDevice, queueFamily: Int): Long {
        return MemoryStack.stackPush().use { stack ->
            val createInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(queueFamily)

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
            val extensionNames = enumerateDeviceExtensions(physicalDevice, stack)

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
                extensions = extensionNames
            )
        }
    }

    private fun enumerateDeviceExtensions(device: VkPhysicalDevice, stack: MemoryStack): Set<String> {
        val countBuffer = stack.mallocInt(1)
        val initialResult = vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, countBuffer, null)
        if (initialResult != VK_SUCCESS) {
            return emptySet()
        }

        val count = countBuffer[0]
        if (count == 0) return emptySet()

        val propertiesBuffer = VkExtensionProperties.calloc(count, stack)
        countBuffer.put(0, count)
        val enumerationResult =
            vkEnumerateDeviceExtensionProperties(device, null as ByteBuffer?, countBuffer, propertiesBuffer)
        if (enumerationResult != VK_SUCCESS) {
            return emptySet()
        }

        val names = HashSet<String>(count)
        for (i in 0 until count) {
            names.add(propertiesBuffer[i].extensionNameString())
        }
        return names
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

    private data class PipelineCacheKey(
        val vertexLayouts: List<VertexLayoutSignature>,
        val renderState: RenderStateSignature
    )

    private data class VertexLayoutSignature(
        val arrayStride: Int,
        val stepMode: VertexStepMode,
        val attributes: List<VertexAttributeSignature>
    )

    private data class VertexAttributeSignature(
        val format: VertexFormat,
        val offset: Int,
        val shaderLocation: Int
    )

    private data class RenderStateSignature(
        val topology: PrimitiveTopology,
        val cullMode: CullMode,
        val frontFace: FrontFace,
        val colorTarget: ColorTargetSignature
    )

    private data class ColorTargetSignature(
        val format: TextureFormat,
        val blend: BlendSignature?,
        val writeMask: ColorWriteMask
    )

    private data class BlendSignature(
        val color: BlendComponentSignature,
        val alpha: BlendComponentSignature
    )

    private data class BlendComponentSignature(
        val srcFactor: BlendFactor,
        val dstFactor: BlendFactor,
        val operation: BlendOperation
    )

    private fun createPipelineKey(
        vertexLayouts: List<VertexBufferLayout>,
        renderState: MaterialRenderState
    ): PipelineCacheKey {
        val layoutSignature = vertexLayouts.map { layout ->
            VertexLayoutSignature(
                arrayStride = layout.arrayStride,
                stepMode = layout.stepMode,
                attributes = layout.attributes.map { attr ->
                    VertexAttributeSignature(
                        format = attr.format,
                        offset = attr.offset,
                        shaderLocation = attr.shaderLocation
                    )
                }
            )
        }

        val blendSignature = renderState.colorTarget.blendState?.let { blend ->
            BlendSignature(
                color = BlendComponentSignature(
                    srcFactor = blend.color.srcFactor,
                    dstFactor = blend.color.dstFactor,
                    operation = blend.color.operation
                ),
                alpha = BlendComponentSignature(
                    srcFactor = blend.alpha.srcFactor,
                    dstFactor = blend.alpha.dstFactor,
                    operation = blend.alpha.operation
                )
            )
        }

        val renderSignature = RenderStateSignature(
            topology = renderState.topology,
            cullMode = renderState.cullMode,
            frontFace = renderState.frontFace,
            colorTarget = ColorTargetSignature(
                format = renderState.colorTarget.format,
                blend = blendSignature,
                writeMask = renderState.colorTarget.writeMask
            )
        )

        return PipelineCacheKey(layoutSignature, renderSignature)
    }

    private fun warnDepthStateIfNeeded(renderState: MaterialRenderState) {
        if ((renderState.depthTest || renderState.depthWrite) && !depthStateWarningIssued) {
            println("Warning: Depth testing requested but VulkanRenderer currently lacks depth attachments; depthTest/depthWrite are ignored.")
            depthStateWarningIssued = true
        }
    }

    private fun updateStats(frameTimeMs: Long, drawCalls: Int, triangleCount: Int) {
        val currentTime = System.currentTimeMillis()
        lastFrameTime = currentTime

        val instantFps = if (frameTimeMs > 0) 1000.0 / frameTimeMs else 0.0
        fpsAccumulator += instantFps
        fpsFrameCount++

        val averageFps = fpsAccumulator / fpsFrameCount

        val bufferMemory = meshBuffers.values.sumOf { buffers ->
            val vertexBytes = buffers.vertexBuffers.sumOf { it.size.toLong() }
            val indexBytes = buffers.indexBuffer?.size?.toLong() ?: 0L
            vertexBytes + indexBytes
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
