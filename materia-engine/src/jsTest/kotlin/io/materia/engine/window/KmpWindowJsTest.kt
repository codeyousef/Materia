package io.materia.engine.window

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals

/**
 * JS-specific tests for the KmpWindow (Canvas-based).
 */
class KmpWindowJsTest {

    @Test
    fun windowConfig_defaultsForWeb() {
        val config = WindowConfig()
        
        // Default dimensions
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
    }

    @Test
    fun windowConfig_canSpecifyDimensions() {
        val config = WindowConfig(
            width = 1920,
            height = 1080,
            title = "Test Canvas"
        )
        
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals("Test Canvas", config.title)
    }

    @Test
    fun windowConfig_fullscreenOption() {
        val windowed = WindowConfig(fullscreen = false)
        val fullscreen = WindowConfig(fullscreen = true)
        
        kotlin.test.assertFalse(windowed.fullscreen)
        kotlin.test.assertTrue(fullscreen.fullscreen)
    }
}
