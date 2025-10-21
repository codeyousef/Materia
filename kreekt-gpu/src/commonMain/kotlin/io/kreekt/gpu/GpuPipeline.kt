package io.kreekt.gpu

import kotlinx.serialization.Serializable

enum class GpuShaderStage {
    VERTEX,
    FRAGMENT,
    COMPUTE
}

@Serializable
data class GpuShaderModuleDescriptor(
    val label: String? = null,
    val code: String,
    val sourceLanguage: ShaderSourceLanguage = ShaderSourceLanguage.WGSL
)

enum class ShaderSourceLanguage {
    WGSL,
    SPIRV
}

@Serializable
data class GpuBindGroupLayoutDescriptor(
    val label: String? = null,
    val entries: List<GpuBindGroupLayoutEntry> = emptyList()
)

@Serializable
data class GpuBindGroupLayoutEntry(
    val binding: Int,
    val visibility: Set<GpuShaderStage>,
    val resourceType: String
)

@Serializable
data class GpuBindGroupDescriptor(
    val label: String? = null,
    val layout: GpuBindGroupLayout,
    val entries: List<GpuBindGroupEntry> = emptyList()
)

@Serializable
data class GpuBindGroupEntry(
    val binding: Int,
    val resource: Any
)

@Serializable
data class GpuRenderPipelineDescriptor(
    val label: String? = null,
    val vertexShader: GpuShaderModule,
    val fragmentShader: GpuShaderModule? = null,
    val colorFormats: List<GpuTextureFormat> = emptyList(),
    val depthStencilFormat: GpuTextureFormat? = null
)

@Serializable
data class GpuComputePipelineDescriptor(
    val label: String? = null,
    val shader: GpuShaderModule
)

expect class GpuShaderModule internal constructor(
    device: GpuDevice,
    descriptor: GpuShaderModuleDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuShaderModuleDescriptor
}

expect class GpuBindGroupLayout internal constructor(
    device: GpuDevice,
    descriptor: GpuBindGroupLayoutDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuBindGroupLayoutDescriptor
}

expect class GpuBindGroup internal constructor(
    layout: GpuBindGroupLayout,
    descriptor: GpuBindGroupDescriptor
) {
    val layout: GpuBindGroupLayout
    val descriptor: GpuBindGroupDescriptor
}

expect class GpuRenderPipeline internal constructor(
    device: GpuDevice,
    descriptor: GpuRenderPipelineDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuRenderPipelineDescriptor
}

expect class GpuComputePipeline internal constructor(
    device: GpuDevice,
    descriptor: GpuComputePipelineDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuComputePipelineDescriptor
}
