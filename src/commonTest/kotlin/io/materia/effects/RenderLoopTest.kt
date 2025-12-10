package io.materia.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * TDD Tests for RenderLoop - Animation loop management utility.
 * 
 * RenderLoop provides:
 * - Frame timing information (delta time, total time, frame count)
 * - Time scaling for slow motion / fast forward effects
 * - Pause/resume functionality
 * - Clean callback-based API
 */
class RenderLoopTest {

    // ============ Creation Tests ============

    @Test
    fun create_withCallback() {
        var called = false
        val loop = RenderLoop { frame ->
            called = true
        }
        
        assertNotNull(loop)
        assertFalse(loop.isRunning)
    }

    @Test
    fun create_defaultTimeScale() {
        val loop = RenderLoop { }
        assertEquals(1.0f, loop.timeScale, 0.0001f)
    }

    @Test
    fun create_defaultNotPaused() {
        val loop = RenderLoop { }
        assertFalse(loop.isPaused)
    }

    // ============ FrameInfo Tests ============

    @Test
    fun frameInfo_initialValues() {
        val frame = FrameInfo(
            deltaTime = 0.016f,
            totalTime = 0.0f,
            realTime = 0.0f,
            frameCount = 0
        )
        
        assertEquals(0.016f, frame.deltaTime, 0.0001f)
        assertEquals(0.0f, frame.totalTime, 0.0001f)
        assertEquals(0.0f, frame.realTime, 0.0001f)
        assertEquals(0, frame.frameCount)
    }

    @Test
    fun frameInfo_fps() {
        val frame60fps = FrameInfo(deltaTime = 1f / 60f, totalTime = 0f, realTime = 0f, frameCount = 0)
        assertEquals(60f, frame60fps.fps, 1f)
        
        val frame30fps = FrameInfo(deltaTime = 1f / 30f, totalTime = 0f, realTime = 0f, frameCount = 0)
        assertEquals(30f, frame30fps.fps, 1f)
    }

    // ============ Time Scale Tests ============

    @Test
    fun timeScale_affectsTotalTime() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.timeScale = 0.5f
        
        // Simulate frames
        loop.simulateFrame(0.016f)  // 16ms delta
        
        // With 0.5 time scale, totalTime should be half of realTime
        assertNotNull(lastFrame)
        assertEquals(0.016f, lastFrame!!.realTime, 0.0001f)
        assertEquals(0.008f, lastFrame!!.totalTime, 0.0001f)  // Half due to timeScale
    }

    @Test
    fun timeScale_doubleSpeed() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.timeScale = 2.0f
        loop.simulateFrame(0.016f)
        
        assertEquals(0.016f, lastFrame!!.realTime, 0.0001f)
        assertEquals(0.032f, lastFrame!!.totalTime, 0.0001f)  // Double due to timeScale
    }

    @Test
    fun timeScale_zero_pausesTime() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.timeScale = 0.0f
        loop.simulateFrame(0.016f)
        loop.simulateFrame(0.016f)
        
        assertEquals(0.032f, lastFrame!!.realTime, 0.0001f)
        assertEquals(0.0f, lastFrame!!.totalTime, 0.0001f)  // No time passes
    }

    @Test
    fun timeScale_negative_reversesTime() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        // Start with some time
        loop.simulateFrame(0.1f)
        assertEquals(0.1f, lastFrame!!.totalTime, 0.0001f)
        
        // Reverse time
        loop.timeScale = -1.0f
        loop.simulateFrame(0.05f)
        
        assertEquals(0.05f, lastFrame!!.totalTime, 0.0001f)  // 0.1 - 0.05
    }

    // ============ Pause/Resume Tests ============

    @Test
    fun pause_stopsTimeAccumulation() {
        var frameCount = 0
        val loop = RenderLoop { frame ->
            frameCount++
        }
        
        loop.simulateFrame(0.016f)
        assertEquals(1, frameCount)
        
        loop.pause()
        assertTrue(loop.isPaused)
        
        // Frame should still be called but time shouldn't advance
        loop.simulateFrame(0.016f)
        assertEquals(2, frameCount)  // Callback still called
    }

    @Test
    fun pause_preservesTotalTime() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.simulateFrame(0.1f)
        val timeBeforePause = lastFrame!!.totalTime
        
        loop.pause()
        loop.simulateFrame(0.1f)
        loop.simulateFrame(0.1f)
        
        // Total time should not advance while paused
        assertEquals(timeBeforePause, lastFrame!!.totalTime, 0.0001f)
    }

    @Test
    fun resume_continuesTimeAccumulation() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.simulateFrame(0.1f)
        loop.pause()
        loop.simulateFrame(0.1f)
        loop.resume()
        
        assertFalse(loop.isPaused)
        
        loop.simulateFrame(0.1f)
        assertEquals(0.2f, lastFrame!!.totalTime, 0.0001f)
    }

    // ============ Frame Count Tests ============

    @Test
    fun frameCount_incrementsEachFrame() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.simulateFrame(0.016f)
        assertEquals(1, lastFrame!!.frameCount)
        
        loop.simulateFrame(0.016f)
        assertEquals(2, lastFrame!!.frameCount)
        
        loop.simulateFrame(0.016f)
        assertEquals(3, lastFrame!!.frameCount)
    }

    @Test
    fun frameCount_incrementsWhenPaused() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.simulateFrame(0.016f)
        loop.pause()
        loop.simulateFrame(0.016f)
        
        // Frame count still increments when paused
        assertEquals(2, lastFrame!!.frameCount)
    }

    // ============ Delta Time Tests ============

    @Test
    fun deltaTime_respectsTimeScale() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.timeScale = 0.5f
        loop.simulateFrame(0.016f)
        
        // Delta time should be scaled
        assertEquals(0.008f, lastFrame!!.deltaTime, 0.0001f)
    }

    @Test
    fun deltaTime_zeroWhenPaused() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.pause()
        loop.simulateFrame(0.016f)
        
        // Delta time should be zero when paused
        assertEquals(0.0f, lastFrame!!.deltaTime, 0.0001f)
    }

    // ============ Reset Tests ============

    @Test
    fun reset_clearsAllTiming() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.simulateFrame(0.1f)
        loop.simulateFrame(0.1f)
        loop.simulateFrame(0.1f)
        
        loop.reset()
        loop.simulateFrame(0.016f)
        
        assertEquals(0.016f, lastFrame!!.totalTime, 0.0001f)
        assertEquals(0.016f, lastFrame!!.realTime, 0.0001f)
        assertEquals(1, lastFrame!!.frameCount)
    }

    // ============ Running State Tests ============

    @Test
    fun start_setsRunningTrue() {
        val loop = RenderLoop { }
        
        assertFalse(loop.isRunning)
        loop.start()
        assertTrue(loop.isRunning)
    }

    @Test
    fun stop_setsRunningFalse() {
        val loop = RenderLoop { }
        
        loop.start()
        assertTrue(loop.isRunning)
        
        loop.stop()
        assertFalse(loop.isRunning)
    }

    // ============ Max Delta Time Tests ============

    @Test
    fun maxDeltaTime_clampsLargeDeltaTimes() {
        var lastFrame: FrameInfo? = null
        val loop = RenderLoop { frame ->
            lastFrame = frame
        }
        
        loop.maxDeltaTime = 0.1f
        loop.simulateFrame(0.5f)  // Simulate 500ms frame (lag spike)
        
        // Should be clamped to max
        assertEquals(0.1f, lastFrame!!.deltaTime, 0.0001f)
    }

    @Test
    fun maxDeltaTime_default() {
        val loop = RenderLoop { }
        
        // Default should be reasonable (e.g., 1/10th of a second)
        assertTrue(loop.maxDeltaTime > 0f)
        assertTrue(loop.maxDeltaTime <= 0.5f)
    }
}
