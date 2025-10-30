package io.materia.renderer.webgpu

/**
 * Buffer creation descriptor.
 * T026: BufferDescriptor
 */
data class BufferDescriptor(
    val label: String? = null,
    val size: Int,
    val usage: Int, // GPUBufferUsageFlags
    val mappedAtCreation: Boolean = false
)

/**
 * Shader module creation descriptor.
 * T027: ShaderModuleDescriptor
 */
data class ShaderModuleDescriptor(
    val label: String? = null,
    val code: String,
    val stage: ShaderStage
)

/**
 * Shader stages.
 */
enum class ShaderStage {
    VERTEX,
    FRAGMENT,
    COMPUTE
}

/**
 * Render pipeline creation descriptor.
 * T028: RenderPipelineDescriptor
 */
data class RenderPipelineDescriptor(
    val label: String? = null,
    val vertexShader: String,
    val fragmentShader: String,
    val vertexLayouts: List<VertexBufferLayout>,
    val primitiveTopology: PrimitiveTopology = PrimitiveTopology.TRIANGLE_LIST,
    val cullMode: CullMode = CullMode.BACK,
    val frontFace: FrontFace = FrontFace.CCW,
    val depthStencilState: DepthStencilState? = null,
    val multisampleState: MultisampleState? = null,
    val colorTarget: ColorTargetDescriptor = ColorTargetDescriptor()
)

/**
 * Vertex buffer layout.
 */
data class VertexBufferLayout(
    val arrayStride: Int,
    val stepMode: VertexStepMode = VertexStepMode.VERTEX,
    val attributes: List<VertexAttribute>
)

/**
 * Vertex attribute.
 */
data class VertexAttribute(
    val format: VertexFormat,
    val offset: Int,
    val shaderLocation: Int
)

/**
 * Vertex formats.
 */
enum class VertexFormat(val size: Int) {
    FLOAT32(4),
    FLOAT32X2(8),
    FLOAT32X3(12),
    FLOAT32X4(16),
    UINT32(4),
    UINT32X2(8),
    UINT32X3(12),
    UINT32X4(16)
}

/**
 * Vertex step mode.
 */
enum class VertexStepMode {
    VERTEX,
    INSTANCE
}

/**
 * Primitive topology.
 */
enum class PrimitiveTopology {
    POINT_LIST,
    LINE_LIST,
    LINE_STRIP,
    TRIANGLE_LIST,
    TRIANGLE_STRIP
}

/**
 * Cull mode.
 */
enum class CullMode {
    NONE,
    FRONT,
    BACK
}

/**
 * Front face winding.
 */
enum class FrontFace {
    CCW,  // Counter-clockwise
    CW    // Clockwise
}

/**
 * Depth/stencil state.
 */
data class DepthStencilState(
    val format: TextureFormat = TextureFormat.DEPTH24_PLUS,
    val depthWriteEnabled: Boolean = true,
    val depthCompare: CompareFunction = CompareFunction.LESS
)

/**
 * Color target configuration including blending.
 */
data class ColorTargetDescriptor(
    val format: TextureFormat = TextureFormat.BGRA8_UNORM,
    val blendState: BlendState? = null,
    val writeMask: ColorWriteMask = ColorWriteMask.ALL
)

data class BlendState(
    val color: BlendComponent,
    val alpha: BlendComponent
)

data class BlendComponent(
    val srcFactor: BlendFactor,
    val dstFactor: BlendFactor,
    val operation: BlendOperation
)

enum class BlendFactor {
    ZERO,
    ONE,
    SRC,
    ONE_MINUS_SRC,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST,
    ONE_MINUS_DST,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA
}

enum class BlendOperation {
    ADD,
    SUBTRACT,
    REVERSE_SUBTRACT,
    MIN,
    MAX
}

enum class ColorWriteMask(val bits: Int) {
    NONE(0),
    RED(0x1),
    GREEN(0x2),
    BLUE(0x4),
    ALPHA(0x8),
    ALL(0xF)
}

/**
 * Multisample state.
 */
data class MultisampleState(
    val count: Int = 1,
    val mask: Int = 0xFFFFFFFF.toInt(),
    val alphaToCoverageEnabled: Boolean = false
)

/**
 * Texture formats.
 */
enum class TextureFormat {
    RGBA8_UNORM,
    RGBA8_SRGB,
    BGRA8_UNORM,
    BGRA8_SRGB,
    DEPTH24_PLUS,
    DEPTH32_FLOAT
}

/**
 * Comparison functions.
 */
enum class CompareFunction {
    NEVER,
    LESS,
    EQUAL,
    LESS_EQUAL,
    GREATER,
    NOT_EQUAL,
    GREATER_EQUAL,
    ALWAYS
}

/**
 * GPU buffer usage flags.
 */
object GPUBufferUsage {
    const val MAP_READ = 0x0001
    const val MAP_WRITE = 0x0002
    const val COPY_SRC = 0x0004
    const val COPY_DST = 0x0008
    const val INDEX = 0x0010
    const val VERTEX = 0x0020
    const val UNIFORM = 0x0040
    const val STORAGE = 0x0080
    const val INDIRECT = 0x0100
    const val QUERY_RESOLVE = 0x0200
}

/**
 * GPU shader stage flags.
 */
object GPUShaderStage {
    const val VERTEX = 0x1
    const val FRAGMENT = 0x2
    const val COMPUTE = 0x4
}

/**
 * GPU texture usage flags.
 */
object GPUTextureUsage {
    const val COPY_SRC = 0x01
    const val COPY_DST = 0x02
    const val TEXTURE_BINDING = 0x04
    const val STORAGE_BINDING = 0x08
    const val RENDER_ATTACHMENT = 0x10
}
