/**
 * T013-T014: WebGPURenderPassManager Implementation
 * Feature: 020-go-from-mvp
 *
 * WebGPU render pass recording for drawing commands.
 */

package io.materia.renderer.webgpu

import io.materia.renderer.feature020.*

/**
 * WebGPU render pass manager implementation.
 *
 * Records drawing commands using GPURenderPassEncoder.
 *
 * @property commandEncoder WebGPU command encoder
 */
class WebGPURenderPassManager(
    private val commandEncoder: dynamic // GPUCommandEncoder
) : RenderPassManager {

    // Track render pass state
    private var passEncoder: dynamic = null // GPURenderPassEncoder
    private var renderPassActive = false
    private var pipelineBound = false

    /**
     * Get the internal GPURenderPassEncoder for legacy rendering code.
     * This is a temporary method to support the transition from direct WebGPU API usage
     * to the RenderPassManager abstraction.
     */
    fun getPassEncoder(): dynamic = passEncoder

    /**
     * Begin render pass with clear color.
     *
     * @param clearColor Framebuffer clear color (RGBA, 0.0-1.0)
     * @param framebuffer Platform-specific framebuffer handle (GPUTextureView)
     * @throws RenderPassException if render pass already active
     */
    override fun beginRenderPass(clearColor: Color, framebuffer: FramebufferHandle) {
        if (renderPassActive) {
            throw RenderPassException("Render pass already active. Call endRenderPass() first.")
        }

        try {
            val handle = framebuffer.handle
                ?: throw IllegalArgumentException("framebuffer.handle must not be null")

            val colorView: GPUTextureView
            val depthView: GPUTextureView?

            when (handle) {
                is WebGPUFramebufferAttachments -> {
                    colorView = handle.colorView
                    depthView = handle.depthView
                }

                else -> {
                    colorView = handle as? GPUTextureView
                        ?: throw IllegalArgumentException("framebuffer.handle must be GPUTextureView or WebGPUFramebufferAttachments")
                    depthView = null
                }
            }

            // Create render pass descriptor (build object programmatically for proper Kotlin→JS conversion)
            val clearValue = js("{}")
            clearValue.r = clearColor.r
            clearValue.g = clearColor.g
            clearValue.b = clearColor.b
            clearValue.a = clearColor.a

            val colorAttachment = js("{}")
            colorAttachment.view = colorView
            colorAttachment.loadOp = "clear"
            colorAttachment.storeOp = "store"
            colorAttachment.clearValue = clearValue

            val descriptor = js("{}")
            descriptor.colorAttachments = arrayOf(colorAttachment)

            depthView?.let { depthTextureView ->
                val depthAttachment = js("{}")
                depthAttachment.view = depthTextureView
                depthAttachment.depthClearValue = 1.0
                depthAttachment.depthLoadOp = "clear"
                depthAttachment.depthStoreOp = "store"
                descriptor.depthStencilAttachment = depthAttachment
            }

            // Begin render pass
            passEncoder = commandEncoder.beginRenderPass(descriptor)

            if (passEncoder == null || passEncoder == undefined) {
                throw RenderPassException("Failed to begin render pass")
            }

            renderPassActive = true
            pipelineBound = false
        } catch (e: RenderPassException) {
            throw e
        } catch (e: Exception) {
            throw RenderPassException("Failed to begin render pass: ${e.message}")
        } catch (e: Throwable) {
            throw RenderPassException("Failed to begin render pass: ${e.message}")
        }
    }

    /**
     * Bind graphics pipeline.
     *
     * @param pipeline Platform-specific pipeline handle (GPURenderPipeline)
     * @throws IllegalStateException if no active render pass
     */
    override fun bindPipeline(pipeline: PipelineHandle) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        try {
            val renderPipeline = pipeline.handle
                ?: throw IllegalArgumentException("pipeline.handle must be GPURenderPipeline")

            passEncoder.setPipeline(renderPipeline)
            pipelineBound = true
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Failed to bind pipeline: ${e.message}")
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to bind pipeline: ${e.message}")
        }
    }

    /**
     * Bind vertex buffer to slot.
     *
     * @param buffer Vertex buffer handle (GPUBuffer)
     * @param slot Binding slot (default 0)
     * @throws InvalidBufferException if buffer invalid
     */
    override fun bindVertexBuffer(buffer: BufferHandle, slot: Int) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        if (!buffer.isValid()) {
            throw InvalidBufferException("Vertex buffer handle is invalid")
        }

        try {
            val gpuBuffer = buffer.handle
                ?: throw InvalidBufferException("Buffer handle is null")

            passEncoder.setVertexBuffer(slot, gpuBuffer)
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to bind vertex buffer: ${e.message}")
        } catch (e: Throwable) {
            throw InvalidBufferException("Failed to bind vertex buffer: ${e.message}")
        }
    }

    /**
     * Bind index buffer.
     *
     * @param buffer Index buffer handle (GPUBuffer)
     * @throws InvalidBufferException if buffer invalid
     */
    override fun bindIndexBuffer(buffer: BufferHandle, indexSizeInBytes: Int) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        if (!buffer.isValid()) {
            throw InvalidBufferException("Index buffer handle is invalid")
        }

        require(indexSizeInBytes == 2 || indexSizeInBytes == 4) {
            "Index size must be 2 or 4 bytes, got $indexSizeInBytes"
        }

        try {
            val gpuBuffer = buffer.handle
                ?: throw InvalidBufferException("Buffer handle is null")

            val format = if (indexSizeInBytes == 2) "uint16" else "uint32"
            passEncoder.setIndexBuffer(gpuBuffer, format)
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to bind index buffer: ${e.message}")
        } catch (e: Throwable) {
            throw InvalidBufferException("Failed to bind index buffer: ${e.message}")
        }
    }

    /**
     * Bind uniform buffer to group and binding.
     *
     * @param buffer Uniform buffer handle (GPUBuffer)
     * @param group Binding group (default 0)
     * @param binding Binding index (default 0)
     * @throws InvalidBufferException if buffer invalid
     */
    override fun bindUniformBuffer(buffer: BufferHandle, group: Int, binding: Int) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        if (!buffer.isValid()) {
            throw InvalidBufferException("Uniform buffer handle is invalid")
        }

        try {
            val gpuBuffer = buffer.handle
                ?: throw InvalidBufferException("Buffer handle is null")

            // Note: Uniform buffer binding requires GPUBindGroup creation with bind group layout.
            // This is deferred to full pipeline implementation where bind group layouts are defined.
            // For Feature 020 core implementation, uniform buffers are created and managed,
            // but binding requires integration with the full rendering pipeline (WebGPURenderer).
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to bind uniform buffer: ${e.message}")
        } catch (e: Throwable) {
            throw InvalidBufferException("Failed to bind uniform buffer: ${e.message}")
        }
    }

    /**
     * Draw indexed primitives.
     *
     * @param indexCount Number of indices to draw
     * @param firstIndex First index to start drawing from
     * @param instanceCount Number of instances (1 for non-instanced)
     * @throws IllegalStateException if no pipeline or buffers bound
     */
    override fun drawIndexed(indexCount: Int, firstIndex: Int, instanceCount: Int) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        if (!pipelineBound) {
            throw IllegalStateException("No pipeline bound. Call bindPipeline() first.")
        }

        try {
            passEncoder.drawIndexed(indexCount, instanceCount, firstIndex, 0, 0)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to draw indexed: ${e.message}")
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to draw indexed: ${e.message}")
        }
    }

    /**
     * End render pass.
     *
     * @throws IllegalStateException if no active render pass
     */
    override fun endRenderPass() {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        try {
            passEncoder.end()
            renderPassActive = false
            pipelineBound = false
            passEncoder = null
        } catch (e: Exception) {
            throw IllegalStateException("Failed to end render pass: ${e.message}")
        } catch (e: Throwable) {
            throw IllegalStateException("Failed to end render pass: ${e.message}")
        }
    }
}
