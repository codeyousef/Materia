package io.kreekt.renderer.material

import io.kreekt.material.MaterialSide
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.material.MeshStandardMaterial
import io.kreekt.renderer.geometry.GeometryAttribute
import io.kreekt.renderer.material.MaterialBindingSource
import io.kreekt.renderer.webgpu.ColorWriteMask
import io.kreekt.renderer.webgpu.CullMode
import io.kreekt.renderer.shader.MaterialShaderGenerator
import io.kreekt.renderer.shader.MaterialShaderLibrary
import io.kreekt.renderer.shader.ShaderChunkRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaterialDescriptorRegistryTest {

    @BeforeTest
    fun setUp() {
        ShaderChunkRegistry.clearForTests()
        MaterialShaderLibrary.resetForTests()
        MaterialShaderGenerator.clearCacheForTests()
        MaterialDescriptorRegistry.resetForTests()
    }

    @AfterTest
    fun tearDown() {
        ShaderChunkRegistry.clearForTests()
        MaterialShaderLibrary.resetForTests()
        MaterialShaderGenerator.clearCacheForTests()
        MaterialDescriptorRegistry.resetForTests()
    }

    @Test
    fun basicDescriptorAvailable() {
        val material = MeshBasicMaterial()
        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
        assertNotNull(descriptor)
        assertEquals("material.basic", descriptor.key)
        assertEquals(240, MaterialDescriptorRegistry.uniformBlockSizeBytes())
        assertTrue(descriptor.requiredAttributes.contains(GeometryAttribute.POSITION))
        assertTrue(descriptor.requiredAttributes.contains(GeometryAttribute.NORMAL))
        assertEquals(setOf(1), descriptor.bindingGroups(MaterialBindingSource.ALBEDO_MAP))
    }

    @Test
    fun standardDescriptorRequiresEnvironmentBinding() {
        val descriptor = MaterialDescriptorRegistry.descriptorFor(MeshStandardMaterial())
        assertNotNull(descriptor)
        assertTrue(descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER))
        assertEquals(setOf(2), descriptor.bindingGroups(MaterialBindingSource.ENVIRONMENT_PREFILTER))
        assertTrue(descriptor.requiresBinding(MaterialBindingSource.ENVIRONMENT_BRDF))
        assertEquals(setOf(2), descriptor.bindingGroups(MaterialBindingSource.ENVIRONMENT_BRDF))
        assertEquals(setOf(1), descriptor.bindingGroups(MaterialBindingSource.ROUGHNESS_MAP))
        assertEquals(setOf(1), descriptor.bindingGroups(MaterialBindingSource.METALNESS_MAP))
        assertEquals(setOf(1), descriptor.bindingGroups(MaterialBindingSource.AO_MAP))
        assertEquals(setOf(1), descriptor.bindingGroups(MaterialBindingSource.ALBEDO_MAP))
        assertTrue(descriptor.requiredAttributes.contains(GeometryAttribute.POSITION))
        assertTrue(descriptor.requiredAttributes.contains(GeometryAttribute.NORMAL))
    }

    @Test
    fun descriptorLookupReturnsSameInstance() {
        val first = MaterialDescriptorRegistry.descriptorFor(MeshBasicMaterial())
        val second = MaterialDescriptorRegistry.descriptorFor(MeshBasicMaterial())
        assertTrue(first === second)
    }

    @Test
    fun resolveAppliesAlphaBlending() {
        val material = MeshBasicMaterial().apply {
            transparent = true
            opacity = 0.5f
        }
        val resolved = MaterialDescriptorRegistry.resolve(material)
        assertNotNull(resolved)
        val blend = resolved.renderState.colorTarget.blendState
        assertNotNull(blend)
        assertEquals(false, resolved.renderState.depthWrite, "Depth write should be disabled for blended materials")
    }

    @Test
    fun resolveRespectsCullModeAndColorWrite() {
        val material = MeshStandardMaterial().apply {
            side = MaterialSide.DOUBLE
            colorWrite = false
            depthTest = false
        }
        val resolved = MaterialDescriptorRegistry.resolve(material)
        assertNotNull(resolved)
        assertEquals(CullMode.NONE, resolved.renderState.cullMode)
        assertEquals(false, resolved.renderState.depthTest)
        assertEquals(ColorWriteMask.NONE, resolved.renderState.colorTarget.writeMask)
    }
}
