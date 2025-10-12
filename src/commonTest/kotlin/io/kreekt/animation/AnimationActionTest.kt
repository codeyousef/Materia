package io.kreekt.animation

import io.kreekt.core.scene.Object3D
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contract test for AnimationAction
 * T015 - Tests for AnimationAction implementation
 */
class AnimationActionTest {

    // Simple test object for animation
    private class TestObject : Object3D() {
        override fun clone(recursive: Boolean): Object3D {
            return TestObject().also { it.copy(this, recursive) }
        }
    }

    // Create a test mixer implementation
    private fun createTestMixer(root: Object3D): AnimationMixer {
        return object : AnimationMixer {
            override val root: Object3D = root
            override val isDisposed: Boolean = false

            override fun clipAction(clip: AnimationClip): ClipAction {
                return DefaultClipAction(clip, this)
            }

            override fun update(deltaTime: Float) {}
            override fun stopAllAction() {}
            override fun dispose() {}
        }
    }

    @Test
    fun testAnimationActionPlayContract() {
        val root = TestObject()
        val mixer = createTestMixer(root)

        val clip = AnimationClip("test", 1f, emptyList())
        val action = mixer.clipAction(clip)

        assertFalse(action.isRunning, "Action should not be running initially")

        action.play()

        assertTrue(action.isRunning, "Action should be running after play()")
        assertFalse(action.isPaused, "Action should not be paused after play()")
    }

    @Test
    fun testAnimationActionStopContract() {
        val root = TestObject()
        val mixer = createTestMixer(root)

        val clip = AnimationClip("test", 1f, emptyList())
        val action = mixer.clipAction(clip)

        action.play()
        assertTrue(action.isRunning, "Action should be running after play()")

        action.stop()
        assertFalse(action.isRunning, "Action should not be running after stop()")
        assertEquals(0f, action.time, 0.01f, "Time should reset to 0 after stop()")
    }

    @Test
    fun testAnimationActionFadeContract() {
        val root = TestObject()
        val mixer = createTestMixer(root)

        val clip = AnimationClip("test", 1f, emptyList())
        val action = mixer.clipAction(clip) as DefaultClipAction

        // Test fade in
        action.fadeIn(0.5f)
        assertTrue(action.isRunning, "Action should be running after fadeIn()")
        assertEquals(0f, action.weight, 0.01f, "Weight should start at 0 for fade in")

        // Update to complete fade
        action.update(0.5f)
        assertEquals(1f, action.weight, 0.01f, "Weight should be 1 after fade in completes")

        // Test fade out - Set weight first, then call fadeOut
        action.play()  // Make sure it's running
        action.weight = 1f  // Start from weight 1
        action.fadeOut(0.5f)

        action.update(0.25f)
        val midWeight = action.weight
        assertTrue(midWeight > 0f && midWeight < 1f, "Weight should be between 0 and 1 during fade (was $midWeight)")

        action.update(0.25f)
        assertEquals(0f, action.weight, 0.01f, "Weight should be 0 after fade out completes")
        assertFalse(action.isRunning, "Action should stop after fade out completes")
    }

    @Test
    fun testAnimationActionAppliesPosition() {
        val root = TestObject()
        root.position.set(0f, 0f, 0f)
        val mixer = createTestMixer(root)

        // Create animation clip with position track
        val times = floatArrayOf(0f, 1f)
        val values = floatArrayOf(0f, 0f, 0f, 10f, 5f, 2f) // From (0,0,0) to (10,5,2)
        val positionTrack = KeyframeTrack("position", times, values, InterpolationType.LINEAR)
        val clip = AnimationClip("TestPosition", 1f, listOf(positionTrack))

        val action = mixer.clipAction(clip)
        action.play()

        // Test interpolation at halfway
        action.time = 0.5f
        action.update(0f)
        assertEquals(5f, root.position.x, 0.01f, "Position X at t=0.5 should be interpolated")
        assertEquals(2.5f, root.position.y, 0.01f, "Position Y at t=0.5 should be interpolated")
        assertEquals(1f, root.position.z, 0.01f, "Position Z at t=0.5 should be interpolated")
    }

    @Test
    fun testAnimationActionWeightBlending() {
        val root = TestObject()
        root.position.set(0f, 0f, 0f)
        val mixer = createTestMixer(root)

        val times = floatArrayOf(0f, 1f)
        val values = floatArrayOf(0f, 0f, 0f, 10f, 10f, 10f)
        val track = KeyframeTrack("position", times, values, InterpolationType.LINEAR)
        val clip = AnimationClip("TestWeight", 1f, listOf(track))

        val action = mixer.clipAction(clip)
        action.play()

        // Test with weight 0.5
        action.weight = 0.5f
        action.time = 1f
        action.update(0f)

        // With weight 0.5, the position should blend between original (0,0,0) and target (10,10,10)
        assertEquals(5f, root.position.x, 0.01f, "Position X with weight 0.5 should be blended")
        assertEquals(5f, root.position.y, 0.01f, "Position Y with weight 0.5 should be blended")
        assertEquals(5f, root.position.z, 0.01f, "Position Z with weight 0.5 should be blended")
    }
}