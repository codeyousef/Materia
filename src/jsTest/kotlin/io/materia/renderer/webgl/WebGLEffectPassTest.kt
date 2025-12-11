package io.materia.renderer.webgl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import io.materia.effects.BlendMode
import io.materia.effects.ClearColor

/**
 * Unit tests for WebGLEffectPass.
 *
 * Tests pass configuration and state management without requiring WebGL context.
 */
class WebGLEffectPassTest {

    @Test
    fun testPassCreate_basicConfiguration() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
        }

        assertTrue(pass.enabled)
        assertFalse(pass.isDisposed)
        assertFalse(pass.requiresInputTexture)
        assertFalse(pass.autoUpdateResolution)
        assertEquals(0, pass.width)
        assertEquals(0, pass.height)
    }

    @Test
    fun testPassCreate_withInputTexture() {
        val pass = WebGLEffectPass.create(
            requiresInputTexture = true
        ) {
            fragmentShader = """
                precision mediump float;
                uniform sampler2D u_inputTexture;
                varying vec2 vUv;
                void main() {
                    gl_FragColor = texture2D(u_inputTexture, vUv);
                }
            """.trimIndent()
        }

        assertTrue(pass.requiresInputTexture)
    }

    @Test
    fun testPassCreate_withAutoResolution() {
        val pass = WebGLEffectPass.create(
            autoUpdateResolution = true
        ) {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                vec2("resolution")
            }
        }

        assertTrue(pass.autoUpdateResolution)

        // Setting size should update the resolution uniform
        pass.setSize(1920, 1080)

        assertEquals(1920, pass.width)
        assertEquals(1080, pass.height)
        assertTrue(pass.isUniformBufferDirty)
    }

    @Test
    fun testPassSetSize() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
        }

        pass.setSize(800, 600)

        assertEquals(800, pass.width)
        assertEquals(600, pass.height)
    }

    @Test
    fun testPassUpdateUniforms_marksDirty() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                float("time")
            }
        }

        assertFalse(pass.isUniformBufferDirty)

        pass.updateUniforms {
            set("time", 1.0f)
        }

        assertTrue(pass.isUniformBufferDirty)
    }

    @Test
    fun testPassClearDirtyFlag() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            uniforms {
                float("time")
            }
        }

        pass.updateUniforms {
            set("time", 1.0f)
        }
        assertTrue(pass.isUniformBufferDirty)

        pass.clearDirtyFlag()
        assertFalse(pass.isUniformBufferDirty)
    }

    @Test
    fun testPassEnableDisable() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
        }

        assertTrue(pass.enabled)

        pass.enabled = false
        assertFalse(pass.enabled)

        pass.enabled = true
        assertTrue(pass.enabled)
    }

    @Test
    fun testPassRenderToScreen() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
        }

        assertFalse(pass.renderToScreen)

        pass.renderToScreen = true
        assertTrue(pass.renderToScreen)
    }

    @Test
    fun testPassEffect_inheritsBlendMode() {
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            blendMode = BlendMode.ADDITIVE
        }

        assertEquals(BlendMode.ADDITIVE, pass.effect.blendMode)
    }

    @Test
    fun testPassEffect_inheritsClearColor() {
        val customColor = ClearColor(0.2f, 0.3f, 0.4f, 1.0f)
        val pass = WebGLEffectPass.create {
            fragmentShader = "void main() { gl_FragColor = vec4(1.0); }"
            clearColor = customColor
        }

        assertEquals(customColor, pass.effect.clearColor)
    }
}
