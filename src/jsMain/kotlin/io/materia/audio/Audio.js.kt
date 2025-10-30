package io.materia.audio

import io.materia.core.scene.Object3D

actual open class Audio actual constructor(listener: AudioListener) : Object3D() {
    protected val _listener = listener
    private var _volume = 1f
    private var _playbackRate = 1f
    private var _loop = false
    private var _autoplay = false
    private var _isPlaying = false
    private var _duration = 0f
    private var endedCallback: (() -> Unit)? = null

    actual var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
        }

    actual var playbackRate: Float
        get() = _playbackRate
        set(value) {
            _playbackRate = value.coerceAtLeast(0f)
        }

    actual var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
        }

    actual var autoplay: Boolean
        get() = _autoplay
        set(value) {
            _autoplay = value
        }

    actual val isPlaying: Boolean
        get() = _isPlaying

    actual val duration: Float
        get() = _duration

    actual fun load(url: String): Audio {
        // Web Audio API implementation
        return this
    }

    actual fun setBuffer(buffer: AudioBuffer): Audio {
        return this
    }

    actual fun play(delay: Float): Audio {
        _isPlaying = true
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

actual class PositionalAudio actual constructor(listener: AudioListener) : Audio(listener) {
    actual var refDistance: Float = 1f
    actual var maxDistance: Float = 10000f
    actual var rolloffFactor: Float = 1f
    actual var distanceModel: DistanceModel = DistanceModel.INVERSE
    actual var coneInnerAngle: Float = (2 * kotlin.math.PI).toFloat()
    actual var coneOuterAngle: Float = (2 * kotlin.math.PI).toFloat()
    actual var coneOuterGain: Float = 0f

    actual fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float) {
        coneInnerAngle = innerAngle
        coneOuterAngle = outerAngle
        coneOuterGain = outerGain
    }
}

actual class AudioBuffer {
    actual val sampleRate: Float = 44100f
    actual val length: Int = 0
    actual val duration: Float = 0f
    actual val numberOfChannels: Int = 2
}

actual class AudioLoader {
    actual fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            val buffer = AudioBuffer()
            onLoad(buffer)
        } catch (e: Throwable) {
            onError?.invoke(e.message ?: "Audio loading failed")
        }
    }

    actual suspend fun loadAsync(url: String): Result<AudioBuffer> {
        return try {
            Result.success(AudioBuffer())
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}

actual class AudioAnalyser actual constructor(audio: Audio, fftSize: Int) {
    actual var fftSize: Int = fftSize
    actual val frequencyBinCount: Int = fftSize / 2
    actual var smoothingTimeConstant: Float = 0.8f

    actual fun getFrequencyData(): FloatArray = FloatArray(frequencyBinCount)
    actual fun getByteFrequencyData(): ByteArray = ByteArray(frequencyBinCount)
    actual fun getByteTimeDomainData(): ByteArray = ByteArray(fftSize)
    actual fun getAverageFrequency(): Float = 0f
}

actual object AudioContext {
    actual val sampleRate: Float = 44100f
    actual val state: AudioContextState = AudioContextState.RUNNING

    actual suspend fun resume() {}
    actual suspend fun suspend() {}
    actual fun close() {}
}