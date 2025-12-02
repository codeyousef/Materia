package io.materia.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for the Window abstraction configuration.
 */
class WindowConfigTest {

    @Test
    fun windowConfig_defaultValues() {
        val config = WindowConfig()
        
        assertEquals("Materia", config.title)
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertTrue(config.resizable)
        assertFalse(config.fullscreen)
        assertTrue(config.vsync)
        assertNull(config.canvasId)
        assertTrue(config.highDpi)
        assertFalse(config.transparent)
    }

    @Test
    fun windowConfig_customTitle() {
        val config = WindowConfig(title = "My 3D Application")
        
        assertEquals("My 3D Application", config.title)
    }

    @Test
    fun windowConfig_customDimensions() {
        val config = WindowConfig(width = 1920, height = 1080)
        
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
    }

    @Test
    fun windowConfig_smallDimensions() {
        val config = WindowConfig(width = 320, height = 240)
        
        assertEquals(320, config.width)
        assertEquals(240, config.height)
    }

    @Test
    fun windowConfig_largeDimensions() {
        val config = WindowConfig(width = 3840, height = 2160)  // 4K
        
        assertEquals(3840, config.width)
        assertEquals(2160, config.height)
    }

    @Test
    fun windowConfig_nonResizable() {
        val config = WindowConfig(resizable = false)
        
        assertFalse(config.resizable)
    }

    @Test
    fun windowConfig_fullscreen() {
        val config = WindowConfig(fullscreen = true)
        
        assertTrue(config.fullscreen)
    }

    @Test
    fun windowConfig_noVsync() {
        val config = WindowConfig(vsync = false)
        
        assertFalse(config.vsync)
    }

    @Test
    fun windowConfig_withCanvasId() {
        val config = WindowConfig(canvasId = "my-canvas")
        
        assertEquals("my-canvas", config.canvasId)
    }

    @Test
    fun windowConfig_noHighDpi() {
        val config = WindowConfig(highDpi = false)
        
        assertFalse(config.highDpi)
    }

    @Test
    fun windowConfig_transparent() {
        val config = WindowConfig(transparent = true)
        
        assertTrue(config.transparent)
    }

    @Test
    fun windowConfig_fullCustomization() {
        val config = WindowConfig(
            title = "Custom Window",
            width = 800,
            height = 600,
            resizable = false,
            fullscreen = false,
            vsync = true,
            canvasId = "custom-canvas",
            highDpi = true,
            transparent = true
        )
        
        assertEquals("Custom Window", config.title)
        assertEquals(800, config.width)
        assertEquals(600, config.height)
        assertFalse(config.resizable)
        assertFalse(config.fullscreen)
        assertTrue(config.vsync)
        assertEquals("custom-canvas", config.canvasId)
        assertTrue(config.highDpi)
        assertTrue(config.transparent)
    }

    @Test
    fun windowConfig_dataClass_equality() {
        val config1 = WindowConfig(title = "Test", width = 800, height = 600)
        val config2 = WindowConfig(title = "Test", width = 800, height = 600)
        val config3 = WindowConfig(title = "Test", width = 1024, height = 768)
        
        assertEquals(config1, config2)
        assertTrue(config1 != config3)
    }

    @Test
    fun windowConfig_dataClass_copy() {
        val original = WindowConfig(title = "Original", width = 800, height = 600)
        val copy = original.copy(title = "Modified")
        
        assertEquals("Modified", copy.title)
        assertEquals(800, copy.width)
        assertEquals(600, copy.height)
    }

    @Test
    fun windowConfig_aspectRatio_16by9() {
        val config = WindowConfig(width = 1920, height = 1080)
        val aspectRatio = config.width.toFloat() / config.height.toFloat()
        
        assertEquals(16f / 9f, aspectRatio, 0.01f)
    }

    @Test
    fun windowConfig_aspectRatio_4by3() {
        val config = WindowConfig(width = 1024, height = 768)
        val aspectRatio = config.width.toFloat() / config.height.toFloat()
        
        assertEquals(4f / 3f, aspectRatio, 0.01f)
    }

    @Test
    fun windowConfig_aspectRatio_1by1() {
        val config = WindowConfig(width = 512, height = 512)
        val aspectRatio = config.width.toFloat() / config.height.toFloat()
        
        assertEquals(1f, aspectRatio, 0.01f)
    }

    @Test
    fun windowConfig_commonResolutions() {
        // HD
        val hd = WindowConfig(width = 1280, height = 720)
        assertEquals(1280, hd.width)
        assertEquals(720, hd.height)
        
        // Full HD
        val fullHd = WindowConfig(width = 1920, height = 1080)
        assertEquals(1920, fullHd.width)
        assertEquals(1080, fullHd.height)
        
        // 2K
        val qhd = WindowConfig(width = 2560, height = 1440)
        assertEquals(2560, qhd.width)
        assertEquals(1440, qhd.height)
        
        // 4K
        val uhd = WindowConfig(width = 3840, height = 2160)
        assertEquals(3840, uhd.width)
        assertEquals(2160, uhd.height)
    }

    @Test
    fun windowEventListener_defaultImplementations() {
        val listener = object : WindowEventListener {}
        
        // These should not throw and have default no-op behavior
        listener.onResize(800, 600)
        listener.onFocusChanged(true)
        listener.onFocusChanged(false)
        
        // Default should allow close
        assertTrue(listener.onCloseRequested())
    }

    @Test
    fun windowEventListener_customOnResize() {
        var resizeWidth = 0
        var resizeHeight = 0
        
        val listener = object : WindowEventListener {
            override fun onResize(width: Int, height: Int) {
                resizeWidth = width
                resizeHeight = height
            }
        }
        
        listener.onResize(1024, 768)
        
        assertEquals(1024, resizeWidth)
        assertEquals(768, resizeHeight)
    }

    @Test
    fun windowEventListener_customOnFocusChanged() {
        var lastFocusState: Boolean? = null
        
        val listener = object : WindowEventListener {
            override fun onFocusChanged(focused: Boolean) {
                lastFocusState = focused
            }
        }
        
        listener.onFocusChanged(true)
        assertEquals(true, lastFocusState)
        
        listener.onFocusChanged(false)
        assertEquals(false, lastFocusState)
    }

    @Test
    fun windowEventListener_customOnCloseRequested() {
        val preventCloseListener = object : WindowEventListener {
            override fun onCloseRequested(): Boolean = false
        }
        
        val allowCloseListener = object : WindowEventListener {
            override fun onCloseRequested(): Boolean = true
        }
        
        assertFalse(preventCloseListener.onCloseRequested())
        assertTrue(allowCloseListener.onCloseRequested())
    }
}
