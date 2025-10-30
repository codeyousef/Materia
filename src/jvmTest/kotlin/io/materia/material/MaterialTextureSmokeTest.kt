package io.materia.material

import io.materia.core.math.Color
import io.materia.renderer.geometry.GeometryAttribute
import io.materia.renderer.material.MaterialBindingSource
import io.materia.renderer.material.MaterialBindingType
import io.materia.renderer.material.MaterialDescriptorRegistry
import io.materia.texture.Texture2D
import io.materia.renderer.material.requiresBinding
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MaterialTextureSmokeTest {

    @Test
    fun meshBasicMaterialWithTextureHasAlbedoBinding() {
        val texture = Texture2D.solidColor(Color.RED)
        val material = MeshBasicMaterial().apply { map = texture }

        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
        assertNotNull(descriptor, "Descriptor should be registered for MeshBasicMaterial")
        assertTrue(
            descriptor.optionalAttributes.contains(GeometryAttribute.UV0),
            "UV attribute should remain available for textured materials"
        )

        val textureBinding = descriptor.bindings.firstOrNull {
            it.source == MaterialBindingSource.ALBEDO_MAP && it.type == MaterialBindingType.TEXTURE_2D
        }
        assertNotNull(textureBinding, "Albedo texture binding should be defined")

        val samplerBinding = descriptor.bindings.firstOrNull {
            it.source == MaterialBindingSource.ALBEDO_MAP && it.type == MaterialBindingType.SAMPLER
        }
        assertNotNull(samplerBinding, "Albedo sampler binding should be defined")

        assertTrue(texture.needsUpdate, "Newly created textures should request upload")
        assertTrue(texture.getDataSize() > 0, "Solid color textures should provide CPU data")

        val resolved = MaterialDescriptorRegistry.resolve(material)
        assertNotNull(resolved, "Material resolution should succeed for MeshBasicMaterial")

        val previousVersion = texture.version
        texture.setData(texture.getData()!!)
        assertTrue(
            texture.version > previousVersion,
            "Updating texture data should bump the version"
        )
    }

    @Test
    fun meshStandardMaterialExposesNormalMapBindings() {
        val material = MeshStandardMaterial()
        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
        assertNotNull(descriptor, "Descriptor should be registered for MeshStandardMaterial")

        val normalTextureBinding = descriptor.bindings.firstOrNull {
            it.source == MaterialBindingSource.NORMAL_MAP && it.type == MaterialBindingType.TEXTURE_2D
        }
        assertNotNull(
            normalTextureBinding,
            "Normal map texture binding should exist for standard materials"
        )

        val normalSamplerBinding = descriptor.bindings.firstOrNull {
            it.source == MaterialBindingSource.NORMAL_MAP && it.type == MaterialBindingType.SAMPLER
        }
        assertNotNull(
            normalSamplerBinding,
            "Normal map sampler binding should exist for standard materials"
        )
    }

    @Test
    fun meshStandardMaterialRequiresEnvironmentBindings() {
        val material = MeshStandardMaterial()
        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
        assertNotNull(descriptor)
        assertTrue(descriptor!!.requiresBinding(MaterialBindingSource.ENVIRONMENT_PREFILTER))
        assertTrue(descriptor!!.requiresBinding(MaterialBindingSource.ENVIRONMENT_BRDF))
    }

    @Test
    fun meshStandardMaterialIncludesPbrBindings() {
        val descriptor = MaterialDescriptorRegistry.descriptorFor(MeshStandardMaterial())
        assertNotNull(descriptor)

        fun hasBinding(source: MaterialBindingSource, type: MaterialBindingType) =
            descriptor!!.bindings.any { it.source == source && it.type == type }

        assertTrue(hasBinding(MaterialBindingSource.ROUGHNESS_MAP, MaterialBindingType.TEXTURE_2D))
        assertTrue(hasBinding(MaterialBindingSource.ROUGHNESS_MAP, MaterialBindingType.SAMPLER))
        assertTrue(hasBinding(MaterialBindingSource.METALNESS_MAP, MaterialBindingType.TEXTURE_2D))
        assertTrue(hasBinding(MaterialBindingSource.METALNESS_MAP, MaterialBindingType.SAMPLER))
        assertTrue(hasBinding(MaterialBindingSource.AO_MAP, MaterialBindingType.TEXTURE_2D))
        assertTrue(hasBinding(MaterialBindingSource.AO_MAP, MaterialBindingType.SAMPLER))
    }
}
