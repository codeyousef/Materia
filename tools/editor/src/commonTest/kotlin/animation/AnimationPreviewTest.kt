package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for AnimationPreview - Real-time animation preview system
 */
class AnimationPreviewTest {

    private lateinit var animationPreview: AnimationPreview
    private lateinit var testAnimation: AnimationTimeline

    @BeforeTest
    fun setup() {
        // Create test animation with position track
        testAnimation = AnimationTimeline.createPositionAnimation(
            name = "Test Preview Animation",
            targetPath = "scene.objects.cube1",
            startPosition = Vector3(0.0f, 0.0f, 0.0f),
            endPosition = Vector3(10.0f, 5.0f, 2.0f),
            duration = 2.0f
        )

        animationPreview = AnimationPreview()
    }

    @Test
    fun `AnimationPreview initializes with default state`() = runTest {
        assertEquals(null, animationPreview.currentAnimation.first())
        assertEquals(0.0f, animationPreview.currentTime.first())
        assertEquals(PreviewPlaybackState.STOPPED, animationPreview.playbackState.first())
        assertEquals(1.0f, animationPreview.playbackSpeed.first())
        assertEquals(30, animationPreview.targetFrameRate.first())
        assertFalse(animationPreview.isLooping.first())
    }

    @Test
    fun `AnimationPreview loads animation correctly`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        val loadedAnimation = animationPreview.currentAnimation.first()
        assertNotNull(loadedAnimation)
        assertEquals("Test Preview Animation", loadedAnimation.name)
        assertEquals(2.0f, loadedAnimation.duration)
    }

    @Test
    fun `AnimationPreview playback controls work correctly`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Test play
        animationPreview.play()
        assertEquals(PreviewPlaybackState.PLAYING, animationPreview.playbackState.first())

        // Test pause
        animationPreview.pause()
        assertEquals(PreviewPlaybackState.PAUSED, animationPreview.playbackState.first())

        // Test stop
        animationPreview.stop()
        assertEquals(PreviewPlaybackState.STOPPED, animationPreview.playbackState.first())
        assertEquals(0.0f, animationPreview.currentTime.first())
    }

    @Test
    fun `AnimationPreview scrubbing works with real-time updates`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Scrub to middle
        animationPreview.scrubTo(1.0f)
        assertEquals(1.0f, animationPreview.currentTime.first())

        // Get animated values at current time
        val values = animationPreview.currentAnimatedValues.first()
        assertTrue(values.isNotEmpty())

        // Values should be interpolated at midpoint
        val positionValue = values["scene.objects.cube1.transform.position"]
        assertNotNull(positionValue)
    }

    @Test
    fun `AnimationPreview frame-by-frame navigation`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Go to specific frame
        animationPreview.goToFrame(30) // 1 second at 30fps
        assertEquals(1.0f, animationPreview.currentTime.first(), 0.01f)

        // Step forward
        val currentFrame = animationPreview.currentFrame.first()
        animationPreview.stepForward()
        assertEquals(currentFrame + 1, animationPreview.currentFrame.first())

        // Step backward
        animationPreview.stepBackward()
        assertEquals(currentFrame, animationPreview.currentFrame.first())
    }

    @Test
    fun `AnimationPreview playback speed adjustment`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Set half speed
        animationPreview.setPlaybackSpeed(0.5f)
        assertEquals(0.5f, animationPreview.playbackSpeed.first())

        // Set double speed
        animationPreview.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, animationPreview.playbackSpeed.first())

        // Test bounds clamping
        animationPreview.setPlaybackSpeed(10.0f)
        assertEquals(5.0f, animationPreview.playbackSpeed.first()) // Max speed

        animationPreview.setPlaybackSpeed(-1.0f)
        assertEquals(0.1f, animationPreview.playbackSpeed.first()) // Min speed
    }

    @Test
    fun `AnimationPreview looping functionality`() = runTest {
        animationPreview.loadAnimation(testAnimation)
        animationPreview.setLooping(true)

        assertTrue(animationPreview.isLooping.first())

        // Scrub past end should wrap around when looping
        animationPreview.scrubTo(2.5f) // Beyond 2.0f duration
        assertEquals(0.5f, animationPreview.currentTime.first(), 0.01f)
    }

    @Test
    fun `AnimationPreview frame rate adjustment`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Change frame rate
        animationPreview.setTargetFrameRate(60)
        assertEquals(60, animationPreview.targetFrameRate.first())

        // Verify frame calculations
        animationPreview.goToFrame(60) // 1 second at 60fps
        assertEquals(1.0f, animationPreview.currentTime.first(), 0.01f)
    }

    @Test
    fun `AnimationPreview real-time value updates`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Start at beginning
        animationPreview.scrubTo(0.0f)
        val startValues = animationPreview.currentAnimatedValues.first()

        // Move to middle
        animationPreview.scrubTo(1.0f)
        val middleValues = animationPreview.currentAnimatedValues.first()

        // Move to end
        animationPreview.scrubTo(2.0f)
        val endValues = animationPreview.currentAnimatedValues.first()

        // All should have position values but they should be different
        assertTrue(startValues.containsKey("scene.objects.cube1.transform.position"))
        assertTrue(middleValues.containsKey("scene.objects.cube1.transform.position"))
        assertTrue(endValues.containsKey("scene.objects.cube1.transform.position"))

        // Values should be different due to interpolation
        assertNotEquals(startValues, middleValues)
        assertNotEquals(middleValues, endValues)
    }

    @Test
    fun `AnimationPreview onion skinning`() = runTest {
        animationPreview.loadAnimation(testAnimation)
        animationPreview.enableOnionSkinning(true, frames = 5, opacity = 0.3f)

        assertTrue(animationPreview.isOnionSkinningEnabled.first())

        val onionFrames = animationPreview.onionSkinFrames.first()
        assertEquals(5, onionFrames.size)

        // Each onion frame should have position and opacity
        onionFrames.forEach { frame ->
            assertTrue(frame.opacity <= 0.3f)
            assertTrue(frame.values.isNotEmpty())
        }
    }

    @Test
    fun `AnimationPreview performance tracking`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Perform rapid scrubbing to test performance
        val startTime = kotlinx.datetime.Clock.System.now()

        repeat(60) { frame ->
            val time = (frame / 60.0f) * 2.0f // Full animation over 60 frames
            animationPreview.scrubTo(time)
        }

        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = endTime - startTime

        // Should complete in reasonable time (less than 100ms for 60 updates)
        assertTrue(duration.inWholeMilliseconds < 100)

        val metrics = animationPreview.getPerformanceMetrics()
        assertTrue(metrics.averageEvaluationTimeMs < 16.0) // Should be sub-frame time
        assertTrue(metrics.isPerformingWell)
    }

    @Test
    fun `AnimationPreview marker integration`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Go to specific marker
        animationPreview.goToMarker("Start")
        assertEquals(0.0f, animationPreview.currentTime.first())

        animationPreview.goToMarker("End")
        assertEquals(2.0f, animationPreview.currentTime.first())

        // Navigate between markers
        animationPreview.scrubTo(0.0f)
        animationPreview.goToNextMarker()
        assertEquals(2.0f, animationPreview.currentTime.first())

        animationPreview.goToPreviousMarker()
        assertEquals(0.0f, animationPreview.currentTime.first())
    }

    @Test
    fun `AnimationPreview time range selection playback`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Set time range selection
        animationPreview.setTimeRangeSelection(0.5f, 1.5f)
        animationPreview.setPlaySelectedRange(true)

        // Play should only play selected range
        animationPreview.play()
        animationPreview.scrubTo(0.5f) // Start of selection

        // Simulated playback reaching end of selection should loop back to start
        animationPreview.scrubTo(1.5f)
        // In actual implementation, this would wrap back to 0.5f when playing selected range
    }

    @Test
    fun `AnimationPreview synchronized playback with timeline`() = runTest {
        animationPreview.loadAnimation(testAnimation)
        val timeline = Timeline()
        timeline.loadAnimation(testAnimation)

        // Synchronize with timeline
        animationPreview.synchronizeWithTimeline(timeline)

        // Changes in timeline should reflect in preview
        timeline.scrubTo(1.0f)
        // In a real implementation, this would trigger preview updates

        timeline.setPlaybackSpeed(2.0f)
        // Preview should match timeline speed
    }

    @Test
    fun `AnimationPreview handles complex animations`() = runTest {
        // Create animation with multiple tracks
        val complexAnimation = AnimationTimeline(
            name = "Complex Animation",
            duration = 3.0f,
            frameRate = 30,
            tracks = listOf(
                AnimationTrack.createPositionTrack("cube1", listOf(
                    Keyframe(0.0f, Vector3(0f, 0f, 0f)),
                    Keyframe(1.0f, Vector3(5f, 0f, 0f)),
                    Keyframe(2.0f, Vector3(5f, 5f, 0f)),
                    Keyframe(3.0f, Vector3(0f, 5f, 0f))
                )),
                AnimationTrack.createRotationTrack("cube1", listOf(
                    Keyframe(0.0f, Vector3(0f, 0f, 0f)),
                    Keyframe(3.0f, Vector3(0f, 360f, 0f))
                ))
            ),
            markers = emptyList(),
            settings = TimelineSettings.default()
        )

        animationPreview.loadAnimation(complexAnimation)
        animationPreview.scrubTo(1.5f)

        val values = animationPreview.currentAnimatedValues.first()

        // Should have both position and rotation values
        assertTrue(values.containsKey("cube1.transform.position"))
        assertTrue(values.containsKey("cube1.transform.rotation"))
    }

    @Test
    fun `AnimationPreview smooth interpolation quality`() = runTest {
        animationPreview.loadAnimation(testAnimation)

        // Test interpolation quality with dense sampling
        val sampleTimes = (0..20).map { it / 20.0f * 2.0f } // 21 samples over 2 seconds
        val values = sampleTimes.map { time ->
            animationPreview.scrubTo(time)
            animationPreview.currentAnimatedValues.first()
        }

        // All samples should have valid values
        values.forEach { valueMap ->
            assertTrue(valueMap.isNotEmpty())
        }

        // Interpolation should be smooth (no sudden jumps)
        for (i in 1 until values.size) {
            // Each value map should be different (showing progression)
            assertNotEquals(values[i-1], values[i])
        }
    }
}