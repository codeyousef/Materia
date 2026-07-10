package io.materia.audio

/** Oscillator waveform used by browser-generated sound effects and ambience. */
enum class AudioWaveform {
    SINE,
    SQUARE,
    SAWTOOTH,
    TRIANGLE
}

/**
 * Platform-neutral description of a short generated cue or looping ambience bed.
 * Browser playback is provided by [BrowserAudioEngine].
 */
data class ProceduralAudioSpec(
    val waveform: AudioWaveform = AudioWaveform.SINE,
    val startFrequencyHz: Float = 440f,
    val endFrequencyHz: Float = startFrequencyHz,
    val durationSeconds: Float = 0.2f,
    val attackSeconds: Float = 0.01f,
    val releaseSeconds: Float = 0.05f,
    val oscillatorGain: Float = 1f,
    val noiseGain: Float = 0f,
    val lowPassFrequencyHz: Float? = null
) {
    init {
        require(startFrequencyHz > 0f)
        require(endFrequencyHz > 0f)
        require(durationSeconds > 0f)
        require(attackSeconds >= 0f)
        require(releaseSeconds >= 0f)
        require(oscillatorGain >= 0f)
        require(noiseGain >= 0f)
        require(oscillatorGain > 0f || noiseGain > 0f)
        require(lowPassFrequencyHz == null || lowPassFrequencyHz > 0f)
    }
}
