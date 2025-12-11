/**
 * WebGLFullScreenEffect - High-level API for fullscreen shader effects using WebGL/GLSL
 *
 * Provides a simplified interface for creating fullscreen post-processing
 * and background effects using GLSL shaders. This is the WebGL equivalent
 * of the WebGPU FullScreenEffect class.
 *
 * Features:
 * - Single fullscreen triangle (3 vertices, no vertex buffer needed)
 * - Automatic UV coordinate generation
 * - Integration with UniformBlock for type-safe uniforms
 * - Configurable blend modes and clear colors
 * - Automatic WebGL uniform location management
 */
package io.materia.renderer.webgl

import io.materia.effects.BlendMode
import io.materia.effects.ClearColor
import io.materia.effects.UniformBlock
import io.materia.effects.UniformBlockBuilder
import io.materia.effects.UniformType
import io.materia.effects.UniformUpdater
import io.materia.effects.uniformBlock
import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.ARRAY_BUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.BLEND
import org.khronos.webgl.WebGLRenderingContext.Companion.COMPILE_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.DST_COLOR
import org.khronos.webgl.WebGLRenderingContext.Companion.FLOAT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAGMENT_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.LINK_STATUS
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE_MINUS_SRC_ALPHA
import org.khronos.webgl.WebGLRenderingContext.Companion.ONE_MINUS_SRC_COLOR
import org.khronos.webgl.WebGLRenderingContext.Companion.SRC_ALPHA
import org.khronos.webgl.WebGLRenderingContext.Companion.STATIC_DRAW
import org.khronos.webgl.WebGLRenderingContext.Companion.TRIANGLES
import org.khronos.webgl.WebGLRenderingContext.Companion.VERTEX_SHADER
import org.khronos.webgl.WebGLRenderingContext.Companion.ZERO
import org.khronos.webgl.WebGLShader
import org.khronos.webgl.WebGLUniformLocation
import org.khronos.webgl.WebGLBuffer

// Extension function for indexed assignment on Float32Array
private fun Float32Array.put(index: Int, value: Float) {
    asDynamic()[index] = value
}

/**
 * A fullscreen shader effect that renders to the entire canvas using WebGL/GLSL.
 * Uses an optimized single-triangle approach (no vertex buffer needed).
 *
 * @property fragmentShader The GLSL fragment shader code
 * @property uniforms The uniform block layout (optional)
 * @property blendMode The blend mode for rendering
 * @property clearColor The clear color for the render pass
 */
class WebGLFullScreenEffect(
    /** The GLSL fragment shader code */
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

    /** Compiled WebGL program (set after compilation) */
    internal var program: WebGLProgram? = null
        private set

    /** Cached uniform locations for efficient updates */
    internal val uniformLocations = mutableMapOf<String, WebGLUniformLocation?>()

    /** Vertex buffer for the fullscreen triangle */
    internal var vertexBuffer: WebGLBuffer? = null
        private set

    /** Position attribute location */
    internal var positionLocation: Int = -1
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
     * Compile and set up the shader program
     *
     * @param gl The WebGL rendering context
     * @return true if compilation was successful
     */
    fun compile(gl: WebGLRenderingContext): Boolean {
        if (program != null) return true

        try {
            // Compile vertex shader
            val vs = gl.createShader(VERTEX_SHADER) ?: return false
            gl.shaderSource(vs, vertexShader)
            gl.compileShader(vs)
            if (gl.getShaderParameter(vs, COMPILE_STATUS) != true) {
                console.error("WebGL vertex shader error: ${gl.getShaderInfoLog(vs)}")
                gl.deleteShader(vs)
                return false
            }

            // Compile fragment shader
            val fs = gl.createShader(FRAGMENT_SHADER) ?: return false
            gl.shaderSource(fs, fragmentShader)
            gl.compileShader(fs)
            if (gl.getShaderParameter(fs, COMPILE_STATUS) != true) {
                console.error("WebGL fragment shader error: ${gl.getShaderInfoLog(fs)}")
                gl.deleteShader(vs)
                gl.deleteShader(fs)
                return false
            }

            // Link program
            val prog = gl.createProgram() ?: return false
            gl.attachShader(prog, vs)
            gl.attachShader(prog, fs)
            gl.linkProgram(prog)

            if (gl.getProgramParameter(prog, LINK_STATUS) != true) {
                console.error("WebGL program link error: ${gl.getProgramInfoLog(prog)}")
                gl.deleteShader(vs)
                gl.deleteShader(fs)
                gl.deleteProgram(prog)
                return false
            }

            // Clean up shaders (they're now part of the program)
            gl.deleteShader(vs)
            gl.deleteShader(fs)

            program = prog
            positionLocation = gl.getAttribLocation(prog, "aPosition")

            // Cache uniform locations
            cacheUniformLocations(gl)

            // Create vertex buffer for fullscreen triangle
            createVertexBuffer(gl)

            return true
        } catch (e: Exception) {
            console.error("WebGL effect compilation failed: ${e.message}")
            return false
        }
    }

    /**
     * Cache uniform locations for all defined uniforms
     */
    private fun cacheUniformLocations(gl: WebGLRenderingContext) {
        val prog = program ?: return

        // Cache locations for all uniforms defined in the block
        uniforms.layout.forEach { field ->
            uniformLocations[field.name] = gl.getUniformLocation(prog, "u_${field.name}")
        }
    }

    /**
     * Create the vertex buffer for the fullscreen triangle
     */
    private fun createVertexBuffer(gl: WebGLRenderingContext) {
        val buffer = gl.createBuffer() ?: return

        // Fullscreen triangle vertices (clip space)
        // Vertex 0: (-1, -1)
        // Vertex 1: (3, -1)
        // Vertex 2: (-1, 3)
        val vertices = Float32Array(6)
        vertices.put(0, -1.0f)
        vertices.put(1, -1.0f)
        vertices.put(2, 3.0f)
        vertices.put(3, -1.0f)
        vertices.put(4, -1.0f)
        vertices.put(5, 3.0f)

        gl.bindBuffer(ARRAY_BUFFER, buffer)
        gl.bufferData(ARRAY_BUFFER, vertices, STATIC_DRAW)
        gl.bindBuffer(ARRAY_BUFFER, null)

        vertexBuffer = buffer
    }

    /**
     * Upload uniform values to the GPU
     */
    fun uploadUniforms(gl: WebGLRenderingContext) {
        val prog = program ?: return
        gl.useProgram(prog)

        uniforms.layout.forEach { field ->
            val location = uniformLocations[field.name] ?: return@forEach
            val index = field.offset / 4

            when (field.type) {
                UniformType.FLOAT -> gl.uniform1f(location, uniformBuffer[index])
                UniformType.INT, UniformType.UINT -> gl.uniform1i(location, Float.fromBits(uniformBuffer[index].toRawBits()).toInt())
                UniformType.VEC2 -> gl.uniform2f(location, uniformBuffer[index], uniformBuffer[index + 1])
                UniformType.VEC3 -> gl.uniform3f(location, uniformBuffer[index], uniformBuffer[index + 1], uniformBuffer[index + 2])
                UniformType.VEC4 -> gl.uniform4f(location, uniformBuffer[index], uniformBuffer[index + 1], uniformBuffer[index + 2], uniformBuffer[index + 3])
                UniformType.MAT3 -> {
                    // Extract 3x3 from the padded layout (skip padding bytes)
                    val mat3 = Float32Array(9)
                    mat3.put(0, uniformBuffer[index + 0])
                    mat3.put(1, uniformBuffer[index + 1])
                    mat3.put(2, uniformBuffer[index + 2])
                    mat3.put(3, uniformBuffer[index + 4])
                    mat3.put(4, uniformBuffer[index + 5])
                    mat3.put(5, uniformBuffer[index + 6])
                    mat3.put(6, uniformBuffer[index + 8])
                    mat3.put(7, uniformBuffer[index + 9])
                    mat3.put(8, uniformBuffer[index + 10])
                    gl.uniformMatrix3fv(location, false, mat3)
                }
                UniformType.MAT4 -> {
                    val mat4 = Float32Array(16)
                    for (i in 0 until 16) {
                        mat4.put(i, uniformBuffer[index + i])
                    }
                    gl.uniformMatrix4fv(location, false, mat4)
                }
                else -> {} // Arrays handled separately if needed
            }
        }
    }

    /**
     * Set up blend mode for rendering
     */
    fun setupBlendMode(gl: WebGLRenderingContext) {
        when (blendMode) {
            BlendMode.OPAQUE -> {
                gl.disable(BLEND)
            }
            BlendMode.ALPHA_BLEND -> {
                gl.enable(BLEND)
                gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.ADDITIVE -> {
                gl.enable(BLEND)
                gl.blendFunc(ONE, ONE)
            }
            BlendMode.MULTIPLY -> {
                gl.enable(BLEND)
                gl.blendFunc(DST_COLOR, ZERO)
            }
            BlendMode.SCREEN -> {
                gl.enable(BLEND)
                gl.blendFunc(ONE, ONE_MINUS_SRC_COLOR)
            }
            BlendMode.OVERLAY -> {
                // Overlay approximated as multiply (true overlay requires shader)
                gl.enable(BLEND)
                gl.blendFunc(DST_COLOR, ZERO)
            }
            BlendMode.PREMULTIPLIED_ALPHA -> {
                gl.enable(BLEND)
                gl.blendFunc(ONE, ONE_MINUS_SRC_ALPHA)
            }
        }
    }

    /**
     * Render the effect
     */
    fun render(gl: WebGLRenderingContext) {
        if (isDisposed) return
        val prog = program ?: return

        gl.useProgram(prog)

        // Set up blend mode
        setupBlendMode(gl)

        // Upload uniforms
        uploadUniforms(gl)

        // Bind vertex buffer and set up attribute
        gl.bindBuffer(ARRAY_BUFFER, vertexBuffer)
        gl.enableVertexAttribArray(positionLocation)
        gl.vertexAttribPointer(positionLocation, 2, FLOAT, false, 0, 0)

        // Draw the fullscreen triangle
        gl.drawArrays(TRIANGLES, 0, 3)

        // Clean up
        gl.disableVertexAttribArray(positionLocation)
        gl.bindBuffer(ARRAY_BUFFER, null)
    }

    /**
     * Release resources held by this effect
     */
    fun dispose(gl: WebGLRenderingContext) {
        if (isDisposed) return
        isDisposed = true

        program?.let { gl.deleteProgram(it) }
        vertexBuffer?.let { gl.deleteBuffer(it) }

        program = null
        vertexBuffer = null
        uniformLocations.clear()
    }

    companion object {
        /**
         * Optimized fullscreen vertex shader using a single triangle.
         * Covers the entire screen with just 3 vertices.
         *
         * The triangle vertices are:
         * - Vertex 0: (-1, -1) -> uv (0, 1)
         * - Vertex 1: (3, -1)  -> uv (2, 1)
         * - Vertex 2: (-1, 3)  -> uv (0, -1)
         */
        const val FULLSCREEN_VERTEX_SHADER = """
attribute vec2 aPosition;
varying vec2 vUv;

void main() {
    // UV coordinates from clip space position
    vUv = aPosition * 0.5 + 0.5;
    // Flip Y for WebGL coordinate system
    vUv.y = 1.0 - vUv.y;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""
    }
}

/**
 * Builder for creating WebGLFullScreenEffect instances with a fluent API
 */
class WebGLFullScreenEffectBuilder {
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
     * Build the WebGLFullScreenEffect
     */
    internal fun build(): WebGLFullScreenEffect {
        require(fragmentShader.isNotBlank()) { "Fragment shader is required" }

        return WebGLFullScreenEffect(
            fragmentShader = fragmentShader,
            uniforms = uniformBlock,
            blendMode = blendMode,
            clearColor = clearColor
        )
    }
}

/**
 * DSL function to create a WebGLFullScreenEffect
 */
fun webGLFullScreenEffect(block: WebGLFullScreenEffectBuilder.() -> Unit): WebGLFullScreenEffect {
    val builder = WebGLFullScreenEffectBuilder()
    builder.block()
    return builder.build()
}
