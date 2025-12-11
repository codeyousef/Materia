package io.materia.effects

/**
 * Blend state types for WebGPU pipelines.
 */
enum class BlendStateType {
    /** No blending - fully opaque */
    NONE,
    /** Standard alpha blending: src * srcAlpha + dst * (1 - srcAlpha) */
    ALPHA,
    /** Additive blending: src + dst */
    ADDITIVE,
    /** Multiply blending: src * dst */
    MULTIPLY,
    /** Premultiplied alpha: src + dst * (1 - srcAlpha) */
    PREMULTIPLIED
}

/**
 * Describes a binding in a bind group layout.
 */
data class BindingDescriptor(
    val binding: Int,
    val type: BindingType,
    val visibility: Set<ShaderStage>
)

/**
 * Types of bindings in a bind group.
 */
enum class BindingType {
    UNIFORM_BUFFER,
    TEXTURE,
    SAMPLER
}

/**
 * Shader stages for binding visibility.
 */
enum class ShaderStage {
    VERTEX,
    FRAGMENT
}

/**
 * Descriptor for creating a GPU pipeline from a [FullScreenEffectPass].
 *
 * This contains all the information needed to create a WebGPU render pipeline:
 * - Compiled WGSL shader code
 * - Blend state configuration
 * - Bind group layouts for uniforms and textures
 * - Buffer size requirements
 */
data class EffectPipelineDescriptor(
    /** Human-readable label for debugging */
    val label: String,
    /** Complete WGSL shader module code */
    val shaderCode: String,
    /** Blend state for color attachments */
    val blendState: BlendStateType,
    /** Whether this effect has uniform bindings */
    val hasUniforms: Boolean,
    /** Whether this effect requires an input texture */
    val hasInputTexture: Boolean,
    /** Uniform buffer size in bytes */
    val uniformBufferSize: Int,
    /** Binding descriptors for uniforms (group 0) */
    val uniformBindings: List<BindingDescriptor>,
    /** Binding descriptors for textures (group 1) */
    val textureBindings: List<BindingDescriptor>
)

/**
 * Factory for creating GPU pipeline descriptors from [FullScreenEffectPass].
 *
 * This factory generates [EffectPipelineDescriptor] objects that can be used
 * to create WebGPU render pipelines. It handles:
 * - Shader code generation from the effect
 * - Blend mode translation to WebGPU blend states
 * - Bind group layout generation for uniforms and input textures
 *
 * Usage:
 * ```kotlin
 * val descriptor = EffectPipelineFactory.createDescriptor(pass, label = "bloom")
 * val pipeline = device.createRenderPipeline(descriptor.toGpuDescriptor())
 * ```
 */
object EffectPipelineFactory {

    /**
     * Creates a pipeline descriptor from a [FullScreenEffectPass].
     *
     * @param pass The effect pass to create a pipeline for
     * @param label Optional label for debugging
     * @return Descriptor containing all pipeline configuration
     */
    fun createDescriptor(
        pass: FullScreenEffectPass,
        label: String = "effect"
    ): EffectPipelineDescriptor {
        val shaderCode = pass.getShaderCode()
        val blendState = mapBlendMode(pass.blendMode)
        val hasUniforms = pass.effect.uniforms.size > 0
        val uniformBufferSize = pass.effect.uniforms.size
        val uniformBindings = createUniformBindings(hasUniforms)
        val textureBindings = createTextureBindings(pass)

        return EffectPipelineDescriptor(
            label = "effect-pipeline-$label",
            shaderCode = shaderCode,
            blendState = blendState,
            hasUniforms = hasUniforms,
            hasInputTexture = textureBindings.isNotEmpty(),
            uniformBufferSize = uniformBufferSize,
            uniformBindings = uniformBindings,
            textureBindings = textureBindings
        )
    }

    /**
     * Maps a [BlendMode] to a [BlendStateType].
     */
    private fun mapBlendMode(blendMode: BlendMode): BlendStateType {
        return when (blendMode) {
            BlendMode.OPAQUE -> BlendStateType.NONE
            BlendMode.ALPHA_BLEND -> BlendStateType.ALPHA
            BlendMode.ADDITIVE -> BlendStateType.ADDITIVE
            BlendMode.MULTIPLY -> BlendStateType.MULTIPLY
            BlendMode.PREMULTIPLIED_ALPHA -> BlendStateType.PREMULTIPLIED
        }
    }

    /**
     * Creates uniform buffer binding descriptors.
     */
    private fun createUniformBindings(hasUniforms: Boolean): List<BindingDescriptor> {
        if (!hasUniforms) return emptyList()

        return listOf(
            BindingDescriptor(
                binding = 0,
                type = BindingType.UNIFORM_BUFFER,
                visibility = setOf(ShaderStage.VERTEX, ShaderStage.FRAGMENT)
            )
        )
    }

    /**
     * Creates texture binding descriptors for input textures.
     */
    private fun createTextureBindings(pass: FullScreenEffectPass): List<BindingDescriptor> {
        // Check if the shader references input textures
        val shaderCode = pass.getShaderCode()
        val hasInputTexture = shaderCode.contains("inputTexture") ||
                shaderCode.contains("tDiffuse")

        if (!hasInputTexture) return emptyList()

        return listOf(
            BindingDescriptor(
                binding = 0,
                type = BindingType.TEXTURE,
                visibility = setOf(ShaderStage.FRAGMENT)
            ),
            BindingDescriptor(
                binding = 1,
                type = BindingType.SAMPLER,
                visibility = setOf(ShaderStage.FRAGMENT)
            )
        )
    }
}
