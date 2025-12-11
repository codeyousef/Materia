/**
 * WebGL-specific uniform handling utilities.
 *
 * This module provides helper functions for managing uniforms in WebGL shaders,
 * including automatic location caching and common uniform patterns like
 * time, resolution, and mouse position.
 */
package io.materia.renderer.webgl

import org.khronos.webgl.Float32Array
import org.khronos.webgl.WebGLProgram
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLUniformLocation

// Extension function for indexed assignment on Float32Array
private fun Float32Array.put(index: Int, value: Float) {
    asDynamic()[index] = value
}

/**
 * Common uniforms for fullscreen effects.
 *
 * This class manages frequently-used uniforms like time, resolution, and mouse
 * with automatic location caching.
 */
class WebGLEffectUniforms(
    private val gl: WebGLRenderingContext,
    private val program: WebGLProgram
) {
    // Cached uniform locations
    private var timeLocation: WebGLUniformLocation? = null
    private var deltaTimeLocation: WebGLUniformLocation? = null
    private var resolutionLocation: WebGLUniformLocation? = null
    private var mouseLocation: WebGLUniformLocation? = null
    private var mouseDownLocation: WebGLUniformLocation? = null
    private var frameLocation: WebGLUniformLocation? = null

    // Current values
    private var time: Float = 0f
    private var deltaTime: Float = 0f
    private var resolutionX: Float = 0f
    private var resolutionY: Float = 0f
    private var mouseX: Float = 0f
    private var mouseY: Float = 0f
    private var mouseDown: Boolean = false
    private var frame: Int = 0

    init {
        cacheLocations()
    }

    private fun cacheLocations() {
        timeLocation = gl.getUniformLocation(program, "u_time")
        deltaTimeLocation = gl.getUniformLocation(program, "u_deltaTime")
        resolutionLocation = gl.getUniformLocation(program, "u_resolution")
        mouseLocation = gl.getUniformLocation(program, "u_mouse")
        mouseDownLocation = gl.getUniformLocation(program, "u_mouseDown")
        frameLocation = gl.getUniformLocation(program, "u_frame")
    }

    /**
     * Set the time uniform.
     */
    fun setTime(value: Float) {
        time = value
    }

    /**
     * Set the delta time uniform.
     */
    fun setDeltaTime(value: Float) {
        deltaTime = value
    }

    /**
     * Set the resolution uniform.
     */
    fun setResolution(width: Float, height: Float) {
        resolutionX = width
        resolutionY = height
    }

    /**
     * Set the mouse position uniform (normalized 0-1 coordinates).
     */
    fun setMouse(x: Float, y: Float) {
        mouseX = x
        mouseY = y
    }

    /**
     * Set the mouse down state uniform.
     */
    fun setMouseDown(down: Boolean) {
        mouseDown = down
    }

    /**
     * Set the frame count uniform.
     */
    fun setFrame(count: Int) {
        frame = count
    }

    /**
     * Upload all uniforms to the GPU.
     */
    fun upload() {
        gl.useProgram(program)

        timeLocation?.let { gl.uniform1f(it, time) }
        deltaTimeLocation?.let { gl.uniform1f(it, deltaTime) }
        resolutionLocation?.let { gl.uniform2f(it, resolutionX, resolutionY) }
        mouseLocation?.let { gl.uniform2f(it, mouseX, mouseY) }
        mouseDownLocation?.let { gl.uniform1i(it, if (mouseDown) 1 else 0) }
        frameLocation?.let { gl.uniform1i(it, frame) }
    }
}

/**
 * Uniform setter helper that manages custom uniforms by name.
 */
class WebGLUniformSetter(
    private val gl: WebGLRenderingContext,
    private val program: WebGLProgram
) {
    private val locations = mutableMapOf<String, WebGLUniformLocation?>()

    /**
     * Get or cache a uniform location.
     */
    fun getLocation(name: String): WebGLUniformLocation? {
        return locations.getOrPut(name) {
            gl.getUniformLocation(program, name)
        }
    }

    /**
     * Set a float uniform.
     */
    fun setFloat(name: String, value: Float) {
        getLocation(name)?.let { gl.uniform1f(it, value) }
    }

    /**
     * Set an integer uniform.
     */
    fun setInt(name: String, value: Int) {
        getLocation(name)?.let { gl.uniform1i(it, value) }
    }

    /**
     * Set a vec2 uniform.
     */
    fun setVec2(name: String, x: Float, y: Float) {
        getLocation(name)?.let { gl.uniform2f(it, x, y) }
    }

    /**
     * Set a vec3 uniform.
     */
    fun setVec3(name: String, x: Float, y: Float, z: Float) {
        getLocation(name)?.let { gl.uniform3f(it, x, y, z) }
    }

    /**
     * Set a vec4 uniform.
     */
    fun setVec4(name: String, x: Float, y: Float, z: Float, w: Float) {
        getLocation(name)?.let { gl.uniform4f(it, x, y, z, w) }
    }

    /**
     * Set a mat3 uniform.
     */
    fun setMat3(name: String, values: FloatArray) {
        require(values.size >= 9) { "Mat3 requires 9 float values" }
        getLocation(name)?.let {
            val mat3 = Float32Array(9)
            for (i in 0 until 9) {
                mat3.put(i, values[i])
            }
            gl.uniformMatrix3fv(it, false, mat3)
        }
    }

    /**
     * Set a mat4 uniform.
     */
    fun setMat4(name: String, values: FloatArray) {
        require(values.size >= 16) { "Mat4 requires 16 float values" }
        getLocation(name)?.let {
            val mat4 = Float32Array(16)
            for (i in 0 until 16) {
                mat4.put(i, values[i])
            }
            gl.uniformMatrix4fv(it, false, mat4)
        }
    }

    /**
     * Set a float array uniform.
     */
    fun setFloatArray(name: String, values: FloatArray) {
        getLocation(name)?.let {
            val arr = Float32Array(values.size)
            for (i in values.indices) {
                arr.put(i, values[i])
            }
            gl.uniform1fv(it, arr)
        }
    }

    /**
     * Set a vec2 array uniform.
     */
    fun setVec2Array(name: String, values: FloatArray) {
        getLocation(name)?.let {
            val arr = Float32Array(values.size)
            for (i in values.indices) {
                arr.put(i, values[i])
            }
            gl.uniform2fv(it, arr)
        }
    }

    /**
     * Set a vec3 array uniform.
     */
    fun setVec3Array(name: String, values: FloatArray) {
        getLocation(name)?.let {
            val arr = Float32Array(values.size)
            for (i in values.indices) {
                arr.put(i, values[i])
            }
            gl.uniform3fv(it, arr)
        }
    }

    /**
     * Set a vec4 array uniform.
     */
    fun setVec4Array(name: String, values: FloatArray) {
        getLocation(name)?.let {
            val arr = Float32Array(values.size)
            for (i in values.indices) {
                arr.put(i, values[i])
            }
            gl.uniform4fv(it, arr)
        }
    }

    /**
     * Clear cached locations (e.g., after recompiling shader).
     */
    fun clearCache() {
        locations.clear()
    }
}

/**
 * Standard GLSL uniform declarations for common effects.
 *
 * Use these in your fragment shader to receive standard uniforms:
 *
 * ```glsl
 * precision mediump float;
 *
 * // Include standard uniforms
 * uniform float u_time;
 * uniform vec2 u_resolution;
 * uniform vec2 u_mouse;
 *
 * void main() {
 *     vec2 uv = gl_FragCoord.xy / u_resolution;
 *     // ...
 * }
 * ```
 */
object GLSLUniforms {
    /**
     * Standard time uniform declaration.
     */
    const val TIME = "uniform float u_time;"

    /**
     * Standard delta time uniform declaration.
     */
    const val DELTA_TIME = "uniform float u_deltaTime;"

    /**
     * Standard resolution uniform declaration.
     */
    const val RESOLUTION = "uniform vec2 u_resolution;"

    /**
     * Standard mouse position uniform declaration.
     */
    const val MOUSE = "uniform vec2 u_mouse;"

    /**
     * Standard mouse down state uniform declaration.
     */
    const val MOUSE_DOWN = "uniform int u_mouseDown;"

    /**
     * Standard frame count uniform declaration.
     */
    const val FRAME = "uniform int u_frame;"

    /**
     * All standard uniforms combined.
     */
    const val ALL = """
uniform float u_time;
uniform float u_deltaTime;
uniform vec2 u_resolution;
uniform vec2 u_mouse;
uniform int u_mouseDown;
uniform int u_frame;
"""

    /**
     * Input texture uniform for post-processing passes.
     */
    const val INPUT_TEXTURE = """
uniform sampler2D u_inputTexture;
"""
}
