package io.kreekt.gpu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLACEHOLDER_VENDOR = "KreeKt"
private const val PLACEHOLDER_DRIVER = "mvp-placeholder"

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
            val backend = descriptor.preferredBackends.firstOrNull() ?: GpuBackend.WEBGPU
            val info = GpuAdapterInfo(
                name = "Placeholder $backend Adapter",
                vendor = PLACEHOLDER_VENDOR,
                architecture = "virtual",
                driverVersion = PLACEHOLDER_DRIVER
            )
            GpuAdapter(backend, options, info)
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
    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice =
        withContext(Dispatchers.Default) {
            GpuDevice(this@GpuAdapter, descriptor)
        }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    actual val queue: GpuQueue = GpuQueue(label = descriptor.label)

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer =
        GpuBuffer(this, descriptor)

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
        // Nothing to release in the placeholder implementation.
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        // Placeholder no-op submission.
        commandBuffers.forEach { _ -> }
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
            mipLevelCount = 1,
            sampleCount = 1,
            dimension = GpuTextureDimension.D2,
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
    private val storage = ByteArray(descriptor.size.toInt())

    actual fun write(data: ByteArray, offset: Int) {
        require(offset >= 0) { "Offset must be >= 0" }
        require(offset + data.size <= storage.size) {
            "Write range exceeds buffer size (offset=$offset, data=${data.size}, capacity=${storage.size})"
        }
        data.copyInto(storage, destinationOffset = offset, endIndex = data.size)
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        var byteOffset = offset * Float.SIZE_BYTES
        val requiredBytes = data.size * Float.SIZE_BYTES
        require(byteOffset >= 0) { "Offset must be >= 0" }
        require(byteOffset + requiredBytes <= storage.size) {
            "Write range exceeds buffer size (offset=$byteOffset, dataBytes=$requiredBytes, capacity=${storage.size})"
        }
        data.forEach { value ->
            val bits = value.toRawBits()
            storage[byteOffset++] = (bits and 0xFF).toByte()
            storage[byteOffset++] = ((bits shr 8) and 0xFF).toByte()
            storage[byteOffset++] = ((bits shr 16) and 0xFF).toByte()
            storage[byteOffset++] = ((bits shr 24) and 0xFF).toByte()
        }
    }

    actual fun destroy() {
        // Placeholder no-op
        storage.fill(0)
    }
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView =
        GpuTextureView(this, descriptor)

    actual fun destroy() {
        // Placeholder no-op
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

actual class GpuCommandEncoder actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    actual fun finish(label: String?): GpuCommandBuffer =
        GpuCommandBuffer(device, label ?: descriptor?.label)

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder =
        GpuRenderPassEncoder(this, descriptor)
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
)

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
)

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
    private val recordedCalls = mutableListOf<String>()

    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        recordedCalls += "setPipeline:${pipeline.descriptor.label ?: "pipeline"}"
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        recordedCalls += "setVertexBuffer:$slot:${buffer.descriptor.label ?: "buffer"}"
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        recordedCalls += "draw:$vertexCount:$instanceCount:$firstVertex:$firstInstance"
    }

    actual fun end() {
        recordedCalls.clear()
    }
}
