package io.kreekt.examples.embeddinggalaxy

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmbeddingGalaxySceneTest {

    @Test
    fun qualitySwitchAdjustsPointCount() {
        val config = EmbeddingGalaxyScene.Config(
            basePointCount = 1_200,
            clusterCount = 4,
            seed = 32L
        )
        val scene = EmbeddingGalaxyScene(config)

        assertEquals(config.basePointCount, scene.activePointCount)

        scene.setQuality(EmbeddingGalaxyScene.Quality.Performance)
        assertTrue(scene.activePointCount < config.basePointCount)

        scene.setQuality(EmbeddingGalaxyScene.Quality.Fidelity)
        assertTrue(scene.activePointCount >= config.basePointCount)

        val before = scene.activePointCount
        scene.triggerQuery()
        assertEquals(before, scene.activePointCount)
    }

    @Test
    fun headlessBootProducesStubLog() = runTest {
        val example = EmbeddingGalaxyExample(
            sceneConfig = EmbeddingGalaxyScene.Config(
                basePointCount = 512,
                clusterCount = 3,
                seed = 9L
            ),
            enableFxaa = true
        )
        val result = example.boot()
        val log = result.log

        assertEquals("Stub Device", log.deviceName)
        assertEquals(result.runtime.activePointCount, log.pointCount)
        assertEquals(EmbeddingGalaxyScene.Quality.Balanced, log.quality)
        assertEquals(512, log.pointCount)
        assertTrue(log.fxaaEnabled)
    }

    @Test
    fun fxaaTogglePersistsInHeadlessRuntime() = runTest {
        val example = EmbeddingGalaxyExample(enableFxaa = true)
        val runtime = example.boot().runtime
        assertTrue(runtime.fxaaEnabled)
        runtime.toggleFxaa()
        assertTrue(!runtime.fxaaEnabled)
    }
}
