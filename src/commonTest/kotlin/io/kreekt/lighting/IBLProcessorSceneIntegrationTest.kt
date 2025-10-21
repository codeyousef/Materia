package io.kreekt.lighting

import io.kreekt.core.scene.Scene
import io.kreekt.lighting.ibl.HDREnvironment
import io.kreekt.lighting.ibl.IBLResult
import io.kreekt.renderer.Texture
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IBLProcessorSceneIntegrationTest {
    private val processor = IBLProcessorImpl()
    private val scene = Scene()

    @AfterTest
    fun tearDown() {
        scene.environment?.dispose()
        scene.environmentBrdfLut?.dispose()
    }

    @Test
    fun processEnvironmentForSceneAppliesPrefilterAndBrdf() = runTest {
        val hdr = HDREnvironment(
            data = FloatArray(12) { index -> 0.25f + (index % 3) * 0.1f },
            width = 2,
            height = 2
        )
        val config = IBLConfig(
            irradianceSize = 1,
            prefilterSize = 1,
            brdfLutSize = 4,
            roughnessLevels = 2
        )

        val result = processor.processEnvironmentForScene(hdr, config, scene)

        assertTrue(result is IBLResult.Success, "Expected IBL processing to succeed, but was $result")
        val maps = result.data
        val sceneEnvironment = scene.environment
        val brdfLut = scene.environmentBrdfLut

        assertNotNull(sceneEnvironment, "Scene should receive the generated prefiltered environment")
        assertNotNull(brdfLut, "Scene should receive the generated BRDF LUT")
        assertSame(maps.prefilter, sceneEnvironment, "Scene environment should reuse the processed prefiltered cube")
        assertSame(maps.brdfLut, brdfLut, "Scene BRDF LUT should reuse the processed LUT")
        assertTrue(maps.brdfLut is Texture, "BRDF LUT must implement Texture so renderers can query dimensions")
    }
}
