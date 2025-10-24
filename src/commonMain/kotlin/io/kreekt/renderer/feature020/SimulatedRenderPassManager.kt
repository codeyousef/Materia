package io.kreekt.renderer.feature020

/**
 * In-memory implementation of [RenderPassManager] used for tests and
 * platforms that do not yet have a native GPU-backed implementation.
 *
 * The manager keeps track of render pass state, validates transitions,
 * and ensures required GPU resources are bound before draw calls.
 */
class SimulatedRenderPassManager : RenderPassManager {

    private var activePass: RenderPassState? = null

    override fun beginRenderPass(clearColor: Color, framebuffer: FramebufferHandle) {
        if (activePass != null) {
            throw RenderPassException("Render pass already active")
        }
        if (framebuffer.handle == null) {
            throw RenderPassException("Framebuffer handle cannot be null")
        }

        activePass = RenderPassState(
            clearColor = clearColor,
            framebuffer = framebuffer
        )
    }

    override fun bindPipeline(pipeline: PipelineHandle) {
        val state = ensureActivePass()
        validatePipeline(pipeline)
        state.pipeline = pipeline
    }

    override fun bindVertexBuffer(buffer: BufferHandle, slot: Int) {
        val state = ensureActivePass()
        validateBuffer(buffer, BufferUsage.VERTEX)
        require(slot >= 0) { "Vertex buffer slot must be non-negative, got $slot" }
        state.vertexBuffers[slot] = buffer
    }

    override fun bindIndexBuffer(buffer: BufferHandle, indexSizeInBytes: Int) {
        val state = ensureActivePass()
        validateBuffer(buffer, BufferUsage.INDEX)
        require(indexSizeInBytes == 2 || indexSizeInBytes == 4) {
            "Index size must be 2 or 4 bytes, got $indexSizeInBytes"
        }
        state.indexBuffer = buffer
        state.indexElementSize = indexSizeInBytes
    }

    override fun bindUniformBuffer(buffer: BufferHandle, group: Int, binding: Int) {
        val state = ensureActivePass()
        validateBuffer(buffer, BufferUsage.UNIFORM)
        require(group >= 0) { "Bind group must be non-negative, got $group" }
        require(binding >= 0) { "Binding index must be non-negative, got $binding" }
        state.uniformBindings[group to binding] = buffer
    }

    override fun drawIndexed(indexCount: Int, firstIndex: Int, instanceCount: Int) {
        val state = ensureActivePass()

        require(indexCount > 0) { "indexCount must be > 0, got $indexCount" }
        require(firstIndex >= 0) { "firstIndex must be >= 0, got $firstIndex" }
        require(instanceCount > 0) { "instanceCount must be > 0, got $instanceCount" }

        if (state.pipeline == null) {
            throw IllegalStateException("Cannot draw indexed primitives without a bound pipeline")
        }
        if (state.vertexBuffers.isEmpty()) {
            throw IllegalStateException("No vertex buffer bound for drawIndexed call")
        }
        if (state.indexBuffer == null) {
            throw IllegalStateException("No index buffer bound for drawIndexed call")
        }
    }

    override fun endRenderPass() {
        if (activePass == null) {
            throw IllegalStateException("No active render pass to end")
        }
        activePass = null
    }

    private fun ensureActivePass(): RenderPassState {
        return activePass ?: throw IllegalStateException("Render pass not active. Call beginRenderPass() first.")
    }

    private fun validatePipeline(pipeline: PipelineHandle) {
        if (pipeline.handle == null) {
            throw IllegalArgumentException("Pipeline handle cannot be null")
        }
    }

    private fun validateBuffer(buffer: BufferHandle, expectedUsage: BufferUsage) {
        if (!buffer.isValid()) {
            throw InvalidBufferException("Buffer is not valid (handle=${buffer.handle}, size=${buffer.size})")
        }
        if (buffer.usage != expectedUsage) {
            throw InvalidBufferException(
                "Buffer usage mismatch. Expected $expectedUsage, actual ${buffer.usage}"
            )
        }
    }

    private data class RenderPassState(
        val clearColor: Color,
        val framebuffer: FramebufferHandle,
        var pipeline: PipelineHandle? = null,
        val vertexBuffers: MutableMap<Int, BufferHandle> = mutableMapOf(),
        var indexBuffer: BufferHandle? = null,
        var indexElementSize: Int = 0,
        val uniformBindings: MutableMap<Pair<Int, Int>, BufferHandle> = mutableMapOf()
    )
}
