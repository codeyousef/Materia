package io.kreekt.gpu

import io.kreekt.renderer.RenderSurface
import kotlin.js.Promise
import kotlin.js.jsTypeOf
import kotlinx.coroutines.await
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLCanvasElement

private fun navigatorGpu(): dynamic = js("typeof navigator !== 'undefined' && navigator.gpu ? navigator.gpu : null")
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
    val canvasId = label.orDefault("kreekt-canvas")
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
    val fn = target.asDynamic()[functionName]
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
            jsOptions.asDynamic().powerPreference = when (options.powerPreference) {
                GpuPowerPreference.LOW_POWER -> "low-power"
                GpuPowerPreference.HIGH_PERFORMANCE -> "high-performance"
            }
            if (options.forceFallbackAdapter) {
                jsOptions.asDynamic().forceFallbackAdapter = true
            }
            val adapterHandle = (gpu.requestAdapter(jsOptions) as Promise<dynamic>).awaitJs()
                ?: error("Failed to acquire WebGPU adapter")

            val info = adapterHandle.asDynamic().info
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
        descriptor.label?.let { jsDescriptor.asDynamic().label = it }
        if (descriptor.requiredFeatures.isNotEmpty()) {
            jsDescriptor.asDynamic().requiredFeatures = descriptor.requiredFeatures.toTypedArray()
        }
        if (descriptor.requiredLimits.isNotEmpty()) {
            val limits = emptyObject()
            val limitsDyn = limits.asDynamic()
            descriptor.requiredLimits.forEach { (key, value) ->
                limitsDyn[key] = value
            }
            jsDescriptor.asDynamic().requiredLimits = limits
        }

        val deviceHandle = (adapterHandle().requestDevice(jsDescriptor) as Promise<dynamic>).awaitJs()
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
        descriptor.label?.let { jsDescriptor.asDynamic().label = it }
        jsDescriptor.asDynamic().size = descriptor.size.toInt()
        jsDescriptor.asDynamic().usage = descriptor.usage
        jsDescriptor.asDynamic().mappedAtCreation = descriptor.mappedAtCreation
        val buffer = handle().createBuffer(jsDescriptor)
        return GpuBuffer(this, descriptor).apply { attach(buffer, queue()) }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.asDynamic().label = it }
        val size = emptyObject()
        size.asDynamic().width = descriptor.size.first
        size.asDynamic().height = descriptor.size.second
        size.asDynamic().depthOrArrayLayers = descriptor.size.third
        jsDescriptor.asDynamic().size = size
        jsDescriptor.asDynamic().mipLevelCount = descriptor.mipLevelCount
        jsDescriptor.asDynamic().sampleCount = descriptor.sampleCount
        jsDescriptor.asDynamic().dimension = when (descriptor.dimension) {
            GpuTextureDimension.D1 -> "1d"
            GpuTextureDimension.D2 -> "2d"
            GpuTextureDimension.D3 -> "3d"
        }
        jsDescriptor.asDynamic().format = descriptor.format.toWebGpuFormat()
        jsDescriptor.asDynamic().usage = descriptor.usage
        val texture = handle().createTexture(jsDescriptor)
        return GpuTexture(this, descriptor).apply { attach(texture) }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val jsDescriptor = emptyObject()
        descriptor.label?.let { jsDescriptor.asDynamic().label = it }
        jsDescriptor.asDynamic().addressModeU = descriptor.addressModeU.name.lowercase().replace('_', '-')
        jsDescriptor.asDynamic().addressModeV = descriptor.addressModeV.name.lowercase().replace('_', '-')
        jsDescriptor.asDynamic().addressModeW = descriptor.addressModeW.name.lowercase().replace('_', '-')
        jsDescriptor.asDynamic().magFilter = descriptor.magFilter.name.lowercase()
        jsDescriptor.asDynamic().minFilter = descriptor.minFilter.name.lowercase()
        jsDescriptor.asDynamic().mipmapFilter = descriptor.mipmapFilter.name.lowercase()
        jsDescriptor.asDynamic().lodMinClamp = descriptor.lodMinClamp
        jsDescriptor.asDynamic().lodMaxClamp = descriptor.lodMaxClamp
        val sampler = handle().createSampler(jsDescriptor)
        return GpuSampler(this, descriptor).apply { attach(sampler) }
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder {
        val encoder = descriptor?.label?.let {
            val encDesc = emptyObject()
            encDesc.asDynamic().label = it
            handle().createCommandEncoder(encDesc)
        } ?: handle().createCommandEncoder()
        return GpuCommandEncoder(this, descriptor).apply { attach(encoder) }
    }

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        val moduleDescriptor = emptyObject()
        descriptor.label?.let { moduleDescriptor.asDynamic().label = it }
        moduleDescriptor.asDynamic().code = descriptor.code
        val module = handle().createShaderModule(moduleDescriptor)
        return GpuShaderModule(this, descriptor).apply { attach(module) }
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline {
        val pipelineDescriptor = emptyObject()
        descriptor.label?.let { pipelineDescriptor.asDynamic().label = it }
        pipelineDescriptor.asDynamic().layout = "auto"
        val vertexState = emptyObject()
        vertexState.asDynamic().module = descriptor.vertexShader.handle()
        vertexState.asDynamic().entryPoint = "main"
        vertexState.asDynamic().buffers = JsArray()
        pipelineDescriptor.asDynamic().vertex = vertexState

        descriptor.fragmentShader?.let { fragment ->
            val fragmentState = emptyObject()
            fragmentState.asDynamic().module = fragment.handle()
            fragmentState.asDynamic().entryPoint = "main"
            val targets = JsArray()
            descriptor.colorFormats.forEach { format ->
                val target = emptyObject()
                target.asDynamic().format = format.toWebGpuFormat()
                targets.asDynamic().push(target)
            }
            fragmentState.asDynamic().targets = targets
            pipelineDescriptor.asDynamic().fragment = fragmentState
        }

        val primitive = emptyObject()
        primitive.asDynamic().topology = "triangle-list"
        primitive.asDynamic().cullMode = "none"
        primitive.asDynamic().frontFace = "ccw"
        pipelineDescriptor.asDynamic().primitive = primitive

        descriptor.depthStencilFormat?.let { depthFormat ->
            val depthStencil = emptyObject()
            depthStencil.asDynamic().format = depthFormat.toWebGpuFormat()
            depthStencil.asDynamic().depthWriteEnabled = true
            depthStencil.asDynamic().depthCompare = "less"
            pipelineDescriptor.asDynamic().depthStencil = depthStencil
        }

        val pipeline = handle().createRenderPipeline(pipelineDescriptor)
        return GpuRenderPipeline(this, descriptor).apply { attach(pipeline) }
    }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline {
        val pipelineDescriptor = emptyObject()
        descriptor.label?.let { pipelineDescriptor.asDynamic().label = it }
        val computeState = emptyObject()
        computeState.asDynamic().module = descriptor.shader.handle()
        computeState.asDynamic().entryPoint = "main"
        pipelineDescriptor.asDynamic().compute = computeState
        pipelineDescriptor.asDynamic().layout = "auto"
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
            jsBuffers.asDynamic().push(buffer.handle())
        }
        queue.submit(jsBuffers)
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    private var configuration: GpuSurfaceConfiguration? = null
    private var configuredDevice: GpuDevice? = null
    internal var canvas: dynamic = null
    private var context: dynamic = null
    internal var attachedSurface: RenderSurface? = null

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        configuredDevice = device
        this.configuration = configuration
        val canvas = ensureCanvas(label)
        configureCanvas(canvas, configuration.width, configuration.height)
        this.canvas = canvas

        context = context ?: canvas.getContext("webgpu")
            ?: error("Unable to acquire WebGPU canvas context")

        val jsConfig = emptyObject()
        jsConfig.asDynamic().device = device.handle()
        jsConfig.asDynamic().format = configuration.format.toWebGpuFormat()
        jsConfig.asDynamic().usage = configuration.usage
        jsConfig.asDynamic().alphaMode = "opaque"
        context.configure(jsConfig)
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
        val width = (canvas.width as? Int) ?: (canvas.width as? Double)?.toInt() ?: surfaceConfig.width
        val height = (canvas.height as? Int) ?: (canvas.height as? Double)?.toInt() ?: surfaceConfig.height
        val texture = GpuTexture(
            device = device,
            descriptor = GpuTextureDescriptor(
                label = "${label.orDefault("surface")}-frame",
                size = Triple(width, height, 1),
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
        if (canvas != null) {
            configureCanvas(canvas, width, height)
        }
        configuration = configuration?.copy(width = width, height = height)
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
        val dynArray = uint8.asDynamic()
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
        val dynFloats = floatArray.asDynamic()
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
        descriptor.label?.let { jsDescriptor.asDynamic().label = it }
        descriptor.format?.let { jsDescriptor.asDynamic().format = it.toWebGpuFormat() }
        jsDescriptor.asDynamic().baseMipLevel = descriptor.baseMipLevel
        descriptor.mipLevelCount?.let { jsDescriptor.asDynamic().mipLevelCount = it }
        jsDescriptor.asDynamic().baseArrayLayer = descriptor.baseArrayLayer
        descriptor.arrayLayerCount?.let { jsDescriptor.asDynamic().arrayLayerCount = it }
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
            finishDescriptor.asDynamic().label = it
            handle().finish(finishDescriptor)
        } ?: handle().finish()
        return GpuCommandBuffer(device, label ?: descriptor?.label).apply { attach(commandBuffer) }
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder {
        val passDescriptor = emptyObject()
        descriptor.label?.let { passDescriptor.asDynamic().label = it }
        val attachments = JsArray()
        descriptor.colorAttachments.forEach { attachment ->
            val jsAttachment = emptyObject()
            jsAttachment.asDynamic().view = attachment.view.handle()
            attachment.resolveTarget?.let { jsAttachment.asDynamic().resolveTarget = it.handle() }
            jsAttachment.asDynamic().loadOp = attachment.loadOp.toWebGpu()
            jsAttachment.asDynamic().storeOp = attachment.storeOp.toWebGpu()
            val color = attachment.clearColor
            val clearValue = emptyObject()
            clearValue.asDynamic().r = color.componentOrDefault(0, 0.0)
            clearValue.asDynamic().g = color.componentOrDefault(1, 0.0)
            clearValue.asDynamic().b = color.componentOrDefault(2, 0.0)
            clearValue.asDynamic().a = color.componentOrDefault(3, 1.0)
            jsAttachment.asDynamic().clearValue = clearValue
            attachments.asDynamic().push(jsAttachment)
        }
        passDescriptor.asDynamic().colorAttachments = attachments
        val passHandle = handle().beginRenderPass(passDescriptor)
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

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        handle().draw(vertexCount, instanceCount, firstVertex, firstInstance)
    }

    actual fun end() {
        handle().end()
    }
}

private fun GpuTextureFormat.toWebGpuFormat(): String = when (this) {
    GpuTextureFormat.RGBA8_UNORM -> "rgba8unorm"
    GpuTextureFormat.BGRA8_UNORM -> "bgra8unorm"
    GpuTextureFormat.RGBA16_FLOAT -> "rgba16float"
    GpuTextureFormat.DEPTH24_PLUS -> "depth24plus"
}

private fun GpuLoadOp.toWebGpu(): String = when (this) {
    GpuLoadOp.LOAD -> "load"
    GpuLoadOp.CLEAR -> "clear"
}

private fun GpuStoreOp.toWebGpu(): String = when (this) {
    GpuStoreOp.STORE -> "store"
    GpuStoreOp.DISCARD -> "discard"
}
