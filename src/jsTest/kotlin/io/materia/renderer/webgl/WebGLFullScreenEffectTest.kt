package io.materia.renderer.webgl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import io.materia.effects.BlendMode
import io.materia.effects.ClearColor
import io.materia.effects.uniformBlock

/**
 * Unit tests for WebGLFullScreenEffect.
 *
 * These tests verify the effect configuration, uniform handling, and shader generation
 * without requiring a WebGL context.
 */
class WebGLFullScreenEffectTest {

    @Test
    fun testDefaultEffect_hasCorrectDefaults() {
        val effect = WebGLFullScreenEffect(
            fragmentShader = """
                precision mediump float;
                varying vec2 vUv;
                void main() {
                    gl_FragColor = vec4(vUv, 0.0, 1.0);
                }
            """.trimIndent()
        )

        assertEquals(BlendMode.OPAQUE, effect.blendMode)
        assertEquals(ClearColor.BLACK, effect.clearColor)
        assertFalse(effect.isDisposed)
        assertTrue(effect.uniforms.layout.isEmpty())
    }

    @Test
    fun testEffectWithUniforms_createsCorrectBuffer() {
        val effect = WebGLFullScreenEffect(
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }",
            uniforms = uniformBlock {
                float("time")
                vec2("resolution")
                vec4("color")
            }
        )

        // Verify uniform buffer is created with correct size
        // float(4) + padding(4) + vec2(8) + vec4(16) = 32 bytes = 8 floats
        assertTrue(effect.uniformBuffer.isNotEmpty())
        assertEquals(3, effect.uniforms.layout.size)
    }

    @Test
    fun testUpdateUniforms_modifiesBuffer() {
        val effect = WebGLFullScreenEffect(
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }",
            uniforms = uniformBlock {
                float("time")
            }
        )

        effect.updateUniforms {
            set("time", 1.5f)
        }

        assertEquals(1.5f, effect.uniformBuffer[0])
    }

    @Test
    fun testEffectBuilder_createsEffect() {
        val effect = webGLFullScreenEffect {
            fragmentShader = """
                precision mediump float;
                varying vec2 vUv;
                uniform float u_time;
                void main() {
                    gl_FragColor = vec4(vUv, sin(u_time), 1.0);
                }
            """.trimIndent()
            blendMode = BlendMode.ALPHA_BLEND
            clearColor = ClearColor.TRANSPARENT
            uniforms {
                float("time")
            }
        }

        assertEquals(BlendMode.ALPHA_BLEND, effect.blendMode)
        assertEquals(ClearColor.TRANSPARENT, effect.clearColor)
        assertEquals(1, effect.uniforms.layout.size)
        assertTrue(effect.fragmentShader.contains("u_time"))
    }

    @Test
    fun testVertexShader_hasCorrectStructure() {
        val effect = WebGLFullScreenEffect(
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
        )

        assertTrue(effect.vertexShader.contains("attribute vec2 aPosition"))
        assertTrue(effect.vertexShader.contains("varying vec2 vUv"))
        assertTrue(effect.vertexShader.contains("gl_Position"))
    }

    @Test
    fun testBlendModes_allValuesSupported() {
        val modes = listOf(
            BlendMode.OPAQUE,
            BlendMode.ALPHA_BLEND,
            BlendMode.ADDITIVE,
            BlendMode.MULTIPLY,
            BlendMode.SCREEN,
            BlendMode.OVERLAY,
            BlendMode.PREMULTIPLIED_ALPHA
        )

        modes.forEach { mode ->
            val effect = WebGLFullScreenEffect(
                fragmentShader = "void main() { gl_FragColor = vec4(1.0); }",
                blendMode = mode
            )
            assertEquals(mode, effect.blendMode)
        }
    }

    @Test
    fun testClearColor_customValues() {
        val customColor = ClearColor(0.1f, 0.2f, 0.3f, 0.5f)
        val effect = WebGLFullScreenEffect(
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }",
            clearColor = customColor
        )

        assertEquals(0.1f, effect.clearColor.r)
        assertEquals(0.2f, effect.clearColor.g)
        assertEquals(0.3f, effect.clearColor.b)
        assertEquals(0.5f, effect.clearColor.a)
    }

    @Test
    fun testUniformTypes_allTypesSupported() {
        val effect = webGLFullScreenEffect {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                float("f")
                int("i")
                vec2("v2")
                vec3("v3")
                vec4("v4")
                mat3("m3")
                mat4("m4")
            }
        }

        assertEquals(7, effect.uniforms.layout.size)

        // Verify all fields exist
        assertNotNull(effect.uniforms.field("f"))
        assertNotNull(effect.uniforms.field("i"))
        assertNotNull(effect.uniforms.field("v2"))
        assertNotNull(effect.uniforms.field("v3"))
        assertNotNull(effect.uniforms.field("v4"))
        assertNotNull(effect.uniforms.field("m3"))
        assertNotNull(effect.uniforms.field("m4"))
    }

    @Test
    fun testUpdateVec2Uniform() {
        val effect = webGLFullScreenEffect {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                vec2("resolution")
            }
        }

        effect.updateUniforms {
            set("resolution", 1920f, 1080f)
        }

        val offset = effect.uniforms.field("resolution")!!.offset / 4
        assertEquals(1920f, effect.uniformBuffer[offset])
        assertEquals(1080f, effect.uniformBuffer[offset + 1])
    }

    @Test
    fun testUpdateVec4Uniform() {
        val effect = webGLFullScreenEffect {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                vec4("color")
            }
        }

        effect.updateUniforms {
            set("color", 1.0f, 0.5f, 0.25f, 0.8f)
        }

        val offset = effect.uniforms.field("color")!!.offset / 4
        assertEquals(1.0f, effect.uniformBuffer[offset])
        assertEquals(0.5f, effect.uniformBuffer[offset + 1])
        assertEquals(0.25f, effect.uniformBuffer[offset + 2])
        assertEquals(0.8f, effect.uniformBuffer[offset + 3])
    }
}
