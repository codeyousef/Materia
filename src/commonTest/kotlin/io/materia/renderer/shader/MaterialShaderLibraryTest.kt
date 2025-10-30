package io.materia.renderer.shader

import io.materia.renderer.material.MaterialDescriptorRegistry
import kotlin.test.*

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
        assertFalse(
            shader.vertexSource.contains("#include"),
            "Includes should be resolved in vertex shader"
        )
        assertTrue(shader.vertexSource.contains("uniforms.baseColor"))

        assertTrue(shader.fragmentSource.contains("@fragment"))
        assertFalse(
            shader.fragmentSource.contains("#include"),
            "Includes should be resolved in fragment shader"
        )
        assertTrue(shader.fragmentSource.contains("uniforms.baseColor.a"))
    }

    @Test
    fun meshStandardShaderExposesEnvironmentBindings() {
        val shader = MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard())

        assertTrue(shader.fragmentSource.contains("@group(2) @binding(0) var prefilterTexture"))
        assertTrue(shader.fragmentSource.contains("@group(2) @binding(2) var brdfLutTexture"))
        assertTrue(shader.fragmentSource.contains("roughness_to_mip"))
    }

    @Test
    fun generatorCachesCompiledDescriptors() {
        val descriptor = MaterialShaderLibrary.basic()
        val first = MaterialShaderGenerator.compile(descriptor)
        val second = MaterialShaderGenerator.compile(descriptor)

        assertSame(
            first,
            second,
            "Shader generator should return cached source for identical descriptors"
        )
    }

    @Test
    fun fragmentOverridesRemovePlaceholdersAndPreserveFormatting() {
        val baseOverrides = emptyOverrideMap()

        val fragmentBindings = listOf(
            "    @group(1) @binding(0) var materialAlbedo: texture_2d<f32>;",
            "    @group(1) @binding(1) var materialSampler: sampler;"
        ).joinToString("\n")
        val fragmentInit = """
            let sample = textureSample(materialAlbedo, materialSampler, in.uv).rgb;
        """.trimIndent().prependIndent("    ")
        val fragmentExtra = "    color = sample;"

        baseOverrides["FRAGMENT_BINDINGS"] = fragmentBindings
        baseOverrides["FRAGMENT_INIT_EXTRA"] = fragmentInit
        baseOverrides["FRAGMENT_EXTRA"] = fragmentExtra

        val descriptor = MaterialShaderLibrary.basic().withOverrides(baseOverrides)
        val shader = MaterialShaderGenerator.compile(descriptor)
        val fragmentSource = shader.fragmentSource

        assertTrue(
            fragmentSource.contains(fragmentBindings),
            "Fragment shader should include custom binding block"
        )
        assertTrue(
            fragmentSource.contains(fragmentInit.trim()),
            "Fragment shader should include initialisation snippet"
        )
        assertTrue(
            fragmentSource.contains(fragmentExtra.trim()),
            "Fragment shader should include fragment extra snippet"
        )

        listOf(
            "FRAGMENT_BINDINGS",
            "FRAGMENT_INIT_EXTRA",
            "FRAGMENT_EXTRA"
        ).forEach { placeholder ->
            assertFalse(
                fragmentSource.contains("{{$placeholder}}"),
                "Placeholder {{$placeholder}} should be removed from fragment shader"
            )
        }
    }

    @Test
    fun fragmentOverridesPreserveLiteralBraces() {
        val overrides = emptyOverrideMap()
        overrides["FRAGMENT_INIT_EXTRA"] = """
            var emissive = in.albedo;
            emissive = emissive * {{EMISSIVE_SCALE}};
        """.trimIndent().prependIndent("    ")

        val descriptor = MaterialShaderLibrary.meshStandard().withOverrides(overrides)
        val fragment = MaterialShaderGenerator.compile(descriptor).fragmentSource

        assertTrue(
            fragment.contains("emissive = emissive * {{EMISSIVE_SCALE}};"),
            "Literal double braces inside overrides should be preserved"
        )
        assertFalse(
            fragment.contains("{{FRAGMENT_INIT_EXTRA}}"),
            "Original placeholder must be removed after replacement"
        )
    }

    private fun emptyOverrideMap(): MutableMap<String, String> = mutableMapOf(
        "VERTEX_INPUT_EXTRA" to "",
        "VERTEX_OUTPUT_EXTRA" to "",
        "VERTEX_ASSIGN_EXTRA" to "",
        "FRAGMENT_INPUT_EXTRA" to "",
        "FRAGMENT_INIT_EXTRA" to "",
        "FRAGMENT_EXTRA" to "",
        "FRAGMENT_BINDINGS" to ""
    )
}
