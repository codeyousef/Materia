package io.materia.audio

import io.materia.core.scene.Object3D

/**
 * Base audio source class
 */
expect open class Audio(listener: AudioListener) : Object3D {
    var volume: Float
    var playbackRate: Float
    var loop: Boolean
    var autoplay: Boolean
    val isPlaying: Boolean
    val duration: Float

    fun load(url: String): Audio
    fun setBuffer(buffer: AudioBuffer): Audio
    fun play(delay: Float = 0f): Audio
    fun pause(): Audio
    fun stop(): Audio
    fun setVolume(value: Float): Audio
    fun setPlaybackRate(value: Float): Audio
    fun setLoop(value: Boolean): Audio
    fun onEnded(callback: () -> Unit)
}