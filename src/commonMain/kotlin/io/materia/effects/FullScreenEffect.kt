/**
 * FullScreenEffect - High-level API for fullscreen shader effects
 * 
 * Provides a simplified interface for creating fullscreen post-processing
 * and background effects using WGSL shaders. Automatically generates
 * an optimized fullscreen triangle vertex shader.
 * 
 * Features:
 * - Single fullscreen triangle (3 vertices, no vertex buffer needed)
 * - Automatic UV coordinate generation
 * - Integration with UniformBlock for type-safe uniforms
 * - Configurable blend modes and clear colors
 */
package io.materia.effects

/**
 * Blend modes for fullscreen effects
 */
enum class BlendMode {
    /** No blending, fully opaque */
    OPAQUE,
    /** Standard alpha blending: src * srcAlpha + dst * (1 - srcAlpha) */
    ALPHA_BLEND,
    /** Additive blending: src + dst */
    ADDITIVE,
    /** Multiply blending: src * dst */
    MULTIPLY,
    /** Screen blending: 1 - (1-src) * (1-dst), lightens the image */
    SCREEN,
    /**
     * Overlay blending: combines multiply and screen based on base color luminance.
     * Note: True overlay blending cannot be achieved with fixed-function blend states alone.
     * This maps to MULTIPLY as an approximation. For accurate overlay, use shader-based
     * implementation with WGSLLib.Color functions.
     */
    OVERLAY,
    /** Premultiplied alpha: src + dst * (1 - srcAlpha) */
    PREMULTIPLIED_ALPHA
}

/**
 * Clear color for render passes
 */
data class ClearColor(
    val r: Float = 0f,
    val g: Float = 0f,
    val b: Float = 0f,
    val a: Float = 1f
) {
    companion object {
        val BLACK = ClearColor(0f, 0f, 0f, 1f)
        val TRANSPARENT = ClearColor(0f, 0f, 0f, 0f)
        val WHITE = ClearColor(1f, 1f, 1f, 1f)
    }
}

/**
 * A fullscreen shader effect that renders to the entire canvas.
 * Uses an optimized single-triangle approach (no vertex buffer needed).
 * 
 * @property fragmentShader The WGSL fragment shader code
 * @property uniforms The uniform block layout (optional)
 * @property blendMode The blend mode for rendering
 * @property clearColor The clear color for the render pass
 */
class FullScreenEffect(
    /** The WGSL fragment shader code */
    val fragmentShader: String,
    /** The uniform block for this effect */
    val uniforms: UniformBlock = UniformBlock.empty(),
    /** Blend mode for rendering */
    val blendMode: BlendMode = BlendMode.OPAQUE,
    /** Clear color for the render pass */
    val clearColor: ClearColor = ClearColor.BLACK
) {
    
    /** Buffer for uniform data */
    val uniformBuffer: FloatArray = uniforms.createBuffer()
    
    /** Updater for modifying uniform values */
    private val uniformUpdater: UniformUpdater = uniforms.createUpdater(uniformBuffer)
    
    /** Whether this effect has been disposed */
    var isDisposed: Boolean = false
        private set
    
    /**
     * The automatically generated vertex shader for fullscreen rendering.
     * Uses an optimized single-triangle approach.
     */
    val vertexShader: String = FULLSCREEN_VERTEX_SHADER
    
    /**
     * Update uniform values using the DSL
     */
    fun updateUniforms(block: UniformUpdater.() -> Unit) {
        uniformUpdater.block()
    }
    
    /**
     * Generate the complete WGSL shader module including:
     * - Uniform struct (if uniforms are defined)
     * - Vertex shader
     * - Fragment shader
     */
    fun generateShaderModule(): String = buildString {
        // Uniform struct and binding (if defined)
        if (uniforms.size > 0) {
            appendLine(uniforms.toWGSL("Uniforms"))
            appendLine()
            appendLine("@group(0) @binding(0) var<uniform> u: Uniforms;")
            appendLine()
        }
        
        // Vertex output struct
        appendLine(VERTEX_OUTPUT_STRUCT)
        appendLine()
        
        // Vertex shader
        appendLine(vertexShader)
        appendLine()
        
        // Fragment shader
        appendLine(fragmentShader)
    }
    
    /**
     * Release resources held by this effect
     */
    fun dispose() {
        isDisposed = true
    }
    
    companion object {
        /**
         * Optimized fullscreen vertex shader using a single triangle.
         * Covers the entire screen with just 3 vertices, no vertex buffer needed.
         * 
         * The triangle vertices are calculated using bit manipulation:
         * - Vertex 0: (-1, -1) -> uv (0, 0)
         * - Vertex 1: (3, -1)  -> uv (2, 0)
         * - Vertex 2: (-1, 3)  -> uv (0, 2)
         */
        const val FULLSCREEN_VERTEX_SHADER = """
@vertex
fn vs_main(@builtin(vertex_index) vertex_index: u32) -> VertexOutput {
    // Single triangle covering entire screen
    let x = f32((vertex_index << 1u) & 2u);
    let y = f32(vertex_index & 2u);
    
    var output: VertexOutput;
    output.position = vec4<f32>(x * 2.0 - 1.0, 1.0 - y * 2.0, 0.0, 1.0);
    output.uv = vec2<f32>(x, y);
    return output;
}"""
        
        /**
         * Vertex output struct shared between vertex and fragment shaders
         */
        const val VERTEX_OUTPUT_STRUCT = """
struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
}"""
    }
}

/**
 * Builder for creating FullScreenEffect instances with a fluent API
 */
class FullScreenEffectBuilder {
    /** The fragment shader code */
    var fragmentShader: String = ""
    
    /** The blend mode */
    var blendMode: BlendMode = BlendMode.OPAQUE
    
    /** The clear color */
    var clearColor: ClearColor = ClearColor.BLACK
    
    /** The uniform block (built from uniforms DSL) */
    private var uniformBlock: UniformBlock = UniformBlock.empty()
    
    /**
     * Define uniforms using the DSL
     */
    fun uniforms(block: UniformBlockBuilder.() -> Unit) {
        uniformBlock = uniformBlock(block)
    }
    
    /**
     * Build the FullScreenEffect
     */
    internal fun build(): FullScreenEffect {
        require(fragmentShader.isNotBlank()) { "Fragment shader is required" }
        
        return FullScreenEffect(
            fragmentShader = fragmentShader,
            uniforms = uniformBlock,
            blendMode = blendMode,
            clearColor = clearColor
        )
    }
}

/**
 * DSL function to create a FullScreenEffect
 */
fun fullScreenEffect(block: FullScreenEffectBuilder.() -> Unit): FullScreenEffect {
    val builder = FullScreenEffectBuilder()
    builder.block()
    return builder.build()
}
