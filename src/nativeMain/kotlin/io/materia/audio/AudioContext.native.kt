package io.materia.audio

actual object AudioContext {
    private var _state = AudioContextState.SUSPENDED

    actual val sampleRate: Float = 44100f

    actual val state: AudioContextState
        get() = _state

    actual suspend fun resume() {
        _state = AudioContextState.RUNNING
    }

    actual suspend fun suspend() {
        _state = AudioContextState.SUSPENDED
    }

    actual fun close() {
        _state = AudioContextState.CLOSED
    }
}
