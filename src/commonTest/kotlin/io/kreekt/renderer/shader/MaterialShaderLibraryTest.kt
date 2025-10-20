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
        val shader = MaterialShaderGenerator.compile(MaterialShaderLibrary.basic(), ShaderLanguage.WGSL)

        assertTrue(shader.vertexSource.contains("@vertex"))
        assertFalse(shader.vertexSource.contains("#include"), "Includes should be resolved in vertex shader")
        assertTrue(shader.vertexSource.contains("uniforms.baseColor"))

        assertTrue(shader.fragmentSource.contains("@fragment"))
        assertFalse(shader.fragmentSource.contains("#include"), "Includes should be resolved in fragment shader")
        assertTrue(shader.fragmentSource.contains("uniforms.baseColor.a"))
    }

    @Test
    fun meshStandardShaderExposesEnvironmentBindings() {
        val shader = MaterialShaderGenerator.compile(MaterialShaderLibrary.meshStandard(), ShaderLanguage.WGSL)

        assertTrue(shader.fragmentSource.contains("@group(2) @binding(0) var prefilterTexture"))
        assertTrue(shader.fragmentSource.contains("@group(2) @binding(2) var brdfLutTexture"))
        assertTrue(shader.fragmentSource.contains("roughness_to_mip"))
    }

    @Test
    fun generatorCachesCompiledDescriptors() {
        val descriptor = MaterialShaderLibrary.basic()
        val first = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.WGSL)
        val second = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.WGSL)

        assertSame(first, second, "Shader generator should return cached source for identical descriptors")
    }

    @Test
    fun fragmentOverridesRemovePlaceholdersAndPreserveFormatting() {
        val baseOverrides = buildMap {
            put("VERTEX_INPUT_EXTRA", "")
            put("VERTEX_OUTPUT_EXTRA", "")
            put("VERTEX_ASSIGN_EXTRA", "")
            put("FRAGMENT_INPUT_EXTRA", "")
            put("FRAGMENT_INIT_EXTRA", "")
            put("FRAGMENT_EXTRA", "")
            put("FRAGMENT_BINDINGS", "")
        }.toMutableMap()

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
        val shader = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.WGSL)
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

        listOf("FRAGMENT_BINDINGS", "FRAGMENT_INIT_EXTRA", "FRAGMENT_EXTRA").forEach { placeholder ->
            assertFalse(
                fragmentSource.contains("{{$placeholder}}"),
                "Placeholder {{$placeholder}} should be removed from fragment shader"
            )
        }
    }

    @Test
    fun glslChunksResolveIncludesAndPlaceholders() {
        val overrides = mapOf(
            "VERTEX_INPUT_EXTRA" to "layout(location = 0) in vec3 inPosition;",
            "VERTEX_OUTPUT_EXTRA" to "layout(location = 0) out vec3 vColor;",
            "VERTEX_ASSIGN_EXTRA" to "    vColor = vec3(1.0)\n    gl_Position = vec4(inPosition, 1.0);",
            "FRAGMENT_INPUT_EXTRA" to "layout(location = 0) in vec3 vColor;",
            "FRAGMENT_BINDINGS" to "",
            "FRAGMENT_INIT_EXTRA" to "    vec3 color = vColor;",
            "FRAGMENT_EXTRA" to ""
        )
        val descriptor = MaterialShaderLibrary.basic().withOverrides(overrides)
        val shader = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.GLSL)

        assertTrue(shader.vertexSource.contains("gl_Position"), "GLSL vertex shader should write gl_Position")
        assertFalse(shader.vertexSource.contains("#include"), "GLSL vertex shader should resolve includes")
        assertTrue(shader.vertexSource.contains("layout(location = 0) in vec3 inPosition"))

        assertTrue(shader.fragmentSource.contains("layout(location = 0) out vec4 outColor"))
        assertFalse(shader.fragmentSource.contains("#include"), "GLSL fragment shader should resolve includes")
        assertTrue(shader.fragmentSource.contains("outColor = vec4"), "GLSL fragment shader should assign to outColor")
    }


    @Test
    fun cacheSeparatesLanguagesPerDescriptor() {
        val descriptor = MaterialShaderLibrary.basic()
        val wgsl = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.WGSL)
        val glsl = MaterialShaderGenerator.compile(descriptor, ShaderLanguage.GLSL)

        assertFalse(
            wgsl === glsl,
            "Cache should store separate shader sources for each language"
        )
        assertTrue(wgsl.vertexSource.contains("@vertex"))
        assertTrue(glsl.vertexSource.contains("gl_Position"))
    }

}
