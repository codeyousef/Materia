/**
 * Contract Test: Audio loading and playback controls
 * Covers: FR-A005, FR-A006, FR-A007 from contracts/audio-api.kt
 *
 * Test Cases:
 * - Load audio from URL
 * - Play/pause/stop controls
 * - Volume and playback rate adjustments
 * - Looping behavior
 *
 * Expected: All tests FAIL
 */
package io.materia.audio

import io.materia.camera.PerspectiveCamera
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AudioPlaybackContractTest {

    @Test
    fun testAudioCreation() {
        // Given: An audio listener
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)

        // When: Creating audio
        val audio = Audio(listener)

        // Then: Audio should exist
        assertNotNull(audio, "Audio should be created")
    }

    @Test
    fun testAudioLoadFromURL() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Loading audio from URL
        val result = audio.load("sounds/test.mp3")

        // Then: Should return audio instance for chaining
        assertEquals(audio, result, "load() should return self for chaining")
    }

    @Test
    fun testAudioDefaultProperties() {
        // Given: A new audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // Then: Should have sensible defaults
        assertEquals(1f, audio.volume, 0.001f, "Default volume should be 1.0")
        assertEquals(1f, audio.playbackRate, 0.001f, "Default playback rate should be 1.0")
        assertFalse(audio.loop, "Default loop should be false")
        assertFalse(audio.autoplay, "Default autoplay should be false")
        assertFalse(audio.isPlaying, "Audio should not be playing initially")
    }

    @Test
    fun testAudioPlayControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Playing audio
        val result = audio.play()

        // Then: Should return audio instance for chaining
        assertEquals(audio, result, "play() should return self for chaining")
    }

    @Test
    fun testAudioPlayWithDelay() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Playing audio with delay
        val result = audio.play(delay = 0.5f)

        // Then: Should return audio instance
        assertEquals(audio, result, "play(delay) should return self for chaining")
    }

    @Test
    fun testAudioPauseControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Pausing audio
        val result = audio.pause()

        // Then: Should return audio instance for chaining
        assertEquals(audio, result, "pause() should return self for chaining")
    }

    @Test
    fun testAudioStopControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Stopping audio
        val result = audio.stop()

        // Then: Should return audio instance for chaining
        assertEquals(audio, result, "stop() should return self for chaining")
    }

    @Test
    fun testVolumeControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting volume
        audio.setVolume(0.5f)

        // Then: Volume should be set
        assertEquals(0.5f, audio.volume, 0.001f, "Volume should be 0.5")
    }

    @Test
    fun testVolumeRange() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting various volumes
        audio.volume = 0f
        assertEquals(0f, audio.volume, 0.001f)

        audio.volume = 0.5f
        assertEquals(0.5f, audio.volume, 0.001f)

        audio.volume = 1f
        assertEquals(1f, audio.volume, 0.001f)

        // Then: Volume should be in range [0, 1]
        assertTrue(
            audio.volume >= 0f && audio.volume <= 1f,
            "Volume should be in range [0, 1]"
        )
    }

    @Test
    fun testPlaybackRateControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting playback rate
        audio.setPlaybackRate(2f)

        // Then: Playback rate should be set
        assertEquals(2f, audio.playbackRate, 0.001f, "Playback rate should be 2.0")
    }

    @Test
    fun testPlaybackRateVariations() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting different playback rates
        audio.playbackRate = 0.5f  // Half speed
        assertEquals(0.5f, audio.playbackRate, 0.001f)

        audio.playbackRate = 1f    // Normal speed
        assertEquals(1f, audio.playbackRate, 0.001f)

        audio.playbackRate = 2f    // Double speed
        assertEquals(2f, audio.playbackRate, 0.001f)

        // Then: Should accept positive values
        assertTrue(audio.playbackRate > 0f, "Playback rate should be positive")
    }

    @Test
    fun testLoopControl() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Enabling loop
        audio.setLoop(true)

        // Then: Loop should be enabled
        assertTrue(audio.loop, "Loop should be enabled")

        // When: Disabling loop
        audio.setLoop(false)

        // Then: Loop should be disabled
        assertFalse(audio.loop, "Loop should be disabled")
    }

    @Test
    fun testAutoplayProperty() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting autoplay
        audio.autoplay = true

        // Then: Autoplay should be set
        assertTrue(audio.autoplay, "Autoplay should be enabled")
    }

    @Test
    fun testMethodChaining() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Chaining multiple operations
        val result = audio
            .setVolume(0.8f)
            .setPlaybackRate(1.5f)
            .setLoop(true)
            .play()

        // Then: Should support method chaining
        assertEquals(audio, result, "Methods should support chaining")
        assertEquals(0.8f, audio.volume, 0.001f)
        assertEquals(1.5f, audio.playbackRate, 0.001f)
        assertTrue(audio.loop)
    }

    @Test
    fun testAudioDuration() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // Then: Should have duration property (may be 0 before loading)
        assertTrue(audio.duration >= 0f, "Duration should be non-negative")
    }

    @Test
    fun testOnEndedCallback() {
        // Given: An audio instance
        val camera = PerspectiveCamera(75f, 16f / 9f, 0.1f, 1000f)
        val listener = AudioListener(camera)
        val audio = Audio(listener)

        // When: Setting ended callback
        var callbackInvoked = false
        audio.onEnded { callbackInvoked = true }

        // Then: Should not throw exception
        assertTrue(true, "onEnded callback should be set without error")
    }
}