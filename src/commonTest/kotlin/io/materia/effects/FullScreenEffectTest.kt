package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * TDD Tests for FullScreenEffect - High-level API for fullscreen shader effects.
 * 
 * FullScreenEffect provides:
 * - Automatic fullscreen triangle vertex shader
 * - Simple API for fragment-shader-only effects  
 * - Integration with UniformBlock for type-safe uniforms
 * - Built-in UV coordinates passed to fragment shader
 */
class FullScreenEffectTest {

    // ============ Creation Tests ============

    @Test
    fun create_withFragmentShaderOnly() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent()
        )
        
        assertNotNull(effect)
        assertTrue(effect.fragmentShader.contains("@fragment"))
    }

    @Test
    fun create_withUniformBlock() {
        val uniforms = uniformBlock {
            float("time")
            vec2("resolution")
        }
        
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent(),
            uniforms = uniforms
        )
        
        assertNotNull(effect)
        assertEquals(uniforms, effect.uniforms)
    }

    @Test
    fun create_withEmptyUniforms() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent(),
            uniforms = UniformBlock.empty()
        )
        
        assertNotNull(effect)
        assertEquals(0, effect.uniforms.size)
    }

    // ============ Vertex Shader Tests ============

    @Test
    fun vertexShader_generatesFullscreenTriangle() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )
        
        val vertexShader = effect.vertexShader
        
        // Should use vertex_index builtin for position calculation
        assertContains(vertexShader, "@builtin(vertex_index)")
        assertContains(vertexShader, "@vertex")
        // Should output to VertexOutput struct which contains UV
        assertContains(vertexShader, "VertexOutput")
        assertContains(vertexShader, "uv")
    }

    @Test
    fun vertexShader_usesOptimizedSingleTriangle() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )
        
        val vertexShader = effect.vertexShader
        
        // Optimized fullscreen uses bit manipulation for triangle vertices
        // Single triangle covering entire screen with 3 vertices
        assertContains(vertexShader, "vertex_index")
    }

    // ============ Full Shader Generation Tests ============

    @Test
    fun generateCompleteShader_includesVertexAndFragment() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn fs_main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent()
        )
        
        val complete = effect.generateShaderModule()
        
        assertContains(complete, "@vertex")
        assertContains(complete, "@fragment")
    }

    @Test
    fun generateCompleteShader_includesUniformStruct() {
        val uniforms = uniformBlock {
            float("time")
            vec4("color")
        }
        
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn fs_main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return u.color;
                }
            """.trimIndent(),
            uniforms = uniforms
        )
        
        val complete = effect.generateShaderModule()
        
        // Should include uniform struct
        assertContains(complete, "struct Uniforms")
        assertContains(complete, "time: f32")
        assertContains(complete, "color: vec4<f32>")
        // Should include binding declaration
        assertContains(complete, "@group(0)")
        assertContains(complete, "@binding(0)")
    }

    @Test
    fun generateCompleteShader_noUniformStruct_whenEmpty() {
        val effect = FullScreenEffect(
            fragmentShader = """
                @fragment
                fn fs_main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent(),
            uniforms = UniformBlock.empty()
        )
        
        val complete = effect.generateShaderModule()
        
        // Should not include uniform struct when empty
        assertTrue(!complete.contains("struct Uniforms") || complete.contains("// Empty"))
    }

    // ============ Uniform Update Tests ============

    @Test
    fun updateUniforms_setsValues() {
        val uniforms = uniformBlock {
            float("time")
            vec2("resolution")
        }
        
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            uniforms = uniforms
        )
        
        effect.updateUniforms {
            set("time", 1.5f)
            set("resolution", 1920f, 1080f)
        }
        
        // Verify buffer was updated
        val buffer = effect.uniformBuffer
        assertEquals(1.5f, buffer[0], 0.0001f)
        assertEquals(1920f, buffer[2], 0.0001f)  // offset 8 / 4 = index 2
        assertEquals(1080f, buffer[3], 0.0001f)
    }

    @Test
    fun updateUniforms_multipleCallsOverwrite() {
        val uniforms = uniformBlock {
            float("time")
        }
        
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            uniforms = uniforms
        )
        
        effect.updateUniforms { set("time", 1.0f) }
        assertEquals(1.0f, effect.uniformBuffer[0], 0.0001f)
        
        effect.updateUniforms { set("time", 2.0f) }
        assertEquals(2.0f, effect.uniformBuffer[0], 0.0001f)
    }

    // ============ Configuration Tests ============

    @Test
    fun configure_blendMode_default() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )
        
        assertEquals(BlendMode.OPAQUE, effect.blendMode)
    }

    @Test
    fun configure_blendMode_alpha() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.ALPHA_BLEND
        )
        
        assertEquals(BlendMode.ALPHA_BLEND, effect.blendMode)
    }

    @Test
    fun configure_blendMode_screen() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.SCREEN
        )
        
        assertEquals(BlendMode.SCREEN, effect.blendMode)
    }

    @Test
    fun configure_blendMode_overlay() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.OVERLAY
        )
        
        assertEquals(BlendMode.OVERLAY, effect.blendMode)
    }

    @Test
    fun configure_blendMode_multiply() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.MULTIPLY
        )
        
        assertEquals(BlendMode.MULTIPLY, effect.blendMode)
    }

    @Test
    fun configure_blendMode_additive() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.ADDITIVE
        )
        
        assertEquals(BlendMode.ADDITIVE, effect.blendMode)
    }

    @Test
    fun configure_blendMode_premultipliedAlpha() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            blendMode = BlendMode.PREMULTIPLIED_ALPHA
        )
        
        assertEquals(BlendMode.PREMULTIPLIED_ALPHA, effect.blendMode)
    }

    @Test
    fun configure_clearColor() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
            clearColor = ClearColor(0.1f, 0.2f, 0.3f, 1.0f)
        )
        
        assertEquals(0.1f, effect.clearColor.r, 0.0001f)
        assertEquals(0.2f, effect.clearColor.g, 0.0001f)
        assertEquals(0.3f, effect.clearColor.b, 0.0001f)
        assertEquals(1.0f, effect.clearColor.a, 0.0001f)
    }

    // ============ Resource Management Tests ============

    @Test
    fun dispose_clearsResources() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )
        
        effect.dispose()
        
        assertTrue(effect.isDisposed)
    }

    @Test
    fun dispose_multipleCallsSafe() {
        val effect = FullScreenEffect(
            fragmentShader = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }"
        )
        
        effect.dispose()
        effect.dispose()  // Should not throw
        
        assertTrue(effect.isDisposed)
    }

    // ============ Builder Pattern Tests ============

    @Test
    fun builder_fluientAPI() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    return vec4<f32>(uv, 0.0, 1.0);
                }
            """.trimIndent()
            
            uniforms {
                float("time")
                vec2("resolution")
            }
            
            blendMode = BlendMode.ALPHA_BLEND
        }
        
        assertNotNull(effect)
        assertEquals(BlendMode.ALPHA_BLEND, effect.blendMode)
        assertEquals(2, effect.uniforms.layout.size)
    }

    // ============ Aurora Example Test ============

    @Test
    fun aurora_exampleConfiguration() {
        val effect = fullScreenEffect {
            fragmentShader = """
                @fragment
                fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                    let time = u.time;
                    let n = sin(uv.x * 10.0 + time) * 0.5 + 0.5;
                    return vec4<f32>(u.paletteA.rgb * n, 0.85);
                }
            """.trimIndent()
            
            uniforms {
                float("time")
                vec2("resolution")
                vec2("mouse")
                vec4("paletteA")
                vec4("paletteB")
                vec4("paletteC")
                vec4("paletteD")
            }
            
            blendMode = BlendMode.ALPHA_BLEND
        }
        
        // Verify uniform layout
        assertEquals(7, effect.uniforms.layout.size)
        assertEquals(96, effect.uniforms.size)
        
        // Update uniforms
        effect.updateUniforms {
            set("time", 0.0f)
            set("resolution", 1920f, 1080f)
            set("mouse", 960f, 540f)
            set("paletteA", 0.5f, 0.5f, 0.5f, 1.0f)
            set("paletteB", 0.5f, 0.5f, 0.5f, 1.0f)
            set("paletteC", 1.0f, 1.0f, 1.0f, 1.0f)
            set("paletteD", 0.0f, 0.33f, 0.67f, 1.0f)
        }
        
        // Verify some values
        assertEquals(0.0f, effect.uniformBuffer[0], 0.0001f)  // time
    }
}
