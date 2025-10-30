package io.materia.audio

actual object AudioContext {
    actual val sampleRate: Float = 44100f
    actual val state: AudioContextState = AudioContextState.RUNNING

    actual suspend fun resume() {
        // OpenAL context resume
    }

    actual suspend fun suspend() {
        // OpenAL context suspend
    }

    actual fun close() {
        // OpenAL context close
    }
}