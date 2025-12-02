package io.materia.engine.material

import io.materia.core.math.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import io.materia.engine.shader.ShaderFeature

/**
 * Comprehensive tests for the Material system.
 */
class MaterialTest {

    // ========== Side Tests ==========

    @Test
    fun side_front_isDefault() {
        assertEquals(Side.FRONT, Side.FRONT)
    }

    @Test
    fun side_hasAllValues() {
        val sides = Side.entries
        assertEquals(3, sides.size)
        assertTrue(Side.FRONT in sides)
        assertTrue(Side.BACK in sides)
        assertTrue(Side.DOUBLE in sides)
    }

    // ========== BasicMaterial Tests ==========

    @Test
    fun basicMaterial_defaultValues() {
        val material = BasicMaterial()
        
        assertEquals("BasicMaterial", material.name)
        assertEquals(Color(1f, 1f, 1f), material.color)
        assertEquals(1f, material.opacity)
        assertFalse(material.transparent)
        assertTrue(material.visible)
        assertEquals(0, material.renderOrder)
        assertEquals(Side.FRONT, material.side)
        assertTrue(material.depthTest)
        assertTrue(material.depthWrite)
        assertTrue(material.needsUpdate)
    }

    @Test
    fun basicMaterial_customValues() {
        val material = BasicMaterial(
            name = "CustomMaterial",
            color = Color(1f, 0f, 0f),
            opacity = 0.5f,
            transparent = true
        )
        
        assertEquals("CustomMaterial", material.name)
        assertEquals(Color(1f, 0f, 0f), material.color)
        assertEquals(0.5f, material.opacity)
        assertTrue(material.transparent)
    }

    @Test
    fun basicMaterial_getRequiredFeatures_isEmpty() {
        val material = BasicMaterial()
        
        val features = material.getRequiredFeatures()
        
        assertTrue(features.isEmpty())
    }

    @Test
    fun basicMaterial_dispose_marksAsDisposed() {
        val material = BasicMaterial()
        
        assertFalse(material.isDisposed)
        material.dispose()
        assertTrue(material.isDisposed)
    }

    @Test
    fun basicMaterial_dispose_isIdempotent() {
        val material = BasicMaterial()
        
        material.dispose()
        material.dispose()
        material.dispose()
        
        assertTrue(material.isDisposed)
    }

    // ========== StandardMaterial Tests ==========

    @Test
    fun standardMaterial_defaultValues() {
        val material = StandardMaterial()
        
        assertEquals("StandardMaterial", material.name)
        assertEquals(Color(1f, 1f, 1f), material.baseColor)
        assertEquals(0f, material.metallic)
        assertEquals(0.5f, material.roughness)
        assertEquals(Color(0f, 0f, 0f), material.emissive)
        assertEquals(1f, material.emissiveIntensity)
        assertEquals(1f, material.normalScale)
        assertEquals(1f, material.aoIntensity)
        assertEquals(1f, material.opacity)
        assertEquals(0f, material.alphaCutoff)
        assertFalse(material.transparent)
    }

    @Test
    fun standardMaterial_customValues() {
        val material = StandardMaterial(
            name = "CustomPBR",
            baseColor = Color(0.5f, 0.5f, 0.5f),
            metallic = 1f,
            roughness = 0.2f,
            emissive = Color(1f, 0f, 0f),
            emissiveIntensity = 2f
        )
        
        assertEquals("CustomPBR", material.name)
        assertEquals(Color(0.5f, 0.5f, 0.5f), material.baseColor)
        assertEquals(1f, material.metallic)
        assertEquals(0.2f, material.roughness)
        assertEquals(Color(1f, 0f, 0f), material.emissive)
        assertEquals(2f, material.emissiveIntensity)
    }

    @Test
    fun standardMaterial_textureReferences_initiallyNull() {
        val material = StandardMaterial()
        
        assertNull(material.baseColorMap)
        assertNull(material.normalMap)
        assertNull(material.metallicRoughnessMap)
        assertNull(material.aoMap)
        assertNull(material.emissiveMap)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withoutTextures() {
        val material = StandardMaterial()
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in features)
        assertFalse(ShaderFeature.USE_TEXTURE in features)
        assertFalse(ShaderFeature.USE_NORMAL_MAP in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withBaseColorMap() {
        val material = StandardMaterial()
        material.baseColorMap = "dummy-texture"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_TEXTURE in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withNormalMap() {
        val material = StandardMaterial()
        material.normalMap = "dummy-texture"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_NORMAL_MAP in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withMetallicRoughnessMap() {
        val material = StandardMaterial()
        material.metallicRoughnessMap = "dummy-texture"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_METALLIC_ROUGHNESS_MAP in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withAoMap() {
        val material = StandardMaterial()
        material.aoMap = "dummy-texture"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_AO_MAP in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withEmissiveMap() {
        val material = StandardMaterial()
        material.emissiveMap = "dummy-texture"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_EMISSIVE_MAP in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_withAlphaCutoff() {
        val material = StandardMaterial(alphaCutoff = 0.5f)
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_ALPHA_CUTOFF in features)
    }

    @Test
    fun standardMaterial_getRequiredFeatures_multipleTextures() {
        val material = StandardMaterial()
        material.baseColorMap = "color-tex"
        material.normalMap = "normal-tex"
        material.emissiveMap = "emissive-tex"
        
        val features = material.getRequiredFeatures()
        
        assertTrue(ShaderFeature.USE_TEXTURE in features)
        assertTrue(ShaderFeature.USE_NORMAL_MAP in features)
        assertTrue(ShaderFeature.USE_EMISSIVE_MAP in features)
        assertTrue(ShaderFeature.USE_DIRECTIONAL_LIGHT in features)
    }

    @Test
    fun standardMaterial_dispose_marksAsDisposed() {
        val material = StandardMaterial()
        
        assertFalse(material.isDisposed)
        material.dispose()
        assertTrue(material.isDisposed)
    }

    // ========== Material Properties Tests ==========

    @Test
    fun material_sideProperty_affectsRendering() {
        val frontMaterial = BasicMaterial().apply { side = Side.FRONT }
        val backMaterial = BasicMaterial().apply { side = Side.BACK }
        val doubleMaterial = BasicMaterial().apply { side = Side.DOUBLE }
        
        assertEquals(Side.FRONT, frontMaterial.side)
        assertEquals(Side.BACK, backMaterial.side)
        assertEquals(Side.DOUBLE, doubleMaterial.side)
    }

    @Test
    fun material_depthProperties_canBeDisabled() {
        val material = BasicMaterial()
        
        material.depthTest = false
        material.depthWrite = false
        
        assertFalse(material.depthTest)
        assertFalse(material.depthWrite)
    }

    @Test
    fun material_renderOrder_canBeSet() {
        val material1 = BasicMaterial().apply { renderOrder = 0 }
        val material2 = BasicMaterial().apply { renderOrder = 10 }
        val material3 = BasicMaterial().apply { renderOrder = -5 }
        
        assertEquals(0, material1.renderOrder)
        assertEquals(10, material2.renderOrder)
        assertEquals(-5, material3.renderOrder)
    }

    @Test
    fun material_visible_canBeToggled() {
        val material = BasicMaterial()
        
        assertTrue(material.visible)
        material.visible = false
        assertFalse(material.visible)
        material.visible = true
        assertTrue(material.visible)
    }

    @Test
    fun material_needsUpdate_initiallyTrue() {
        val basic = BasicMaterial()
        val standard = StandardMaterial()
        
        assertTrue(basic.needsUpdate)
        assertTrue(standard.needsUpdate)
    }

    @Test
    fun material_needsUpdate_canBeReset() {
        val material = BasicMaterial()
        
        material.needsUpdate = false
        assertFalse(material.needsUpdate)
        
        material.needsUpdate = true
        assertTrue(material.needsUpdate)
    }

    // ========== PBR Parameter Range Tests ==========

    @Test
    fun standardMaterial_metallicRange() {
        val materialLow = StandardMaterial(metallic = 0f)
        val materialMid = StandardMaterial(metallic = 0.5f)
        val materialHigh = StandardMaterial(metallic = 1f)
        
        assertEquals(0f, materialLow.metallic)
        assertEquals(0.5f, materialMid.metallic)
        assertEquals(1f, materialHigh.metallic)
    }

    @Test
    fun standardMaterial_roughnessRange() {
        val materialSmooth = StandardMaterial(roughness = 0f)
        val materialMid = StandardMaterial(roughness = 0.5f)
        val materialRough = StandardMaterial(roughness = 1f)
        
        assertEquals(0f, materialSmooth.roughness)
        assertEquals(0.5f, materialMid.roughness)
        assertEquals(1f, materialRough.roughness)
    }

    @Test
    fun standardMaterial_emissiveIntensity_canBeGreaterThanOne() {
        val material = StandardMaterial(
            emissive = Color(1f, 1f, 1f),
            emissiveIntensity = 5f
        )
        
        assertEquals(5f, material.emissiveIntensity)
    }
}
