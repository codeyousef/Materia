@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package io.materia.audio

import io.materia.core.math.Quaternion
import io.materia.core.math.Vector3
import io.materia.core.scene.Object3D
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.khronos.webgl.Float32Array
import org.khronos.webgl.Uint8Array
import kotlin.math.PI

actual open class Audio actual constructor(listener: AudioListener) : Object3D() {
    protected val audioListener = listener
    private val scope = MainScope()
    private var buffer: AudioBuffer? = null
    private var proceduralSpec: ProceduralAudioSpec? = null
    private var outputGain: dynamic = null
    private var busName: String = DEFAULT_BUS
    private val activeSources = mutableListOf<dynamic>()
    private var endedCallback: (() -> Unit)? = null
    private var manuallyStopping = false
    private var playbackOffset = 0.0
    private var startedAt = 0.0

    private var _volume = 1f
    private var _playbackRate = 1f
    private var _loop = false
    private var _autoplay = false
    private var _isPlaying = false

    actual var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            ensureOutputGain()?.gain?.value = _volume
        }

    actual var playbackRate: Float
        get() = _playbackRate
        set(value) {
            _playbackRate = value.coerceAtLeast(0.01f)
            activeSources.forEach { source ->
                if (source.playbackRate != undefined) source.playbackRate.value = _playbackRate
            }
        }

    actual var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
            activeSources.forEach { source ->
                if (source.loop != undefined) source.loop = value
            }
        }

    actual var autoplay: Boolean
        get() = _autoplay
        set(value) {
            _autoplay = value
        }

    actual val isPlaying: Boolean
        get() = _isPlaying

    actual val duration: Float
        get() = buffer?.duration ?: proceduralSpec?.durationSeconds ?: 0f

    actual fun load(url: String): Audio {
        scope.launch {
            AudioLoader().loadAsync(url)
                .onSuccess { loaded ->
                    setBuffer(loaded)
                    if (autoplay) play()
                }
        }
        return this
    }

    actual fun setBuffer(buffer: AudioBuffer): Audio {
        stop()
        this.buffer = buffer
        proceduralSpec = null
        return this
    }

    /** Use a generated cue instead of a decoded buffer. Available on browser targets. */
    fun setProcedural(spec: ProceduralAudioSpec): Audio {
        stop()
        proceduralSpec = spec
        buffer = null
        return this
    }

    /** Route this source through a named gain bus such as `sfx` or `ambience`. */
    fun setBus(name: String): Audio {
        require(name.isNotBlank())
        busName = name
        reconnectOutput()
        return this
    }

    actual fun play(delay: Float): Audio {
        stopActiveSources(resetOffset = false)
        val context = BrowserAudioEngine.contextOrNull()
        val destination = sourceDestination()
        _isPlaying = true
        if (context == null || destination == null) return this

        val safeDelay = delay.coerceAtLeast(0f)
        startedAt = (context.currentTime as Number).toDouble() + safeDelay
        val loadedBuffer = buffer
        val generated = proceduralSpec

        when {
            loadedBuffer?.nativeBuffer != null -> {
                val source = context.createBufferSource()
                source.buffer = loadedBuffer.nativeBuffer
                source.loop = loop
                source.playbackRate.value = playbackRate
                source.connect(destination)
                source.onended = { handleNaturalEnd() }
                source.start(startedAt, playbackOffset.coerceAtMost(duration.toDouble()))
                activeSources.add(source)
            }

            generated != null -> {
                activeSources.addAll(BrowserAudioEngine.startProcedural(
                    spec = generated,
                    destination = destination,
                    loop = loop,
                    delaySeconds = safeDelay,
                    onEnded = ::handleNaturalEnd
                ))
            }
        }
        return this
    }

    actual fun pause(): Audio {
        if (_isPlaying && duration > 0f && !loop) {
            val context = BrowserAudioEngine.contextOrNull()
            if (context != null) {
                val elapsed = ((context.currentTime as Number).toDouble() - startedAt)
                    .coerceAtLeast(0.0) * playbackRate
                playbackOffset = (playbackOffset + elapsed).coerceAtMost(duration.toDouble())
            }
        }
        stopActiveSources(resetOffset = false)
        _isPlaying = false
        return this
    }

    actual fun stop(): Audio {
        stopActiveSources(resetOffset = true)
        _isPlaying = false
        return this
    }

    actual fun setVolume(value: Float): Audio {
        volume = value
        return this
    }

    actual fun setPlaybackRate(value: Float): Audio {
        playbackRate = value
        return this
    }

    actual fun setLoop(value: Boolean): Audio {
        loop = value
        return this
    }

    actual fun onEnded(callback: () -> Unit) {
        endedCallback = callback
    }

    internal fun attachAnalyser(analyser: dynamic) {
        val gain = ensureOutputGain() ?: return
        try {
            gain.connect(analyser)
            analyser.connect(BrowserAudioEngine.bus(busName))
        } catch (_: dynamic) {
        }
    }

    protected open fun sourceDestination(): dynamic = ensureOutputGain()

    protected fun ensureOutputGain(): dynamic {
        if (outputGain != null && outputGain != undefined) return outputGain
        val context = BrowserAudioEngine.contextOrNull() ?: return null
        outputGain = context.createGain()
        outputGain.gain.value = volume
        outputGain.connect(BrowserAudioEngine.bus(busName))
        return outputGain
    }

    private fun reconnectOutput() {
        val gain = ensureOutputGain() ?: return
        try {
            gain.disconnect()
        } catch (_: dynamic) {
        }
        gain.connect(BrowserAudioEngine.bus(busName))
    }

    private fun stopActiveSources(resetOffset: Boolean) {
        manuallyStopping = true
        activeSources.forEach { source ->
            try {
                source.stop()
            } catch (_: dynamic) {
            }
            try {
                source.disconnect()
            } catch (_: dynamic) {
            }
        }
        activeSources.clear()
        manuallyStopping = false
        if (resetOffset) playbackOffset = 0.0
    }

    private fun handleNaturalEnd() {
        if (manuallyStopping || loop) return
        activeSources.clear()
        playbackOffset = 0.0
        _isPlaying = false
        endedCallback?.invoke()
    }

    private companion object {
        const val DEFAULT_BUS = "master"
    }
}

/** Browser-generated audio source with the normal Materia playback controls. */
class ProceduralAudio(listener: AudioListener, spec: ProceduralAudioSpec) : Audio(listener) {
    init {
        setProcedural(spec)
    }
}

actual class PositionalAudio actual constructor(listener: AudioListener) : Audio(listener) {
    private var panner: dynamic = null

    actual var refDistance: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0.001f)
            ensurePanner()?.refDistance = field
        }
    actual var maxDistance: Float = 10000f
        set(value) {
            field = value.coerceAtLeast(refDistance)
            ensurePanner()?.maxDistance = field
        }
    actual var rolloffFactor: Float = 1f
        set(value) {
            field = value.coerceAtLeast(0f)
            ensurePanner()?.rolloffFactor = field
        }
    actual var distanceModel: DistanceModel = DistanceModel.INVERSE
        set(value) {
            field = value
            ensurePanner()?.distanceModel = value.serialName
        }

    actual var coneInnerAngle: Float = (2f * PI).toFloat()
        set(value) {
            field = value.coerceAtLeast(0f)
            ensurePanner()?.coneInnerAngle = field.radiansToDegrees()
        }
    actual var coneOuterAngle: Float = (2f * PI).toFloat()
        set(value) {
            field = value.coerceAtLeast(coneInnerAngle)
            ensurePanner()?.coneOuterAngle = field.radiansToDegrees()
        }
    actual var coneOuterGain: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            ensurePanner()?.coneOuterGain = field
        }

    actual fun setDirectionalCone(innerAngle: Float, outerAngle: Float, outerGain: Float) {
        coneInnerAngle = innerAngle
        coneOuterAngle = outerAngle
        coneOuterGain = outerGain
    }

    override fun updateMatrixWorld(force: Boolean) {
        super.updateMatrixWorld(force)
        val node = ensurePanner() ?: return
        val context = BrowserAudioEngine.contextOrNull() ?: return
        val now = (context.currentTime as Number).toDouble()
        val position = getWorldPosition(Vector3())
        val direction = Vector3(0f, 0f, -1f).applyQuaternion(getWorldQuaternion(Quaternion()))
        setAudioParam(node.positionX, position.x, now)
        setAudioParam(node.positionY, position.y, now)
        setAudioParam(node.positionZ, position.z, now)
        setAudioParam(node.orientationX, direction.x, now)
        setAudioParam(node.orientationY, direction.y, now)
        setAudioParam(node.orientationZ, direction.z, now)
    }

    override fun sourceDestination(): dynamic = ensurePanner()

    private fun ensurePanner(): dynamic {
        if (panner != null && panner != undefined) return panner
        val context = BrowserAudioEngine.contextOrNull() ?: return null
        panner = context.createPanner()
        panner.panningModel = "HRTF"
        panner.distanceModel = distanceModel.serialName
        panner.refDistance = refDistance
        panner.maxDistance = maxDistance
        panner.rolloffFactor = rolloffFactor
        panner.coneInnerAngle = coneInnerAngle.radiansToDegrees()
        panner.coneOuterAngle = coneOuterAngle.radiansToDegrees()
        panner.coneOuterGain = coneOuterGain
        panner.connect(ensureOutputGain())
        return panner
    }
}

actual class AudioBuffer internal constructor(internal val nativeBuffer: dynamic = null) {
    actual val sampleRate: Float
        get() = (nativeBuffer?.sampleRate as? Number)?.toFloat() ?: 44100f
    actual val length: Int
        get() = (nativeBuffer?.length as? Number)?.toInt() ?: 0
    actual val duration: Float
        get() = (nativeBuffer?.duration as? Number)?.toFloat() ?: 0f
    actual val numberOfChannels: Int
        get() = (nativeBuffer?.numberOfChannels as? Number)?.toInt() ?: 0
}

actual class AudioLoader {
    private val scope = MainScope()

    actual fun load(
        url: String,
        onLoad: (AudioBuffer) -> Unit,
        onProgress: ((Float) -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        onProgress?.invoke(0f)
        scope.launch {
            loadAsync(url)
                .onSuccess {
                    onProgress?.invoke(1f)
                    onLoad(it)
                }
                .onFailure { onError?.invoke(it.message ?: "Audio loading failed") }
        }
    }

    actual suspend fun loadAsync(url: String): Result<AudioBuffer> = runCatching {
        BrowserAudioEngine.loadBuffer(url)
    }
}

actual class AudioAnalyser actual constructor(audio: Audio, fftSize: Int) {
    private val analyser: dynamic = BrowserAudioEngine.contextOrNull()?.createAnalyser()

    actual var fftSize: Int = fftSize
        set(value) {
            field = value.coerceIn(32, 32768)
            if (analyser != null) analyser.fftSize = field
        }

    actual val frequencyBinCount: Int
        get() = (analyser?.frequencyBinCount as? Number)?.toInt() ?: fftSize / 2

    actual var smoothingTimeConstant: Float = 0.8f
        set(value) {
            field = value.coerceIn(0f, 1f)
            if (analyser != null) analyser.smoothingTimeConstant = field
        }

    init {
        this.fftSize = fftSize
        audio.attachAnalyser(analyser)
    }

    actual fun getFrequencyData(): FloatArray {
        if (analyser == null) return FloatArray(frequencyBinCount)
        val values = Float32Array(frequencyBinCount)
        analyser.getFloatFrequencyData(values)
        return FloatArray(frequencyBinCount) { index ->
            (values.asDynamic()[index] as Number).toFloat()
        }
    }

    actual fun getByteFrequencyData(): ByteArray {
        if (analyser == null) return ByteArray(frequencyBinCount)
        val values = Uint8Array(frequencyBinCount)
        analyser.getByteFrequencyData(values)
        return ByteArray(frequencyBinCount) { index ->
            (values.asDynamic()[index] as Number).toByte()
        }
    }

    actual fun getByteTimeDomainData(): ByteArray {
        if (analyser == null) return ByteArray(fftSize)
        val values = Uint8Array(fftSize)
        analyser.getByteTimeDomainData(values)
        return ByteArray(fftSize) { index ->
            (values.asDynamic()[index] as Number).toByte()
        }
    }

    actual fun getAverageFrequency(): Float {
        val values = getByteFrequencyData()
        if (values.isEmpty()) return 0f
        return values.sumOf { it.toUByte().toInt() }.toFloat() / values.size
    }
}

actual object AudioContext {
    actual val sampleRate: Float
        get() = BrowserAudioEngine.sampleRate
    actual val state: AudioContextState
        get() = BrowserAudioEngine.state

    actual suspend fun resume() = BrowserAudioEngine.resume()
    actual suspend fun suspend() = BrowserAudioEngine.suspend()
    actual fun close() = BrowserAudioEngine.close()
}

private val DistanceModel.serialName: String
    get() = when (this) {
        DistanceModel.LINEAR -> "linear"
        DistanceModel.INVERSE -> "inverse"
        DistanceModel.EXPONENTIAL -> "exponential"
    }

private fun Float.radiansToDegrees(): Float = this * 180f / PI.toFloat()

private fun setAudioParam(parameter: dynamic, value: Float, time: Double) {
    if (parameter == null || parameter == undefined) return
    if (parameter.setValueAtTime != undefined) {
        parameter.setValueAtTime(value, time)
    } else {
        parameter.value = value
    }
}
