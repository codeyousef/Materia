package io.materia.engine.renderer

import io.materia.core.math.Color
import io.materia.gpu.GpuPowerPreference
import io.materia.gpu.GpuTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Comprehensive tests for the WebGPURenderer configuration and stats.
 */
class WebGPURendererConfigTest {

    // ========== WebGPURendererConfig Tests ==========

    @Test
    fun config_defaultValues() {
        val config = WebGPURendererConfig()
        
        assertTrue(config.depthTest)
        assertEquals(Color(0f, 0f, 0f), config.clearColor)
        assertEquals(1f, config.clearAlpha)
        assertEquals(GpuPowerPreference.HIGH_PERFORMANCE, config.powerPreference)
        assertTrue(config.autoResize)
        assertNull(config.preferredFormat)
        assertEquals(1, config.antialias)
        assertFalse(config.debug)
    }

    @Test
    fun config_customValues() {
        val config = WebGPURendererConfig(
            depthTest = false,
            clearColor = Color(0.5f, 0.5f, 0.5f),
            clearAlpha = 0.8f,
            powerPreference = GpuPowerPreference.LOW_POWER,
            autoResize = false,
            preferredFormat = GpuTextureFormat.RGBA8_UNORM,
            antialias = 4,
            debug = true
        )
        
        assertFalse(config.depthTest)
        assertEquals(Color(0.5f, 0.5f, 0.5f), config.clearColor)
        assertEquals(0.8f, config.clearAlpha)
        assertEquals(GpuPowerPreference.LOW_POWER, config.powerPreference)
        assertFalse(config.autoResize)
        assertEquals(GpuTextureFormat.RGBA8_UNORM, config.preferredFormat)
        assertEquals(4, config.antialias)
        assertTrue(config.debug)
    }

    @Test
    fun config_depthTest_canBeDisabled() {
        val configWithDepth = WebGPURendererConfig(depthTest = true)
        val configWithoutDepth = WebGPURendererConfig(depthTest = false)
        
        assertTrue(configWithDepth.depthTest)
        assertFalse(configWithoutDepth.depthTest)
    }

    @Test
    fun config_clearColor_variousValues() {
        val black = WebGPURendererConfig(clearColor = Color(0f, 0f, 0f))
        val white = WebGPURendererConfig(clearColor = Color(1f, 1f, 1f))
        val red = WebGPURendererConfig(clearColor = Color(1f, 0f, 0f))
        val custom = WebGPURendererConfig(clearColor = Color(0.2f, 0.4f, 0.6f))
        
        assertEquals(Color(0f, 0f, 0f), black.clearColor)
        assertEquals(Color(1f, 1f, 1f), white.clearColor)
        assertEquals(Color(1f, 0f, 0f), red.clearColor)
        assertEquals(Color(0.2f, 0.4f, 0.6f), custom.clearColor)
    }

    @Test
    fun config_clearAlpha_range() {
        val opaque = WebGPURendererConfig(clearAlpha = 1f)
        val transparent = WebGPURendererConfig(clearAlpha = 0f)
        val semi = WebGPURendererConfig(clearAlpha = 0.5f)
        
        assertEquals(1f, opaque.clearAlpha)
        assertEquals(0f, transparent.clearAlpha)
        assertEquals(0.5f, semi.clearAlpha)
    }

    @Test
    fun config_powerPreference_values() {
        val highPerf = WebGPURendererConfig(powerPreference = GpuPowerPreference.HIGH_PERFORMANCE)
        val lowPower = WebGPURendererConfig(powerPreference = GpuPowerPreference.LOW_POWER)
        
        assertEquals(GpuPowerPreference.HIGH_PERFORMANCE, highPerf.powerPreference)
        assertEquals(GpuPowerPreference.LOW_POWER, lowPower.powerPreference)
    }

    @Test
    fun config_antialias_values() {
        val noAA = WebGPURendererConfig(antialias = 1)
        val msaa2x = WebGPURendererConfig(antialias = 2)
        val msaa4x = WebGPURendererConfig(antialias = 4)
        val msaa8x = WebGPURendererConfig(antialias = 8)
        
        assertEquals(1, noAA.antialias)
        assertEquals(2, msaa2x.antialias)
        assertEquals(4, msaa4x.antialias)
        assertEquals(8, msaa8x.antialias)
    }

    @Test
    fun config_preferredFormat_nullByDefault() {
        val config = WebGPURendererConfig()
        
        assertNull(config.preferredFormat)
    }

    @Test
    fun config_preferredFormat_canBeSet() {
        val bgra = WebGPURendererConfig(preferredFormat = GpuTextureFormat.BGRA8_UNORM)
        val rgba = WebGPURendererConfig(preferredFormat = GpuTextureFormat.RGBA8_UNORM)
        
        assertEquals(GpuTextureFormat.BGRA8_UNORM, bgra.preferredFormat)
        assertEquals(GpuTextureFormat.RGBA8_UNORM, rgba.preferredFormat)
    }

    // ========== WebGPURenderStats Tests ==========

    @Test
    fun stats_defaultValues() {
        val stats = WebGPURenderStats()
        
        assertEquals(0L, stats.frameCount)
        assertEquals(0, stats.drawCalls)
        assertEquals(0, stats.triangles)
        assertEquals(0, stats.textureBinds)
        assertEquals(0, stats.pipelineSwitches)
        assertEquals(0f, stats.frameTime)
    }

    @Test
    fun stats_frameCount_canIncrement() {
        val stats = WebGPURenderStats()
        
        stats.frameCount = 1
        assertEquals(1L, stats.frameCount)
        
        stats.frameCount = 100
        assertEquals(100L, stats.frameCount)
        
        stats.frameCount = Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, stats.frameCount)
    }

    @Test
    fun stats_drawCalls_canBeSet() {
        val stats = WebGPURenderStats()
        
        stats.drawCalls = 42
        assertEquals(42, stats.drawCalls)
        
        stats.drawCalls = 0
        assertEquals(0, stats.drawCalls)
    }

    @Test
    fun stats_triangles_canBeSet() {
        val stats = WebGPURenderStats()
        
        stats.triangles = 10000
        assertEquals(10000, stats.triangles)
        
        stats.triangles = 1000000
        assertEquals(1000000, stats.triangles)
    }

    @Test
    fun stats_textureBinds_canBeSet() {
        val stats = WebGPURenderStats()
        
        stats.textureBinds = 8
        assertEquals(8, stats.textureBinds)
    }

    @Test
    fun stats_pipelineSwitches_canBeSet() {
        val stats = WebGPURenderStats()
        
        stats.pipelineSwitches = 5
        assertEquals(5, stats.pipelineSwitches)
    }

    @Test
    fun stats_frameTime_canBeSet() {
        val stats = WebGPURenderStats()
        
        stats.frameTime = 16.67f  // ~60 FPS
        assertEquals(16.67f, stats.frameTime)
        
        stats.frameTime = 33.33f  // ~30 FPS
        assertEquals(33.33f, stats.frameTime)
    }

    @Test
    fun stats_customInitialization() {
        val stats = WebGPURenderStats(
            frameCount = 1000,
            drawCalls = 50,
            triangles = 100000,
            textureBinds = 16,
            pipelineSwitches = 10,
            frameTime = 8.33f
        )
        
        assertEquals(1000L, stats.frameCount)
        assertEquals(50, stats.drawCalls)
        assertEquals(100000, stats.triangles)
        assertEquals(16, stats.textureBinds)
        assertEquals(10, stats.pipelineSwitches)
        assertEquals(8.33f, stats.frameTime)
    }

    @Test
    fun stats_canBeReset() {
        val stats = WebGPURenderStats(
            frameCount = 1000,
            drawCalls = 50,
            triangles = 100000
        )
        
        // Reset to defaults
        stats.frameCount = 0
        stats.drawCalls = 0
        stats.triangles = 0
        
        assertEquals(0L, stats.frameCount)
        assertEquals(0, stats.drawCalls)
        assertEquals(0, stats.triangles)
    }

    @Test
    fun stats_dataClass_equality() {
        val stats1 = WebGPURenderStats(frameCount = 10, drawCalls = 5)
        val stats2 = WebGPURenderStats(frameCount = 10, drawCalls = 5)
        val stats3 = WebGPURenderStats(frameCount = 10, drawCalls = 6)
        
        assertEquals(stats1, stats2)
        assertTrue(stats1 != stats3)
    }

    @Test
    fun stats_dataClass_copy() {
        val original = WebGPURenderStats(
            frameCount = 100,
            drawCalls = 20,
            triangles = 5000
        )
        
        val copy = original.copy(triangles = 10000)
        
        assertEquals(100L, copy.frameCount)
        assertEquals(20, copy.drawCalls)
        assertEquals(10000, copy.triangles)
    }
}
