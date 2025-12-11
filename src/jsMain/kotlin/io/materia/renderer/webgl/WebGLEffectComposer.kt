/**
 * WebGLEffectComposer - Pass chain manager for WebGL fullscreen effects
 *
 * Manages a chain of post-processing passes using WebGL/GLSL shaders.
 * This is the WebGL equivalent of EffectComposer for WebGPU.
 *
 * Features:
 * - Pass chain management (add, remove, reorder)
 * - Ping-pong framebuffer system for multi-pass rendering
 * - Size propagation to all passes
 * - Enable/disable filtering
 * - Resource lifecycle management
 *
 * Usage:
 * ```kotlin
 * val composer = WebGLEffectComposer(gl, width = 1920, height = 1080)
 *
 * composer.addPass(webGLFullScreenEffect {
 *     fragmentShader = vignetteShader
 * })
 *
 * composer.addPass(webGLFullScreenEffect {
 *     fragmentShader = colorGradingShader
 * })
 *
 * // In render loop:
 * composer.render()
 * ```
 */
package io.materia.renderer.webgl

import org.khronos.webgl.WebGLFramebuffer
import org.khronos.webgl.WebGLRenderingContext
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_ATTACHMENT0
import org.khronos.webgl.WebGLRenderingContext.Companion.COLOR_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.DEPTH_BUFFER_BIT
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER
import org.khronos.webgl.WebGLRenderingContext.Companion.FRAMEBUFFER_COMPLETE
import org.khronos.webgl.WebGLRenderingContext.Companion.LINEAR
import org.khronos.webgl.WebGLRenderingContext.Companion.NEAREST
import org.khronos.webgl.WebGLRenderingContext.Companion.RGBA
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE0
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_2D
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MAG_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_MIN_FILTER
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_S
import org.khronos.webgl.WebGLRenderingContext.Companion.TEXTURE_WRAP_T
import org.khronos.webgl.WebGLRenderingContext.Companion.CLAMP_TO_EDGE
import org.khronos.webgl.WebGLRenderingContext.Companion.UNSIGNED_BYTE
import org.khronos.webgl.WebGLTexture

/**
 * A render pass wrapper for WebGL effects.
 *
 * This wraps a [WebGLFullScreenEffect] and tracks its state within a
 * [WebGLEffectComposer] chain.
 */
class WebGLEffectPass(
    /** The underlying effect */
    val effect: WebGLFullScreenEffect,
    /** Whether this pass requires input from the previous pass */
    val requiresInputTexture: Boolean = false,
    /** Whether to auto-update a "resolution" uniform on resize */
    val autoUpdateResolution: Boolean = false
) {
    /** Whether this pass is enabled */
    var enabled: Boolean = true

    /** Current width in pixels */
    var width: Int = 0
        private set

    /** Current height in pixels */
    var height: Int = 0
        private set

    /** Whether the uniform buffer has been modified */
    var isUniformBufferDirty: Boolean = false
        private set

    /** Whether this pass has been disposed */
    var isDisposed: Boolean = false
        private set

    /** Whether this pass marks the final output to screen */
    var renderToScreen: Boolean = false

    /** Cached input texture uniform location */
    internal var inputTextureLocation: org.khronos.webgl.WebGLUniformLocation? = null

    /**
     * Update uniform values using the DSL.
     */
    fun updateUniforms(block: io.materia.effects.UniformUpdater.() -> Unit) {
        effect.updateUniforms(block)
        isUniformBufferDirty = true
    }

    /**
     * Clears the dirty flag after GPU upload.
     */
    fun clearDirtyFlag() {
        isUniformBufferDirty = false
    }

    /**
     * Sets the size and optionally updates resolution uniform.
     */
    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height

        if (autoUpdateResolution) {
            val hasResolution = effect.uniforms.field("resolution") != null
            if (hasResolution) {
                effect.updateUniforms {
                    set("resolution", width.toFloat(), height.toFloat())
                }
                isUniformBufferDirty = true
            }
        }
    }

    /**
     * Compile this pass's shader program.
     */
    fun compile(gl: WebGLRenderingContext): Boolean {
        if (!effect.compile(gl)) return false

        // Cache input texture location if this pass requires input
        if (requiresInputTexture) {
            inputTextureLocation = gl.getUniformLocation(effect.program!!, "u_inputTexture")
        }

        return true
    }

    /**
     * Release resources.
     */
    fun dispose(gl: WebGLRenderingContext) {
        if (isDisposed) return
        isDisposed = true
        effect.dispose(gl)
    }

    companion object {
        /**
         * Create a pass from a builder.
         */
        fun create(
            requiresInputTexture: Boolean = false,
            autoUpdateResolution: Boolean = false,
            block: WebGLFullScreenEffectBuilder.() -> Unit
        ): WebGLEffectPass {
            val effect = webGLFullScreenEffect(block)
            return WebGLEffectPass(effect, requiresInputTexture, autoUpdateResolution)
        }
    }
}

/**
 * Manages a chain of WebGL post-processing effects.
 *
 * @property gl The WebGL rendering context
 * @property width Current width in pixels
 * @property height Current height in pixels
 */
class WebGLEffectComposer(
    private val gl: WebGLRenderingContext,
    width: Int = 0,
    height: Int = 0
) {
    private val _passes = mutableListOf<WebGLEffectPass>()

    /** Read-only list of all passes in the chain */
    val passes: List<WebGLEffectPass>
        get() = _passes.toList()

    /** Number of passes in the chain */
    val passCount: Int
        get() = _passes.size

    /** Current width in pixels */
    var width: Int = width
        private set

    /** Current height in pixels */
    var height: Int = height
        private set

    /** Whether this composer has been disposed */
    var isDisposed: Boolean = false
        private set

    // Ping-pong framebuffers for multi-pass rendering
    private var framebufferA: WebGLFramebuffer? = null
    private var framebufferB: WebGLFramebuffer? = null
    private var textureA: WebGLTexture? = null
    private var textureB: WebGLTexture? = null

    /** Whether framebuffers have been initialized */
    private var framebuffersInitialized: Boolean = false

    /**
     * Initialize or resize the ping-pong framebuffers.
     */
    fun initializeFramebuffers() {
        // Clean up existing framebuffers
        disposeFramebuffers()

        if (width <= 0 || height <= 0) return

        // Create texture A
        textureA = createRenderTexture()
        framebufferA = createFramebuffer(textureA!!)

        // Create texture B
        textureB = createRenderTexture()
        framebufferB = createFramebuffer(textureB!!)

        framebuffersInitialized = true
    }

    private fun createRenderTexture(): WebGLTexture {
        val texture = gl.createTexture() ?: error("Failed to create WebGL texture")
        gl.bindTexture(TEXTURE_2D, texture)
        gl.texImage2D(TEXTURE_2D, 0, RGBA, width, height, 0, RGBA, UNSIGNED_BYTE, null)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE)
        gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE)
        gl.bindTexture(TEXTURE_2D, null)
        return texture
    }

    private fun createFramebuffer(texture: WebGLTexture): WebGLFramebuffer {
        val framebuffer = gl.createFramebuffer() ?: error("Failed to create WebGL framebuffer")
        gl.bindFramebuffer(FRAMEBUFFER, framebuffer)
        gl.framebufferTexture2D(FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, texture, 0)

        val status = gl.checkFramebufferStatus(FRAMEBUFFER)
        if (status != FRAMEBUFFER_COMPLETE) {
            error("Framebuffer not complete: $status")
        }

        gl.bindFramebuffer(FRAMEBUFFER, null)
        return framebuffer
    }

    private fun disposeFramebuffers() {
        framebufferA?.let { gl.deleteFramebuffer(it) }
        framebufferB?.let { gl.deleteFramebuffer(it) }
        textureA?.let { gl.deleteTexture(it) }
        textureB?.let { gl.deleteTexture(it) }

        framebufferA = null
        framebufferB = null
        textureA = null
        textureB = null
        framebuffersInitialized = false
    }

    /**
     * Adds a pass to the end of the chain.
     */
    fun addPass(pass: WebGLEffectPass) {
        checkNotDisposed()
        _passes.add(pass)
        pass.setSize(width, height)
        pass.compile(gl)
    }

    /**
     * Adds an effect directly, wrapping it in a pass.
     */
    fun addEffect(effect: WebGLFullScreenEffect, requiresInput: Boolean = false) {
        addPass(WebGLEffectPass(effect, requiresInput))
    }

    /**
     * Inserts a pass at the specified index.
     */
    fun insertPass(pass: WebGLEffectPass, index: Int) {
        checkNotDisposed()
        _passes.add(index, pass)
        pass.setSize(width, height)
        pass.compile(gl)
    }

    /**
     * Removes a pass from the chain.
     */
    fun removePass(pass: WebGLEffectPass): Boolean {
        return _passes.remove(pass)
    }

    /**
     * Removes the pass at the specified index.
     */
    fun removePassAt(index: Int): WebGLEffectPass {
        return _passes.removeAt(index)
    }

    /**
     * Removes all passes from the chain.
     */
    fun clearPasses() {
        _passes.forEach { it.dispose(gl) }
        _passes.clear()
    }

    /**
     * Updates the size and propagates to all passes.
     */
    fun setSize(width: Int, height: Int) {
        if (this.width == width && this.height == height) return

        this.width = width
        this.height = height

        for (pass in _passes) {
            pass.setSize(width, height)
        }

        // Reinitialize framebuffers at new size
        if (framebuffersInitialized) {
            initializeFramebuffers()
        }
    }

    /**
     * Swaps the positions of two passes.
     */
    fun swapPasses(index1: Int, index2: Int) {
        val temp = _passes[index1]
        _passes[index1] = _passes[index2]
        _passes[index2] = temp
    }

    /**
     * Gets only enabled passes.
     */
    fun getEnabledPasses(): List<WebGLEffectPass> {
        return _passes.filter { it.enabled && !it.isDisposed }
    }

    /**
     * Render all enabled passes in the chain.
     *
     * This uses ping-pong framebuffers for multi-pass rendering:
     * - First pass reads from scene (or no input) and writes to buffer A
     * - Second pass reads from buffer A and writes to buffer B
     * - And so on...
     * - Final pass writes directly to the screen
     *
     * @param inputTexture Optional input texture (e.g., from scene render)
     */
    fun render(inputTexture: WebGLTexture? = null) {
        if (isDisposed) return

        val enabledPasses = getEnabledPasses()
        if (enabledPasses.isEmpty()) return

        // Initialize framebuffers if needed
        if (!framebuffersInitialized && enabledPasses.size > 1) {
            initializeFramebuffers()
        }

        var readTexture: WebGLTexture? = inputTexture
        var writeFramebuffer: WebGLFramebuffer? = framebufferA
        var readFramebuffer: WebGLFramebuffer? = null

        for ((index, pass) in enabledPasses.withIndex()) {
            val isLastPass = index == enabledPasses.lastIndex

            // Determine write target
            val targetFramebuffer = if (isLastPass || pass.renderToScreen) {
                null // Write to screen
            } else {
                writeFramebuffer
            }

            // Bind framebuffer
            gl.bindFramebuffer(FRAMEBUFFER, targetFramebuffer)
            gl.viewport(0, 0, width, height)

            // Clear with pass's clear color
            val cc = pass.effect.clearColor
            gl.clearColor(cc.r, cc.g, cc.b, cc.a)
            gl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT)

            // Bind input texture if this pass requires it
            if (pass.requiresInputTexture && readTexture != null) {
                gl.activeTexture(TEXTURE0)
                gl.bindTexture(TEXTURE_2D, readTexture)
                pass.inputTextureLocation?.let { loc ->
                    gl.uniform1i(loc, 0)
                }
            }

            // Render the pass
            pass.effect.render(gl)

            // Swap read/write buffers for next pass
            if (!isLastPass) {
                readTexture = if (writeFramebuffer == framebufferA) textureA else textureB
                writeFramebuffer = if (writeFramebuffer == framebufferA) framebufferB else framebufferA
            }
        }

        // Ensure we're back to the screen framebuffer
        gl.bindFramebuffer(FRAMEBUFFER, null)
    }

    /**
     * Render a single effect (no pass chain).
     */
    fun renderSingle(effect: WebGLFullScreenEffect) {
        if (isDisposed) return

        if (!effect.compile(gl)) {
            console.error("Failed to compile effect")
            return
        }

        val cc = effect.clearColor
        gl.clearColor(cc.r, cc.g, cc.b, cc.a)
        gl.clear(COLOR_BUFFER_BIT or DEPTH_BUFFER_BIT)
        gl.viewport(0, 0, width, height)

        effect.render(gl)
    }

    /**
     * Releases all resources.
     */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true

        for (pass in _passes) {
            pass.dispose(gl)
        }
        _passes.clear()

        disposeFramebuffers()
    }

    private fun checkNotDisposed() {
        check(!isDisposed) { "WebGLEffectComposer has been disposed" }
    }
}
