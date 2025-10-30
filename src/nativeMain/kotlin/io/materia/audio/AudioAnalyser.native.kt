package io.materia.audio

actual class AudioAnalyser actual constructor(audio: Audio, fftSize: Int) {
    private var _fftSize = fftSize
    private var _smoothingTimeConstant = 0.8f

    actual var fftSize: Int
        get() = _fftSize
        set(value) {
            require(value >= 32 && value <= 32768) { "FFT size must be between 32 and 32768" }
            _fftSize = value
        }

    actual val frequencyBinCount: Int
        get() = _fftSize / 2

    actual var smoothingTimeConstant: Float
        get() = _smoothingTimeConstant
        set(value) {
            _smoothingTimeConstant = value.coerceIn(0f, 1f)
        }

    actual fun getFrequencyData(): FloatArray {
        return FloatArray(frequencyBinCount)
    }

    actual fun getByteFrequencyData(): ByteArray {
        return ByteArray(frequencyBinCount)
    }

    actual fun getByteTimeDomainData(): ByteArray {
        return ByteArray(_fftSize)
    }

    actual fun getAverageFrequency(): Float {
        return 0f
    }
}
