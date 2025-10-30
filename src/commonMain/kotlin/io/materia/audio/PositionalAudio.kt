package io.materia.audio

/**
 * Positional audio source with 3D panning and distance attenuation
 */
expect class PositionalAudio(listener: AudioListener) : Audio {
    var refDistance: Float
    var maxDistance: Float
    var rolloffFactor: Float
    var distanceModel: DistanceModel

    var coneInnerAngle: Float
    var coneOuterAngle: Float
    var coneOuterGain: Float

    fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float)
}

enum class DistanceModel {
    LINEAR,
    INVERSE,
    EXPONENTIAL
}