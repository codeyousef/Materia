package io.materia.audio

actual class PositionalAudio actual constructor(listener: AudioListener) : Audio(listener) {
    actual var refDistance: Float = 1f
    actual var maxDistance: Float = 10000f
    actual var rolloffFactor: Float = 1f
    actual var distanceModel: DistanceModel = DistanceModel.INVERSE

    actual var coneInnerAngle: Float = (2 * Math.PI).toFloat()
    actual var coneOuterAngle: Float = (2 * Math.PI).toFloat()
    actual var coneOuterGain: Float = 0f

    actual fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float) {
        coneInnerAngle = innerAngle
        coneOuterAngle = outerAngle
        coneOuterGain = outerGain
    }
}