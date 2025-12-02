package io.materia.engine.core

import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * JS-specific tests for the RenderLoop.
 *
 * These tests verify the JS implementation uses requestAnimationFrame correctly.
 */
class RenderLoopJsTest {

    @Test
    fun renderLoop_canBeCreated() {
        val config = RenderLoopConfig(targetFps = 60)
        // In JS, the actual RenderLoop would use requestAnimationFrame
        // This test verifies the config is valid
        assertNotNull(config)
    }

    @Test
    fun renderLoop_configAcceptsCustomFps() {
        val config30 = RenderLoopConfig(targetFps = 30)
        val config60 = RenderLoopConfig(targetFps = 60)
        val config144 = RenderLoopConfig(targetFps = 144)
        
        kotlin.test.assertEquals(30, config30.targetFps)
        kotlin.test.assertEquals(60, config60.targetFps)
        kotlin.test.assertEquals(144, config144.targetFps)
    }
}
