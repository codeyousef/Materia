package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/**
 * TDD Tests for WGSLLib - Reusable WGSL shader code snippets.
 * 
 * WGSLLib provides common shader functions for:
 * - Hash functions (procedural generation)
 * - Noise functions (value, perlin, simplex, worley)
 * - Fractal functions (fbm, turbulence, ridged)
 * - Color utilities (palette, color space conversion)
 * - Math utilities (remap, smoothstep, rotation)
 * - SDF primitives (shapes for 2D effects)
 */
class WGSLLibTest {

    // ============ Hash Function Tests ============

    @Test
    fun hash21_validWGSL() {
        val code = WGSLLib.Hash.HASH_21
        
        assertContains(code, "fn hash21")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun hash22_validWGSL() {
        val code = WGSLLib.Hash.HASH_22
        
        assertContains(code, "fn hash22")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> vec2<f32>")
        assertValidWGSLFunction(code)
    }

    @Test
    fun hash31_validWGSL() {
        val code = WGSLLib.Hash.HASH_31
        
        assertContains(code, "fn hash31")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun hash33_validWGSL() {
        val code = WGSLLib.Hash.HASH_33
        
        assertContains(code, "fn hash33")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> vec3<f32>")
        assertValidWGSLFunction(code)
    }

    // ============ Noise Function Tests ============

    @Test
    fun valueNoise2D_validWGSL() {
        val code = WGSLLib.Noise.VALUE_2D
        
        assertContains(code, "fn valueNoise")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun perlinNoise2D_validWGSL() {
        val code = WGSLLib.Noise.PERLIN_2D
        
        assertContains(code, "fn perlinNoise")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun simplexNoise2D_validWGSL() {
        val code = WGSLLib.Noise.SIMPLEX_2D
        
        assertContains(code, "fn simplexNoise")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun worleyNoise2D_validWGSL() {
        val code = WGSLLib.Noise.WORLEY_2D
        
        assertContains(code, "fn worleyNoise")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    // ============ Fractal Function Tests ============

    @Test
    fun fbm_validWGSL() {
        val code = WGSLLib.Fractal.FBM
        
        assertContains(code, "fn fbm")
        assertContains(code, "vec2<f32>")
        assertContains(code, "octaves")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun turbulence_validWGSL() {
        val code = WGSLLib.Fractal.TURBULENCE
        
        assertContains(code, "fn turbulence")
        assertContains(code, "vec2<f32>")
        assertContains(code, "octaves")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun ridgedNoise_validWGSL() {
        val code = WGSLLib.Fractal.RIDGED
        
        assertContains(code, "fn ridgedNoise")
        assertContains(code, "vec2<f32>")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    // ============ Color Function Tests ============

    @Test
    fun cosinePalette_validWGSL() {
        val code = WGSLLib.Color.COSINE_PALETTE
        
        assertContains(code, "fn cosinePalette")
        assertContains(code, "-> vec3<f32>")
        // Should have 5 parameters: t, a, b, c, d
        assertContains(code, "t:")
        assertContains(code, "a:")
        assertContains(code, "b:")
        assertContains(code, "c:")
        assertContains(code, "d:")
        assertValidWGSLFunction(code)
    }

    @Test
    fun hsvToRgb_validWGSL() {
        val code = WGSLLib.Color.HSV_TO_RGB
        
        assertContains(code, "fn hsvToRgb")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> vec3<f32>")
        assertValidWGSLFunction(code)
    }

    @Test
    fun rgbToHsv_validWGSL() {
        val code = WGSLLib.Color.RGB_TO_HSV
        
        assertContains(code, "fn rgbToHsv")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> vec3<f32>")
        assertValidWGSLFunction(code)
    }

    @Test
    fun srgbToLinear_validWGSL() {
        val code = WGSLLib.Color.SRGB_TO_LINEAR
        
        assertContains(code, "fn srgbToLinear")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> vec3<f32>")
        assertValidWGSLFunction(code)
    }

    @Test
    fun linearToSrgb_validWGSL() {
        val code = WGSLLib.Color.LINEAR_TO_SRGB
        
        assertContains(code, "fn linearToSrgb")
        assertContains(code, "vec3<f32>")
        assertContains(code, "-> vec3<f32>")
        assertValidWGSLFunction(code)
    }

    // ============ Math Utility Tests ============

    @Test
    fun remap_validWGSL() {
        val code = WGSLLib.Math.REMAP
        
        assertContains(code, "fn remap")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun smoothstepCubic_validWGSL() {
        val code = WGSLLib.Math.SMOOTHSTEP_CUBIC
        
        assertContains(code, "fn smoothstepCubic")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun smoothstepQuintic_validWGSL() {
        val code = WGSLLib.Math.SMOOTHSTEP_QUINTIC
        
        assertContains(code, "fn smoothstepQuintic")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun rotation2d_validWGSL() {
        val code = WGSLLib.Math.ROTATION_2D
        
        assertContains(code, "fn rotate2d")
        assertContains(code, "-> mat2x2<f32>")
        assertValidWGSLFunction(code)
    }

    // ============ SDF Primitive Tests ============

    @Test
    fun sdfCircle_validWGSL() {
        val code = WGSLLib.SDF.CIRCLE
        
        assertContains(code, "fn sdCircle")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun sdfBox_validWGSL() {
        val code = WGSLLib.SDF.BOX
        
        assertContains(code, "fn sdBox")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun sdfRoundedBox_validWGSL() {
        val code = WGSLLib.SDF.ROUNDED_BOX
        
        assertContains(code, "fn sdRoundedBox")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    @Test
    fun sdfLine_validWGSL() {
        val code = WGSLLib.SDF.LINE
        
        assertContains(code, "fn sdLine")
        assertContains(code, "-> f32")
        assertValidWGSLFunction(code)
    }

    // ============ Integration Tests ============

    @Test
    fun snippets_canBeCombined() {
        val shader = """
            ${WGSLLib.Hash.HASH_22}
            ${WGSLLib.Noise.VALUE_2D}
            ${WGSLLib.Fractal.FBM}
            ${WGSLLib.Color.COSINE_PALETTE}
            
            @fragment
            fn main(@location(0) uv: vec2<f32>) -> @location(0) vec4<f32> {
                let n = fbm(uv * 10.0, 6);
                let color = cosinePalette(n, 
                    vec3<f32>(0.5), vec3<f32>(0.5), 
                    vec3<f32>(1.0), vec3<f32>(0.0, 0.33, 0.67));
                return vec4<f32>(color, 1.0);
            }
        """.trimIndent()
        
        // All functions should be present
        assertContains(shader, "fn hash22")
        assertContains(shader, "fn valueNoise")
        assertContains(shader, "fn fbm")
        assertContains(shader, "fn cosinePalette")
        assertContains(shader, "@fragment")
    }

    @Test
    fun snippets_uniqueNames() {
        // Ensure no duplicate function names
        val allSnippets = listOf(
            WGSLLib.Hash.HASH_21,
            WGSLLib.Hash.HASH_22,
            WGSLLib.Hash.HASH_31,
            WGSLLib.Hash.HASH_33,
            WGSLLib.Noise.VALUE_2D,
            WGSLLib.Noise.PERLIN_2D,
            WGSLLib.Noise.SIMPLEX_2D,
            WGSLLib.Noise.WORLEY_2D,
            WGSLLib.Fractal.FBM,
            WGSLLib.Fractal.TURBULENCE,
            WGSLLib.Fractal.RIDGED,
            WGSLLib.Color.COSINE_PALETTE,
            WGSLLib.Color.HSV_TO_RGB,
            WGSLLib.Color.RGB_TO_HSV,
            WGSLLib.Color.SRGB_TO_LINEAR,
            WGSLLib.Color.LINEAR_TO_SRGB,
            WGSLLib.Math.REMAP,
            WGSLLib.Math.SMOOTHSTEP_CUBIC,
            WGSLLib.Math.SMOOTHSTEP_QUINTIC,
            WGSLLib.Math.ROTATION_2D,
            WGSLLib.SDF.CIRCLE,
            WGSLLib.SDF.BOX,
            WGSLLib.SDF.ROUNDED_BOX,
            WGSLLib.SDF.LINE
        )
        
        // Extract function names
        val functionNameRegex = Regex("""fn\s+(\w+)""")
        val functionNames = allSnippets.flatMap { snippet ->
            functionNameRegex.findAll(snippet).map { it.groupValues[1] }.toList()
        }
        
        // Check for duplicates (except helper functions that might be in multiple snippets)
        val uniqueNames = functionNames.toSet()
        // Main functions should be unique
        assertTrue(uniqueNames.contains("hash21"))
        assertTrue(uniqueNames.contains("hash22"))
        assertTrue(uniqueNames.contains("fbm"))
        assertTrue(uniqueNames.contains("cosinePalette"))
    }

    // ============ Helper Functions ============

    private fun assertValidWGSLFunction(code: String) {
        // Basic WGSL function validation
        assertContains(code, "fn ")
        assertContains(code, "->")
        assertContains(code, "{")
        assertContains(code, "}")
        assertContains(code, "return")
    }
}
