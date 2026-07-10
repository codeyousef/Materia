# Audio API Reference

Materia provides shared audio source, listener, loader, and positional-audio
contracts. The browser implementation uses the Web Audio API for decoded clips,
generated cues, gain buses, and HRTF positional playback.

## Browser Setup

Browser audio must be resumed by a user gesture. Materia can install the unlock
and hidden-tab lifecycle handlers once during scene hydration.

```kotlin
BrowserAudioEngine.unlockOnFirstGesture()
BrowserAudioEngine.installVisibilityHandling()
BrowserAudioEngine.setMasterVolume(0.8f)
BrowserAudioEngine.setBusVolume("ambience", 0.25f)
BrowserAudioEngine.setBusVolume("sfx", 0.7f)
```

## Buffered Audio

`AudioLoader` fetches and decodes clips asynchronously and caches successful
results by URL.

```kotlin
val listener = AudioListener(camera)
val cue = Audio(listener).setBus("sfx")

AudioLoader().load(
    url = "/audio/confirm.ogg",
    onLoad = { cue.setBuffer(it).play() },
    onError = { message -> console.warn(message) }
)
```

Sources support volume, playback rate, looping, delayed playback, pause, stop,
and completion callbacks. A fresh native buffer source is created for every
play, as required by Web Audio.

## Procedural Audio

`ProceduralAudio` combines an oscillator, optional generated noise, an envelope,
and an optional low-pass filter. It supports one-shot cues and looping ambience
without external media files.

```kotlin
val success = ProceduralAudio(
    listener,
    ProceduralAudioSpec(
        waveform = AudioWaveform.SINE,
        startFrequencyHz = 440f,
        endFrequencyHz = 880f,
        durationSeconds = 0.24f,
        attackSeconds = 0.01f,
        releaseSeconds = 0.08f
    )
).setBus("sfx")

success.play()
```

## Positional Audio

`PositionalAudio` inserts an HRTF panner before the source gain. Its position and
forward vector are updated from the node's world transform, while
`AudioListener` tracks the active camera.

```kotlin
val conveyor = PositionalAudio(listener).apply {
    setProcedural(conveyorHum)
    setBus("ambience")
    loop = true
    refDistance = 2f
    maxDistance = 30f
    position.set(0f, 1f, -4f)
    play()
}
```
