package io.kreekt.gpu

import io.kreekt.renderer.gpu.GpuBuffer as RendererGpuBuffer
import io.kreekt.renderer.gpu.GpuContext as RendererGpuContext
import io.kreekt.renderer.gpu.GpuDevice as RendererGpuDevice
import io.kreekt.renderer.gpu.GpuDeviceFactory as RendererGpuDeviceFactory
import io.kreekt.renderer.gpu.GpuQueue as RendererGpuQueue
import io.kreekt.renderer.gpu.commandPoolHandle
import io.kreekt.renderer.gpu.unwrapHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.vulkan.VK10.*
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo
import org.lwjgl.vulkan.VkCommandBufferBeginInfo
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkQueue
import org.lwjgl.vulkan.VkShaderModuleCreateInfo
import org.lwjgl.vulkan.VkSubmitInfo

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance =
    withContext(Dispatchers.Default) {
        GpuInstance(descriptor)
    }

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter =
        ensureActive {
            val config = descriptor.toRendererConfig(options)
            val rendererContext = RendererGpuDeviceFactory.requestContext(config)
            val adapterInfo = rendererContext.device.info.toAdapterInfo()
            val adapter = GpuAdapter(
                backend = rendererContext.device.backend.toGpuBackend(),
                options = options,
                info = adapterInfo
            )
            adapter.attachContext(rendererContext)
            adapter
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
    private lateinit var rendererContext: RendererGpuContext

    internal fun attachContext(context: RendererGpuContext) {
        rendererContext = context
    }

    internal fun context(): RendererGpuContext =
        if (::rendererContext.isInitialized) rendererContext
        else error("Renderer context not attached to adapter")

    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice =
        GpuDevice(this, descriptor).also { it.attachContext(context()) }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    private lateinit var rendererContext: RendererGpuContext

    internal val rendererDevice: RendererGpuDevice
        get() = rendererContext.device

    internal var commandPool: Long = VK_NULL_HANDLE

    internal fun attachContext(context: RendererGpuContext) {
        rendererContext = context
        commandPool = context.device.commandPoolHandle()
        queue.attachQueue(context.queue, this)
    }

    actual val queue: GpuQueue = GpuQueue(descriptor.label)

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        val rendererDescriptor = descriptor.toRendererDescriptor()
        val rendererBuffer = rendererDevice.createBuffer(rendererDescriptor, null)
        return GpuBuffer(this, descriptor).apply { attach(rendererBuffer) }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture =
        GpuTexture(this, descriptor)

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler =
        GpuSampler(this, descriptor)

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder =
        GpuCommandEncoder(this, descriptor)

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule =
        GpuShaderModule(this, descriptor)

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline =
        GpuRenderPipeline(this, descriptor)

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline =
        GpuComputePipeline(this, descriptor)

    actual fun destroy() {
        // Renderer context lifecycle is managed externally (RendererFactory). No-op here.
    }

    internal fun mapAndCopy(buffer: RendererGpuBuffer, data: ByteArray, byteOffset: Long) {
        val handle = rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for buffer writes")
        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(1)
            val result = vkMapMemory(handle, buffer.memory, byteOffset, data.size.toLong(), 0, pointerBuffer)
            check(result == VK_SUCCESS) { "vkMapMemory failed with error code $result" }
            val mappedPtr = pointerBuffer[0]
            val target = MemoryUtil.memByteBuffer(mappedPtr, data.size)
            target.put(data)
            target.flip()
            vkUnmapMemory(handle, buffer.memory)
        }
    }

    internal fun destroyBuffer(buffer: RendererGpuBuffer) {
        val handle = rendererDevice.unwrapHandle() as? VkDevice
            ?: return
        vkDestroyBuffer(handle, buffer.buffer, null)
        vkFreeMemory(handle, buffer.memory, null)
    }

    internal fun allocateCommandBuffer(): VkCommandBuffer {
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for command buffer allocation")
        check(commandPool != VK_NULL_HANDLE) { "Command pool not initialised for device" }

        MemoryStack.stackPush().use { stack ->
            val allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1)

            val pCommandBuffer = stack.mallocPointer(1)
            val result = vkAllocateCommandBuffers(deviceHandle, allocInfo, pCommandBuffer)
            check(result == VK_SUCCESS) { "vkAllocateCommandBuffers failed with error code $result" }

            return VkCommandBuffer(pCommandBuffer[0], deviceHandle)
        }
    }

    internal fun freeCommandBuffers(buffers: List<VkCommandBuffer>) {
        if (buffers.isEmpty()) return
        val deviceHandle = rendererDevice.unwrapHandle() as? VkDevice ?: return
        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(buffers.size)
            buffers.forEach { buffer ->
                pointerBuffer.put(buffer.address())
            }
            pointerBuffer.flip()
            vkFreeCommandBuffers(deviceHandle, commandPool, pointerBuffer)
        }
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    private lateinit var rendererQueue: RendererGpuQueue
    private var vkQueue: VkQueue? = null
    private var device: GpuDevice? = null

    internal fun attachQueue(queue: RendererGpuQueue, device: GpuDevice) {
        rendererQueue = queue
        vkQueue = queue.unwrapHandle() as? VkQueue
        this.device = device
    }

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        val queueHandle = vkQueue ?: error("Vulkan queue handle unavailable for submission")
        val deviceRef = device ?: error("GpuDevice reference unavailable for queue submission")

        MemoryStack.stackPush().use { stack ->
            val pointerBuffer = stack.mallocPointer(commandBuffers.size)
            val vkBuffers = commandBuffers.map { it.handle }
            vkBuffers.forEach { buffer ->
                pointerBuffer.put(buffer.address())
            }
            pointerBuffer.flip()

            val submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pCommandBuffers(pointerBuffer)

            val result = vkQueueSubmit(queueHandle, submitInfo, VK_NULL_HANDLE)
            check(result == VK_SUCCESS) { "vkQueueSubmit failed with error code $result" }

            vkQueueWaitIdle(queueHandle)
            deviceRef.freeCommandBuffers(vkBuffers)
        }
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    private var configuration: GpuSurfaceConfiguration? = null
    private var configuredDevice: GpuDevice? = null
    private var frameCounter: Long = 0L

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        configuredDevice = device
        this.configuration = configuration
    }

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat {
        @Suppress("UNUSED_PARAMETER")
        val unused = adapter
        return configuration?.format ?: GpuTextureFormat.BGRA8_UNORM
    }

    actual fun acquireFrame(): GpuSurfaceFrame {
        val config = configuration
            ?: error("GpuSurface not configured before acquiring frame")
        val device = configuredDevice
            ?: error("GpuSurface missing device reference")
        val descriptor = GpuTextureDescriptor(
            label = "${label ?: "surface"}-frame-${frameCounter++}",
            size = Triple(config.width, config.height, 1),
            format = config.format,
            usage = config.usage
        )
        val texture = device.createTexture(descriptor)
        val view = texture.createView()
        return GpuSurfaceFrame(texture, view)
    }

    actual fun present(frame: GpuSurfaceFrame) {
        frame.texture.destroy()
    }

    actual fun resize(width: Int, height: Int) {
        configuration = configuration?.copy(width = width, height = height)
    }
}

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    private lateinit var rendererBuffer: RendererGpuBuffer

    actual fun write(data: ByteArray, offset: Int) {
        check(::rendererBuffer.isInitialized) { "Renderer buffer not attached" }
        require(offset >= 0) { "Offset must be >= 0" }
        val byteOffset = offset.toLong()
        device.mapAndCopy(rendererBuffer, data, byteOffset)
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val byteArray = ByteArray(data.size * Float.SIZE_BYTES)
        var position = 0
        data.forEach { value ->
            val bits = value.toRawBits()
            byteArray[position++] = (bits and 0xFF).toByte()
            byteArray[position++] = ((bits shr 8) and 0xFF).toByte()
            byteArray[position++] = ((bits shr 16) and 0xFF).toByte()
            byteArray[position++] = ((bits shr 24) and 0xFF).toByte()
        }
        write(byteArray, offset * Float.SIZE_BYTES)
    }

    actual fun destroy() {
        if (::rendererBuffer.isInitialized) {
            device.destroyBuffer(rendererBuffer)
        }
    }

    internal fun attach(buffer: RendererGpuBuffer) {
        rendererBuffer = buffer
    }
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView =
        GpuTextureView(this, descriptor)

    actual fun destroy() {
        // Textures not yet backed by renderer resources on JVM.
    }
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
)

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
)

actual class GpuCommandEncoder actual internal constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    private val commandBuffer: VkCommandBuffer = device.allocateCommandBuffer()
    private var finished = false

    init {
        MemoryStack.stackPush().use { stack ->
            val beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)

            val result = vkBeginCommandBuffer(commandBuffer, beginInfo)
            check(result == VK_SUCCESS) { "vkBeginCommandBuffer failed with error code $result" }
        }
    }

    actual fun finish(label: String?): GpuCommandBuffer {
        check(!finished) { "Command encoder already finished" }
        val result = vkEndCommandBuffer(commandBuffer)
        check(result == VK_SUCCESS) { "vkEndCommandBuffer failed with error code $result" }
        finished = true
        return GpuCommandBuffer(device, label ?: descriptor?.label).also {
            it.handle = commandBuffer
        }
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder =
        GpuRenderPassEncoder(this, descriptor)
}

actual class GpuCommandBuffer actual internal constructor(
    actual val device: GpuDevice,
    actual val label: String?
) {
    internal lateinit var handle: VkCommandBuffer
}

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    internal val handle: Long = createShaderModule(device, descriptor)

    private fun createShaderModule(device: GpuDevice, descriptor: GpuShaderModuleDescriptor): Long {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice
            ?: error("Vulkan device handle unavailable for shader module creation")

        val spirvBytes = loadSpirvBytes(descriptor)
        require(spirvBytes.size % 4 == 0) {
            "SPIR-V module size must be a multiple of 4 bytes (label=${descriptor.label})"
        }

        MemoryStack.stackPush().use { stack ->
            val codeBuffer = stack.malloc(spirvBytes.size)
            codeBuffer.put(spirvBytes)
            codeBuffer.flip()

            val createInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                .pCode(codeBuffer)

            val pShaderModule = stack.mallocLong(1)
            val result = vkCreateShaderModule(vkDevice, createInfo, null, pShaderModule)
            if (result != VK_SUCCESS) {
                throw IllegalStateException("vkCreateShaderModule failed with error code $result (label=${descriptor.label})")
            }

            return pShaderModule[0]
        }
    }

    private fun loadSpirvBytes(descriptor: GpuShaderModuleDescriptor): ByteArray {
        val label = descriptor.label
            ?: error("Shader module descriptor requires a label to locate compiled SPIR-V")

        val resourceName = "/shaders/${label}.main.spv"
        val stream = GpuShaderModule::class.java.getResourceAsStream(resourceName)
            ?: error("Compiled SPIR-V resource not found for shader label '$label' at '$resourceName'. Did you run ./gradlew compileShaders?")

        return stream.use { it.readBytes() }
    }

    internal fun destroy() {
        val vkDevice = device.rendererDevice.unwrapHandle() as? VkDevice ?: return
        vkDestroyShaderModule(vkDevice, handle, null)
    }
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
)

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
)

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
)

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
)

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    actual fun setPipeline(pipeline: GpuRenderPipeline) {}

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {}

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {}

    actual fun end() {}
}
