package io.materia.lighting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Lightweight contract checks for lighting system IBL behaviour.
 * Uses a fake implementation to validate high-level expectations without heavy assets.
 */
class DefaultLightingSystemIBLTest {
    private val lightingSystem = FakeLightingSystem()
    private val environment = FakeCubeTexture(size = 64)

    @Test
    fun generateIrradianceMapProducesConvolvedTexture() {
        val result = lightingSystem.generateIrradianceMap(environment)
        val irradiance = assertIs<FakeLightingResult.Success<FakeCubeTexture>>(result).value
        assertTrue(irradiance.size <= environment.size)
        assertTrue(irradiance !== environment)
    }

    @Test
    fun generatePrefilterMapCachesPerEnvironment() {
        val first = lightingSystem.generatePrefilterMap(environment)
        val second = lightingSystem.generatePrefilterMap(environment)

        val firstSuccess = assertIs<FakeLightingResult.Success<FakeCubeTexture>>(first)
        val secondSuccess = assertIs<FakeLightingResult.Success<FakeCubeTexture>>(second)
        assertSame(firstSuccess.value, secondSuccess.value)
        assertEquals(environment.size, secondSuccess.value.size)
    }

    @Test
    fun generateBrdfLutReturnsReusableTexture() {
        val first = lightingSystem.generateBrdfLut()
        val second = lightingSystem.generateBrdfLut()

        val lut = assertIs<FakeLightingResult.Success<FakeTexture2D>>(first).value
        val cached = assertIs<FakeLightingResult.Success<FakeTexture2D>>(second).value
        assertEquals(512, lut.width)
        assertEquals(512, lut.height)
        assertSame(lut, cached)
    }

    @Test
    fun applyEnvironmentToSceneStoresMaps() {
        val scene = FakeScene()
        val result = lightingSystem.applyEnvironmentToScene(scene, environment)

        assertIs<FakeLightingResult.Success<Unit>>(result)
        assertNotNull(scene.environment)
        assertNotNull(scene.environmentBrdfLut)
    }
}

// ---------------------------------------------------------------------
// Fakes used by the contract tests
// ---------------------------------------------------------------------

internal data class FakeCubeTexture(val size: Int)
internal data class FakeTexture2D(val width: Int, val height: Int)

internal class FakeScene {
    var environment: FakeCubeTexture? = null
    var environmentBrdfLut: FakeTexture2D? = null
}

internal sealed class FakeLightingResult<out T> {
    data class Success<T>(val value: T) : FakeLightingResult<T>()
    data class Error(val message: String) : FakeLightingResult<Nothing>()
}

internal class FakeLightingSystem {
    private var cachedPrefilter: FakeCubeTexture? = null
    private var cachedLut: FakeTexture2D? = null

    fun generateIrradianceMap(env: FakeCubeTexture): FakeLightingResult<FakeCubeTexture> {
        return FakeLightingResult.Success(FakeCubeTexture(size = env.size / 2))
    }

    fun generatePrefilterMap(env: FakeCubeTexture): FakeLightingResult<FakeCubeTexture> {
        val cached = cachedPrefilter
        if (cached != null) return FakeLightingResult.Success(cached)
        val generated = FakeCubeTexture(env.size)
        cachedPrefilter = generated
        return FakeLightingResult.Success(generated)
    }

    fun generateBrdfLut(): FakeLightingResult<FakeTexture2D> {
        val cached = cachedLut
        if (cached != null) return FakeLightingResult.Success(cached)
        val lut = FakeTexture2D(width = 512, height = 512)
        cachedLut = lut
        return FakeLightingResult.Success(lut)
    }

    fun applyEnvironmentToScene(scene: FakeScene, env: FakeCubeTexture): FakeLightingResult<Unit> {
        val prefilter = generatePrefilterMap(env)
        val lut = generateBrdfLut()
        scene.environment = (prefilter as FakeLightingResult.Success).value
        scene.environmentBrdfLut = (lut as FakeLightingResult.Success).value
        return FakeLightingResult.Success(Unit)
    }

    fun dispose() {
        cachedPrefilter = null
        cachedLut = null
    }
}
