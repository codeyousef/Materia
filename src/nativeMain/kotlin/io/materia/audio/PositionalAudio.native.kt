package io.materia.audio

actual class PositionalAudio actual constructor(listener: AudioListener) : Audio(listener) {
    private var _refDistance = 1f
    private var _maxDistance = 10000f
    private var _rolloffFactor = 1f
    private var _distanceModel = DistanceModel.INVERSE

    private var _coneInnerAngle = 360f
    private var _coneOuterAngle = 360f
    private var _coneOuterGain = 0f

    actual var refDistance: Float
        get() = _refDistance
        set(value) {
            _refDistance = value.coerceAtLeast(0f)
        }

    actual var maxDistance: Float
        get() = _maxDistance
        set(value) {
            _maxDistance = value.coerceAtLeast(0f)
        }

    actual var rolloffFactor: Float
        get() = _rolloffFactor
        set(value) {
            _rolloffFactor = value.coerceAtLeast(0f)
        }

    actual var distanceModel: DistanceModel
        get() = _distanceModel
        set(value) {
            _distanceModel = value
        }

    actual var coneInnerAngle: Float
        get() = _coneInnerAngle
        set(value) {
            _coneInnerAngle = value.coerceIn(0f, 360f)
        }

    actual var coneOuterAngle: Float
        get() = _coneOuterAngle
        set(value) {
            _coneOuterAngle = value.coerceIn(0f, 360f)
        }

    actual var coneOuterGain: Float
        get() = _coneOuterGain
        set(value) {
            _coneOuterGain = value.coerceIn(0f, 1f)
        }

    actual fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float) {
        coneInnerAngle = innerAngle
        coneOuterAngle = outerAngle
        coneOuterGain = outerGain
    }
}
