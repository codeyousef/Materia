package io.materia.audio

/**
 * Audio frequency analyzer for visualization
 */
expect class AudioAnalyser(audio: Audio, fftSize: Int = 2048) {
    var fftSize: Int
    val frequencyBinCount: Int
    var smoothingTimeConstant: Float

    fun getFrequencyData(): FloatArray
    fun getByteFrequencyData(): ByteArray
    fun getByteTimeDomainData(): ByteArray
    fun getAverageFrequency(): Float
}