package io.materia.renderer.feature020

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Contract tests for [RenderPassManager].
 * Uses the simulated implementation to validate state handling.
 */
class RenderPassManagerTest {

    private lateinit var renderPassManager: RenderPassManager
    private lateinit var framebuffer: FramebufferHandle
    private lateinit var pipeline: PipelineHandle
    private lateinit var vertexBuffer: BufferHandle
    private lateinit var indexBuffer: BufferHandle
    private lateinit var uniformBuffer: BufferHandle

    @BeforeTest
    fun setup() {
        renderPassManager = SimulatedRenderPassManager()
        framebuffer = FramebufferHandle(handle = Any())
        pipeline = PipelineHandle(handle = Any())
        vertexBuffer = BufferHandle(
            handle = Any(),
            size = 256,
            usage = BufferUsage.VERTEX
        )
        indexBuffer = BufferHandle(
            handle = Any(),
            size = 128,
            usage = BufferUsage.INDEX
        )
        uniformBuffer = BufferHandle(
            handle = Any(),
            size = 256,
            usage = BufferUsage.UNIFORM
        )
    }

    @Test
    fun testRenderPassLifecycle_validUsage_succeeds() {
        val clearColor = Color(0.53f, 0.81f, 0.92f, 1.0f)
        renderPassManager.beginRenderPass(clearColor, framebuffer)
        renderPassManager.endRenderPass()
    }

    @Test
    fun testBeginRenderPass_alreadyActive_throwsException() {
        renderPassManager.beginRenderPass(Color(0f, 0f, 0f, 1f), framebuffer)

        assertFailsWith<RenderPassException> {
            renderPassManager.beginRenderPass(Color(0f, 0f, 0f, 1f), framebuffer)
        }

        renderPassManager.endRenderPass()
    }

    @Test
    fun testBindPipeline_validPipeline_succeeds() {
        renderPassManager.beginRenderPass(Color(0.53f, 0.81f, 0.92f, 1.0f), framebuffer)
        renderPassManager.bindPipeline(pipeline)
        renderPassManager.endRenderPass()
    }

    @Test
    fun testBindVertexBuffer_validBuffer_succeeds() {
        renderPassManager.beginRenderPass(Color(0.53f, 0.81f, 0.92f, 1.0f), framebuffer)
        renderPassManager.bindVertexBuffer(vertexBuffer, slot = 0)
        renderPassManager.endRenderPass()
    }

    @Test
    fun testBindIndexBuffer_validBuffer_succeeds() {
        renderPassManager.beginRenderPass(Color(0.53f, 0.81f, 0.92f, 1.0f), framebuffer)
        renderPassManager.bindIndexBuffer(indexBuffer, indexSizeInBytes = 4)
        renderPassManager.endRenderPass()
    }

    @Test
    fun testDrawIndexed_completeFlow_succeeds() {
        renderPassManager.beginRenderPass(Color(0.53f, 0.81f, 0.92f, 1.0f), framebuffer)
        renderPassManager.bindPipeline(pipeline)
        renderPassManager.bindVertexBuffer(vertexBuffer)
        renderPassManager.bindIndexBuffer(indexBuffer, indexSizeInBytes = 4)
        renderPassManager.bindUniformBuffer(uniformBuffer)

        renderPassManager.drawIndexed(indexCount = 3, firstIndex = 0, instanceCount = 1)

        renderPassManager.endRenderPass()
    }

    @Test
    fun testDrawIndexed_noPipeline_throwsException() {
        renderPassManager.beginRenderPass(Color(0.53f, 0.81f, 0.92f, 1.0f), framebuffer)
        renderPassManager.bindVertexBuffer(vertexBuffer)
        renderPassManager.bindIndexBuffer(indexBuffer, indexSizeInBytes = 4)

        assertFailsWith<IllegalStateException> {
            renderPassManager.drawIndexed(indexCount = 3)
        }

        renderPassManager.endRenderPass()
    }
}
