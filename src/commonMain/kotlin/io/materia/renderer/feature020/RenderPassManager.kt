package io.materia.renderer.feature020

/**
 * Render pass manager for recording drawing commands.
 * Feature 020 - Production-Ready Renderer
 *
 * Provides cross-platform render pass recording for clearing framebuffer
 * and executing draw calls.
 */
interface RenderPassManager {
    /**
     * Begin render pass with clear color.
     *
     * @param clearColor Framebuffer clear color (RGBA, 0.0-1.0)
     * @param framebuffer Platform-specific framebuffer handle
     * @throws RenderPassException if render pass already active
     */
    fun beginRenderPass(clearColor: Color, framebuffer: FramebufferHandle)

    /**
     * Bind graphics pipeline.
     *
     * @param pipeline Platform-specific pipeline handle
     * @throws IllegalStateException if no active render pass
     */
    fun bindPipeline(pipeline: PipelineHandle)

    /**
     * Bind vertex buffer to slot.
     *
     * @param buffer Vertex buffer handle
     * @param slot Binding slot (default 0)
     * @throws InvalidBufferException if buffer invalid
     */
    fun bindVertexBuffer(buffer: BufferHandle, slot: Int = 0)

    /**
     * Bind index buffer.
     *
     * @param buffer Index buffer handle
     * @throws InvalidBufferException if buffer invalid
     */
    fun bindIndexBuffer(buffer: BufferHandle, indexSizeInBytes: Int = 2)

    /**
     * Bind uniform buffer to group and binding.
     *
     * @param buffer Uniform buffer handle
     * @param group Binding group (default 0)
     * @param binding Binding index (default 0)
     * @throws InvalidBufferException if buffer invalid
     */
    fun bindUniformBuffer(buffer: BufferHandle, group: Int = 0, binding: Int = 0)

    /**
     * Draw indexed primitives.
     *
     * @param indexCount Number of indices to draw
     * @param firstIndex First index to start drawing from
     * @param instanceCount Number of instances (1 for non-instanced)
     * @throws IllegalStateException if no pipeline or buffers bound
     */
    fun drawIndexed(indexCount: Int, firstIndex: Int = 0, instanceCount: Int = 1)

    /**
     * End render pass.
     *
     * @throws IllegalStateException if no active render pass
     */
    fun endRenderPass()
}

/**
 * Simple RGBA color (0.0-1.0 range).
 */
data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1.0f
)

/**
 * Platform-specific framebuffer handle.
 */
data class FramebufferHandle(val handle: Any?)

/**
 * Platform-specific pipeline handle.
 */
data class PipelineHandle(val handle: Any?)

/**
 * Exception thrown when render pass state is invalid.
 */
class RenderPassException(message: String) : Exception(message)
