package io.kreekt.examples.forcegraph

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForceGraphSceneTest {

    @Test
    fun toggleCyclesMode() {
        val layout = ForceGraphLayoutGenerator.generate(
            ForceGraphScene.Config(nodeCount = 300, edgeCount = 600, clusterCount = 4, seed = 9L)
        )
        val scene = ForceGraphScene(layout)

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

    @Test
    fun layoutGenerationIsDeterministic() {
        val config = ForceGraphScene.Config(nodeCount = 32, edgeCount = 48, clusterCount = 3, seed = 17L)
        val layoutA = ForceGraphLayoutGenerator.generate(config)
        val layoutB = ForceGraphLayoutGenerator.generate(config)

        assertEquals(layoutA, layoutB)
        assertContentEquals(layoutA.clusterAssignments, layoutB.clusterAssignments)
        assertContentEquals(layoutA.tfidfPositions, layoutB.tfidfPositions)
        assertContentEquals(layoutA.semanticPositions, layoutB.semanticPositions)

        val encoded = Json.encodeToString(layoutA)
        val decoded = Json.decodeFromString(ForceGraphLayout.serializer(), encoded)
        assertEquals(layoutA, decoded)
    }
}
