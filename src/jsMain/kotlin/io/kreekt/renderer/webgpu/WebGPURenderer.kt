package io.kreekt.renderer.webgpu

import io.kreekt.camera.Camera
import io.kreekt.camera.Viewport
import io.kreekt.core.math.Color
import io.kreekt.core.math.Matrix4
import io.kreekt.core.scene.Mesh
import io.kreekt.core.scene.Scene
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.optimization.BoundingBox
import io.kreekt.optimization.Frustum
import io.kreekt.renderer.*
import io.kreekt.renderer.webgpu.shaders.BasicShaders
import org.w3c.dom.HTMLCanvasElement
import kotlin.js.Promise
import kotlin.js.jsTypeOf

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

    private val statsTracker = RenderStatsTracker()

    // Core WebGPU objects
    private var device: GPUDevice? = null
    private var context: GPUCanvasContext? = null
    private var adapter: GPUAdapter? = null

    // Component managers
    private lateinit var pipelineCache: PipelineCache
    private lateinit var bufferPool: BufferPool
    private val contextLossRecovery = ContextLossRecovery()

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
    private val geometryCache = GeometryBufferCache({ device }, statsTracker)
    private val uniformManager = UniformBufferManager({ device }, statsTracker)

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

        val maxCombinedTextures = if (limits.maxSampledTexturesPerShaderStage > limits.maxSamplersPerShaderStage) {
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


    override suspend fun initialize(config: RendererConfig): io.kreekt.core.Result<Unit> {
        // For WebGPU, surface is the canvas - already provided in constructor
        return initializeInternal()
    }

    private suspend fun initializeInternal(): io.kreekt.core.Result<Unit> {
        return try {
            console.log("T033: Starting WebGPU renderer initialization...")
            val startTime = js("performance.now()").unsafeCast<Double>()

            // Get GPU
            console.log("T033: Getting GPU interface...")
            val gpu = WebGPUDetector.getGPU()
            if (gpu == null) {
                console.error("T033: WebGPU not available in this browser")
                return io.kreekt.core.Result.Error(
                    "WebGPU not available",
                    RuntimeException("WebGPU not available")
                )
            }
            console.log("T033: GPU interface obtained")

            // Request adapter
            console.log("T033: Requesting GPU adapter (powerPreference=high-performance)...")
            val adapterOptions = js("({})").unsafeCast<GPURequestAdapterOptions>()
            adapterOptions.powerPreference = "high-performance"

            val adapterPromise = gpu.requestAdapter(adapterOptions).unsafeCast<Promise<GPUAdapter?>>()
            val adapterResult = adapterPromise.awaitPromise()
            if (adapterResult == null) {
                console.error("T033: Failed to request GPU adapter")
                return io.kreekt.core.Result.Error(
                    "Failed to request GPU adapter",
                    RuntimeException("Failed to request GPU adapter")
                )
            }
            adapter = adapterResult
            console.log("T033: GPU adapter obtained")

            // Request device
            console.log("T033: Requesting GPU device...")
            val deviceDescriptor = js("({})").unsafeCast<GPUDeviceDescriptor>()
            deviceDescriptor.label = "KreeKt WebGPU Device"

            val devicePromise = adapter!!.requestDevice(deviceDescriptor).unsafeCast<Promise<GPUDevice>>()
            device = devicePromise.awaitPromise()
            console.log("T033: GPU device created successfully")

            // Monitor device loss
            console.log("T033: Setting up device loss monitoring...")
            device!!.lost.then { info ->
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
                ?: return io.kreekt.core.Result.Error(
                    "Failed to get WebGPU context from canvas",
                    RuntimeException("Failed to get WebGPU context")
                )

            val contextConfig = js("({})").unsafeCast<GPUCanvasConfiguration>()
            contextConfig.device = device!!
            contextConfig.format = "bgra8unorm"
            contextConfig.alphaMode = "opaque"
            context!!.configure(contextConfig)
            console.log("T033: Canvas context configured (format=bgra8unorm, alphaMode=opaque)")

            ensureDepthTexture(canvas.width, canvas.height)

            // Create buffer pool
            console.log("T033: Creating buffer pool...")
            bufferPool = BufferPool(device!!)
            console.log("T033: Buffer pool created")

            // T020: Initialize Feature 020 Managers
            console.log("T033: Initializing BufferManager...")
            bufferManager = WebGPUBufferManager(device!!)
            console.log("T033: BufferManager initialized")
            uniformManager.onDeviceReady(device!!)
            // Note: RenderPassManager is initialized per-frame with command encoder

            // T021 PERFORMANCE: Create custom pipeline layout with dynamic offset support
            console.log("T033: Creating custom pipeline layout with dynamic offsets...")
            console.log("T033: Custom pipeline layout created")

            // Create capabilities
            console.log("T033: Querying device capabilities...")
            rendererCapabilities = createCapabilities(adapter!!)
            console.log("T033: Capabilities detected: maxTextureSize=${rendererCapabilities!!.maxTextureSize}, maxVertexAttributes=${rendererCapabilities!!.maxVertexAttributes}")

            isInitialized = true

            val initTime = js("performance.now()").unsafeCast<Double>() - startTime
            console.log("T033: WebGPU renderer initialization completed in ${initTime}ms")

            io.kreekt.core.Result.Success(Unit)
        } catch (e: Exception) {
            console.error("T033: ERROR during initialization: ${e.message}")
            console.error("T033: Stack trace: ${e.stackTraceToString()}")
            io.kreekt.core.Result.Error(
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
                console.warn("⚠️ Depth texture unavailable; rendering without depth buffer")
            }

            // Create command encoder
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Creating command encoder...")
            val commandEncoder = device!!.createCommandEncoder()

            // T020: Initialize RenderPassManager for this frame
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Initializing RenderPassManager...")
            renderPassManager = WebGPURenderPassManager(commandEncoder)

            // T020: Begin render pass using manager
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Beginning render pass (clearColor=[${clearColor.r}, ${clearColor.g}, ${clearColor.b}])...")
            val framebufferHandle = if (depthView != null) {
                io.kreekt.renderer.feature020.FramebufferHandle(
                    WebGPUFramebufferAttachments(textureView, depthView)
                )
            } else {
                io.kreekt.renderer.feature020.FramebufferHandle(textureView)
            }
            val clearColorFeature020 = io.kreekt.renderer.feature020.Color(
                clearColor.r,
                clearColor.g,
                clearColor.b,
                clearAlpha
            )
            renderPassManager!!.beginRenderPass(clearColorFeature020, framebufferHandle)

            // Get the internal render pass encoder for legacy rendering code
            val renderPass = (renderPassManager as WebGPURenderPassManager).getPassEncoder().unsafeCast<GPURenderPassEncoder>()

            // T009: Render scene with frustum culling
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Traversing scene graph and rendering meshes...")
                    scene.traverse { obj ->
                        if (obj is Mesh) {
                            renderMesh(obj, camera, renderPass)
                        }
                    }
            // T020: End render pass using manager
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Ending render pass...")
            renderPassManager!!.endRenderPass()

            // Submit commands
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Finishing command encoder...")
            val commandBuffer = commandEncoder.finish()
            val commandBuffers = js("[]").unsafeCast<Array<GPUCommandBuffer>>()
            js("commandBuffers.push(commandBuffer)")
            if (enableFrameLogging) console.log("T033: [Frame $frameCount] - Submitting command buffer to GPU...")
            device!!.queue.submit(commandBuffers)

            // T009: Log frustum culling statistics
            if (culledCount > 0 || visibleCount > 0) {
                console.log("T009 Frustum culling: $visibleCount visible, $culledCount culled (${culledCount + visibleCount} total)")
            }

            // T021 FIX: Validate buffer capacity was not exceeded
            if (drawIndexInFrame > UniformBufferManager.MAX_MESHES_PER_FRAME) {
                console.warn("⚠️ T021: Frame rendered $drawIndexInFrame meshes but buffer supports only ${UniformBufferManager.MAX_MESHES_PER_FRAME}")
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

    private fun renderMesh(mesh: Mesh, camera: Camera, renderPass: GPURenderPassEncoder) {
        val maxMeshesPerFrame = UniformBufferManager.MAX_MESHES_PER_FRAME
        if (drawIndexInFrame >= maxMeshesPerFrame) {
            if (drawIndexInFrame == maxMeshesPerFrame) {
                console.warn("⚠️ T021: Mesh count (${drawIndexInFrame + 1}) exceeds buffer capacity ($maxMeshesPerFrame), skipping remaining meshes this frame")
            }
            return
        }

        mesh.updateMatrixWorld()

        if (drawCallCount < 5) {
            console.log("T021 Model matrix: " + mesh.matrixWorld.elements.joinToString(", "))
        }

        val geometry = mesh.geometry
        val material = mesh.material as? MeshBasicMaterial ?: return

        val buffers = geometryCache.getOrCreate(geometry, frameCount)
        if (buffers == null) {
            console.warn("Failed to create buffers for mesh")
            return
        }

        val pipeline = getOrCreatePipeline(geometry, material)
        if (pipeline == null) {
            console.warn("Failed to create pipeline for mesh")
            return
        }

        val frameInfo = FrameDebugInfo(frameCount, drawCallCount)
        if (!uniformManager.updateUniforms(mesh, camera, drawIndexInFrame, frameInfo, enableFrameLogging)) {
            return
        }

        renderPass.setPipeline(pipeline)
        renderPass.setVertexBuffer(0, buffers.vertexBuffer)

        val bindGroup = uniformManager.bindGroup()
        if (bindGroup == null) {
            console.warn("Failed to acquire uniform bind group")
            return
        }

        val dynamicOffset = uniformManager.dynamicOffset(drawIndexInFrame)
        val offsetsArray = js("[]")
        offsetsArray[0] = dynamicOffset
        renderPass.setBindGroup(0, bindGroup, offsetsArray)

        if (buffers.indexBuffer != null && buffers.indexCount > 0) {
            renderPass.setIndexBuffer(buffers.indexBuffer!!, buffers.indexFormat)
            renderPass.drawIndexed(buffers.indexCount, 1, 0, 0, 0)
            val trianglesDrawn = buffers.indexCount / 3
            triangleCount += trianglesDrawn
            statsTracker.recordDrawCall(trianglesDrawn)
        } else {
            renderPass.draw(buffers.vertexCount, 1, 0, 0)
            val trianglesDrawn = buffers.vertexCount / 3
            triangleCount += trianglesDrawn
            statsTracker.recordDrawCall(trianglesDrawn)
        }

        drawCallCount++
        drawIndexInFrame++
    }

    private fun createPipelineDescriptor(
        geometry: BufferGeometry,
        material: MeshBasicMaterial
    ): RenderPipelineDescriptor {
        // Create basic pipeline descriptor
        // T021: Temporarily disable backface culling to debug black screen
        return RenderPipelineDescriptor(
            vertexShader = BasicShaders.vertexShader,
            fragmentShader = BasicShaders.fragmentShader,
            vertexBufferLayout = VertexBufferLayout(
                arrayStride = 36, // 3 floats * 3 attributes * 4 bytes
                attributes = listOf(
                    VertexAttribute(VertexFormat.FLOAT32X3, 0, 0),  // position
                    VertexAttribute(VertexFormat.FLOAT32X3, 12, 1), // normal
                    VertexAttribute(VertexFormat.FLOAT32X3, 24, 2)  // color
                )
            ),
            cullMode = CullMode.NONE,  // T021: Disable culling to test winding order issue
            depthStencilState = DepthStencilState(
                format = TextureFormat.DEPTH24_PLUS,
                depthWriteEnabled = true,
                depthCompare = CompareFunction.LESS
            )
        )
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
    private fun getOrCreatePipeline(geometry: BufferGeometry, material: MeshBasicMaterial): GPURenderPipeline? {
        val pipelineDescriptor = createPipelineDescriptor(geometry, material)
        val cacheKey = PipelineKey.fromDescriptor(pipelineDescriptor)

        pipelineCacheMap[cacheKey]?.let { cached ->
            if (cached.isReady) {
                return cached.getPipeline()
            }
        }

        if (!pipelineCacheMap.containsKey(cacheKey)) {
            console.log("Creating new pipeline for key: $cacheKey")
            val pipeline = WebGPUPipeline(device!!, pipelineDescriptor)
            pipelineCacheMap[cacheKey] = pipeline

            try {
                val layout = uniformManager.pipelineLayout()
                val creationResult = pipeline.create(layout)
                when (creationResult) {
                    is io.kreekt.core.Result.Success<*> -> {
                        console.log("Pipeline ready for key: $cacheKey, isReady=${pipeline.isReady}, pipeline=${pipeline.getPipeline()}")
                    }
                    is io.kreekt.core.Result.Error -> {
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
    private fun createVertexBufferViaManager(vertices: FloatArray): io.kreekt.renderer.feature020.BufferHandle {
        return bufferManager!!.createVertexBuffer(vertices)
    }

    /**
     * Create index buffer using Feature 020 BufferManager.
     *
     * @param indices Triangle indices (must be multiple of 3)
     * @return BufferHandle for the created index buffer
     */
    private fun createIndexBufferViaManager(indices: IntArray): io.kreekt.renderer.feature020.BufferHandle {
        return bufferManager!!.createIndexBuffer(indices)
    }

    /**
     * Create uniform buffer using Feature 020 BufferManager.
     *
     * @param sizeBytes Buffer size in bytes (minimum 64 for mat4x4)
     * @return BufferHandle for the created uniform buffer
     */
    private fun createUniformBufferViaManager(sizeBytes: Int): io.kreekt.renderer.feature020.BufferHandle {
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
            is io.kreekt.core.Result.Error -> {
                console.error("❌ Failed to create depth texture")
                depthTexture = null
                depthTextureView = null
                depthTextureWidth = 0
                depthTextureHeight = 0
                depthTextureBytes = 0
            }

            is io.kreekt.core.Result.Success<*> -> {
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
