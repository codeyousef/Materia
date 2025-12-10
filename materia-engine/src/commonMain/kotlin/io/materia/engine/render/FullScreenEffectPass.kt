package io.materia.engine.render

import io.materia.effects.BlendMode
import io.materia.effects.ClearColor
import io.materia.effects.FullScreenEffect
import io.materia.effects.FullScreenEffectBuilder
import io.materia.effects.UniformUpdater
import io.materia.effects.fullScreenEffect

/**
 * A render pass that executes a [FullScreenEffect].
 *
 * This class bridges the high-level `FullScreenEffect` API with the engine's
 * rendering pipeline. It handles:
 * - Shader code generation and caching
 * - Uniform buffer management and dirty tracking
 * - Blend mode and clear color configuration
 * - Resolution uniform auto-updates
 * - Resource lifecycle management
 *
 * Usage:
 * ```kotlin
 * val pass = FullScreenEffectPass.create {
 *     fragmentShader = """
 *         @fragment
 *         fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
 *             return vec4<f32>(uv, sin(u.time), 1.0);
 *         }
 *     """
 *     uniforms {
 *         float("time")
 *     }
 * }
 *
 * // In render loop:
 * pass.updateUniforms { set("time", elapsedTime) }
 * composer.render() // or manual pipeline execution
 * ```
 *
 * @property effect The underlying [FullScreenEffect] configuration
 * @property requiresInputTexture Whether this pass needs the previous pass's output texture
 * @property autoUpdateResolution Whether to auto-update a "resolution" uniform on resize
 */
class FullScreenEffectPass(
    val effect: FullScreenEffect,
    private val requiresInputTexture: Boolean = false,
    private val autoUpdateResolution: Boolean = false
) {

    /**
     * Whether this pass is enabled and should be executed.
     */
    var enabled: Boolean = true

    /**
     * Current width of the render target in pixels.
     */
    var width: Int = 0
        private set

    /**
     * Current height of the render target in pixels.
     */
    var height: Int = 0
        private set

    /**
     * Whether the uniform buffer has been modified since the last GPU upload.
     */
    var isUniformBufferDirty: Boolean = false
        private set

    /**
     * Whether this pass has been disposed.
     */
    var isDisposed: Boolean = false
        private set

    /**
     * The blend mode inherited from the effect.
     */
    val blendMode: BlendMode
        get() = effect.blendMode

    /**
     * The clear color inherited from the effect.
     */
    val clearColor: ClearColor
        get() = effect.clearColor

    // Cached shader code
    private var cachedShaderCode: String? = null

    /**
     * Returns the complete WGSL shader module for this pass.
     *
     * For standard passes, this returns the effect's generated shader.
     * For passes requiring input textures (post-processing chain), this
     * adds the necessary texture/sampler bindings.
     */
    fun getShaderCode(): String {
        cachedShaderCode?.let { return it }

        val baseShader = effect.generateShaderModule()

        val shaderCode = if (requiresInputTexture) {
            injectInputTextureBindings(baseShader)
        } else {
            baseShader
        }

        cachedShaderCode = shaderCode
        return shaderCode
    }

    /**
     * Updates uniform values using the DSL.
     *
     * This marks the uniform buffer as dirty, indicating it needs
     * to be uploaded to the GPU before the next render.
     *
     * @param block DSL block for setting uniform values
     */
    fun updateUniforms(block: UniformUpdater.() -> Unit) {
        effect.updateUniforms(block)
        isUniformBufferDirty = true
    }

    /**
     * Clears the dirty flag after GPU upload.
     *
     * Call this after uploading the uniform buffer to the GPU
     * to reset the dirty tracking state.
     */
    fun clearDirtyFlag() {
        isUniformBufferDirty = false
    }

    /**
     * Sets the size of this pass and optionally updates resolution uniforms.
     *
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height

        if (autoUpdateResolution) {
            tryUpdateResolutionUniform(width, height)
        }
    }

    /**
     * Releases all resources held by this pass.
     *
     * After calling dispose, the pass cannot be used for rendering.
     * This method is idempotent - multiple calls have no effect.
     */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        effect.dispose()
        cachedShaderCode = null
    }

    /**
     * Attempts to update a "resolution" uniform if one exists.
     */
    private fun tryUpdateResolutionUniform(width: Int, height: Int) {
        // Check if the effect has a "resolution" uniform
        val hasResolution = effect.uniforms.field("resolution") != null
        if (hasResolution) {
            effect.updateUniforms {
                set("resolution", width.toFloat(), height.toFloat())
            }
            isUniformBufferDirty = true
        }
    }

    /**
     * Injects input texture bindings for post-processing chain usage.
     */
    private fun injectInputTextureBindings(baseShader: String): String {
        // Determine the next available binding group
        // If uniforms exist, they use @group(0) @binding(0)
        // Input texture uses @group(1) or the next available
        val inputBindings = """
// Input texture from previous pass
@group(1) @binding(0) var inputTexture: texture_2d<f32>;
@group(1) @binding(1) var inputSampler: sampler;

"""
        // Insert after any existing uniform declarations
        val insertionPoint = findInsertionPoint(baseShader)
        return baseShader.substring(0, insertionPoint) +
                inputBindings +
                baseShader.substring(insertionPoint)
    }

    /**
     * Finds the best point to insert input texture bindings.
     */
    private fun findInsertionPoint(shader: String): Int {
        // Look for the end of uniform declarations or vertex output struct
        val patterns = listOf(
            "var<uniform>",
            "struct VertexOutput"
        )

        for (pattern in patterns) {
            val idx = shader.indexOf(pattern)
            if (idx >= 0) {
                // Find the end of this line/block
                val lineEnd = shader.indexOf('\n', idx)
                if (lineEnd >= 0) {
                    // For var<uniform>, find the semicolon
                    val semicolon = shader.indexOf(';', idx)
                    if (semicolon >= 0 && semicolon < lineEnd + 50) {
                        return semicolon + 1
                    }
                    return lineEnd + 1
                }
            }
        }

        // Default: insert at the beginning
        return 0
    }

    companion object {
        /**
         * Creates a [FullScreenEffectPass] using the DSL builder.
         *
         * @param requiresInputTexture Whether this pass needs the previous output
         * @param autoUpdateResolution Whether to auto-update resolution uniform
         * @param block DSL builder for configuring the effect
         */
        fun create(
            requiresInputTexture: Boolean = false,
            autoUpdateResolution: Boolean = false,
            block: FullScreenEffectBuilder.() -> Unit
        ): FullScreenEffectPass {
            val effect = fullScreenEffect(block)
            return FullScreenEffectPass(
                effect = effect,
                requiresInputTexture = requiresInputTexture,
                autoUpdateResolution = autoUpdateResolution
            )
        }
    }
}
