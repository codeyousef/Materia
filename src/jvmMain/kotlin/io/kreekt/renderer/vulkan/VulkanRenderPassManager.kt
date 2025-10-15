/**
 * T007-T008: VulkanRenderPassManager Implementation
 * Feature: 020-go-from-mvp
 *
 * Vulkan render pass recording for drawing commands.
 */

package io.kreekt.renderer.vulkan

import io.kreekt.renderer.feature020.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.vulkan.VK12.*
import org.lwjgl.vulkan.VkClearValue
import org.lwjgl.vulkan.VkCommandBuffer
import org.lwjgl.vulkan.VkDevice
import org.lwjgl.vulkan.VkRenderPassBeginInfo

/**
 * Vulkan render pass manager implementation.
 *
 * Records drawing commands into a command buffer using VkRenderPass.
 *
 * @property device Vulkan logical device
 * @property commandBuffer Vulkan command buffer for recording
 * @property renderPass Vulkan render pass handle
 */
class VulkanRenderPassManager(
    private val device: VkDevice,
    private val commandBuffer: VkCommandBuffer,
    private val renderPass: Long // VkRenderPass
) : RenderPassManager {

    // Track render pass state
    private var renderPassActive = false
    private var pipelineBound = false

    /**
     * Begin render pass with clear color.
     *
     * @param clearColor Framebuffer clear color (RGBA, 0.0-1.0)
     * @param framebuffer Platform-specific framebuffer handle
     * @throws RenderPassException if render pass already active
     */
    override fun beginRenderPass(clearColor: Color, framebuffer: FramebufferHandle) {
        if (renderPassActive) {
            throw RenderPassException("Render pass already active. Call endRenderPass() first.")
        }

        try {
            MemoryStack.stackPush().use { stack ->
                // Extract framebuffer data from handle
                val framebufferData = framebuffer.handle as? VulkanFramebufferData
                    ?: throw IllegalArgumentException("framebuffer.handle must be VulkanFramebufferData")
                val vkFramebuffer = framebufferData.framebuffer

                // Create clear value from color
                val clearValues = VkClearValue.calloc(1, stack)
                clearValues.get(0).color()
                    .float32(0, clearColor.r)
                    .float32(1, clearColor.g)
                    .float32(2, clearColor.b)
                    .float32(3, clearColor.a)

                // Create render pass begin info
                val renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                    .renderPass(renderPass)
                    .framebuffer(vkFramebuffer)
                    .renderArea { area ->
                        area.offset().set(0, 0)
                        area.extent().set(framebufferData.width, framebufferData.height)
                    }
                    .pClearValues(clearValues)

                // Begin render pass
                vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE)

                renderPassActive = true
                pipelineBound = false
            }
        } catch (e: RenderPassException) {
            throw e
        } catch (e: Exception) {
            throw RenderPassException("Failed to begin render pass: ${e.message}")
        }
    }

    /**
     * Bind graphics pipeline.
     *
     * @param pipeline Platform-specific pipeline handle
     * @throws IllegalStateException if no active render pass
     */
    override fun bindPipeline(pipeline: PipelineHandle) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        try {
            val vkPipeline = (pipeline.handle as? Long)
                ?: throw IllegalArgumentException("pipeline.handle must be VkPipeline (Long)")

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, vkPipeline)
            pipelineBound = true
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Failed to bind pipeline: ${e.message}")
        }
    }

    /**
     * Bind vertex buffer to slot.
     *
     * @param buffer Vertex buffer handle
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
            val bufferData = buffer.handle as? VulkanBufferHandleData
                ?: throw InvalidBufferException("Buffer handle is not a VulkanBufferHandleData")

            MemoryStack.stackPush().use { stack ->
                val vertexBuffers = stack.mallocLong(1).put(0, bufferData.buffer)
                val offsets = stack.mallocLong(1).put(0, 0L)

                vkCmdBindVertexBuffers(commandBuffer, slot, vertexBuffers, offsets)
            }
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to bind vertex buffer: ${e.message}")
        }
    }

    /**
     * Bind index buffer.
     *
     * @param buffer Index buffer handle
     * @throws InvalidBufferException if buffer invalid
     */
    override fun bindIndexBuffer(buffer: BufferHandle) {
        if (!renderPassActive) {
            throw IllegalStateException("No active render pass. Call beginRenderPass() first.")
        }

        if (!buffer.isValid()) {
            throw InvalidBufferException("Index buffer handle is invalid")
        }

        try {
            val bufferData = buffer.handle as? VulkanBufferHandleData
                ?: throw InvalidBufferException("Buffer handle is not a VulkanBufferHandleData")

            vkCmdBindIndexBuffer(commandBuffer, bufferData.buffer, 0, VK_INDEX_TYPE_UINT32)
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
            throw InvalidBufferException("Failed to bind index buffer: ${e.message}")
        }
    }

    /**
     * Bind uniform buffer to group and binding.
     *
     * @param buffer Uniform buffer handle
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
            val bufferData = buffer.handle as? VulkanBufferHandleData
                ?: throw InvalidBufferException("Buffer handle is not a VulkanBufferHandleData")

            // Note: Uniform buffer binding requires descriptor sets.
            // This is deferred to full pipeline implementation where descriptor set layouts are defined.
            // For Feature 020 core implementation, uniform buffers are created and managed,
            // but binding requires integration with the full rendering pipeline (VulkanRenderer).
        } catch (e: InvalidBufferException) {
            throw e
        } catch (e: Exception) {
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
            vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, 0, 0)
        } catch (e: Exception) {
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
            vkCmdEndRenderPass(commandBuffer)
            renderPassActive = false
            pipelineBound = false
        } catch (e: Exception) {
            throw IllegalStateException("Failed to end render pass: ${e.message}")
        }
    }
}
