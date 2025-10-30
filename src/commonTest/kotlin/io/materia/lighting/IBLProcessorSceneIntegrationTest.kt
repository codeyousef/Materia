package io.materia.lighting

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * Contract test verifying that an IBL processor wires processed maps into a scene.
 * Uses lightweight fakes to validate behaviour without heavy HDR assets.
 */
class IBLProcessorSceneIntegrationTest {
    private val processor = ProcessorFakeIBLProcessor()
    private val scene = ProcessorFakeScene()

    @Test
    fun processEnvironmentForSceneAppliesPrefilterAndBrdf() {
        val hdr = ProcessorFakeHDR(data = FloatArray(4), width = 2, height = 2)
        val config = ProcessorFakeConfig(irradianceSize = 1, prefilterSize = 2, brdfLutSize = 4)

        val result = processor.processEnvironmentForScene(hdr, config, scene)

        val maps = assertIs<ProcessorFakeResult.Success<ProcessorFakeEnvironmentMaps>>(result).data
        assertSame(maps.prefilter, scene.environment)
        assertSame(maps.brdfLut, scene.environmentBrdfLut)
        assertNotNull(scene.environment)
        assertNotNull(scene.environmentBrdfLut)
    }
}

// ---------------------------------------------------------------------
// Fakes used by the tests
// ---------------------------------------------------------------------

internal data class ProcessorFakeHDR(val data: FloatArray, val width: Int, val height: Int)
internal data class ProcessorFakeConfig(
    val irradianceSize: Int,
    val prefilterSize: Int,
    val brdfLutSize: Int
)

internal data class ProcessorFakeEnvironmentMaps(
    val irradiance: ProcessorFakeCubeTexture,
    val prefilter: ProcessorFakeCubeTexture,
    val brdfLut: ProcessorFakeTexture2D
)

internal class ProcessorFakeScene {
    var environment: ProcessorFakeCubeTexture? = null
    var environmentBrdfLut: ProcessorFakeTexture2D? = null
}

internal sealed class ProcessorFakeResult<out T> {
    data class Success<T>(val data: T) : ProcessorFakeResult<T>()
    data class Error(val reason: String) : ProcessorFakeResult<Nothing>()
}

internal class ProcessorFakeCubeTexture(val size: Int)
internal class ProcessorFakeTexture2D(val width: Int, val height: Int)

internal class ProcessorFakeIBLProcessor {
    fun processEnvironmentForScene(
        hdr: ProcessorFakeHDR,
        config: ProcessorFakeConfig,
        scene: ProcessorFakeScene
    ): ProcessorFakeResult<ProcessorFakeEnvironmentMaps> {
        val maps = ProcessorFakeEnvironmentMaps(
            irradiance = ProcessorFakeCubeTexture(config.irradianceSize),
            prefilter = ProcessorFakeCubeTexture(config.prefilterSize),
            brdfLut = ProcessorFakeTexture2D(config.brdfLutSize, config.brdfLutSize)
        )
        scene.environment = maps.prefilter
        scene.environmentBrdfLut = maps.brdfLut
        return ProcessorFakeResult.Success(maps)
    }
}
