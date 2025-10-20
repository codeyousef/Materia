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
import io.kreekt.core.scene.Material
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.core.math.Matrix4
import io.kreekt.geometry.BufferGeometry
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.geometry.GeometryBuilder
import io.kreekt.renderer.geometry.GeometryBuildOptions
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.material.MeshStandardMaterial
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
import io.kreekt.renderer.material.MaterialBinding
import io.kreekt.renderer.material.MaterialBindingSource
import io.kreekt.renderer.material.MaterialBindingType
import io.kreekt.renderer.material.MaterialDescriptor
import io.kreekt.renderer.material.MaterialDescriptorRegistry
import io.kreekt.renderer.material.MaterialRenderState
import io.kreekt.renderer.material.requiresBinding
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
import java.nio.ByteOrder
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.*
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.KHRSwapchain.*
import kotlin.system.measureTimeMillis
import kotlin.math.roundToInt
import kotlin.math.max
import java.util.Locale

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
    private var environmentManager: VulkanEnvironmentManager? = null
    private var uniformBuffer: BufferHandle? = null
    private var swapchainFramebuffers: List<VulkanFramebufferData> = emptyList()
    private var gpuContext: GpuContext? = null

    private val meshBuffers: MutableMap<Int, VulkanMeshBuffers> = mutableMapOf()
    private var depthStateWarningIssued = false

    private val materialTextureBindingLayout: List<MaterialBinding> =
        MaterialDescriptorRegistry.materialTextureBindingLayout()
    private val environmentBindingLayout: List<MaterialBinding> =
        MaterialDescriptorRegistry.environmentBindingLayout()
    private val materialBindingLookup: Map<MaterialBindingSource, List<MaterialBinding>> =
        materialTextureBindingLayout.groupBy { it.source }
    private val environmentBindingLookup: Map<MaterialBindingSource, List<MaterialBinding>> =
        environmentBindingLayout.groupBy { it.source }

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
                vkQueue,
                materialTextureBindingLayout
            )
            println("T033: Material texture resources ready")

            environmentManager = VulkanEnvironmentManager(
                vkDevice,
                vkPhysicalDevice,
                commandPool,
                vkQueue,
                environmentBindingLayout
            )
            println("T033: Environment resources ready")

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

        val projectionMatrix = camera.projectionMatrix
        val viewMatrix = camera.matrixWorldInverse

        val environmentMgr = environmentManager
        val sceneEnvironmentBrdf = scene.environmentBrdfLut as? Texture2D
        val sceneEnvironmentBinding = environmentMgr?.prepare(scene.environment, sceneEnvironmentBrdf)

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

                val requiresEnvironment = descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER)

                val environmentBindingForMesh = if (requiresEnvironment) {
                    val materialEnv = (material as? MeshStandardMaterial)?.envMap
                    val cubeTexture = materialEnv ?: scene.environment
                    if (cubeTexture == null) {
                        return@traverseVisible
                    }
                    environmentMgr?.prepare(cubeTexture, sceneEnvironmentBrdf) ?: return@traverseVisible
                } else {
                    sceneEnvironmentBinding
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
                    val environmentBinding = drawInfo.environmentBinding
                    val hasEnvironmentSet = environmentBinding != null &&
                        environmentBinding.descriptorSet != VK_NULL_HANDLE

                    val descriptorSetLayouts = buildList {
                        add(descriptorSetLayout)
                        if (hasMaterialSet) add(materialTextureDescriptorSetLayout)
                        if (hasEnvironmentSet) add(environmentBinding!!.layout)
                    }.toLongArray()

                    val material = drawInfo.mesh.material ?: continue

                    val shaderConfig = buildShaderProgramConfig(
                        material = material,
                        descriptor = materialDescriptor,
                        metadata = buffers.metadata,
                        vertexLayouts = buffers.vertexLayouts,
                        hasEnvironmentBinding = hasEnvironmentSet
                    )

                    val pipelineKey = createPipelineKey(
                        buffers.vertexLayouts,
                        materialDescriptor.renderState,
                        shaderConfig.features
                    )
                    val pipelineForDraw = pipelineCache.getOrPut(pipelineKey) {
                        warnDepthStateIfNeeded(materialDescriptor.renderState)
                        val newPipeline = VulkanPipeline(deviceHandle)
                        if (!newPipeline.createPipeline(
                                renderPass,
                                extent.first,
                                extent.second,
                                descriptorSetLayouts,
                                buffers.vertexLayouts,
                                materialDescriptor.renderState,
                                shaderConfig.vertexSource,
                                shaderConfig.fragmentSource
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

                val modelMatrix = drawInfo.mesh.matrixWorld

                val baseColor = when (material) {
                    is MeshStandardMaterial -> floatArrayOf(
                        material.color.r,
                        material.color.g,
                            material.color.b,
                            material.opacity
                        )

                        is MeshBasicMaterial -> floatArrayOf(
                            material.color.r,
                            material.color.g,
                            material.color.b,
                            material.opacity
                        )

                        else -> floatArrayOf(1f, 1f, 1f, 1f)
                    }

                    val prefilterMipCount = (environmentBinding?.mipCount ?: 1).toFloat()

                    val pbrParams = when (material) {
                        is MeshStandardMaterial -> floatArrayOf(
                            material.roughness,
                            material.metalness,
                            material.envMapIntensity,
                            prefilterMipCount
                        )

                        else -> floatArrayOf(1f, 0f, 0f, prefilterMipCount)
                    }

                    val aoIntensity = when (material) {
                        is MeshStandardMaterial -> material.aoMapIntensity
                        else -> 1f
                    }

                    val cameraPositionVector = floatArrayOf(
                        camera.position.x,
                        camera.position.y,
                        camera.position.z,
                        aoIntensity
                    )

                    updateUniformBuffer(
                        projectionMatrix,
                        viewMatrix,
                        modelMatrix,
                        baseColor,
                        pbrParams,
                        cameraPositionVector
                    )

                    val pipelineLayout = pipelineForDraw.getPipelineLayout()
                    val descriptorSetsBuffer = stack.mallocLong(1 + (if (hasMaterialSet) 1 else 0) + (if (hasEnvironmentSet) 1 else 0))
                    descriptorSetsBuffer.put(0, descriptorHandle)
                    var descriptorIndex = 1
                    if (hasMaterialSet) {
                        descriptorSetsBuffer.put(descriptorIndex, drawInfo.textureBinding.descriptorSet)
                        descriptorIndex += 1
                    }
                    if (hasEnvironmentSet) {
                        descriptorSetsBuffer.put(descriptorIndex, environmentBinding!!.descriptorSet)
                        descriptorIndex += 1
                    }
                    descriptorSetsBuffer.limit(descriptorIndex)
                    descriptorSetsBuffer.position(0)
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
        environmentManager?.dispose()
        environmentManager = null

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
                graphicsQueue!!,
                materialTextureBindingLayout
            )
            environmentManager = VulkanEnvironmentManager(
                deviceHandle,
                physicalDevice!!,
                commandPool,
                graphicsQueue!!,
                environmentBindingLayout
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

        val identity = Matrix4()
        updateUniformBuffer(
            projection = identity,
            view = identity,
            model = identity,
            baseColor = floatArrayOf(1f, 1f, 1f, 1f),
            pbrParams = floatArrayOf(1f, 0f, 0f, 1f),
            cameraPosition = floatArrayOf(0f, 0f, 0f, 1f)
        )
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

        val layoutBindings = materialTextureBindingLayout

        if (materialTextureDescriptorSetLayout == VK_NULL_HANDLE) {
            MemoryStack.stackPush().use { stack ->
                val bindingsBuffer = VkDescriptorSetLayoutBinding.calloc(layoutBindings.size, stack)

                layoutBindings.forEachIndexed { index, binding ->
                    bindingsBuffer[index]
                        .binding(binding.binding)
                        .descriptorCount(1)
                        .descriptorType(binding.toVulkanDescriptorType())
                        .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT)
                }

                val layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindingsBuffer)

                val pLayout = stack.mallocLong(1)
                val result = vkCreateDescriptorSetLayout(deviceHandle, layoutInfo, null, pLayout)
                if (result != VK_SUCCESS) {
                    throw RuntimeException("Failed to create material texture descriptor set layout: VkResult=$result")
                }
                materialTextureDescriptorSetLayout = pLayout[0]
            }
        }

        if (materialTextureDescriptorPool == VK_NULL_HANDLE && layoutBindings.isNotEmpty()) {
            MemoryStack.stackPush().use { stack ->
                val sampledImageCount = layoutBindings.count { it.type != MaterialBindingType.SAMPLER }
                val samplerCount = layoutBindings.count { it.type == MaterialBindingType.SAMPLER }
                val poolSizeCount = (if (sampledImageCount > 0) 1 else 0) + (if (samplerCount > 0) 1 else 0)

                val poolSizes = VkDescriptorPoolSize.calloc(poolSizeCount, stack)
                var poolIndex = 0
                if (sampledImageCount > 0) {
                    poolSizes[poolIndex]
                        .type(VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE)
                        .descriptorCount(MAX_MATERIAL_TEXTURE_SETS * sampledImageCount)
                    poolIndex += 1
                }
                if (samplerCount > 0) {
                    poolSizes[poolIndex]
                        .type(VK_DESCRIPTOR_TYPE_SAMPLER)
                        .descriptorCount(MAX_MATERIAL_TEXTURE_SETS * samplerCount)
                }

                val poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .pPoolSizes(poolSizes)
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

    private fun updateUniformBuffer(
        projection: Matrix4,
        view: Matrix4,
        model: Matrix4,
        baseColor: FloatArray,
        pbrParams: FloatArray,
        cameraPosition: FloatArray
    ) {
        val bufferMgr = bufferManager ?: return
        val buffer = uniformBuffer ?: return
        val bytes = ByteArray(UNIFORM_BUFFER_SIZE)
        val byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        fun putMatrix(matrix: Matrix4) {
            val elements = matrix.elements
            for (i in 0 until 16) {
                byteBuffer.putFloat(elements[i])
            }
        }

        fun putVec4(array: FloatArray) {
            for (i in 0 until 4) {
                byteBuffer.putFloat(array.getOrElse(i) { if (i == 3) 1f else 0f })
            }
        }

        putMatrix(projection)
        putMatrix(view)
        putMatrix(model)
        putVec4(baseColor)
        putVec4(pbrParams)
        putVec4(cameraPosition)

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
        val textureBinding: VulkanMaterialTextureManager.MaterialTextureBinding,
        val environmentBinding: VulkanEnvironmentManager.EnvironmentBinding?
    )

    companion object {
        private const val POSITION_COMPONENTS = 3
        private const val COLOR_COMPONENTS = 3
        private const val UNIFORM_BUFFER_SIZE = 240
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

        environmentManager?.dispose()
        environmentManager = null

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

    private data class MaterialPipelineFeatures(
        val usesAlbedoMap: Boolean,
        val usesNormalMap: Boolean,
        val usesRoughnessMap: Boolean,
        val usesMetalnessMap: Boolean,
        val usesAmbientOcclusionMap: Boolean,
        val usesEnvironmentMaps: Boolean,
        val usesInstancing: Boolean
    )

    private data class ShaderProgramConfig(
        val vertexSource: String,
        val fragmentSource: String,
        val features: MaterialPipelineFeatures
    )

    private data class PipelineCacheKey(
        val vertexLayouts: List<VertexLayoutSignature>,
        val renderState: RenderStateSignature,
        val materialFeatures: MaterialPipelineFeatures
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
        renderState: MaterialRenderState,
        features: MaterialPipelineFeatures
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

        return PipelineCacheKey(layoutSignature, renderSignature, features)
    }

    private fun warnDepthStateIfNeeded(renderState: MaterialRenderState) {
        if ((renderState.depthTest || renderState.depthWrite) && !depthStateWarningIssued) {
            println("Warning: Depth testing requested but VulkanRenderer currently lacks depth attachments; depthTest/depthWrite are ignored.")
            depthStateWarningIssued = true
        }
    }

    private fun MaterialBinding.toVulkanDescriptorType(): Int = when (type) {
        MaterialBindingType.TEXTURE_2D,
        MaterialBindingType.TEXTURE_CUBE -> VK_DESCRIPTOR_TYPE_SAMPLED_IMAGE
        MaterialBindingType.SAMPLER -> VK_DESCRIPTOR_TYPE_SAMPLER
    }

    private data class BindingPair(
        val texture: MaterialBinding,
        val sampler: MaterialBinding
    )

    private fun bindingPair(
        lookup: Map<MaterialBindingSource, List<MaterialBinding>>,
        source: MaterialBindingSource
    ): BindingPair? {
        val entries = lookup[source] ?: return null
        val texture = entries.firstOrNull { it.type != MaterialBindingType.SAMPLER }
        val sampler = entries.firstOrNull { it.type == MaterialBindingType.SAMPLER }
        return if (texture != null && sampler != null) BindingPair(texture, sampler) else null
    }

    private fun MaterialBinding.uniformName(): String {
        if (name.isEmpty()) return "uBinding${binding}"
        val first = name.first()
        val capitalized = if (first.isLowerCase()) {
            first.titlecase(Locale.ROOT) + name.substring(1)
        } else {
            name
        }
        return "u$capitalized"
    }

    private fun BindingPair.samplerRef(): String {
        val ctor = when (texture.type) {
            MaterialBindingType.TEXTURE_CUBE -> "samplerCube"
            MaterialBindingType.TEXTURE_2D -> "sampler2D"
            MaterialBindingType.SAMPLER -> "sampler2D"
        }
        return "$ctor(${texture.uniformName()}, ${sampler.uniformName()})"
    }

    private fun BindingPair.textureSample(coord: String): String = "texture(${samplerRef()}, $coord)"

    private fun MaterialBinding.glslType(): String = when (type) {
        MaterialBindingType.TEXTURE_2D -> "texture2D"
        MaterialBindingType.TEXTURE_CUBE -> "textureCube"
        MaterialBindingType.SAMPLER -> "sampler"
    }

    private fun composeVertexShader(
        metadata: GeometryMetadata,
        vertexLayouts: List<VertexBufferLayout>,
        features: MaterialPipelineFeatures
    ): String {
        val positionBinding = metadata.bindingFor(GeometryAttribute.POSITION)
            ?: error("Geometry metadata missing POSITION binding")
        val normalBinding = metadata.bindingFor(GeometryAttribute.NORMAL)
        val colorBinding = metadata.bindingFor(GeometryAttribute.COLOR)
        val tangentBinding = metadata.bindingFor(GeometryAttribute.TANGENT)
        val uvBinding = metadata.bindingFor(GeometryAttribute.UV0)

        val hasNormalAttr = normalBinding != null
        val hasColorAttr = colorBinding != null
        val hasTangentAttr = tangentBinding != null
        val hasUvAttr = uvBinding != null

        val declaredLocations = mutableSetOf(positionBinding.location)
        val instanceAttributeNames = mutableListOf<String>()

        fun declareInput(builder: StringBuilder, location: Int, glslType: String, name: String) {
            if (declaredLocations.add(location)) {
                builder.appendLine("layout(location = $location) in $glslType $name;")
            }
        }

        return buildString {
            appendLine("#version 450")
            appendLine("layout(location = ${positionBinding.location}) in vec3 inPosition;")
            if (hasNormalAttr) {
                declareInput(this, normalBinding!!.location, "vec3", "inNormal")
            }
            if (hasColorAttr) {
                declareInput(this, colorBinding!!.location, "vec3", "inColor")
            }
            if (hasTangentAttr) {
                declareInput(this, tangentBinding!!.location, "vec4", "inTangent")
            }
            if (hasUvAttr) {
                declareInput(this, uvBinding!!.location, "vec2", "inUV")
            }

            if (features.usesInstancing) {
                val instanceAttributes = vertexLayouts
                    .filter { it.stepMode == VertexStepMode.INSTANCE }
                    .flatMap { it.attributes }
                    .sortedBy { it.shaderLocation }

                var index = 0
                instanceAttributes.forEach { attr ->
                    val location = attr.shaderLocation
                    if (!declaredLocations.contains(location)) {
                        val name = "inInstanceAttr${index++}"
                        declareInput(this, location, glslTypeForVertex(attr.format), name)
                        instanceAttributeNames += name
                    }
                }
            }

            appendLine()
            appendLine("layout(location = 0) out vec3 vColor;")
            appendLine("layout(location = 1) out vec2 vUV;")
            appendLine("layout(location = 2) out vec3 vNormal;")
            appendLine("layout(location = 3) out vec3 vTangent;")
            appendLine("layout(location = 4) out vec3 vBitangent;")
            appendLine("layout(location = 5) out vec3 vWorldPos;")
            appendLine()
            appendLine("layout(set = 0, binding = 0) uniform UniformBufferObject {")
            appendLine("    mat4 uProjection;")
            appendLine("    mat4 uView;")
            appendLine("    mat4 uModel;")
            appendLine("    vec4 uBaseColor;")
            appendLine("    vec4 uPbrParams;")
            appendLine("    vec4 uCameraPosition;")
            appendLine("} ubo;")
            appendLine()
            appendLine("void main() {")
            if (features.usesInstancing) {
                val identityColumns = listOf(
                    "vec4(1.0, 0.0, 0.0, 0.0)",
                    "vec4(0.0, 1.0, 0.0, 0.0)",
                    "vec4(0.0, 0.0, 1.0, 0.0)",
                    "vec4(0.0, 0.0, 0.0, 1.0)"
                )
                val columns = (0 until 4).map { index -> instanceAttributeNames.getOrNull(index) ?: identityColumns[index] }
                appendLine("    mat4 instanceMatrix = mat4(${columns.joinToString(", ")});")
                appendLine("    mat4 modelMatrix = ubo.uModel * instanceMatrix;")
            } else {
                appendLine("    mat4 modelMatrix = ubo.uModel;")
            }
            appendLine("    vec4 worldPosition = modelMatrix * vec4(inPosition, 1.0);")
            appendLine("    gl_Position = ubo.uProjection * ubo.uView * worldPosition;")
            appendLine("    mat3 normalMatrix = transpose(inverse(mat3(modelMatrix)));")
            appendLine(
                "    vec3 normal = normalize(normalMatrix * ${
                    if (hasNormalAttr) "inNormal" else "vec3(0.0, 0.0, 1.0)"
                });"
            )
            appendLine("    if (length(normal) < 1e-5) normal = vec3(0.0, 0.0, 1.0);")
            if (hasTangentAttr) {
                appendLine("    vec3 tangent = normalize(normalMatrix * inTangent.xyz);")
                appendLine("    float handedness = inTangent.w == 0.0 ? 1.0 : inTangent.w;")
                appendLine("    vec3 bitangent = normalize(cross(normal, tangent)) * handedness;")
            } else {
                appendLine("    vec3 up = abs(normal.y) > 0.99 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);")
                appendLine("    vec3 tangent = normalize(cross(up, normal));")
                appendLine("    float handedness = 1.0;")
                appendLine("    vec3 bitangent = normalize(cross(normal, tangent)) * handedness;")
            }
            appendLine(
                "    vec3 vertexColor = ${if (hasColorAttr) "inColor" else "vec3(1.0, 1.0, 1.0)"};"
            )
            appendLine(
                "    vec2 uv = ${if (hasUvAttr) "inUV" else "vec2(0.0, 0.0)"};"
            )
            appendLine("    vColor = vertexColor;")
            appendLine("    vUV = uv;")
            appendLine("    vNormal = normal;")
            appendLine("    vTangent = tangent;")
            appendLine("    vBitangent = bitangent;")
            appendLine("    vWorldPos = worldPosition.xyz;")
            appendLine("}")
        }
    }

    private fun glslTypeForVertex(format: VertexFormat): String = when (format) {
        VertexFormat.FLOAT32 -> "float"
        VertexFormat.FLOAT32X2 -> "vec2"
        VertexFormat.FLOAT32X3 -> "vec3"
        VertexFormat.FLOAT32X4 -> "vec4"
        VertexFormat.UINT32 -> "uint"
        VertexFormat.UINT32X2 -> "uvec2"
        VertexFormat.UINT32X3 -> "uvec3"
        VertexFormat.UINT32X4 -> "uvec4"
    }

    private fun composeFragmentShader(
        descriptor: MaterialDescriptor,
        features: MaterialPipelineFeatures,
        hasUv: Boolean,
        albedoPair: BindingPair?,
        normalPair: BindingPair?,
        roughnessPair: BindingPair?,
        metalnessPair: BindingPair?,
        aoPair: BindingPair?,
        prefilterPair: BindingPair?,
        brdfPair: BindingPair?
    ): String {
        val sb = StringBuilder()
        sb.appendLine("#version 450")
        sb.appendLine("layout(location = 0) in vec3 vColor;")
        sb.appendLine("layout(location = 1) in vec2 vUV;")
        sb.appendLine("layout(location = 2) in vec3 vNormal;")
        sb.appendLine("layout(location = 3) in vec3 vTangent;")
        sb.appendLine("layout(location = 4) in vec3 vBitangent;")
        sb.appendLine("layout(location = 5) in vec3 vWorldPos;")
        sb.appendLine()
        sb.appendLine("layout(location = 0) out vec4 outColor;")
        sb.appendLine()
        val uniformBlock = descriptor.uniformBlock
        sb.appendLine("layout(set = ${uniformBlock.group}, binding = ${uniformBlock.binding}) uniform UniformBufferObject {")
        sb.appendLine("    mat4 uProjection;")
        sb.appendLine("    mat4 uView;")
        sb.appendLine("    mat4 uModel;")
        sb.appendLine("    vec4 uBaseColor;")
        sb.appendLine("    vec4 uPbrParams;")
        sb.appendLine("    vec4 uCameraPosition;")
        sb.appendLine("} ubo;")
        sb.appendLine()

        materialTextureBindingLayout.sortedBy { it.binding }.forEach { binding ->
            sb.appendLine("layout(set = ${binding.group}, binding = ${binding.binding}) uniform ${binding.glslType()} ${binding.uniformName()};")
        }
        if (environmentBindingLayout.isNotEmpty()) {
            environmentBindingLayout.sortedBy { it.binding }.forEach { binding ->
                sb.appendLine("layout(set = ${binding.group}, binding = ${binding.binding}) uniform ${binding.glslType()} ${binding.uniformName()};")
            }
        }
        sb.appendLine()
        sb.appendLine("float roughnessToMip(float roughness, float mipCount) {")
        sb.appendLine("    if (mipCount <= 1.0) {")
        sb.appendLine("        return 0.0;")
        sb.appendLine("    }")
        sb.appendLine("    float clamped = clamp(roughness, 0.0, 1.0);")
        sb.appendLine("    float perceptual = clamped * clamped;")
        sb.appendLine("    float maxLevel = mipCount - 1.0;")
        sb.appendLine("    return min(maxLevel, perceptual * maxLevel);")
        sb.appendLine("}")
        sb.appendLine()
        if (features.usesNormalMap && normalPair != null) {
            sb.appendLine("vec3 applyNormalMap(vec3 normal, vec3 tangent, vec3 bitangent) {")
            sb.appendLine("    vec3 mapped = ${normalPair.textureSample("vUV")}.xyz * 2.0 - vec3(1.0);")
            sb.appendLine("    mat3 tbn = mat3(normalize(tangent), normalize(bitangent), normalize(normal));")
            sb.appendLine("    return normalize(tbn * mapped);")
            sb.appendLine("}")
            sb.appendLine()
        }

        val uvCoord = if (hasUv) "vUV" else "vec2(0.0, 0.0)"
        val albedoSampleExpr = albedoPair?.textureSample(uvCoord)
        val roughnessSampleExpr = roughnessPair?.textureSample(uvCoord)?.let { "$it.r" }
        val metalnessSampleExpr = metalnessPair?.textureSample(uvCoord)?.let { "$it.r" }
        val aoSampleExpr = aoPair?.textureSample(uvCoord)?.let { "$it.r" }

        sb.appendLine("void main() {")
        sb.appendLine("    vec4 albedoSample = vec4(1.0);")
        if (features.usesAlbedoMap && albedoSampleExpr != null) {
            sb.appendLine("    albedoSample = $albedoSampleExpr;")
        }
        sb.appendLine("    vec3 baseColor = clamp(ubo.uBaseColor.rgb * vColor * albedoSample.rgb, 0.0, 1.0);")
        sb.appendLine("    float alpha = clamp(ubo.uBaseColor.a * albedoSample.a, 0.0, 1.0);")
        sb.appendLine("    vec3 normal = normalize(vNormal);")
        sb.appendLine("    vec3 tangent = normalize(vTangent);")
        sb.appendLine("    vec3 bitangent = normalize(vBitangent);")
        if (features.usesNormalMap && normalPair != null) {
            sb.appendLine("    vec3 perturbedNormal = applyNormalMap(normal, tangent, bitangent);")
        } else {
            sb.appendLine("    vec3 perturbedNormal = normal;")
        }

        if (features.usesRoughnessMap && roughnessSampleExpr != null) {
            sb.appendLine("    float roughness = clamp(ubo.uPbrParams.x * $roughnessSampleExpr, 0.045, 1.0);")
        } else {
            sb.appendLine("    float roughness = clamp(ubo.uPbrParams.x, 0.045, 1.0);")
        }

        if (features.usesMetalnessMap && metalnessSampleExpr != null) {
            sb.appendLine("    float metalness = clamp(ubo.uPbrParams.y * $metalnessSampleExpr, 0.0, 1.0);")
        } else {
            sb.appendLine("    float metalness = clamp(ubo.uPbrParams.y, 0.0, 1.0);")
        }

        sb.appendLine("    vec3 viewDir = normalize(ubo.uCameraPosition.xyz - vWorldPos);")
        sb.appendLine("    vec3 specular = vec3(0.0);")
        if (features.usesEnvironmentMaps && prefilterPair != null && brdfPair != null) {
            sb.appendLine("    float envIntensity = ubo.uPbrParams.z;")
            sb.appendLine("    float mipCount = ubo.uPbrParams.w;")
            sb.appendLine("    vec3 reflection = vec3(0.0);")
            sb.appendLine("    float NdotV = 0.0;")
            sb.appendLine("    if (length(viewDir) > 0.0) {")
            sb.appendLine("        vec3 R = reflect(-viewDir, perturbedNormal);")
            sb.appendLine("        float lod = roughnessToMip(roughness, mipCount);")
            sb.appendLine("        reflection = textureLod(${prefilterPair.samplerRef()}, R, lod).rgb;")
            sb.appendLine("        NdotV = clamp(dot(perturbedNormal, viewDir), 0.0, 1.0);")
            sb.appendLine("    }")
            sb.appendLine("    vec3 F0 = mix(vec3(0.04), baseColor, metalness);")
            sb.appendLine("    vec2 brdf = texture(${brdfPair.samplerRef()}, vec2(NdotV, roughness)).rg;")
            sb.appendLine("    specular = reflection * (F0 * brdf.x + vec3(brdf.y)) * envIntensity;")
        } else {
            sb.appendLine("    float NdotV = clamp(dot(perturbedNormal, viewDir), 0.0, 1.0);")
        }

        sb.appendLine("    vec3 diffuse = baseColor * (1.0 - metalness);")
        val aoTerm = if (features.usesAmbientOcclusionMap && aoSampleExpr != null) aoSampleExpr else "1.0"
        sb.appendLine("    float ao = clamp($aoTerm * ubo.uCameraPosition.w, 0.0, 1.0);")
        sb.appendLine("    vec3 color = clamp((diffuse + specular) * ao, 0.0, 1.0);")
        sb.appendLine("    outColor = vec4(color, alpha);")
        sb.appendLine("}")

        return sb.toString()
    }

    private fun buildShaderProgramConfig(
        material: Material,
        descriptor: MaterialDescriptor,
        metadata: GeometryMetadata,
        vertexLayouts: List<VertexBufferLayout>,
        hasEnvironmentBinding: Boolean
    ): ShaderProgramConfig {
        val hasUv = metadata.bindingFor(GeometryAttribute.UV0) != null
        val hasTangent = metadata.bindingFor(GeometryAttribute.TANGENT) != null

        fun descriptorHas(source: MaterialBindingSource, type: MaterialBindingType) =
            descriptor.bindings.any { it.source == source && it.type == type }

        val albedoPair = bindingPair(materialBindingLookup, MaterialBindingSource.ALBEDO_MAP)
        val usesAlbedoMap = albedoPair != null && descriptorHas(
            MaterialBindingSource.ALBEDO_MAP,
            MaterialBindingType.TEXTURE_2D
        ) && hasUv && when (material) {
            is MeshBasicMaterial -> material.map != null
            is MeshStandardMaterial -> material.map != null
            else -> false
        }

        val normalPair = bindingPair(materialBindingLookup, MaterialBindingSource.NORMAL_MAP)
        val usesNormalMap = normalPair != null && descriptorHas(
            MaterialBindingSource.NORMAL_MAP,
            MaterialBindingType.TEXTURE_2D
        ) && hasUv && hasTangent && material is MeshStandardMaterial && material.normalMap != null

        val roughnessPair = bindingPair(materialBindingLookup, MaterialBindingSource.ROUGHNESS_MAP)
        val usesRoughnessMap = roughnessPair != null && descriptorHas(
            MaterialBindingSource.ROUGHNESS_MAP,
            MaterialBindingType.TEXTURE_2D
        ) && hasUv && material is MeshStandardMaterial && material.roughnessMap != null

        val metalnessPair = bindingPair(materialBindingLookup, MaterialBindingSource.METALNESS_MAP)
        val usesMetalnessMap = metalnessPair != null && descriptorHas(
            MaterialBindingSource.METALNESS_MAP,
            MaterialBindingType.TEXTURE_2D
        ) && hasUv && material is MeshStandardMaterial && material.metalnessMap != null

        val aoPair = bindingPair(materialBindingLookup, MaterialBindingSource.AO_MAP)
        val usesAoMap = aoPair != null && descriptorHas(
            MaterialBindingSource.AO_MAP,
            MaterialBindingType.TEXTURE_2D
        ) && hasUv && material is MeshStandardMaterial && material.aoMap != null

        val prefilterPair = bindingPair(environmentBindingLookup, MaterialBindingSource.ENVIRONMENT_PREFILTER)
        val brdfPair = bindingPair(environmentBindingLookup, MaterialBindingSource.ENVIRONMENT_BRDF)
        val usesEnvironment = hasEnvironmentBinding && prefilterPair != null && brdfPair != null

        val features = MaterialPipelineFeatures(
            usesAlbedoMap = usesAlbedoMap,
            usesNormalMap = usesNormalMap,
            usesRoughnessMap = usesRoughnessMap,
            usesMetalnessMap = usesMetalnessMap,
            usesAmbientOcclusionMap = usesAoMap,
            usesEnvironmentMaps = usesEnvironment,
            usesInstancing = metadata.isInstanced
        )

        val vertexSource = composeVertexShader(
            metadata = metadata,
            vertexLayouts = vertexLayouts,
            features = features
        )

        val fragmentSource = composeFragmentShader(
            descriptor = descriptor,
            features = features,
            hasUv = hasUv,
            albedoPair = albedoPair,
            normalPair = normalPair,
            roughnessPair = roughnessPair,
            metalnessPair = metalnessPair,
            aoPair = aoPair,
            prefilterPair = prefilterPair,
            brdfPair = brdfPair
        )

        return ShaderProgramConfig(vertexSource, fragmentSource, features)
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
