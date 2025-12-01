package io.materia.audio

import io.materia.camera.Camera
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("$feature is not yet supported on Android")

actual open class Audio actual constructor(
    listener: AudioListener
) : Object3D() {
    private var buffer: AudioBuffer? = null

    actual var volume: Float = 1f
    actual var playbackRate: Float = 1f
    actual var loop: Boolean = false
    actual var autoplay: Boolean = false
    private var endedCallback: (() -> Unit)? = null

    private var _isPlaying: Boolean = false
    actual val isPlaying: Boolean
        get() = _isPlaying

    actual val duration: Float
        get() = buffer?.duration ?: 0f

    actual fun load(url: String): Audio {
        if (buffer == null) {
            buffer = AudioBuffer()
        }
        return this
    }

    actual fun setBuffer(buffer: AudioBuffer): Audio {
        this.buffer = buffer
        return this
    }

    actual fun play(delay: Float): Audio {
        _isPlaying = true
        if (delay <= 0f) {
            endedCallback?.invoke()
        }
        return this
    }

    actual fun pause(): Audio {
        _isPlaying = false
        return this
    }

    actual fun stop(): Audio {
        _isPlaying = false
        return this
    }

    actual fun setVolume(value: Float): Audio {
        volume = value
        return this
    }

    actual fun setPlaybackRate(value: Float): Audio {
        playbackRate = value
        return this
    }

    actual fun setLoop(value: Boolean): Audio {
        loop = value
        return this
    }

    actual fun onEnded(callback: () -> Unit) {
        endedCallback = callback
    }
}

actual class AudioBuffer {
    actual val sampleRate: Float = 44100f
    actual val length: Int = 0
    actual val duration: Float = 0f
    actual val numberOfChannels: Int = 2
}

actual class AudioAnalyser actual constructor(
    audio: Audio,
    actual var fftSize: Int
) {
    actual val frequencyBinCount: Int
        get() = fftSize / 2

    actual var smoothingTimeConstant: Float = 0.8f

    actual fun getFrequencyData(): FloatArray = FloatArray(frequencyBinCount)
    actual fun getByteFrequencyData(): ByteArray = ByteArray(frequencyBinCount)
    actual fun getByteTimeDomainData(): ByteArray = ByteArray(frequencyBinCount)
    actual fun getAverageFrequency(): Float = 0f
}

actual object AudioContext {
    actual val sampleRate: Float = 44100f

    private var stateInternal: AudioContextState = AudioContextState.SUSPENDED
    actual val state: AudioContextState
        get() = stateInternal

    actual suspend fun resume() {
        withContext(Dispatchers.Main) {
            stateInternal = AudioContextState.RUNNING
        }
    }

    actual suspend fun suspend() {
        withContext(Dispatchers.Main) {
            stateInternal = AudioContextState.SUSPENDED
        }
    }

    actual fun close() {
        stateInternal = AudioContextState.CLOSED
    }
}

actual class AudioListener actual constructor(
    camera: Camera?
) : Object3D() {
    private val attachedCamera: Camera? = camera

    actual override fun updateMatrixWorld(force: Boolean) {
        val camera = attachedCamera
        if (camera == null) {
            super.updateMatrixWorld(force)
            return
        }

        camera.quaternion.setFromEuler(camera.rotation)
        camera.updateMatrix()
        camera.updateMatrixWorld(force)
        super.updateMatrixWorld(force)

        position.copy(camera.position)
        rotation.copy(camera.rotation)
        quaternion.copy(camera.quaternion)
        scale.copy(camera.scale)

        matrix.copy(camera.matrix)
        matrixWorld.copy(camera.matrixWorld)
        matrixWorldNeedsUpdate = false

        if (children.isNotEmpty()) {
            for (child in children) {
                child.updateMatrixWorld(force)
            }
        }
    }
}

actual class AudioLoader {
    actual fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        onError?.invoke("Audio loading is not supported on Android yet: $url")
    }

    actual suspend fun loadAsync(url: String): Result<AudioBuffer> {
        return Result.failure(UnsupportedOperationException("Audio loading is not supported on Android yet: $url"))
    }
}

actual class PositionalAudio actual constructor(
    listener: AudioListener
) : Audio(listener) {
    actual var refDistance: Float = 1f
    actual var maxDistance: Float = 10000f
    actual var rolloffFactor: Float = 1f
    actual var distanceModel: DistanceModel = DistanceModel.INVERSE

    actual var coneInnerAngle: Float = 360f
    actual var coneOuterAngle: Float = 360f
    actual var coneOuterGain: Float = 0f

    actual fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float) {
        coneInnerAngle = innerAngle
        coneOuterAngle = outerAngle
        coneOuterGain = outerGain
    }
}
