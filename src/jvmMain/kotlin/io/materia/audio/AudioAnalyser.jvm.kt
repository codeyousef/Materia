package io.materia.audio

actual class AudioAnalyser actual constructor(audio: Audio, fftSize: Int) {
    actual var fftSize: Int = fftSize
    actual val frequencyBinCount: Int = fftSize / 2
    actual var smoothingTimeConstant: Float = 0.8f

    actual fun getFrequencyData(): FloatArray {
        return FloatArray(frequencyBinCount)
    }

    actual fun getByteFrequencyData(): ByteArray {
        return ByteArray(frequencyBinCount)
    }

    actual fun getByteTimeDomainData(): ByteArray {
        return ByteArray(fftSize)
    }

    actual fun getAverageFrequency(): Float {
        return 0f
    }
}