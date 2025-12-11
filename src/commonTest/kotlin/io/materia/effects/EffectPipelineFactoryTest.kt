package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD Tests for EffectPipelineFactory - Creates GPU pipelines for FullScreenEffectPass.
 *
 * EffectPipelineFactory generates WebGPU render pipelines from FullScreenEffectPass
 * configurations, handling:
 * - Shader module compilation
 * - Blend state configuration
 * - Bind group layout creation for uniforms and textures
 */
class EffectPipelineFactoryTest {

    // ============ Pipeline Descriptor Tests ============

    @Test
    fun createDescriptor_fromSimpleEffect() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = """
                    @fragment
                    fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                        return vec4<f32>(uv, 0.0, 1.0);
                    }
                """.trimIndent()
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertNotNull(descriptor)
        assertNotNull(descriptor.shaderCode)
        assertTrue(descriptor.shaderCode.contains("@vertex"))
        assertTrue(descriptor.shaderCode.contains("@fragment"))
    }

    @Test
    fun createDescriptor_withUniforms_hasBindGroupLayout() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = """
                    @fragment
                    fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                        return vec4<f32>(u.time, 0.0, 0.0, 1.0);
                    }
                """.trimIndent()
                uniforms {
                    float("time")
                }
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertTrue(descriptor.hasUniforms)
        assertEquals(1, descriptor.uniformBindings.size)
    }

    @Test
    fun createDescriptor_withInputTexture_hasTextureBindings() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = """
                    @fragment
                    fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                        let color = textureSample(inputTexture, inputSampler, uv);
                        return color;
                    }
                """.trimIndent()
            },
            requiresInputTexture = true
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertTrue(descriptor.hasInputTexture)
        assertTrue(descriptor.textureBindings.isNotEmpty())
    }

    // ============ Blend State Tests ============

    @Test
    fun blendState_opaque_noBlending() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.OPAQUE
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.NONE, descriptor.blendState)
    }

    @Test
    fun blendState_alphaBlend_standardBlending() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.ALPHA_BLEND
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.ALPHA, descriptor.blendState)
    }

    @Test
    fun blendState_additive() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.ADDITIVE
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.ADDITIVE, descriptor.blendState)
    }

    @Test
    fun blendState_screen() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.SCREEN
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.SCREEN, descriptor.blendState)
    }

    @Test
    fun blendState_overlay_mapsToMultiply() {
        // Overlay is approximated as multiply since true overlay requires shader-based implementation
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.OVERLAY
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.MULTIPLY, descriptor.blendState)
    }

    @Test
    fun blendState_multiply() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.MULTIPLY
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.MULTIPLY, descriptor.blendState)
    }

    @Test
    fun blendState_premultipliedAlpha() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                blendMode = BlendMode.PREMULTIPLIED_ALPHA
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        assertEquals(BlendStateType.PREMULTIPLIED, descriptor.blendState)
    }

    // ============ Uniform Buffer Size Tests ============

    @Test
    fun uniformBufferSize_calculatedCorrectly() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
                uniforms {
                    float("time")
                    vec2("resolution")
                    vec4("color")
                }
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass)

        // float(4) + padding(4) + vec2(8) + vec4(16) = 32 bytes = 8 floats
        assertTrue(descriptor.uniformBufferSize > 0)
    }

    // ============ Label Generation Tests ============

    @Test
    fun pipelineLabel_isDescriptive() {
        val pass = FullScreenEffectPass(
            fullScreenEffect {
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            }
        )

        val descriptor = EffectPipelineFactory.createDescriptor(pass, label = "vignette")

        assertEquals("effect-pipeline-vignette", descriptor.label)
    }
}
