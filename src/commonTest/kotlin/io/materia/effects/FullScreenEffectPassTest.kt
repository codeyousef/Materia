package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD Tests for FullScreenEffectPass - A renderable wrapper around FullScreenEffect.
 *
 * FullScreenEffectPass bridges FullScreenEffect (configuration) with the rendering pipeline:
 * - Manages uniform buffer updates
 * - Handles input texture binding
 * - Provides render-time state (enabled, size)
 * - Supports disposal lifecycle
 */
class FullScreenEffectPassTest {

    // ============ Creation Tests ============

    @Test
    fun create_withEffect_succeeds() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertNotNull(pass)
        assertEquals(effect, pass.effect)
    }

    @Test
    fun create_withDslEffect_succeeds() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent()
        }

        val pass = FullScreenEffectPass(effect)

        assertNotNull(pass)
    }

    // ============ Enabled State Tests ============

    @Test
    fun enabled_defaultsToTrue() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertTrue(pass.enabled)
    }

    @Test
    fun enabled_canBeDisabled() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)
        pass.enabled = false

        assertFalse(pass.enabled)
    }

    // ============ Uniform Update Tests ============

    @Test
    fun updateUniforms_setsFloatValue() {
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
            set("time", 1.5f)
        }

        // Check the value in the uniform buffer (time is at offset 0)
        assertEquals(1.5f, effect.uniformBuffer[0])
    }

    @Test
    fun updateUniforms_setsVec2Value() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(u.resolution, 0.0, 1.0);
                }
            """.trimIndent()
            uniforms {
                vec2("resolution")
            }
        }

        val pass = FullScreenEffectPass(effect)

        pass.updateUniforms {
            set("resolution", 1920f, 1080f)
        }

        // Check the values in the uniform buffer (resolution is at offset 0)
        assertEquals(1920f, effect.uniformBuffer[0])
        assertEquals(1080f, effect.uniformBuffer[1])
    }

    @Test
    fun updateUniforms_setsVec4Value() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return u.color;
                }
            """.trimIndent()
            uniforms {
                vec4("color")
            }
        }

        val pass = FullScreenEffectPass(effect)

        pass.updateUniforms {
            set("color", 1.0f, 0.5f, 0.25f, 1.0f)
        }

        // Check the values in the uniform buffer (color is at offset 0)
        assertEquals(1.0f, effect.uniformBuffer[0])
        assertEquals(0.5f, effect.uniformBuffer[1])
        assertEquals(0.25f, effect.uniformBuffer[2])
        assertEquals(1.0f, effect.uniformBuffer[3])
    }

    @Test
    fun updateUniforms_multipleValues() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4(u.time, u.intensity, 0.0, 1.0);
                }
            """.trimIndent()
            uniforms {
                float("time")
                float("intensity")
            }
        }

        val pass = FullScreenEffectPass(effect)

        pass.updateUniforms {
            set("time", 2.5f)
            set("intensity", 0.8f)
        }

        // time at offset 0, intensity at offset 1 (both floats, 4 bytes each)
        assertEquals(2.5f, effect.uniformBuffer[0])
        assertEquals(0.8f, effect.uniformBuffer[1])
    }

    // ============ Input Texture Tests ============

    @Test
    fun requiresInputTexture_defaultsFalse() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertFalse(pass.requiresInputTexture)
    }

    @Test
    fun requiresInputTexture_canBeSetTrue() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let color = textureSample(inputTexture, inputSampler, uv);
                    return color;
                }
            """.trimIndent()
        }

        val pass = FullScreenEffectPass(effect, requiresInputTexture = true)

        assertTrue(pass.requiresInputTexture)
    }

    // ============ Render to Screen Tests ============

    @Test
    fun renderToScreen_defaultsFalse() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertFalse(pass.renderToScreen)
    }

    @Test
    fun renderToScreen_canBeSetTrue() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect, renderToScreen = true)

        assertTrue(pass.renderToScreen)
    }

    // ============ Shader Code Access Tests ============

    @Test
    fun getShaderCode_returnsEffectShader() {
        val fragmentCode = """
            @fragment
            fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                return vec4<f32>(1.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()

        val effect = FullScreenEffect(fragmentShader = fragmentCode)
        val pass = FullScreenEffectPass(effect)

        // getShaderCode() returns the full module including vertex shader
        val shaderModule = pass.getShaderCode()
        assertTrue(shaderModule.contains("@vertex"), "Should contain vertex shader")
        assertTrue(shaderModule.contains("@fragment"), "Should contain fragment shader")
        assertTrue(shaderModule.contains("return vec4<f32>(1.0, 0.0, 0.0, 1.0)"), "Should contain fragment shader body")
    }

    // ============ Blend Mode Tests ============

    @Test
    fun blendMode_fromEffect() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
            blendMode = BlendMode.ADDITIVE
        }

        val pass = FullScreenEffectPass(effect)

        assertEquals(BlendMode.ADDITIVE, pass.blendMode)
    }

    @Test
    fun blendMode_defaultsToOpaque() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertEquals(BlendMode.OPAQUE, pass.blendMode)
    }

    // ============ Size Management Tests ============

    @Test
    fun size_defaultsToZero() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )

        val pass = FullScreenEffectPass(effect)

        assertEquals(0, pass.width)
        assertEquals(0, pass.height)
    }

    @Test
    fun setSize_updatesDimensions() {
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
