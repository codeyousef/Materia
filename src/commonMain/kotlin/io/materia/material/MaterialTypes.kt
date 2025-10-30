package io.materia.material

import io.materia.core.math.Plane

/**
 * Material blending modes
 * Three.js r180 compatible blending enum
 */
enum class Blending {
    NoBlending,
    NormalBlending,
    AdditiveBlending,
    SubtractiveBlending,
    MultiplyBlending,
    CustomBlending
}

/**
 * Blending factor constants for custom blending
 */
enum class BlendingFactor {
    ZeroFactor,
    OneFactor,
    SrcColorFactor,
    OneMinusSrcColorFactor,
    SrcAlphaFactor,
    OneMinusSrcAlphaFactor,
    DstAlphaFactor,
    OneMinusDstAlphaFactor,
    DstColorFactor,
    OneMinusDstColorFactor,
    SrcAlphaSaturateFactor
}

/**
 * Blending equation for custom blending
 */
enum class BlendingEquation {
    AddEquation,
    SubtractEquation,
    ReverseSubtractEquation,
    MinEquation,
    MaxEquation
}

/**
 * Depth test function
 */
enum class DepthMode {
    NeverDepth,
    AlwaysDepth,
    LessDepth,
    LessEqualDepth,
    EqualDepth,
    GreaterEqualDepth,
    GreaterDepth,
    NotEqualDepth
}

/**
 * Stencil test function
 */
enum class StencilFunc {
    NeverStencilFunc,
    LessStencilFunc,
    EqualStencilFunc,
    LessEqualStencilFunc,
    GreaterStencilFunc,
    NotEqualStencilFunc,
    GreaterEqualStencilFunc,
    AlwaysStencilFunc
}

/**
 * Stencil operation
 */
enum class StencilOp {
    ZeroStencilOp,
    KeepStencilOp,
    ReplaceStencilOp,
    IncrementStencilOp,
    DecrementStencilOp,
    IncrementWrapStencilOp,
    DecrementWrapStencilOp,
    InvertStencilOp
}

/**
 * Texture combine operation
 */
enum class Combine {
    MultiplyOperation,
    MixOperation,
    AddOperation
}

/**
 * Normal map type
 */
enum class NormalMapType {
    TangentSpaceNormalMap,
    ObjectSpaceNormalMap
}

/**
 * Depth packing format
 */
enum class DepthPacking {
    BasicDepthPacking,
    RGBADepthPacking
}

/**
 * Shader precision
 */
enum class Precision {
    HighP,
    MediumP,
    LowP
}

/**
 * Material side rendering
 */
enum class Side {
    FrontSide,
    BackSide,
    DoubleSide
}

/**
 * Shader uniform value holder
 */
data class Uniform(
    var value: Any,
    val type: UniformType? = null
)

/**
 * Uniform type for explicit type specification
 */
enum class UniformType {
    FLOAT,
    VEC2,
    VEC3,
    VEC4,
    INT,
    IVEC2,
    IVEC3,
    IVEC4,
    BOOL,
    MAT2,
    MAT3,
    MAT4,
    SAMPLER2D,
    SAMPLERCUBE
}

/**
 * Shader extensions configuration
 */
data class ShaderExtensions(
    var derivatives: Boolean = false,
    var fragDepth: Boolean = false,
    var drawBuffers: Boolean = false,
    var shaderTextureLOD: Boolean = false
)
