@file:Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")

package io.materia.audio

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.khronos.webgl.ArrayBuffer
import org.w3c.dom.events.Event
import kotlin.js.Promise
import kotlin.math.max
import kotlin.random.Random

/** Real browser audio runtime shared by buffered and procedural Materia sources. */
object BrowserAudioEngine {
    private val scope = MainScope()
    private var nativeContext: dynamic = null
    private var masterGain: dynamic = null
    private val buses = mutableMapOf<String, dynamic>()
    private val busVolumes = mutableMapOf<String, Float>()
    private val decodedBuffers = mutableMapOf<String, AudioBuffer>()
    private var unlockInstalled = false
    private var visibilityInstalled = false
    private var resumeAfterVisibility = false

    internal var contextFactoryForTesting: (() -> dynamic)? = null
    internal var bufferLoaderForTesting: (suspend (String) -> AudioBuffer)? = null

    val state: AudioContextState
        get() = when (contextOrNull()?.state as? String) {
            "running" -> AudioContextState.RUNNING
            "closed" -> AudioContextState.CLOSED
            else -> AudioContextState.SUSPENDED
        }

    val sampleRate: Float
        get() = (contextOrNull()?.sampleRate as? Number)?.toFloat() ?: 44100f

    fun contextOrNull(): dynamic {
        if (nativeContext != null && nativeContext != undefined) return nativeContext
        nativeContext = contextFactoryForTesting?.invoke() ?: createNativeContext()
        if (nativeContext == null || nativeContext == undefined) return null

        masterGain = nativeContext.createGain()
        masterGain.gain.value = 1.0
        masterGain.connect(nativeContext.destination)
        return nativeContext
    }

    suspend fun resume() {
        val context = contextOrNull() ?: return
        awaitIfPromise(context.resume())
    }

    suspend fun suspend() {
        val context = contextOrNull() ?: return
        awaitIfPromise(context.suspend())
    }

    fun close() {
        val context = nativeContext
        if (context != null && context != undefined) {
            context.close()
        }
        nativeContext = null
        masterGain = null
        buses.clear()
    }

    fun setMasterVolume(value: Float) {
        contextOrNull() ?: return
        masterGain.gain.value = value.coerceIn(0f, 1f)
    }

    fun setBusVolume(name: String, value: Float) {
        require(name.isNotBlank())
        val safeValue = value.coerceIn(0f, 1f)
        busVolumes[name] = safeValue
        bus(name)?.gain?.value = safeValue
    }

    fun getBusVolume(name: String): Float = busVolumes[name] ?: 1f

    internal fun bus(name: String): dynamic {
        val context = contextOrNull() ?: return null
        val existing = buses[name]
        if (existing != null) return existing
        val gain = context.createGain()
        gain.gain.value = getBusVolume(name)
        gain.connect(masterGain)
        buses[name] = gain
        return gain
    }

    fun unlockOnFirstGesture() {
        if (unlockInstalled) return
        val document = js("globalThis.document")
        if (document == null || document == undefined) return
        unlockInstalled = true

        val handler: (Event) -> Unit = {
            scope.launch { resume() }
        }
        val options = js("({ once: true, passive: true })")
        document.addEventListener("pointerdown", handler, options)
        document.addEventListener("touchstart", handler, options)
        document.addEventListener("keydown", handler, options)
    }

    fun installVisibilityHandling() {
        if (visibilityInstalled) return
        val document = js("globalThis.document")
        if (document == null || document == undefined) return
        visibilityInstalled = true

        val handler: (Event) -> Unit = {
            val hidden = document.hidden as? Boolean ?: false
            if (hidden) {
                resumeAfterVisibility = state == AudioContextState.RUNNING
                if (resumeAfterVisibility) scope.launch { suspend() }
            } else if (resumeAfterVisibility) {
                resumeAfterVisibility = false
                scope.launch { resume() }
            }
        }
        document.addEventListener("visibilitychange", handler)
    }

    internal suspend fun loadBuffer(url: String): AudioBuffer {
        require(url.isNotBlank())
        decodedBuffers[url]?.let { return it }
        val loaded = bufferLoaderForTesting?.invoke(url) ?: loadNativeBuffer(url)
        decodedBuffers[url] = loaded
        return loaded
    }

    internal fun startProcedural(
        spec: ProceduralAudioSpec,
        destination: dynamic,
        loop: Boolean,
        delaySeconds: Float,
        onEnded: () -> Unit
    ): List<dynamic> {
        val context = contextOrNull() ?: return emptyList()
        val startAt = (context.currentTime as Number).toDouble() + delaySeconds.coerceAtLeast(0f)
        val duration = spec.durationSeconds.toDouble()
        val endAt = startAt + duration
        val cueGain = context.createGain()
        configureEnvelope(cueGain.gain, startAt, endAt, spec, loop)

        val input = if (spec.lowPassFrequencyHz != null) {
            val filter = context.createBiquadFilter()
            filter.type = "lowpass"
            filter.frequency.value = spec.lowPassFrequencyHz
            filter.connect(cueGain)
            filter
        } else {
            cueGain
        }
        cueGain.connect(destination)

        val sources = mutableListOf<dynamic>()
        if (spec.oscillatorGain > 0f) {
            val oscillator = context.createOscillator()
            oscillator.type = spec.waveform.serialName
            oscillator.frequency.setValueAtTime(spec.startFrequencyHz, startAt)
            oscillator.frequency.linearRampToValueAtTime(spec.endFrequencyHz, endAt)
            val gain = context.createGain()
            gain.gain.value = spec.oscillatorGain
            oscillator.connect(gain)
            gain.connect(input)
            oscillator.start(startAt)
            if (!loop) oscillator.stop(endAt)
            sources.add(oscillator)
        }

        if (spec.noiseGain > 0f) {
            val noise = context.createBufferSource()
            noise.buffer = createNoiseBuffer(context, spec.durationSeconds)
            noise.loop = loop
            val gain = context.createGain()
            gain.gain.value = spec.noiseGain
            noise.connect(gain)
            gain.connect(input)
            noise.start(startAt)
            if (!loop) noise.stop(endAt)
            sources.add(noise)
        }

        sources.firstOrNull()?.onended = {
            if (!loop) onEnded()
        }
        return sources
    }

    internal fun resetForTesting() {
        nativeContext = null
        masterGain = null
        buses.clear()
        busVolumes.clear()
        decodedBuffers.clear()
        unlockInstalled = false
        visibilityInstalled = false
        resumeAfterVisibility = false
        contextFactoryForTesting = null
        bufferLoaderForTesting = null
    }

    private suspend fun loadNativeBuffer(url: String): AudioBuffer {
        val context = contextOrNull()
            ?: throw IllegalStateException("Web Audio API is unavailable")
        val fetch = js("globalThis.fetch") as? (String) -> Promise<dynamic>
            ?: throw IllegalStateException("globalThis.fetch is unavailable")
        val response = fetch(url).await()
        if (response.ok != true) {
            throw IllegalStateException("Audio request failed for $url (${response.status})")
        }
        val arrayBuffer = (response.arrayBuffer() as Promise<ArrayBuffer>).await()
        val decoded = (context.decodeAudioData(arrayBuffer) as Promise<dynamic>).await()
        return AudioBuffer(decoded)
    }

    private fun createNativeContext(): dynamic {
        return try {
            js("new (globalThis.AudioContext || globalThis.webkitAudioContext)()")
        } catch (_: dynamic) {
            null
        }
    }

    private suspend fun awaitIfPromise(value: dynamic) {
        if (value != null && value != undefined && value.then != undefined) {
            (value as Promise<dynamic>).await()
        }
    }

    private fun configureEnvelope(
        parameter: dynamic,
        startAt: Double,
        endAt: Double,
        spec: ProceduralAudioSpec,
        loop: Boolean
    ) {
        val attackEnd = (startAt + spec.attackSeconds).coerceAtMost(endAt)
        parameter.setValueAtTime(if (spec.attackSeconds > 0f) 0.0 else 1.0, startAt)
        parameter.linearRampToValueAtTime(1.0, attackEnd)
        if (!loop) {
            val releaseStart = max(attackEnd, endAt - spec.releaseSeconds)
            parameter.setValueAtTime(1.0, releaseStart)
            parameter.linearRampToValueAtTime(0.0, endAt)
        }
    }

    private fun createNoiseBuffer(context: dynamic, durationSeconds: Float): dynamic {
        val sampleRate = (context.sampleRate as Number).toInt()
        val length = max(1, (sampleRate * durationSeconds).toInt())
        val buffer = context.createBuffer(1, length, sampleRate)
        val data = buffer.getChannelData(0)
        for (index in 0 until length) {
            data[index] = Random.nextFloat() * 2f - 1f
        }
        return buffer
    }
}

private val AudioWaveform.serialName: String
    get() = when (this) {
        AudioWaveform.SINE -> "sine"
        AudioWaveform.SQUARE -> "square"
        AudioWaveform.SAWTOOTH -> "sawtooth"
        AudioWaveform.TRIANGLE -> "triangle"
    }
