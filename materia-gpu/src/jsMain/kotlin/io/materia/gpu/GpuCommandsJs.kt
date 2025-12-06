package io.materia.gpu

import io.ygdrasil.webgpu.*

// ============================================================================
// wgpu4k-based JS GPU Commands Implementation
// ============================================================================

actual class GpuCommandEncoder actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuCommandEncoderDescriptor?
) {
    internal lateinit var wgpuEncoder: GPUCommandEncoder
    
    internal constructor(device: GpuDevice, descriptor: GpuCommandEncoderDescriptor?, encoder: GPUCommandEncoder) : this(device, descriptor) {
        wgpuEncoder = encoder
    }

    actual fun finish(label: String?): GpuCommandBuffer {
        val wgpuBuffer = wgpuEncoder.finish()
        return GpuCommandBuffer(device, label, wgpuBuffer)
    }

    actual fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder {
        val colorAttachments = descriptor.colorAttachments.map { attachment ->
            RenderPassColorAttachment(
                view = attachment.view.wgpuTextureView,
                resolveTarget = attachment.resolveTarget?.wgpuTextureView,
                loadOp = attachment.loadOp.toWgpu(),
                storeOp = attachment.storeOp.toWgpu(),
                clearValue = Color(
                    attachment.clearColor[0].toDouble(),
                    attachment.clearColor[1].toDouble(),
                    attachment.clearColor[2].toDouble(),
                    attachment.clearColor[3].toDouble()
                )
            )
        }

        val depthStencil = descriptor.depthStencilAttachment?.let { ds ->
            RenderPassDepthStencilAttachment(
                view = ds.view.wgpuTextureView,
                depthLoadOp = ds.depthLoadOp.toWgpu(),
                depthStoreOp = ds.depthStoreOp.toWgpu(),
                depthClearValue = ds.depthClearValue,
                depthReadOnly = ds.depthReadOnly,
                stencilLoadOp = ds.stencilLoadOp?.toWgpu(),
                stencilStoreOp = ds.stencilStoreOp?.toWgpu(),
                stencilClearValue = ds.stencilClearValue.toUInt(),
                stencilReadOnly = ds.stencilReadOnly
            )
        }

        val wgpuPass = wgpuEncoder.beginRenderPass(
            RenderPassDescriptor(
                label = descriptor.label ?: "",
                colorAttachments = colorAttachments,
                depthStencilAttachment = depthStencil
            )
        )
        return GpuRenderPassEncoder(this, descriptor, wgpuPass)
    }
}

actual class GpuCommandBuffer actual constructor(
    actual val device: GpuDevice,
    actual val label: String?
) {
    internal lateinit var wgpuCommandBuffer: GPUCommandBuffer
    
    internal constructor(device: GpuDevice, label: String?, buffer: GPUCommandBuffer) : this(device, label) {
        wgpuCommandBuffer = buffer
    }
}

actual class GpuRenderPassEncoder actual constructor(
    actual val encoder: GpuCommandEncoder,
    actual val descriptor: GpuRenderPassDescriptor
) {
    internal lateinit var wgpuPass: GPURenderPassEncoder
    
    internal constructor(encoder: GpuCommandEncoder, descriptor: GpuRenderPassDescriptor, pass: GPURenderPassEncoder) : this(encoder, descriptor) {
        wgpuPass = pass
    }

    actual fun setPipeline(pipeline: GpuRenderPipeline) {
        wgpuPass.setPipeline(pipeline.wgpuPipeline)
    }

    actual fun setVertexBuffer(slot: Int, buffer: GpuBuffer) {
        wgpuPass.setVertexBuffer(slot.toUInt(), buffer.wgpuBuffer)
    }

    actual fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long) {
        wgpuPass.setIndexBuffer(
            buffer.wgpuBuffer,
            format.toWgpu(),
            offset.toULong()
        )
    }

    actual fun setBindGroup(index: Int, bindGroup: GpuBindGroup) {
        wgpuPass.setBindGroup(index.toUInt(), bindGroup.wgpuBindGroup)
    }

    actual fun draw(vertexCount: Int, instanceCount: Int, firstVertex: Int, firstInstance: Int) {
        wgpuPass.draw(
            vertexCount.toUInt(),
            instanceCount.toUInt(),
            firstVertex.toUInt(),
            firstInstance.toUInt()
        )
    }

    actual fun drawIndexed(
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
        baseVertex: Int,
        firstInstance: Int
    ) {
        wgpuPass.drawIndexed(
            indexCount.toUInt(),
            instanceCount.toUInt(),
            firstIndex.toUInt(),
            baseVertex.toInt(),
            firstInstance.toUInt()
        )
    }

    actual fun end() {
        wgpuPass.end()
    }
}

// ============================================================================
// Type conversions for commands
// ============================================================================

internal fun GpuLoadOp.toWgpu(): GPULoadOp = when (this) {
    GpuLoadOp.LOAD -> GPULoadOp.Load
    GpuLoadOp.CLEAR -> GPULoadOp.Clear
}

internal fun GpuStoreOp.toWgpu(): GPUStoreOp = when (this) {
    GpuStoreOp.STORE -> GPUStoreOp.Store
    GpuStoreOp.DISCARD -> GPUStoreOp.Discard
}

internal fun GpuIndexFormat.toWgpu(): GPUIndexFormat = when (this) {
    GpuIndexFormat.UINT16 -> GPUIndexFormat.Uint16
    GpuIndexFormat.UINT32 -> GPUIndexFormat.Uint32
}
