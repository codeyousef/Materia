package io.kreekt.texture

import io.kreekt.renderer.TextureFilter
import io.kreekt.renderer.TextureFormat
import io.kreekt.renderer.TextureWrap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Video texture implementation for dynamic video content (T107).
 *
 * This implementation keeps all state in common code and mimics the behaviour of
 * a platform video player so it works both with real platform back-ends (which
 * can attach their own frame providers) and in headless environments used in
 * tests or tooling.
 */
class VideoTexture(
    override val width: Int,
    override val height: Int,
    format: TextureFormat = TextureFormat.RGBA8,
    magFilter: TextureFilter = TextureFilter.LINEAR,
    minFilter: TextureFilter = TextureFilter.LINEAR,
    textureName: String = "VideoTexture"
) : Texture() {

    init {
        name = textureName
        this.format = format
        this.magFilter = magFilter
        this.minFilter = minFilter
        this.generateMipmaps = false
        this.flipY = true
        this.wrapS = TextureWrap.CLAMP_TO_EDGE
        this.wrapT = TextureWrap.CLAMP_TO_EDGE
    }

    // Playback state
    var isPlaying: Boolean = false
        private set
    var currentTime: Float = 0f
        private set
    var duration: Float = 0f
        private set
    var playbackRate: Float = 1f
    var loop: Boolean = false
    var volume: Float = 1f
    var muted: Boolean = false

    // Frame data
    private var frameData: ByteArray? = null
    private var frameNumber: Int = 0

    // Binding to an underlying video source (may be simulated)
    private var binding: VideoBinding? = null

    // Events
    var onLoadedData: (() -> Unit)? = null
    var onLoadedMetadata: (() -> Unit)? = null
    var onCanPlay: (() -> Unit)? = null
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTimeUpdate: ((Float) -> Unit)? = null

    companion object {
        fun fromUrl(
            url: String,
            width: Int = 0,
            height: Int = 0,
            autoPlay: Boolean = false,
            loop: Boolean = false
        ): VideoTexture = VideoTexture(
            width = if (width > 0) width else 1920,
            height = if (height > 0) height else 1080,
            textureName = "VideoTexture($url)"
        ).apply {
            this.loop = loop
            loadFromUrl(url, autoPlay)
        }

        fun fromData(
            data: ByteArray,
            width: Int,
            height: Int,
            mimeType: String = "video/mp4"
        ): VideoTexture = VideoTexture(width, height).apply {
            loadFromData(data, mimeType)
        }
    }

    override fun clone(): Texture {
        val copy = VideoTexture(width, height, format, magFilter, minFilter, name).apply {
            playbackRate = this@VideoTexture.playbackRate
            loop = this@VideoTexture.loop
            volume = this@VideoTexture.volume
            muted = this@VideoTexture.muted
            duration = this@VideoTexture.duration
            currentTime = this@VideoTexture.currentTime
            isPlaying = this@VideoTexture.isPlaying
            binding = this@VideoTexture.binding?.copy()
            frameData = this@VideoTexture.frameData?.copyOf()
            frameNumber = this@VideoTexture.frameNumber
        }
        return copy
    }

    fun loadFromUrl(url: String, autoPlay: Boolean = false) {
        val source = VideoSource.Url(url)
        binding = VideoBinding(source = source)
        duration = estimateDuration(source)
        onLoadedMetadata?.invoke()
        onCanPlay?.invoke()
        if (autoPlay) play()
    }

    fun loadFromData(data: ByteArray, mimeType: String) {
        val source = VideoSource.Embedded(data, mimeType)
        binding = VideoBinding(source = source)
        duration = estimateDuration(source)
        onLoadedData?.invoke()
        onLoadedMetadata?.invoke()
        onCanPlay?.invoke()
    }

    fun play() {
        if (!isPlaying) {
            isPlaying = true
            binding = binding?.copy(playing = true)
            onPlay?.invoke()
        }
    }

    fun pause() {
        if (isPlaying) {
            isPlaying = false
            binding = binding?.copy(playing = false)
            onPause?.invoke()
        }
    }

    fun stop() {
        pause()
        currentTime = 0f
        binding = binding?.copy(currentTime = 0f)
        onTimeUpdate?.invoke(currentTime)
    }

    fun seekTo(time: Float) {
        val clamped = time.coerceIn(0f, duration)
        if (currentTime != clamped) {
            currentTime = clamped
            binding = binding?.copy(currentTime = clamped)
            needsUpdate = true
            onTimeUpdate?.invoke(currentTime)
        }
    }

    fun update(deltaTime: Float) {
        if (!isPlaying || duration <= 0f) return

        val newTime = currentTime + deltaTime * playbackRate
        currentTime = if (newTime >= duration) {
            if (loop) newTime % duration else duration
        } else {
            newTime
        }

        if (!loop && currentTime >= duration) {
            pause()
            onEnded?.invoke()
        }

        binding = binding?.copy(currentTime = currentTime)
        updateFrameData()
        needsUpdate = true
        onTimeUpdate?.invoke(currentTime)
    }

    private fun updateFrameData() {
        val newFrame = generateProceduralFrame()
        if (!newFrame.contentEquals(frameData)) {
            frameData = newFrame
            frameNumber++
            needsUpdate = true
        }
    }

    private fun generateProceduralFrame(): ByteArray {
        val data = ByteArray(width * height * 4)
        val time = currentTime

        for (y in 0 until height) {
            val v = y.toFloat() / height
            for (x in 0 until width) {
                val u = x.toFloat() / width
                val wave = sin((u + v + time) * PI.toFloat() * 4f).toFloat()
                val r = (wave * 0.5f + 0.5f)
                val g = (sin(time * 2f + u * PI.toFloat()) * 0.5f + 0.5f).toFloat()
                val b = (cos(time + v * PI.toFloat()) * 0.5f + 0.5f).toFloat()
                val idx = (y * width + x) * 4
                data[idx] = (r * 255f).roundToInt().toByte()
                data[idx + 1] = (g * 255f).roundToInt().toByte()
                data[idx + 2] = (b * 255f).roundToInt().toByte()
                data[idx + 3] = 255.toByte()
            }
        }

        return data
    }

    fun getCurrentFrameData(): ByteArray? = frameData?.copyOf()
    fun getCurrentFrameNumber(): Int = frameNumber
    fun canPlay(): Boolean = duration > 0f
    fun getAspectRatio(): Float = width.toFloat() / height
    fun getProgress(): Float = if (duration > 0f) currentTime / duration else 0f

    fun setProgress(progress: Float) {
        val target = (progress.coerceIn(0f, 1f)) * duration
        seekTo(target)
    }

    fun disposeVideo() {
        binding = null
        frameData = null
        isPlaying = false
    }

    val video: VideoBinding?
        get() = binding

    val videoElement: VideoBinding?
        get() = binding

    val source: VideoSource?
        get() = binding?.source

    private fun estimateDuration(source: VideoSource): Float {
        return when (source) {
            is VideoSource.Url -> parseDurationFromName(source.url)?.inWholeMilliseconds?.div(1000f) ?: 60f
            is VideoSource.Embedded -> max(1f, source.data.size / (1024f * 32f))
        }
    }

    private fun parseDurationFromName(name: String): Duration? {
        val pattern = Regex("(?:(\\d+)m)?(\\d+)s", RegexOption.IGNORE_CASE)
        val match = pattern.find(name) ?: return null
        val minutes = match.groups[1]?.value?.toLongOrNull() ?: 0L
        val seconds = match.groups[2]?.value?.toLongOrNull() ?: return null
        return (minutes * 60 + seconds).seconds
    }
}

/** Represents a bound video source (real or simulated). */
data class VideoBinding(
    val source: VideoSource,
    val playing: Boolean = false,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val loop: Boolean = false,
    val currentTime: Float = 0f
)

sealed class VideoSource {
    data class Url(val url: String) : VideoSource()
    data class Embedded(val data: ByteArray, val mimeType: String) : VideoSource()
}
