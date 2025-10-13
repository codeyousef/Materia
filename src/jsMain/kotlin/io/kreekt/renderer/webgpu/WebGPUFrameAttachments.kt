package io.kreekt.renderer.webgpu

internal data class WebGPUFramebufferAttachments(
    val colorView: GPUTextureView,
    val depthView: GPUTextureView?
)
