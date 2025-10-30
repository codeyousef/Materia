package io.materia.lighting

import io.materia.lighting.ibl.IBLConvolutionProfiler
import io.materia.lighting.ibl.PrefilterMipSelector
import io.materia.texture.CubeTexture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrefilterMipSelectorTest {

    private val lightingSystem = DefaultLightingSystem()
    private val environment = CubeTexture.gradientSky(size = 16)

    @AfterTest
    fun tearDown() {
        lightingSystem.dispose()
        environment.dispose()
    }

    @Test
    fun roughnessToMipLevelSquaredMapping() {
        val mipCount = 6
        assertEquals(0f, PrefilterMipSelector.roughnessToMipLevel(0f, mipCount))
        assertEquals(
            mipCount - 1f,
            PrefilterMipSelector.roughnessToMipLevel(1f, mipCount)
        )

        val mid = PrefilterMipSelector.roughnessToMipLevel(0.5f, mipCount)
        // 0.5^2 = 0.25 => quarter of mip range
        assertTrue(mid in 1f..2f, "Expected mip near 1-2, got $mid")
    }

    @Test
    fun profilerCapturesConvolutionMetrics() {
        lightingSystem.generatePrefilterMap(environment)
        val metrics = IBLConvolutionProfiler.snapshot()

        assertEquals(16, metrics.prefilterSize)
        assertTrue(metrics.prefilterMipCount >= 1)
        assertTrue(metrics.prefilterSamples > 0)

        lightingSystem.generateIrradianceMap(environment)
        val updated = IBLConvolutionProfiler.snapshot()
        assertTrue(updated.irradianceSamples > 0)
        assertTrue(updated.irradianceSize > 0)
    }
}
