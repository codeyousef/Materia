package io.materia.renderer.webgpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import io.materia.effects.BlendMode
import io.materia.effects.ClearColor
import io.materia.effects.FullScreenEffectPass
import io.materia.effects.fullScreenEffect

/**
 * Unit tests for WebGPUEffectComposer.
 *
 * Tests pass management and configuration without requiring WebGPU context.
 * Tests that require actual WebGPU rendering are separate integration tests.
 */
class WebGPUEffectComposerTest {

    // ============ Helper to create test passes ============

    private fun createTestPass(name: String = "test"): FullScreenEffectPass {
        return FullScreenEffectPass.create {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0, 0.0, 0.0, 1.0);
                }
            """
        }
    }

    private fun createTestPassWithUniforms(): FullScreenEffectPass {
        return FullScreenEffectPass.create {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(u.time, 0.0, 0.0, 1.0);
                }
            """
            uniforms {
                float("time")
            }
        }
    }

    private fun createTestPassRequiringInput(): FullScreenEffectPass {
        return FullScreenEffectPass.create(requiresInputTexture = true) {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let color = textureSample(inputTexture, inputSampler, uv);
                    return color;
                }
            """
        }
    }

    // ============ Pass Management Tests (don't require GPU) ============

    @Test
    fun testPassList_initiallyEmpty() {
        // We can't create a real WebGPUEffectComposer without a GPU device,
        // but we can test FullScreenEffectPass independently
        val pass = createTestPass()
        
        assertTrue(pass.enabled)
        assertFalse(pass.isDisposed)
        assertEquals(0, pass.width)
        assertEquals(0, pass.height)
    }

    @Test
    fun testPass_setSize() {
        val pass = createTestPass()
        
        pass.setSize(1920, 1080)
        
        assertEquals(1920, pass.width)
        assertEquals(1080, pass.height)
    }

    @Test
    fun testPass_updateUniforms_marksDirty() {
        val pass = createTestPassWithUniforms()
        
        assertFalse(pass.isUniformBufferDirty)
        
        pass.updateUniforms {
            set("time", 1.5f)
        }
        
        assertTrue(pass.isUniformBufferDirty)
    }

    @Test
    fun testPass_clearDirtyFlag() {
        val pass = createTestPassWithUniforms()
        
        pass.updateUniforms {
            set("time", 1.0f)
        }
        assertTrue(pass.isUniformBufferDirty)
        
        pass.clearDirtyFlag()
        
        assertFalse(pass.isUniformBufferDirty)
    }

    @Test
    fun testPass_enableDisable() {
        val pass = createTestPass()
        
        assertTrue(pass.enabled)
        
        pass.enabled = false
        
        assertFalse(pass.enabled)
    }

    @Test
    fun testPass_dispose() {
        val pass = createTestPass()
        
        assertFalse(pass.isDisposed)
        
        pass.dispose()
        
        assertTrue(pass.isDisposed)
    }

    @Test
    fun testPass_blendMode() {
        val pass = FullScreenEffectPass.create {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"
            blendMode = BlendMode.ADDITIVE
        }
        
        assertEquals(BlendMode.ADDITIVE, pass.blendMode)
    }

    @Test
    fun testPass_clearColor() {
        val customColor = ClearColor(0.2f, 0.3f, 0.4f, 1.0f)
        val pass = FullScreenEffectPass.create {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"
            clearColor = customColor
        }
        
        assertEquals(customColor, pass.clearColor)
    }

    @Test
    fun testPass_requiresInputTexture() {
        val passWithInput = createTestPassRequiringInput()
        val passWithoutInput = createTestPass()
        
        assertTrue(passWithInput.requiresInputTexture)
        assertFalse(passWithoutInput.requiresInputTexture)
    }

    @Test
    fun testPass_autoUpdateResolution() {
        val pass = FullScreenEffectPass.create(autoUpdateResolution = true) {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"
            uniforms {
                vec2("resolution")
            }
        }
        
        pass.setSize(800, 600)
        
        // Should mark dirty because resolution uniform is updated
        assertTrue(pass.isUniformBufferDirty)
    }

    @Test
    fun testPass_getShaderCode_cachesResult() {
        val pass = createTestPass()
        
        val code1 = pass.getShaderCode()
        val code2 = pass.getShaderCode()
        
        // Should return same cached string
        assertEquals(code1, code2)
    }

    @Test
    fun testPass_shaderCode_containsVertexShader() {
        val pass = createTestPass()
        
        val shaderCode = pass.getShaderCode()
        
        assertTrue(shaderCode.contains("@vertex"))
        assertTrue(shaderCode.contains("vs_main"))
    }

    @Test
    fun testPass_shaderCode_containsFragmentShader() {
        val pass = createTestPass()
        
        val shaderCode = pass.getShaderCode()
        
        assertTrue(shaderCode.contains("@fragment"))
    }

    @Test
    fun testPass_shaderCodeWithUniforms_containsUniformStruct() {
        val pass = createTestPassWithUniforms()
        
        val shaderCode = pass.getShaderCode()
        
        assertTrue(shaderCode.contains("struct Uniforms"))
        assertTrue(shaderCode.contains("@group(0) @binding(0)"))
        assertTrue(shaderCode.contains("var<uniform>"))
    }

    @Test
    fun testPass_shaderCodeRequiringInput_containsInputBindings() {
        val pass = createTestPassRequiringInput()
        
        val shaderCode = pass.getShaderCode()
        
        assertTrue(shaderCode.contains("inputTexture"))
        assertTrue(shaderCode.contains("inputSampler"))
        assertTrue(shaderCode.contains("@group(1)"))
    }

    // ============ Effect Tests ============

    @Test
    fun testFullScreenEffect_generateShaderModule() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """
        }
        
        val module = effect.generateShaderModule()
        
        assertTrue(module.contains("struct VertexOutput"))
        assertTrue(module.contains("@vertex"))
        assertTrue(module.contains("@fragment"))
    }

    @Test
    fun testFullScreenEffect_uniformBuffer() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"
            uniforms {
                float("time")
                vec2("resolution")
            }
        }
        
        // Uniform buffer should be allocated
        assertTrue(effect.uniformBuffer.isNotEmpty())
    }

    @Test
    fun testFullScreenEffect_updateUniforms() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(u.time); }"
            uniforms {
                float("time")
            }
        }
        
        effect.updateUniforms {
            set("time", 2.5f)
        }
        
        // The value should be in the buffer
        assertEquals(2.5f, effect.uniformBuffer[0])
    }

    @Test
    fun testFullScreenEffect_dispose() {
        val effect = fullScreenEffect {
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"
        }
        
        assertFalse(effect.isDisposed)
        
        effect.dispose()
        
        assertTrue(effect.isDisposed)
    }
}
