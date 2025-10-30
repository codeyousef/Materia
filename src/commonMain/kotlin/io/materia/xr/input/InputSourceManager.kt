package io.materia.xr.input

import io.materia.core.math.Vector3
import io.materia.xr.*
import kotlinx.coroutines.*

/**
 * Input source manager for unified input handling
 */
class XRInputSourceManager(
    private val session: XRSession,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val inputSources = mutableMapOf<String, DefaultXRInputSource>()
    private val inputCallbacks = mutableListOf<XRInputCallback>()

    private var primaryHand: DefaultXRHand? = null
    private var secondaryHand: DefaultXRHand? = null
    private var eyeGaze: DefaultXRGaze? = null

    private var monitoringJob: Job? = null

    init {
        startInputMonitoring()
    }

    fun getPrimaryHand(): XRHand? = primaryHand
    fun getSecondaryHand(): XRHand? = secondaryHand
    fun getEyeGaze(): XRGaze? = eyeGaze

    fun registerCallback(callback: XRInputCallback) {
        inputCallbacks.add(callback)
    }

    fun unregisterCallback(callback: XRInputCallback) {
        inputCallbacks.remove(callback)
    }

    private fun startInputMonitoring() {
        // Use the provided coroutine scope for proper lifecycle management
        monitoringJob = coroutineScope.launch {
            while (session.isSessionActive()) {
                updateInputSources()
                updateHandTracking()
                updateEyeTracking()
                delay(16)
            }
        }
    }

    /**
     * Dispose of resources and cancel background monitoring.
     * Must be called when the manager is no longer needed to prevent memory leaks.
     */
    fun dispose() {
        monitoringJob?.cancel()
        coroutineScope.cancel() // Cancel the scope to ensure all coroutines are stopped
        inputSources.clear()
        inputCallbacks.clear()
        primaryHand = null
        secondaryHand = null
        eyeGaze = null
    }

    private suspend fun updateInputSources() {
        val currentSources = session.inputSources

        currentSources.forEach { source ->
            val id = getInputSourceId(source)
            if (!inputSources.containsKey(id)) {
                handleNewInputSource(source)
            }
        }

        val currentIds = currentSources.map { getInputSourceId(it) }.toSet()
        val removedIds = inputSources.keys - currentIds

        removedIds.forEach { id ->
            handleRemovedInputSource(id)
        }
    }

    private fun handleNewInputSource(source: XRInputSource) {
        val id = getInputSourceId(source)
        val defaultSource = source as? DefaultXRInputSource ?: return

        inputSources[id] = defaultSource

        source.hand?.let { hand ->
            when (source.handedness) {
                XRHandedness.LEFT -> {
                    primaryHand = hand as? DefaultXRHand
                    inputCallbacks.forEach { it.onHandTrackingStarted(hand, XRHandedness.LEFT) }
                }

                XRHandedness.RIGHT -> {
                    secondaryHand = hand as? DefaultXRHand
                    inputCallbacks.forEach { it.onHandTrackingStarted(hand, XRHandedness.RIGHT) }
                }

                else -> {}
            }
        }
    }

    private fun handleRemovedInputSource(id: String) {
        val source = inputSources.remove(id) ?: return

        source.hand?.let { hand ->
            when (source.handedness) {
                XRHandedness.LEFT -> {
                    primaryHand = null
                    inputCallbacks.forEach { it.onHandTrackingLost(XRHandedness.LEFT) }
                }

                XRHandedness.RIGHT -> {
                    secondaryHand = null
                    inputCallbacks.forEach { it.onHandTrackingLost(XRHandedness.RIGHT) }
                }

                else -> {}
            }
        }

        eyeGaze = null
        inputCallbacks.forEach { it.onEyeTrackingLost() }
    }

    private suspend fun updateHandTracking() {
        primaryHand?.let { hand ->
            updateHandJoints(hand)
            val gestures = hand.detectGestures()
            if (gestures.isNotEmpty()) {
                inputCallbacks.forEach {
                    it.onHandGestureDetected(gestures, XRHandedness.LEFT)
                }
            }
        }

        secondaryHand?.let { hand ->
            updateHandJoints(hand)
            val gestures = hand.detectGestures()
            if (gestures.isNotEmpty()) {
                inputCallbacks.forEach {
                    it.onHandGestureDetected(gestures, XRHandedness.RIGHT)
                }
            }
        }
    }

    private suspend fun updateHandJoints(hand: DefaultXRHand) {
        val jointPoses = getPlatformHandJointPoses(hand)
        jointPoses.forEach { entry ->
            hand.updateJointPose(entry.key, entry.value)
        }
    }

    private suspend fun updateEyeTracking() {
        eyeGaze?.let { gaze ->
            val eyeData = getPlatformEyeTrackingData()
            if (eyeData != null) {
                gaze.updateTracking(true)
                gaze.updateGaze(eyeData.pose, eyeData.direction)
                gaze.updateEyeMetrics(
                    eyeData.openness,
                    eyeData.dilation,
                    eyeData.convergence
                )

                inputCallbacks.forEach {
                    it.onEyeGazeUpdated(eyeData.direction, eyeData.convergence)
                }
            } else {
                gaze.updateTracking(false)
            }
        }
    }

    private fun getInputSourceId(source: XRInputSource): String {
        return "${source.handedness}_${source.targetRayMode}_${source.hashCode()}"
    }
}

/**
 * Input callback interface
 */
interface XRInputCallback {
    fun onHandTrackingStarted(hand: XRHand, handedness: XRHandedness)
    fun onHandTrackingLost(handedness: XRHandedness)
    fun onHandGestureDetected(gestures: List<HandGesture>, handedness: XRHandedness)
    fun onEyeTrackingStarted(gaze: XRGaze)
    fun onEyeTrackingLost()
    fun onEyeGazeUpdated(direction: Vector3, convergenceDistance: Float)
}

// Platform-specific functions (will be implemented via expect/actual)
internal expect suspend fun getPlatformHandJointPoses(
    hand: DefaultXRHand
): Map<XRHandJoint, XRJointPose>

internal expect suspend fun getPlatformEyeTrackingData(): EyeTrackingData?
