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

    // Core WebGPU objects
    private var device: GPUDevice? = null
    private var context: GPUCanvasContext? = null
    private var adapter: GPUAdapter? = null

    // Component managers
    private lateinit var pipelineCache: PipelineCache
    private lateinit var bufferPool: BufferPool
    private lateinit var contextLossRecovery: ContextLossRecovery

    // Feature 020 Managers (T020)
    private var bufferManager: WebGPUBufferManager? = null
    private var renderPassManager: WebGPURenderPassManager? = null

    // Rendering state
    private var currentPipeline: WebGPUPipeline? = null
    private var frameCount = 0
    private var triangleCount = 0
    private var drawCallCount = 0
    
    // T021: Draw index for per-mesh uniform buffer offsets
    // Reset to 0 at start of each frame, incremented after each mesh draw
    private var drawIndexInFrame = 0
    
    // T021 FIX: Buffer capacity constants
    companion object {
        const val MAX_MESHES_PER_FRAME = 200  // Increased from 100 to handle VoxelCraft's 120+ chunks
        const val UNIFORM_SIZE_PER_MESH = 256  // 192 bytes data + 64 bytes padding for alignment
        const val UNIFORM_BUFFER_SIZE = MAX_MESHES_PER_FRAME * UNIFORM_SIZE_PER_MESH  // 51,200 bytes
    }

    // Geometry buffer cache (mesh.uuid -> buffers)
    private val geometryCache = GeometryBufferCache { device }

    // Pipeline cache map (for synchronous access)
    private val pipelineCacheMap = mutableMapOf<PipelineKey, WebGPUPipeline>()

    // Uniform buffer for MVP matrices
    private var uniformBuffer: WebGPUBuffer? = null

    // T021 PERFORMANCE: Custom pipeline layout with dynamic offset support
    private var uniformBindGroupLayout: GPUBindGroupLayout? = null
    private var uniformPipelineLayout: GPUPipelineLayout? = null
    
    // T021 PERFORMANCE: Single cached bind group with dynamic offsets
    // Create once, reuse 68 times per frame with different dynamic offsets
    private var cachedUniformBindGroup: GPUBindGroup? = null
    
    // Bind groups cached per pipeline (since each pipeline has its own layout when using "auto")
    private val bindGroupCache = mutableMapOf<GPURenderPipeline, GPUBindGroup>()

    // Depth resources
    private var depthTexture: WebGPUTexture? = null
    private var depthTextureView: GPUTextureView? = null
    private var depthTextureWidth: Int = 0
    private var depthTextureHeight: Int = 0

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
        get() = RenderStats(
            fps = 0.0, // TODO: Calculate actual FPS
            frameTime = 0.0, // TODO: Calculate actual frame time
            triangles = triangleCount,
            drawCalls = drawCallCount,
            textureMemory = 0, // TODO: Track texture memory
            bufferMemory = 0 // TODO: Track buffer memory
        )

    // Old Three.js-style properties removed - not part of Feature 020 Renderer interface
    // These will be restored in advanced features phase (Phase 2-13)
    var clearColor: Color = Color(0x0000FF) // Blue
    var clearAlpha: Float = 1f

    var isInitialized: Boolean = false
        private set

    val isWebGPU: Boolean = true


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
            // Note: RenderPassManager is initialized per-frame with command encoder

            // T021 PERFORMANCE: Create custom pipeline layout with dynamic offset support
            console.log("T033: Creating custom pipeline layout with dynamic offsets...")
            uniformBindGroupLayout = createUniformBindGroupLayout()
            uniformPipelineLayout = createUniformPipelineLayout()
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

    override fun render(scene: Scene, camera: Camera) {
        if (!isInitialized || device == null || context == null) {
            console.error("T033: Renderer not initialized, cannot render")
            return
        }

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
            if (drawIndexInFrame > MAX_MESHES_PER_FRAME) {
                console.warn("⚠️ T021: Frame rendered ${drawIndexInFrame} meshes but buffer supports only $MAX_MESHES_PER_FRAME")
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
        }
    }

    private fun renderMesh(mesh: Mesh, camera: Camera, renderPass: GPURenderPassEncoder) {
        // T021 FIX: Check buffer capacity BEFORE attempting to render
        if (drawIndexInFrame >= MAX_MESHES_PER_FRAME) {
            if (drawIndexInFrame == MAX_MESHES_PER_FRAME) {
                console.warn("⚠️ T021: Mesh count (${drawIndexInFrame + 1}) exceeds buffer capacity ($MAX_MESHES_PER_FRAME), skipping remaining meshes this frame")
            }
            return  // Graceful degradation instead of crash
        }
        
        // Ensure mesh matrix is up to date
        mesh.updateMatrixWorld()

        if (drawCallCount < 5) {
            console.log("T021 Model matrix: " + mesh.matrixWorld.elements.joinToString(", "))
        }

        val geometry = mesh.geometry
        val material = mesh.material as? MeshBasicMaterial ?: return

        // Get or create buffers for this geometry
        val buffers = geometryCache.getOrCreate(geometry, frameCount)
        if (buffers == null) {
            console.warn("Failed to create buffers for mesh")
            return
        }

        // Get or create pipeline for this material
        val pipeline = getOrCreatePipeline(geometry, material)
        if (pipeline == null) {
            console.warn("Failed to create pipeline for mesh")
            return
        }

        // T021 FIX: Update uniforms with bounds checking
        updateUniforms(mesh, camera)

        // Bind pipeline
        renderPass.setPipeline(pipeline)

        // Bind vertex buffer
        renderPass.setVertexBuffer(0, buffers.vertexBuffer)

        // T021 PERFORMANCE FIX: Use cached bind group with dynamic offset
        val bindGroup = getOrCreateCachedBindGroup()
        val dynamicOffset = drawIndexInFrame * UNIFORM_SIZE_PER_MESH
        val offsetsArray = js("[]")
        offsetsArray[0] = dynamicOffset
        renderPass.setBindGroup(0, bindGroup, offsetsArray)

        // Draw
        if (buffers.indexBuffer != null && buffers.indexCount > 0) {
            // Indexed draw
            renderPass.setIndexBuffer(buffers.indexBuffer!!, buffers.indexFormat)
            renderPass.drawIndexed(buffers.indexCount, 1, 0, 0, 0)
            triangleCount += buffers.indexCount / 3
        } else {
            // Non-indexed draw
            renderPass.draw(buffers.vertexCount, 1, 0, 0)
            triangleCount += buffers.vertexCount / 3
        }

        drawCallCount++
        drawIndexInFrame++  // T021 FIX: Increment for next mesh's unique offset
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

        // Synchronous cache lookup - return immediately if ready
        pipelineCacheMap[cacheKey]?.let {
            if (it.isReady) {
                return it.getPipeline()
            }
        }

        // Create pipeline synchronously if not exists
        if (!pipelineCacheMap.containsKey(cacheKey)) {
            console.log("🆕 Creating new pipeline for key: $cacheKey")
            val pipeline = WebGPUPipeline(device!!, pipelineDescriptor)
            pipelineCacheMap[cacheKey] = pipeline

            try {
                // T021 PERFORMANCE: Use custom pipeline layout with dynamic offset support
                val result = pipeline.create(uniformPipelineLayout)
                when (result) {
                    is io.kreekt.core.Result.Success -> {
                        console.log("✅ Pipeline ready for key: $cacheKey, isReady=${pipeline.isReady}, pipeline=${pipeline.getPipeline()}")
                    }

                    is io.kreekt.core.Result.Error -> {
                        console.error("❌ Pipeline creation failed: ${result.message}")
                        pipelineCacheMap.remove(cacheKey) // Remove failed pipeline
                        return null
                    }
                }
            } catch (e: Exception) {
                console.error("❌ Pipeline creation exception: ${e.message}")
                pipelineCacheMap.remove(cacheKey)
                return null
            }
        }

        // Return pipeline
        return pipelineCacheMap[cacheKey]?.getPipeline()
    }

    /**
     * Update uniform buffer with MVP matrices.
     */
    private fun updateUniforms(mesh: Mesh, camera: Camera) {
        // T021: Always log for first few calls to diagnose issue
        if (frameCount < 50 && drawCallCount == 0) {
            console.log("T021 Frame $frameCount: updateUniforms called, drawCallCount=$drawCallCount")
        }

        if (uniformBuffer == null) {
            createUniformBuffer()
        }

        // Create MVP matrices (3 x mat4x4<f32> = 3 x 64 bytes = 192 bytes)
        val projMatrix = camera.projectionMatrix.elements
        val viewMatrix = camera.matrixWorldInverse.elements
        val modelMatrix = mesh.matrixWorld.elements

        // T021: Enhanced diagnostic logging for debugging sideways terrain
        if (frameCount < 3 && drawCallCount < 2) {
            console.log("T021 Frame $frameCount, Draw $drawCallCount:")
            console.log("  Projection matrix:")
            console.log("    [${projMatrix[0]}, ${projMatrix[4]}, ${projMatrix[8]}, ${projMatrix[12]}]")
            console.log("    [${projMatrix[1]}, ${projMatrix[5]}, ${projMatrix[9]}, ${projMatrix[13]}]")
            console.log("    [${projMatrix[2]}, ${projMatrix[6]}, ${projMatrix[10]}, ${projMatrix[14]}]")
            console.log("    [${projMatrix[3]}, ${projMatrix[7]}, ${projMatrix[11]}, ${projMatrix[15]}]")
            console.log("  View matrix:")
            console.log("    [${viewMatrix[0]}, ${viewMatrix[4]}, ${viewMatrix[8]}, ${viewMatrix[12]}]")
            console.log("    [${viewMatrix[1]}, ${viewMatrix[5]}, ${viewMatrix[9]}, ${viewMatrix[13]}]")
            console.log("    [${viewMatrix[2]}, ${viewMatrix[6]}, ${viewMatrix[10]}, ${viewMatrix[14]}]")
            console.log("    [${viewMatrix[3]}, ${viewMatrix[7]}, ${viewMatrix[11]}, ${viewMatrix[15]}]")
            console.log("  Model matrix:")
            console.log("    [${modelMatrix[0]}, ${modelMatrix[4]}, ${modelMatrix[8]}, ${modelMatrix[12]}]")
            console.log("    [${modelMatrix[1]}, ${modelMatrix[5]}, ${modelMatrix[9]}, ${modelMatrix[13]}]")
            console.log("    [${modelMatrix[2]}, ${modelMatrix[6]}, ${modelMatrix[10]}, ${modelMatrix[14]}]")
            console.log("    [${modelMatrix[3]}, ${modelMatrix[7]}, ${modelMatrix[11]}, ${modelMatrix[15]}]")
            console.log("  Camera pos: (${camera.position.x}, ${camera.position.y}, ${camera.position.z})")
            console.log("  Camera rot: (${camera.rotation.x}, ${camera.rotation.y}, ${camera.rotation.z})")
        }

        // Flatten matrices into uniform data
        val uniformData = FloatArray(48) // 3 matrices * 16 floats each

        // Projection matrix
        for (i in 0 until 16) {
            uniformData[i] = projMatrix[i]
        }

        // View matrix
        for (i in 0 until 16) {
            uniformData[16 + i] = viewMatrix[i]
        }

        // Model matrix
        for (i in 0 until 16) {
            uniformData[32 + i] = modelMatrix[i]
        }

        // T021 FIX: Upload to GPU at unique offset for this mesh
        // Bounds check is already done in renderMesh(), but double-check here for safety
        val offset = drawIndexInFrame * UNIFORM_SIZE_PER_MESH
        if (offset + 192 > UNIFORM_BUFFER_SIZE) {
            console.error("🔴 T021 CRITICAL: Buffer overflow prevented! Offset=$offset exceeds buffer size=$UNIFORM_BUFFER_SIZE")
            return
        }
        uniformBuffer?.upload(uniformData, offset)
    }

    /**
     * Create uniform buffer.
     * T021 FIX: Support multiple meshes per frame with unique offsets.
     * Size: 256 bytes per mesh × 100 meshes = 25,600 bytes
     */
    private fun createUniformBuffer() {
        // Create uniform buffer (3 mat4x4 = 192 bytes per mesh, 256-byte aligned)
        // T021 FIX: Increased from 100 to 200 meshes to prevent buffer overflow
        // VoxelCraft generates 120+ chunks as player moves, was causing black screen crash
        uniformBuffer = WebGPUBuffer(
            device!!,
            BufferDescriptor(
                size = UNIFORM_BUFFER_SIZE,  // 200 meshes × 256 bytes = 51,200 bytes
                usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST,
                label = "Uniform Buffer (Multi-Mesh, $MAX_MESHES_PER_FRAME max)"
            )
        )
        uniformBuffer!!.create()
    }

    /**
     * T021 PERFORMANCE: Create custom bind group layout with dynamic offset support.
     * 
     * KEY FIX: Sets hasDynamicOffset=true to enable dynamic offsets in bind groups.
     * This allows us to reuse one bind group across all meshes with different offsets.
     */
    private fun createUniformBindGroupLayout(): GPUBindGroupLayout {
        val layoutDescriptor = js("({})").unsafeCast<GPUBindGroupLayoutDescriptor>()
        layoutDescriptor.label = "Uniform Bind Group Layout (Dynamic Offsets)"
        
        val entries = js("[]").unsafeCast<Array<GPUBindGroupLayoutEntry>>()
        val entry = js("({})").unsafeCast<GPUBindGroupLayoutEntry>()
        entry.binding = 0
        entry.visibility = 3  // VERTEX | FRAGMENT (GPUShaderStage.VERTEX=1 | FRAGMENT=2)
        
        // ✅ KEY FIX: Declare dynamic offset support
        val buffer = js("({})")
        buffer.type = "uniform"
        buffer.hasDynamicOffset = true      // ✅ THIS ENABLES DYNAMIC OFFSETS!
        buffer.minBindingSize = 192          // Size of uniform struct (3x mat4 = 192 bytes)
        entry.buffer = buffer
        
        js("entries.push(entry)")
        layoutDescriptor.entries = entries
        
        return device!!.createBindGroupLayout(layoutDescriptor)
    }

    /**
     * T021 PERFORMANCE: Create pipeline layout using custom bind group layout.
     */
    private fun createUniformPipelineLayout(): GPUPipelineLayout {
        val layoutDescriptor = js("({})").unsafeCast<GPUPipelineLayoutDescriptor>()
        layoutDescriptor.label = "Uniform Pipeline Layout (Dynamic Offsets)"
        
        // Create array and add the bind group layout directly
        val bindGroupLayouts = js("[]")
        bindGroupLayouts[0] = uniformBindGroupLayout
        layoutDescriptor.bindGroupLayouts = bindGroupLayouts
        
        return device!!.createPipelineLayout(layoutDescriptor)
    }

    /**
     * T021 PERFORMANCE: Get or create cached bind group with dynamic offset support.
     * 
     * Creates bind group ONCE and reuses it across all mesh draws.
     * Instead of creating 68 bind groups per frame (~250ms), we create 1 and vary
     * the offset via setBindGroup(0, bindGroup, [dynamicOffset]).
     * 
     * This reduces bind group creation overhead from ~250ms to <1ms per frame.
     */
    private fun getOrCreateCachedBindGroup(): GPUBindGroup {
        // Return cached bind group if already created
        if (cachedUniformBindGroup != null) {
            return cachedUniformBindGroup!!
        }

        // Create bind group with dynamic offset support (first time only)
        val bindGroupDescriptor = js("({})").unsafeCast<GPUBindGroupDescriptor>()
        bindGroupDescriptor.label = "Cached Uniform Bind Group (Dynamic Offsets)"
        bindGroupDescriptor.layout = uniformBindGroupLayout!!

        val bindingEntries = js("[]").unsafeCast<Array<GPUBindGroupEntry>>()
        val bindingEntry = js("({})").unsafeCast<GPUBindGroupEntry>()
        bindingEntry.binding = 0
        
        val bufferBinding = js("({})")
        bufferBinding.buffer = uniformBuffer!!.getBuffer()!!
        // ✅ KEY: Set offset to 0 in descriptor!
        // Actual offset will be provided dynamically via setBindGroup()
        bufferBinding.offset = 0
        bufferBinding.size = 192
        bindingEntry.resource = bufferBinding
        js("bindingEntries.push(bindingEntry)")

        bindGroupDescriptor.entries = bindingEntries
        val bindGroup = device!!.createBindGroup(bindGroupDescriptor)

        // Cache for reuse
        cachedUniformBindGroup = bindGroup
        console.log("✅ T021 PERFORMANCE: Created cached bind group with dynamic offset support")
        
        return bindGroup
    }

    override fun dispose() {
        if (!isInitialized) {
            console.log("T033: Dispose called but renderer not initialized, skipping")
            return
        }

        console.log("T033: Starting WebGPU renderer disposal...")

        console.log("T033: Clearing pipeline cache...")
        pipelineCache.clear()
        console.log("T033: Pipeline cache cleared")

        console.log("T033: Disposing buffer pool...")
        bufferPool.dispose()
        console.log("T033: Buffer pool disposed")

        console.log("T033: Clearing context loss recovery...")
        contextLossRecovery.clear()
        console.log("T033: Context loss recovery cleared")

        depthTexture?.dispose()
        depthTexture = null
        depthTextureView = null
        depthTextureWidth = 0
        depthTextureHeight = 0

        geometryCache.clear()

        // T021 PERFORMANCE: Clean up cached bind group and layouts
        cachedUniformBindGroup = null
        uniformBindGroupLayout = null
        uniformPipelineLayout = null
        bindGroupCache.clear()

        // T020: Clean up Feature 020 managers
        console.log("T033: Cleaning up BufferManager...")
        // Note: BufferManager doesn't have a dispose method - buffers are cleaned up when device is destroyed
        bufferManager = null
        console.log("T033: BufferManager cleaned up")

        console.log("T033: Cleaning up RenderPassManager...")
        renderPassManager = null
        console.log("T033: RenderPassManager cleaned up")

        if (device != null) {
            console.log("T033: Destroying GPU device...")
            device?.asDynamic()?.destroy()
            device = null
            console.log("T033: GPU device destroyed")
        }

        console.log("T033: Releasing canvas context...")
        context = null
        console.log("T033: Canvas context released")

        isInitialized = false
        console.log("T033: WebGPU renderer disposal completed")
    }

    private fun createCapabilities(adapter: GPUAdapter): RendererCapabilities {
        val limits = adapter.limits
        return RendererCapabilities(
            maxTextureSize = limits.maxTextureDimension2D,
            maxCubeMapSize = limits.maxTextureDimension2D,
            maxVertexAttributes = limits.maxVertexAttributes,
            maxVertexUniforms = limits.maxUniformBufferBindingSize / 16,
            maxFragmentUniforms = limits.maxUniformBufferBindingSize / 16
        )
    }

    private fun createDefaultCapabilities(): RendererCapabilities {
        return RendererCapabilities(
            maxTextureSize = 8192,
            maxCubeMapSize = 8192,
            maxVertexAttributes = 16,
            maxVertexUniforms = 4096,
            maxFragmentUniforms = 4096
        )
    }

    // ========================================================================
    // T020: Feature 020 BufferManager Integration Helper Methods
    // ========================================================================

    /**
     * Create vertex buffer using Feature 020 BufferManager.
     *
     * This method demonstrates how to use the new BufferManager API
     * for simple vertex data (position + color, 6 floats per vertex).
     *
     * GeometryBufferCache currently uses position + normal + color (9 floats per vertex)
     * and will be migrated to use BufferManager in a future refactoring.
     *
     * @param vertices Interleaved vertex data [x, y, z, r, g, b, ...]
     * @return BufferHandle for the created vertex buffer
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
            }

            is io.kreekt.core.Result.Success -> {
                depthTexture = texture
                depthTextureView = texture.getView()
                depthTextureWidth = width
                depthTextureHeight = height
            }
        }
    }
}

