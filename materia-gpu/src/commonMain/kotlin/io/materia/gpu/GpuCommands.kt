package io.materia.gpu

import kotlinx.serialization.Serializable

/**
 * Configuration for creating a command encoder.
 *
 * @property label Optional debug label.
 */
@Serializable
data class GpuCommandEncoderDescriptor(
    val label: String? = null
)

/**
 * Records GPU commands into a command buffer.
 *
 * Create via [GpuDevice.createCommandEncoder], record render passes,
 * then call [finish] to produce a [GpuCommandBuffer] for submission.
 */
expect class GpuCommandEncoder internal constructor(
    device: GpuDevice,
    descriptor: GpuCommandEncoderDescriptor?
) {
    val device: GpuDevice
    val descriptor: GpuCommandEncoderDescriptor?

    fun finish(label: String? = descriptor?.label): GpuCommandBuffer
    fun beginRenderPass(descriptor: GpuRenderPassDescriptor): GpuRenderPassEncoder
}

/**
 * Recorded GPU commands ready for queue submission.
 *
 * Produced by calling [GpuCommandEncoder.finish]. Submit via [GpuQueue.submit].
 */
expect class GpuCommandBuffer internal constructor(
    device: GpuDevice,
    label: String?
) {
    val device: GpuDevice
    val label: String?
}

/** How to initialize a render target at the start of a pass. */
enum class GpuLoadOp {
    /** Preserve existing contents. */
    LOAD,
    /** Clear to a specified value. */
    CLEAR
}

/** What to do with render target contents at the end of a pass. */
enum class GpuStoreOp {
    /** Write results to the texture. */
    STORE,
    /** Discard results (useful for depth). */
    DISCARD
}

data class GpuRenderPassColorAttachment(
    val view: GpuTextureView,
    val resolveTarget: GpuTextureView? = null,
    val loadOp: GpuLoadOp = GpuLoadOp.CLEAR,
    val storeOp: GpuStoreOp = GpuStoreOp.STORE,
    val clearColor: FloatArray = floatArrayOf(0f, 0f, 0f, 1f)
)

data class GpuRenderPassDepthStencilAttachment(
    val view: GpuTextureView,
    val depthLoadOp: GpuLoadOp = GpuLoadOp.CLEAR,
    val depthStoreOp: GpuStoreOp = GpuStoreOp.DISCARD,
    val depthClearValue: Float = 1.0f,
    val depthReadOnly: Boolean = false,
    val stencilLoadOp: GpuLoadOp? = null,
    val stencilStoreOp: GpuStoreOp? = null,
    val stencilClearValue: Int = 0,
    val stencilReadOnly: Boolean = false
)

/**
 * Configuration for beginning a render pass.
 *
 * @property colorAttachments List of color render targets.
 * @property depthStencilAttachment Optional depth/stencil target.
 * @property label Optional debug label.
 */
data class GpuRenderPassDescriptor(
    val colorAttachments: List<GpuRenderPassColorAttachment>,
    val depthStencilAttachment: GpuRenderPassDepthStencilAttachment? = null,
    val label: String? = null
)

/** Index buffer element size. */
enum class GpuIndexFormat {
    /** 16-bit unsigned indices. */
    UINT16,
    /** 32-bit unsigned indices. */
    UINT32
}

/**
 * Records draw commands within a render pass.
 *
 * Obtained from [GpuCommandEncoder.beginRenderPass]. Set pipeline and resources,
 * issue draw calls, then call [end] to finish the pass.
 */
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
