package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import io.materia.tools.editor.animation.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for Timeline interface - professional animation timeline with real-time scrubbing
 */
class TimelineTest {

    private lateinit var timeline: Timeline
    private lateinit var animationTimeline: AnimationTimeline

    @BeforeTest
    fun setup() {
        // Create test animation with position track
        animationTimeline = AnimationTimeline.createPositionAnimation(
            name = "Test Animation",
            targetPath = "scene.objects.cube1",
            startPosition = Vector3(0.0f, 0.0f, 0.0f),
            endPosition = Vector3(10.0f, 5.0f, 2.0f),
            duration = 3.0f
        )

        timeline = Timeline()
    }

    @Test
    fun `Timeline initializes with default state`() = runTest {
        assertEquals(0.0f, timeline.currentTime.first())
        assertFalse(timeline.isPlaying.first())
        assertEquals(TimelinePlaybackState.STOPPED, timeline.playbackState.first())
        assertEquals(1.0f, timeline.playbackSpeed.first())
    }

    @Test
    fun `Timeline loads animation correctly`() = runTest {
        timeline.loadAnimation(animationTimeline)

        val loadedAnimation = timeline.currentAnimation.first()
        assertNotNull(loadedAnimation)
        assertEquals("Test Animation", loadedAnimation.name)
        assertEquals(3.0f, loadedAnimation.duration)
        assertEquals(30, loadedAnimation.frameRate)
    }

    @Test
    fun `Timeline scrubbing updates current time`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Scrub to middle of timeline
        timeline.scrubTo(1.5f)
        assertEquals(1.5f, timeline.currentTime.first())

        // Scrub beyond duration should clamp
        timeline.scrubTo(5.0f)
        assertEquals(3.0f, timeline.currentTime.first())

        // Scrub to negative should clamp to 0
        timeline.scrubTo(-1.0f)
        assertEquals(0.0f, timeline.currentTime.first())
    }

    @Test
    fun `Timeline frame navigation works correctly`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Go to specific frame
        timeline.goToFrame(45) // 1.5 seconds at 30fps
        assertEquals(1.5f, timeline.currentTime.first(), 0.01f)

        // Step forward
        timeline.stepForward()
        assertEquals(1.5333f, timeline.currentTime.first(), 0.01f)

        // Step backward
        timeline.stepBackward()
        timeline.stepBackward()
        assertEquals(1.4667f, timeline.currentTime.first(), 0.01f)
    }

    @Test
    fun `Timeline playback state management`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Start playback
        timeline.play()
        assertTrue(timeline.isPlaying.first())
        assertEquals(TimelinePlaybackState.PLAYING, timeline.playbackState.first())

        // Pause playback
        timeline.pause()
        assertFalse(timeline.isPlaying.first())
        assertEquals(TimelinePlaybackState.PAUSED, timeline.playbackState.first())

        // Stop playback
        timeline.stop()
        assertFalse(timeline.isPlaying.first())
        assertEquals(TimelinePlaybackState.STOPPED, timeline.playbackState.first())
        assertEquals(0.0f, timeline.currentTime.first())
    }

    @Test
    fun `Timeline loop mode functionality`() = runTest {
        timeline.loadAnimation(animationTimeline)
        timeline.setLoopMode(true)

        assertTrue(timeline.isLooping.first())

        // When reaching end while playing, should loop back
        timeline.scrubTo(3.0f)
        timeline.play()

        // Simulate reaching end (this would happen in actual playback)
        timeline.scrubTo(3.1f) // Beyond duration
        assertEquals(0.1f, timeline.currentTime.first(), 0.01f) // Should wrap around
    }

    @Test
    fun `Timeline playback speed adjustment`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Set double speed
        timeline.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, timeline.playbackSpeed.first())

        // Set half speed
        timeline.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, timeline.playbackSpeed.first())

        // Invalid speeds should be clamped
        timeline.setPlaybackSpeed(-1.0f)
        assertEquals(0.1f, timeline.playbackSpeed.first()) // Minimum speed

        timeline.setPlaybackSpeed(10.0f)
        assertEquals(5.0f, timeline.playbackSpeed.first()) // Maximum speed
    }

    @Test
    fun `Timeline marker navigation`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Go to marker
        timeline.goToMarker("Start")
        assertEquals(0.0f, timeline.currentTime.first())

        timeline.goToMarker("End")
        assertEquals(3.0f, timeline.currentTime.first())

        // Navigate to next/previous markers
        timeline.scrubTo(0.0f)
        timeline.goToNextMarker()
        assertEquals(3.0f, timeline.currentTime.first())

        timeline.goToPreviousMarker()
        assertEquals(0.0f, timeline.currentTime.first())
    }

    @Test
    fun `Timeline snap to frames functionality`() = runTest {
        timeline.loadAnimation(animationTimeline)
        timeline.setSnapToFrames(true)

        // Scrubbing should snap to nearest frame
        timeline.scrubTo(1.52f) // Between frames
        assertEquals(1.5333f, timeline.currentTime.first(), 0.01f) // Should snap to frame 46

        // Disable snapping
        timeline.setSnapToFrames(false)
        timeline.scrubTo(1.52f)
        assertEquals(1.52f, timeline.currentTime.first(), 0.01f) // Should be exact
    }

    @Test
    fun `Timeline zoom functionality`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Set zoom level
        timeline.setZoom(2.0f)
        assertEquals(2.0f, timeline.zoomLevel.first())

        // Zoom with focal point
        timeline.zoomAtTime(1.5f, 1.5f) // Zoom 1.5x at 1.5 seconds
        assertEquals(1.5f, timeline.zoomLevel.first())
        // Verify focal point is maintained (implementation specific)

        // Reset zoom
        timeline.resetZoom()
        assertEquals(1.0f, timeline.zoomLevel.first())
    }

    @Test
    fun `Timeline selection functionality`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Select time range
        timeline.setSelection(0.5f, 2.5f)
        val selection = timeline.selectedTimeRange.first()
        assertNotNull(selection)
        assertEquals(0.5f, selection.start)
        assertEquals(2.5f, selection.end)

        // Clear selection
        timeline.clearSelection()
        assertNull(timeline.selectedTimeRange.first())
    }

    @Test
    fun `Timeline evaluation at current time`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Evaluate at start
        timeline.scrubTo(0.0f)
        val startValues = timeline.evaluateAtCurrentTime().first()
        assertTrue(startValues.isNotEmpty())

        // Evaluate at middle
        timeline.scrubTo(1.5f)
        val middleValues = timeline.evaluateAtCurrentTime().first()
        assertTrue(middleValues.isNotEmpty())

        // Values should be different (animation is happening)
        assertNotEquals(startValues, middleValues)
    }

    @Test
    fun `Timeline performance with frequent updates`() = runTest {
        timeline.loadAnimation(animationTimeline)

        val startTime = kotlinx.datetime.Clock.System.now()

        // Simulate rapid scrubbing (60fps)
        repeat(60) { frame ->
            val time = frame / 60.0f * 3.0f // 3 second animation
            timeline.scrubTo(time)
        }

        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = endTime - startTime

        // Should complete in reasonable time (less than 100ms for 60 updates)
        assertTrue(duration.inWholeMilliseconds < 100)
    }

    @Test
    fun `Timeline handles empty animation gracefully`() = runTest {
        val emptyAnimation = AnimationTimeline.createEmpty("Empty", 1.0f)
        timeline.loadAnimation(emptyAnimation)

        // Should not crash
        timeline.play()
        timeline.scrubTo(0.5f)

        val values = timeline.evaluateAtCurrentTime().first()
        assertTrue(values.isEmpty()) // No tracks, so no values
    }

    @Test
    fun `Timeline maintains state consistency`() = runTest {
        timeline.loadAnimation(animationTimeline)

        // Complex sequence of operations
        timeline.play()
        timeline.setPlaybackSpeed(2.0f)
        timeline.pause()
        timeline.scrubTo(1.0f)
        timeline.setLoopMode(true)
        timeline.play()
        timeline.stop()

        // Final state should be consistent
        assertFalse(timeline.isPlaying.first())
        assertEquals(TimelinePlaybackState.STOPPED, timeline.playbackState.first())
        assertEquals(0.0f, timeline.currentTime.first()) // Stop resets to start
        assertTrue(timeline.isLooping.first()) // Loop mode should persist
        assertEquals(2.0f, timeline.playbackSpeed.first()) // Speed should persist
    }
}

// Vector3 is imported from data package

/**
 * TimeRange data class for selection testing
 */
data class TimeRange(val start: Float, val end: Float)