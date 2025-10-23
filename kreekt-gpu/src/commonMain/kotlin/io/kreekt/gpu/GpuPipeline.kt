package io.kreekt.gpu


enum class GpuShaderStage {
    VERTEX,
    FRAGMENT,
    COMPUTE
}

data class GpuShaderModuleDescriptor(
    val label: String? = null,
    val code: String,
    val sourceLanguage: ShaderSourceLanguage = ShaderSourceLanguage.WGSL
)

enum class ShaderSourceLanguage {
    WGSL,
    SPIRV
}

data class GpuBindGroupLayoutDescriptor(
    val label: String? = null,
    val entries: List<GpuBindGroupLayoutEntry> = emptyList()
)

data class GpuBindGroupLayoutEntry(
    val binding: Int,
    val visibility: Set<GpuShaderStage>,
    val resourceType: GpuBindingResourceType
)

data class GpuBindGroupDescriptor(
    val label: String? = null,
    val layout: GpuBindGroupLayout,
    val entries: List<GpuBindGroupEntry> = emptyList()
)

data class GpuBindGroupEntry(
    val binding: Int,
    val resource: GpuBindingResource
)

enum class GpuBindingResourceType {
    UNIFORM_BUFFER,
    STORAGE_BUFFER,
    SAMPLER,
    TEXTURE
}

sealed class GpuBindingResource {
    data class Buffer(
        val buffer: GpuBuffer,
        val offset: Long = 0L,
        val size: Long? = null
    ) : GpuBindingResource()

    data class Sampler(
        val sampler: GpuSampler
    ) : GpuBindingResource()

    data class Texture(
        val textureView: GpuTextureView
    ) : GpuBindingResource()
}

enum class GpuVertexStepMode {
    VERTEX,
    INSTANCE
}

enum class GpuVertexFormat {
    FLOAT32,
    FLOAT32x2,
    FLOAT32x3,
    FLOAT32x4,
    UINT32,
    UINT32x2,
    UINT32x3,
    UINT32x4,
    SINT32,
    SINT32x2,
    SINT32x3,
    SINT32x4
}

data class GpuVertexAttribute(
    val shaderLocation: Int,
    val format: GpuVertexFormat,
    val offset: Int
)

data class GpuVertexBufferLayout(
    val arrayStride: Int,
    val stepMode: GpuVertexStepMode = GpuVertexStepMode.VERTEX,
    val attributes: List<GpuVertexAttribute> = emptyList()
)

enum class GpuPrimitiveTopology {
    POINT_LIST,
    LINE_LIST,
    LINE_STRIP,
    TRIANGLE_LIST,
    TRIANGLE_STRIP
}

enum class GpuFrontFace {
    CCW,
    CW
}

enum class GpuCullMode {
    NONE,
    FRONT,
    BACK
}

data class GpuRenderPipelineDescriptor(
    val label: String? = null,
    val vertexShader: GpuShaderModule,
    val fragmentShader: GpuShaderModule? = null,
    val colorFormats: List<GpuTextureFormat> = emptyList(),
    val depthStencilFormat: GpuTextureFormat? = null,
    val vertexBuffers: List<GpuVertexBufferLayout> = emptyList(),
    val primitiveTopology: GpuPrimitiveTopology = GpuPrimitiveTopology.TRIANGLE_LIST,
    val frontFace: GpuFrontFace = GpuFrontFace.CCW,
    val cullMode: GpuCullMode = GpuCullMode.NONE,
    val bindGroupLayouts: List<GpuBindGroupLayout> = emptyList()
)

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
