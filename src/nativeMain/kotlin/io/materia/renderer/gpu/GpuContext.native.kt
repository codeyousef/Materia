package io.materia.renderer.gpu

import io.materia.renderer.currentApplePlatformInfo

private val APPLE_GPU_LIMITS = GpuLimits(
    maxTextureDimension1D = 16384,
    maxTextureDimension2D = 16384,
    maxTextureDimension3D = 2048,
    maxTextureArrayLayers = 2048,
    maxBindGroups = 8,
    maxUniformBuffersPerStage = 12,
    maxStorageBuffersPerStage = 8,
    maxBufferSize = 256L * 1024L * 1024L
)

actual class GpuDevice internal constructor(
    actual val backend: GpuBackend,
    actual val info: GpuDeviceInfo,
    actual val limits: GpuLimits,
    internal val handle: Any? = null,
    internal val queueFamilyIndex: Int = 0
) {
    actual fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray?): GpuBuffer {
        return GpuBuffer(
            size = descriptor.size,
            usage = descriptor.usage,
            data = data?.copyOf()
        )
    }

    actual fun createCommandEncoder(label: String?): GpuCommandEncoder {
        return GpuCommandEncoder(label)
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        return GpuTexture(
            width = descriptor.width,
            height = descriptor.height,
            depth = descriptor.depthOrArrayLayers,
            format = descriptor.format,
            mipLevelCount = descriptor.mipLevelCount
        )
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        return GpuSampler(descriptor)
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        return GpuBindGroupLayout(descriptor)
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        return GpuBindGroup(descriptor)
    }

    actual fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout {
        return GpuPipelineLayout(descriptor)
    }
}

actual class GpuQueue internal constructor(
    actual val backend: GpuBackend,
    internal val handle: Any? = null
) {
    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        // Apple native support currently exposes a lifecycle-safe placeholder queue.
    }
}

actual class GpuBuffer internal constructor(
    actual val size: Long,
    actual val usage: Int,
    internal val data: ByteArray? = null
)

actual class GpuCommandEncoder internal constructor(
    internal val label: String? = null
) {
    actual fun finish(): GpuCommandBuffer = GpuCommandBuffer(label)
}

actual class GpuCommandBuffer internal constructor(
    internal val label: String? = null
)

actual class GpuTexture internal constructor(
    actual val width: Int,
    actual val height: Int,
    actual val depth: Int,
    actual val format: String,
    actual val mipLevelCount: Int
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        return GpuTextureView(this, descriptor)
    }

    actual fun destroy() {
        // Placeholder texture lifecycle for Apple native targets.
    }
}

actual class GpuSampler internal constructor(
    internal val descriptor: GpuSamplerDescriptor
)

actual class GpuBindGroupLayout internal constructor(
    internal val descriptor: GpuBindGroupLayoutDescriptor
)

actual class GpuBindGroup internal constructor(
    internal val descriptor: GpuBindGroupDescriptor
)

actual class GpuPipelineLayout internal constructor(
    internal val descriptor: GpuPipelineLayoutDescriptor
)

actual class GpuTextureView internal constructor(
    internal val texture: GpuTexture,
    internal val descriptor: GpuTextureViewDescriptor?
)

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext {
        val platformInfo = currentApplePlatformInfo()
        val backend = if (config.preferredBackend == GpuBackend.WEBGPU) {
            GpuBackend.VULKAN
        } else {
            config.preferredBackend
        }
        val device = GpuDevice(
            backend = backend,
            info = GpuDeviceInfo(
                name = platformInfo.deviceId,
                vendor = "Apple",
                driverVersion = platformInfo.driverVersion,
                architecture = platformInfo.platformName
            ),
            limits = APPLE_GPU_LIMITS,
            handle = platformInfo.deviceId
        )
        val queue = GpuQueue(
            backend = backend,
            handle = "apple-moltenvk-queue"
        )
        return GpuContext(device = device, queue = queue)
    }
}

actual fun GpuDevice.unwrapHandle(): Any? = handle

actual fun GpuDevice.unwrapPhysicalHandle(): Any? = null

actual fun GpuQueue.unwrapHandle(): Any? = handle

actual fun GpuBuffer.unwrapHandle(): Any? = data ?: this

actual fun GpuCommandEncoder.unwrapHandle(): Any? = label ?: this

actual fun GpuCommandBuffer.unwrapHandle(): Any? = label ?: this

actual fun GpuTexture.unwrapHandle(): Any? = this

actual fun GpuSampler.unwrapHandle(): Any? = descriptor

actual fun GpuDevice.unwrapInstance(): Any? = handle

actual fun GpuDevice.unwrapDescriptorPool(): Any? = null

actual fun GpuDevice.queueFamilyIndex(): Int = queueFamilyIndex

actual fun GpuDevice.commandPoolHandle(): Long = 0L

actual fun GpuQueue.queueFamilyIndex(): Int = 0

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = descriptor

actual fun GpuBindGroup.unwrapHandle(): Any? = descriptor

actual fun GpuPipelineLayout.unwrapHandle(): Any? = descriptor

actual fun GpuTextureView.unwrapHandle(): Any? = descriptor ?: texture