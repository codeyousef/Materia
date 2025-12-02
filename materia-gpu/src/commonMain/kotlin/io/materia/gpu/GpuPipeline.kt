package io.materia.gpu


/** GPU shader stages for pipeline binding. */
enum class GpuShaderStage {
    /** Vertex processing stage. */
    VERTEX,
    /** Fragment (pixel) processing stage. */
    FRAGMENT,
    /** Compute stage. */
    COMPUTE
}

/**
 * Configuration for creating a shader module.
 *
 * @property label Optional debug label.
 * @property code Shader source code (WGSL or SPIR-V bytes as base64).
 * @property sourceLanguage Language of the shader source.
 */
data class GpuShaderModuleDescriptor(
    val label: String? = null,
    val code: String,
    val sourceLanguage: ShaderSourceLanguage = ShaderSourceLanguage.WGSL
)

/** Shader source language. */
enum class ShaderSourceLanguage {
    /** WebGPU Shading Language (text). */
    WGSL,
    /** SPIR-V binary format. */
    SPIRV
}

/**
 * Configuration for creating a bind group layout.
 *
 * @property label Optional debug label.
 * @property entries List of binding entries in this layout.
 */
data class GpuBindGroupLayoutDescriptor(
    val label: String? = null,
    val entries: List<GpuBindGroupLayoutEntry> = emptyList()
)

/**
 * Describes a single binding slot in a bind group layout.
 *
 * @property binding Slot index in the bind group.
 * @property visibility Which shader stages can access this binding.
 * @property resourceType Type of resource expected at this binding.
 */
data class GpuBindGroupLayoutEntry(
    val binding: Int,
    val visibility: Set<GpuShaderStage>,
    val resourceType: GpuBindingResourceType
)

/**
 * Configuration for creating a bind group.
 *
 * @property label Optional debug label.
 * @property layout The layout this bind group conforms to.
 * @property entries List of resource bindings.
 */
data class GpuBindGroupDescriptor(
    val label: String? = null,
    val layout: GpuBindGroupLayout,
    val entries: List<GpuBindGroupEntry> = emptyList()
)

/**
 * A resource binding within a bind group.
 *
 * @property binding Slot index matching the layout.
 * @property resource The bound resource.
 */
data class GpuBindGroupEntry(
    val binding: Int,
    val resource: GpuBindingResource
)

/** Types of resources that can be bound to shaders. */
enum class GpuBindingResourceType {
    /** Uniform buffer (read-only, small, frequently updated). */
    UNIFORM_BUFFER,
    /** Storage buffer (read/write, large). */
    STORAGE_BUFFER,
    /** Texture sampler. */
    SAMPLER,
    /** Texture for sampling. */
    TEXTURE
}

/**
 * A resource to bind to a shader.
 */
sealed class GpuBindingResource {
    /**
     * Buffer binding.
     *
     * @property buffer The buffer to bind.
     * @property offset Byte offset into the buffer.
     * @property size Byte size to bind (null = entire buffer).
     */
    data class Buffer(
        val buffer: GpuBuffer,
        val offset: Long = 0L,
        val size: Long? = null
    ) : GpuBindingResource()

    /**
     * Sampler binding.
     *
     * @property sampler The sampler to bind.
     */
    data class Sampler(
        val sampler: GpuSampler
    ) : GpuBindingResource()

    /**
     * Texture binding.
     *
     * @property textureView The texture view to bind.
     */
    data class Texture(
        val textureView: GpuTextureView
    ) : GpuBindingResource()
}

/** How vertex data advances through the buffer. */
enum class GpuVertexStepMode {
    /** Advance per vertex. */
    VERTEX,
    /** Advance per instance. */
    INSTANCE
}

/** Vertex attribute data formats. */
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

/**
 * Describes a single vertex attribute.
 *
 * @property shaderLocation Attribute location in the vertex shader.
 * @property format Data format of the attribute.
 * @property offset Byte offset within the vertex.
 */
data class GpuVertexAttribute(
    val shaderLocation: Int,
    val format: GpuVertexFormat,
    val offset: Int
)

/**
 * Describes the layout of a vertex buffer.
 *
 * @property arrayStride Bytes between consecutive vertices.
 * @property stepMode Per-vertex or per-instance.
 * @property attributes List of attributes in this buffer.
 */
data class GpuVertexBufferLayout(
    val arrayStride: Int,
    val stepMode: GpuVertexStepMode = GpuVertexStepMode.VERTEX,
    val attributes: List<GpuVertexAttribute> = emptyList()
)

/** Primitive topology for rasterization. */
enum class GpuPrimitiveTopology {
    /** Render disconnected points. */
    POINT_LIST,
    /** Render disconnected line segments. */
    LINE_LIST,
    /** Render connected line strip. */
    LINE_STRIP,
    /** Render disconnected triangles. */
    TRIANGLE_LIST,
    /** Render connected triangle strip. */
    TRIANGLE_STRIP
}

/** Winding order for front-facing triangles. */
enum class GpuFrontFace {
    /** Counter-clockwise vertices are front-facing. */
    CCW,
    /** Clockwise vertices are front-facing. */
    CW
}

/** Face culling mode. */
enum class GpuCullMode {
    /** No culling. */
    NONE,
    /** Cull front faces. */
    FRONT,
    /** Cull back faces. */
    BACK
}

/** Depth comparison functions. */
enum class GpuCompareFunction {
    /** Always pass. */
    ALWAYS,
    /** Pass if new depth < existing. */
    LESS,
    /** Pass if new depth <= existing. */
    LESS_EQUAL
}

/**
 * Depth/stencil state configuration.
 *
 * @property format Depth buffer format.
 * @property depthWriteEnabled Whether to write depth values.
 * @property depthCompare Comparison function for depth test.
 */
data class GpuDepthState(
    val format: GpuTextureFormat = GpuTextureFormat.DEPTH24_PLUS,
    val depthWriteEnabled: Boolean = true,
    val depthCompare: GpuCompareFunction = GpuCompareFunction.LESS
)

/** Blend mode presets. */
enum class GpuBlendMode {
    /** No blending. */
    DISABLED,
    /** Standard alpha blending. */
    ALPHA,
    /** Additive blending. */
    ADDITIVE
}

/**
 * Configuration for creating a render pipeline.
 *
 * @property label Optional debug label.
 * @property vertexShader Shader module for vertex stage.
 * @property fragmentShader Optional shader module for fragment stage.
 * @property colorFormats Formats of color attachments.
 * @property depthStencilFormat Optional depth/stencil format.
 * @property vertexBuffers Vertex buffer layouts.
 * @property primitiveTopology How vertices form primitives.
 * @property frontFace Winding order for front faces.
 * @property cullMode Which faces to cull.
 * @property bindGroupLayouts Layouts for bind groups.
 * @property depthState Optional depth/stencil configuration.
 * @property blendMode Alpha blending mode.
 */
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
    val bindGroupLayouts: List<GpuBindGroupLayout> = emptyList(),
    val depthState: GpuDepthState? = null,
    val blendMode: GpuBlendMode = GpuBlendMode.DISABLED
)

/**
 * Configuration for creating a compute pipeline.
 *
 * @property label Optional debug label.
 * @property shader Compute shader module.
 */
data class GpuComputePipelineDescriptor(
    val label: String? = null,
    val shader: GpuShaderModule
)

/** Compiled shader code ready for use in pipelines. */
expect class GpuShaderModule internal constructor(
    device: GpuDevice,
    descriptor: GpuShaderModuleDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuShaderModuleDescriptor
}

/** Defines the binding layout for a set of shader resources. */
expect class GpuBindGroupLayout internal constructor(
    device: GpuDevice,
    descriptor: GpuBindGroupLayoutDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuBindGroupLayoutDescriptor
}

/** Collection of resources bound together for shader access. */
expect class GpuBindGroup internal constructor(
    layout: GpuBindGroupLayout,
    descriptor: GpuBindGroupDescriptor
) {
    val layout: GpuBindGroupLayout
    val descriptor: GpuBindGroupDescriptor
}

/** Compiled render pipeline encapsulating shader stages and state. */
expect class GpuRenderPipeline internal constructor(
    device: GpuDevice,
    descriptor: GpuRenderPipelineDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuRenderPipelineDescriptor
}

/** Compiled compute pipeline for GPU compute workloads. */
expect class GpuComputePipeline internal constructor(
    device: GpuDevice,
    descriptor: GpuComputePipelineDescriptor
) {
    val device: GpuDevice
    val descriptor: GpuComputePipelineDescriptor
}
