/**
 * WebGPUEffectComposer - Pass chain manager for WebGPU fullscreen effects
 *
 * Manages a chain of post-processing passes using WebGPU/WGSL shaders.
 * This is the WebGPU equivalent of WebGLEffectComposer for WebGL.
 *
 * Features:
 * - Pass chain management (add, remove, reorder)
 * - Ping-pong framebuffer system for multi-pass rendering
 * - Size propagation to all passes
 * - Enable/disable filtering
 * - Resource lifecycle management
 * - Automatic pipeline creation and caching
 *
 * Usage:
 * ```kotlin
 * val composer = WebGPUEffectComposer(device, width = 1920, height = 1080)
 *
 * composer.addPass(FullScreenEffectPass.create {
 *     fragmentShader = vignetteShader
 * })
 *
 * composer.addPass(FullScreenEffectPass.create(requiresInputTexture = true) {
 *     fragmentShader = colorGradingShader
 * })
 *
 * // In render loop:
 * composer.render(outputView)
 * ```
 */
package io.materia.renderer.webgpu

import io.materia.effects.BlendMode
import io.materia.effects.FullScreenEffectPass
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set

/**
 * Manages a chain of WebGPU post-processing effects.
 *
 * @property device The WebGPU device
 * @property width Current width in pixels
 * @property height Current height in pixels
 * @property format The texture format for render targets (default: bgra8unorm)
 */
class WebGPUEffectComposer(
    private val device: GPUDevice,
    width: Int = 0,
    height: Int = 0,
    private val format: String = "bgra8unorm"
) {
    private val _passes = mutableListOf<FullScreenEffectPass>()

    /** Read-only list of all passes in the chain */
    val passes: List<FullScreenEffectPass>
        get() = _passes.toList()

    /** Number of passes in the chain */
    val passCount: Int
        get() = _passes.size

    /** Current width in pixels */
    var width: Int = width
        private set

    /** Current height in pixels */
    var height: Int = height
        private set

    /** Whether this composer has been disposed */
    var isDisposed: Boolean = false
        private set

    // Ping-pong textures and views for multi-pass rendering
    private var textureA: GPUTexture? = null
    private var textureB: GPUTexture? = null
    private var viewA: GPUTextureView? = null
    private var viewB: GPUTextureView? = null

    /** Whether framebuffers have been initialized */
    private var framebuffersInitialized: Boolean = false

    // Cached pipelines and resources per pass
    private val pipelineCache = mutableMapOf<FullScreenEffectPass, GPURenderPipeline>()
    private val bindGroupCache = mutableMapOf<FullScreenEffectPass, GPUBindGroup>()
    private val uniformBufferCache = mutableMapOf<FullScreenEffectPass, GPUBuffer>()
    private val shaderModuleCache = mutableMapOf<FullScreenEffectPass, GPUShaderModule>()

    // Input texture bind group (for passes that need previous output)
    private var inputBindGroupLayoutA: GPUBindGroupLayout? = null
    private var inputBindGroupLayoutB: GPUBindGroupLayout? = null
    private var inputBindGroupA: GPUBindGroup? = null
    private var inputBindGroupB: GPUBindGroup? = null
    private var inputSampler: GPUSampler? = null

    /**
     * Initialize or resize the ping-pong textures.
     */
    fun initializeFramebuffers() {
        // Clean up existing framebuffers
        disposeFramebuffers()

        if (width <= 0 || height <= 0) return

        // Create texture A
        textureA = createRenderTexture("WebGPUEffectComposer-TextureA")
        viewA = textureA!!.createView()

        // Create texture B
        textureB = createRenderTexture("WebGPUEffectComposer-TextureB")
        viewB = textureB!!.createView()

        // Create sampler for input textures
        inputSampler = device.createSampler(js("({})").unsafeCast<GPUSamplerDescriptor>().apply {
            magFilter = "linear"
            minFilter = "linear"
            addressModeU = "clamp-to-edge"
            addressModeV = "clamp-to-edge"
        })

        // Create bind group layout for input texture
        val bindGroupLayoutDescriptor = js("({})").unsafeCast<GPUBindGroupLayoutDescriptor>()
        bindGroupLayoutDescriptor.label = "WebGPUEffectComposer-InputBindGroupLayout"
        bindGroupLayoutDescriptor.entries = arrayOf(
            js("({})").unsafeCast<GPUBindGroupLayoutEntry>().apply {
                binding = 0
                visibility = GPUShaderStage.FRAGMENT
                texture = js("({})").unsafeCast<GPUTextureBindingLayout>().apply {
                    sampleType = "float"
                    viewDimension = "2d"
                }
            },
            js("({})").unsafeCast<GPUBindGroupLayoutEntry>().apply {
                binding = 1
                visibility = GPUShaderStage.FRAGMENT
                sampler = js("({})").unsafeCast<GPUSamplerBindingLayout>().apply {
                    type = "filtering"
                }
            }
        )
        inputBindGroupLayoutA = device.createBindGroupLayout(bindGroupLayoutDescriptor)
        inputBindGroupLayoutB = device.createBindGroupLayout(bindGroupLayoutDescriptor)

        // Create bind groups for ping-pong textures
        inputBindGroupA = createInputBindGroup(viewA!!, inputBindGroupLayoutA!!, "InputBindGroup-A")
        inputBindGroupB = createInputBindGroup(viewB!!, inputBindGroupLayoutB!!, "InputBindGroup-B")

        framebuffersInitialized = true
    }

    private fun createRenderTexture(label: String): GPUTexture {
        val descriptor = js("({})").unsafeCast<GPUTextureDescriptor>()
        descriptor.label = label
        
        // Build size object programmatically (js() requires constant strings)
        val size = js("({})").asDynamic()
        size.width = width
        size.height = height
        descriptor.asDynamic().size = size
        
        descriptor.format = format
        descriptor.usage = GPUTextureUsage.TEXTURE_BINDING or
                GPUTextureUsage.RENDER_ATTACHMENT or
                GPUTextureUsage.COPY_SRC or
                GPUTextureUsage.COPY_DST
        return device.createTexture(descriptor)
    }

    private fun createInputBindGroup(view: GPUTextureView, layout: GPUBindGroupLayout, label: String): GPUBindGroup {
        val descriptor = js("({})").unsafeCast<GPUBindGroupDescriptor>()
        descriptor.label = label
        descriptor.layout = layout
        descriptor.entries = arrayOf(
            js("({})").unsafeCast<GPUBindGroupEntry>().apply {
                binding = 0
                resource = view
            },
            js("({})").unsafeCast<GPUBindGroupEntry>().apply {
                binding = 1
                resource = inputSampler!!
            }
        )
        return device.createBindGroup(descriptor)
    }

    private fun disposeFramebuffers() {
        textureA?.destroy()
        textureB?.destroy()
        textureA = null
        textureB = null
        viewA = null
        viewB = null
        inputBindGroupA = null
        inputBindGroupB = null
        inputBindGroupLayoutA = null
        inputBindGroupLayoutB = null
        inputSampler = null
        framebuffersInitialized = false
    }

    /**
     * Adds a pass to the end of the chain.
     */
    fun addPass(pass: FullScreenEffectPass) {
        checkNotDisposed()
        _passes.add(pass)
        pass.setSize(width, height)
        // Pipeline will be created lazily on first render
    }

    /**
     * Inserts a pass at the specified index.
     */
    fun insertPass(pass: FullScreenEffectPass, index: Int) {
        checkNotDisposed()
        _passes.add(index, pass)
        pass.setSize(width, height)
    }

    /**
     * Removes a pass from the chain.
     */
    fun removePass(pass: FullScreenEffectPass): Boolean {
        val removed = _passes.remove(pass)
        if (removed) {
            // Clean up cached resources for this pass
            pipelineCache.remove(pass)
            bindGroupCache.remove(pass)
            uniformBufferCache[pass]?.destroy()
            uniformBufferCache.remove(pass)
            shaderModuleCache.remove(pass)
        }
        return removed
    }

    /**
     * Removes the pass at the specified index.
     */
    fun removePassAt(index: Int): FullScreenEffectPass {
        val pass = _passes.removeAt(index)
        // Clean up cached resources
        pipelineCache.remove(pass)
        bindGroupCache.remove(pass)
        uniformBufferCache[pass]?.destroy()
        uniformBufferCache.remove(pass)
        shaderModuleCache.remove(pass)
        return pass
    }

    /**
     * Removes all passes from the chain.
     */
    fun clearPasses() {
        for (pass in _passes) {
            pipelineCache.remove(pass)
            bindGroupCache.remove(pass)
            uniformBufferCache[pass]?.destroy()
            uniformBufferCache.remove(pass)
            shaderModuleCache.remove(pass)
        }
        _passes.clear()
    }

    /**
     * Updates the size and propagates to all passes.
     */
    fun setSize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return

        this.width = width
        this.height = height

        for (pass in _passes) {
            pass.setSize(width, height)
        }

        // Reinitialize framebuffers at new size
        if (framebuffersInitialized) {
            initializeFramebuffers()
        }

        // Invalidate bind groups since uniform buffers may need updating
        bindGroupCache.clear()
    }

    /**
     * Swaps the positions of two passes.
     */
    fun swapPasses(index1: Int, index2: Int) {
        val temp = _passes[index1]
        _passes[index1] = _passes[index2]
        _passes[index2] = temp
    }

    /**
     * Gets only enabled passes.
     */
    fun getEnabledPasses(): List<FullScreenEffectPass> {
        return _passes.filter { it.enabled && !it.isDisposed }
    }

    /**
     * Render all enabled passes in the chain.
     *
     * This uses ping-pong textures for multi-pass rendering:
     * - First pass renders to texture A (or output if single pass)
     * - Second pass reads from texture A, writes to texture B
     * - Third pass reads from texture B, writes to texture A
     * - And so on...
     * - Final pass writes directly to the output view
     *
     * @param outputView The final render target (e.g., swapchain texture view)
     */
    fun render(outputView: GPUTextureView) {
        if (isDisposed) return

        val enabledPasses = getEnabledPasses()
        if (enabledPasses.isEmpty()) return

        // Initialize framebuffers if needed for multi-pass
        if (!framebuffersInitialized && enabledPasses.size > 1) {
            initializeFramebuffers()
        }

        // Track ping-pong state
        // We alternate writing between A and B textures
        // readFromA tracks which texture contains the previous pass's output
        var readFromA = false  // After first pass writes to A, next pass reads from A
        
        for ((index, pass) in enabledPasses.withIndex()) {
            val isFirstPass = index == 0
            val isLastPass = index == enabledPasses.lastIndex

            // Determine the render target
            // - Last pass (or renderToScreen) writes to the output view
            // - Otherwise alternate between A and B
            val targetView = if (isLastPass || pass.renderToScreen) {
                outputView
            } else {
                // First pass writes to A, second to B, third to A, etc.
                if (index % 2 == 0) viewA!! else viewB!!
            }

            // Get or create pipeline for this pass
            val pipeline = getOrCreatePipeline(pass)
            
            // Get or create uniform bind group
            val uniformBindGroup = getOrCreateUniformBindGroup(pass)
            
            // Update uniform buffer if dirty
            if (pass.isUniformBufferDirty) {
                updateUniformBuffer(pass)
                pass.clearDirtyFlag()
            }

            // Determine input bind group for passes that need previous output
            // inputBindGroupA contains viewA, inputBindGroupB contains viewB
            val inputBindGroup: GPUBindGroup? = if (pass.requiresInputTexture && !isFirstPass) {
                // Read from whichever texture the previous pass wrote to
                if (readFromA) inputBindGroupA else inputBindGroupB
            } else {
                null
            }

            // Create command encoder and render pass
            val commandEncoder = device.createCommandEncoder()
            
            val colorAttachment = js("({})").unsafeCast<GPURenderPassColorAttachment>()
            colorAttachment.view = targetView
            colorAttachment.loadOp = "clear"
            colorAttachment.storeOp = "store"
            
            // Build clearValue object programmatically
            val cc = pass.clearColor
            val clearValue = js("({})").asDynamic()
            clearValue.r = cc.r
            clearValue.g = cc.g
            clearValue.b = cc.b
            clearValue.a = cc.a
            colorAttachment.asDynamic().clearValue = clearValue

            val renderPassDescriptor = js("({})").unsafeCast<GPURenderPassDescriptor>()
            renderPassDescriptor.colorAttachments = arrayOf(colorAttachment)

            val renderPass = commandEncoder.beginRenderPass(renderPassDescriptor)
            renderPass.setPipeline(pipeline)
            
            // Bind uniform group at index 0
            if (uniformBindGroup != null) {
                renderPass.setBindGroup(0, uniformBindGroup)
            }
            
            // Bind input texture group at index 1 (if needed)
            if (inputBindGroup != null) {
                renderPass.setBindGroup(1, inputBindGroup)
            }
            
            // Draw fullscreen triangle (3 vertices)
            renderPass.draw(3)
            renderPass.end()

            // Submit commands
            device.queue.submit(arrayOf(commandEncoder.finish()))

            // Update state for next pass - track which texture we just wrote to
            if (!isLastPass) {
                readFromA = (index % 2 == 0)  // We just wrote to A if index was even
            }
        }
    }

    /**
     * Render a single pass directly (no pass chain).
     */
    fun renderSingle(pass: FullScreenEffectPass, outputView: GPUTextureView) {
        if (isDisposed) return

        val pipeline = getOrCreatePipeline(pass)
        val uniformBindGroup = getOrCreateUniformBindGroup(pass)

        if (pass.isUniformBufferDirty) {
            updateUniformBuffer(pass)
            pass.clearDirtyFlag()
        }

        val commandEncoder = device.createCommandEncoder()

        val colorAttachment = js("({})").unsafeCast<GPURenderPassColorAttachment>()
        colorAttachment.view = outputView
        colorAttachment.loadOp = "clear"
        colorAttachment.storeOp = "store"
        
        // Build clearValue object programmatically
        val cc = pass.clearColor
        val clearValue = js("({})").asDynamic()
        clearValue.r = cc.r
        clearValue.g = cc.g
        clearValue.b = cc.b
        clearValue.a = cc.a
        colorAttachment.asDynamic().clearValue = clearValue

        val renderPassDescriptor = js("({})").unsafeCast<GPURenderPassDescriptor>()
        renderPassDescriptor.colorAttachments = arrayOf(colorAttachment)

        val renderPass = commandEncoder.beginRenderPass(renderPassDescriptor)
        renderPass.setPipeline(pipeline)

        if (uniformBindGroup != null) {
            renderPass.setBindGroup(0, uniformBindGroup)
        }

        renderPass.draw(3)
        renderPass.end()

        device.queue.submit(arrayOf(commandEncoder.finish()))
    }

    private fun getOrCreatePipeline(pass: FullScreenEffectPass): GPURenderPipeline {
        pipelineCache[pass]?.let { return it }

        // Create shader module
        val shaderCode = pass.getShaderCode()
        val shaderModuleDescriptor = js("({})").unsafeCast<GPUShaderModuleDescriptor>()
        shaderModuleDescriptor.label = "WebGPUEffectComposer-ShaderModule"
        shaderModuleDescriptor.code = shaderCode
        val shaderModule = device.createShaderModule(shaderModuleDescriptor)
        shaderModuleCache[pass] = shaderModule

        // Create bind group layout for uniforms
        val bindGroupLayouts = mutableListOf<GPUBindGroupLayout>()
        
        if (pass.effect.uniforms.size > 0) {
            val uniformBindGroupLayout = createUniformBindGroupLayout()
            bindGroupLayouts.add(uniformBindGroupLayout)
        }
        
        // Add input texture bind group layout if needed
        if (pass.requiresInputTexture) {
            inputBindGroupLayoutA?.let { bindGroupLayouts.add(it) }
        }

        // Create pipeline layout
        val pipelineLayoutDescriptor = js("({})").unsafeCast<GPUPipelineLayoutDescriptor>()
        pipelineLayoutDescriptor.label = "WebGPUEffectComposer-PipelineLayout"
        pipelineLayoutDescriptor.bindGroupLayouts = bindGroupLayouts.toTypedArray()
        val pipelineLayout = device.createPipelineLayout(pipelineLayoutDescriptor)

        // Create render pipeline
        val pipelineDescriptor = js("({})").unsafeCast<GPURenderPipelineDescriptor>()
        pipelineDescriptor.label = "WebGPUEffectComposer-RenderPipeline"
        pipelineDescriptor.layout = pipelineLayout

        // Vertex state
        pipelineDescriptor.vertex = js("({})").unsafeCast<GPUVertexState>().apply {
            module = shaderModule
            entryPoint = "vs_main"
        }

        // Fragment state
        pipelineDescriptor.fragment = js("({})").unsafeCast<GPUFragmentState>().apply {
            module = shaderModule
            entryPoint = "main"
            targets = arrayOf(
                js("({})").unsafeCast<GPUColorTargetState>().apply {
                    this.format = this@WebGPUEffectComposer.format
                    blend = createBlendState(pass.blendMode)
                }
            )
        }

        // Primitive state
        pipelineDescriptor.primitive = js("({})").unsafeCast<GPUPrimitiveState>().apply {
            topology = "triangle-list"
            cullMode = "none"
        }

        val pipeline = device.createRenderPipeline(pipelineDescriptor)
        pipelineCache[pass] = pipeline
        return pipeline
    }

    private fun createUniformBindGroupLayout(): GPUBindGroupLayout {
        val descriptor = js("({})").unsafeCast<GPUBindGroupLayoutDescriptor>()
        descriptor.label = "WebGPUEffectComposer-UniformBindGroupLayout"
        descriptor.entries = arrayOf(
            js("({})").unsafeCast<GPUBindGroupLayoutEntry>().apply {
                binding = 0
                visibility = GPUShaderStage.VERTEX or GPUShaderStage.FRAGMENT
                buffer = js("({})").unsafeCast<GPUBufferBindingLayout>().apply {
                    type = "uniform"
                }
            }
        )
        return device.createBindGroupLayout(descriptor)
    }

    private fun createBlendState(blendMode: BlendMode): GPUBlendState? {
        return when (blendMode) {
            BlendMode.OPAQUE -> null
            BlendMode.ALPHA_BLEND -> js("({})").unsafeCast<GPUBlendState>().apply {
                color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "src-alpha"
                    dstFactor = "one-minus-src-alpha"
                    operation = "add"
                }
                alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one-minus-src-alpha"
                    operation = "add"
                }
            }
            BlendMode.ADDITIVE -> js("({})").unsafeCast<GPUBlendState>().apply {
                color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one"
                    operation = "add"
                }
                alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one"
                    operation = "add"
                }
            }
            BlendMode.MULTIPLY -> js("({})").unsafeCast<GPUBlendState>().apply {
                color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "dst"
                    dstFactor = "zero"
                    operation = "add"
                }
                alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "dst-alpha"
                    dstFactor = "zero"
                    operation = "add"
                }
            }
            BlendMode.SCREEN -> js("({})").unsafeCast<GPUBlendState>().apply {
                color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one-minus-src"
                    operation = "add"
                }
                alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one-minus-src-alpha"
                    operation = "add"
                }
            }
            BlendMode.OVERLAY -> {
                // Overlay cannot be done with fixed-function blending, approximate with multiply
                js("({})").unsafeCast<GPUBlendState>().apply {
                    color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                        srcFactor = "dst"
                        dstFactor = "zero"
                        operation = "add"
                    }
                    alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                        srcFactor = "dst-alpha"
                        dstFactor = "zero"
                        operation = "add"
                    }
                }
            }
            BlendMode.PREMULTIPLIED_ALPHA -> js("({})").unsafeCast<GPUBlendState>().apply {
                color = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one-minus-src-alpha"
                    operation = "add"
                }
                alpha = js("({})").unsafeCast<GPUBlendComponent>().apply {
                    srcFactor = "one"
                    dstFactor = "one-minus-src-alpha"
                    operation = "add"
                }
            }
        }
    }

    private fun getOrCreateUniformBindGroup(pass: FullScreenEffectPass): GPUBindGroup? {
        if (pass.effect.uniforms.size == 0) return null

        bindGroupCache[pass]?.let { return it }

        // Create or get uniform buffer
        val uniformBuffer = getOrCreateUniformBuffer(pass)

        // Create bind group layout
        val bindGroupLayout = createUniformBindGroupLayout()

        // Create bind group
        val descriptor = js("({})").unsafeCast<GPUBindGroupDescriptor>()
        descriptor.label = "WebGPUEffectComposer-UniformBindGroup"
        descriptor.layout = bindGroupLayout
        descriptor.entries = arrayOf(
            js("({})").unsafeCast<GPUBindGroupEntry>().apply {
                binding = 0
                resource = js("({})").unsafeCast<GPUBufferBinding>().apply {
                    buffer = uniformBuffer
                }
            }
        )

        val bindGroup = device.createBindGroup(descriptor)
        bindGroupCache[pass] = bindGroup
        return bindGroup
    }

    private fun getOrCreateUniformBuffer(pass: FullScreenEffectPass): GPUBuffer {
        uniformBufferCache[pass]?.let { return it }

        val bufferSize = pass.effect.uniformBuffer.size * 4 // FloatArray to bytes
        // Ensure 16-byte alignment for WebGPU
        val alignedSize = ((bufferSize + 15) / 16) * 16

        val descriptor = js("({})").unsafeCast<GPUBufferDescriptor>()
        descriptor.label = "WebGPUEffectComposer-UniformBuffer"
        descriptor.size = alignedSize
        descriptor.usage = GPUBufferUsage.UNIFORM or GPUBufferUsage.COPY_DST

        val buffer = device.createBuffer(descriptor)
        uniformBufferCache[pass] = buffer

        // Initial upload
        uploadUniformBuffer(pass, buffer)

        return buffer
    }

    private fun updateUniformBuffer(pass: FullScreenEffectPass) {
        val buffer = uniformBufferCache[pass] ?: return
        uploadUniformBuffer(pass, buffer)
    }

    private fun uploadUniformBuffer(pass: FullScreenEffectPass, buffer: GPUBuffer) {
        val data = pass.effect.uniformBuffer
        val float32Array = Float32Array(data.size)
        for (i in data.indices) {
            float32Array[i] = data[i]
        }
        device.queue.writeBuffer(buffer, 0, float32Array)
    }

    /**
     * Releases all resources.
     */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        // Dispose all passes
        for (pass in _passes) {
            pass.dispose()
        }
        _passes.clear()

        // Clean up cached resources
        for (buffer in uniformBufferCache.values) {
            buffer.destroy()
        }
        uniformBufferCache.clear()
        pipelineCache.clear()
        bindGroupCache.clear()
        shaderModuleCache.clear()

        // Clean up framebuffers
        disposeFramebuffers()
    }

    private fun checkNotDisposed() {
        check(!isDisposed) { "WebGPUEffectComposer has been disposed" }
    }
}
