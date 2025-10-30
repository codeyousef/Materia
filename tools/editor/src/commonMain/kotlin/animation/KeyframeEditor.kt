package io.materia.tools.editor.animation

import io.materia.tools.editor.data.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * KeyframeEditor - Professional keyframe editing controls
 *
 * Provides comprehensive keyframe manipulation including:
 * - Precise timing control with sub-frame accuracy
 * - Multi-selection with range and toggle operations
 * - Create, delete, move, and scale keyframes
 * - Copy/paste operations with temporal offset
 * - Interpolation and easing type editing
 * - Value constraints and validation
 * - Undo/redo with batch operations
 * - Snap-to-frame functionality
 * - Performance optimization for large keyframe sets
 */
class KeyframeEditor {

    // Core state flows
    private val _currentTrack = MutableStateFlow<AnimationTrack?>(null)
    private val _selectedKeyframes = MutableStateFlow<Set<Int>>(emptySet())
    private val _editMode = MutableStateFlow(KeyframeEditMode.SELECT)
    private val _isEditing = MutableStateFlow(false)
    private val _snapToFrames = MutableStateFlow(false)
    private val _frameRate = MutableStateFlow(30)
    private val _clipboard = MutableStateFlow<List<KeyframeClipboardItem>>(emptyList())

    // Public state flows (read-only)
    val currentTrack: StateFlow<AnimationTrack?> = _currentTrack.asStateFlow()
    val selectedKeyframes: StateFlow<Set<Int>> = _selectedKeyframes.asStateFlow()
    val editMode: StateFlow<KeyframeEditMode> = _editMode.asStateFlow()
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()
    val snapToFrames: StateFlow<Boolean> = _snapToFrames.asStateFlow()
    val frameRate: StateFlow<Int> = _frameRate.asStateFlow()

    // Undo/redo system
    private val undoStack = mutableListOf<TrackState>()
    private val redoStack = mutableListOf<TrackState>()
    private var isBatchEditing = false

    // Value constraints for different property types
    private val propertyConstraints = mapOf(
        AnimatableProperty.OPACITY to ValueRange(0.0f, 1.0f),
        AnimatableProperty.SCALE_X to ValueRange(0.001f, 100.0f),
        AnimatableProperty.SCALE_Y to ValueRange(0.001f, 100.0f),
        AnimatableProperty.SCALE_Z to ValueRange(0.001f, 100.0f),
        AnimatableProperty.MORPH_WEIGHT to ValueRange(0.0f, 1.0f)
    )

    /**
     * Loads a track for editing
     */
    fun loadTrack(track: AnimationTrack) {
        saveState() // Save previous state for undo
        _currentTrack.value = track
        _selectedKeyframes.value = emptySet()
        _editMode.value = KeyframeEditMode.SELECT
        _isEditing.value = false
        clearUndoHistory()
    }

    /**
     * Selects a single keyframe by index
     */
    fun selectKeyframe(index: Int) {
        val track = _currentTrack.value ?: return
        if (index >= 0 && index < track.keyframes.size) {
            _selectedKeyframes.value = setOf(index)
        }
    }

    /**
     * Adds a keyframe to the current selection
     */
    fun addToSelection(index: Int) {
        val track = _currentTrack.value ?: return
        if (index >= 0 && index < track.keyframes.size) {
            _selectedKeyframes.value = _selectedKeyframes.value + index
        }
    }

    /**
     * Toggles selection state of a keyframe
     */
    fun toggleSelection(index: Int) {
        val track = _currentTrack.value ?: return
        if (index >= 0 && index < track.keyframes.size) {
            val currentSelection = _selectedKeyframes.value
            _selectedKeyframes.value = if (index in currentSelection) {
                currentSelection - index
            } else {
                currentSelection + index
            }
        }
    }

    /**
     * Selects a range of keyframes (inclusive)
     */
    fun selectRange(startIndex: Int, endIndex: Int) {
        val track = _currentTrack.value ?: return
        val validStart = startIndex.coerceAtLeast(0)
        val validEnd = endIndex.coerceAtMost(track.keyframes.size - 1)

        if (validStart <= validEnd) {
            _selectedKeyframes.value = (validStart..validEnd).toSet()
        }
    }

    /**
     * Clears the current selection
     */
    fun clearSelection() {
        _selectedKeyframes.value = emptySet()
    }

    /**
     * Creates a new keyframe at specified time with value
     */
    fun createKeyframe(time: Float, value: Any) {
        val track = _currentTrack.value ?: return
        saveStateIfNotBatching()

        val adjustedTime = if (_snapToFrames.value) {
            snapTimeToFrame(time, _frameRate.value)
        } else {
            time
        }

        val constrainedValue = applyValueConstraints(value, track.property)
        val newKeyframe = Keyframe(adjustedTime, constrainedValue)

        val updatedTrack = track.addKeyframe(newKeyframe)
        _currentTrack.value = updatedTrack
    }

    /**
     * Deletes all selected keyframes
     */
    fun deleteSelectedKeyframes() {
        val track = _currentTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveStateIfNotBatching()

        // Remove keyframes in reverse order to maintain indices
        val sortedIndices = selected.sortedDescending()
        var updatedKeyframes = track.keyframes.toMutableList()

        sortedIndices.forEach { index ->
            if (index < updatedKeyframes.size) {
                updatedKeyframes.removeAt(index)
            }
        }

        // Ensure at least one keyframe remains
        if (updatedKeyframes.isEmpty()) {
            updatedKeyframes.add(Keyframe(0.0f, getDefaultValue(track.property)))
        }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        _currentTrack.value = updatedTrack
        _selectedKeyframes.value = emptySet()
    }

    /**
     * Moves selected keyframes by a time offset
     */
    fun moveSelectedKeyframes(timeOffset: Float) {
        val track = _currentTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveStateIfNotBatching()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                val newTime = (keyframe.time + timeOffset).coerceAtLeast(0.0f)
                val adjustedTime = if (_snapToFrames.value) {
                    snapTimeToFrame(newTime, _frameRate.value)
                } else {
                    newTime
                }
                keyframe.copy(time = adjustedTime)
            } else {
                keyframe
            }
        }.sortedBy { it.time }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        _currentTrack.value = updatedTrack

        // Update selection indices after reordering
        updateSelectionAfterReorder(updatedTrack)
    }

    /**
     * Scales values of selected keyframes by a factor
     */
    fun scaleSelectedValues(scaleFactor: Float) {
        val track = _currentTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveStateIfNotBatching()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                val scaledValue = scaleValue(keyframe.value, scaleFactor)
                val constrainedValue = applyValueConstraints(scaledValue, track.property)
                keyframe.copy(value = constrainedValue)
            } else {
                keyframe
            }
        }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        _currentTrack.value = updatedTrack
    }

    /**
     * Sets interpolation type for the track
     */
    fun setInterpolationForSelected(interpolationType: InterpolationType) {
        val track = _currentTrack.value ?: return
        if (_selectedKeyframes.value.isEmpty()) return

        saveStateIfNotBatching()

        val updatedTrack = track.copy(interpolation = interpolationType)
        _currentTrack.value = updatedTrack
    }

    /**
     * Sets easing type for selected keyframes
     */
    fun setEasingForSelected(easingType: EasingType) {
        val track = _currentTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        saveStateIfNotBatching()

        val updatedKeyframes = track.keyframes.mapIndexed { index, keyframe ->
            if (index in selected) {
                keyframe.copy(easing = easingType)
            } else {
                keyframe
            }
        }

        val updatedTrack = track.copy(keyframes = updatedKeyframes)
        _currentTrack.value = updatedTrack
    }

    /**
     * Copies selected keyframes to clipboard
     */
    fun copySelectedKeyframes() {
        val track = _currentTrack.value ?: return
        val selected = _selectedKeyframes.value
        if (selected.isEmpty()) return

        val baseTime = selected.minOf { track.keyframes[it].time }
        val clipboardItems = selected.map { index ->
            val keyframe = track.keyframes[index]
            KeyframeClipboardItem(
                relativeTime = keyframe.time - baseTime,
                value = keyframe.value,
                easing = keyframe.easing,
                handles = keyframe.handles
            )
        }

        _clipboard.value = clipboardItems
    }

    /**
     * Pastes keyframes from clipboard at specified time
     */
    fun pasteKeyframes(pasteTime: Float) {
        val track = _currentTrack.value ?: return
        val clipboardItems = _clipboard.value
        if (clipboardItems.isEmpty()) return

        saveStateIfNotBatching()

        val newKeyframes = clipboardItems.map { item ->
            val absoluteTime = pasteTime + item.relativeTime
            val adjustedTime = if (_snapToFrames.value) {
                snapTimeToFrame(absoluteTime, _frameRate.value)
            } else {
                absoluteTime
            }

            Keyframe(
                time = adjustedTime,
                value = item.value,
                easing = item.easing,
                handles = item.handles
            )
        }

        var updatedTrack = track
        newKeyframes.forEach { keyframe ->
            updatedTrack = updatedTrack.addKeyframe(keyframe)
        }

        _currentTrack.value = updatedTrack
    }

    /**
     * Sets snap to frames mode
     */
    fun setSnapToFrames(snap: Boolean, frameRate: Int = 30) {
        _snapToFrames.value = snap
        _frameRate.value = frameRate
    }

    /**
     * Begins batch editing to group multiple operations
     */
    fun beginBatchEdit() {
        if (!isBatchEditing) {
            saveState()
            isBatchEditing = true
        }
    }

    /**
     * Ends batch editing
     */
    fun endBatchEdit() {
        isBatchEditing = false
    }

    /**
     * Undoes the last operation
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentState = TrackState(_currentTrack.value, _selectedKeyframes.value)
            redoStack.add(currentState)

            val previousState = undoStack.removeAt(undoStack.size - 1)
            _currentTrack.value = previousState.track
            _selectedKeyframes.value = previousState.selection
        }
    }

    /**
     * Redoes the last undone operation
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentState = TrackState(_currentTrack.value, _selectedKeyframes.value)
            undoStack.add(currentState)

            val nextState = redoStack.removeAt(redoStack.size - 1)
            _currentTrack.value = nextState.track
            _selectedKeyframes.value = nextState.selection
        }
    }

    // Private helper methods

    private fun snapTimeToFrame(time: Float, frameRate: Int): Float {
        val frameNumber = round(time * frameRate).toInt()
        return frameNumber.toFloat() / frameRate.toFloat()
    }

    private fun applyValueConstraints(value: Any, property: AnimatableProperty): Any {
        val constraint = propertyConstraints[property] ?: return value

        return when (value) {
            is Number -> value.toFloat().coerceIn(constraint.min, constraint.max)
            is List<*> -> {
                // For color values
                (value as List<Number>).map { num ->
                    num.toFloat().coerceIn(constraint.min, constraint.max)
                }
            }
            else -> value
        }
    }

    private fun scaleValue(value: Any, scaleFactor: Float): Any {
        return when (value) {
            is Float -> value * scaleFactor
            is Double -> value * scaleFactor
            is Int -> (value * scaleFactor).toInt()
            is List<*> -> {
                (value as List<Number>).map { num ->
                    num.toFloat() * scaleFactor
                }
            }
            else -> value
        }
    }

    private fun getDefaultValue(property: AnimatableProperty): Any {
        return when (property) {
            AnimatableProperty.POSITION_X, AnimatableProperty.POSITION_Y, AnimatableProperty.POSITION_Z -> 0.0f
            AnimatableProperty.ROTATION_X, AnimatableProperty.ROTATION_Y, AnimatableProperty.ROTATION_Z -> 0.0f
            AnimatableProperty.SCALE_X, AnimatableProperty.SCALE_Y, AnimatableProperty.SCALE_Z -> 1.0f
            AnimatableProperty.OPACITY -> 1.0f
            AnimatableProperty.MORPH_WEIGHT -> 0.0f
            AnimatableProperty.COLOR -> listOf(1.0f, 1.0f, 1.0f, 1.0f)
        }
    }

    private fun saveState() {
        val currentState = TrackState(_currentTrack.value, _selectedKeyframes.value)
        undoStack.add(currentState)

        // Limit undo stack size for memory management
        if (undoStack.size > 100) {
            undoStack.removeAt(0)
        }

        // Clear redo stack when new action is performed
        redoStack.clear()
    }

    private fun saveStateIfNotBatching() {
        if (!isBatchEditing) {
            saveState()
        }
    }

    private fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun updateSelectionAfterReorder(updatedTrack: AnimationTrack) {
        val track = _currentTrack.value ?: return
        val currentSelection = _selectedKeyframes.value
        val selectedTimes = currentSelection.map { track.keyframes[it].time }

        // Find new indices for the selected times
        val newSelection = selectedTimes.mapNotNull { time ->
            updatedTrack.keyframes.indexOfFirst { it.time == time }.takeIf { it >= 0 }
        }.toSet()

        _selectedKeyframes.value = newSelection
    }
}

/**
 * Keyframe editing modes
 */
enum class KeyframeEditMode {
    SELECT,    // Selection and manipulation
    CREATE,    // Creating new keyframes
    DELETE,    // Deleting keyframes
    MOVE,      // Moving keyframes in time
    SCALE      // Scaling keyframe values
}

/**
 * Value range constraint for property types
 */
data class ValueRange(
    val min: Float,
    val max: Float
)

/**
 * Clipboard item for keyframe copy/paste operations
 */
data class KeyframeClipboardItem(
    val relativeTime: Float,
    val value: Any,
    val easing: EasingType,
    val handles: BezierHandles?
)

/**
 * Track state for undo/redo operations
 */
data class TrackState(
    val track: AnimationTrack?,
    val selection: Set<Int>
)