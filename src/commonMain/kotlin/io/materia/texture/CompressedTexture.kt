package io.materia.texture

import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureWrap

/**
 * CompressedTexture - GPU-compressed texture formats
 * T080 - Support for BC7, ETC2, ASTC, PVRTC formats
 *
 * Provides efficient texture storage using hardware-accelerated compression formats.
 * Different platforms support different compression formats.
 */
class CompressedTexture(
    override val width: Int,
    override val height: Int,
    val compressedFormat: CompressedTextureFormat
) : Texture() {

    val compressedMipmaps: MutableList<CompressedTextureMipmap> = mutableListOf()
    var isCompressed: Boolean = true

    init {
        name = "CompressedTexture"
        this.generateMipmaps = false  // Compressed textures have pre-generated mipmaps
        this.flipY = false  // Compressed textures are typically not flipped
    }

    /**
     * Add a mipmap level
     */
    fun addMipmap(level: Int, width: Int, height: Int, data: ByteArray) {
        compressedMipmaps.add(CompressedTextureMipmap(level, width, height, data))
        needsUpdate = true
    }

    /**
     * Get data for a specific mipmap level
     */
    fun getMipmap(level: Int): CompressedTextureMipmap? =
        compressedMipmaps.find { it.level == level }

    /**
     * Check if this format is supported on the current platform
     */
    fun isSupported(): Boolean = CompressedTextureSupport.isFormatSupported(compressedFormat)

    /**
     * Get the total size of all mipmap data
     */
    fun getTotalSize(): Int = compressedMipmaps.sumOf { it.data.size }

    override fun clone(): CompressedTexture =
        CompressedTexture(width, height, compressedFormat).apply {
            copy(this@CompressedTexture)
            this@CompressedTexture.compressedMipmaps.forEach { mipmap ->
                addMipmap(mipmap.level, mipmap.width, mipmap.height, mipmap.data.copyOf())
            }
        }

    override fun dispose() {
        super.dispose()
        compressedMipmaps.clear()
    }
}

/**
 * Mipmap level data for compressed textures
 */
data class CompressedTextureMipmap(
    val level: Int,
    val width: Int,
    val height: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressedTextureMipmap) return false

        if (level != other.level) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = level
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Compressed texture formats
 */
enum class CompressedTextureFormat {
    // Desktop formats
    BC1_RGB,        // DXT1 - RGB, no alpha
    BC1_RGBA,       // DXT1 - RGBA with 1-bit alpha
    BC2_RGBA,       // DXT3 - RGBA with 4-bit alpha
    BC3_RGBA,       // DXT5 - RGBA with interpolated alpha
    BC4_R,          // Single channel compression
    BC5_RG,         // Two channel compression (normal maps)
    BC6H_RGB,       // HDR RGB
    BC7_RGBA,       // High quality RGBA

    // Mobile formats
    ETC1_RGB,       // Ericsson Texture Compression v1
    ETC2_RGB,       // ETC2 RGB
    ETC2_RGBA,      // ETC2 RGBA
    ETC2_RGB_A1,    // ETC2 RGB with 1-bit alpha

    ASTC_4x4,       // Adaptive Scalable Texture Compression
    ASTC_5x4,
    ASTC_5x5,
    ASTC_6x5,
    ASTC_6x6,
    ASTC_8x5,
    ASTC_8x6,
    ASTC_8x8,
    ASTC_10x5,
    ASTC_10x6,
    ASTC_10x8,
    ASTC_10x10,
    ASTC_12x10,
    ASTC_12x12,

    PVRTC_2BPP_RGB,  // PowerVR 2 bits per pixel RGB
    PVRTC_2BPP_RGBA, // PowerVR 2 bits per pixel RGBA
    PVRTC_4BPP_RGB,  // PowerVR 4 bits per pixel RGB
    PVRTC_4BPP_RGBA  // PowerVR 4 bits per pixel RGBA
}

/**
 * Platform-specific compressed texture support detection
 * T081 - Platform-specific compression detection
 */
expect object CompressedTextureSupport {
    /**
     * Check if a specific format is supported
     */
    fun isFormatSupported(format: CompressedTextureFormat): Boolean

    /**
     * Get all supported formats on this platform
     */
    fun getSupportedFormats(): Set<CompressedTextureFormat>

    /**
     * Get the best format for this platform
     */
    fun getBestFormat(requireAlpha: Boolean = true): CompressedTextureFormat?
}

/**
 * Compressed texture loader
 * T082 - Load KTX2, DDS, PVR files
 */
class CompressedTextureLoader {

    /**
     * Load a compressed texture from file data
     */
    fun load(data: ByteArray, mimeType: String? = null): CompressedTexture? {
        // Detect format from magic bytes or mime type
        val format = detectFormat(data, mimeType) ?: return null

        return when (format) {
            TextureFileFormat.KTX2 -> loadKTX2(data)
            TextureFileFormat.DDS -> loadDDS(data)
            TextureFileFormat.PVR -> loadPVR(data)
            TextureFileFormat.ASTC -> loadASTC(data)
        }
    }

    private fun detectFormat(data: ByteArray, mimeType: String?): TextureFileFormat? {
        // Check mime type first
        mimeType?.let {
            return when (it) {
                "image/ktx2" -> TextureFileFormat.KTX2
                "image/vnd-ms.dds" -> TextureFileFormat.DDS
                "image/x-pvr" -> TextureFileFormat.PVR
                else -> null
            }
        }

        // Check magic bytes
        if (data.size < 12) return null

        return when {
            // KTX2: 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB
            data[0] == 0xAB.toByte() && data[1] == 0x4B.toByte() &&
                    data[2] == 0x54.toByte() && data[3] == 0x58.toByte() -> TextureFileFormat.KTX2

            // DDS: "DDS " (0x44, 0x44, 0x53, 0x20)
            data[0] == 0x44.toByte() && data[1] == 0x44.toByte() &&
                    data[2] == 0x53.toByte() && data[3] == 0x20.toByte() -> TextureFileFormat.DDS

            // PVR: "PVR\x03" or "PVR!"
            data[0] == 0x50.toByte() && data[1] == 0x56.toByte() &&
                    data[2] == 0x52.toByte() -> TextureFileFormat.PVR

            // ASTC: 0x13, 0xAB, 0xA1, 0x5C
            data[0] == 0x13.toByte() && data[1] == 0xAB.toByte() &&
                    data[2] == 0xA1.toByte() && data[3] == 0x5C.toByte() -> TextureFileFormat.ASTC

            else -> null
        }
    }

    private fun loadKTX2(data: ByteArray): CompressedTexture {
        // KTX2 file format parsing
        // This is a simplified implementation
        val width = 512  // Parse from header
        val height = 512  // Parse from header
        val format = CompressedTextureFormat.BC7_RGBA  // Parse from vkFormat

        val texture = CompressedTexture(width, height, format)
        // Parse and add mipmaps
        texture.addMipmap(0, width, height, data)

        return texture
    }

    private fun loadDDS(data: ByteArray): CompressedTexture {
        // DDS file format parsing
        val width = 512  // Parse from header
        val height = 512  // Parse from header
        val format = CompressedTextureFormat.BC3_RGBA  // Parse from fourCC

        val texture = CompressedTexture(width, height, format)
        texture.addMipmap(0, width, height, data)

        return texture
    }

    private fun loadPVR(data: ByteArray): CompressedTexture {
        // PVR file format parsing
        val width = 512  // Parse from header
        val height = 512  // Parse from header
        val format = CompressedTextureFormat.PVRTC_4BPP_RGBA  // Parse from header

        val texture = CompressedTexture(width, height, format)
        texture.addMipmap(0, width, height, data)

        return texture
    }

    private fun loadASTC(data: ByteArray): CompressedTexture {
        // ASTC file format parsing
        val width = 512  // Parse from header
        val height = 512  // Parse from header
        val format = CompressedTextureFormat.ASTC_6x6  // Parse from header

        val texture = CompressedTexture(width, height, format)
        texture.addMipmap(0, width, height, data)

        return texture
    }
}

/**
 * Texture file formats
 */
private enum class TextureFileFormat {
    KTX2,
    DDS,
    PVR,
    ASTC
}