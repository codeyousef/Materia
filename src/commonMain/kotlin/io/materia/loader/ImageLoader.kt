package io.materia.loader

/**
 * Decoded image data.
 * 
 * Represents raw image pixels in RGBA format.
 */
data class ImageData(
    /** Width in pixels. */
    val width: Int,
    /** Height in pixels. */
    val height: Int,
    /** Raw RGBA pixel data (4 bytes per pixel). */
    val data: ByteArray
) {
    /** Total number of pixels. */
    val pixelCount: Int get() = width * height

    /** Total data size in bytes. */
    val dataSize: Int get() = data.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ImageData

        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Loads images from URLs or file paths.
 *
 * ImageLoader provides low-level image loading that returns raw pixel data.
 * For textures, use [TextureLoader] instead.
 *
 * ## Usage
 *
 * ```kotlin
 * val loader = ImageLoader()
 * val image = loader.load("textures/sprite.png")
 * println("Loaded ${image.width}x${image.height} image")
 * ```
 *
 * @param resolver Asset resolver for loading image bytes.
 * @param manager Optional loading manager for progress tracking.
 */
class ImageLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) : AssetLoader<ImageData> {

    private val cache = mutableMapOf<String, ImageData>()

    /**
     * Loads an image from the given path.
     *
     * @param path URL or file path to the image.
     * @return The decoded image data.
     * @throws Exception If loading or decoding fails.
     */
    override suspend fun load(path: String): ImageData {
        return load(path, null)
    }

    /**
     * Loads an image with progress callback.
     *
     * @param path URL or file path to the image.
     * @param onProgress Optional progress callback.
     * @return The decoded image data.
     */
    suspend fun load(
        path: String,
        onProgress: ((LoadingProgress) -> Unit)?
    ): ImageData {
        // Check cache
        cache[path]?.let { return it }

        val resolvedPath = manager?.resolveURL(path) ?: path

        manager?.itemStart(resolvedPath)

        return try {
            // Load raw bytes
            val bytes = resolver.load(resolvedPath)

            // Decode image using platform-specific decoder
            val decoded = PlatformImageDecoder.decode(bytes)

            val imageData = ImageData(
                width = decoded.width,
                height = decoded.height,
                data = decoded.pixels
            )

            // Cache result
            cache[path] = imageData

            manager?.itemEnd(resolvedPath)

            imageData
        } catch (e: Exception) {
            manager?.itemError(resolvedPath, e)
            throw e
        }
    }

    /**
     * Loads an image with callbacks (Three.js-style API).
     *
     * @param path URL or file path.
     * @param onLoad Success callback.
     * @param onProgress Progress callback.
     * @param onError Error callback.
     */
    suspend fun load(
        path: String,
        onLoad: (ImageData) -> Unit,
        onProgress: ((LoadingProgress) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val image = load(path, onProgress)
            onLoad(image)
        } catch (e: Exception) {
            onError?.invoke(e) ?: throw e
        }
    }

    /**
     * Clears the image cache.
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Removes a specific image from cache.
     *
     * @param path The path of the image to uncache.
     */
    fun uncache(path: String) {
        cache.remove(path)
    }

    /**
     * Creates image data from raw RGBA bytes.
     *
     * @param width Image width.
     * @param height Image height.
     * @param data RGBA pixel data.
     * @return ImageData instance.
     */
    companion object {
        fun createImageData(width: Int, height: Int, data: ByteArray): ImageData {
            require(data.size == width * height * 4) {
                "Data size (${data.size}) must equal width * height * 4 (${width * height * 4})"
            }
            return ImageData(width, height, data)
        }
    }
}
