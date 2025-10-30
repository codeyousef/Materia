package io.materia.lighting

import io.materia.core.scene.Scene
import io.materia.lighting.ibl.HDREnvironment
import io.materia.lighting.ibl.IBLConfig
import io.materia.lighting.ibl.IBLResult
import io.materia.lighting.ibl.IBLEnvironmentMaps
import io.materia.renderer.Texture2D
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EnvironmentSceneIntegrationTest {

    @Test
    fun processEnvironmentPipelinePopulatesScene() {
        runBlocking {
            val processor = IBLProcessorImpl()
            val scene = Scene()
            val config = IBLConfig(
                irradianceSize = 16,
                prefilterSize = 32,
                brdfLutSize = 64,
                roughnessLevels = 3,
                samples = 64
            )

            val hdr = createSyntheticHdr(width = 16, height = 8)
            val result = processor.processEnvironmentForScene(hdr, config, scene)

            assertTrue(result is IBLResult.Success<*>)
            val maps = result.data as IBLEnvironmentMaps

            assertSame(
                maps.prefilter,
                scene.environment,
                "Scene should reference the generated prefilter map"
            )
            assertSame(
                maps.brdfLut,
                scene.environmentBrdfLut,
                "Scene should reference the generated BRDF LUT"
            )
            assertTrue(scene.environmentBrdfLut is Texture2D)
            val brdfTexture = scene.environmentBrdfLut as Texture2D
            assertEquals(config.brdfLutSize, brdfTexture.width)
            assertEquals(config.brdfLutSize, brdfTexture.height)
            assertNotNull(scene.environment)
        }
    }

    private fun createSyntheticHdr(width: Int, height: Int): HDREnvironment {
        val channels = 3
        val data = FloatArray(width * height * channels) { index ->
            val pattern = (index % (width * channels)).toFloat() / (width * channels)
            0.25f + pattern
        }
        return HDREnvironment(data, width, height)
    }
}
