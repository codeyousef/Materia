package io.materia.audio

/**
 * Global audio context management
 */
expect object AudioContext {
    val sampleRate: Float
    val state: AudioContextState

    suspend fun resume()
    suspend fun suspend()
    fun close()
}

enum class AudioContextState {
    SUSPENDED,
    RUNNING,
    CLOSED
}