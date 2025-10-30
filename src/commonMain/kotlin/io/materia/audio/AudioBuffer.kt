package io.materia.audio

/**
 * Audio buffer containing decoded audio data
 */
expect class AudioBuffer {
    val sampleRate: Float
    val length: Int
    val duration: Float
    val numberOfChannels: Int
}