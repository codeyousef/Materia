package io.materia.renderer.vulkan

import io.materia.geometry.BufferAttribute
import io.materia.geometry.BufferGeometry
import io.materia.material.MeshBasicMaterial
import io.materia.renderer.BackendType
import io.materia.renderer.RenderSurface
import io.materia.renderer.RendererConfig
import io.materia.renderer.geometry.GeometryBuilder
import io.materia.renderer.geometry.buildGeometryOptions
import io.materia.renderer.material.MaterialDescriptorRegistry
import io.materia.texture.Data3DTexture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class VulkanVolumeSamplingTest {

    private object StubSurface : RenderSurface {
        override val width: Int = 640
        override val height: Int = 480
        override fun getHandle(): Any = Any()
    }

    private val renderer = VulkanRenderer(
        surface = StubSurface,
        config = RendererConfig(preferredBackend = BackendType.VULKAN, enableValidation = false)
    )

    @AfterTest
    fun tearDown() {
        MaterialDescriptorRegistry.resetForTests()
    }

    @Test
    fun fragmentShaderSamplesVolumeTextureWhenData3dMapAssigned() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        -1f, -1f, -1f,
                        1f, -1f, -1f,
                        -1f, 1f, 1f
                    ),
                    itemSize = 3
                )
            )
        }

        val material = MeshBasicMaterial().apply {
            map = Data3DTexture.solidColor(io.materia.core.math.Color.RED, width = 2, height = 2, depth = 2)
        }

        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
            ?: error("MeshBasicMaterial descriptor was not registered")

        val buildOptions = descriptor.buildGeometryOptions(geometry)
        val geometryBuffer = GeometryBuilder.build(geometry, buildOptions)

        val shaderMethod = VulkanRenderer::class.java.getDeclaredMethod(
            "buildShaderProgramConfig",
            io.materia.core.scene.Material::class.java,
            io.materia.renderer.material.MaterialDescriptor::class.java,
            io.materia.renderer.geometry.GeometryMetadata::class.java,
            List::class.java,
            java.lang.Boolean.TYPE
        ).apply { isAccessible = true }

        val vertexLayouts = geometryBuffer.streams.map { it.layout }
        val shaderConfig = shaderMethod.invoke(
            renderer,
            material,
            descriptor,
            geometryBuffer.metadata,
            vertexLayouts,
            false
        )!!

        val fragmentSource = shaderConfig.javaClass
            .getDeclaredMethod("getFragmentSource")
            .apply { isAccessible = true }
            .invoke(shaderConfig) as String

        val features = shaderConfig.javaClass
            .getDeclaredMethod("getFeatures")
            .apply { isAccessible = true }
            .invoke(shaderConfig)

        val usesVolume = features.javaClass
            .getDeclaredMethod("getUsesVolumeMap")
            .apply { isAccessible = true }
            .invoke(features) as Boolean

        assertTrue(
            fragmentSource.contains("sampler3D(") && fragmentSource.contains("vVolumeCoord"),
            "Fragment shader should sample the bound volume texture when a Data3DTexture map is present:\n$fragmentSource"
        )
        assertTrue(
            usesVolume,
            "Pipeline features should flag volume map usage when MeshBasicMaterial.map is a Data3DTexture"
        )
    }
}