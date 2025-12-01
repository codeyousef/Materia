package io.materia.gpu

import io.materia.renderer.RenderSurface
import kotlin.js.Promise
import kotlin.js.jsTypeOf
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLCanvasElement

private fun navigatorGpu(): dynamic =
    js("typeof navigator !== 'undefined' && navigator.gpu ? navigator.gpu : null")

private fun currentDocument(): dynamic = js("typeof document !== 'undefined' ? document : null")
private fun emptyObject(): dynamic = js("({})")
private fun emptyArray(): dynamic = js("[]")
private fun createCanvas(document: dynamic): dynamic = document.createElement("canvas")

private fun JsArray(): dynamic = emptyArray()

private fun FloatArray.componentOrDefault(index: Int, default: Double): Double =
    if (index in indices) this[index].toDouble() else default

private suspend fun <T> Promise<T>.awaitJs(): T = await()

private fun requireWebGpu(): dynamic =
    navigatorGpu() ?: error("WebGPU is not available in this environment")

private fun String?.orDefault(default: String): String = this ?: default

private fun ensureCanvas(label: String?): dynamic {
    val document = currentDocument() ?: error("Document is not available")
    val canvasId = label.orDefault("materia-canvas")
    val existing = document.getElementById(canvasId)
    return existing ?: createCanvas(document).also { created ->
        created.id = canvasId
        val parent = document.body ?: document
        parent.appendChild(created)
    }
}

private fun configureCanvas(canvas: dynamic, width: Int, height: Int) {
    canvas.width = width
    canvas.height = height
}

private fun createUint8Array(length: Int): Uint8Array = Uint8Array(length)
private fun createFloat32Array(length: Int): Float32Array = Float32Array(length)

private fun callIfFunction(target: dynamic, functionName: String) {
    val fn = target[functionName]
    if (fn != null && jsTypeOf(fn) == "function") {
        fn.call(target)
    }
}

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance {
    requireWebGpu()
    return GpuInstance(descriptor)
}

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter =
        ensureActive {
            val gpu = requireWebGpu()
            val jsOptions = emptyObject()
            jsOptions.powerPreference = when (options.powerPreference) {
                GpuPowerPreference.LOW_POWER -> "low-power"
                GpuPowerPreference.HIGH_PERFORMANCE -> "high-performance"
            }
            if (options.forceFallbackAdapter) {
                jsOptions.forceFallbackAdapter = true
            }
            val adapterHandle = (gpu.requestAdapter(jsOptions) as Promise<dynamic>).awaitJs()
                ?: error("Failed to acquire WebGPU adapter")

            val info = adapterHandle.info
            val adapterInfo = GpuAdapterInfo(
                name = info?.device as? String
                    ?: info?.name as? String
                    ?: "WebGPU Adapter",
                vendor = info?.vendor as? String,
                architecture = info?.architecture as? String,
                driverVersion = info?.driver as? String ?: info?.driverVersion as? String
            )

            GpuAdapter(
                backend = descriptor.preferredBackends.firstOrNull() ?: GpuBackend.WEBGPU,
                options = options,
                info = adapterInfo
            ).apply { attachHandle(adapterHandle) }
        }

    actual fun dispose() {
        disposed = true
    }

    private inline fun <T> ensureActive(block: () -> T): T {
        check(!disposed) { "GpuInstance has been disposed." }
        return block()
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    private var handle: dynamic = null

    internal fun attachHandle(adapterHandle: dynamic) {
        handle = adapterHandle
    }

    private fun adapterHandle(): dynamic {
        val stored = handle
        require(stored != null) { "Adapter handle not initialised" }
        return stored
    }

    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        if (descriptor.requiredFeatures.isNotEmpty()) {
            jsDescriptor.requiredFeatures = descriptor.requiredFeatures.toTypedArray()
        }
        if (descriptor.requiredLimits.isNotEmpty()) {
            val limits = emptyObject()
            val limitsDyn = limits
            descriptor.requiredLimits.forEach { (key, value) ->
                limitsDyn[key] = value
            }
            jsDescriptor.requiredLimits = limits
        }

        val deviceHandle =
            (adapterHandle().requestDevice(jsDescriptor) as Promise<dynamic>).awaitJs()
        val queueHandle = deviceHandle.queue ?: error("WebGPU device queue unavailable")

        return GpuDevice(this, descriptor).apply {
            attachHandles(deviceHandle, queueHandle)
        }
    }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    private var deviceHandle: dynamic = null
    private var queueHandle: dynamic = null

    actual val queue: GpuQueue = GpuQueue(descriptor.label)

    internal fun attachHandles(device: dynamic, queue: dynamic) {
        deviceHandle = device
        queueHandle = queue
        this.queue.attachQueue(queue)
    }

    internal fun handle(): dynamic {
        val handle = deviceHandle
        require(handle != null) { "Device handle not initialised" }
        return handle
    }

    private fun queue(): dynamic {
        val handle = queueHandle
        require(handle != null) { "Queue handle not initialised" }
        return handle
    }

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        require(descriptor.size <= Int.MAX_VALUE) {
            "WebGPU buffer size must be <= Int.MAX_VALUE, got ${descriptor.size}"
        }

        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        jsDescriptor.size = descriptor.size.toInt()
        jsDescriptor.usage = descriptor.usage
        jsDescriptor.mappedAtCreation = descriptor.mappedAtCreation
        val buffer = handle().createBuffer(jsDescriptor)
        return GpuBuffer(this, descriptor).apply { attach(buffer, queue()) }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        val size = emptyObject()
        size.width = descriptor.size.first
        size.height = descriptor.size.second
        size.depthOrArrayLayers = descriptor.size.third
        jsDescriptor.size = size
        jsDescriptor.mipLevelCount = descriptor.mipLevelCount
        jsDescriptor.sampleCount = descriptor.sampleCount
        jsDescriptor.dimension = when (descriptor.dimension) {
            GpuTextureDimension.D1 -> "1d"
            GpuTextureDimension.D2 -> "2d"
            GpuTextureDimension.D3 -> "3d"
        }
        jsDescriptor.format = descriptor.format.toWebGpuFormat()
        jsDescriptor.usage = descriptor.usage
        val texture = handle().createTexture(jsDescriptor)
        return GpuTexture(this, descriptor).apply { attach(texture) }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        jsDescriptor.addressModeU =
            descriptor.addressModeU.name.lowercase().replace('_', '-')
        jsDescriptor.addressModeV =
            descriptor.addressModeV.name.lowercase().replace('_', '-')
        jsDescriptor.addressModeW =
            descriptor.addressModeW.name.lowercase().replace('_', '-')
        jsDescriptor.magFilter = descriptor.magFilter.name.lowercase()
        jsDescriptor.minFilter = descriptor.minFilter.name.lowercase()
        jsDescriptor.mipmapFilter = descriptor.mipmapFilter.name.lowercase()
        jsDescriptor.lodMinClamp = descriptor.lodMinClamp
        jsDescriptor.lodMaxClamp = descriptor.lodMaxClamp
        val sampler = handle().createSampler(jsDescriptor)
        return GpuSampler(this, descriptor).apply { attach(sampler) }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        val entries = JsArray()
        descriptor.entries.forEach { entry ->
            val jsEntry = emptyObject()
            jsEntry.binding = entry.binding
            jsEntry.visibility = entry.visibility.toWebGpuVisibilityMask()
            when (entry.resourceType) {
                GpuBindingResourceType.UNIFORM_BUFFER -> {
                    val buffer = emptyObject()
                    buffer.type = "uniform"
                    jsEntry.buffer = buffer
                }

                GpuBindingResourceType.STORAGE_BUFFER -> {
                    val buffer = emptyObject()
                    buffer.type = "storage"
                    jsEntry.buffer = buffer
                }

                GpuBindingResourceType.SAMPLER -> {
                    val sampler = emptyObject()
                    sampler.type = "filtering"
                    jsEntry.sampler = sampler
                }

                GpuBindingResourceType.TEXTURE -> {
                    val texture = emptyObject()
                    texture.sampleType = "float"
                    texture.viewDimension = "2d"
                    texture.multisampled = false
                    jsEntry.texture = texture
                }
            }
            entries.push(jsEntry)
        }
        jsDescriptor.entries = entries
        val layout = handle().createBindGroupLayout(jsDescriptor)
        return GpuBindGroupLayout(this, descriptor).apply { attach(layout) }
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        jsDescriptor.layout = descriptor.layout.handle()
        val entries = JsArray()
        descriptor.entries.forEach { entry ->
            val jsEntry = emptyObject()
            jsEntry.binding = entry.binding
            val resource = when (val binding = entry.resource) {
                is GpuBindingResource.Buffer -> {
                    val bufferBinding = emptyObject()
                    bufferBinding.buffer = binding.buffer.handle()
                    if (binding.offset != 0L) {
                        bufferBinding.offset = binding.offset
                    }
                    binding.size?.let { bufferBinding.size = it }
                    bufferBinding
                }

                is GpuBindingResource.Sampler -> binding.sampler.handle()
                is GpuBindingResource.Texture -> binding.textureView.handle()
            }
            jsEntry.resource = resource
            entries.push(jsEntry)
        }
        jsDescriptor.entries = entries
        val bindGroup = handle().createBindGroup(jsDescriptor)
        return GpuBindGroup(descriptor.layout, descriptor).apply { attach(bindGroup) }
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder {
        val encoder = descriptor?.label?.let {
            val encDesc = emptyObject()
            encDesc.label = it
            handle().createCommandEncoder(encDesc)
        } ?: handle().createCommandEncoder()
        return GpuCommandEncoder(this, descriptor).apply { attach(encoder) }
    }

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        val moduleDescriptor = emptyObject()
        descriptor.label?.let { moduleDescriptor.label = it }
        moduleDescriptor.code = descriptor.code
        val module = handle().createShaderModule(moduleDescriptor)
        return GpuShaderModule(this, descriptor).apply { attach(module) }
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline {
        val pipelineDescriptor = emptyObject()
        descriptor.label?.let { pipelineDescriptor.label = it }

        if (descriptor.bindGroupLayouts.isNotEmpty()) {
            val layoutDescriptor = emptyObject()
            val layouts = JsArray()
            descriptor.bindGroupLayouts.forEach { layout ->
                layouts.push(layout.handle())
            }
            layoutDescriptor.bindGroupLayouts = layouts
            val pipelineLayout = handle().createPipelineLayout(layoutDescriptor)
            pipelineDescriptor.layout = pipelineLayout
        } else {
            pipelineDescriptor.layout = "auto"
        }
        descriptor.depthState?.let { depthState ->
            val depthStencil = emptyObject()
            depthStencil.format = depthState.format.toWebGpuFormat()
            depthStencil.depthWriteEnabled = depthState.depthWriteEnabled
            depthStencil.depthCompare = depthState.depthCompare.toWebGpu()
            pipelineDescriptor.depthStencil = depthStencil
        }

        val vertexState = emptyObject()
        vertexState.module = descriptor.vertexShader.handle()
        vertexState.entryPoint = "main"
        val vertexBuffers = JsArray()
        descriptor.vertexBuffers.forEach { layout ->
            val bufferLayout = emptyObject()
            bufferLayout.arrayStride = layout.arrayStride
            bufferLayout.stepMode = layout.stepMode.toWebGpu()
            val attributes = JsArray()
            layout.attributes.forEach { attribute ->
                val attributeDesc = emptyObject()
                attributeDesc.shaderLocation = attribute.shaderLocation
                attributeDesc.offset = attribute.offset
                attributeDesc.format = attribute.format.toWebGpuFormat()
                attributes.push(attributeDesc)
            }
            bufferLayout.attributes = attributes
            vertexBuffers.push(bufferLayout)
        }
        vertexState.buffers = vertexBuffers
        pipelineDescriptor.vertex = vertexState

        descriptor.fragmentShader?.let { fragment ->
            val fragmentState = emptyObject()
            fragmentState.module = fragment.handle()
            fragmentState.entryPoint = "main"
            val targets = JsArray()
            descriptor.colorFormats.forEach { format ->
                val target = emptyObject()
                target.format = format.toWebGpuFormat()
                val blend = descriptor.blendMode.toWebGpu()
                if (blend != null) {
                    target.blend = blend
                }
                targets.push(target)
            }
            fragmentState.targets = targets
            pipelineDescriptor.fragment = fragmentState
        }

        val primitive = emptyObject()
        primitive.topology = descriptor.primitiveTopology.toWebGpu()
        primitive.cullMode = descriptor.cullMode.toWebGpu()
        primitive.frontFace = descriptor.frontFace.toWebGpu()
        pipelineDescriptor.primitive = primitive

        descriptor.depthStencilFormat?.let { depthFormat ->
            val depthStencil = emptyObject()
            depthStencil.format = depthFormat.toWebGpuFormat()
            depthStencil.depthWriteEnabled = true
            depthStencil.depthCompare = "less"
            pipelineDescriptor.depthStencil = depthStencil
        }

        val pipeline = handle().createRenderPipeline(pipelineDescriptor)
        return GpuRenderPipeline(this, descriptor).apply { attach(pipeline) }
    }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline {
        val pipelineDescriptor = emptyObject()
        descriptor.label?.let { pipelineDescriptor.label = it }
        val computeState = emptyObject()
        computeState.module = descriptor.shader.handle()
        computeState.entryPoint = "main"
        pipelineDescriptor.compute = computeState
        pipelineDescriptor.layout = "auto"
        val pipeline = handle().createComputePipeline(pipelineDescriptor)
        return GpuComputePipeline(this, descriptor).apply { attach(pipeline) }
    }

    actual fun destroy() {
        val handle = deviceHandle
        if (handle != null) {
            callIfFunction(handle, "destroy")
        }
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    private var queueHandle: dynamic = null

    internal fun attachQueue(handle: dynamic) {
        queueHandle = handle
    }

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        val queue = queueHandle ?: return
        val jsBuffers = JsArray()
        commandBuffers.forEach { buffer ->
            jsBuffers.push(buffer.handle())
        }
        queue.submit(jsBuffers)
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    internal var configuration: GpuSurfaceConfiguration? = null
    private var configuredDevice: GpuDevice? = null
    internal var canvas: dynamic = null
    private var context: dynamic = null
    internal var attachedSurface: RenderSurface? = null

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        configuredDevice = device
        this.configuration = configuration
        // Use the attached canvas if available, otherwise create/find one by label
        val existingCanvas = canvas
        val canvasElement = if (existingCanvas != null && jsTypeOf(existingCanvas) != "undefined") {
            existingCanvas
        } else {
            ensureCanvas(label)
        }
        configureCanvas(canvasElement, configuration.width, configuration.height)
        this.canvas = canvasElement
        
        console.log("GpuSurface.configure: canvas.width=${canvasElement.width}, canvas.height=${canvasElement.height}, config=${configuration.width}x${configuration.height}")

        context = context ?: canvasElement.getContext("webgpu")
                ?: error("Unable to acquire WebGPU canvas context")

        val jsConfig = emptyObject()
        jsConfig.device = device.handle()
        jsConfig.format = configuration.format.toWebGpuFormat()
        jsConfig.usage = configuration.usage
        jsConfig.alphaMode = "opaque"
        context.configure(jsConfig)
        
        // Check what the context thinks the size is
        val tex = context.getCurrentTexture()
        console.log("GpuSurface.configure: texture size=${tex.width}x${tex.height}")
    }

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat {
        val gpu = requireWebGpu()
        val preferred = gpu.getPreferredCanvasFormat?.call(gpu) as? String
        return when (preferred) {
            "bgra8unorm" -> GpuTextureFormat.BGRA8_UNORM
            "rgba8unorm" -> GpuTextureFormat.RGBA8_UNORM
            "rgba16float" -> GpuTextureFormat.RGBA16_FLOAT
            else -> configuration?.format ?: GpuTextureFormat.BGRA8_UNORM
        }
    }

    actual fun acquireFrame(): GpuSurfaceFrame {
        val device = configuredDevice ?: error("GpuSurface not configured with a device")
        val context = context ?: error("Surface not configured with context")
        val canvas = this.canvas ?: error("GpuSurface canvas not initialised")
        val textureHandle = context.getCurrentTexture()
            ?: error("Failed to acquire swapchain texture")
        val surfaceConfig = configuration ?: error("Surface configuration missing")
        
        // Get actual texture dimensions from the WebGPU texture, not from canvas
        val actualWidth = textureHandle.width as Int
        val actualHeight = textureHandle.height as Int
        
        val texture = GpuTexture(
            device = device,
            descriptor = GpuTextureDescriptor(
                label = "${label.orDefault("surface")}-frame",
                size = Triple(actualWidth, actualHeight, 1),
                mipLevelCount = 1,
                sampleCount = 1,
                dimension = GpuTextureDimension.D2,
                format = surfaceConfig.format,
                usage = surfaceConfig.usage
            )
        ).apply { attach(textureHandle) }
        val view = texture.createView(GpuTextureViewDescriptor())
        return GpuSurfaceFrame(texture, view)
    }

    actual fun present(frame: GpuSurfaceFrame) {
        // WebGPU presents automatically after queue submission.
    }

    actual fun resize(width: Int, height: Int) {
        val dev = configuredDevice ?: return
        val ctx = context ?: return
        val config = configuration ?: return
        val canvasEl = canvas ?: return
        
        // First update the canvas dimensions
        configureCanvas(canvasEl, width, height)
        
        // Update configuration
        val newConfig = config.copy(width = width, height = height)
        configuration = newConfig
        
        // Reconfigure the WebGPU context - it will use the canvas dimensions
        val jsConfig = emptyObject()
        jsConfig.device = dev.handle()
        jsConfig.format = newConfig.format.toWebGpuFormat()
        jsConfig.usage = newConfig.usage
        jsConfig.alphaMode = "opaque"
        ctx.configure(jsConfig)
        
        // Verify the texture size after reconfiguration
        val tex = ctx.getCurrentTexture()
        console.log("GpuSurface.resize: canvas=${canvasEl.width}x${canvasEl.height}, texture=${tex.width}x${tex.height}")
    }
}

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {
    attachedSurface = surface
    val handle = surface.getHandle()
    val canvasElement = when (handle) {
        is HTMLCanvasElement -> handle
        else -> handle as? HTMLCanvasElement
    } ?: error("RenderSurface handle is not an HTMLCanvasElement")
    canvas = canvasElement
    configuration?.let {
        configureCanvas(canvasElement, it.width, it.height)
    }
}

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    private var bufferHandle: dynamic = null
    private var queueHandle: dynamic = null

    internal fun attach(handle: dynamic, queue: dynamic) {
        bufferHandle = handle
        queueHandle = queue
    }

    internal fun handle(): dynamic {
        require(bufferHandle != null) { "Buffer handle not initialised" }
        return bufferHandle
    }

    private fun queue(): dynamic {
        require(queueHandle != null) { "Queue handle not initialised" }
        return queueHandle
    }

    actual fun write(data: ByteArray, offset: Int) {
        require(offset >= 0) { "Offset must be >= 0" }
        require(offset + data.size <= descriptor.size) {
            "Write range exceeds buffer size (offset=$offset, data=${data.size}, capacity=${descriptor.size})"
        }

        val uint8 = createUint8Array(data.size)
        val dynArray: dynamic = uint8
        data.forEachIndexed { index, byte ->
            dynArray[index] = byte.toInt() and 0xFF
        }
        queue().writeBuffer(handle(), offset, uint8)
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val byteOffset = offset * Float.SIZE_BYTES
        val byteSize = data.size * Float.SIZE_BYTES
        require(offset >= 0) { "Offset must be >= 0" }
        require(byteOffset + byteSize <= descriptor.size) {
            "Write range exceeds buffer size"
        }
        val floatArray = createFloat32Array(data.size)
        val dynFloats: dynamic = floatArray
        data.forEachIndexed { index, value ->
            dynFloats[index] = value
        }
        queue().writeBuffer(handle(), byteOffset, floatArray)
    }

    actual fun destroy() {
        val handle = bufferHandle
        if (handle != null) {
            callIfFunction(handle, "destroy")
        }
    }
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    private var textureHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        textureHandle = handle
    }

    private fun handle(): dynamic {
        val handle = textureHandle
        require(handle != null) { "Texture handle not initialised" }
        return handle
    }

    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.label = it }
        descriptor.format?.let { jsDescriptor.format = it.toWebGpuFormat() }
        jsDescriptor.baseMipLevel = descriptor.baseMipLevel
        descriptor.mipLevelCount?.let { jsDescriptor.mipLevelCount = it }
        jsDescriptor.baseArrayLayer = descriptor.baseArrayLayer
        descriptor.arrayLayerCount?.let { jsDescriptor.arrayLayerCount = it }
        val viewHandle = handle().createView(jsDescriptor)
        return GpuTextureView(this, descriptor).apply { attach(viewHandle) }
    }

    actual fun destroy() {
        val texHandle = textureHandle
        if (texHandle != null) {
            callIfFunction(texHandle, "destroy")
        }
    }
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
) {
    private var viewHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        viewHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = viewHandle
        require(handle != null) { "Texture view handle not initialised" }
        return handle
    }
}

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
) {
    private var samplerHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        samplerHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = samplerHandle
        require(handle != null) { "Sampler handle not initialised" }
        return handle
    }
}

actual class GpuCommandEncoder actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    private var encoderHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        encoderHandle = handle
    }

    private fun handle(): dynamic {
        val handle = encoderHandle
        require(handle != null) { "Command encoder handle not initialised" }
        return handle
    }

    actual fun finish(label: String?): GpuCommandBuffer {
        val commandBuffer = label?.let {
            val finishDescriptor = emptyObject()
            finishDescriptor.label = it
            handle().finish(finishDescriptor)
        } ?: handle().finish()
        return GpuCommandBuffer(device, label ?: descriptor?.label).apply { attach(commandBuffer) }
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder {
        val passDescriptor = emptyObject()
        descriptor.label?.let { passDescriptor.label = it }
        val attachments = JsArray()
        descriptor.colorAttachments.forEach { attachment ->
            val jsAttachment = emptyObject()
            jsAttachment.view = attachment.view.handle()
            attachment.resolveTarget?.let { jsAttachment.resolveTarget = it.handle() }
            jsAttachment.loadOp = attachment.loadOp.toWebGpu()
            jsAttachment.storeOp = attachment.storeOp.toWebGpu()
            val color = attachment.clearColor
            val clearValue = emptyObject()
            clearValue.r = color.componentOrDefault(0, 0.0)
            clearValue.g = color.componentOrDefault(1, 0.0)
            clearValue.b = color.componentOrDefault(2, 0.0)
            clearValue.a = color.componentOrDefault(3, 1.0)
            jsAttachment.clearValue = clearValue
            attachments.push(jsAttachment)
        }
        passDescriptor.colorAttachments = attachments

        descriptor.depthStencilAttachment?.let { depth ->
            val depthAttachment = emptyObject()
            depthAttachment.view = depth.view.handle()
            depthAttachment.depthLoadOp = depth.depthLoadOp.toWebGpu()
            depthAttachment.depthStoreOp = depth.depthStoreOp.toWebGpu()
            depthAttachment.depthClearValue = depth.depthClearValue
            depthAttachment.depthReadOnly = depth.depthReadOnly

            depth.stencilLoadOp?.let { depthAttachment.stencilLoadOp = it.toWebGpu() }
            depth.stencilStoreOp?.let { depthAttachment.stencilStoreOp = it.toWebGpu() }
            depthAttachment.stencilClearValue = depth.stencilClearValue
            depthAttachment.stencilReadOnly = depth.stencilReadOnly

            passDescriptor.depthStencilAttachment = depthAttachment
        }

        val passHandle = handle().beginRenderPass(passDescriptor)
        
        // Explicitly set viewport to match the render target size
        // This is needed for some WebGPU implementations (like SwiftShader)
        val firstAttachment = descriptor.colorAttachments.firstOrNull()
        if (firstAttachment != null) {
            val textureDesc = firstAttachment.view.texture.descriptor
            val width = textureDesc.size.first.toDouble()
            val height = textureDesc.size.second.toDouble()
            // WebGPU setViewport expects: x, y, width, height, minDepth, maxDepth
            passHandle.setViewport(0.0, 0.0, width, height, 0.0, 1.0)
            passHandle.setScissorRect(0, 0, width.toInt(), height.toInt())
        }
        
        return GpuRenderPassEncoder(this, descriptor).apply { attach(passHandle) }
    }
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
) {
    private var bufferHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        bufferHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = bufferHandle
        require(handle != null) { "Command buffer handle not initialised" }
        return handle
    }
}

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    private var shaderHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        shaderHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = shaderHandle
        require(handle != null) { "Shader handle not initialised" }
        return handle
    }
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
) {
    private var layoutHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        layoutHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = layoutHandle
        require(handle != null) { "Bind group layout handle not initialised" }
        return handle
    }
}

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
) {
    private var bindGroupHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        bindGroupHandle = handle
    }

    internal fun handle(): dynamic {
        val handle = bindGroupHandle
        require(handle != null) { "Bind group handle not initialised" }
        return handle
    }
}

private fun Set<GpuShaderStage>.toWebGpuVisibilityMask(): Int {
    var mask = 0
    if (contains(GpuShaderStage.VERTEX)) mask = mask or 0x1
    if (contains(GpuShaderStage.FRAGMENT)) mask = mask or 0x2
    if (contains(GpuShaderStage.COMPUTE)) mask = mask or 0x4
    return mask
}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = handle()

actual fun GpuBindGroup.unwrapHandle(): Any? = handle()

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
) {
    private var pipelineHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        pipelineHandle = handle
    }

    internal fun handle(): dynamic {
        val stored = pipelineHandle
        require(stored != null) { "Pipeline handle not initialised" }
        return stored
    }
}

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
) {
    private var pipelineHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        pipelineHandle = handle
    }

    internal fun handle(): dynamic {
        val stored = pipelineHandle
        require(stored != null) { "Pipeline handle not initialised" }
        return stored
    }
}

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    private var passHandle: dynamic = null

    internal fun attach(handle: dynamic) {
        passHandle = handle
    }

    private fun handle(): dynamic {
        val stored = passHandle
        require(stored != null) { "Render pass handle not initialised" }
        return stored
    }

    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        handle().setPipeline(pipeline.handle())
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        handle().setVertexBuffer(slot, buffer.handle())
    }

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) {
        handle().setIndexBuffer(buffer.handle(), format.toWebGpu(), offset.toDouble())
    }

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) {
        handle().setBindGroup(index, bindGroup.handle())
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        handle().draw(vertexCount, instanceCount, firstVertex, firstInstance)
    }

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) {
        handle().drawIndexed(indexCount, instanceCount, firstIndex, baseVertex, firstInstance)
    }

    actual fun end() {
        handle().end()
    }
}

private fun GpuIndexFormat.toWebGpu(): String = when (this) {
    GpuIndexFormat.UINT16 -> "uint16"
    GpuIndexFormat.UINT32 -> "uint32"
}

private fun GpuTextureFormat.toWebGpuFormat(): String = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> "rgba8unorm"
    GpuTextureFormat.BGRA8_UNORM -> "bgra8unorm"
    GpuTextureFormat.RGBA16_FLOAT -> "rgba16float"
    GpuTextureFormat.DEPTH24_PLUS -> "depth24plus"
}

private fun GpuCompareFunction.toWebGpu(): String = when (this) {
    GpuCompareFunction.ALWAYS -> "always"
    GpuCompareFunction.LESS -> "less"
    GpuCompareFunction.LESS_EQUAL -> "less-equal"
}

private fun GpuBlendMode.toWebGpu(): dynamic? = when (this) {
    GpuBlendMode.DISABLED -> null
    GpuBlendMode.ALPHA -> {
        val blend = emptyObject()
        val color = emptyObject()
        color.srcFactor = "src-alpha"
        color.dstFactor = "one-minus-src-alpha"
        color.operation = "add"
        val alpha = emptyObject()
        alpha.srcFactor = "one"
        alpha.dstFactor = "one-minus-src-alpha"
        alpha.operation = "add"
        blend.color = color
        blend.alpha = alpha
        blend
    }

    GpuBlendMode.ADDITIVE -> {
        val blend = emptyObject()
        val color = emptyObject()
        color.srcFactor = "one"
        color.dstFactor = "one"
        color.operation = "add"
        val alpha = emptyObject()
        alpha.srcFactor = "one"
        alpha.dstFactor = "one"
        alpha.operation = "add"
        blend.color = color
        blend.alpha = alpha
        blend
    }
}

private fun GpuLoadOp.toWebGpu(): String = when (this) {
    GpuLoadOp.LOAD -> "load"
    GpuLoadOp.CLEAR -> "clear"
}

private fun GpuStoreOp.toWebGpu(): String = when (this) {
    GpuStoreOp.STORE -> "store"
    GpuStoreOp.DISCARD -> "discard"
}

private fun GpuVertexStepMode.toWebGpu(): String = when (this) {
    GpuVertexStepMode.VERTEX -> "vertex"
    GpuVertexStepMode.INSTANCE -> "instance"
}

private fun GpuVertexFormat.toWebGpuFormat(): String = when (this) {
    GpuVertexFormat.FLOAT32 -> "float32"
    GpuVertexFormat.FLOAT32x2 -> "float32x2"
    GpuVertexFormat.FLOAT32x3 -> "float32x3"
    GpuVertexFormat.FLOAT32x4 -> "float32x4"
    GpuVertexFormat.UINT32 -> "uint32"
    GpuVertexFormat.UINT32x2 -> "uint32x2"
    GpuVertexFormat.UINT32x3 -> "uint32x3"
    GpuVertexFormat.UINT32x4 -> "uint32x4"
    GpuVertexFormat.SINT32 -> "sint32"
    GpuVertexFormat.SINT32x2 -> "sint32x2"
    GpuVertexFormat.SINT32x3 -> "sint32x3"
    GpuVertexFormat.SINT32x4 -> "sint32x4"
}

private fun GpuPrimitiveTopology.toWebGpu(): String = when (this) {
    GpuPrimitiveTopology.POINT_LIST -> "point-list"
    GpuPrimitiveTopology.LINE_LIST -> "line-list"
    GpuPrimitiveTopology.LINE_STRIP -> "line-strip"
    GpuPrimitiveTopology.TRIANGLE_LIST -> "triangle-list"
    GpuPrimitiveTopology.TRIANGLE_STRIP -> "triangle-strip"
}

private fun GpuCullMode.toWebGpu(): String = when (this) {
    GpuCullMode.NONE -> "none"
    GpuCullMode.FRONT -> "front"
    GpuCullMode.BACK -> "back"
}

private fun GpuFrontFace.toWebGpu(): String = when (this) {
    GpuFrontFace.CCW -> "ccw"
    GpuFrontFace.CW -> "cw"
}
