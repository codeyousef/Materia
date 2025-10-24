package io.kreekt.examples.forcegraph

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceGraphSceneTest {

    @Test
    fun toggleCyclesMode() {
        val scene = ForceGraphScene(
            ForceGraphScene.Config(nodeCount = 300, edgeCount = 600, clusterCount = 4, seed = 9L)
        )

        val initialMetrics = scene.metrics()
        assertEquals(ForceGraphScene.Mode.TfIdf, initialMetrics.mode)

        scene.triggerToggle()
        repeat(60) { scene.update(1f / 60f) }
        val toggled = scene.metrics()
        assertEquals(ForceGraphScene.Mode.Semantic, toggled.mode)
    }

    @Test
    fun headlessBootProvidesLog() = runTest {
        val example = ForceGraphExample(
            sceneConfig = ForceGraphScene.Config(nodeCount = 256, edgeCount = 512, clusterCount = 3, seed = 5L)
        )
        val result = example.boot()
        val log = result.log
        assertEquals("Stub Device", log.deviceName)
        assertEquals(256, log.nodeCount)
        assertEquals(512, log.edgeCount)
        assertTrue(log.frameTimeMs == 0.0)
    }
}
