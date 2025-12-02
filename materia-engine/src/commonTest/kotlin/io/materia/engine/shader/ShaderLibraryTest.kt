package io.materia.engine.shader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertContains

/**
 * Comprehensive tests for the ShaderLibrary uber shader system.
 */
class ShaderLibraryTest {

    @Test
    fun shaderFeature_hasCorrectDefineStrings() {
        assertEquals("USE_TEXTURE", ShaderFeature.USE_TEXTURE.define)
        assertEquals("USE_NORMAL_MAP", ShaderFeature.USE_NORMAL_MAP.define)
        assertEquals("USE_DIRECTIONAL_LIGHT", ShaderFeature.USE_DIRECTIONAL_LIGHT.define)
        assertEquals("USE_VERTEX_COLORS", ShaderFeature.USE_VERTEX_COLORS.define)
        assertEquals("USE_FOG", ShaderFeature.USE_FOG.define)
        assertEquals("USE_ALPHA_CUTOFF", ShaderFeature.USE_ALPHA_CUTOFF.define)
    }

    @Test
    fun generateDefines_emptyFeatures_setsAllToFalse() {
        val defines = ShaderLibrary.generateDefines(emptySet())
        
        // All features should be set to false
        ShaderFeature.entries.forEach { feature ->
            assertContains(defines, "const ${feature.define}: bool = false;")
        }
    }

    @Test
    fun generateDefines_withFeatures_setsEnabledToTrue() {
        val features = setOf(ShaderFeature.USE_TEXTURE, ShaderFeature.USE_DIRECTIONAL_LIGHT)
        val defines = ShaderLibrary.generateDefines(features)
        
        // Enabled features should be true
        assertContains(defines, "const USE_TEXTURE: bool = true;")
        assertContains(defines, "const USE_DIRECTIONAL_LIGHT: bool = true;")
        
        // Disabled features should be false
        assertContains(defines, "const USE_NORMAL_MAP: bool = false;")
        assertContains(defines, "const USE_FOG: bool = false;")
    }

    @Test
    fun generateDefines_allFeatures_setsAllToTrue() {
        val allFeatures = ShaderFeature.entries.toSet()
        val defines = ShaderLibrary.generateDefines(allFeatures)
        
        ShaderFeature.entries.forEach { feature ->
            assertContains(defines, "const ${feature.define}: bool = true;")
        }
    }

    @Test
    fun commonStructures_containsCameraUniforms() {
        val structures = ShaderLibrary.COMMON_STRUCTURES
        
        assertContains(structures, "struct CameraUniforms")
        assertContains(structures, "viewMatrix: mat4x4<f32>")
        assertContains(structures, "projectionMatrix: mat4x4<f32>")
        assertContains(structures, "cameraPosition: vec3<f32>")
    }

    @Test
    fun commonStructures_containsModelUniforms() {
        val structures = ShaderLibrary.COMMON_STRUCTURES
        
        assertContains(structures, "struct ModelUniforms")
        assertContains(structures, "modelMatrix: mat4x4<f32>")
        assertContains(structures, "normalMatrix: mat4x4<f32>")
    }

    @Test
    fun commonStructures_containsMaterialUniforms() {
        val structures = ShaderLibrary.COMMON_STRUCTURES
        
        assertContains(structures, "struct MaterialUniforms")
        assertContains(structures, "baseColor: vec4<f32>")
        assertContains(structures, "metallic: f32")
        assertContains(structures, "roughness: f32")
    }

    @Test
    fun commonStructures_containsLightStructs() {
        val structures = ShaderLibrary.COMMON_STRUCTURES
        
        assertContains(structures, "struct DirectionalLight")
        assertContains(structures, "struct PointLight")
        assertContains(structures, "direction: vec3<f32>")
        assertContains(structures, "intensity: f32")
    }

    @Test
    fun standardVertexShader_containsBindGroups() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        assertContains(shader, "@group(0) @binding(0)")
        assertContains(shader, "@group(1) @binding(0)")
        assertContains(shader, "var<uniform> camera: CameraUniforms")
        assertContains(shader, "var<uniform> model: ModelUniforms")
    }

    @Test
    fun standardVertexShader_containsVertexInput() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        assertContains(shader, "struct VertexInput")
        assertContains(shader, "@location(0) position: vec3<f32>")
        assertContains(shader, "@location(1) normal: vec3<f32>")
        assertContains(shader, "@location(2) uv: vec2<f32>")
    }

    @Test
    fun standardVertexShader_containsVertexOutput() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        assertContains(shader, "struct VertexOutput")
        assertContains(shader, "@builtin(position) clipPosition")
        assertContains(shader, "worldPosition: vec3<f32>")
        assertContains(shader, "worldNormal: vec3<f32>")
    }

    @Test
    fun standardVertexShader_hasMainFunction() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        assertContains(shader, "@vertex")
        assertContains(shader, "fn main(input: VertexInput)")
    }

    @Test
    fun standardFragmentShader_containsPBRFunctions() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "fn distributionGGX")
        assertContains(shader, "fn geometrySchlickGGX")
        assertContains(shader, "fn geometrySmith")
        assertContains(shader, "fn fresnelSchlick")
    }

    @Test
    fun standardFragmentShader_containsLightingCalculation() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "directionalLight")
        assertContains(shader, "ambient")
        assertContains(shader, "emissive")
    }

    @Test
    fun standardFragmentShader_containsTonemapping() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        // Reinhard tonemapping
        assertContains(shader, "color / (color + vec3<f32>(1.0))")
        // Gamma correction
        assertContains(shader, "pow(color, vec3<f32>(1.0 / 2.2))")
    }

    @Test
    fun unlitVertexShader_isSimpler() {
        val shader = ShaderLibrary.UNLIT_VERTEX_SHADER
        
        assertContains(shader, "modelViewProjection: mat4x4<f32>")
        assertContains(shader, "@location(0) position: vec3<f32>")
        assertContains(shader, "@location(1) color: vec3<f32>")
    }

    @Test
    fun unlitFragmentShader_outputsColor() {
        val shader = ShaderLibrary.UNLIT_FRAGMENT_SHADER
        
        assertContains(shader, "@fragment")
        assertContains(shader, "@location(0) vec4<f32>")
        assertContains(shader, "input.color")
    }

    @Test
    fun compileShader_includesDefines() {
        val features = setOf(ShaderFeature.USE_TEXTURE)
        val compiled = ShaderLibrary.compileShader(ShaderLibrary.STANDARD_VERTEX_SHADER, features)
        
        assertContains(compiled, "const USE_TEXTURE: bool = true;")
    }

    @Test
    fun compileShader_includesCommonStructures() {
        val compiled = ShaderLibrary.compileShader(ShaderLibrary.STANDARD_VERTEX_SHADER, emptySet())
        
        assertContains(compiled, "struct CameraUniforms")
        assertContains(compiled, "struct ModelUniforms")
    }

    @Test
    fun compileShader_includesOriginalSource() {
        val features = setOf(ShaderFeature.USE_DIRECTIONAL_LIGHT)
        val compiled = ShaderLibrary.compileShader(ShaderLibrary.STANDARD_FRAGMENT_SHADER, features)
        
        assertContains(compiled, "@fragment")
        assertContains(compiled, "fn main(input: FragmentInput)")
    }

    @Test
    fun shaderVariant_unlitVertexColor_hasCorrectFeatures() {
        val variant = ShaderVariants.UNLIT_VERTEX_COLOR
        
        assertTrue(variant.features.isEmpty())
        assertEquals(ShaderLibrary.UNLIT_VERTEX_SHADER, variant.vertex)
        assertEquals(ShaderLibrary.UNLIT_FRAGMENT_SHADER, variant.fragment)
    }

    @Test
    fun shaderVariant_standardTextured_hasTextureFeature() {
        val variant = ShaderVariants.STANDARD_TEXTURED
        
        assertTrue(ShaderFeature.USE_TEXTURE in variant.features)
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in variant.features)
    }

    @Test
    fun shaderVariant_standardNormalMapped_hasNormalMapFeature() {
        val variant = ShaderVariants.STANDARD_NORMAL_MAPPED
        
        assertTrue(ShaderFeature.USE_TEXTURE in variant.features)
        assertTrue(ShaderFeature.USE_NORMAL_MAP in variant.features)
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in variant.features)
    }

    @Test
    fun shaderVariant_compiledVertex_isLazy() {
        val variant = ShaderVariants.STANDARD_TEXTURED
        
        // Access the property
        val compiled = variant.compiledVertex
        
        assertTrue(compiled.isNotEmpty())
        assertContains(compiled, "const USE_TEXTURE: bool = true;")
    }

    @Test
    fun shaderVariant_compiledFragment_isLazy() {
        val variant = ShaderVariants.STANDARD_TEXTURED
        
        val compiled = variant.compiledFragment
        
        assertTrue(compiled.isNotEmpty())
        assertContains(compiled, "@fragment")
    }

    @Test
    fun standardShader_conditionalVertexColors() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        // Should have conditional vertex colors
        assertContains(shader, "#ifdef USE_VERTEX_COLORS")
    }

    @Test
    fun standardShader_conditionalTangent() {
        val shader = ShaderLibrary.STANDARD_VERTEX_SHADER
        
        // Should have conditional tangent support
        assertContains(shader, "#ifdef USE_TANGENT")
    }

    @Test
    fun standardFragmentShader_conditionalTexture() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "#ifdef USE_TEXTURE")
    }

    @Test
    fun standardFragmentShader_conditionalNormalMap() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "#ifdef USE_NORMAL_MAP")
    }

    @Test
    fun standardFragmentShader_conditionalFog() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "#ifdef USE_FOG")
    }

    @Test
    fun standardFragmentShader_conditionalAlphaCutoff() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "#ifdef USE_ALPHA_CUTOFF")
    }

    @Test
    fun allShaderFeatures_haveUniqueDefines() {
        val defines = ShaderFeature.entries.map { it.define }
        val uniqueDefines = defines.toSet()
        
        assertEquals(defines.size, uniqueDefines.size, "All shader features should have unique define strings")
    }

    @Test
    fun pbrConstants_areCorrect() {
        val shader = ShaderLibrary.STANDARD_FRAGMENT_SHADER
        
        assertContains(shader, "const PI: f32 = 3.14159265359")
        assertContains(shader, "const DIELECTRIC_F0: vec3<f32> = vec3<f32>(0.04, 0.04, 0.04)")
    }
}
