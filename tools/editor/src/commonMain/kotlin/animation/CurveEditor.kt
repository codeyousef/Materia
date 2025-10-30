package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * CurveEditor - Track and curve editing with Bezier interpolation
 *
 * Provides comprehensive curve editing capabilities including:
 * - Multi-track curve visualization and editing
 * - Bezier handle manipulation with precise control
 * - Easing presets and interpolation type switching
 * - Auto-tangent calculation and smoothing operations
 * - Viewport navigation (pan, zoom, frame) for detailed editing
 * - Keyframe creation directly on curves
 * - Tangent break/unify operations
 * - Track filtering, visibility, and isolation
 * - Performance optimization for complex curves
 * - Undo/redo for all curve operations
 */
class CurveEditor {

    // Core state flows
    private val _tracks = MutableStateFlow<List<AnimationTrack>>(emptyList())
    private val _selectedTrack = MutableStateFlow<AnimationTrack?>(null)
    private val _selectedTracks = MutableStateFlow<List<AnimationTrack>>(emptyList())
    private val _selectedKeyframes = MutableStateFlow<Set<Int>>(emptySet())
    private val _editMode = MutableStateFlow(CurveEditMode.SELECT)
    private val _zoomLevel = MutableStateFlow(1.0f)
    private val _panOffset = MutableStateFlow(Vector2(0.0f, 0.0f))
    private val _trackVisibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _activePropertyFilter = MutableStateFlow<AnimatableProperty?>(null)

    // Public state flows (read-only)
    val tracks: StateFlow<List<AnimationTrack>> = _tracks.asStateFlow()
    val selectedTrack: StateFlow<AnimationTrack?> = _selectedTrack.asStateFlow()
    val selectedTracks: StateFlow<List<AnimationTrack>> = _selectedTracks.asStateFlow()
    val selectedKeyframes: StateFlow<Set<Int>> = _selectedKeyframes.asStateFlow()
    val editMode: StateFlow<CurveEditMode> = _editMode.asStateFlow()
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()
    val panOffset: StateFlow<Vector2> = _panOffset.asStateFlow()

    // Derived states
    val visibleTracks: StateFlow<List<AnimationTrack>> = combine(
        tracks, _trackVisibility, _activePropertyFilter
    ) { allTracks, visibility, propertyFilter ->
        allTracks.filter { track ->
            val isVisible = visibility[track.id] ?: true
            val matchesFilter = propertyFilter?.let { track.property == it } ?: true
            isVisible && matchesFilter
        }
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(),
        initialValue = emptyList()
    )

    // Undo/redo system
    private val undoStack = mutableListOf<CurveEditorState>()
    private val redoStack = mutableListOf<CurveEditorState>()

    // Curve evaluation cache for performance
    private val evaluationCache = mutableMapOf<String, Map<Float, Any>>()

    /**
     * Loads multiple tracks for curve editing
     */
    fun loadTracks(tracks: List<AnimationTrack>) {
        saveState()

        _tracks.value = tracks
        _selectedTrack.value = tracks.firstOrNull()
        _selectedTracks.value = tracks.take(1)
        _selectedKeyframes.value = emptySet()

        // Initialize visibility for all tracks
        val visibility = tracks.associate { it.id to true }
        _trackVisibility.value = visibility

        clearCache()
        resetViewport()
    }

    /**
     * Selects a specific track by ID
     */
    fun selectTrack(trackId: String) {
        val track = _tracks.value.find { it.id == trackId }
        if (track != null) {
            _selectedTrack.value = track
            _selectedTracks.value = listOf(track)
            _selectedKeyframes.value = emptySet()
        }
    }

    /**
     * Selects multiple tracks for multi-track editing
     */
    fun selectMultipleTracks(trackIds: List<String>) {
        val tracksToSelect = _tracks.value.filter { it.id in trackIds }
        _selectedTracks.value = tracksToSelect
        _selectedTrack.value = tracksToSelect.firstOrNull()
        _selectedKeyframes.value = emptySet()
    }

    /**
     * Sets visibility for a specific track
     */
    fun setTrackVisible(trackId: String, visible: Boolean) {
        _trackVisibility.value = _trackVisibility.value + (trackId to visible)
    }

    /**
     * Selects a keyframe by index
     */
    fun selectKeyframe(index: Int) {
        val track = _selectedTrack.value ?: return
        if (index >= 0 && index < track.keyframes.size) {
            _selectedKeyframes.value = setOf(index)
        }
    }

    /**
     * Adds a keyframe to the selection
     */
    fun addToSelection(index: Int) {
        val track = _selectedTrack.value ?: return
        if (index >= 0 && index < track.keyframes.size) {
            _selectedKeyframes.value = _selectedKeyframes.value + index
        }
    }

    /**
     * Selects keyframes in a range
     */
    fun selectRange(startIndex: Int, endIndex: Int) {
        val track = _selectedTrack.value ?: return
        val validStart = startIndex.coerceAtLeast(0)
        val validEnd = endIndex.coerceAtMost(track.keyframes.size - 1)

        if (validStart <= validEnd) {
            _selectedKeyframes.value = (validStart..validEnd).toSet()
        }
    }

    /**
     * Box selection within a rectangular area
     */
    fun boxSelect(topLeft: Vector2, bottomRight: Vector2) {
        val track = _selectedTrack.value ?: return
        val minTime = min(topLeft.x, bottomRight.x)
        val maxTime = max(topLeft.x, bottomRight.x)
        val minValue = min(topLeft.y, bottomRight.y)
        val maxValue = max(topLeft.y, bottomRight.y)

        val selectedIndices = track.keyframes.mapIndexedNotNull { index, keyframe ->
            val value = when (keyframe.value) {
                is Number -> keyframe.value.toFloat()
                else -> 0.0f
            }

            if (keyframe.time in minTime..maxTime && value in minValue..maxValue) {
                index
            } else {
                null
            }
        }.toSet()

        _selectedKeyframes.value = selectedIndices
    }

    /**
     * Moves a Bezier handle for a keyframe
     */
    fun moveHandle(keyframeIndex: Int, handle: CurveHandle, newPosition: Vector2) {
        val track = _selectedTrack.value ?: return
        if (keyframeIndex < 0 || keyframeIndex >= track.keyframes.size) return

        saveState()

        val keyframe = track.keyframes[keyframeIndex]
        val currentHandles = keyframe.handles ?: BezierHandles(
            Vector2(-0.1f, 0.0f),
            Vector2(0.1f, 0.0f)
        )

        val newHandles = when (handle) {
            CurveHandle.LEFT -> currentHandles.copy(leftHandle = newPosition)
            CurveHandle.RIGHT -> currentHandles.copy(rightHandle = newPosition)
        }

        val updatedKeyframe = keyframe.copy(handles = newHandles)
        val updatedKeyframes = track.keyframes.toMutableList()
        updatedKeyframes[keyframeIndex] = updatedKeyframe

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Sets handle mode for auto-tangent calculation
     */
    fun setHandleMode(keyframeIndex: Int, mode: CurveHandleMode) {
        val track = _selectedTrack.value ?: return
        if (keyframeIndex < 0 || keyframeIndex >= track.keyframes.size) return

        saveState()

        val keyframe = track.keyframes[keyframeIndex]
        val newHandles = when (mode) {
            CurveHandleMode.AUTO -> calculateAutoTangent(track, keyframeIndex)
            CurveHandleMode.MANUAL -> keyframe.handles ?: BezierHandles(
                Vector2(-0.1f, 0.0f),
                Vector2(0.1f, 0.0f)
            )
            CurveHandleMode.BROKEN -> keyframe.handles // Keep existing handles
        }

        val updatedKeyframe = keyframe.copy(handles = newHandles)
        val updatedKeyframes = track.keyframes.toMutableList()
        updatedKeyframes[keyframeIndex] = updatedKeyframe

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Sets interpolation type for the selected track
     */
    fun setInterpolationType(interpolationType: InterpolationType) {
        val track = _selectedTrack.value ?: return
        saveState()

        val updatedTrack = track.copy(interpolation = interpolationType)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Applies an easing preset to selected keyframes
     */
    fun applyEasingPreset(preset: EasingPreset) {
        val track = _selectedTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveState()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                val (easingType, handles) = getPresetConfiguration(preset)
                keyframe.copy(easing = easingType, handles = handles)
            } else {
                keyframe
            }
        }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Pans the viewport
     */
    fun pan(offset: Vector2) {
        _panOffset.value = _panOffset.value + offset
    }

    /**
     * Zooms the viewport at a focal point
     */
    fun zoom(factor: Float, focalPoint: Vector2) {
        val newZoom = (_zoomLevel.value * factor).coerceIn(0.1f, 10.0f)
        _zoomLevel.value = newZoom

        // Adjust pan to keep focal point centered
        val zoomDelta = factor - 1.0f
        val panAdjustment = focalPoint * zoomDelta
        _panOffset.value = _panOffset.value - panAdjustment
    }

    /**
     * Frames all visible curves in the viewport
     */
    fun frameAll() {
        val visibleTracks = visibleTracks.value
        if (visibleTracks.isEmpty()) return

        val allKeyframes = visibleTracks.flatMap { it.keyframes }
        if (allKeyframes.isEmpty()) return

        val minTime = allKeyframes.minOf { it.time }
        val maxTime = allKeyframes.maxOf { it.time }
        val minValue = allKeyframes.minOf {
            when (val value = it.value) {
                is Number -> value.toFloat()
                else -> 0.0f
            }
        }
        val maxValue = allKeyframes.maxOf {
            when (val value = it.value) {
                is Number -> value.toFloat()
                else -> 0.0f
            }
        }

        val timeRange = maxTime - minTime
        val valueRange = maxValue - minValue

        // Calculate zoom to fit content with padding
        val padding = 0.1f
        val viewportAspect = 16.0f / 9.0f // Assume typical aspect ratio
        val contentAspect = timeRange / valueRange

        _zoomLevel.value = if (contentAspect > viewportAspect) {
            // Content is wider, fit to width
            1.0f / (timeRange * (1.0f + padding))
        } else {
            // Content is taller, fit to height
            1.0f / (valueRange * (1.0f + padding))
        }

        // Center the content
        _panOffset.value = Vector2(
            -(minTime + timeRange * 0.5f),
            -(minValue + valueRange * 0.5f)
        )
    }

    /**
     * Resets viewport to default position
     */
    fun resetViewport() {
        _zoomLevel.value = 1.0f
        _panOffset.value = Vector2(0.0f, 0.0f)
    }

    /**
     * Evaluates a curve at a specific time
     */
    fun evaluateCurveAt(trackId: String, time: Float): Any? {
        val track = _tracks.value.find { it.id == trackId } ?: return null

        // Check cache first
        val cached = evaluationCache[trackId]?.get(time)
        if (cached != null) return cached

        val result = track.evaluateAtTime(time).value

        // Cache the result
        val trackCache = evaluationCache.getOrPut(trackId) { mutableMapOf() }
        if (trackCache.size < 1000) { // Limit cache size
            trackCache[time] = result
        }

        return result
    }

    /**
     * Creates a keyframe on a curve at specified time and value
     */
    fun createKeyframeOnCurve(trackId: String, time: Float, value: Any) {
        val track = _tracks.value.find { it.id == trackId } ?: return
        if (track != _selectedTrack.value) {
            selectTrack(trackId)
        }

        saveState()

        val newKeyframe = Keyframe(time, value)
        val updatedTrack = track.addKeyframe(newKeyframe)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Sets the current edit mode
     */
    fun setEditMode(mode: CurveEditMode) {
        _editMode.value = mode
    }

    /**
     * Smooths selected keyframes using auto-tangent calculation
     */
    fun smoothSelectedKeyframes(strength: Float = 0.5f) {
        val track = _selectedTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveState()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                val autoHandles = calculateAutoTangent(track, index, strength)
                keyframe.copy(handles = autoHandles)
            } else {
                keyframe
            }
        }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Breaks tangents for a keyframe (makes handles independent)
     */
    fun breakTangents(keyframeIndex: Int) {
        val track = _selectedTrack.value ?: return
        if (keyframeIndex < 0 || keyframeIndex >= track.keyframes.size) return

        saveState()

        val keyframe = track.keyframes[keyframeIndex]
        val handles = keyframe.handles ?: BezierHandles(
            Vector2(-0.1f, 0.0f),
            Vector2(0.1f, 0.0f)
        )

        // Mark as broken by storing different handle values
        val updatedKeyframe = keyframe.copy(handles = handles)
        val updatedKeyframes = track.keyframes.toMutableList()
        updatedKeyframes[keyframeIndex] = updatedKeyframe

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Unifies tangents for a keyframe (makes handles symmetric)
     */
    fun unifyTangents(keyframeIndex: Int) {
        val track = _selectedTrack.value ?: return
        if (keyframeIndex < 0 || keyframeIndex >= track.keyframes.size) return

        saveState()

        val keyframe = track.keyframes[keyframeIndex]
        val handles = keyframe.handles ?: BezierHandles(
            Vector2(-0.1f, 0.0f),
            Vector2(0.1f, 0.0f)
        )

        // Make symmetric based on right handle
        val symmetricHandles = BezierHandles(
            Vector2(-handles.rightHandle.x, -handles.rightHandle.y),
            handles.rightHandle
        )

        val updatedKeyframe = keyframe.copy(handles = symmetricHandles)
        val updatedKeyframes = track.keyframes.toMutableList()
        updatedKeyframes[keyframeIndex] = updatedKeyframe

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Moves selected keyframes by offset
     */
    fun moveSelectedKeyframes(offset: Vector2) {
        val track = _selectedTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveState()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                val newTime = (keyframe.time + offset.x).coerceAtLeast(0.0f)
                val currentValue = when (keyframe.value) {
                    is Number -> keyframe.value.toFloat()
                    else -> 0.0f
                }
                val newValue = currentValue + offset.y
                keyframe.copy(time = newTime, value = newValue)
            } else {
                keyframe
            }
        }.sortedBy { it.time }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        updateTrack(updatedTrack)
        clearCache()
    }

    /**
     * Filters tracks by property type
     */
    fun filterTracksByProperty(property: AnimatableProperty) {
        _activePropertyFilter.value = property
    }

    /**
     * Clears the track filter
     */
    fun clearTrackFilter() {
        _activePropertyFilter.value = null
    }

    /**
     * Isolates a single track (hides all others)
     */
    fun isolateTrack(trackId: String) {
        val visibility = _tracks.value.associate { track ->
            track.id to (track.id == trackId)
        }
        _trackVisibility.value = visibility
    }

    /**
     * Undoes the last operation
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = CurveEditorState(_tracks.value, _selectedTrack.value, _selectedKeyframes.value)
            redoStack.add(currentState)

            val previousState = undoStack.removeAt(undoStack.size - 1)
            restoreState(previousState)
        }
    }

    /**
     * Redoes the last undone operation
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = CurveEditorState(_tracks.value, _selectedTrack.value, _selectedKeyframes.value)
            undoStack.add(currentState)

            val nextState = redoStack.removeAt(redoStack.size - 1)
            restoreState(nextState)
        }
    }

    // Private helper methods

    private fun updateTrack(updatedTrack: AnimationTrack) {
        val updatedTracks = _tracks.value.map { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
        _tracks.value = updatedTracks
        _selectedTrack.value = updatedTrack
        _selectedTracks.value = _selectedTracks.value.map { track ->
            if (track.id == updatedTrack.id) updatedTrack else track
        }
    }

    private fun calculateAutoTangent(track: AnimationTrack, keyframeIndex: Int, strength: Float = 0.5f): BezierHandles {
        val keyframes = track.keyframes
        val keyframe = keyframes[keyframeIndex]

        val prevKeyframe = keyframes.getOrNull(keyframeIndex - 1)
        val nextKeyframe = keyframes.getOrNull(keyframeIndex + 1)

        val currentValue = when (keyframe.value) {
            is Number -> keyframe.value.toFloat()
            else -> 0.0f
        }

        val leftHandle = if (prevKeyframe != null) {
            val prevValue = when (prevKeyframe.value) {
                is Number -> prevKeyframe.value.toFloat()
                else -> 0.0f
            }
            val timeDiff = keyframe.time - prevKeyframe.time
            val valueDiff = currentValue - prevValue
            Vector2(-timeDiff * 0.3f * strength, -valueDiff * 0.3f * strength)
        } else {
            Vector2(-0.1f, 0.0f)
        }

        val rightHandle = if (nextKeyframe != null) {
            val nextValue = when (nextKeyframe.value) {
                is Number -> nextKeyframe.value.toFloat()
                else -> 0.0f
            }
            val timeDiff = nextKeyframe.time - keyframe.time
            val valueDiff = nextValue - currentValue
            Vector2(timeDiff * 0.3f * strength, valueDiff * 0.3f * strength)
        } else {
            Vector2(0.1f, 0.0f)
        }

        return BezierHandles(leftHandle, rightHandle)
    }

    private fun getPresetConfiguration(preset: EasingPreset): Pair<EasingType, BezierHandles?> {
        return when (preset) {
            EasingPreset.LINEAR -> EasingType.LINEAR to null
            EasingPreset.EASE_IN_QUAD -> EasingType.EASE_IN to BezierHandles(
                Vector2(-0.3f, 0.0f), Vector2(0.1f, 0.4f)
            )
            EasingPreset.EASE_OUT_QUAD -> EasingType.EASE_OUT to BezierHandles(
                Vector2(-0.1f, 0.4f), Vector2(0.3f, 0.0f)
            )
            EasingPreset.EASE_IN_OUT_QUAD -> EasingType.EASE_IN_OUT to BezierHandles(
                Vector2(-0.2f, 0.0f), Vector2(0.2f, 0.0f)
            )
            EasingPreset.BOUNCE -> EasingType.BOUNCE to BezierHandles(
                Vector2(-0.1f, -0.2f), Vector2(0.1f, 0.8f)
            )
        }
    }

    private fun saveState() {
        val state = CurveEditorState(_tracks.value, _selectedTrack.value, _selectedKeyframes.value)
        undoStack.add(state)

        // Limit undo stack size
        if (undoStack.size > 50) {
            undoStack.removeAt(0)
        }

        redoStack.clear()
    }

    private fun restoreState(state: CurveEditorState) {
        _tracks.value = state.tracks
        _selectedTrack.value = state.selectedTrack
        _selectedKeyframes.value = state.selectedKeyframes
        clearCache()
    }

    private fun clearCache() {
        evaluationCache.clear()
    }
}

/**
 * Curve editing modes
 */
enum class CurveEditMode {
    SELECT,    // Selection mode
    CREATE,    // Creating keyframes
    MOVE,      // Moving keyframes
    SCALE,     // Scaling values
    HANDLE     // Editing handles
}

/**
 * Curve handle types
 */
enum class CurveHandle {
    LEFT, RIGHT
}

/**
 * Handle mode for tangent calculation
 */
enum class CurveHandleMode {
    AUTO,      // Automatically calculated smooth tangents
    MANUAL,    // Manually controlled handles
    BROKEN     // Independent left and right handles
}

/**
 * Easing presets for quick application
 */
enum class EasingPreset {
    LINEAR,
    EASE_IN_QUAD,
    EASE_OUT_QUAD,
    EASE_IN_OUT_QUAD,
    BOUNCE
}

/**
 * State for undo/redo operations
 */
data class CurveEditorState(
    val tracks: List<AnimationTrack>,
    val selectedTrack: AnimationTrack?,
    val selectedKeyframes: Set<Int>
)