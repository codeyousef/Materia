package io.materia.texture

import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap

/**
 * Video texture implementation for dynamic video content
 * T107 - VideoTexture for real-time video rendering
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
        this.generateMipmaps = false // Video textures typically don't use mipmaps
        this.flipY = true
        this.wrapS = TextureWrap.CLAMP_TO_EDGE
        this.wrapT = TextureWrap.CLAMP_TO_EDGE
    }

    // Video properties
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

    // Video source (platform-specific)
    var videoElement: Any? = null // HTMLVideoElement on Web, MediaPlayer on Android, etc.

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
        /**
         * Create a video texture from a URL
         */
        fun fromUrl(
            url: String,
            width: Int = 0, // 0 means auto-detect from video
            height: Int = 0,
            autoPlay: Boolean = false,
            loop: Boolean = false
        ): VideoTexture = VideoTexture(
            width = if (width > 0) width else 1920, // Default resolution
            height = if (height > 0) height else 1080,
            textureName = "VideoTexture($url)"
        ).apply {
            this.loop = loop
            loadFromUrl(url, autoPlay)
        }

        /**
         * Create a video texture from a data source
         */
        fun fromData(
            data: ByteArray,
            width: Int,
            height: Int,
            mimeType: String = "video/mp4"
        ): VideoTexture = VideoTexture(width, height).apply {
            loadFromData(data, mimeType)
        }
    }

    /**
     * Load video from URL (platform-specific implementation)
     */
    fun loadFromUrl(url: String, autoPlay: Boolean = false) {
        // Platform-specific implementation would create appropriate video element
        // For now, this is a stub that would be implemented per platform
        println("Loading video from URL: $url (autoPlay: $autoPlay)")

        // Simulate loading complete
        duration = 60f // Simulate 60 second video
        onLoadedMetadata?.invoke()
        onCanPlay?.invoke()

        if (autoPlay) {
            play()
        }
    }

    /**
     * Load video from data (platform-specific implementation)
     */
    fun loadFromData(data: ByteArray, mimeType: String) {
        // Platform-specific implementation
        println("Loading video from data: ${data.size} bytes, type: $mimeType")

        // Simulate loading complete
        duration = 30f // Simulate 30 second video
        onLoadedData?.invoke()
        onLoadedMetadata?.invoke()
        onCanPlay?.invoke()
    }

    /**
     * Start playing the video
     */
    fun play() {
        if (!isPlaying) {
            isPlaying = true
            onPlay?.invoke()
        }
    }

    /**
     * Pause the video
     */
    fun pause() {
        if (isPlaying) {
            isPlaying = false
            onPause?.invoke()
        }
    }

    /**
     * Stop the video and reset to beginning
     */
    fun stop() {
        pause()
        currentTime = 0f
        onTimeUpdate?.invoke(currentTime)
    }

    /**
     * Seek to a specific time
     */
    fun seekTo(time: Float) {
        val clampedTime = time.coerceIn(0f, duration)
        if (currentTime != clampedTime) {
            currentTime = clampedTime
            needsUpdate = true
            onTimeUpdate?.invoke(currentTime)
        }
    }

    /**
     * Update the video texture (should be called each frame)
     */
    fun update(deltaTime: Float) {
        if (isPlaying && duration > 0f) {
            val newTime = currentTime + deltaTime * playbackRate

            if (newTime >= duration) {
                if (loop) {
                    currentTime = newTime % duration
                } else {
                    currentTime = duration
                    pause()
                    onEnded?.invoke()
                }
            } else {
                currentTime = newTime
            }

            // Update frame data (platform-specific)
            updateFrameData()
            needsUpdate = true
            onTimeUpdate?.invoke(currentTime)
        }
    }

    /**
     * Update frame data from video source (platform-specific)
     */
    private fun updateFrameData() {
        // Platform-specific implementation would read current frame
        // For now, generate test pattern based on time
        val newFrame = generateTestFrame()
        if (!newFrame.contentEquals(frameData)) {
            frameData = newFrame
            frameNumber++
            needsUpdate = true
        }
    }

    /**
     * Generate a test frame pattern
     */
    private fun generateTestFrame(): ByteArray {
        val data = ByteArray(width * height * 4)
        val time = currentTime

        for (y in 0 until height) {
            for (x in 0 until width) {
                val u = x.toFloat() / width
                val v = y.toFloat() / height

                // Create animated pattern
                val wave = kotlin.math.sin((u + v + time) * kotlin.math.PI * 4).toFloat()
                val r = (wave * 0.5f + 0.5f)
                val g = (kotlin.math.sin(time * 2f + u * kotlin.math.PI) * 0.5f + 0.5f).toFloat()
                val b = (kotlin.math.cos(time + v * kotlin.math.PI) * 0.5f + 0.5f).toFloat()

                val index = (y * width + x) * 4
                data[index] = (r * 255).toInt().toByte()
                data[index + 1] = (g * 255).toInt().toByte()
                data[index + 2] = (b * 255).toInt().toByte()
                data[index + 3] = 255.toByte()
            }
        }

        return data
    }

    /**
     * Get current frame data
     */
    fun getCurrentFrameData(): ByteArray? = frameData?.copyOf()

    /**
     * Get current frame number
     */
    fun getCurrentFrameNumber(): Int = frameNumber

    /**
     * Check if video is ready to play
     */
    fun canPlay(): Boolean = duration > 0f

    /**
     * Get video aspect ratio
     */
    fun getAspectRatio(): Float = width.toFloat() / height.toFloat()

    /**
     * Get progress as a value between 0 and 1
     */
    fun getProgress(): Float = if (duration > 0f) currentTime / duration else 0f

    /**
     * Set progress as a value between 0 and 1
     */
    fun setProgress(progress: Float) {
        seekTo(progress.coerceIn(0f, 1f) * duration)
    }

    /**
     * Clone this video texture
     */
    override fun clone(): VideoTexture = VideoTexture(
        width = width,
        height = height,
        format = format,
        magFilter = magFilter,
        minFilter = minFilter,
        textureName = name
    ).apply {
        copy(this@VideoTexture)

        // Copy video-specific properties
        playbackRate = this@VideoTexture.playbackRate
        loop = this@VideoTexture.loop
        volume = this@VideoTexture.volume
        muted = this@VideoTexture.muted

        // Note: We don't copy playing state or current time
        // The cloned texture starts fresh
    }

    /**
     * Dispose of this texture and clean up video resources
     */
    override fun dispose() {
        super.dispose()
        stop()
        frameData = null
        videoElement = null

        // Clear event handlers
        onLoadedData = null
        onLoadedMetadata = null
        onCanPlay = null
        onPlay = null
        onPause = null
        onEnded = null
        onError = null
        onTimeUpdate = null
    }

    override fun toString(): String =
        "VideoTexture(id=$id, name='${name}', ${width}x$height, duration=${duration}s, playing=$isPlaying)"
}