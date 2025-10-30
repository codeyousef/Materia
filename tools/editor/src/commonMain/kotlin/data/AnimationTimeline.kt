package io.materia.tools.editor.data

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.math.*

/**
 * AnimationTimeline - Data model for animation editing and preview
 *
 * This data class represents a complete animation timeline containing multiple tracks,
 * keyframes, and timeline markers. It supports various interpolation types and can be
 * serialized to binary format for performance or JSON for export compatibility.
 */
@Serializable
data class AnimationTimeline @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    val duration: Float,
    val frameRate: Int,
    val tracks: List<AnimationTrack>,
    val markers: List<TimelineMarker>,
    val settings: TimelineSettings
) {
    init {
        require(name.isNotBlank()) { "Animation timeline name must be non-empty" }
        require(duration > 0.0f) { "Animation duration must be positive" }
        require(frameRate > 0) { "Frame rate must be positive" }
        require(frameRate <= 240) { "Frame rate cannot exceed 240 FPS for performance reasons" }

        // Validate that all keyframes are within the timeline duration
        tracks.forEach { track ->
            track.keyframes.forEach { keyframe ->
                require(keyframe.time >= 0.0f && keyframe.time <= duration) {
                    "Keyframe at time ${keyframe.time} in track '${track.id}' is outside timeline duration $duration"
                }
            }
        }

        // Validate that all markers are within the timeline duration
        markers.forEach { marker ->
            require(marker.time >= 0.0f && marker.time <= duration) {
                "Timeline marker '${marker.name}' at time ${marker.time} is outside timeline duration $duration"
            }
        }

        // Validate unique track IDs
        val trackIds = tracks.map { it.id }
        require(trackIds.size == trackIds.toSet().size) {
            "Animation tracks must have unique IDs"
        }

        // Validate unique marker names
        val markerNames = markers.map { it.name }
        require(markerNames.size == markerNames.toSet().size) {
            "Timeline markers must have unique names"
        }
    }

    /**
     * Total number of frames in this timeline
     */
    val totalFrames: Int
        get() = (duration * frameRate).toInt()

    /**
     * Converts time in seconds to frame number
     */
    fun timeToFrame(time: Float): Int {
        return (time * frameRate).toInt().coerceIn(0, totalFrames - 1)
    }

    /**
     * Converts frame number to time in seconds
     */
    fun frameToTime(frame: Int): Float {
        return frame.toFloat() / frameRate.toFloat()
    }

    /**
     * Gets all animated values at a specific time
     */
    fun evaluateAtTime(time: Float): Map<String, AnimatedValue> {
        val clampedTime = time.coerceIn(0.0f, duration)
        return tracks.filter { it.enabled }.associate { track ->
            track.targetPath to track.evaluateAtTime(clampedTime)
        }
    }

    /**
     * Gets the timeline marker closest to the specified time
     */
    fun getClosestMarker(time: Float): TimelineMarker? {
        return markers.minByOrNull { abs(it.time - time) }
    }

    /**
     * Adds a new track to the timeline
     */
    fun addTrack(track: AnimationTrack): AnimationTimeline {
        require(tracks.none { it.id == track.id }) {
            "Track with ID '${track.id}' already exists"
        }
        return copy(tracks = tracks + track)
    }

    /**
     * Removes a track from the timeline
     */
    fun removeTrack(trackId: String): AnimationTimeline {
        return copy(tracks = tracks.filter { it.id != trackId })
    }

    /**
     * Updates an existing track
     */
    fun updateTrack(trackId: String, updater: (AnimationTrack) -> AnimationTrack): AnimationTimeline {
        val trackIndex = tracks.indexOfFirst { it.id == trackId }
        return if (trackIndex >= 0) {
            val updatedTracks = tracks.toMutableList()
            updatedTracks[trackIndex] = updater(tracks[trackIndex])
            copy(tracks = updatedTracks)
        } else {
            this
        }
    }

    /**
     * Adds a timeline marker
     */
    fun addMarker(marker: TimelineMarker): AnimationTimeline {
        require(markers.none { it.name == marker.name }) {
            "Marker with name '${marker.name}' already exists"
        }
        val newMarkers = (markers + marker).sortedBy { it.time }
        return copy(markers = newMarkers)
    }

    /**
     * Removes a timeline marker
     */
    fun removeMarker(markerName: String): AnimationTimeline {
        return copy(markers = markers.filter { it.name != markerName })
    }

    companion object {
        /**
         * Creates a new empty animation timeline
         */
        fun createEmpty(name: String, duration: Float = 5.0f, frameRate: Int = 30): AnimationTimeline {
            return AnimationTimeline(
                name = name,
                duration = duration,
                frameRate = frameRate,
                tracks = emptyList(),
                markers = emptyList(),
                settings = TimelineSettings.default()
            )
        }

        /**
         * Creates a simple position animation for an object
         */
        fun createPositionAnimation(
            name: String,
            targetPath: String,
            startPosition: Vector3,
            endPosition: Vector3,
            duration: Float = 2.0f
        ): AnimationTimeline {
            val positionTrack = AnimationTrack.createPositionTrack(
                targetPath = targetPath,
                keyframes = listOf(
                    Keyframe(0.0f, startPosition),
                    Keyframe(duration, endPosition)
                )
            )

            return AnimationTimeline(
                name = name,
                duration = duration,
                frameRate = 30,
                tracks = listOf(positionTrack),
                markers = listOf(
                    TimelineMarker("Start", 0.0f, MarkerType.KEYFRAME),
                    TimelineMarker("End", duration, MarkerType.KEYFRAME)
                ),
                settings = TimelineSettings.default()
            )
        }
    }
}

/**
 * AnimationTrack - Represents a single property being animated over time
 */
@Serializable
data class AnimationTrack @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val targetPath: String, // Path to the object/property being animated (e.g., "scene.objects.cube1.transform.position.x")
    val property: AnimatableProperty,
    val keyframes: List<Keyframe>,
    val interpolation: InterpolationType,
    val enabled: Boolean = true,
    val color: TrackColor = TrackColor.BLUE,
    val locked: Boolean = false
) {
    init {
        require(targetPath.isNotBlank()) { "Target path must be non-empty" }
        require(keyframes.isNotEmpty()) { "Animation track must have at least one keyframe" }

        // Validate keyframes are sorted by time
        val sortedKeyframes = keyframes.sortedBy { it.time }
        require(keyframes == sortedKeyframes) {
            "Keyframes must be sorted by time"
        }

        // Validate keyframe values match the property type
        keyframes.forEach { keyframe ->
            validateKeyframeValue(keyframe, property)
        }
    }

    private fun validateKeyframeValue(keyframe: Keyframe, property: AnimatableProperty) {
        when (property) {
            AnimatableProperty.POSITION_X, AnimatableProperty.POSITION_Y, AnimatableProperty.POSITION_Z,
            AnimatableProperty.ROTATION_X, AnimatableProperty.ROTATION_Y, AnimatableProperty.ROTATION_Z,
            AnimatableProperty.SCALE_X, AnimatableProperty.SCALE_Y, AnimatableProperty.SCALE_Z,
            AnimatableProperty.OPACITY, AnimatableProperty.MORPH_WEIGHT -> {
                require(keyframe.value is Number) {
                    "Keyframe value for property $property must be a number"
                }
            }
            AnimatableProperty.COLOR -> {
                val list = keyframe.value as? List<*>
                require(list != null && list.size in 3..4) {
                    "Keyframe value for COLOR property must be a list of 3 or 4 numbers"
                }
            }
        }
    }

    /**
     * Evaluates the track value at a specific time using interpolation
     */
    fun evaluateAtTime(time: Float): AnimatedValue {
        if (keyframes.isEmpty()) {
            return AnimatedValue.createDefault(property)
        }

        if (keyframes.size == 1) {
            return AnimatedValue(property, keyframes.first().value)
        }

        // Find surrounding keyframes
        val afterIndex = keyframes.indexOfFirst { it.time >= time }

        return when {
            afterIndex == -1 -> {
                // Time is after all keyframes, use last value
                AnimatedValue(property, keyframes.last().value)
            }
            afterIndex == 0 -> {
                // Time is before all keyframes, use first value
                AnimatedValue(property, keyframes.first().value)
            }
            else -> {
                // Interpolate between keyframes
                val keyframeBefore = keyframes[afterIndex - 1]
                val keyframeAfter = keyframes[afterIndex]
                interpolateKeyframes(keyframeBefore, keyframeAfter, time)
            }
        }
    }

    private fun interpolateKeyframes(before: Keyframe, after: Keyframe, time: Float): AnimatedValue {
        val t = (time - before.time) / (after.time - before.time)
        val easedT = applyEasing(t, before.easing)

        val interpolatedValue = when (interpolation) {
            InterpolationType.LINEAR -> interpolateLinear(before.value, after.value, easedT)
            InterpolationType.STEP -> before.value // Step interpolation uses the before value
            InterpolationType.CUBIC -> {
                if (before.handles != null && after.handles != null) {
                    interpolateCubic(before, after, easedT)
                } else {
                    interpolateLinear(before.value, after.value, easedT)
                }
            }
            InterpolationType.SMOOTH -> interpolateSmooth(before.value, after.value, easedT)
        }

        return AnimatedValue(property, interpolatedValue)
    }

    private fun applyEasing(t: Float, easing: EasingType): Float {
        return when (easing) {
            EasingType.LINEAR -> t
            EasingType.EASE_IN -> t * t
            EasingType.EASE_OUT -> 1.0f - (1.0f - t) * (1.0f - t)
            EasingType.EASE_IN_OUT -> {
                if (t < 0.5f) {
                    2.0f * t * t
                } else {
                    1.0f - 2.0f * (1.0f - t) * (1.0f - t)
                }
            }
            EasingType.BOUNCE -> {
                if (t < 1.0f / 2.75f) {
                    7.5625f * t * t
                } else if (t < 2.0f / 2.75f) {
                    7.5625f * (t - 1.5f / 2.75f) * (t - 1.5f / 2.75f) + 0.75f
                } else if (t < 2.5f / 2.75f) {
                    7.5625f * (t - 2.25f / 2.75f) * (t - 2.25f / 2.75f) + 0.9375f
                } else {
                    7.5625f * (t - 2.625f / 2.75f) * (t - 2.625f / 2.75f) + 0.984375f
                }
            }
        }
    }

    private fun interpolateLinear(a: Any, b: Any, t: Float): Any {
        return when (a) {
            is Float -> {
                val bFloat = b as? Float ?: return a
                a + (bFloat - a) * t
            }
            is Double -> {
                val bDouble = b as? Double ?: return a
                a + (bDouble - a) * t
            }
            is Int -> {
                val bInt = b as? Int ?: return a
                (a + (bInt - a) * t).toInt()
            }
            is List<*> -> {
                val listA = a as? List<Number> ?: return a
                val listB = b as? List<Number> ?: return a
                if (listA.size != listB.size) return a
                listA.zip(listB) { numA, numB ->
                    numA.toFloat() + (numB.toFloat() - numA.toFloat()) * t
                }
            }
            else -> if (t < 0.5f) a else b
        }
    }

    private fun interpolateSmooth(a: Any, b: Any, t: Float): Any {
        val smoothT = t * t * (3.0f - 2.0f * t) // Smoothstep function
        return interpolateLinear(a, b, smoothT)
    }

    private fun interpolateCubic(before: Keyframe, after: Keyframe, t: Float): Any {
        // Simplified cubic interpolation using handles
        // In a full implementation, this would use proper Bezier curve math
        return interpolateSmooth(before.value, after.value, t)
    }

    /**
     * Adds a keyframe to this track, maintaining time order
     */
    fun addKeyframe(keyframe: Keyframe): AnimationTrack {
        validateKeyframeValue(keyframe, property)
        val newKeyframes = (keyframes + keyframe).sortedBy { it.time }
        return copy(keyframes = newKeyframes)
    }

    /**
     * Removes keyframes at the specified time (within tolerance)
     */
    fun removeKeyframeAt(time: Float, tolerance: Float = 0.001f): AnimationTrack {
        val filteredKeyframes = keyframes.filter { abs(it.time - time) > tolerance }
        return copy(keyframes = filteredKeyframes)
    }

    companion object {
        /**
         * Creates a position track for animating object position
         */
        fun createPositionTrack(targetPath: String, keyframes: List<Keyframe>): AnimationTrack {
            return AnimationTrack(
                targetPath = "$targetPath.transform.position",
                property = AnimatableProperty.POSITION_X, // Will be expanded to XYZ in practice
                keyframes = keyframes,
                interpolation = InterpolationType.LINEAR
            )
        }

        /**
         * Creates a rotation track for animating object rotation
         */
        fun createRotationTrack(targetPath: String, keyframes: List<Keyframe>): AnimationTrack {
            return AnimationTrack(
                targetPath = "$targetPath.transform.rotation",
                property = AnimatableProperty.ROTATION_X, // Will be expanded to XYZ in practice
                keyframes = keyframes,
                interpolation = InterpolationType.LINEAR
            )
        }

        /**
         * Creates a scale track for animating object scale
         */
        fun createScaleTrack(targetPath: String, keyframes: List<Keyframe>): AnimationTrack {
            return AnimationTrack(
                targetPath = "$targetPath.transform.scale",
                property = AnimatableProperty.SCALE_X, // Will be expanded to XYZ in practice
                keyframes = keyframes,
                interpolation = InterpolationType.LINEAR
            )
        }
    }
}

/**
 * Keyframe - Represents a single animation keyframe with time, value, and easing
 */
@Serializable
data class Keyframe(
    val time: Float,
    val value: Any,
    val easing: EasingType = EasingType.LINEAR,
    val handles: BezierHandles? = null,
    val selected: Boolean = false
) {
    init {
        require(time >= 0.0f) { "Keyframe time must be non-negative" }
    }
}

/**
 * BezierHandles - Control handles for cubic Bezier interpolation
 */
@Serializable
data class BezierHandles(
    val leftHandle: Vector2,
    val rightHandle: Vector2
)

/**
 * Vector2 - 2D vector for handle positions
 */
@Serializable
data class Vector2(
    val x: Float,
    val y: Float
) {
    operator fun plus(other: Vector2): Vector2 = Vector2(x + other.x, y + other.y)
    operator fun minus(other: Vector2): Vector2 = Vector2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vector2 = Vector2(x * scalar, y * scalar)
    operator fun div(scalar: Float): Vector2 = Vector2(x / scalar, y / scalar)
}

/**
 * TimelineMarker - Marks important points in the timeline
 */
@Serializable
data class TimelineMarker(
    val name: String,
    val time: Float,
    val type: MarkerType,
    val color: MarkerColor = MarkerColor.YELLOW,
    val description: String = ""
) {
    init {
        require(name.isNotBlank()) { "Marker name must be non-empty" }
        require(time >= 0.0f) { "Marker time must be non-negative" }
    }
}

/**
 * TimelineSettings - Configuration for timeline playback and display
 */
@Serializable
data class TimelineSettings(
    val loop: Boolean = false,
    val autoKey: Boolean = false, // Automatically create keyframes when values change
    val snapToFrames: Boolean = true,
    val showOnlySelectedTracks: Boolean = false,
    val trackHeight: Int = 20,
    val timelineZoom: Float = 1.0f,
    val playbackSpeed: Float = 1.0f
) {
    init {
        require(trackHeight > 0) { "Track height must be positive" }
        require(timelineZoom > 0.0f) { "Timeline zoom must be positive" }
        require(playbackSpeed > 0.0f) { "Playback speed must be positive" }
    }

    companion object {
        fun default(): TimelineSettings = TimelineSettings()
    }
}

/**
 * AnimatedValue - Represents a computed animation value with its property type
 */
data class AnimatedValue(
    val property: AnimatableProperty,
    val value: Any
) {
    companion object {
        fun createDefault(property: AnimatableProperty): AnimatedValue {
            val defaultValue = when (property) {
                AnimatableProperty.POSITION_X, AnimatableProperty.POSITION_Y, AnimatableProperty.POSITION_Z -> 0.0f
                AnimatableProperty.ROTATION_X, AnimatableProperty.ROTATION_Y, AnimatableProperty.ROTATION_Z -> 0.0f
                AnimatableProperty.SCALE_X, AnimatableProperty.SCALE_Y, AnimatableProperty.SCALE_Z -> 1.0f
                AnimatableProperty.OPACITY -> 1.0f
                AnimatableProperty.MORPH_WEIGHT -> 0.0f
                AnimatableProperty.COLOR -> listOf(1.0f, 1.0f, 1.0f, 1.0f)
            }
            return AnimatedValue(property, defaultValue)
        }
    }
}

// Enums

@Serializable
enum class AnimatableProperty {
    POSITION_X, POSITION_Y, POSITION_Z,
    ROTATION_X, ROTATION_Y, ROTATION_Z,
    SCALE_X, SCALE_Y, SCALE_Z,
    OPACITY, COLOR, MORPH_WEIGHT
}

@Serializable
enum class InterpolationType {
    LINEAR, STEP, CUBIC, SMOOTH
}

@Serializable
enum class EasingType {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, BOUNCE
}

@Serializable
enum class MarkerType {
    KEYFRAME, SECTION, CUE, LOOP_START, LOOP_END
}

@Serializable
enum class MarkerColor {
    RED, GREEN, BLUE, YELLOW, ORANGE, PURPLE, CYAN, GRAY
}

@Serializable
enum class TrackColor {
    RED, GREEN, BLUE, YELLOW, ORANGE, PURPLE, CYAN, GRAY, WHITE
}