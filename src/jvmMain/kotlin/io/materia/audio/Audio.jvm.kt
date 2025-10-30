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
        // OpenAL implementation would go here
        return this
    }

    actual fun setBuffer(buffer: AudioBuffer): Audio {
        // Set audio buffer
        return this
    }

    actual fun play(delay: Float): Audio {
        _isPlaying = true
        // OpenAL play implementation
        return this
    }

    actual fun pause(): Audio {
        _isPlaying = false
        // OpenAL pause implementation
        return this
    }

    actual fun stop(): Audio {
        _isPlaying = false
        // OpenAL stop implementation
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