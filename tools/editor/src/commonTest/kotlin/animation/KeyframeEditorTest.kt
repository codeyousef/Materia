package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for KeyframeEditor - Professional keyframe editing with precise timing control
 */
class KeyframeEditorTest {

    private lateinit var keyframeEditor: KeyframeEditor
    private lateinit var testTrack: AnimationTrack

    @BeforeTest
    fun setup() {
        // Create test track with position keyframes
        testTrack = AnimationTrack(
            targetPath = "scene.objects.cube1.transform.position.x",
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(0.0f, 0.0f),
                Keyframe(1.0f, 5.0f),
                Keyframe(2.0f, 10.0f),
                Keyframe(3.0f, 5.0f)
            ),
            interpolation = InterpolationType.LINEAR
        )

        keyframeEditor = KeyframeEditor()
    }

    @Test
    fun `KeyframeEditor initializes with empty state`() = runTest {
        assertEquals(null, keyframeEditor.currentTrack.first())
        assertEquals(emptySet<Int>(), keyframeEditor.selectedKeyframes.first())
        assertEquals(KeyframeEditMode.SELECT, keyframeEditor.editMode.first())
        assertFalse(keyframeEditor.isEditing.first())
    }

    @Test
    fun `KeyframeEditor loads track correctly`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        val loadedTrack = keyframeEditor.currentTrack.first()
        assertNotNull(loadedTrack)
        assertEquals(testTrack.id, loadedTrack.id)
        assertEquals(4, loadedTrack.keyframes.size)
    }

    @Test
    fun `KeyframeEditor selection functionality`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Select single keyframe
        keyframeEditor.selectKeyframe(1)
        assertEquals(setOf(1), keyframeEditor.selectedKeyframes.first())

        // Add to selection
        keyframeEditor.addToSelection(2)
        assertEquals(setOf(1, 2), keyframeEditor.selectedKeyframes.first())

        // Toggle selection
        keyframeEditor.toggleSelection(1)
        assertEquals(setOf(2), keyframeEditor.selectedKeyframes.first())

        // Select range
        keyframeEditor.selectRange(0, 3)
        assertEquals(setOf(0, 1, 2, 3), keyframeEditor.selectedKeyframes.first())

        // Clear selection
        keyframeEditor.clearSelection()
        assertEquals(emptySet<Int>(), keyframeEditor.selectedKeyframes.first())
    }

    @Test
    fun `KeyframeEditor creates keyframes`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Create keyframe at specific time
        keyframeEditor.createKeyframe(1.5f, 7.5f)

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(5, track.keyframes.size)

        val newKeyframe = track.keyframes.find { it.time == 1.5f }
        assertNotNull(newKeyframe)
        assertEquals(7.5f, newKeyframe.value)
    }

    @Test
    fun `KeyframeEditor deletes keyframes`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Select and delete keyframe
        keyframeEditor.selectKeyframe(1)
        keyframeEditor.deleteSelectedKeyframes()

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(3, track.keyframes.size)
        assertTrue(track.keyframes.none { it.time == 1.0f })
    }

    @Test
    fun `KeyframeEditor moves keyframes in time`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Move keyframe to new time
        keyframeEditor.selectKeyframe(1)
        keyframeEditor.moveSelectedKeyframes(0.5f) // Move 0.5 seconds later

        val track = keyframeEditor.currentTrack.first()!!
        val movedKeyframe = track.keyframes.find { it.time == 1.5f }
        assertNotNull(movedKeyframe)
        assertEquals(5.0f, movedKeyframe.value)

        // Original keyframe should be gone
        assertTrue(track.keyframes.none { it.time == 1.0f })
    }

    @Test
    fun `KeyframeEditor scales keyframe values`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Select multiple keyframes and scale values
        keyframeEditor.selectRange(1, 3)
        keyframeEditor.scaleSelectedValues(2.0f) // Double the values

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(10.0f, track.keyframes[1].value) // 5.0 * 2.0
        assertEquals(20.0f, track.keyframes[2].value) // 10.0 * 2.0
        assertEquals(10.0f, track.keyframes[3].value) // 5.0 * 2.0
    }

    @Test
    fun `KeyframeEditor changes interpolation type`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Change interpolation for selected keyframes
        keyframeEditor.selectKeyframe(1)
        keyframeEditor.setInterpolationForSelected(InterpolationType.CUBIC)

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(InterpolationType.CUBIC, track.interpolation)
    }

    @Test
    fun `KeyframeEditor changes easing type`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Change easing for selected keyframe
        keyframeEditor.selectKeyframe(1)
        keyframeEditor.setEasingForSelected(EasingType.EASE_IN_OUT)

        val track = keyframeEditor.currentTrack.first()!!
        val keyframe = track.keyframes[1]
        assertEquals(EasingType.EASE_IN_OUT, keyframe.easing)
    }

    @Test
    fun `KeyframeEditor copy and paste functionality`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Copy keyframes
        keyframeEditor.selectRange(1, 2)
        keyframeEditor.copySelectedKeyframes()

        // Paste at new time
        keyframeEditor.pasteKeyframes(4.0f)

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(6, track.keyframes.size) // Original 4 + 2 pasted

        val pastedKeyframe1 = track.keyframes.find { it.time == 4.0f }
        val pastedKeyframe2 = track.keyframes.find { it.time == 5.0f }
        assertNotNull(pastedKeyframe1)
        assertNotNull(pastedKeyframe2)
        assertEquals(5.0f, pastedKeyframe1.value)
        assertEquals(10.0f, pastedKeyframe2.value)
    }

    @Test
    fun `KeyframeEditor precision timing control`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Test sub-frame precision
        keyframeEditor.createKeyframe(1.033333f, 6.0f) // Between frames at 30fps

        val track = keyframeEditor.currentTrack.first()!!
        val precisionKeyframe = track.keyframes.find { it.time == 1.033333f }
        assertNotNull(precisionKeyframe)
    }

    @Test
    fun `KeyframeEditor snap to frames`() = runTest {
        keyframeEditor.loadTrack(testTrack)
        keyframeEditor.setSnapToFrames(true, 30) // 30fps

        // Create keyframe with non-frame time
        keyframeEditor.createKeyframe(1.2345f, 8.0f)

        val track = keyframeEditor.currentTrack.first()!!
        val snappedKeyframe = track.keyframes.find {
            kotlin.math.abs(it.time - 1.233333f) < 0.001f // Closest frame at 30fps
        }
        assertNotNull(snappedKeyframe)
    }

    @Test
    fun `KeyframeEditor maintains value constraints`() = runTest {
        // Create track with opacity property (0-1 range)
        val opacityTrack = AnimationTrack(
            targetPath = "scene.objects.cube1.material.opacity",
            property = AnimatableProperty.OPACITY,
            keyframes = listOf(
                Keyframe(0.0f, 1.0f),
                Keyframe(1.0f, 0.5f)
            ),
            interpolation = InterpolationType.LINEAR
        )

        keyframeEditor.loadTrack(opacityTrack)

        // Try to create keyframe with invalid value
        keyframeEditor.createKeyframe(2.0f, 1.5f) // Above max

        val track = keyframeEditor.currentTrack.first()!!
        val clampedKeyframe = track.keyframes.find { it.time == 2.0f }
        assertNotNull(clampedKeyframe)
        assertEquals(1.0f, clampedKeyframe.value) // Should be clamped to max
    }

    @Test
    fun `KeyframeEditor undo and redo operations`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        val originalKeyframeCount = testTrack.keyframes.size

        // Make a change
        keyframeEditor.createKeyframe(1.5f, 7.5f)
        assertEquals(originalKeyframeCount + 1, keyframeEditor.currentTrack.first()!!.keyframes.size)

        // Undo
        keyframeEditor.undo()
        assertEquals(originalKeyframeCount, keyframeEditor.currentTrack.first()!!.keyframes.size)

        // Redo
        keyframeEditor.redo()
        assertEquals(originalKeyframeCount + 1, keyframeEditor.currentTrack.first()!!.keyframes.size)
    }

    @Test
    fun `KeyframeEditor batch operations performance`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        val startTime = kotlinx.datetime.Clock.System.now()

        // Perform batch operation - create 100 keyframes
        keyframeEditor.beginBatchEdit()
        repeat(100) { i ->
            keyframeEditor.createKeyframe(i * 0.1f, i.toFloat())
        }
        keyframeEditor.endBatchEdit()

        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = endTime - startTime

        // Should complete quickly (less than 100ms)
        assertTrue(duration.inWholeMilliseconds < 100)

        val track = keyframeEditor.currentTrack.first()!!
        assertEquals(104, track.keyframes.size) // Original 4 + 100 new
    }

    @Test
    fun `KeyframeEditor handles edge cases`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Try to select non-existent keyframe
        keyframeEditor.selectKeyframe(999)
        assertEquals(emptySet<Int>(), keyframeEditor.selectedKeyframes.first())

        // Try to delete when nothing selected
        keyframeEditor.deleteSelectedKeyframes()
        assertEquals(4, keyframeEditor.currentTrack.first()!!.keyframes.size)

        // Try to move with no selection
        keyframeEditor.moveSelectedKeyframes(1.0f)
        // Should not crash or modify track

        // Try to paste with empty clipboard
        keyframeEditor.pasteKeyframes(5.0f)
        assertEquals(4, keyframeEditor.currentTrack.first()!!.keyframes.size)
    }

    @Test
    fun `KeyframeEditor maintains temporal ordering`() = runTest {
        keyframeEditor.loadTrack(testTrack)

        // Move keyframe to create temporal conflict
        keyframeEditor.selectKeyframe(2) // Keyframe at time 2.0
        keyframeEditor.moveSelectedKeyframes(-1.5f) // Move to time 0.5

        val track = keyframeEditor.currentTrack.first()!!
        val sortedTimes = track.keyframes.map { it.time }.sorted()
        val originalTimes = track.keyframes.map { it.time }

        // Times should remain sorted
        assertEquals(sortedTimes, originalTimes)
    }
}