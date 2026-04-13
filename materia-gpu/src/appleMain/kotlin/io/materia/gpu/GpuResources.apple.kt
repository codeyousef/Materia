package io.materia.gpu

import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.TextureViewDescriptor

actual class GpuBuffer actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBufferDescriptor
) {
    internal lateinit var wgpuBuffer: GPUBuffer

    internal constructor(device: GpuDevice, descriptor: GpuBufferDescriptor, buffer: GPUBuffer) : this(device, descriptor) {
        wgpuBuffer = buffer
    }

    actual fun write(data: ByteArray, offset: Int) {
        device.queue.writeBuffer(this, offset.toLong(), data)
    }

    actual fun writeFloats(data: FloatArray, offset: Int) {
        val byteBuffer = ByteArray(data.size * 4)
        for (index in data.indices) {
            val bits = data[index].toBits()
            byteBuffer[index * 4] = (bits and 0xFF).toByte()
            byteBuffer[index * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
            byteBuffer[index * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
            byteBuffer[index * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
        }
        write(byteBuffer, offset)
    }

    actual fun destroy() {
        wgpuBuffer.close()
    }
}

actual class GpuTexture actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuTextureDescriptor
) {
    internal lateinit var wgpuTexture: GPUTexture

    internal constructor(device: GpuDevice, descriptor: GpuTextureDescriptor, texture: GPUTexture) : this(device, descriptor) {
        wgpuTexture = texture
    }

    actual fun createView(descriptor: GpuTextureViewDescriptor): GpuTextureView {
        val wgpuView = wgpuTexture.createView(
            TextureViewDescriptor(
                label = descriptor.label ?: "",
                format = descriptor.format?.toWgpu(),
                baseMipLevel = descriptor.baseMipLevel.toUInt(),
                mipLevelCount = descriptor.mipLevelCount?.toUInt(),
                baseArrayLayer = descriptor.baseArrayLayer.toUInt(),
                arrayLayerCount = descriptor.arrayLayerCount?.toUInt()
            )
        )
        return GpuTextureView(this, descriptor, wgpuView)
    }

    actual fun destroy() {
        wgpuTexture.close()
    }
}

actual class GpuTextureView actual constructor(
    actual val texture: GpuTexture,
    actual val descriptor: GpuTextureViewDescriptor
) {
    internal lateinit var wgpuTextureView: GPUTextureView

    internal constructor(texture: GpuTexture, descriptor: GpuTextureViewDescriptor, view: GPUTextureView) : this(texture, descriptor) {
        wgpuTextureView = view
    }
}

actual class GpuSampler actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuSamplerDescriptor
) {
    internal lateinit var wgpuSampler: GPUSampler

    internal constructor(device: GpuDevice, descriptor: GpuSamplerDescriptor, sampler: GPUSampler) : this(device, descriptor) {
        wgpuSampler = sampler
    }
}