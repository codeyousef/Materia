package io.materia.audio

import io.materia.camera.PerspectiveCamera
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Float32Array
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserAudioEngineTest {
    private lateinit var state: FakeAudioState

    @BeforeTest
    fun setUp() {
        BrowserAudioEngine.resetForTesting()
        state = FakeAudioState()
        BrowserAudioEngine.contextFactoryForTesting = { fakeContext(state) }
    }

    @AfterTest
    fun tearDown() {
        BrowserAudioEngine.resetForTesting()
    }

    @Test
    fun bufferedAudioUsesRealNodeLifecycleAndBus() {
        val audio = Audio(AudioListener(PerspectiveCamera()))
            .setBuffer(AudioBuffer(fakeNativeBuffer(duration = 2.0)))
            .setBus("sfx")
            .setVolume(0.4f)

        audio.play()

        assertTrue(audio.isPlaying)
        assertEquals(1, state.bufferStarts)
        assertEquals(0.4f, audio.volume)
        BrowserAudioEngine.setBusVolume("sfx", 0.35f)
        assertEquals(0.35f, BrowserAudioEngine.getBusVolume("sfx"))

        audio.pause()
        assertFalse(audio.isPlaying)
        audio.play()
        assertEquals(2, state.bufferStarts)
        audio.stop()
        assertFalse(audio.isPlaying)
    }

    @Test
    fun proceduralAudioBuildsOscillatorAndNoiseGraph() {
        val audio = ProceduralAudio(
            AudioListener(PerspectiveCamera()),
            ProceduralAudioSpec(
                waveform = AudioWaveform.SQUARE,
                startFrequencyHz = 220f,
                endFrequencyHz = 440f,
                durationSeconds = 0.3f,
                noiseGain = 0.2f
            )
        )

        audio.play()

        assertEquals(1, state.oscillatorStarts)
        assertEquals(1, state.bufferStarts)
        assertTrue(audio.isPlaying)
    }

    @Test
    fun decodedBuffersAreCachedByUrl() = runTest {
        var loads = 0
        BrowserAudioEngine.bufferLoaderForTesting = {
            loads++
            AudioBuffer(fakeNativeBuffer(duration = 1.0))
        }

        val first = AudioLoader().loadAsync("/sound/cue.ogg")
        val second = AudioLoader().loadAsync("/sound/cue.ogg")

        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertEquals(1, loads)
    }

    @Test
    fun contextResumeAndSuspendReflectNativeState() = runTest {
        assertEquals(AudioContextState.SUSPENDED, AudioContext.state)
        AudioContext.resume()
        assertEquals(AudioContextState.RUNNING, AudioContext.state)
        AudioContext.suspend()
        assertEquals(AudioContextState.SUSPENDED, AudioContext.state)
    }

    @Test
    fun analyserReadsTypedFrequencyAndTimeDomainBuffers() {
        val audio = Audio(AudioListener(PerspectiveCamera()))
        val analyser = AudioAnalyser(audio, fftSize = 32)

        assertEquals(16, analyser.getFrequencyData().size)
        assertEquals(16, analyser.getByteFrequencyData().size)
        assertEquals(32, analyser.getByteTimeDomainData().size)
        assertEquals(0f, analyser.getAverageFrequency())
    }

    private fun fakeContext(state: FakeAudioState): dynamic {
        val context = js("({})")
        context.state = "suspended"
        context.sampleRate = 48000
        context.currentTime = 1.0
        context.destination = js("({})")
        context.listener = fakeListener()
        context.resume = {
            context.state = "running"
            Promise.resolve(Unit)
        }
        context.suspend = {
            context.state = "suspended"
            Promise.resolve(Unit)
        }
        context.close = {
            context.state = "closed"
            Promise.resolve(Unit)
        }
        context.createGain = { fakeGain() }
        context.createBiquadFilter = {
            val filter = fakeConnectable()
            filter.frequency = fakeParam(0.0)
            filter
        }
        context.createBufferSource = { fakeSource(state, oscillator = false) }
        context.createOscillator = { fakeSource(state, oscillator = true) }
        context.createPanner = {
            val panner = fakeConnectable()
            panner.positionX = fakeParam(0.0)
            panner.positionY = fakeParam(0.0)
            panner.positionZ = fakeParam(0.0)
            panner.orientationX = fakeParam(0.0)
            panner.orientationY = fakeParam(0.0)
            panner.orientationZ = fakeParam(-1.0)
            panner
        }
        context.createAnalyser = {
            val analyser = fakeConnectable()
            analyser.frequencyBinCount = 16
            analyser.fftSize = 32
            analyser.getFloatFrequencyData = { _: dynamic -> }
            analyser.getByteFrequencyData = { _: dynamic -> }
            analyser.getByteTimeDomainData = { _: dynamic -> }
            analyser
        }
        context.createBuffer = { channels: Int, length: Int, sampleRate: Int ->
            val buffer = fakeNativeBuffer(length.toDouble() / sampleRate)
            buffer.numberOfChannels = channels
            buffer.length = length
            buffer.sampleRate = sampleRate
            buffer.getChannelData = { _: Int -> Float32Array(length) }
            buffer
        }
        return context
    }

    private fun fakeSource(state: FakeAudioState, oscillator: Boolean): dynamic {
        val source = fakeConnectable()
        source.playbackRate = fakeParam(1.0)
        source.frequency = fakeParam(440.0)
        source.loop = false
        source.onended = null
        source.start = { _: Double, _: dynamic ->
            if (oscillator) state.oscillatorStarts++ else state.bufferStarts++
        }
        source.stop = { _: dynamic -> source.onended?.invoke() }
        return source
    }

    private fun fakeGain(): dynamic {
        val gain = fakeConnectable()
        gain.gain = fakeParam(1.0)
        return gain
    }

    private fun fakeConnectable(): dynamic {
        val node = js("({})")
        node.connect = { _: dynamic -> Unit }
        node.disconnect = { Unit }
        return node
    }

    private fun fakeParam(initial: Double): dynamic {
        val parameter = js("({})")
        parameter.value = initial
        parameter.setValueAtTime = { value: Double, _: Double -> parameter.value = value }
        parameter.linearRampToValueAtTime = { value: Double, _: Double -> parameter.value = value }
        return parameter
    }

    private fun fakeListener(): dynamic {
        val listener = js("({})")
        listener.positionX = fakeParam(0.0)
        listener.positionY = fakeParam(0.0)
        listener.positionZ = fakeParam(0.0)
        listener.forwardX = fakeParam(0.0)
        listener.forwardY = fakeParam(0.0)
        listener.forwardZ = fakeParam(-1.0)
        listener.upX = fakeParam(0.0)
        listener.upY = fakeParam(1.0)
        listener.upZ = fakeParam(0.0)
        return listener
    }

    private fun fakeNativeBuffer(duration: Double): dynamic {
        val buffer = js("({})")
        buffer.sampleRate = 48000
        buffer.length = (duration * 48000).toInt()
        buffer.duration = duration
        buffer.numberOfChannels = 2
        return buffer
    }

    private class FakeAudioState {
        var bufferStarts: Int = 0
        var oscillatorStarts: Int = 0
    }
}
