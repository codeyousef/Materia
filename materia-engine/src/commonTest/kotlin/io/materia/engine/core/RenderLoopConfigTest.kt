package io.materia.engine.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for the RenderLoop configuration.
 */
class RenderLoopConfigTest {

    @Test
    fun renderLoopConfig_defaultValues() {
        val config = RenderLoopConfig()
        
        assertEquals(0, config.targetFps)
        assertFalse(config.fixedTimestep)
        assertEquals(1f / 60f, config.fixedDeltaTime, 0.0001f)
        assertEquals(5, config.maxUpdatesPerFrame)
    }

    @Test
    fun renderLoopConfig_customTargetFps() {
        val config30 = RenderLoopConfig(targetFps = 30)
        val config60 = RenderLoopConfig(targetFps = 60)
        val config120 = RenderLoopConfig(targetFps = 120)
        val configUncapped = RenderLoopConfig(targetFps = 0)
        
        assertEquals(30, config30.targetFps)
        assertEquals(60, config60.targetFps)
        assertEquals(120, config120.targetFps)
        assertEquals(0, configUncapped.targetFps)
    }

    @Test
    fun renderLoopConfig_fixedTimestep() {
        val configFixed = RenderLoopConfig(fixedTimestep = true)
        val configVariable = RenderLoopConfig(fixedTimestep = false)
        
        assertTrue(configFixed.fixedTimestep)
        assertFalse(configVariable.fixedTimestep)
    }

    @Test
    fun renderLoopConfig_customFixedDeltaTime() {
        val config60Hz = RenderLoopConfig(fixedDeltaTime = 1f / 60f)
        val config30Hz = RenderLoopConfig(fixedDeltaTime = 1f / 30f)
        val config120Hz = RenderLoopConfig(fixedDeltaTime = 1f / 120f)
        
        assertEquals(1f / 60f, config60Hz.fixedDeltaTime, 0.0001f)
        assertEquals(1f / 30f, config30Hz.fixedDeltaTime, 0.0001f)
        assertEquals(1f / 120f, config120Hz.fixedDeltaTime, 0.0001f)
    }

    @Test
    fun renderLoopConfig_customMaxUpdatesPerFrame() {
        val config1 = RenderLoopConfig(maxUpdatesPerFrame = 1)
        val config5 = RenderLoopConfig(maxUpdatesPerFrame = 5)
        val config10 = RenderLoopConfig(maxUpdatesPerFrame = 10)
        
        assertEquals(1, config1.maxUpdatesPerFrame)
        assertEquals(5, config5.maxUpdatesPerFrame)
        assertEquals(10, config10.maxUpdatesPerFrame)
    }

    @Test
    fun renderLoopConfig_fullCustomization() {
        val config = RenderLoopConfig(
            targetFps = 144,
            fixedTimestep = true,
            fixedDeltaTime = 1f / 60f,
            maxUpdatesPerFrame = 8
        )
        
        assertEquals(144, config.targetFps)
        assertTrue(config.fixedTimestep)
        assertEquals(1f / 60f, config.fixedDeltaTime, 0.0001f)
        assertEquals(8, config.maxUpdatesPerFrame)
    }

    @Test
    fun renderLoopConfig_physicsConfig() {
        // Common physics configuration: fixed 60Hz updates
        val physicsConfig = RenderLoopConfig(
            fixedTimestep = true,
            fixedDeltaTime = 1f / 60f,
            maxUpdatesPerFrame = 5
        )
        
        assertTrue(physicsConfig.fixedTimestep)
        
        // Calculate expected updates per second
        val updatesPerSecond = 1f / physicsConfig.fixedDeltaTime
        assertEquals(60f, updatesPerSecond, 0.01f)
    }

    @Test
    fun renderLoopConfig_variableTimestepConfig() {
        // Variable timestep for smooth rendering
        val smoothConfig = RenderLoopConfig(
            targetFps = 0,  // Uncapped / vsync
            fixedTimestep = false
        )
        
        assertEquals(0, smoothConfig.targetFps)
        assertFalse(smoothConfig.fixedTimestep)
    }

    @Test
    fun renderLoopConfig_dataClass_equality() {
        val config1 = RenderLoopConfig(targetFps = 60, fixedTimestep = true)
        val config2 = RenderLoopConfig(targetFps = 60, fixedTimestep = true)
        val config3 = RenderLoopConfig(targetFps = 30, fixedTimestep = true)
        
        assertEquals(config1, config2)
        assertTrue(config1 != config3)
    }

    @Test
    fun renderLoopConfig_dataClass_copy() {
        val original = RenderLoopConfig(targetFps = 60, fixedTimestep = false)
        val copy = original.copy(fixedTimestep = true)
        
        assertEquals(60, copy.targetFps)
        assertTrue(copy.fixedTimestep)
    }

    @Test
    fun renderLoopConfig_frameTimeCalculation() {
        val config60 = RenderLoopConfig(targetFps = 60)
        val config30 = RenderLoopConfig(targetFps = 30)
        
        // Calculate expected frame time in milliseconds
        val expectedFrameTime60 = if (config60.targetFps > 0) 1000f / config60.targetFps else 0f
        val expectedFrameTime30 = if (config30.targetFps > 0) 1000f / config30.targetFps else 0f
        
        assertEquals(16.67f, expectedFrameTime60, 0.1f)  // ~16.67ms for 60 FPS
        assertEquals(33.33f, expectedFrameTime30, 0.1f)  // ~33.33ms for 30 FPS
    }

    @Test
    fun renderLoopConfig_spiralOfDeathPrevention() {
        // maxUpdatesPerFrame prevents spiral of death
        val config = RenderLoopConfig(
            fixedTimestep = true,
            fixedDeltaTime = 1f / 60f,
            maxUpdatesPerFrame = 3  // Only allow 3 updates per frame
        )
        
        // Simulate a very slow frame (200ms)
        val slowFrameDelta = 0.2f
        val expectedUpdates = slowFrameDelta / config.fixedDeltaTime  // ~12 updates needed
        
        // But we should be capped at maxUpdatesPerFrame
        val actualMaxUpdates = config.maxUpdatesPerFrame
        
        assertTrue(expectedUpdates > actualMaxUpdates)
        assertEquals(3, actualMaxUpdates)
    }
}
