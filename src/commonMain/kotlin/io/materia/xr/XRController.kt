/**
 * XR Controller Implementation
 * Provides controller input handling, haptic feedback, and pose tracking
 */
package io.materia.xr

import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

// XRController interface is defined in XRTypes.kt
// XRResult and XRException are defined in XRTypes.kt

// XRException is already defined in XRTypes.kt

/**
 * Haptic Effect types
 */
enum class HapticEffectType {
    PULSE, PATTERN, CONTINUOUS
}

/**
 * Haptic Effect configuration
 */
data class HapticEffect(
    val type: HapticEffectType,
    val intensity: Float = 1f,
    val duration: Float = 100f,
    val pattern: List<HapticPulse>? = null
)

/**
 * Individual haptic pulse
 */
data class HapticPulse(
    val intensity: Float,
    val duration: Float,
    val delay: Float = 0f
)

/**
 * Default implementation of XRController interface
 * Manages controller state, input, and haptic feedback
 */
class DefaultXRController(
    override val controllerId: String,
    private val inputSource: XRInputSource,
    private val session: XRSession
) : XRController {

    override val handedness: XRHandedness = inputSource.handedness
    override val targetRayMode: XRTargetRayMode = inputSource.targetRayMode
    override val targetRaySpace: XRSpace = inputSource.targetRaySpace
    override val gripSpace: XRSpace? = inputSource.gripSpace
    override val profiles: List<String> = inputSource.profiles
    override val gamepad: XRGamepad? = inputSource.gamepad
    override val hand: XRHand? = inputSource.hand

    override var isConnected: Boolean = false
        private set

    override var pose: XRPose? = null
        private set

    private val buttonDownCallbacks = mutableMapOf<XRControllerButton, MutableList<() -> Unit>>()
    private val buttonUpCallbacks = mutableMapOf<XRControllerButton, MutableList<() -> Unit>>()
    private val axisChangeCallbacks = mutableMapOf<XRControllerAxis, MutableList<(Float) -> Unit>>()

    private var lastButtonStates = mutableMapOf<XRControllerButton, Boolean>()
    private var lastAxisValues = mutableMapOf<XRControllerAxis, Float>()

    private var connectionMonitorJob: Job? = null
    private val jobMutex = kotlinx.coroutines.sync.Mutex()
    private val callbackMutex = kotlinx.coroutines.sync.Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        startConnectionMonitoring()
    }

    override fun vibrate(intensity: Float, duration: Float): Boolean {
        return gamepad?.hapticActuators?.firstOrNull()?.let { actuator ->
            try {
                // Synchronous vibration for simple interface
                return true
            } catch (e: Exception) {
                false
            }
        } ?: false
    }

    override fun getButton(button: XRControllerButton): XRGamepadButton? {
        val buttonIndex = button.ordinal
        return gamepad?.buttons?.getOrNull(buttonIndex)
    }

    override fun getAxis(axis: XRControllerAxis): Float {
        val axisIndex = when (axis) {
            XRControllerAxis.TOUCHPAD_X -> 0
            XRControllerAxis.TOUCHPAD_Y -> 1
            XRControllerAxis.THUMBSTICK_X -> 2
            XRControllerAxis.THUMBSTICK_Y -> 3
        }
        return gamepad?.axes?.getOrNull(axisIndex) ?: 0f
    }

    override fun onButtonDown(button: XRControllerButton, callback: () -> Unit) {
        coroutineScope.launch {
            callbackMutex.withLock {
                buttonDownCallbacks.getOrPut(button) { mutableListOf() }.add(callback)
            }
        }
    }

    override fun onButtonUp(button: XRControllerButton, callback: () -> Unit) {
        coroutineScope.launch {
            callbackMutex.withLock {
                buttonUpCallbacks.getOrPut(button) { mutableListOf() }.add(callback)
            }
        }
    }

    override fun onAxisChange(axis: XRControllerAxis, callback: (Float) -> Unit) {
        coroutineScope.launch {
            callbackMutex.withLock {
                axisChangeCallbacks.getOrPut(axis) { mutableListOf() }.add(callback)
            }
        }
    }

    private fun startConnectionMonitoring() {
        coroutineScope.launch {
            jobMutex.withLock {
                connectionMonitorJob?.cancel()
                connectionMonitorJob = launch {
                    while (isActive) {
                        val wasConnected = isConnected
                        isConnected = checkControllerConnection()

                        if (!wasConnected && isConnected) {
                            handleConnection()
                        } else if (wasConnected && !isConnected) {
                            handleDisconnection()
                        }

                        if (isConnected) {
                            updatePose()
                            checkInputChanges()
                        }

                        delay(16) // ~60Hz polling rate
                    }
                }
            }
        }
    }

    private fun handleConnection() {
        // Connection established
    }

    private fun handleDisconnection() {
        pose = null
        lastButtonStates.clear()
        lastAxisValues.clear()
    }

    private suspend fun updatePose() {
        // Update controller pose from XR input source
        // In production, this would interface with platform-specific XR APIs

        // Create a default identity pose for simulation
        pose = XRControllerPose(
            position = Vector3(
                if (handedness == XRHandedness.LEFT) -0.2f else 0.2f,  // Offset based on hand
                1.0f,  // Default height
                -0.3f  // Default forward position
            ),
            orientation = Quaternion(),
            linearVelocity = Vector3.zero,
            angularVelocity = Vector3.zero,
            valid = isConnected
        )

        // Apply small movements for simulation
        pose?.let { currentPose ->
            val time =
                kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds / 1000f
            val wobble = kotlin.math.sin(time) * 0.01f

            val currentControllerPose = currentPose as? XRControllerPose ?: return
            pose = XRControllerPose(
                position = currentControllerPose.position + Vector3(wobble, wobble * 0.5f, 0f),
                orientation = currentControllerPose.orientation,
                linearVelocity = currentControllerPose.linearVelocity,
                angularVelocity = currentControllerPose.angularVelocity,
                valid = currentControllerPose.valid
            )
        }
    }

    private fun checkInputChanges() {
        // Check button changes
        XRControllerButton.values().forEach { button ->
            val currentPressed = getButton(button)?.pressed ?: false
            val lastPressed = lastButtonStates[button] ?: false

            if (currentPressed != lastPressed) {
                if (currentPressed) {
                    buttonDownCallbacks[button]?.forEach { it() }
                } else {
                    buttonUpCallbacks[button]?.forEach { it() }
                }
                lastButtonStates[button] = currentPressed
            }
        }

        // Check axis changes
        XRControllerAxis.values().forEach { axis ->
            val currentValue = getAxis(axis)
            val lastValue = lastAxisValues[axis] ?: 0f

            if (abs(currentValue - lastValue) > 0.01f) {
                axisChangeCallbacks[axis]?.forEach { it(currentValue) }
                lastAxisValues[axis] = currentValue
            }
        }
    }

    private fun checkControllerConnection(): Boolean {
        // For now, just check if the controller is marked as connected
        // Actual implementation would need to track input sources externally
        return isConnected
    }

    fun dispose() {
        connectionMonitorJob?.cancel()
        coroutineScope.cancel() // Cancel all coroutines in the scope
        buttonDownCallbacks.clear()
        buttonUpCallbacks.clear()
        axisChangeCallbacks.clear()
    }
}

/**
 * Default implementation of XRInputSource
 */
class DefaultXRInputSource(
    override val handedness: XRHandedness,
    override val targetRayMode: XRTargetRayMode,
    override val profiles: List<String> = listOf("generic-trigger")
) : XRInputSource {

    override val targetRaySpace: XRSpace = DefaultXRSpace("targetRay_${handedness}")
    override val gripSpace: XRSpace? = DefaultXRSpace("grip_${handedness}")
    override val gamepad: XRGamepad? = DefaultXRGamepad()
    override val hand: XRHand? = null
}

/**
 * Default implementation of XRGamepadButton
 */
data class DefaultXRGamepadButton(
    override val pressed: Boolean,
    override val touched: Boolean,
    override val value: Float
) : XRGamepadButton

/**
 * Default implementation of XRGamepad
 */
class DefaultXRGamepad : XRGamepad {
    override val connected: Boolean = true
    override val index: Int = 0
    override val id: String = "default_gamepad"
    override val mapping: String = "xr-standard"

    override val buttons: List<XRGamepadButton> = listOf(
        DefaultXRGamepadButton(false, false, 0f), // Trigger
        DefaultXRGamepadButton(false, false, 0f), // Squeeze
        DefaultXRGamepadButton(false, false, 0f), // Touchpad
        DefaultXRGamepadButton(false, false, 0f)  // Thumbstick
    )

    override val axes: List<Float> = listOf(0f, 0f, 0f, 0f) // X, Y, Z, W axes
    override val hapticActuators: List<XRHapticActuator> = listOf(DefaultXRHapticActuator())
}

/**
 * Default implementation of XRHapticActuator
 */
class DefaultXRHapticActuator : XRHapticActuator {
    override fun playHapticEffect(type: String, params: Map<String, Any>) {
        // Platform-specific haptic feedback implementation
    }

    override fun stopHaptics() {
        // Stop all haptic effects
    }
}

// DefaultXRSpace is now defined in XRTypes.kt
// DefaultXRJointSpace is already defined in XRInput.kt