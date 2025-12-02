package io.materia.loader

import io.materia.texture.CubeTexture
import io.materia.texture.CubeFace
import io.materia.texture.Texture2D
import io.materia.renderer.TextureFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow

/**
 * Loader for HDR cube textures (high dynamic range environment maps).
 *
 * HDRCubeTextureLoader loads HDR images (RGBE/Radiance format) and converts
 * them to cube textures suitable for physically-based rendering.
 *
 * ## Usage
 *
 * ```kotlin
 * val loader = HDRCubeTextureLoader()
 * val envMap = loader.load(arrayOf(
 *     "px.hdr", "nx.hdr",
 *     "py.hdr", "ny.hdr",
 *     "pz.hdr", "nz.hdr"
 * ))
 * scene.environment = envMap
 * ```
 *
 * @param resolver Asset resolver for loading files.
 * @param manager Optional loading manager.
 */
class HDRCubeTextureLoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) {
    private val rgbeLoader = RGBELoader(resolver, manager)

    /**
     * Loads an HDR cube texture from 6 face images.
     *
     * @param paths Array of 6 HDR file paths: [+X, -X, +Y, -Y, +Z, -Z]
     * @param onProgress Progress callback.
     * @return HDR cube texture.
     */
    suspend fun load(
        paths: Array<String>,
        onProgress: ((Int, Int) -> Unit)? = null
    ): CubeTexture {
        require(paths.size == 6) { "HDR cube texture requires exactly 6 face images" }

        val faces = mutableListOf<RGBELoader.HDRImageData>()

        for ((index, path) in paths.withIndex()) {
            val hdrData = rgbeLoader.loadRaw(path)
            faces.add(hdrData)
            onProgress?.invoke(index + 1, 6)
        }

        // Determine size from first face
        val size = faces[0].width

        // Create HDR cube texture
        val cubeTexture = CubeTexture(
            size = size,
            format = TextureFormat.RGBA32F
        )
        cubeTexture.name = paths[0].substringBeforeLast('/') + "/hdr_cubemap"

        // Set face data
        CubeFace.entries.forEachIndexed { index, face ->
            cubeTexture.setFaceFloatData(face, faces[index].data)
        }

        cubeTexture.needsUpdate = true
        return cubeTexture
    }

    /**
     * Loads an HDR cube texture from an equirectangular HDR image.
     *
     * @param path Path to the equirectangular HDR image.
     * @param faceSize Size of each cube face.
     * @return HDR cube texture.
     */
    suspend fun loadEquirectangular(
        path: String,
        faceSize: Int = 512
    ): CubeTexture {
        val hdrData = rgbeLoader.loadRaw(path)

        val cubeTexture = CubeTexture(
            size = faceSize,
            format = TextureFormat.RGBA32F
        )
        cubeTexture.name = path.substringAfterLast('/').substringBeforeLast('.')

        // Convert equirectangular to cube faces
        CubeFace.entries.forEach { face ->
            val faceData = convertEquirectToCubeFaceHDR(hdrData, face, faceSize)
            cubeTexture.setFaceFloatData(face, faceData)
        }

        cubeTexture.needsUpdate = true
        return cubeTexture
    }

    companion object {
        private fun convertEquirectToCubeFaceHDR(
            equirect: RGBELoader.HDRImageData,
            face: CubeFace,
            size: Int
        ): FloatArray {
            val data = FloatArray(size * size * 4)
            val eqWidth = equirect.width
            val eqHeight = equirect.height
            val equirectData = equirect.data

            for (y in 0 until size) {
                for (x in 0 until size) {
                    val u = (x.toFloat() / (size - 1)) * 2f - 1f
                    val v = (y.toFloat() / (size - 1)) * 2f - 1f

                    val (dx, dy, dz) = when (face) {
                        CubeFace.POSITIVE_X -> Triple(1f, -v, -u)
                        CubeFace.NEGATIVE_X -> Triple(-1f, -v, u)
                        CubeFace.POSITIVE_Y -> Triple(u, 1f, v)
                        CubeFace.NEGATIVE_Y -> Triple(u, -1f, -v)
                        CubeFace.POSITIVE_Z -> Triple(u, -v, 1f)
                        CubeFace.NEGATIVE_Z -> Triple(-u, -v, -1f)
                    }

                    val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                    val nx = dx / len
                    val ny = dy / len
                    val nz = dz / len

                    val phi = kotlin.math.atan2(nz.toDouble(), nx.toDouble())
                    val theta = kotlin.math.acos(ny.toDouble().coerceIn(-1.0, 1.0))

                    val eqU = (phi / (2 * kotlin.math.PI) + 0.5).toFloat()
                    val eqV = (theta / kotlin.math.PI).toFloat()

                    val eqX = (eqU * (eqWidth - 1)).toInt().coerceIn(0, eqWidth - 1)
                    val eqY = (eqV * (eqHeight - 1)).toInt().coerceIn(0, eqHeight - 1)

                    val srcIndex = (eqY * eqWidth + eqX) * 4
                    val dstIndex = (y * size + x) * 4

                    if (srcIndex + 3 < equirectData.size) {
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

/**
 * Loader for RGBE/Radiance HDR format (.hdr files).
 *
 * RGBELoader decodes HDR images using the Radiance RGBE format,
 * which encodes floating-point RGB values with a shared exponent.
 *
 * ## Usage
 *
 * ```kotlin
 * val loader = RGBELoader()
 * val hdrTexture = loader.load("environment.hdr")
 * ```
 *
 * @param resolver Asset resolver for loading files.
 * @param manager Optional loading manager.
 */
class RGBELoader(
    private val resolver: AssetResolver = AssetResolver.default(),
    private val manager: LoadingManager? = null
) : AssetLoader<Texture2D> {

    /**
     * HDR image data in floating point format.
     */
    data class HDRImageData(
        val width: Int,
        val height: Int,
        val data: FloatArray,
        val gamma: Float = 1f,
        val exposure: Float = 1f
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as HDRImageData

            if (width != other.width) return false
            if (height != other.height) return false
            if (!data.contentEquals(other.data)) return false
            if (gamma != other.gamma) return false
            if (exposure != other.exposure) return false

            return true
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            result = 31 * result + data.contentHashCode()
            result = 31 * result + gamma.hashCode()
            result = 31 * result + exposure.hashCode()
            return result
        }
    }

    /**
     * Loads an HDR file and returns a Texture2D.
     *
     * @param path Path to the HDR file.
     * @return Texture2D with float data.
     */
    override suspend fun load(path: String): Texture2D {
        val hdrData = loadRaw(path)

        return Texture2D.fromFloatData(
            width = hdrData.width,
            height = hdrData.height,
            data = hdrData.data,
            format = TextureFormat.RGBA32F
        ).apply {
            name = path.substringAfterLast('/')
            flipY = true
            generateMipmaps = false
            needsUpdate = true
        }
    }

    /**
     * Loads raw HDR data.
     *
     * @param path Path to the HDR file.
     * @return Raw HDR image data.
     */
    suspend fun loadRaw(path: String): HDRImageData = withContext(Dispatchers.Default) {
        manager?.itemStart(path)

        try {
            val bytes = resolver.load(path)
            val result = decodeRGBE(bytes)
            manager?.itemEnd(path)
            result
        } catch (e: Exception) {
            manager?.itemError(path, e)
            throw e
        }
    }

    companion object {
        /**
         * Decodes RGBE format data.
         */
        private fun decodeRGBE(bytes: ByteArray): HDRImageData {
            var offset = 0
            var gamma = 1f
            var exposure = 1f

            // Read header
            val headerEnd = findHeaderEnd(bytes)
            val header = bytes.decodeToString(0, headerEnd)

            // Parse header for dimensions and settings
            val lines = header.split('\n')
            var width = 0
            var height = 0

            for (line in lines) {
                when {
                    line.startsWith("GAMMA=") -> {
                        gamma = line.substringAfter("GAMMA=").trim().toFloatOrNull() ?: 1f
                    }
                    line.startsWith("EXPOSURE=") -> {
                        exposure = line.substringAfter("EXPOSURE=").trim().toFloatOrNull() ?: 1f
                    }
                    line.startsWith("-Y") || line.startsWith("+Y") -> {
                        // Resolution line: -Y 512 +X 1024
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            height = parts[1].toIntOrNull() ?: 0
                            width = parts[3].toIntOrNull() ?: 0
                        }
                    }
                }
            }

            if (width == 0 || height == 0) {
                throw IllegalArgumentException("Invalid HDR file: could not parse dimensions")
            }

            offset = headerEnd + 1

            // Decode pixel data
            val pixels = FloatArray(width * height * 4)
            
            // Support both RLE and non-RLE formats
            if (isNewRLE(bytes, offset, width)) {
                decodeRLEPixels(bytes, offset, width, height, pixels)
            } else {
                decodeFlatPixels(bytes, offset, width, height, pixels)
            }

            return HDRImageData(width, height, pixels, gamma, exposure)
        }

        private fun findHeaderEnd(bytes: ByteArray): Int {
            // Find empty line that ends header
            var i = 0
            while (i < bytes.size - 1) {
                if (bytes[i] == '\n'.code.toByte() && bytes[i + 1] == '\n'.code.toByte()) {
                    return i + 1
                }
                if (bytes[i] == '\n'.code.toByte()) {
                    // Check for resolution line which ends header
                    var j = i + 1
                    while (j < bytes.size && bytes[j] != '\n'.code.toByte()) j++
                    val line = bytes.decodeToString(i + 1, j)
                    if (line.startsWith("-Y") || line.startsWith("+Y")) {
                        return j
                    }
                }
                i++
            }
            return bytes.size
        }

        private fun isNewRLE(bytes: ByteArray, offset: Int, width: Int): Boolean {
            if (offset + 4 > bytes.size) return false

            val r = bytes[offset].toInt() and 0xFF
            val g = bytes[offset + 1].toInt() and 0xFF

            // New RLE marker: 2 2 width_high width_low
            return r == 2 && g == 2 &&
                    ((bytes[offset + 2].toInt() and 0xFF) * 256 +
                            (bytes[offset + 3].toInt() and 0xFF)) == width
        }

        private fun decodeRLEPixels(
            bytes: ByteArray,
            startOffset: Int,
            width: Int,
            height: Int,
            pixels: FloatArray
        ) {
            var offset = startOffset

            for (y in 0 until height) {
                // Skip RLE header (2 2 w_hi w_lo)
                offset += 4

                // Read each channel separately
                val scanline = Array(4) { ByteArray(width) }

                for (ch in 0..3) {
                    var x = 0
                    while (x < width) {
                        val code = bytes[offset++].toInt() and 0xFF
                        if (code > 128) {
                            // Run
                            val count = code - 128
                            val value = bytes[offset++]
                            for (i in 0 until count) {
                                scanline[ch][x++] = value
                            }
                        } else {
                            // Literal
                            for (i in 0 until code) {
                                scanline[ch][x++] = bytes[offset++]
                            }
                        }
                    }
                }

                // Convert scanline to float
                for (x in 0 until width) {
                    val r = scanline[0][x].toInt() and 0xFF
                    val g = scanline[1][x].toInt() and 0xFF
                    val b = scanline[2][x].toInt() and 0xFF
                    val e = scanline[3][x].toInt() and 0xFF

                    val idx = (y * width + x) * 4
                    if (e == 0) {
                        pixels[idx] = 0f
                        pixels[idx + 1] = 0f
                        pixels[idx + 2] = 0f
                        pixels[idx + 3] = 1f
                    } else {
                        val scale = 2f.pow(e - 128 - 8)
                        pixels[idx] = r * scale
                        pixels[idx + 1] = g * scale
                        pixels[idx + 2] = b * scale
                        pixels[idx + 3] = 1f
                    }
                }
            }
        }

        private fun decodeFlatPixels(
            bytes: ByteArray,
            startOffset: Int,
            width: Int,
            height: Int,
            pixels: FloatArray
        ) {
            var offset = startOffset

            for (i in 0 until width * height) {
                val r = bytes[offset++].toInt() and 0xFF
                val g = bytes[offset++].toInt() and 0xFF
                val b = bytes[offset++].toInt() and 0xFF
                val e = bytes[offset++].toInt() and 0xFF

                val idx = i * 4
                if (e == 0) {
                    pixels[idx] = 0f
                    pixels[idx + 1] = 0f
                    pixels[idx + 2] = 0f
                    pixels[idx + 3] = 1f
                } else {
                    val scale = 2f.pow(e - 128 - 8)
                    pixels[idx] = r * scale
                    pixels[idx + 1] = g * scale
                    pixels[idx + 2] = b * scale
                    pixels[idx + 3] = 1f
                }
            }
        }
    }
}
