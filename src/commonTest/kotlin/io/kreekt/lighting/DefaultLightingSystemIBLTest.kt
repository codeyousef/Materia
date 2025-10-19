package io.kreekt.lighting

import io.kreekt.core.scene.Scene
import io.kreekt.renderer.CubeFace
import io.kreekt.renderer.CubeTextureImpl
import io.kreekt.renderer.Texture2D
import io.kreekt.texture.CubeTexture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultLightingSystemIBLTest {
    private val lightingSystem = DefaultLightingSystem()
    private val environment = CubeTexture.gradientSky(size = 64)

    @AfterTest
    fun tearDown() {
        lightingSystem.dispose()
        environment.dispose()
    }

    @Test
    fun generateIrradianceMapProducesConvolvedTexture() {
        val result = lightingSystem.generateIrradianceMap(environment)
        assertTrue(result is LightResult.Success)
        val irradiance = result.value
        // Expect reduced resolution to match DefaultLightingSystem constant (<= source size)
        assertTrue(irradiance.size <= environment.size)
        // The generated map should be a new texture instance, not the original environment
        assertTrue(irradiance !== environment)
    }

    @Test
    fun generatePrefilterMapCachesPerEnvironment() {
        val first = lightingSystem.generatePrefilterMap(environment)
        val second = lightingSystem.generatePrefilterMap(environment)

        assertTrue(first is LightResult.Success)
        assertTrue(second is LightResult.Success)
        // Subsequent calls reuse the cached cube texture
        assertSame(first.value, second.value)
        assertEquals(64, second.value.size)

        val prefilter = second.value as CubeTextureImpl
        // Roughness chain stored as mip levels
        val mip1 = prefilter.getFaceData(CubeFace.POSITIVE_X, 1)
        assertTrue(mip1 != null && mip1.isNotEmpty(), "Prefilter map should populate mip level 1")
    }

    @Test
    fun generateBrdfLutReturnsReusableTexture() {
        val first = lightingSystem.generateBRDFLUT()
        val second = lightingSystem.generateBRDFLUT()

        assertTrue(first is LightResult.Success)
        assertTrue(second is LightResult.Success)
        val lut = first.value
        assertTrue(lut is Texture2D)
        assertEquals(512, lut.width)
        assertEquals(512, lut.height)
        // The cached LUT should be returned on subsequent calls
        assertSame(first.value, second.value)
    }

    @Test
    fun applyEnvironmentToSceneWiresPrefilterAndBrdf() {
        val scene = Scene()
        val result = lightingSystem.applyEnvironmentToScene(scene, environment)

        assertTrue(result is LightResult.Success)
        val prefiltered = scene.environment
        val brdfLut = scene.environmentBrdfLut

        assertTrue(prefiltered != null)
        assertTrue(brdfLut is Texture2D)
        assertEquals(environment.size, prefiltered!!.size)
    }
}
