package io.kreekt.material

import io.kreekt.core.math.Color
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for newly implemented materials
 * Validates all material implementations work correctly
 */
class NewMaterialIntegrationTest {

    @Test
    fun testMeshLambertMaterial() {
        val material = MeshLambertMaterial()
        assertEquals("MeshLambertMaterial", material.type)
        assertNotNull(material.color)
        assertNotNull(material.emissive)
        assertEquals(1f, material.emissiveIntensity)
        assertTrue(material.fog)

        // Test cloning
        val clone = material.clone()
        assertTrue(clone is MeshLambertMaterial)
        assertEquals(material.type, clone.type)
    }

    @Test
    fun testMeshPhongMaterial() {
        val material = MeshPhongMaterial()
        assertEquals("MeshPhongMaterial", material.type)
        assertNotNull(material.color)
        assertNotNull(material.emissive)
        assertNotNull(material.specular)
        assertEquals(30f, material.shininess)
        assertTrue(material.fog)

        // Test property modification
        material.shininess = 64f
        assertEquals(64f, material.shininess)

        val clone = material.clone()
        assertTrue(clone is MeshPhongMaterial)
    }

    @Test
    fun testMeshToonMaterial() {
        val material = MeshToonMaterial()
        assertEquals("MeshToonMaterial", material.type)
        assertNotNull(material.color)
        assertNotNull(material.emissive)
        assertTrue(material.fog)

        val clone = material.clone()
        assertTrue(clone is MeshToonMaterial)
    }

    @Test
    fun testMeshNormalMaterial() {
        val material = MeshNormalMaterial()
        assertEquals("MeshNormalMaterial", material.type)
        assertEquals(1f, material.bumpScale)
        assertEquals(NormalMapType.TangentSpaceNormalMap, material.normalMapType)

        val clone = material.clone()
        assertTrue(clone is MeshNormalMaterial)
    }

    @Test
    fun testMeshDepthMaterial() {
        val material = MeshDepthMaterial()
        assertEquals("MeshDepthMaterial", material.type)
        assertEquals(DepthPacking.BasicDepthPacking, material.depthPacking)

        // Test depth packing change
        material.depthPacking = DepthPacking.RGBADepthPacking
        assertEquals(DepthPacking.RGBADepthPacking, material.depthPacking)

        val clone = material.clone()
        assertTrue(clone is MeshDepthMaterial)
    }

    @Test
    fun testMeshDistanceMaterial() {
        val material = MeshDistanceMaterial()
        assertEquals("MeshDistanceMaterial", material.type)
        assertNotNull(material.referencePosition)
        assertEquals(1f, material.nearDistance)
        assertEquals(1000f, material.farDistance)

        val clone = material.clone()
        assertTrue(clone is MeshDistanceMaterial)
    }

    @Test
    fun testMeshMatcapMaterial() {
        val material = MeshMatcapMaterial()
        assertEquals("MeshMatcapMaterial", material.type)
        assertNotNull(material.color)
        assertEquals(1f, material.bumpScale)

        val clone = material.clone()
        assertTrue(clone is MeshMatcapMaterial)
    }

    @Test
    fun testShadowMaterial() {
        val material = ShadowMaterial()
        assertEquals("ShadowMaterial", material.type)
        assertNotNull(material.color)
        assertTrue(material.transparent) // Should be transparent by default

        val clone = material.clone()
        assertTrue(clone is ShadowMaterial)
    }

    @Test
    fun testLineDashedMaterial() {
        val material = LineDashedMaterial()
        assertEquals("LineDashedMaterial", material.type)
        assertNotNull(material.color)
        assertEquals(1f, material.scale)
        assertEquals(3f, material.dashSize)
        assertEquals(1f, material.gapSize)
        assertTrue(material.fog)

        val clone = material.clone()
        assertTrue(clone is LineDashedMaterial)
    }

    @Test
    fun testRawShaderMaterial() {
        val material = RawShaderMaterial(
            vertexShader = "vertex code",
            fragmentShader = "fragment code"
        )
        assertEquals("vertex code", material.vertexShader)
        assertEquals("fragment code", material.fragmentShader)

        val clone = material.clone()
        assertTrue(clone is RawShaderMaterial)
    }

    @Test
    fun testMeshBasicMaterialEnhanced() {
        val material = MeshBasicMaterial()
        assertEquals("MeshBasicMaterial", material.type)
        assertNotNull(material.color)
        assertEquals(Combine.MultiplyOperation, material.combine)
        assertEquals(1f, material.reflectivity)
        assertEquals(0.98f, material.refractionRatio)
        assertTrue(material.fog)

        val clone = material.clone()
        assertTrue(clone is MeshBasicMaterial)
    }

    @Test
    fun testMaterialBaseEnhancements() {
        val material = MeshBasicMaterial()

        // Test UUID generation
        assertNotNull(material.uuid)
        assertTrue(material.uuid.isNotEmpty())

        // Test blending properties
        assertEquals(Blending.NormalBlending, material.blending)
        assertEquals(BlendingFactor.SrcAlphaFactor, material.blendSrc)
        assertEquals(BlendingFactor.OneMinusSrcAlphaFactor, material.blendDst)

        // Test depth properties
        assertEquals(DepthMode.LessEqualDepth, material.depthFunc)

        // Test stencil properties
        assertEquals(false, material.stencilWrite)
        assertEquals(StencilFunc.AlwaysStencilFunc, material.stencilFunc)

        // Test user data
        assertNotNull(material.userData)
        material.userData["test"] = "value"
        assertEquals("value", material.userData["test"])
    }

    @Test
    fun testMaterialSetValues() {
        val material = MeshBasicMaterial()

        material.setValues(mapOf(
            "name" to "TestMaterial",
            "opacity" to 0.5f,
            "transparent" to true,
            "depthTest" to false
        ))

        assertEquals("TestMaterial", material.name)
        assertEquals(0.5f, material.opacity)
        assertTrue(material.transparent)
        assertEquals(false, material.depthTest)
    }

    @Test
    fun testMaterialCopy() {
        val source = MeshLambertMaterial().apply {
            color = Color(1f, 0f, 0f)
            emissive = Color(0f, 1f, 0f)
            opacity = 0.8f
            transparent = true
        }

        val target = MeshLambertMaterial()
        target.copy(source)

        assertEquals(source.opacity, target.opacity)
        assertEquals(source.transparent, target.transparent)
        // Colors should be cloned, not referenced
        assertEquals(source.color.r, target.color.r)
    }

    @Test
    fun testMaterialDispose() {
        val material = MeshLambertMaterial()
        // Should not throw
        material.dispose()
    }

    @Test
    fun testMaterialTypeEnums() {
        // Test all enum types are accessible
        val blending = Blending.AdditiveBlending
        val blendFactor = BlendingFactor.OneFactor
        val blendEq = BlendingEquation.AddEquation
        val depthMode = DepthMode.LessDepth
        val stencilFunc = StencilFunc.LessStencilFunc
        val stencilOp = StencilOp.KeepStencilOp
        val combine = Combine.MixOperation
        val normalMapType = NormalMapType.ObjectSpaceNormalMap
        val depthPacking = DepthPacking.RGBADepthPacking
        val precision = Precision.HighP
        val side = Side.DoubleSide

        assertNotNull(blending)
        assertNotNull(blendFactor)
        assertNotNull(blendEq)
        assertNotNull(depthMode)
        assertNotNull(stencilFunc)
        assertNotNull(stencilOp)
        assertNotNull(combine)
        assertNotNull(normalMapType)
        assertNotNull(depthPacking)
        assertNotNull(precision)
        assertNotNull(side)
    }

    @Test
    fun testUniformType() {
        val uniform = Uniform(1.0f, UniformType.FLOAT)
        assertEquals(1.0f, uniform.value)
        assertEquals(UniformType.FLOAT, uniform.type)
    }

    @Test
    fun testShaderExtensions() {
        val extensions = ShaderExtensions(
            derivatives = true,
            fragDepth = false,
            drawBuffers = true,
            shaderTextureLOD = false
        )
        assertTrue(extensions.derivatives)
        assertTrue(extensions.drawBuffers)
    }
}
