package io.materia.renderer.webgpu

internal data class WebGPUFramebufferAttachments(
    val colorView: GPUTextureView,
    val depthView: GPUTextureView?
)
