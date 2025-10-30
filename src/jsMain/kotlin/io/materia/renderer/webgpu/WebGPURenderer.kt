package io.materia.renderer.webgpu

import io.materia.camera.Camera
import io.materia.camera.Viewport
import io.materia.core.math.Color
import io.materia.core.math.Matrix4
import io.materia.core.scene.Mesh
import io.materia.core.scene.Scene
import io.materia.lighting.ibl.IBLConvolutionProfiler
import io.materia.lighting.ibl.PrefilterMipSelector
import io.materia.material.MeshBasicMaterial
import io.materia.material.MeshStandardMaterial
import io.materia.optimization.Frustum
import io.materia.renderer.*
import io.materia.renderer.geometry.GeometryAttribute
import io.materia.renderer.geometry.GeometryMetadata
import io.materia.renderer.geometry.buildGeometryOptions
import io.materia.renderer.gpu.*
import io.materia.renderer.lighting.SceneLightingUniforms
import io.materia.renderer.lighting.collectSceneLightingUniforms
import io.materia.renderer.material.*
import io.materia.renderer.shader.MaterialShaderDescriptor
import io.materia.renderer.shader.MaterialShaderGenerator
import io.materia.renderer.shader.withOverrides
import org.w3c.dom.HTMLCanvasElement
import io.materia.material.Material as EngineMaterial

/**
 * Main WebGPU renderer class implementing the Renderer interface.
 * T036: Complete WebGPU renderer implementation.
 *
 * FR-002: Canvas initialization
 * FR-003: Basic geometry rendering
 * FR-004: Buffer management
 * FR-009: Performance (60 FPS @ 1M triangles)
 * FR-011: Context loss recovery
 * FR-013: Pipeline caching
 */
class WebGPURenderer(private val canvas: HTMLCanvasElement) : Renderer {

    private companion object {
        private const val MAX_MORPH_TARGETS = 8
    }

    private val statsTracker = RenderStatsTracker()

    // Core WebGPU objects
    private var device: GPUDevice? = null
    private var gpuContext: io.materia.renderer.gpu.GpuContext? = null
    private var gpuQueue: io.materia.renderer.gpu.GpuQueue? = null
    private var context: GPUCanvasContext? = null
    private var adapter: GPUAdapter? = null

    // Component managers
    private lateinit var pipelineCache: PipelineCache
    private lateinit var bufferPool: BufferPool
    private val contextLossRecovery = ContextLossRecovery()
    private val environmentManager = WebGPUEnvironmentManager({ gpuContext?.device }, statsTracker)
    private val materialTextureManager =
        WebGPUMaterialTextureManager({ gpuContext?.device }, statsTracker)

    // Feature 020 Managers (T020)
    private var bufferManager: WebGPUBufferManager? = null
    private var renderPassManager: WebGPURenderPassManager? = null

    // Rendering state
    private var currentPipeline: WebGPUPipeline? = null
    private var frameCount = 0
    private var triangleCount = 0
    private var drawCallCount = 0

    // Reset to 0 at start of each frame, incremented after each mesh draw
    private var drawIndexInFrame = 0

    // Geometry buffer cache (mesh.uuid -> buffers)
    private val geometryCache = GeometryBufferCache({ gpuContext?.device }, statsTracker)
    private val uniformManager = UniformBufferManager({ gpuContext?.device }, statsTracker)

    // Pipeline cache map (for synchronous access)
    private val pipelineCacheMap = mutableMapOf<PipelineKey, WebGPUPipeline>()


    // Create once, reuse 68 times per frame with different dynamic offsets


    // Depth resources
    private var depthTexture: WebGPUTexture? = null
    private var depthTextureView: GPUTextureView? = null
    private var depthTextureWidth: Int = 0
    private var depthTextureHeight: Int = 0
    private var depthTextureBytes: Long = 0

    // Capabilities
    private var rendererCapabilities: RendererCapabilities? = null

    // Viewport
    private var viewport = Viewport(0, 0, canvas.width, canvas.height)

    // T033: Debug flag for verbose frame logging (default off to avoid spam)
    var enableFrameLogging: Boolean = false

    // Renderer interface properties
    override val backend: BackendType = BackendType.WEBGPU

    override val capabilities: RendererCapabilities
        get() = rendererCapabilities ?: createDefaultCapabilities()

    override val stats: RenderStats
        get() = statsTracker.getStats()

    // Old Three.js-style properties removed - not part of Feature 020 Renderer interface
    // These will be restored in advanced features phase (Phase 2-13)
    var clearColor: Color = Color(0x0000FF) // Blue
    var clearAlpha: Float = 1f

    var isInitialized: Boolean = false
        private set

    val isWebGPU: Boolean = true

    private fun createDefaultCapabilities(): RendererCapabilities {
        return RendererCapabilities(
            backend = BackendType.WEBGPU,
            supportsCompute = true,
            supportsMultisampling = true
        )
    }

    private fun createCapabilities(adapter: GPUAdapter): RendererCapabilities {
        val defaults = createDefaultCapabilities()
        val limits = adapter.limits

        val adapterInfo = try {
            val info = adapter.asDynamic().info
            if (info == null || jsTypeOf(info) == "undefined") null else info
        } catch (_: Throwable) {
            null
        }

        val deviceName = try {
            val info = adapterInfo
            if (info == null) {
                defaults.deviceName
            } else {
                val dynamicInfo = info
                (dynamicInfo.device as? String)
                    ?: (dynamicInfo.name as? String)
                    ?: defaults.deviceName
            }
        } catch (_: Throwable) {
            defaults.deviceName
        }

        val driverVersion = try {
            val info = adapterInfo
            if (info == null) {
                defaults.driverVersion
            } else {
                val dynamicInfo = info
                (dynamicInfo.driver as? String)
                    ?: (dynamicInfo.driverVersion as? String)
                    ?: defaults.driverVersion
            }
        } catch (_: Throwable) {
            defaults.driverVersion
        }

        val maxCombinedTextures =
            if (limits.maxSampledTexturesPerShaderStage > limits.maxSamplersPerShaderStage) {
                limits.maxSampledTexturesPerShaderStage
            } else {
                limits.maxSamplersPerShaderStage
            }

        return defaults.copy(
            deviceName = deviceName,
            driverVersion = driverVersion,
            maxTextureSize = limits.maxTextureDimension2D,
            maxCubeMapSize = limits.maxTextureDimension2D,
            maxVertexAttributes = limits.maxVertexAttributes,
            maxVertexUniforms = limits.maxUniformBuffersPerShaderStage,
            maxFragmentUniforms = limits.maxUniformBuffersPerShaderStage,
            maxVertexTextures = limits.maxSampledTexturesPerShaderStage,
            maxFragmentTextures = limits.maxSamplersPerShaderStage,
            maxCombinedTextures = maxCombinedTextures,
            maxTextureSize3D = limits.maxTextureDimension3D,
            maxTextureArrayLayers = limits.maxTextureArrayLayers,
            maxUniformBufferSize = limits.maxUniformBufferBindingSize,
            maxUniformBufferBindings = limits.maxBindGroups
        )
    }


    override suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit> {
        // For WebGPU, surface is the canvas - already provided in constructor
        return initializeInternal()
    }

    private suspend fun initializeInternal(): io.materia.core.Result<Unit> {
        return try {
            console.log("T033: Starting WebGPU renderer initialization...")
            val startTime = js("performance.now()").unsafeCast<Double>()

            val gpuCtx = GpuDeviceFactory.requestContext(
                GpuRequestConfig(
                    preferredBackend = GpuBackend.WEBGPU,
                    powerPreference = GpuPowerPreference.HIGH_PERFORMANCE
                )
            )

            val underlyingDevice = gpuCtx.device.unwrapHandle() as? GPUDevice
                ?: return io.materia.core.Result.Error(
                    "Failed to unwrap WebGPU device",
                    RuntimeException("Invalid WebGPU device handle")
                )

            device = underlyingDevice
            adapter = gpuCtx.device.unwrapHandleAdapter()
            gpuContext = gpuCtx
            gpuQueue = gpuCtx.queue

            // Monitor device loss
            console.log("T033: Setting up device loss monitoring...")
            underlyingDevice.lost.then { info ->
                try {
                    console.warn("T033: WebGPU device lost: ${info}")
                    contextLossRecovery.handleContextLoss()
                } catch (e: Exception) {
                    console.error("T033: Error monitoring device loss: ${e.message}")
                }
            }

            // Configure canvas context
            console.log("T033: Configuring canvas context...")
            context = canvas.getContext("webgpu").unsafeCast<GPUCanvasContext?>()
                ?: return io.materia.core.Result.Error(
                    "Failed to get WebGPU context from canvas",
                    RuntimeException("Failed to get WebGPU context")
                )

            val contextConfig = js("({})").unsafeCast<GPUCanvasConfiguration>()
            contextConfig.device = underlyingDevice
            contextConfig.format = "bgra8unorm"
            contextConfig.alphaMode = "opaque"
            context!!.configure(contextConfig)
            console.log("T033: Canvas context configured (format=bgra8unorm, alphaMode=opaque)")

            ensureDepthTexture(canvas.width, canvas.height)

            // Create buffer pool
            console.log("T033: Creating buffer pool...")
            bufferPool = BufferPool(gpuCtx.device)
            console.log("T033: Buffer pool created")

            // T020: Initialize Feature 020 Managers
            console.log("T033: Initializing BufferManager...")
            bufferManager = WebGPUBufferManager(gpuCtx.device)
            console.log("T033: BufferManager initialized")
            uniformManager.onDeviceReady(gpuCtx.device)
            materialTextureManager.onDeviceReady(gpuCtx.device)
            // Note: RenderPassManager is initialized per-frame with command encoder

            // T021 PERFORMANCE: Create custom pipeline layout with dynamic offset support
            console.log("T033: Creating custom pipeline layout with dynamic offsets...")
            console.log("T033: Custom pipeline layout created")

            // Create capabilities
            console.log("T033: Querying device capabilities...")
            rendererCapabilities = createCapabilities(adapter!!)
            console.log("T033: Capabilities detected: maxTextureSize=${rendererCapabilities!!.maxTextureSize}, maxVertexAttributes=${rendererCapabilities!!.maxVertexAttributes}")
            GpuDiagnostics.logContext(gpuCtx, rendererCapabilities)

            isInitialized = true

            val initTime = js("performance.now()").unsafeCast<Double>() - startTime
            console.log("T033: WebGPU renderer initialization completed in ${initTime}ms")

            io.materia.core.Result.Success(Unit)
        } catch (e: Exception) {
            console.error("T033: ERROR during initialization: ${e.message}")
            console.error("T033: Stack trace: ${e.stackTraceToString()}")
            io.materia.core.Result.Error(
                "Renderer initialization failed at stage: ${e.message}",
                e
            )
        }
    }

    override fun resize(width: Int, height: Int) {
        setSize(width, height, false)
    }

    override fun dispose() {
        renderPassManager = null
        environmentManager.dispose()

        pipelineCacheMap.values.forEach { it.dispose() }
        pipelineCacheMap.clear()

        if (this::pipelineCache.isInitialized) {
            pipelineCache.clear()
        }

        if (this::bufferPool.isInitialized) {
            bufferPool.dispose()
        }

        uniformManager.dispose()
        geometryCache.clear()
        bufferManager = null

        if (depthTexture != null && depthTextureBytes > 0) {
            statsTracker.recordTextureDisposed(depthTextureBytes)
            depthTextureBytes = 0
        }
        depthTexture?.dispose()
        depthTexture = null
        depthTextureView = null
        depthTextureWidth = 0
        depthTextureHeight = 0

        contextLossRecovery.clear()

        context?.unconfigure()
        context = null

        device?.destroy()
        device = null
        adapter = null
        gpuContext = null
        gpuQueue = null

        rendererCapabilities = null
        currentPipeline = null
        drawIndexInFrame = 0
        drawCallCount = 0
        triangleCount = 0
        frameCount = 0

        isInitialized = false
        statsTracker.reset()
    }

    override fun render(scene: Scene, camera: Camera) {
        if (!isInitialized || device == null || context == null) {
            console.error("T033: Renderer not initialized, cannot render")
            return
        }

        statsTracker.frameStart()
        statsTracker.recordIBLConvolution(IBLConvolutionProfiler.snapshot())
        statsTracker.recordIBLMaterial(0f, 0)

        // T021 FIX: Frame rendering (removed unreliable performance.now() logging)

        if (enableFrameLogging) {
            console.log("T033: [Frame $frameCount] Starting render...")
        }

        try {
            triangleCount = 0
            drawCallCount = 0
            drawIndexInFrame = 0  // T021 FIX: Reset draw index for new frame

            // T009: Create frustum for culling
            scene.updateMatrixWorld(true)

            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Updating camera matrices...")
            camera.updateMatrixWorld()
            camera.updateProjectionMatrix()
            val projectionViewMatrix = Matrix4()
                .copy(camera.projectionMatrix)
                .multiply(camera.matrixWorldInverse)

            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Creating frustum for culling...")
            val frustum = Frustum()
            frustum.setFromMatrix(projectionViewMatrix)

            var culledCount = 0
            var visibleCount = 0

            // Get current texture from swap chain
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Getting current texture from swap chain...")
            val currentTexture = context!!.getCurrentTexture()
            val textureView = currentTexture.createView()

            ensureDepthTexture(canvas.width, canvas.height)
            val depthView = depthTextureView
            if (depthView == null) {
                console.warn("ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â Depth texture unavailable; rendering without depth buffer")
            }

            // Create command encoder
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Creating command encoder...")
            val commandEncoderWrapper =
                gpuContext?.device?.createCommandEncoder(label = "Frame Encoder")
            val commandEncoder = commandEncoderWrapper?.unwrapHandle() as? GPUCommandEncoder
            if (commandEncoderWrapper == null || commandEncoder == null) {
                console.error("T033: Failed to create command encoder")
                return
            }

            // T020: Initialize RenderPassManager for this frame
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Initializing RenderPassManager...")
            renderPassManager = WebGPURenderPassManager(commandEncoder)

            // T020: Begin render pass using manager
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Beginning render pass (clearColor=[${clearColor.r}, ${clearColor.g}, ${clearColor.b}])...")
            val framebufferHandle = if (depthView != null) {
                io.materia.renderer.feature020.FramebufferHandle(
                    WebGPUFramebufferAttachments(textureView, depthView)
                )
            } else {
                io.materia.renderer.feature020.FramebufferHandle(textureView)
            }
            val clearColorFeature020 = io.materia.renderer.feature020.Color(
                clearColor.r,
                clearColor.g,
                clearColor.b,
                clearAlpha
            )
            renderPassManager!!.beginRenderPass(clearColorFeature020, framebufferHandle)

            // Get the internal render pass encoder for legacy rendering code
            val renderPass = (renderPassManager as WebGPURenderPassManager).getPassEncoder()
                .unsafeCast<GPURenderPassEncoder>()

            // T009: Render scene with frustum culling
            val sceneBrdf = scene.environmentBrdfLut as? Texture2D
            val lightingUniforms = collectSceneLightingUniforms(scene)
            val environmentBinding = environmentManager.prepare(scene.environment, sceneBrdf)

            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Traversing scene graph and rendering meshes...")
            scene.traverse { obj ->
                if (obj is Mesh) {
                    renderMesh(obj, camera, renderPass, environmentBinding, lightingUniforms)
                }
            }
            // T020: End render pass using manager
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Ending render pass...")
            renderPassManager!!.endRenderPass()

            // Submit commands
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Finishing command encoder...")
            val commandBufferWrapper = commandEncoderWrapper.finish()
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Submitting command buffer to GPU...")
            val queue = gpuQueue
            if (queue != null) {
                queue.submit(listOf(commandBufferWrapper))
            } else {
                val commandBuffer = commandBufferWrapper.unwrapHandle() as GPUCommandBuffer
                val commandBuffers = js("[]").unsafeCast<Array<GPUCommandBuffer>>()
                commandBuffers.asDynamic().push(commandBuffer)
                device!!.queue.submit(commandBuffers)
            }

            // T009: Log frustum culling statistics
            if (culledCount > 0 || visibleCount > 0) {
                console.log("T009 Frustum culling: $visibleCount visible, $culledCount culled (${culledCount + visibleCount} total)")
            }

            // T021 FIX: Validate buffer capacity was not exceeded
            if (drawIndexInFrame > UniformBufferManager.MAX_MESHES_PER_FRAME) {
                console.warn("ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â T021: Frame rendered $drawIndexInFrame meshes but buffer supports only ${UniformBufferManager.MAX_MESHES_PER_FRAME}")
            }

            // T010: Log performance metrics (reduced verbosity)
            console.log("T010 Performance: $drawCallCount draw calls, $triangleCount triangles, $visibleCount meshes")

            if (enableFrameLogging) {
                console.log("T033: [Frame $frameCount] Render completed successfully")
            }

            frameCount++
        } catch (e: Exception) {
            console.error("T033: ERROR during rendering frame $frameCount: ${e.message}")
            console.error("T033: Stack trace: ${e.stackTraceToString()}")
        } finally {
            statsTracker.frameEnd()
        }
    }

    private fun renderMesh(
        mesh: Mesh,
        camera: Camera,
        renderPass: GPURenderPassEncoder,
        environmentBinding: EnvironmentBinding?,
        lightingUniforms: SceneLightingUniforms
    ) {
        val maxMeshesPerFrame = UniformBufferManager.MAX_MESHES_PER_FRAME
        if (drawIndexInFrame >= maxMeshesPerFrame) {
            if (drawIndexInFrame == maxMeshesPerFrame) {
                console.warn("ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¡ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¯ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¸ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â T021: Mesh count (${drawIndexInFrame + 1}) exceeds buffer capacity ($maxMeshesPerFrame), skipping remaining meshes this frame")
            }
            return
        }

        mesh.updateMatrixWorld()

        if (drawCallCount < 5) {
            console.log("T021 Model matrix: " + mesh.matrixWorld.elements.joinToString(", "))
        }

        val geometry = mesh.geometry
        val cameraPosition = floatArrayOf(camera.position.x, camera.position.y, camera.position.z)
        val material = mesh.material ?: run {
            console.warn("Mesh ${mesh.name} missing material; skipping")
            return
        }
        val resolvedDescriptor = MaterialDescriptorRegistry.resolve(material) ?: run {
            console.warn("No material descriptor registered for ${material::class.simpleName}")
            return
        }
        val descriptor = resolvedDescriptor.descriptor

        val requiresEnvironment =
            descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER)
        if (requiresEnvironment && environmentBinding == null) {
            console.warn("Skipping ${descriptor.key} without prefiltered environment")
            return
        }

        val materialUniforms = when (material) {
            is MeshStandardMaterial -> {
                val baseColor = floatArrayOf(
                    material.color.r,
                    material.color.g,
                    material.color.b,
                    material.opacity
                )
                val roughness = PrefilterMipSelector.clamp01(material.roughness)
                val uniforms = MaterialUniformData(
                    baseColor = baseColor,
                    roughness = roughness,
                    metalness = material.metalness,
                    envIntensity = material.envMapIntensity,
                    prefilterMipCount = environmentBinding?.mipCount ?: 1,
                    cameraPosition = cameraPosition,
                    ambientColor = lightingUniforms.ambientColor,
                    fogColor = lightingUniforms.fogColor,
                    fogParams = lightingUniforms.fogParams,
                    mainLightDirection = lightingUniforms.mainLightDirection,
                    mainLightColor = lightingUniforms.mainLightColor
                )
                environmentBinding?.let { statsTracker.recordIBLMaterial(roughness, it.mipCount) }
                uniforms
            }

            is MeshBasicMaterial -> {
                val baseColor = floatArrayOf(
                    material.color.r,
                    material.color.g,
                    material.color.b,
                    material.opacity
                )
                MaterialUniformData(
                    baseColor = baseColor,
                    roughness = 1f,
                    metalness = 0f,
                    envIntensity = 0f,
                    prefilterMipCount = environmentBinding?.mipCount ?: 1,
                    cameraPosition = cameraPosition,
                    ambientColor = lightingUniforms.ambientColor,
                    fogColor = lightingUniforms.fogColor,
                    fogParams = lightingUniforms.fogParams,
                    mainLightDirection = lightingUniforms.mainLightDirection,
                    mainLightColor = lightingUniforms.mainLightColor
                )
            }

            else -> return
        }

        val buildOptions = descriptor.buildGeometryOptions(geometry)
        val buffers = geometryCache.getOrCreate(geometry, frameCount, buildOptions)
        if (buffers == null) {
            console.warn("Failed to create buffers for mesh")
            return
        }

        val attributeOverrides = buildAttributeOverrides(descriptor.key, buffers.metadata)
        val materialOverrides =
            buildMaterialOverrides(material as? EngineMaterial, descriptor, buffers.metadata)
        val combinedOverrides = mergeShaderOverrides(
            descriptor.defines,
            resolvedDescriptor.shaderOverrides,
            attributeOverrides,
            materialOverrides.overrides
        )
        val shaderDescriptor = descriptor.shader.withOverrides(combinedOverrides)

        val materialTextureBinding =
            if (materialOverrides.usesAlbedoMap || materialOverrides.usesNormalMap) {
                materialTextureManager.prepare(
                    descriptor = descriptor,
                    material = material as? EngineMaterial,
                    useAlbedo = materialOverrides.usesAlbedoMap,
                    useNormal = materialOverrides.usesNormalMap
                )
            } else null

        if ((materialOverrides.usesAlbedoMap || materialOverrides.usesNormalMap) && materialTextureBinding == null) {
            console.warn("Material ${descriptor.key} requires texture bindings but none were prepared; skipping mesh")
            return
        }

        val pipeline = getOrCreatePipeline(
            resolvedDescriptor,
            shaderDescriptor,
            environmentBinding,
            materialTextureBinding,
            buffers.vertexStreams.map { it.layout }
        )
        if (pipeline == null) {
            console.warn("Failed to create pipeline for mesh")
            return
        }

        val frameInfo = FrameDebugInfo(frameCount, drawCallCount)
        if (!uniformManager.updateUniforms(
                mesh,
                camera,
                drawIndexInFrame,
                frameInfo,
                enableFrameLogging,
                materialUniforms
            )
        ) {
            return
        }

        renderPass.setPipeline(pipeline)
        buffers.vertexStreams.forEachIndexed { slot, stream ->
            renderPass.setVertexBuffer(slot, stream.buffer)
        }

        val bindGroupWrapper = uniformManager.bindGroup()
        val bindGroup = bindGroupWrapper?.unwrapHandle() as? GPUBindGroup
        if (bindGroup == null) {
            console.warn("Failed to acquire uniform bind group")
            return
        }

        val dynamicOffset = uniformManager.dynamicOffset(drawIndexInFrame)
        val offsetsArray = js("[]")
        offsetsArray[0] = dynamicOffset
        renderPass.setBindGroup(0, bindGroup, offsetsArray)

        bindAdditionalGroups(descriptor, materialTextureBinding, environmentBinding, renderPass)

        val instanceCount = if (buffers.instanceCount > 0) buffers.instanceCount else 1

        if (buffers.indexBuffer != null && buffers.indexCount > 0) {
            renderPass.setIndexBuffer(buffers.indexBuffer!!, buffers.indexFormat)
            renderPass.drawIndexed(buffers.indexCount, instanceCount, 0, 0, 0)
            val trianglesDrawn = (buffers.indexCount / 3) * instanceCount
            triangleCount += trianglesDrawn
            statsTracker.recordDrawCall(trianglesDrawn)
        } else {
            renderPass.draw(buffers.vertexCount, instanceCount, 0, 0)
            val trianglesDrawn = (buffers.vertexCount / 3) * instanceCount
            triangleCount += trianglesDrawn
            statsTracker.recordDrawCall(trianglesDrawn)
        }

        drawCallCount++
        drawIndexInFrame++
    }

    private fun bindAdditionalGroups(
        descriptor: MaterialDescriptor,
        materialBinding: MaterialTextureBinding?,
        environmentBinding: EnvironmentBinding?,
        renderPass: GPURenderPassEncoder
    ) {
        materialBinding?.let { binding ->
            val groups = mutableSetOf<Int>()
            groups += descriptor.bindingGroups(MaterialBindingSource.ALBEDO_MAP)
            groups += descriptor.bindingGroups(MaterialBindingSource.NORMAL_MAP)
            if (groups.isNotEmpty()) {
                val rawGroup = binding.bindGroup.unwrapHandle() as? GPUBindGroup ?: return@let
                groups.sorted().forEach { group ->
                    renderPass.setBindGroup(group, rawGroup)
                }
            }
        }

        environmentBinding?.let { binding ->
            val groups = mutableSetOf<Int>()
            groups += descriptor.bindingGroups(MaterialBindingSource.ENVIRONMENT_PREFILTER)
            groups += descriptor.bindingGroups(MaterialBindingSource.ENVIRONMENT_BRDF)
            if (groups.isEmpty()) return@let
            val rawGroup = binding.bindGroup.unwrapHandle() as? GPUBindGroup ?: return@let
            groups.sorted().forEach { group ->
                renderPass.setBindGroup(group, rawGroup)
            }
        }
    }

    private data class MaterialOverrideResult(
        val overrides: Map<String, String>,
        val usesAlbedoMap: Boolean,
        val usesNormalMap: Boolean
    )

    private fun buildAttributeOverrides(
        materialKey: String,
        metadata: GeometryMetadata
    ): Map<String, String> {
        val vertexInputExtra = StringBuilder()
        val vertexOutputExtra = StringBuilder()
        val vertexAssignExtra = StringBuilder()
        val fragmentInputExtra = StringBuilder()
        val fragmentInitExtra = StringBuilder()
        val fragmentExtra = StringBuilder()
        val fragmentBindings = StringBuilder()

        metadata.bindingFor(GeometryAttribute.UV0)?.let { binding ->
            vertexInputExtra.appendLine("    @location(${binding.location}) uv: vec2<f32>,")
            vertexOutputExtra.appendLine("    @location(${binding.location}) uv: vec2<f32>,")
            vertexAssignExtra.appendLine("    out.uv = in.uv;")
            fragmentInputExtra.appendLine("    @location(${binding.location}) uv: vec2<f32>,")
        }

        metadata.bindingFor(GeometryAttribute.UV1)?.let { binding ->
            vertexInputExtra.appendLine("    @location(${binding.location}) uv2: vec2<f32>,")
            vertexOutputExtra.appendLine("    @location(${binding.location}) uv2: vec2<f32>,")
            vertexAssignExtra.appendLine("    out.uv2 = in.uv2;")
            fragmentInputExtra.appendLine("    @location(${binding.location}) uv2: vec2<f32>,")
        }

        metadata.bindingFor(GeometryAttribute.TANGENT)?.let { binding ->
            vertexInputExtra.appendLine("    @location(${binding.location}) tangent: vec4<f32>,")
            vertexOutputExtra.appendLine("    @location(${binding.location}) tangent: vec4<f32>,")
            vertexAssignExtra.appendLine("    out.tangent = in.tangent;")
            fragmentInputExtra.appendLine("    @location(${binding.location}) tangent: vec4<f32>,")
            if (materialKey == "material.meshStandard") {
                fragmentExtra.appendLine("    let tangent = normalize(in.tangent.xyz);")
                fragmentExtra.appendLine("    let anisotropy = clamp(1.0 - abs(dot(normalize(in.viewDir), tangent)), 0.0, 1.0);")
                fragmentExtra.appendLine("    color = color * (0.75 + 0.25 * anisotropy);")
            }
        }

        val morphPositionBindings = metadata.bindings
            .filter { it.attribute == GeometryAttribute.MORPH_POSITION }
            .sortedBy { it.location }
        val morphNormalBindings = metadata.bindings
            .filter { it.attribute == GeometryAttribute.MORPH_NORMAL }
            .sortedBy { it.location }
        val morphCount = morphPositionBindings.size.coerceAtMost(MAX_MORPH_TARGETS)

        if (morphCount > 0) {
            val positionNames = mutableListOf<String>()
            val normalNames = mutableListOf<String>()
            morphPositionBindings.take(morphCount).forEachIndexed { index, binding ->
                val name = "morphPosition$index"
                vertexInputExtra.appendLine("    @location(${binding.location}) $name: vec3<f32>,")
                positionNames += name
            }
            morphNormalBindings.take(morphCount).forEachIndexed { index, binding ->
                val name = "morphNormal$index"
                vertexInputExtra.appendLine("    @location(${binding.location}) $name: vec3<f32>,")
                normalNames += name
            }

            vertexAssignExtra.appendLine("    var blendedPosition = position;")
            if (normalNames.isNotEmpty()) {
                vertexAssignExtra.appendLine("    var blendedNormal = normal;")
            } else {
                vertexAssignExtra.appendLine("    var blendedNormal = normal;")
            }
            val components = arrayOf("x", "y", "z", "w")
            repeat(morphCount) { index ->
                val source =
                    if (index < 4) "uniforms.morphInfluences0" else "uniforms.morphInfluences1"
                val comp = components[index % 4]
                val weightName = "morphWeight$index"
                vertexAssignExtra.appendLine("    let $weightName = $source.$comp;")
                vertexAssignExtra.appendLine("    blendedPosition = blendedPosition + ${positionNames[index]} * $weightName;")
                normalNames.getOrNull(index)?.let { normalAttr ->
                    vertexAssignExtra.appendLine("    blendedNormal = blendedNormal + $normalAttr * $weightName;")
                }
            }
            vertexAssignExtra.appendLine("    position = blendedPosition;")
            vertexAssignExtra.appendLine("    normal = normalize(blendedNormal);")
        }

        return mapOf(
            "VERTEX_INPUT_EXTRA" to vertexInputExtra.toString(),
            "VERTEX_OUTPUT_EXTRA" to vertexOutputExtra.toString(),
            "VERTEX_ASSIGN_EXTRA" to vertexAssignExtra.toString(),
            "FRAGMENT_INPUT_EXTRA" to fragmentInputExtra.toString(),
            "FRAGMENT_INIT_EXTRA" to fragmentInitExtra.toString(),
            "FRAGMENT_EXTRA" to fragmentExtra.toString(),
            "FRAGMENT_BINDINGS" to fragmentBindings.toString()
        )
    }

    private fun buildMaterialOverrides(
        material: EngineMaterial?,
        descriptor: MaterialDescriptor,
        metadata: GeometryMetadata
    ): MaterialOverrideResult {
        val fragmentBindings = StringBuilder()
        val fragmentInitExtra = StringBuilder()
        val fragmentExtra = StringBuilder()

        val hasUv = metadata.bindingFor(GeometryAttribute.UV0) != null
        val hasTangent = metadata.bindingFor(GeometryAttribute.TANGENT) != null

        fun MaterialDescriptor.bindingFor(
            source: MaterialBindingSource,
            type: MaterialBindingType
        ) = bindings.firstOrNull { it.source == source && it.type == type }

        var usesAlbedoMap = false
        var usesNormalMap = false

        when (val typedMaterial = material) {
            is MeshBasicMaterial -> {
                if (typedMaterial.map != null && hasUv) {
                    val textureBinding = descriptor.bindingFor(
                        MaterialBindingSource.ALBEDO_MAP,
                        MaterialBindingType.TEXTURE_2D
                    )
                    val samplerBinding = descriptor.bindingFor(
                        MaterialBindingSource.ALBEDO_MAP,
                        MaterialBindingType.SAMPLER
                    )
                    if (textureBinding != null && samplerBinding != null) {
                        fragmentBindings.appendLine("                @group(${textureBinding.group}) @binding(${textureBinding.binding}) var materialAlbedoTexture: texture_2d<f32>;")
                        fragmentBindings.appendLine("                @group(${samplerBinding.group}) @binding(${samplerBinding.binding}) var materialAlbedoSampler: sampler;")
                        fragmentInitExtra.appendLine("    let albedoSample = textureSample(materialAlbedoTexture, materialAlbedoSampler, in.uv);")
                        fragmentInitExtra.appendLine("    color = clamp(color * albedoSample.rgb, vec3<f32>(0.0), vec3<f32>(1.0));")
                        usesAlbedoMap = true
                    }
                }
            }

            is MeshStandardMaterial -> {
                if (typedMaterial.map != null && hasUv) {
                    val textureBinding = descriptor.bindingFor(
                        MaterialBindingSource.ALBEDO_MAP,
                        MaterialBindingType.TEXTURE_2D
                    )
                    val samplerBinding = descriptor.bindingFor(
                        MaterialBindingSource.ALBEDO_MAP,
                        MaterialBindingType.SAMPLER
                    )
                    if (textureBinding != null && samplerBinding != null) {
                        fragmentBindings.appendLine("                @group(${textureBinding.group}) @binding(${textureBinding.binding}) var materialAlbedoTexture: texture_2d<f32>;")
                        fragmentBindings.appendLine("                @group(${samplerBinding.group}) @binding(${samplerBinding.binding}) var materialAlbedoSampler: sampler;")
                        fragmentInitExtra.appendLine("    let albedoSample = textureSample(materialAlbedoTexture, materialAlbedoSampler, in.uv);")
                        fragmentInitExtra.appendLine("    baseColor = clamp(baseColor * albedoSample.rgb, vec3<f32>(0.0), vec3<f32>(1.0));")
                        usesAlbedoMap = true
                    }
                }
                if (typedMaterial.normalMap != null && hasUv) {
                    if (hasTangent) {
                        val textureBinding = descriptor.bindingFor(
                            MaterialBindingSource.NORMAL_MAP,
                            MaterialBindingType.TEXTURE_2D
                        )
                        val samplerBinding = descriptor.bindingFor(
                            MaterialBindingSource.NORMAL_MAP,
                            MaterialBindingType.SAMPLER
                        )
                        if (textureBinding != null && samplerBinding != null) {
                            fragmentBindings.appendLine("                @group(${textureBinding.group}) @binding(${textureBinding.binding}) var materialNormalTexture: texture_2d<f32>;")
                            fragmentBindings.appendLine("                @group(${samplerBinding.group}) @binding(${samplerBinding.binding}) var materialNormalSampler: sampler;")
                            fragmentInitExtra.appendLine("    let mappedNormal = textureSample(materialNormalTexture, materialNormalSampler, in.uv).xyz * 2.0 - vec3<f32>(1.0);")
                            fragmentInitExtra.appendLine("    let baseNormal = N;")
                            fragmentInitExtra.appendLine("    let tangent = normalize(in.tangent.xyz);")
                            fragmentInitExtra.appendLine("    let bitangent = normalize(cross(baseNormal, tangent)) * in.tangent.w;")
                            fragmentInitExtra.appendLine("    let tbn = mat3x3<f32>(tangent, bitangent, baseNormal);")
                            fragmentInitExtra.appendLine("    N = normalize(tbn * mappedNormal);")
                            usesNormalMap = true
                        }
                    } else {
                        console.warn("Normal map assigned to ${typedMaterial.name} but geometry lacks tangents; falling back to vertex normals.")
                    }
                }
            }
        }

        val overrides = mutableMapOf<String, String>()
        if (fragmentBindings.isNotEmpty()) {
            overrides["FRAGMENT_BINDINGS"] = fragmentBindings.toString()
        }
        if (fragmentInitExtra.isNotEmpty()) {
            overrides["FRAGMENT_INIT_EXTRA"] = fragmentInitExtra.toString()
        }
        if (fragmentExtra.isNotEmpty()) {
            overrides["FRAGMENT_EXTRA"] = fragmentExtra.toString()
        }

        return MaterialOverrideResult(
            overrides = overrides,
            usesAlbedoMap = usesAlbedoMap,
            usesNormalMap = usesNormalMap
        )
    }

    private fun mergeShaderOverrides(vararg overrideMaps: Map<String, String>): Map<String, String> {
        val concatKeys = setOf(
            "VERTEX_INPUT_EXTRA",
            "VERTEX_OUTPUT_EXTRA",
            "VERTEX_ASSIGN_EXTRA",
            "FRAGMENT_INPUT_EXTRA",
            "FRAGMENT_INIT_EXTRA",
            "FRAGMENT_EXTRA",
            "FRAGMENT_BINDINGS"
        )
        val result = LinkedHashMap<String, String>()
        overrideMaps.forEach { map ->
            map.forEach { (key, value) ->
                if (key in concatKeys) {
                    val existing = result[key]
                    result[key] = when {
                        existing.isNullOrEmpty() -> value
                        value.isEmpty() -> existing
                        else -> buildString {
                            append(existing)
                            if (!existing.endsWith("\n")) append('\n')
                            append(value)
                        }
                    }
                } else {
                    result[key] = value
                }
            }
        }
        concatKeys.forEach { key ->
            if (!result.containsKey(key)) {
                result[key] = ""
            }
        }
        return result
    }


    /**
     * Internal method to resize the canvas.
     * Called by RendererFactory's resize() implementation.
     */
    fun setSize(width: Int, height: Int, updateStyle: Boolean) {
        canvas.width = width
        canvas.height = height
        viewport = Viewport(0, 0, width, height)
        ensureDepthTexture(width, height)
    }

    /**
     * Get or create GPU buffers for a geometry.
     */
    /**
     * Get or create render pipeline for a material.
     * T006: Fixed - No longer blocks render thread with busy-wait.
     * Returns null if pipeline not ready (mesh skipped this frame, will render next frame).
     */
    private fun getOrCreatePipeline(
        resolved: ResolvedMaterialDescriptor,
        shaderDescriptor: MaterialShaderDescriptor,
        environmentBinding: EnvironmentBinding?,
        materialBinding: MaterialTextureBinding?,
        vertexLayouts: List<VertexBufferLayout>
    ): GPURenderPipeline? {
        val gpuDevice = device ?: return null
        val shaderSource = MaterialShaderGenerator.compile(shaderDescriptor)
        val renderState = resolved.renderState
        val depthState = if (renderState.depthTest) {
            DepthStencilState(
                format = renderState.depthFormat,
                depthWriteEnabled = renderState.depthWrite,
                depthCompare = renderState.depthCompare
            )
        } else {
            null
        }
        val pipelineDescriptor = RenderPipelineDescriptor(
            label = resolved.descriptor.key,
            vertexShader = shaderSource.vertexSource,
            fragmentShader = shaderSource.fragmentSource,
            vertexLayouts = vertexLayouts,
            primitiveTopology = renderState.topology,
            cullMode = renderState.cullMode,
            frontFace = renderState.frontFace,
            depthStencilState = depthState,
            colorTarget = renderState.colorTarget
        )
        val cacheKey = PipelineKey.fromDescriptor(pipelineDescriptor)

        pipelineCacheMap[cacheKey]?.let { cached ->
            if (cached.isReady) {
                return cached.getPipeline()
            }
        }

        if (!pipelineCacheMap.containsKey(cacheKey)) {
            console.log("Creating new pipeline for ${resolved.descriptor.key}")
            val pipeline = WebGPUPipeline(gpuDevice, pipelineDescriptor)
            pipelineCacheMap[cacheKey] = pipeline

            try {
                val layoutByGroup = mutableMapOf<Int, GpuBindGroupLayout>()

                materialBinding?.let { binding ->
                    val textureGroups = mutableSetOf<Int>()
                    textureGroups += resolved.descriptor.bindingGroups(MaterialBindingSource.ALBEDO_MAP)
                    textureGroups += resolved.descriptor.bindingGroups(MaterialBindingSource.NORMAL_MAP)
                    textureGroups.filter { it > 0 }.forEach { group ->
                        layoutByGroup[group] = binding.layout
                    }
                }

                if (resolved.descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER)) {
                    val environmentLayout = environmentBinding?.layout ?: return null
                    resolved.descriptor.bindingGroups(MaterialBindingSource.ENVIRONMENT_PREFILTER)
                        .filter { it > 0 }
                        .forEach { group -> layoutByGroup[group] = environmentLayout }
                }

                val extraLayouts = layoutByGroup.entries.sortedBy { it.key }.map { it.value }
                val pipelineLayoutWrapper = uniformManager.pipelineLayout(extraLayouts)
                val creationResult =
                    pipeline.create(pipelineLayoutWrapper?.unwrapHandle() as? GPUPipelineLayout)
                when (creationResult) {
                    is io.materia.core.Result.Success<*> -> {
                        console.log("Pipeline ready for ${resolved.descriptor.key}, isReady=${pipeline.isReady}")
                    }

                    is io.materia.core.Result.Error -> {
                        console.error("Pipeline creation failed: ${creationResult.message}")
                        pipelineCacheMap.remove(cacheKey)
                        return null
                    }
                }
            } catch (e: Exception) {
                console.error("Pipeline creation exception: ${e.message}")
                pipelineCacheMap.remove(cacheKey)
                return null
            }
        }

        return pipelineCacheMap[cacheKey]?.getPipeline()
    }

    /**
     * Update uniform buffer with MVP matrices.
     */
    private fun createVertexBufferViaManager(vertices: FloatArray): io.materia.renderer.feature020.BufferHandle {
        return bufferManager!!.createVertexBuffer(vertices)
    }

    /**
     * Create index buffer using Feature 020 BufferManager.
     *
     * @param indices Triangle indices (must be multiple of 3)
     * @return BufferHandle for the created index buffer
     */
    private fun createIndexBufferViaManager(indices: IntArray): io.materia.renderer.feature020.BufferHandle {
        return bufferManager!!.createIndexBuffer(indices)
    }

    /**
     * Create uniform buffer using Feature 020 BufferManager.
     *
     * @param sizeBytes Buffer size in bytes (minimum 64 for mat4x4)
     * @return BufferHandle for the created uniform buffer
     */
    private fun createUniformBufferViaManager(sizeBytes: Int): io.materia.renderer.feature020.BufferHandle {
        return bufferManager!!.createUniformBuffer(sizeBytes)
    }

    private fun ensureDepthTexture(width: Int, height: Int) {
        if (device == null || width <= 0 || height <= 0) {
            return
        }

        if (depthTexture != null && depthTextureWidth == width && depthTextureHeight == height) {
            return
        }

        if (depthTexture != null && depthTextureBytes > 0) {
            statsTracker.recordTextureDisposed(depthTextureBytes)
            depthTextureBytes = 0
        }
        depthTexture?.dispose()

        val descriptor = TextureDescriptor(
            label = "Depth Texture",
            width = width,
            height = height,
            format = TextureFormat.DEPTH24_PLUS,
            usage = GPUTextureUsage.RENDER_ATTACHMENT
        )

        val texture = WebGPUTexture(device!!, descriptor)
        when (texture.create()) {
            is io.materia.core.Result.Error -> {
                console.error("ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚ÂÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â¦ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬ÃƒÂ¢Ã¢â‚¬Å¾Ã‚Â¢ Failed to create depth texture")
                depthTexture = null
                depthTextureView = null
                depthTextureWidth = 0
                depthTextureHeight = 0
                depthTextureBytes = 0
            }

            is io.materia.core.Result.Success<*> -> {
                depthTexture = texture
                depthTextureView = texture.getView()
                depthTextureWidth = width
                depthTextureHeight = height
                val bytesPerPixel = when (descriptor.format) {
                    TextureFormat.DEPTH32_FLOAT -> 4
                    TextureFormat.DEPTH24_PLUS -> 4 // Approximation; actual layout is implementation-defined
                    else -> 4
                }
                depthTextureBytes = width.toLong() * height * bytesPerPixel
                statsTracker.recordTextureCreated(depthTextureBytes)
            }
        }
    }
}
