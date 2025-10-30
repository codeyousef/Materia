package io.materia.renderer.state

import io.materia.camera.Viewport
import io.materia.material.CullFace

/**
 * Render state definitions for GPU state management.
 * Contains state data structures and enumerations.
 */

/**
 * GPU render state
 */
data class RenderState(
    // Depth state
    val depthTest: Boolean = true,
    val depthWrite: Boolean = true,
    val depthFunction: CompareFunction = CompareFunction.LESS,

    // Stencil state
    val stencilTest: Boolean = false,
    val stencilWrite: Boolean = false,
    val stencilFunction: CompareFunction = CompareFunction.ALWAYS,
    val stencilRef: Int = 0,
    val stencilMask: Int = 0xFF,
    val stencilFail: StencilOperation = StencilOperation.KEEP,
    val stencilZFail: StencilOperation = StencilOperation.KEEP,
    val stencilZPass: StencilOperation = StencilOperation.KEEP,

    // Blend state
    val blending: Boolean = false,
    val blendSrc: BlendFactor = BlendFactor.ONE,
    val blendDst: BlendFactor = BlendFactor.ZERO,
    val blendEquation: BlendEquation = BlendEquation.ADD,
    val blendSrcAlpha: BlendFactor = BlendFactor.ONE,
    val blendDstAlpha: BlendFactor = BlendFactor.ZERO,
    val blendEquationAlpha: BlendEquation = BlendEquation.ADD,

    // Culling state
    val cullFace: CullFace = CullFace.BACK,
    val frontFace: FrontFace = FrontFace.CCW,

    // Color state
    val colorWrite: ColorWrite = ColorWrite.ALL,

    // Polygon state
    val polygonMode: PolygonMode = PolygonMode.FILL,
    val lineWidth: Float = 1f,

    // Viewport state
    val viewport: Viewport = Viewport(0, 0, 1, 1),
    val scissorTest: Boolean = false,
    val scissorRect: Viewport = Viewport(0, 0, 1, 1)
)

/**
 * Comparison functions for depth and stencil tests
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
 * Stencil operations
 */
enum class StencilOperation {
    KEEP,
    ZERO,
    REPLACE,
    INCREMENT,
    INCREMENT_WRAP,
    DECREMENT,
    DECREMENT_WRAP,
    INVERT
}

/**
 * Blend factors
 */
enum class BlendFactor {
    ZERO,
    ONE,
    SRC_COLOR,
    ONE_MINUS_SRC_COLOR,
    DST_COLOR,
    ONE_MINUS_DST_COLOR,
    SRC_ALPHA,
    ONE_MINUS_SRC_ALPHA,
    DST_ALPHA,
    ONE_MINUS_DST_ALPHA,
    CONSTANT_COLOR,
    ONE_MINUS_CONSTANT_COLOR,
    CONSTANT_ALPHA,
    ONE_MINUS_CONSTANT_ALPHA,
    SRC_ALPHA_SATURATE
}

/**
 * Blend equations
 */
enum class BlendEquation {
    ADD,
    SUBTRACT,
    REVERSE_SUBTRACT,
    MIN,
    MAX
}

/**
 * Color write masks
 */
enum class ColorWrite {
    NONE,
    RED,
    GREEN,
    BLUE,
    ALPHA,
    RGB,
    ALL
}

/**
 * Polygon rendering modes
 */
enum class PolygonMode {
    POINT,
    LINE,
    FILL
}

/**
 * Front face winding order
 */
enum class FrontFace {
    CW,  // Clockwise
    CCW  // Counter-clockwise
}
