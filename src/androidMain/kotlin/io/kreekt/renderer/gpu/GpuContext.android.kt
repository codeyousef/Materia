package io.kreekt.renderer.gpu

private fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("Android GPU stub does not implement $feature.")

actual class GpuDevice {
    private var stateBackend: GpuBackend = GpuBackend.VULKAN
    private var stateInfo: GpuDeviceInfo = GpuDeviceInfo("Android Stub GPU")
    private var stateLimits: GpuLimits = GpuLimits.Empty

    actual val backend: GpuBackend
        get() = stateBackend

    actual val info: GpuDeviceInfo
        get() = stateInfo

    actual val limits: GpuLimits
        get() = stateLimits

    internal fun initialize(backend: GpuBackend, info: GpuDeviceInfo, limits: GpuLimits) {
        stateBackend = backend
        stateInfo = info
        stateLimits = limits
    }

    actual fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray?): GpuBuffer {
        return GpuBuffer().apply {
            initialize(descriptor.size, descriptor.usage)
            data?.let { writeBytes(it, 0) }
        }
    }

    actual fun createCommandEncoder(label: String?): GpuCommandEncoder {
        return GpuCommandEncoder().apply { this.label = label }
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        return GpuTexture().apply { initialize(descriptor) }
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        return GpuSampler().apply { this.descriptor = descriptor }
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        return GpuBindGroupLayout().apply { this.descriptor = descriptor }
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        return GpuBindGroup().apply { this.descriptor = descriptor }
    }

    actual fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout {
        return GpuPipelineLayout().apply { this.descriptor = descriptor }
    }
}

actual class GpuQueue {
    actual val backend: GpuBackend = GpuBackend.VULKAN

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        if (commandBuffers.isEmpty()) return
        unsupported("command buffer submission")
    }
}

actual class GpuBuffer {
    private var storedSize: Long = 0
    private var storedUsage: Int = 0
    private var contents: ByteArray? = null

    actual val size: Long
        get() = storedSize

    actual val usage: Int
        get() = storedUsage

    internal fun initialize(size: Long, usage: Int) {
        storedSize = size
        storedUsage = usage
    }

    fun writeBytes(data: ByteArray, offset: Int) {
        val target = ByteArray(offset + data.size)
        contents?.copyInto(target)
        data.copyInto(target, destinationOffset = offset)
        contents = target
    }

    fun writeFloats(data: FloatArray, offset: Int) {
        val byteBuffer = ByteArray(data.size * 4)
        data.forEachIndexed { index, value ->
            val bits = value.toRawBits()
            byteBuffer[index * 4 + 0] = (bits ushr 24).toByte()
            byteBuffer[index * 4 + 1] = (bits ushr 16).toByte()
            byteBuffer[index * 4 + 2] = (bits ushr 8).toByte()
            byteBuffer[index * 4 + 3] = bits.toByte()
        }
        writeBytes(byteBuffer, offset)
    }
}

actual class GpuCommandEncoder {
    internal var label: String? = null
    private var finished = false

    actual fun finish(): GpuCommandBuffer {
        if (finished) error("Command encoder already finished")
        finished = true
        return GpuCommandBuffer().apply { this.label = label }
    }
}

actual class GpuCommandBuffer {
    internal var label: String? = null
}

actual class GpuTexture {
    private var descriptor: GpuTextureDescriptor? = null
    private var destroyed = false

    internal fun initialize(descriptor: GpuTextureDescriptor) {
        this.descriptor = descriptor
    }

    private val currentDescriptor: GpuTextureDescriptor
        get() = descriptor ?: error("Texture not initialised")

    actual val width: Int
        get() = currentDescriptor.width

    actual val height: Int
        get() = currentDescriptor.height

    actual val depth: Int
        get() = currentDescriptor.depthOrArrayLayers

    actual val format: String
        get() = currentDescriptor.format

    actual val mipLevelCount: Int
        get() = currentDescriptor.mipLevelCount

    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        check(!destroyed) { "Texture has been destroyed" }
        return GpuTextureView().apply { this.descriptor = descriptor ?: GpuTextureViewDescriptor() }
    }

    actual fun destroy() {
        destroyed = true
    }
}

actual class GpuSampler {
    internal var descriptor: GpuSamplerDescriptor = GpuSamplerDescriptor()
}

actual class GpuBindGroupLayout {
    internal var descriptor: GpuBindGroupLayoutDescriptor = GpuBindGroupLayoutDescriptor(emptyList())
}

actual class GpuBindGroup {
    internal var descriptor: GpuBindGroupDescriptor = GpuBindGroupDescriptor(
        layout = GpuBindGroupLayout(),
        entries = emptyList()
    )
}

actual class GpuPipelineLayout {
    internal var descriptor: GpuPipelineLayoutDescriptor = GpuPipelineLayoutDescriptor(emptyList())
}

actual class GpuTextureView {
    internal var descriptor: GpuTextureViewDescriptor = GpuTextureViewDescriptor()
}

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext {
        val info = GpuDeviceInfo(
            name = "Android Stub GPU",
            vendor = "Unknown",
            driverVersion = "0.0.1",
            architecture = "ARM"
        )
        val device = GpuDevice().apply { initialize(config.preferredBackend, info, GpuLimits.Empty) }
        val queue = GpuQueue()
        return GpuContext(device, queue)
    }
}

actual fun GpuDevice.unwrapHandle(): Any? = null

actual fun GpuDevice.unwrapPhysicalHandle(): Any? = null

actual fun GpuQueue.unwrapHandle(): Any? = null

actual fun GpuBuffer.unwrapHandle(): Any? = null

actual fun GpuCommandEncoder.unwrapHandle(): Any? = null

actual fun GpuCommandBuffer.unwrapHandle(): Any? = null

actual fun GpuTexture.unwrapHandle(): Any? = null

actual fun GpuSampler.unwrapHandle(): Any? = null

actual fun GpuDevice.unwrapInstance(): Any? = null

actual fun GpuDevice.unwrapDescriptorPool(): Any? = null

actual fun GpuDevice.queueFamilyIndex(): Int = 0

actual fun GpuDevice.commandPoolHandle(): Long = 0L

actual fun GpuQueue.queueFamilyIndex(): Int = 0

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = null

actual fun GpuBindGroup.unwrapHandle(): Any? = null

actual fun GpuPipelineLayout.unwrapHandle(): Any? = null

actual fun GpuTextureView.unwrapHandle(): Any? = null
