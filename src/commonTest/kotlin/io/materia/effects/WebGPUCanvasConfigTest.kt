package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * TDD Tests for WebGPUCanvasConfig - Configuration and state management for WebGPU canvas.
 * 
 * Note: Actual WebGPU initialization requires a browser environment.
 * These tests focus on the configuration and state management aspects
 * that can be tested in commonMain.
 * 
 * WebGPUCanvasConfig provides:
 * - Canvas size and DPR management
 * - Configuration options for WebGPU context
 * - Resize handling configuration
 */
class WebGPUCanvasConfigTest {

    // ============ Options Tests ============

    @Test
    fun options_defaultValues() {
        val options = WebGPUCanvasOptions()
        
        assertEquals(AlphaMode.PREMULTIPLIED, options.alphaMode)
        assertEquals(PowerPreference.HIGH_PERFORMANCE, options.powerPreference)
        assertTrue(options.handleResize)
        assertTrue(options.respectDevicePixelRatio)
    }

    @Test
    fun options_customValues() {
        val options = WebGPUCanvasOptions(
            alphaMode = AlphaMode.OPAQUE,
            powerPreference = PowerPreference.LOW_POWER,
            handleResize = false,
            respectDevicePixelRatio = false
        )
        
        assertEquals(AlphaMode.OPAQUE, options.alphaMode)
        assertEquals(PowerPreference.LOW_POWER, options.powerPreference)
        assertFalse(options.handleResize)
        assertFalse(options.respectDevicePixelRatio)
    }

    // ============ Canvas State Tests ============

    @Test
    fun canvasState_creation() {
        val state = CanvasState(
            width = 1920,
            height = 1080,
            devicePixelRatio = 2.0f
        )
        
        assertEquals(1920, state.width)
        assertEquals(1080, state.height)
        assertEquals(2.0f, state.devicePixelRatio, 0.0001f)
    }

    @Test
    fun canvasState_physicalSize() {
        val state = CanvasState(
            width = 1920,
            height = 1080,
            devicePixelRatio = 2.0f
        )
        
        // Physical size should be logical size * DPR
        assertEquals(3840, state.physicalWidth)
        assertEquals(2160, state.physicalHeight)
    }

    @Test
    fun canvasState_aspectRatio() {
        val state = CanvasState(
            width = 1920,
            height = 1080,
            devicePixelRatio = 1.0f
        )
        
        assertEquals(16f / 9f, state.aspectRatio, 0.01f)
    }

    @Test
    fun canvasState_noDpr() {
        val state = CanvasState(
            width = 800,
            height = 600,
            devicePixelRatio = 1.0f
        )
        
        assertEquals(800, state.physicalWidth)
        assertEquals(600, state.physicalHeight)
    }

    // ============ Canvas Config Tests ============

    @Test
    fun canvasConfig_defaultOptions() {
        val config = WebGPUCanvasConfig()
        
        assertNotNull(config.options)
        assertEquals(AlphaMode.PREMULTIPLIED, config.options.alphaMode)
    }

    @Test
    fun canvasConfig_customOptions() {
        val config = WebGPUCanvasConfig(
            options = WebGPUCanvasOptions(
                alphaMode = AlphaMode.OPAQUE
            )
        )
        
        assertEquals(AlphaMode.OPAQUE, config.options.alphaMode)
    }

    @Test
    fun canvasConfig_updateState() {
        val config = WebGPUCanvasConfig()
        
        config.updateState(1920, 1080, 1.5f)
        
        assertEquals(1920, config.state.width)
        assertEquals(1080, config.state.height)
        assertEquals(1.5f, config.state.devicePixelRatio, 0.0001f)
    }

    @Test
    fun canvasConfig_resizeCallback() {
        var callbackCalled = false
        var lastWidth = 0
        var lastHeight = 0
        
        val config = WebGPUCanvasConfig()
        config.onResize = { width, height ->
            callbackCalled = true
            lastWidth = width
            lastHeight = height
        }
        
        config.updateState(1920, 1080, 1.0f)
        
        assertTrue(callbackCalled)
        assertEquals(1920, lastWidth)
        assertEquals(1080, lastHeight)
    }

    @Test
    fun canvasConfig_resizeCallback_notCalledIfSameSize() {
        var callCount = 0
        
        val config = WebGPUCanvasConfig()
        config.onResize = { _, _ -> callCount++ }
        
        config.updateState(1920, 1080, 1.0f)
        assertEquals(1, callCount)
        
        // Same size should not trigger callback
        config.updateState(1920, 1080, 1.0f)
        assertEquals(1, callCount)
    }

    @Test
    fun canvasConfig_resizeCallback_calledOnDprChange() {
        var callCount = 0
        
        val config = WebGPUCanvasConfig()
        config.onResize = { _, _ -> callCount++ }
        
        config.updateState(1920, 1080, 1.0f)
        assertEquals(1, callCount)
        
        // DPR change should trigger callback (physical size changed)
        config.updateState(1920, 1080, 2.0f)
        assertEquals(2, callCount)
    }

    // ============ Preferred Format Tests ============

    @Test
    fun preferredFormat_bgra8unorm() {
        assertEquals("bgra8unorm", TextureFormat.BGRA8_UNORM.value)
    }

    @Test
    fun preferredFormat_rgba8unorm() {
        assertEquals("rgba8unorm", TextureFormat.RGBA8_UNORM.value)
    }

    // ============ Error State Tests ============

    @Test
    fun initResult_success() {
        val result = InitResult.Success
        assertTrue(result.isSuccess)
    }

    @Test
    fun initResult_adapterNotFound() {
        val result = InitResult.AdapterNotFound
        assertFalse(result.isSuccess)
        assertEquals("WebGPU adapter not found", result.errorMessage)
    }

    @Test
    fun initResult_deviceCreationFailed() {
        val result = InitResult.DeviceCreationFailed("Limit exceeded")
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage?.contains("Device creation failed") == true)
    }

    @Test
    fun initResult_webgpuNotSupported() {
        val result = InitResult.WebGPUNotSupported
        assertFalse(result.isSuccess)
        assertEquals("WebGPU is not supported in this browser", result.errorMessage)
    }
}
