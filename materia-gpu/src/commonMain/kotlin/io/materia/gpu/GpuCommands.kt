package io.materia.gpu

import kotlinx.serialization.Serializable

@Serializable
data class GpuCommandEncoderDescriptor(
    val label: String? = null
)

expect class GpuCommandEncoder internal constructor(
    device: GpuDevice,
    descriptor: GpuCommandEncoderDescriptor?
) {
    val device: GpuDevice
    val descriptor: GpuCommandEncoderDescriptor?

    fun finish(label: String? = descriptor?.label): GpuCommandBuffer
    fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder
}

expect class GpuCommandBuffer internal constructor(
    device: GpuDevice,
    label: String?
) {
    val device: GpuDevice
    val label: String?
}

enum class GpuLoadOp { LOAD, CLEAR }
enum class GpuStoreOp { STORE, DISCARD }

data class GpuRenderPassColorAttachment(
    val view: GpuTextureView,
    val resolveTarget: GpuTextureView? = null,
    val loadOp: GpuLoadOp = GpuLoadOp.CLEAR,
    val storeOp: GpuStoreOp = GpuStoreOp.STORE,
    val clearColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
)

data class GpuRenderPassDescriptor(
    val colorAttachments: List<GpuRenderPassColorAttachment>,
    val depthStencilAttachment: Any? = null,
    val label: String? = null
)

enum class GpuIndexFormat {
    UINT16,
    UINT32
}

expect class GpuRenderPassEncoder internal constructor(
    encoder: GpuCommandEncoder,
    descriptor: GpuRenderPassDescriptor
) {
    val encoder: GpuCommandEncoder
    val descriptor: GpuRenderPassDescriptor

    fun setPipeline(pipeline: GpuRenderPipeline)
    fun setVertexBuffer(slot: Int, buffer: GpuBuffer)
    fun setIndexBuffer(buffer: GpuBuffer, format: GpuIndexFormat, offset: Long = 0L)
    fun setBindGroup(index: Int, bindGroup: GpuBindGroup)
    fun draw(vertexCount: Int, instanceCount: Int = 1, firstVertex: Int = 0, firstInstance: Int = 0)
    fun drawIndexed(
        indexCount: Int,
        instanceCount: Int = 1,
        firstIndex: Int = 0,
        baseVertex: Int = 0,
        firstInstance: Int = 0
    )

    fun end()
}
