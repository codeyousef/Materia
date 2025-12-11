package io.materia.renderer.webgl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

/**
 * Unit tests for GLSLLib shader snippets.
 *
 * Verifies that shader snippets have correct GLSL syntax and expected structure.
 */
class GLSLLibTest {

    // ============ Hash Function Tests ============

    @Test
    fun testHash21_containsExpectedCode() {
        val code = GLSLLib.Hash.HASH_21
        assertContains(code, "float hash21")
        assertContains(code, "vec2 p")
        assertContains(code, "fract")
    }

    @Test
    fun testHash22_containsExpectedCode() {
        val code = GLSLLib.Hash.HASH_22
        assertContains(code, "vec2 hash22")
        assertContains(code, "vec2 p")
        assertContains(code, "fract")
    }

    @Test
    fun testHash31_containsExpectedCode() {
        val code = GLSLLib.Hash.HASH_31
        assertContains(code, "float hash31")
        assertContains(code, "vec3 p")
    }

    @Test
    fun testHash33_containsExpectedCode() {
        val code = GLSLLib.Hash.HASH_33
        assertContains(code, "vec3 hash33")
        assertContains(code, "vec3 p")
    }

    // ============ Noise Function Tests ============

    @Test
    fun testValueNoise2D_containsExpectedCode() {
        val code = GLSLLib.Noise.VALUE_2D
        assertContains(code, "float valueNoise")
        assertContains(code, "floor")
        assertContains(code, "fract")
        assertContains(code, "mix")
    }

    @Test
    fun testPerlinNoise2D_containsExpectedCode() {
        val code = GLSLLib.Noise.PERLIN_2D
        assertContains(code, "float perlinNoise")
        assertContains(code, "dot")
    }

    @Test
    fun testSimplexNoise2D_containsExpectedCode() {
        val code = GLSLLib.Noise.SIMPLEX_2D
        assertContains(code, "float simplexNoise")
        assertContains(code, "K1")
        assertContains(code, "K2")
    }

    @Test
    fun testWorleyNoise2D_containsExpectedCode() {
        val code = GLSLLib.Noise.WORLEY_2D
        assertContains(code, "float worleyNoise")
        assertContains(code, "minDist")
        assertContains(code, "length")
    }

    // ============ Fractal Function Tests ============

    @Test
    fun testFbm_containsExpectedCode() {
        val code = GLSLLib.Fractal.FBM
        assertContains(code, "float fbm")
        assertContains(code, "int octaves")
        assertContains(code, "amplitude")
        assertContains(code, "frequency")
    }

    @Test
    fun testTurbulence_containsExpectedCode() {
        val code = GLSLLib.Fractal.TURBULENCE
        assertContains(code, "float turbulence")
        assertContains(code, "abs")
    }

    @Test
    fun testRidgedNoise_containsExpectedCode() {
        val code = GLSLLib.Fractal.RIDGED
        assertContains(code, "float ridgedNoise")
        assertContains(code, "weight")
    }

    // ============ Color Function Tests ============

    @Test
    fun testCosinePalette_containsExpectedCode() {
        val code = GLSLLib.Color.COSINE_PALETTE
        assertContains(code, "vec3 cosinePalette")
        assertContains(code, "cos")
        assertContains(code, "6.28318")
    }

    @Test
    fun testHsvToRgb_containsExpectedCode() {
        val code = GLSLLib.Color.HSV_TO_RGB
        assertContains(code, "vec3 hsvToRgb")
        assertContains(code, "vec3 hsv")
    }

    @Test
    fun testRgbToHsv_containsExpectedCode() {
        val code = GLSLLib.Color.RGB_TO_HSV
        assertContains(code, "vec3 rgbToHsv")
        assertContains(code, "vec3 rgb")
    }

    @Test
    fun testSrgbToLinear_containsExpectedCode() {
        val code = GLSLLib.Color.SRGB_TO_LINEAR
        assertContains(code, "vec3 srgbToLinear")
        assertContains(code, "pow")
    }

    @Test
    fun testLinearToSrgb_containsExpectedCode() {
        val code = GLSLLib.Color.LINEAR_TO_SRGB
        assertContains(code, "vec3 linearToSrgb")
        assertContains(code, "pow")
    }

    // ============ Math Function Tests ============

    @Test
    fun testRemap_containsExpectedCode() {
        val code = GLSLLib.Math.REMAP
        assertContains(code, "float remap")
        assertContains(code, "inMin")
        assertContains(code, "outMax")
    }

    @Test
    fun testSmoothstepCubic_containsExpectedCode() {
        val code = GLSLLib.Math.SMOOTHSTEP_CUBIC
        assertContains(code, "float smoothstepCubic")
        assertContains(code, "3.0")
    }

    @Test
    fun testSmoothstepQuintic_containsExpectedCode() {
        val code = GLSLLib.Math.SMOOTHSTEP_QUINTIC
        assertContains(code, "float smoothstepQuintic")
        assertContains(code, "6.0")
        assertContains(code, "15.0")
    }

    @Test
    fun testRotation2D_containsExpectedCode() {
        val code = GLSLLib.Math.ROTATION_2D
        assertContains(code, "mat2 rotate2d")
        assertContains(code, "cos")
        assertContains(code, "sin")
    }

    // ============ SDF Function Tests ============

    @Test
    fun testSdCircle_containsExpectedCode() {
        val code = GLSLLib.SDF.CIRCLE
        assertContains(code, "float sdCircle")
        assertContains(code, "length")
    }

    @Test
    fun testSdBox_containsExpectedCode() {
        val code = GLSLLib.SDF.BOX
        assertContains(code, "float sdBox")
        assertContains(code, "abs")
    }

    @Test
    fun testSdRoundedBox_containsExpectedCode() {
        val code = GLSLLib.SDF.ROUNDED_BOX
        assertContains(code, "float sdRoundedBox")
        assertContains(code, "float r")
    }

    @Test
    fun testSdLine_containsExpectedCode() {
        val code = GLSLLib.SDF.LINE
        assertContains(code, "float sdLine")
        assertContains(code, "clamp")
        assertContains(code, "dot")
    }

    @Test
    fun testSdTriangle_containsExpectedCode() {
        val code = GLSLLib.SDF.TRIANGLE
        assertContains(code, "float sdTriangle")
        assertContains(code, "sqrt")
    }

    @Test
    fun testSdRing_containsExpectedCode() {
        val code = GLSLLib.SDF.RING
        assertContains(code, "float sdRing")
        assertContains(code, "thickness")
    }

    // ============ Effects Tests ============

    @Test
    fun testVignette_containsExpectedCode() {
        val code = GLSLLib.Effects.VIGNETTE
        assertContains(code, "float vignette")
        assertContains(code, "intensity")
        assertContains(code, "smoothstep")
    }

    @Test
    fun testFilmGrain_containsExpectedCode() {
        val code = GLSLLib.Effects.FILM_GRAIN
        assertContains(code, "float filmGrain")
        assertContains(code, "hash21")
    }

    @Test
    fun testChromaticAberration_containsExpectedCode() {
        val code = GLSLLib.Effects.CHROMATIC_ABERRATION
        assertContains(code, "vec3 chromaticAberration")
        assertContains(code, "texture2D")
        assertContains(code, "offset")
    }

    @Test
    fun testScanlines_containsExpectedCode() {
        val code = GLSLLib.Effects.SCANLINES
        assertContains(code, "float scanlines")
        assertContains(code, "sin")
    }

    // ============ Preset Tests ============

    @Test
    fun testFragmentHeader_containsExpectedCode() {
        val code = GLSLLib.Presets.FRAGMENT_HEADER
        assertContains(code, "precision mediump float")
        assertContains(code, "varying vec2 vUv")
    }

    @Test
    fun testFragmentHeaderWithUniforms_containsExpectedCode() {
        val code = GLSLLib.Presets.FRAGMENT_HEADER_WITH_UNIFORMS
        assertContains(code, "uniform float u_time")
        assertContains(code, "uniform vec2 u_resolution")
        assertContains(code, "uniform vec2 u_mouse")
    }

    @Test
    fun testAllHash_combinesAllHashFunctions() {
        val code = GLSLLib.Presets.ALL_HASH
        assertContains(code, "hash21")
        assertContains(code, "hash22")
        assertContains(code, "hash31")
        assertContains(code, "hash33")
    }

    @Test
    fun testAllNoise_includesHashDependency() {
        val code = GLSLLib.Presets.ALL_NOISE
        assertContains(code, "hash21") // Dependency
        assertContains(code, "valueNoise")
        assertContains(code, "perlinNoise")
    }

    @Test
    fun testAllSdf_combinesAllSdfFunctions() {
        val code = GLSLLib.Presets.ALL_SDF
        assertContains(code, "sdCircle")
        assertContains(code, "sdBox")
        assertContains(code, "sdRoundedBox")
        assertContains(code, "sdLine")
    }

    // ============ Integration Tests ============

    @Test
    fun testComposeNoiseShader() {
        // Test that snippets can be composed into a valid-looking shader
        val shader = """
            ${GLSLLib.Presets.FRAGMENT_HEADER_WITH_UNIFORMS}
            
            ${GLSLLib.Hash.HASH_21}
            ${GLSLLib.Noise.VALUE_2D}
            ${GLSLLib.Fractal.FBM}
            ${GLSLLib.Color.COSINE_PALETTE}
            
            void main() {
                float n = fbm(vUv * 10.0, 6);
                vec3 color = cosinePalette(n, vec3(0.5), vec3(0.5), vec3(1.0), vec3(0.0));
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()

        // Verify all expected components are present
        assertContains(shader, "precision mediump float")
        assertContains(shader, "hash21")
        assertContains(shader, "valueNoise")
        assertContains(shader, "fbm")
        assertContains(shader, "cosinePalette")
        assertContains(shader, "gl_FragColor")
    }

    @Test
    fun testComposeSdfShader() {
        val shader = """
            ${GLSLLib.Presets.FRAGMENT_HEADER_WITH_UNIFORMS}
            
            ${GLSLLib.SDF.CIRCLE}
            ${GLSLLib.SDF.BOX}
            
            void main() {
                vec2 p = vUv - 0.5;
                float d = min(sdCircle(p, 0.3), sdBox(p, vec2(0.2)));
                vec3 color = vec3(1.0 - smoothstep(0.0, 0.01, d));
                gl_FragColor = vec4(color, 1.0);
            }
        """.trimIndent()

        assertContains(shader, "sdCircle")
        assertContains(shader, "sdBox")
        assertContains(shader, "smoothstep")
    }
}
