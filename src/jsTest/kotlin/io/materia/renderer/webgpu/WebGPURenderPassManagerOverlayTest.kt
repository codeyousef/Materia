package io.materia.renderer.webgpu

import io.materia.renderer.feature020.Color
import io.materia.renderer.feature020.FramebufferHandle
import kotlin.test.Test
import kotlin.test.assertEquals

class WebGPURenderPassManagerOverlayTest {

    @Test
    fun overlayPassLoadsColorAndClearsDepth() {
        var descriptor: dynamic = null
        val pass = js("({})")
        pass.end = { Unit }
        val encoder = js("({})")
        encoder.beginRenderPass = { value: dynamic ->
            descriptor = value
            pass
        }
        val colorView = js("({})").unsafeCast<GPUTextureView>()
        val depthView = js("({})").unsafeCast<GPUTextureView>()
        val framebuffer = FramebufferHandle(
            WebGPUFramebufferAttachments(colorView, depthView)
        )
        val manager = WebGPURenderPassManager(encoder)

        manager.beginRenderPass(
            clearColor = Color(0f, 0f, 0f, 1f),
            framebuffer = framebuffer,
            loadColor = true,
            clearDepth = true
        )

        assertEquals("load", descriptor.colorAttachments[0].loadOp)
        assertEquals("store", descriptor.colorAttachments[0].storeOp)
        assertEquals("clear", descriptor.depthStencilAttachment.depthLoadOp)
        manager.endRenderPass()
    }

    @Test
    fun retainedDepthUsesLoadOperation() {
        var descriptor: dynamic = null
        val pass = js("({})")
        pass.end = { Unit }
        val encoder = js("({})")
        encoder.beginRenderPass = { value: dynamic ->
            descriptor = value
            pass
        }
        val framebuffer = FramebufferHandle(
            WebGPUFramebufferAttachments(
                js("({})").unsafeCast<GPUTextureView>(),
                js("({})").unsafeCast<GPUTextureView>()
            )
        )
        val manager = WebGPURenderPassManager(encoder)

        manager.beginRenderPass(
            clearColor = Color(0f, 0f, 0f, 1f),
            framebuffer = framebuffer,
            loadColor = true,
            clearDepth = false
        )

        assertEquals("load", descriptor.depthStencilAttachment.depthLoadOp)
        manager.endRenderPass()
    }
}
