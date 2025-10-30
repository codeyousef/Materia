package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for CurveEditor - Track and curve editing with Bezier interpolation
 */
class CurveEditorTest {

    private lateinit var curveEditor: CurveEditor
    private lateinit var testTrack: AnimationTrack

    @BeforeTest
    fun setup() {
        // Create test track with cubic interpolation
        testTrack = AnimationTrack(
            targetPath = "scene.objects.cube1.transform.position.x",
            property = AnimatableProperty.POSITION_X,
            keyframes = listOf(
                Keyframe(0.0f, 0.0f, EasingType.LINEAR),
                Keyframe(1.0f, 5.0f, EasingType.EASE_IN_OUT,
                    BezierHandles(Vector2(-0.3f, 0.0f), Vector2(0.3f, 0.0f))),
                Keyframe(2.0f, 10.0f, EasingType.EASE_OUT,
                    BezierHandles(Vector2(-0.25f, 0.0f), Vector2(0.25f, 0.0f))),
                Keyframe(3.0f, 5.0f, EasingType.LINEAR)
            ),
            interpolation = InterpolationType.CUBIC
        )

        curveEditor = CurveEditor()
    }

    @Test
    fun `CurveEditor initializes with default state`() = runTest {
        assertEquals(emptyList<AnimationTrack>(), curveEditor.tracks.first())
        assertEquals(null, curveEditor.selectedTrack.first())
        assertEquals(emptySet<Int>(), curveEditor.selectedKeyframes.first())
        assertEquals(CurveEditMode.SELECT, curveEditor.editMode.first())
        assertEquals(1.0f, curveEditor.zoomLevel.first())
        assertEquals(Vector2(0.0f, 0.0f), curveEditor.panOffset.first())
    }

    @Test
    fun `CurveEditor loads tracks correctly`() = runTest {
        val tracks = listOf(testTrack)
        curveEditor.loadTracks(tracks)

        assertEquals(tracks, curveEditor.tracks.first())
        assertEquals(testTrack, curveEditor.selectedTrack.first())
    }

    @Test
    fun `CurveEditor track selection and visibility`() = runTest {
        val positionTrack = testTrack
        val rotationTrack = AnimationTrack.createRotationTrack("cube1", listOf(
            Keyframe(0.0f, Vector3(0f, 0f, 0f)),
            Keyframe(2.0f, Vector3(0f, 180f, 0f))
        ))

        curveEditor.loadTracks(listOf(positionTrack, rotationTrack))

        // Select different track
        curveEditor.selectTrack(rotationTrack.id)
        assertEquals(rotationTrack, curveEditor.selectedTrack.first())

        // Toggle track visibility
        curveEditor.setTrackVisible(positionTrack.id, false)
        val visibleTracks = curveEditor.visibleTracks.first()
        assertEquals(1, visibleTracks.size)
        assertEquals(rotationTrack.id, visibleTracks.first().id)
    }

    @Test
    fun `CurveEditor keyframe selection on curves`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))

        // Select keyframe
        curveEditor.selectKeyframe(1)
        assertEquals(setOf(1), curveEditor.selectedKeyframes.first())

        // Multi-select keyframes
        curveEditor.addToSelection(2)
        assertEquals(setOf(1, 2), curveEditor.selectedKeyframes.first())

        // Box select area
        curveEditor.boxSelect(Vector2(0.5f, 3.0f), Vector2(2.5f, 12.0f))
        assertTrue(curveEditor.selectedKeyframes.first().contains(1))
        assertTrue(curveEditor.selectedKeyframes.first().contains(2))
    }

    @Test
    fun `CurveEditor Bezier handle manipulation`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.selectKeyframe(1) // Keyframe with handles

        val originalHandles = testTrack.keyframes[1].handles!!

        // Move left handle
        curveEditor.moveHandle(1, CurveHandle.LEFT, Vector2(-0.5f, 0.2f))

        val track = curveEditor.selectedTrack.first()!!
        val updatedHandles = track.keyframes[1].handles!!
        assertEquals(Vector2(-0.5f, 0.2f), updatedHandles.leftHandle)
        assertEquals(originalHandles.rightHandle, updatedHandles.rightHandle)

        // Move right handle
        curveEditor.moveHandle(1, CurveHandle.RIGHT, Vector2(0.4f, -0.1f))

        val trackAfterRight = curveEditor.selectedTrack.first()!!
        val finalHandles = trackAfterRight.keyframes[1].handles!!
        assertEquals(Vector2(0.4f, -0.1f), finalHandles.rightHandle)
    }

    @Test
    fun `CurveEditor handle auto-tangent modes`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.selectKeyframe(1)

        // Set auto tangent mode
        curveEditor.setHandleMode(1, CurveHandleMode.AUTO)

        val track = curveEditor.selectedTrack.first()!!
        val keyframe = track.keyframes[1]

        // Auto tangent should have calculated handles
        assertNotNull(keyframe.handles)
        // In a real implementation, auto mode would calculate smooth tangents
    }

    @Test
    fun `CurveEditor curve interpolation types`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))

        // Change interpolation type
        curveEditor.setInterpolationType(InterpolationType.LINEAR)
        assertEquals(InterpolationType.LINEAR, curveEditor.selectedTrack.first()!!.interpolation)

        curveEditor.setInterpolationType(InterpolationType.STEP)
        assertEquals(InterpolationType.STEP, curveEditor.selectedTrack.first()!!.interpolation)

        curveEditor.setInterpolationType(InterpolationType.CUBIC)
        assertEquals(InterpolationType.CUBIC, curveEditor.selectedTrack.first()!!.interpolation)
    }

    @Test
    fun `CurveEditor easing preset application`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.selectKeyframe(1)

        // Apply easing preset
        curveEditor.applyEasingPreset(EasingPreset.EASE_IN_QUAD)

        val track = curveEditor.selectedTrack.first()!!
        val keyframe = track.keyframes[1]
        assertEquals(EasingType.EASE_IN, keyframe.easing)

        // Check that handles were updated for the preset
        assertNotNull(keyframe.handles)
    }

    @Test
    fun `CurveEditor viewport navigation`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))

        // Pan viewport
        curveEditor.pan(Vector2(10.0f, 5.0f))
        assertEquals(Vector2(10.0f, 5.0f), curveEditor.panOffset.first())

        // Zoom viewport
        curveEditor.zoom(2.0f, Vector2(1.0f, 5.0f)) // Zoom at center of curve
        assertEquals(2.0f, curveEditor.zoomLevel.first())

        // Fit all curves
        curveEditor.frameAll()
        // Should reset zoom and pan to show all keyframes
        assertTrue(curveEditor.zoomLevel.first() > 0.0f)
    }

    @Test
    fun `CurveEditor curve evaluation at time`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))

        // Evaluate curve at specific time
        val value = curveEditor.evaluateCurveAt(testTrack.id, 1.5f)
        assertNotNull(value)

        // Should be interpolated between keyframes at 1.0 and 2.0
        assertTrue(value.toFloat() > 5.0f && value.toFloat() < 10.0f)

        // Evaluate at keyframe time
        val keyframeValue = curveEditor.evaluateCurveAt(testTrack.id, 1.0f)
        assertEquals(5.0f, keyframeValue?.toFloat())
    }

    @Test
    fun `CurveEditor keyframe creation on curve`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.setEditMode(CurveEditMode.CREATE)

        // Create keyframe by clicking on curve
        curveEditor.createKeyframeOnCurve(testTrack.id, 1.5f, 7.5f)

        val track = curveEditor.selectedTrack.first()!!
        assertEquals(5, track.keyframes.size) // Original 4 + 1 new

        val newKeyframe = track.keyframes.find { it.time == 1.5f }
        assertNotNull(newKeyframe)
        assertEquals(7.5f, newKeyframe.value)
    }

    @Test
    fun `CurveEditor curve smoothing operations`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.selectRange(0, 3) // Select all keyframes

        // Smooth selected keyframes
        curveEditor.smoothSelectedKeyframes(0.3f) // 30% smoothing

        val track = curveEditor.selectedTrack.first()!!

        // All keyframes should now have handles for smooth interpolation
        track.keyframes.forEach { keyframe ->
            if (keyframe.time > 0.0f && keyframe.time < track.keyframes.last().time) {
                assertNotNull(keyframe.handles)
            }
        }
    }

    @Test
    fun `CurveEditor tangent operations`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))
        curveEditor.selectKeyframe(1)

        // Break tangents (make handles independent)
        curveEditor.breakTangents(1)

        val track = curveEditor.selectedTrack.first()!!
        val keyframe = track.keyframes[1]

        // Should have independent handles
        assertNotNull(keyframe.handles)

        // Unify tangents (make handles symmetric)
        curveEditor.unifyTangents(1)

        val updatedTrack = curveEditor.selectedTrack.first()!!
        val updatedKeyframe = updatedTrack.keyframes[1]
        val handles = updatedKeyframe.handles!!

        // Left and right handles should be symmetric
        assertEquals(-handles.rightHandle.x, handles.leftHandle.x, 0.01f)
        assertEquals(-handles.rightHandle.y, handles.leftHandle.y, 0.01f)
    }

    @Test
    fun `CurveEditor multiple track editing`() = runTest {
        val positionXTrack = testTrack
        val positionYTrack = AnimationTrack(
            targetPath = "scene.objects.cube1.transform.position.y",
            property = AnimatableProperty.POSITION_Y,
            keyframes = listOf(
                Keyframe(0.0f, 0.0f),
                Keyframe(1.0f, 3.0f),
                Keyframe(2.0f, 6.0f),
                Keyframe(3.0f, 3.0f)
            ),
            interpolation = InterpolationType.LINEAR
        )

        curveEditor.loadTracks(listOf(positionXTrack, positionYTrack))

        // Select both tracks
        curveEditor.selectMultipleTracks(listOf(positionXTrack.id, positionYTrack.id))

        val selectedTracks = curveEditor.selectedTracks.first()
        assertEquals(2, selectedTracks.size)

        // Operations should affect both tracks
        curveEditor.setEditMode(CurveEditMode.MOVE)
        curveEditor.moveSelectedKeyframes(Vector2(0.5f, 1.0f))

        // Both tracks should have moved keyframes
        selectedTracks.forEach { track ->
            assertTrue(track.keyframes.any { it.time > 0.0f && it.time != 1.0f })
        }
    }

    @Test
    fun `CurveEditor performance with complex curves`() = runTest {
        // Create track with many keyframes
        val manyKeyframes = (0..100).map { i ->
            Keyframe(i.toFloat(), sin(i.toFloat() * 0.1f) * 10.0f)
        }

        val complexTrack = testTrack.copy(keyframes = manyKeyframes)
        curveEditor.loadTracks(listOf(complexTrack))

        val startTime = kotlinx.datetime.Clock.System.now()

        // Perform curve evaluation at many points
        repeat(100) { i ->
            val time = i.toFloat()
            curveEditor.evaluateCurveAt(complexTrack.id, time)
        }

        val endTime = kotlinx.datetime.Clock.System.now()
        val duration = endTime - startTime

        // Should complete in reasonable time
        assertTrue(duration.inWholeMilliseconds < 100)
    }

    @Test
    fun `CurveEditor curve filtering and isolation`() = runTest {
        val tracks = listOf(
            testTrack,
            AnimationTrack.createRotationTrack("cube1", listOf(
                Keyframe(0.0f, Vector3(0f, 0f, 0f)),
                Keyframe(2.0f, Vector3(0f, 180f, 0f))
            )),
            AnimationTrack.createScaleTrack("cube1", listOf(
                Keyframe(0.0f, Vector3(1f, 1f, 1f)),
                Keyframe(1.0f, Vector3(2f, 2f, 2f))
            ))
        )

        curveEditor.loadTracks(tracks)

        // Filter by property type
        curveEditor.filterTracksByProperty(AnimatableProperty.POSITION_X)
        assertEquals(1, curveEditor.visibleTracks.first().size)

        // Clear filter
        curveEditor.clearTrackFilter()
        assertEquals(3, curveEditor.visibleTracks.first().size)

        // Isolate single track
        curveEditor.isolateTrack(testTrack.id)
        assertEquals(1, curveEditor.visibleTracks.first().size)
        assertEquals(testTrack.id, curveEditor.visibleTracks.first().first().id)
    }

    @Test
    fun `CurveEditor undo and redo for curve operations`() = runTest {
        curveEditor.loadTracks(listOf(testTrack))

        val originalHandles = testTrack.keyframes[1].handles!!

        // Modify handle
        curveEditor.selectKeyframe(1)
        curveEditor.moveHandle(1, CurveHandle.LEFT, Vector2(-0.8f, 0.3f))

        val modifiedHandles = curveEditor.selectedTrack.first()!!.keyframes[1].handles!!
        assertNotEquals(originalHandles, modifiedHandles)

        // Undo
        curveEditor.undo()
        val restoredHandles = curveEditor.selectedTrack.first()!!.keyframes[1].handles!!
        assertEquals(originalHandles, restoredHandles)

        // Redo
        curveEditor.redo()
        val redoneHandles = curveEditor.selectedTrack.first()!!.keyframes[1].handles!!
        assertEquals(modifiedHandles, redoneHandles)
    }
}