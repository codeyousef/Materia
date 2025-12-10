package io.materia.engine.render

import io.materia.effects.BlendMode
import io.materia.effects.ClearColor
import io.materia.effects.FullScreenEffect
import io.materia.effects.uniformBlock
import io.materia.effects.fullScreenEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD Tests for FullScreenEffectPass - Bridge between FullScreenEffect and the rendering pipeline.
 *
 * FullScreenEffectPass adapts the high-level FullScreenEffect API to work within
 * the engine's post-processing pipeline, handling:
 * - Shader generation and caching
 * - Uniform buffer management
 * - Blend mode translation for WebGPU
 * - Integration with EffectComposer
 */
class FullScreenEffectPassTest {

    // ============ Creation Tests ============

    @Test
    fun create_fromFullScreenEffect() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent()
        )

        val pass = FullScreenEffectPass(effect)

        assertNotNull(pass)
        assertEquals(effect, pass.effect)
    }

    @Test
    fun create_withDslBuilder() {
        val pass = FullScreenEffectPass.create {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent()
        }

        assertNotNull(pass)
        assertTrue(pass.effect.fragmentShader.contains("@fragment"))
    }

    @Test
    fun create_withUniforms() {
        val uniforms = uniformBlock {
            float("time")
            vec2("resolution")
        }

        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv * u.time, 0.0, 1.0);
                }
            """.trimIndent(),
            uniforms = uniforms
        )

        val pass = FullScreenEffectPass(effect)

        assertNotNull(pass)
        assertEquals(uniforms, pass.effect.uniforms)
    }

    // ============ Shader Generation Tests ============

    @Test
    fun getShaderCode_includesVertexAndFragmentShaders() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.5, 1.0);
                }
            """.trimIndent()
        )

        val pass = FullScreenEffectPass(effect)
        val shaderCode = pass.getShaderCode()

        assertTrue(shaderCode.contains("@vertex"), "Should include vertex shader")
        assertTrue(shaderCode.contains("@fragment"), "Should include fragment shader")
        assertTrue(shaderCode.contains("VertexOutput"), "Should include vertex output struct")
    }

    @Test
    fun getShaderCode_includesUniformBindings() {
        val effect = fullScreenEffect {
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

        val pass = FullScreenEffectPass(effect)
        val shaderCode = pass.getShaderCode()

        assertTrue(shaderCode.contains("struct Uniforms"), "Should include uniform struct")
        assertTrue(shaderCode.contains("@group(0)"), "Should include group binding")
        assertTrue(shaderCode.contains("@binding(0)"), "Should include binding")
    }

    @Test
    fun getShaderCode_includesInputTexture_whenRequired() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let color = textureSample(inputTexture, inputSampler, uv);
                    return color * 1.5;
                }
            """.trimIndent()
        )

        val pass = FullScreenEffectPass(effect, requiresInputTexture = true)
        val shaderCode = pass.getShaderCode()

        assertTrue(shaderCode.contains("inputTexture"), "Should include input texture binding")
        assertTrue(shaderCode.contains("inputSampler"), "Should include input sampler binding")
    }

    // ============ Uniform Management Tests ============

    @Test
    fun updateUniforms_modifiesEffectBuffer() {
        val effect = fullScreenEffect {
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

        val pass = FullScreenEffectPass(effect)

        pass.updateUniforms {
            set("time", 2.5f)
        }

        assertEquals(2.5f, effect.uniformBuffer[0], 0.0001f)
    }

    @Test
    fun updateUniforms_marksBufferAsDirty() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(u.time); }"
            uniforms {
                float("time")
            }
        }

        val pass = FullScreenEffectPass(effect)

        assertFalse(pass.isUniformBufferDirty, "Should start clean")

        pass.updateUniforms {
            set("time", 1.0f)
        }

        assertTrue(pass.isUniformBufferDirty, "Should be dirty after update")
    }

    @Test
    fun clearDirtyFlag_resetsState() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(u.time); }"
            uniforms {
                float("time")
            }
        }

        val pass = FullScreenEffectPass(effect)
        pass.updateUniforms { set("time", 1.0f) }
        assertTrue(pass.isUniformBufferDirty)

        pass.clearDirtyFlag()

        assertFalse(pass.isUniformBufferDirty)
    }

    // ============ Configuration Tests ============

    @Test
    fun enabled_defaultsToTrue() {
        val pass = FullScreenEffectPass(
            FullScreenEffect(
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            )
        )

        assertTrue(pass.enabled)
    }

    @Test
    fun enabled_canBeToggled() {
        val pass = FullScreenEffectPass(
            FullScreenEffect(
                fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            )
        )

        pass.enabled = false
        assertFalse(pass.enabled)

        pass.enabled = true
        assertTrue(pass.enabled)
    }

    @Test
    fun blendMode_inheritedFromEffect() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.ADDITIVE
        )

        val pass = FullScreenEffectPass(effect)

        assertEquals(BlendMode.ADDITIVE, pass.blendMode)
    }

    @Test
    fun clearColor_inheritedFromEffect() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            clearColor = ClearColor(0.1f, 0.2f, 0.3f, 1.0f)
        )

        val pass = FullScreenEffectPass(effect)

        assertEquals(0.1f, pass.clearColor.r, 0.0001f)
        assertEquals(0.2f, pass.clearColor.g, 0.0001f)
        assertEquals(0.3f, pass.clearColor.b, 0.0001f)
    }

    // ============ Size Management Tests ============

    @Test
    fun setSize_updatesResolutionUniform_whenPresent() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            uniforms {
                vec2("resolution")
            }
        }

        val pass = FullScreenEffectPass(effect, autoUpdateResolution = true)

        pass.setSize(1920, 1080)

        assertEquals(1920f, effect.uniformBuffer[0], 0.0001f)
        assertEquals(1080f, effect.uniformBuffer[1], 0.0001f)
    }

    @Test
    fun setSize_doesNotThrow_whenNoResolutionUniform() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            uniforms {
                float("time")
            }
        }

        val pass = FullScreenEffectPass(effect, autoUpdateResolution = true)

        // Should not throw
        pass.setSize(1920, 1080)
    }

    @Test
    fun setSize_tracksCurrentDimensions() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        pass.setSize(800, 600)

        assertEquals(800, pass.width)
        assertEquals(600, pass.height)
    }

    // ============ Disposal Tests ============

    @Test
    fun dispose_marksPassAsDisposed() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)
        assertFalse(pass.isDisposed)

        pass.dispose()

        assertTrue(pass.isDisposed)
    }

    @Test
    fun dispose_disposesUnderlyingEffect() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)
        assertFalse(effect.isDisposed)

        pass.dispose()

        assertTrue(effect.isDisposed)
    }

    @Test
    fun dispose_isIdempotent() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        pass.dispose()
        pass.dispose() // Should not throw
        pass.dispose()

        assertTrue(pass.isDisposed)
    }
}
