package io.kreekt.renderer.vulkan

import io.kreekt.core.math.Color
import io.kreekt.geometry.BufferAttribute
import io.kreekt.geometry.BufferGeometry
import io.kreekt.material.MeshBasicMaterial
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.RenderSurface
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.geometry.GeometryBuilder
import io.kreekt.renderer.geometry.buildGeometryOptions
import io.kreekt.renderer.material.MaterialDescriptorRegistry
import io.kreekt.texture.Texture2D
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class VulkanAlbedoSamplingTest {

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
    fun fragmentShaderSamplesAlbedoTextureWhenMapAssigned() {
        val geometry = BufferGeometry().apply {
            setAttribute(
                "position",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f, 0f,
                        1f, 0f, 0f,
                        0f, 1f, 0f
                    ),
                    itemSize = 3
                )
            )
            setAttribute(
                "uv",
                BufferAttribute(
                    floatArrayOf(
                        0f, 0f,
                        1f, 0f,
                        0f, 1f
                    ),
                    itemSize = 2
                )
            )
        }

        val material = MeshBasicMaterial().apply {
            map = Texture2D.solidColor(Color.RED)
        }

        val descriptor = MaterialDescriptorRegistry.descriptorFor(material)
            ?: error("MeshBasicMaterial descriptor was not registered")

        val buildOptions = descriptor.buildGeometryOptions(geometry)
        val geometryBuffer = GeometryBuilder.build(geometry, buildOptions)

        val shaderMethod = VulkanRenderer::class.java.getDeclaredMethod(
            "buildShaderProgramConfig",
            io.kreekt.core.scene.Material::class.java,
            io.kreekt.renderer.material.MaterialDescriptor::class.java,
            io.kreekt.renderer.geometry.GeometryMetadata::class.java,
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

        val usesAlbedo = features.javaClass
            .getDeclaredMethod("getUsesAlbedoMap")
            .apply { isAccessible = true }
            .invoke(features) as Boolean

        assertTrue(
            fragmentSource.contains("albedoSample = texture("),
            "Fragment shader should sample the bound albedo texture when a map is present:\n$fragmentSource"
        )
        assertTrue(
            usesAlbedo,
            "Pipeline features should flag albedo map usage when the material defines a texture map"
        )
    }
}
