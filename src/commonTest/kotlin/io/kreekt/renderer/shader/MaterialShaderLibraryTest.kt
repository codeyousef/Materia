package io.kreekt.renderer.shader

import io.kreekt.renderer.material.MaterialDescriptorRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MaterialShaderLibraryTest {

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
    fun basicShaderCompilesViaChunkRegistry() {
        val shader = MaterialShaderGenerator.compile(MaterialShaderLibrary.basic())

        assertTrue(shader.vertexSource.contains("@vertex"))
        assertFalse(shader.vertexSource.contains("#include"), "Includes should be resolved in vertex shader")
        assertTrue(shader.vertexSource.contains("uniforms.baseColor"))

        assertTrue(shader.fragmentSource.contains("@fragment"))
        assertFalse(shader.fragmentSource.contains("#include"), "Includes should be resolved in fragment shader")
        assertTrue(shader.fragmentSource.contains("uniforms.baseColor.a"))
    }

    @Test
    fun meshStandardShaderExposesEnvironmentBindings() {
        val shader = MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard())

        assertTrue(shader.fragmentSource.contains("@group(1) @binding(0) var prefilterTexture"))
        assertTrue(shader.fragmentSource.contains("roughness_to_mip"))
    }

    @Test
    fun generatorCachesCompiledDescriptors() {
        val descriptor = MaterialShaderLibrary.basic()
        val first = MaterialShaderGenerator.compile(descriptor)
        val second = MaterialShaderGenerator.compile(descriptor)

        assertSame(first, second, "Shader generator should return cached source for identical descriptors")
    }
}
