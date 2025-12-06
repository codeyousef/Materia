package io.materia.gpu

import io.ygdrasil.webgpu.*

// ============================================================================
// wgpu4k-based Android GPU Pipeline Implementation
// ============================================================================

actual class GpuShaderModule actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuShaderModuleDescriptor
) {
    internal lateinit var wgpuModule: GPUShaderModule
    
    internal constructor(device: GpuDevice, descriptor: GpuShaderModuleDescriptor, module: GPUShaderModule) : this(device, descriptor) {
        wgpuModule = module
    }
}

actual class GpuBindGroupLayout actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuBindGroupLayoutDescriptor
) {
    internal lateinit var wgpuLayout: GPUBindGroupLayout
    
    internal constructor(device: GpuDevice, descriptor: GpuBindGroupLayoutDescriptor, layout: GPUBindGroupLayout) : this(device, descriptor) {
        wgpuLayout = layout
    }
}

actual class GpuBindGroup actual constructor(
    actual val layout: GpuBindGroupLayout,
    actual val descriptor: GpuBindGroupDescriptor
) {
    internal lateinit var wgpuBindGroup: GPUBindGroup
    
    internal constructor(layout: GpuBindGroupLayout, descriptor: GpuBindGroupDescriptor, bindGroup: GPUBindGroup) : this(layout, descriptor) {
        wgpuBindGroup = bindGroup
    }
}

actual class GpuRenderPipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuRenderPipelineDescriptor
) {
    internal lateinit var wgpuPipeline: GPURenderPipeline
    
    internal constructor(device: GpuDevice, descriptor: GpuRenderPipelineDescriptor, pipeline: GPURenderPipeline) : this(device, descriptor) {
        wgpuPipeline = pipeline
    }
}

actual class GpuComputePipeline actual constructor(
    actual val device: GpuDevice,
    actual val descriptor: GpuComputePipelineDescriptor
) {
    internal lateinit var wgpuPipeline: GPUComputePipeline
    
    internal constructor(device: GpuDevice, descriptor: GpuComputePipelineDescriptor, pipeline: GPUComputePipeline) : this(device, descriptor) {
        wgpuPipeline = pipeline
    }
}
