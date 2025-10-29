package io.kreekt.loader

import io.kreekt.renderer.TextureFilter
import io.kreekt.renderer.TextureFormat
import io.kreekt.renderer.TextureWrap
import io.kreekt.texture.Texture2D
/**
 * Minimal KTX2 loader supporting uncompressed RGBA8 textures (vkFormat 37).
 * The loader reads the level index and extracts the base mip level into a
 * `Texture2D` instance. Supercompression and block-compressed formats are not
 * handled.
 */
class KTX2Loader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<Texture2D> {

    override suspend fun load(path: String): Texture2D {
        val basePath = path.substringBeforeLast('/', "")
        val data = resolver.load(path, if (basePath.isEmpty()) null else "$basePath/")
        return decodeKtx2(data)
    }

    private fun decodeKtx2(bytes: ByteArray): Texture2D {
        require(bytes.size >= 80) { "KTX2 file too small" }
        val identifier = byteArrayOf(
            0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A
        )
        require(bytes.copyOfRange(0, 12).contentEquals(identifier)) { "Invalid KTX2 header" }

        val vkFormat = readUInt32(bytes, 12)
        require(vkFormat == 37uL) { "Only VK_FORMAT_R8G8B8A8_UNORM is supported (got $vkFormat)" }
        val typeSize = readUInt32(bytes, 16).toInt()
        require(typeSize == 1) { "Only typeSize == 1 textures are supported" }
        val pixelWidth = readUInt32(bytes, 20).toInt()
        val pixelHeight = readUInt32(bytes, 24).toInt()
        require(pixelWidth > 0 && pixelHeight > 0) { "Invalid KTX2 dimensions" }

        val pixelDepth = readUInt32(bytes, 28).toInt()
        require(pixelDepth == 0) { "3D KTX2 textures are not supported" }
        val layerCount = readUInt32(bytes, 32).toInt()
        require(layerCount <= 1) { "Array KTX2 textures are not supported" }
        val faceCount = readUInt32(bytes, 36).toInt()
        require(faceCount == 1) { "Cube KTX2 textures are not supported" }
        val levelCount = readUInt32(bytes, 40).toInt().coerceAtLeast(1)
        val supercompression = readUInt32(bytes, 44).toInt()
        require(supercompression == 0) { "Only uncompressed KTX2 images are supported" }

        val dfdOffset = readUInt32(bytes, 48).toInt()
        val dfdLength = readUInt32(bytes, 52).toInt()
        if (dfdLength > 0) {
            require(dfdOffset >= 80 && dfdOffset + dfdLength <= bytes.size) { "Invalid DFD block" }
        }

        val kvdOffset = readUInt32(bytes, 56).toInt()
        val kvdLength = readUInt32(bytes, 60).toInt()
        if (kvdLength > 0) {
            require(kvdOffset >= 80 && kvdOffset + kvdLength <= bytes.size) { "Invalid KVD block" }
        }

        val sgdOffset = readUInt64(bytes, 64)
        val sgdLength = readUInt64(bytes, 72)
        require(sgdLength == 0uL) { "Supercompression global data is not supported" }
        require(sgdOffset == 0uL) { "Supercompression global data offset must be zero for uncompressed images" }

        val levelIndexOffset = 80
        val levelIndexLength = maxOf(1, levelCount) * 24
        require(levelIndexOffset + levelIndexLength <= bytes.size) { "KTX2 level index truncated" }

        var levelOffset = 0uL
        var levelSize = 0uL
        if (levelCount > 0) {
            levelOffset = readUInt64(bytes, levelIndexOffset)
            levelSize = readUInt64(bytes, levelIndexOffset + 8)
            val uncompressedSize = readUInt64(bytes, levelIndexOffset + 16)
            if (uncompressedSize != 0uL) {
                require(uncompressedSize == levelSize) { "Only uncompressed KTX2 levels are supported" }
            }
        }
        require(levelSize > 0uL) { "KTX2 level size missing" }
        require(levelOffset + levelSize <= bytes.size.toULong()) { "KTX2 level exceeds file bounds" }
        require(levelOffset <= Int.MAX_VALUE.toULong()) { "KTX2 level offset unsupported" }
        require(levelSize <= Int.MAX_VALUE.toULong()) { "KTX2 level size unsupported" }

        val expectedSize = pixelWidth.toLong() * pixelHeight.toLong() * 4L
        require(expectedSize > 0) { "KTX2 expected size overflow" }
        if (levelSize.toLong() >= expectedSize) {
            // OK
        } else {
            require(levelSize.toLong() == expectedSize) { "KTX2 payload smaller than expected texture data" }
        }

        val start = levelOffset.toInt()
        val end = (levelOffset + levelSize).toInt()
        val imageData = bytes.copyOfRange(start, end)
        val texture = Texture2D(
            width = pixelWidth,
            height = pixelHeight,
            format = TextureFormat.RGBA8,
            magFilter = TextureFilter.LINEAR,
            minFilter = TextureFilter.LINEAR,
            textureName = "KTX2_${pixelWidth}x$pixelHeight"
        )
        texture.wrapS = TextureWrap.CLAMP_TO_EDGE
        texture.wrapT = TextureWrap.CLAMP_TO_EDGE
        texture.setData(imageData)
        return texture
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): ULong {
        require(offset + 4 <= bytes.size) { "KTX2 readUInt32 out of bounds" }
        var result = 0uL
        for (i in 0 until 4) {
            result = result or ((bytes[offset + i].toULong() and 0xFFu) shl (8 * i))
        }
        return result
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): ULong {
        require(offset + 8 <= bytes.size) { "KTX2 readUInt64 out of bounds" }
        var result = 0uL
        for (i in 0 until 8) {
            result = result or ((bytes[offset + i].toULong() and 0xFFu) shl (8 * i))
        }
        return result
    }
}
