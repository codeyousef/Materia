package io.kreekt.renderer.gpu

import io.kreekt.renderer.webgpu.GPUAdapter
import io.kreekt.renderer.webgpu.GPUBindGroup
import io.kreekt.renderer.webgpu.GPUBindGroupDescriptor
import io.kreekt.renderer.webgpu.GPUBindGroupEntry
import io.kreekt.renderer.webgpu.GPUBindGroupLayout
import io.kreekt.renderer.webgpu.GPUBindGroupLayoutDescriptor
import io.kreekt.renderer.webgpu.GPUBindGroupLayoutEntry
import io.kreekt.renderer.webgpu.GPUBuffer
import io.kreekt.renderer.webgpu.GPUBufferDescriptor
import io.kreekt.renderer.webgpu.GPUCommandBuffer
import io.kreekt.renderer.webgpu.GPUCommandEncoder
import io.kreekt.renderer.webgpu.GPUCommandEncoderDescriptor
import io.kreekt.renderer.webgpu.GPUDevice
import io.kreekt.renderer.webgpu.GPUDeviceDescriptor
import io.kreekt.renderer.webgpu.GPUPipelineLayout
import io.kreekt.renderer.webgpu.GPUPipelineLayoutDescriptor
import io.kreekt.renderer.webgpu.GPUQueue
import io.kreekt.renderer.webgpu.GPURequestAdapterOptions
import io.kreekt.renderer.webgpu.GPUSampler
import io.kreekt.renderer.webgpu.GPUSamplerDescriptor
import io.kreekt.renderer.webgpu.GPUTexture
import io.kreekt.renderer.webgpu.GPUTextureDescriptor
import io.kreekt.renderer.webgpu.GPUTextureView
import io.kreekt.renderer.gpu.GpuTextureViewDescriptor
import io.kreekt.renderer.webgpu.WebGPUDetector
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import org.khronos.webgl.Uint8Array

actual class GpuDevice internal constructor(
    internal val device: GPUDevice,
    internal val adapter: GPUAdapter,
    private val deviceInfo: GpuDeviceInfo,
    private val deviceLimits: GpuLimits,
    internal val physicalDeviceHandle: Any? = null
) {
    actual val backend: GpuBackend = GpuBackend.WEBGPU
    actual val info: GpuDeviceInfo get() = deviceInfo
    actual val limits: GpuLimits get() = deviceLimits

    actual fun createBuffer(descriptor: GpuBufferDescriptor, data: ByteArray?): GpuBuffer {
        val jsDescriptor = js("({})").unsafeCast<GPUBufferDescriptor>()
        require(descriptor.size <= Int.MAX_VALUE) {
            "WebGPU buffer size exceeds supported range: ${descriptor.size}"
        }
        jsDescriptor.size = descriptor.size.toInt()
        jsDescriptor.usage = descriptor.usage
        jsDescriptor.mappedAtCreation = descriptor.mappedAtCreation
        descriptor.label?.let { jsDescriptor.label = it }

        val buffer = device.createBuffer(jsDescriptor)

        if (data != null && data.isNotEmpty()) {
            val uint8 = Uint8Array(data.size)
            val dynArray = uint8.asDynamic()
            for (i in data.indices) {
                dynArray[i] = data[i].toInt() and 0xFF
            }
            device.queue.writeBuffer(buffer, 0, uint8)
        }

        return GpuBuffer(buffer, descriptor.size, descriptor.usage)
    }

    actual fun createCommandEncoder(label: String?): GpuCommandEncoder {
        val descriptor = if (label != null) {
            val obj = js("({})")
            obj.label = label
            obj
        } else null
        val encoder = if (descriptor != null) {
            device.createCommandEncoder(descriptor.unsafeCast<GPUCommandEncoderDescriptor>())
        } else {
            device.createCommandEncoder()
        }
        return GpuCommandEncoder(encoder)
    }

    actual fun createTexture(descriptor: GpuTextureDescriptor): GpuTexture {
        val jsDescriptor = js("({})").unsafeCast<GPUTextureDescriptor>()
        jsDescriptor.size = js("({})")
        jsDescriptor.size.width = descriptor.width
        jsDescriptor.size.height = descriptor.height
        jsDescriptor.size.depthOrArrayLayers = descriptor.depthOrArrayLayers
        jsDescriptor.mipLevelCount = descriptor.mipLevelCount
        jsDescriptor.sampleCount = descriptor.sampleCount
        jsDescriptor.dimension = when (descriptor.dimension) {
            GpuTextureDimension.D1 -> "1d"
            GpuTextureDimension.D2 -> "2d"
            GpuTextureDimension.D3 -> "3d"
        }
        jsDescriptor.format = descriptor.format
        jsDescriptor.usage = descriptor.usage
        descriptor.label?.let { jsDescriptor.label = it }

        val texture = device.createTexture(jsDescriptor)
        return GpuTexture(
            texture = texture,
            width = descriptor.width,
            height = descriptor.height,
            depth = descriptor.depthOrArrayLayers,
            format = descriptor.format,
            mipLevelCount = descriptor.mipLevelCount
        )
    }

    actual fun createSampler(descriptor: GpuSamplerDescriptor): GpuSampler {
        val jsDescriptor = js("({})").unsafeCast<GPUSamplerDescriptor>()
        jsDescriptor.magFilter = when (descriptor.magFilter) {
            GpuSamplerFilter.NEAREST -> "nearest"
            GpuSamplerFilter.LINEAR -> "linear"
        }
        jsDescriptor.minFilter = when (descriptor.minFilter) {
            GpuSamplerFilter.NEAREST -> "nearest"
            GpuSamplerFilter.LINEAR -> "linear"
        }
        jsDescriptor.mipmapFilter = when (descriptor.mipmapFilter) {
            GpuSamplerFilter.NEAREST -> "nearest"
            GpuSamplerFilter.LINEAR -> "linear"
        }
        jsDescriptor.lodMinClamp = descriptor.lodMinClamp
        jsDescriptor.lodMaxClamp = descriptor.lodMaxClamp
        descriptor.label?.let { jsDescriptor.label = it }

        val sampler = device.createSampler(jsDescriptor)
        return GpuSampler(sampler)
    }

    actual fun createBindGroupLayout(descriptor: GpuBindGroupLayoutDescriptor): GpuBindGroupLayout {
        val jsDescriptor = js("({})").unsafeCast<GPUBindGroupLayoutDescriptor>()
        descriptor.label?.let { jsDescriptor.label = it }
        val entries = js("[]").unsafeCast<Array<GPUBindGroupLayoutEntry>>()
        descriptor.entries.forEach { entry ->
            val jsEntry = js("({})").unsafeCast<GPUBindGroupLayoutEntry>()
            jsEntry.binding = entry.binding
            jsEntry.visibility = entry.visibility
            entry.buffer?.let { bufferLayout ->
                val buffer = js("({})")
                buffer.type = when (bufferLayout.type) {
                    GpuBufferBindingType.UNIFORM -> "uniform"
                    GpuBufferBindingType.STORAGE -> "storage"
                    GpuBufferBindingType.READ_ONLY_STORAGE -> "read-only-storage"
                }
                buffer.hasDynamicOffset = bufferLayout.hasDynamicOffset
                buffer.minBindingSize = bufferLayout.minBindingSize
                jsEntry.buffer = buffer
            }
            entry.sampler?.let { samplerLayout ->
                val sampler = js("({})")
                sampler.type = when (samplerLayout.type) {
                    GpuSamplerBindingType.FILTERING -> "filtering"
                    GpuSamplerBindingType.NON_FILTERING -> "non-filtering"
                    GpuSamplerBindingType.COMPARISON -> "comparison"
                }
                jsEntry.sampler = sampler
            }
            entry.texture?.let { textureLayout ->
                val texture = js("({})")
                texture.sampleType = when (textureLayout.sampleType) {
                    GpuTextureSampleType.FLOAT -> "float"
                    GpuTextureSampleType.UNFILTERABLE_FLOAT -> "unfilterable-float"
                    GpuTextureSampleType.DEPTH -> "depth"
                    GpuTextureSampleType.SINT -> "sint"
                    GpuTextureSampleType.UINT -> "uint"
                }
                texture.viewDimension = when (textureLayout.viewDimension) {
                    GpuTextureViewDimension.D1 -> "1d"
                    GpuTextureViewDimension.D2 -> "2d"
                    GpuTextureViewDimension.D2_ARRAY -> "2d-array"
                    GpuTextureViewDimension.CUBE -> "cube"
                    GpuTextureViewDimension.CUBE_ARRAY -> "cube-array"
                    GpuTextureViewDimension.D3 -> "3d"
                }
                texture.multisampled = textureLayout.multisampled
                jsEntry.texture = texture
            }
            entries.asDynamic().push(jsEntry)
        }
        jsDescriptor.entries = entries
        val layout = device.createBindGroupLayout(jsDescriptor)
        return GpuBindGroupLayout(layout)
    }

    actual fun createBindGroup(descriptor: GpuBindGroupDescriptor): GpuBindGroup {
        val jsDescriptor = js("({})").unsafeCast<GPUBindGroupDescriptor>()
        descriptor.label?.let { jsDescriptor.label = it }
        jsDescriptor.layout = descriptor.layout.unwrapHandle() as GPUBindGroupLayout
        val entries = js("[]").unsafeCast<Array<GPUBindGroupEntry>>()
        descriptor.entries.forEach { entry ->
            val jsEntry = js("({})").unsafeCast<GPUBindGroupEntry>()
            jsEntry.binding = entry.binding
            val resource = when (val binding = entry.resource) {
                is GpuBindingResource.Buffer -> {
                    val bufferResource = js("({})")
                    bufferResource.buffer = binding.buffer.unwrapHandle() as GPUBuffer
                    bufferResource.offset = binding.offset.toDouble()
                    binding.size?.let { bufferResource.size = it.toDouble() }
                    bufferResource
                }
                is GpuBindingResource.Sampler -> binding.sampler.unwrapHandle() as GPUSampler
                is GpuBindingResource.Texture -> binding.textureView.unwrapHandle() as GPUTextureView
            }
            jsEntry.resource = resource
            entries.asDynamic().push(jsEntry)
        }
        jsDescriptor.entries = entries
        val bindGroup = device.createBindGroup(jsDescriptor)
        return GpuBindGroup(bindGroup)
    }

    actual fun createPipelineLayout(descriptor: GpuPipelineLayoutDescriptor): GpuPipelineLayout {
        val jsDescriptor = js("({})").unsafeCast<GPUPipelineLayoutDescriptor>()
        descriptor.label?.let { jsDescriptor.label = it }
        val layouts = js("[]").unsafeCast<Array<GPUBindGroupLayout>>()
        descriptor.bindGroupLayouts.forEach { layout ->
            layouts.asDynamic().push(layout.unwrapHandle() as GPUBindGroupLayout)
        }
        jsDescriptor.bindGroupLayouts = layouts
        val pipelineLayout = device.createPipelineLayout(jsDescriptor)
        return GpuPipelineLayout(pipelineLayout)
    }
}

actual class GpuQueue internal constructor(
    internal val queue: GPUQueue
) {
    actual val backend: GpuBackend = GpuBackend.WEBGPU

    actual fun submit(commandBuffers: List<GpuCommandBuffer>) {
        val jsBuffers = js("[]").unsafeCast<Array<GPUCommandBuffer>>()
        val dynBuffers = jsBuffers.asDynamic()
        commandBuffers.forEach { dynBuffers.push(it.buffer) }
        queue.submit(jsBuffers)
    }
}

actual object GpuDeviceFactory {
    actual suspend fun requestContext(config: GpuRequestConfig): GpuContext {
        val gpu = WebGPUDetector.getGPU()
            ?: error("WebGPU is not available in the current environment")

        val adapterOptions = js("({})").unsafeCast<GPURequestAdapterOptions>()
        adapterOptions.powerPreference = config.powerPreference.rawValue
        if (config.forceFallbackAdapter) {
            adapterOptions.forceFallbackAdapter = true
        }

        val adapter = gpu.requestAdapter(adapterOptions).awaitPromise()
            ?: error("Failed to acquire a WebGPU adapter")

        val deviceDescriptor = js("({})").unsafeCast<GPUDeviceDescriptor>()
        config.label?.let { deviceDescriptor.label = it }

        val device = adapter.requestDevice(deviceDescriptor).awaitPromise()
        val queue = device.queue

        val info = extractAdapterInfo(adapter)
        val limits = extractLimits(adapter)

        val gpuDevice = GpuDevice(device, adapter, info, limits)
        val gpuQueue = GpuQueue(queue)

        return GpuContext(
            device = gpuDevice,
            queue = gpuQueue
        )
    }
}

actual fun GpuDevice.unwrapHandle(): Any? = this.device

actual fun GpuDevice.unwrapPhysicalHandle(): Any? = this.physicalDeviceHandle

actual fun GpuQueue.unwrapHandle(): Any? = this.queue

actual class GpuBuffer internal constructor(
    internal val buffer: GPUBuffer,
    actual val size: Long,
    actual val usage: Int
)

actual fun GpuBuffer.unwrapHandle(): Any? = this.buffer

actual class GpuCommandEncoder internal constructor(
    internal val encoder: GPUCommandEncoder
) {
    actual fun finish(): GpuCommandBuffer = GpuCommandBuffer(encoder.finish())
}

actual fun GpuCommandEncoder.unwrapHandle(): Any? = this.encoder

actual class GpuCommandBuffer internal constructor(
    internal val buffer: GPUCommandBuffer
)

actual fun GpuCommandBuffer.unwrapHandle(): Any? = this.buffer

actual class GpuTexture internal constructor(
    internal val texture: GPUTexture,
    actual val width: Int,
    actual val height: Int,
    actual val depth: Int,
    actual val format: String,
    actual val mipLevelCount: Int
) {
    actual fun createView(descriptor: GpuTextureViewDescriptor?): GpuTextureView {
        val view = if (descriptor == null) {
            texture.createView()
        } else {
            val jsDescriptor = js("({})")
            descriptor.label?.let { jsDescriptor.label = it }
            descriptor.format?.let { jsDescriptor.format = it }
            descriptor.dimension?.let {
                jsDescriptor.dimension = when (it) {
                    GpuTextureViewDimension.D1 -> "1d"
                    GpuTextureViewDimension.D2 -> "2d"
                    GpuTextureViewDimension.D2_ARRAY -> "2d-array"
                    GpuTextureViewDimension.CUBE -> "cube"
                    GpuTextureViewDimension.CUBE_ARRAY -> "cube-array"
                    GpuTextureViewDimension.D3 -> "3d"
                }
            }
            descriptor.aspect?.let { jsDescriptor.aspect = it }
            jsDescriptor.baseMipLevel = descriptor.baseMipLevel
            descriptor.mipLevelCount?.let { jsDescriptor.mipLevelCount = it }
            jsDescriptor.baseArrayLayer = descriptor.baseArrayLayer
            descriptor.arrayLayerCount?.let { jsDescriptor.arrayLayerCount = it }
            texture.createView(jsDescriptor)
        }
        return GpuTextureView(view)
    }
    actual fun destroy() {
        texture.destroy()
    }
}

actual fun GpuTexture.unwrapHandle(): Any? = this.texture

actual class GpuSampler internal constructor(
    internal val sampler: GPUSampler
)

actual fun GpuSampler.unwrapHandle(): Any? = this.sampler

actual class GpuBindGroupLayout internal constructor(
    internal val layout: GPUBindGroupLayout
)

actual fun GpuBindGroupLayout.unwrapHandle(): Any? = this.layout

actual class GpuBindGroup internal constructor(
    internal val bindGroup: GPUBindGroup
)

actual fun GpuBindGroup.unwrapHandle(): Any? = this.bindGroup

actual class GpuPipelineLayout internal constructor(
    internal val layout: GPUPipelineLayout
)

actual fun GpuPipelineLayout.unwrapHandle(): Any? = this.layout

actual class GpuTextureView internal constructor(
    internal val view: GPUTextureView
)

actual fun GpuTextureView.unwrapHandle(): Any? = this.view

actual fun GpuDevice.unwrapInstance(): Any? = null

actual fun GpuDevice.unwrapDescriptorPool(): Any? = null

actual fun GpuDevice.queueFamilyIndex(): Int = 0

actual fun GpuQueue.queueFamilyIndex(): Int = 0

private fun extractAdapterInfo(adapter: GPUAdapter): GpuDeviceInfo {
    val dynamicAdapter = adapter.asDynamic()
    val info = dynamicAdapter.info

    if (info == null || jsTypeOf(info) == "undefined") {
        return GpuDeviceInfo(name = "Unknown GPU")
    }

    val name = (info.device as? String)
        ?: (info.name as? String)
        ?: "Unknown GPU"

    val vendor = info.vendor as? String
    val architecture = info.architecture as? String
    val driver = info.driver as? String ?: info.driverVersion as? String

    return GpuDeviceInfo(
        name = name,
        vendor = vendor,
        driverVersion = driver,
        architecture = architecture
    )
}

private fun extractLimits(adapter: GPUAdapter): GpuLimits {
    val limits = adapter.limits ?: return GpuLimits.Empty
    val storageBindingSize = limits.maxStorageBufferBindingSize
    return GpuLimits(
        maxTextureDimension1D = limits.maxTextureDimension1D,
        maxTextureDimension2D = limits.maxTextureDimension2D,
        maxTextureDimension3D = limits.maxTextureDimension3D,
        maxTextureArrayLayers = limits.maxTextureArrayLayers,
        maxBindGroups = limits.maxBindGroups,
        maxUniformBuffersPerStage = limits.maxUniformBuffersPerShaderStage,
        maxStorageBuffersPerStage = limits.maxStorageBuffersPerShaderStage,
        maxBufferSize = storageBindingSize.toLong()
    )
}

private suspend fun <T> Promise<T>.awaitPromise(): T =
    suspendCancellableCoroutine { continuation ->
        then(
            onFulfilled = { value -> continuation.resume(value) },
            onRejected = { error ->
                val throwable = error as? Throwable ?: Exception(error.toString())
                continuation.resumeWithException(throwable)
            }
        )
    }


internal fun GpuDevice.unwrapHandleAdapter(): GPUAdapter = this.adapter
