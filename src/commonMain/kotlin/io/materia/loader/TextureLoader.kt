package io.materia.loader

import io.materia.texture.Texture2D
import io.materia.texture.CubeTexture
import io.materia.texture.CubeFace
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loader for 2D textures from image files.
 *
 * TextureLoader provides a convenient way to load images as GPU textures,
 * with support for caching, progress tracking, and various texture options.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val loader = TextureLoader()
 * val texture = loader.load("textures/diffuse.png")
 * material.map = texture
 * ```
 *
 * ## With Options
 *
 * ```kotlin
 * val options = TextureLoader.TextureOptions(
 *     flipY = true,
 *     generateMipmaps = true,
 *     wrapS = TextureWrap.REPEAT,
 *     wrapT = TextureWrap.REPEAT
 * )
 * val texture = loader.load("textures/brick.jpg", options)
 * ```
 *
 * ## With Loading Manager
 *
 * ```kotlin
 * val manager = LoadingManager(
 *     onProgress = { url, loaded, total -> 
 *         println("Loading: $loaded/$total") 
 *     }
 * )
 * val loader = TextureLoader(manager = manager)
 * ```
 *
 * @param resolver Asset resolver for loading image data.
 * @param manager Optional loading manager for coordinated loading.
 */
class TextureLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) : AssetLoader<Texture2D> {

    /**
     * Configuration options for texture loading.
     *
     * @property flipY Whether to flip the image vertically (default: true for WebGL compatibility).
     * @property generateMipmaps Whether to generate mipmaps for the texture.
     * @property anisotropy Anisotropic filtering level.
     * @property magFilter Magnification filter.
     * @property minFilter Minification filter.
     * @property wrapS Horizontal wrapping mode.
     * @property wrapT Vertical wrapping mode.
     * @property format Texture format.
     */
    data class TextureOptions(
        val flipY: Boolean = true,
        val generateMipmaps: Boolean = true,
        val anisotropy: Float = 1f,
        val magFilter: TextureFilter = TextureFilter.LINEAR,
        val minFilter: TextureFilter = TextureFilter.LINEAR_MIPMAP_LINEAR,
        val wrapS: TextureWrap = TextureWrap.REPEAT,
        val wrapT: TextureWrap = TextureWrap.REPEAT,
        val format: TextureFormat = TextureFormat.RGBA8
    )

    private val cache = mutableMapOf<String, Texture2D>()
    private val imageLoader = ImageLoader(resolver, manager)

    /**
     * Loads a texture from the given path.
     *
     * @param path URL or file path to the image.
     * @return The loaded texture.
     * @throws Exception If loading fails.
     */
    override suspend fun load(path: String): Texture2D {
        return load(path, TextureOptions())
    }

    /**
     * Loads a texture with custom options.
     *
     * @param path URL or file path to the image.
     * @param options Texture configuration options.
     * @param onProgress Optional progress callback.
     * @return The loaded texture.
     */
    suspend fun load(
        path: String,
        options: TextureOptions = TextureOptions(),
        onProgress: ((LoadingProgress) -> Unit)? = null
    ): Texture2D = withContext(Dispatchers.Default) {
        // Check cache first
        val cacheKey = "$path:${options.hashCode()}"
        cache[cacheKey]?.let { return@withContext it }

        // Load and decode image
        val imageData = imageLoader.load(path, onProgress)

        // Optionally flip Y
        val pixels = if (options.flipY) {
            flipImageY(imageData.data, imageData.width, imageData.height)
        } else {
            imageData.data
        }

        // Create texture from image data
        val texture = Texture2D.fromImageData(
            width = imageData.width,
            height = imageData.height,
            data = pixels,
            format = options.format
        )

        // Apply options
        texture.name = path.substringAfterLast('/')
        texture.flipY = options.flipY
        texture.generateMipmaps = options.generateMipmaps
        texture.anisotropy = options.anisotropy
        texture.magFilter = options.magFilter
        texture.minFilter = options.minFilter
        texture.wrapS = options.wrapS
        texture.wrapT = options.wrapT
        texture.needsUpdate = true

        // Cache and return
        cache[cacheKey] = texture
        texture
    }

    /**
     * Loads a texture with callbacks (Three.js-style API).
     *
     * @param path URL or file path.
     * @param onLoad Success callback.
     * @param onProgress Progress callback.
     * @param onError Error callback.
     */
    suspend fun load(
        path: String,
        onLoad: (Texture2D) -> Unit,
        onProgress: ((LoadingProgress) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val texture = load(path, TextureOptions(), onProgress)
            onLoad(texture)
        } catch (e: Exception) {
            onError?.invoke(e) ?: throw e
        }
    }

    /**
     * Preloads multiple textures.
     *
     * @param paths List of paths to load.
     * @param options Options to apply to all textures.
     * @param onProgress Progress callback (itemIndex, totalItems).
     * @return List of loaded textures.
     */
    suspend fun preload(
        paths: List<String>,
        options: TextureOptions = TextureOptions(),
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<Texture2D> {
        return paths.mapIndexed { index, path ->
            val texture = load(path, options)
            onProgress?.invoke(index + 1, paths.size)
            texture
        }
    }

    /**
     * Clears the texture cache.
     */
    fun clearCache() {
        cache.clear()
        imageLoader.clearCache()
    }

    /**
     * Removes a specific texture from cache.
     *
     * @param path The path of the texture to uncache.
     */
    fun uncache(path: String) {
        cache.keys.filter { it.startsWith(path) }.forEach { cache.remove(it) }
        imageLoader.uncache(path)
    }

    /**
     * Checks if a texture is cached.
     *
     * @param path The path to check.
     * @return True if cached.
     */
    fun isCached(path: String): Boolean {
        return cache.keys.any { it.startsWith(path) }
    }

    companion object {
        /**
         * Flips image data vertically.
         */
        private fun flipImageY(data: ByteArray, width: Int, height: Int): ByteArray {
            val bytesPerRow = width * 4
            val flipped = ByteArray(data.size)

            for (y in 0 until height) {
                val srcOffset = y * bytesPerRow
                val dstOffset = (height - 1 - y) * bytesPerRow
                data.copyInto(flipped, dstOffset, srcOffset, srcOffset + bytesPerRow)
            }

            return flipped
        }

        /**
         * Detects image format from file header bytes.
         */
        fun detectImageFormat(bytes: ByteArray): ImageFormat {
            if (bytes.size < 4) return ImageFormat.UNKNOWN

            return when {
                // PNG: 89 50 4E 47
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> ImageFormat.PNG

                // JPEG: FF D8 FF
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                        bytes[2] == 0xFF.toByte() -> ImageFormat.JPEG

                // WebP: RIFF....WEBP
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                        bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                        bytes.size >= 12 &&
                        bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                        bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> ImageFormat.WEBP

                // GIF: GIF87a or GIF89a
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                        bytes[2] == 0x46.toByte() -> ImageFormat.GIF

                // BMP: BM
                bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> ImageFormat.BMP

                // TGA: No magic bytes, but check for valid header
                else -> ImageFormat.UNKNOWN
            }
        }
    }
}

/**
 * Supported image formats for texture loading.
 */
enum class ImageFormat {
    PNG,
    JPEG,
    WEBP,
    GIF,
    BMP,
    TGA,
    UNKNOWN
}

/**
 * Loader for cube textures (skyboxes, environment maps).
 *
 * ## Loading from 6 Face Images
 *
 * ```kotlin
 * val loader = CubeTextureLoader()
 * val envMap = loader.load(arrayOf(
 *     "px.jpg", "nx.jpg",  // +X, -X
 *     "py.jpg", "ny.jpg",  // +Y, -Y
 *     "pz.jpg", "nz.jpg"   // +Z, -Z
 * ))
 * scene.background = envMap
 * ```
 *
 * ## Loading from Equirectangular Image
 *
 * ```kotlin
 * val loader = CubeTextureLoader()
 * val envMap = loader.loadEquirectangular("environment.hdr")
 * ```
 *
 * @param resolver Asset resolver for loading image data.
 * @param manager Optional loading manager.
 */
class CubeTextureLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) {
    private val textureLoader = TextureLoader(resolver, manager)

    /**
     * Loads a cube texture from 6 face images.
     *
     * @param paths Array of 6 paths in order: [+X, -X, +Y, -Y, +Z, -Z]
     * @param onProgress Progress callback (faceIndex, totalFaces).
     * @return The loaded cube texture.
     */
    suspend fun load(
        paths: Array<String>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): CubeTexture {
        require(paths.size == 6) { "Cube texture requires exactly 6 face images" }

        val options = TextureLoader.TextureOptions(
            flipY = false, // Cube textures don't flip Y
            generateMipmaps = true,
            wrapS = TextureWrap.CLAMP_TO_EDGE,
            wrapT = TextureWrap.CLAMP_TO_EDGE
        )

        // Load all 6 faces
        val faces = paths.mapIndexed { index, path ->
            val texture = textureLoader.load(path, options)
            onProgress?.invoke(index + 1, 6)
            texture
        }

        // Determine size from first face
        val size = faces[0].width

        // Create cube texture
        val cubeTexture = CubeTexture(size)
        cubeTexture.name = paths[0].substringBeforeLast('/') + "/cubemap"

        // Set face data
        CubeFace.entries.forEachIndexed { index, face ->
            val texture = faces[index]
            texture.getData()?.let { data ->
                cubeTexture.setFaceData(face, data)
            }
        }

        cubeTexture.needsUpdate = true
        return cubeTexture
    }

    /**
     * Loads a cube texture with callbacks (Three.js-style API).
     *
     * @param paths Array of 6 paths.
     * @param onLoad Success callback.
     * @param onProgress Progress callback.
     * @param onError Error callback.
     */
    suspend fun load(
        paths: Array<String>,
        onLoad: (CubeTexture) -> Unit,
        onProgress: ((Int, Int) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        try {
            val texture = load(paths, onProgress)
            onLoad(texture)
        } catch (e: Exception) {
            onError?.invoke(e) ?: throw e
        }
    }

    /**
     * Loads a cube texture from a single equirectangular image.
     *
     * Converts the equirectangular panorama to 6 cube faces.
     *
     * @param path Path to the equirectangular image.
     * @param faceSize Size of each cube face (default: derived from image).
     * @return The loaded cube texture.
     */
    suspend fun loadEquirectangular(
        path: String,
        faceSize: Int = 512
    ): CubeTexture {
        val options = TextureLoader.TextureOptions(
            flipY = false,
            generateMipmaps = false
        )

        val equirect = textureLoader.load(path, options)
        val cubeTexture = CubeTexture(faceSize)
        cubeTexture.name = path.substringAfterLast('/').substringBeforeLast('.')

        // Convert equirectangular to cube faces
        CubeFace.entries.forEach { face ->
            val faceData = convertEquirectToCubeFace(equirect, face, faceSize)
            cubeTexture.setFaceData(face, faceData)
        }

        cubeTexture.needsUpdate = true
        return cubeTexture
    }

    companion object {
        /**
         * Converts a portion of an equirectangular image to a cube face.
         */
        private fun convertEquirectToCubeFace(
            equirect: Texture2D,
            face: CubeFace,
            size: Int
        ): ByteArray {
            val data = ByteArray(size * size * 4)
            val equirectData = equirect.getData() ?: return data
            val eqWidth = equirect.width
            val eqHeight = equirect.height

            for (y in 0 until size) {
                for (x in 0 until size) {
                    // Convert pixel coords to [-1, 1] range
                    val u = (x.toFloat() / (size - 1)) * 2f - 1f
                    val v = (y.toFloat() / (size - 1)) * 2f - 1f

                    // Get direction vector for this cube face pixel
                    val (dx, dy, dz) = when (face) {
                        CubeFace.POSITIVE_X -> Triple(1f, -v, -u)
                        CubeFace.NEGATIVE_X -> Triple(-1f, -v, u)
                        CubeFace.POSITIVE_Y -> Triple(u, 1f, v)
                        CubeFace.NEGATIVE_Y -> Triple(u, -1f, -v)
                        CubeFace.POSITIVE_Z -> Triple(u, -v, 1f)
                        CubeFace.NEGATIVE_Z -> Triple(-u, -v, -1f)
                    }

                    // Normalize direction
                    val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                    val nx = dx / len
                    val ny = dy / len
                    val nz = dz / len

                    // Convert to spherical coordinates
                    val phi = kotlin.math.atan2(nz.toDouble(), nx.toDouble()) // longitude
                    val theta = kotlin.math.acos(ny.toDouble().coerceIn(-1.0, 1.0)) // latitude

                    // Convert to equirectangular UV
                    val eqU = (phi / (2 * kotlin.math.PI) + 0.5).toFloat()
                    val eqV = (theta / kotlin.math.PI).toFloat()

                    // Sample equirectangular texture
                    val eqX = (eqU * (eqWidth - 1)).toInt().coerceIn(0, eqWidth - 1)
                    val eqY = (eqV * (eqHeight - 1)).toInt().coerceIn(0, eqHeight - 1)

                    val srcIndex = (eqY * eqWidth + eqX) * 4
                    val dstIndex = (y * size + x) * 4

                    if (srcIndex + 3 < equirectData.size && dstIndex + 3 < data.size) {
                        data[dstIndex] = equirectData[srcIndex]
                        data[dstIndex + 1] = equirectData[srcIndex + 1]
                        data[dstIndex + 2] = equirectData[srcIndex + 2]
                        data[dstIndex + 3] = equirectData[srcIndex + 3]
                    }
                }
            }

            return data
        }
    }
}
