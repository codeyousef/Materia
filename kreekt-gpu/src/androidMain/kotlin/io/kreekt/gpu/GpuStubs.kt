package io.kreekt.gpu

import io.kreekt.renderer.AndroidRenderSurface
import io.kreekt.renderer.RenderSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("Android GPU backend not yet implemented ($feature)")

actual suspend fun createGpuInstance(descriptor: GpuInstanceDescriptor): GpuInstance =
    withContext(Dispatchers.Default) {
        GpuInstance(descriptor)
    }

actual class GpuInstance actual constructor(
    actual val descriptor: GpuInstanceDescriptor
) {
    private var disposed = false

    actual suspend fun requestAdapter(options: GpuRequestAdapterOptions): GpuAdapter {
        check(!disposed) { "GpuInstance has been disposed." }
        return GpuAdapter(
            backend = options.compatibleSurface?.let { GpuBackend.VULKAN } ?: GpuBackend.VULKAN,
            options = options,
            info = GpuAdapterInfo(
                name = "Android GPU (stub)",
                vendor = android.os.Build.MANUFACTURER,
                architecture = android.os.Build.HARDWARE,
                driverVersion = "Unavailable"
            )
        )
    }

    actual fun dispose() {
        disposed = true
    }
}

actual class GpuAdapter actual constructor(
    actual val backend: GpuBackend,
    actual val options: GpuRequestAdapterOptions,
    actual val info: GpuAdapterInfo
) {
    actual suspend fun requestDevice(descriptor: GpuDeviceDescriptor): GpuDevice {
        return GpuDevice(this, descriptor)
    }
}

actual class GpuDevice actual constructor(
    actual val adapter: GpuAdapter,
    actual val descriptor: GpuDeviceDescriptor
) {
    actual val queue: GpuQueue = GpuQueue(descriptor.label)

    actual fun createBuffer(descriptor: GpuBufferDescriptor): GpuBuffer {
        unsupported("createBuffer")
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        unsupported("createTexture")
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        unsupported("createSampler")
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        unsupported("createBindGroupLayout")
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        unsupported("createBindGroup")
    }

    actual fun createCommandEncoder(descriptor: GpuCommandEncoderDescriptor?): GpuCommandEncoder {
        unsupported("createCommandEncoder")
    }

    actual fun createShaderModule(descriptor: GpuShaderModuleDescriptor): GpuShaderModule {
        unsupported("createShaderModule")
    }

    actual fun createRenderPipeline(descriptor: GpuRenderPipelineDescriptor): GpuRenderPipeline {
        unsupported("createRenderPipeline")
    }

    actual fun createComputePipeline(descriptor: GpuComputePipelineDescriptor): GpuComputePipeline {
        unsupported("createComputePipeline")
    }

    actual fun destroy() {
        // no-op stub
    }
}

actual class GpuQueue actual constructor(
    actual val label: String?
) {
    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        unsupported("queue.submit")
    }
}

actual class GpuSurface actual constructor(
    actual val label: String?
) {
    private var renderSurface: RenderSurface? = null
    private var configuration: GpuSurfaceConfiguration? = null

    actual fun configure(device: GpuDevice, configuration: GpuSurfaceConfiguration) {
        this.configuration = configuration
    }

    actual fun getPreferredFormat(adapter: GpuAdapter): GpuTextureFormat = GpuTextureFormat.BGRA8_UNORM

    actual fun acquireFrame(): GpuSurfaceFrame {
        unsupported("acquireFrame")
    }

    actual fun present(frame: GpuSurfaceFrame) {
        unsupported("present")
    }

    actual fun resize(width: Int, height: Int) {
        configuration = configuration?.copy(width = width, height = height)
    }

    internal fun attachedSurface(): RenderSurface? = renderSurface

    fun configuration(): GpuSurfaceConfiguration? = configuration

    fun attachSurface(surface: RenderSurface) {
        renderSurface = surface
    }
}

actual fun GpuSurface.attachRenderSurface(surface: RenderSurface) {
    (surface as? io.kreekt.renderer.AndroidRenderSurface)?.let {
        attachSurface(surface)
    } ?: run {
        attachSurface(surface)
    }
}

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = null

actual fun GpuBindGroup.unwrapHandle(): Any? = null

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    actual fun write(data: ByteArray, offset: Int) {
        unsupported("buffer.write")
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        unsupported("buffer.writeFloats")
    }

    actual fun destroy() {
        // no-op stub
    }
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        unsupported("texture.createView")
    }

    actual fun destroy() {
        // no-op stub
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
    actual fun finish(label: String?): GpuCommandBuffer = unsupported("commandEncoder.finish")

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder =
        unsupported("commandEncoder.beginRenderPass")
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
)

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        unsupported("renderPass.setPipeline")
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        unsupported("renderPass.setVertexBuffer")
    }

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) {
        unsupported("renderPass.setIndexBuffer")
    }

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) {
        unsupported("renderPass.setBindGroup")
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        unsupported("renderPass.draw")
    }

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) {
        unsupported("renderPass.drawIndexed")
    }

    actual fun end() {
        // no-op stub
    }
}

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
