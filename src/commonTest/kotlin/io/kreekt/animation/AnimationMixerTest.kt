package io.kreekt.animation

import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract test for AnimationMixer
 */
class AnimationMixerTest {

    @Test
    fun testAnimationMixerContract() {
        // Test AnimationMixer basic functionality using the concrete implementation
        // Use a concrete Object3D subclass since Object3D is abstract
        val object3d = io.kreekt.core.scene.Group()
        val mixer: AnimationMixer = DefaultAnimationMixer(object3d)

        assertNotNull(mixer.root)
        assertEquals(object3d, mixer.root)
        // Note: AnimationMixer interface doesn't expose time/timeScale properties
        // These are internal to the implementation
    }

    @Test
    fun testClipActionContract() {
        // Test clip action creation
        val clip = AnimationClip("test", 1f, emptyList())
        // Use a concrete Object3D subclass since Object3D is abstract
        val mixer: AnimationMixer = DefaultAnimationMixer(io.kreekt.core.scene.Group())
        val action: ClipAction = mixer.clipAction(clip)

        assertNotNull(action)
        assertEquals(clip, action.clip)
        assertEquals(mixer, action.mixer)
        // Note: AnimationAction interface doesn't have enabled property
        // Check that it's not running by default instead
        assertTrue(!action.isRunning)
    }

    @Test
    fun testMixerUpdateContract() {
        // Test mixer update functionality
        // Use a concrete Object3D subclass since Object3D is abstract
        val mixer: AnimationMixer = DefaultAnimationMixer(io.kreekt.core.scene.Group())
        val clip = AnimationClip("test", 1f, emptyList())
        val action: ClipAction = mixer.clipAction(clip)

        // Start the action
        action.play()

        val deltaTime = 0.016f // 60 FPS

        // Update the mixer
        mixer.update(deltaTime)

        // Verify action is running
        assertTrue(action.isRunning)

        // Verify time advanced
        assertTrue(action.time >= deltaTime)

        // Update again
        mixer.update(deltaTime)

        // Verify time advanced further
        assertTrue(action.time >= deltaTime * 2)
    }
}