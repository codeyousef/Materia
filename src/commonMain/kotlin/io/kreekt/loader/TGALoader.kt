package io.kreekt.loader

import io.kreekt.texture.Texture2D

/**
 * Loader for TGA textures (24/32-bit, uncompressed or RLE). Implemented fully in
 * common code so it works across JVM/Android/JS targets.
 */
class TGALoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<Texture2D> {

    override suspend fun load(path: String): Texture2D {
        val bytes = resolver.load(path, deriveBasePath(path))
        return decode(bytes, path.substringAfterLast('/'))
    }

    private suspend fun decode(data: ByteArray, name: String): Texture2D {
        require(data.size >= HEADER_SIZE) { "TGA file too small" }

        val idLength = data[0].toInt() and 0xFF
        val colorMapType = data[1].toInt() and 0xFF
        val imageType = data[2].toInt() and 0xFF

        require(colorMapType == 0) { "Color mapped TGA images are not supported" }
        require(imageType == 2 || imageType == 10) { "Unsupported TGA type $imageType" }

        val width = readUInt16(data, 12)
        val height = readUInt16(data, 14)
        val bitsPerPixel = data[16].toInt() and 0xFF
        val descriptor = data[17].toInt() and 0xFF

        require(width > 0 && height > 0) { "Invalid TGA dimensions" }
        require(bitsPerPixel == 24 || bitsPerPixel == 32) { "Only 24-bit and 32-bit TGA images are supported" }

        val hasAlpha = bitsPerPixel == 32 && (descriptor and 0x0F) > 0
        val bytesPerPixel = bitsPerPixel / 8
        var offset = HEADER_SIZE + idLength

        require(offset <= data.size) { "TGA header declares invalid ID length" }

        val pixelCount = width * height
        val rgba = ByteArray(pixelCount * 4)

        if (imageType == 2) {
            decodeUncompressed(data, offset, bytesPerPixel, hasAlpha, rgba)
        } else {
            offset = decodeRle(data, offset, bytesPerPixel, hasAlpha, rgba)
        }

        val topOrigin = (descriptor and 0x20) != 0
        if (!topOrigin) {
            flipVertically(rgba, width, height)
        }

        val texture = Texture2D.fromImageData(width, height, rgba)
        texture.setTextureName(name.ifBlank { "Texture.tga" })
        texture.generateMipmaps = true
        return texture
    }

    private fun decodeUncompressed(
        data: ByteArray,
        startOffset: Int,
        bytesPerPixel: Int,
        hasAlpha: Boolean,
        outRgba: ByteArray
    ) {
        var offset = startOffset
        var outIndex = 0
        val expectedSize = startOffset + outRgba.size / 4 * bytesPerPixel
        require(expectedSize <= data.size) { "TGA pixel data truncated" }

        while (outIndex < outRgba.size) {
            val b = data[offset].toInt() and 0xFF
            val g = data[offset + 1].toInt() and 0xFF
            val r = data[offset + 2].toInt() and 0xFF
            val a = if (hasAlpha) data[offset + 3].toInt() and 0xFF else 0xFF

            outRgba[outIndex++] = r.toByte()
            outRgba[outIndex++] = g.toByte()
            outRgba[outIndex++] = b.toByte()
            outRgba[outIndex++] = a.toByte()

            offset += bytesPerPixel
        }
    }

    private fun decodeRle(
        data: ByteArray,
        startOffset: Int,
        bytesPerPixel: Int,
        hasAlpha: Boolean,
        outRgba: ByteArray
    ): Int {
        var offset = startOffset
        var outIndex = 0
        val pixelSize = bytesPerPixel

        while (outIndex < outRgba.size) {
            require(offset < data.size) { "TGA RLE packet overruns buffer" }
            val packet = data[offset++].toInt() and 0xFF
            val count = (packet and 0x7F) + 1

            if ((packet and 0x80) != 0) {
                // Run length packet
                require(offset + pixelSize <= data.size) { "TGA RLE run packet truncated" }
                val b = data[offset].toInt() and 0xFF
                val g = data[offset + 1].toInt() and 0xFF
                val r = data[offset + 2].toInt() and 0xFF
                val a = if (hasAlpha && pixelSize == 4) data[offset + 3].toInt() and 0xFF else 0xFF
                offset += pixelSize

                repeat(count) {
                    require(outIndex + 4 <= outRgba.size) { "TGA RLE run exceeds pixel buffer" }
                    outRgba[outIndex++] = r.toByte()
                    outRgba[outIndex++] = g.toByte()
                    outRgba[outIndex++] = b.toByte()
                    outRgba[outIndex++] = a.toByte()
                }
            } else {
                // Raw packet
                repeat(count) {
                    require(offset + pixelSize <= data.size) { "TGA RLE raw packet truncated" }
                    val b = data[offset].toInt() and 0xFF
                    val g = data[offset + 1].toInt() and 0xFF
                    val r = data[offset + 2].toInt() and 0xFF
                    val a = if (hasAlpha && pixelSize == 4) data[offset + 3].toInt() and 0xFF else 0xFF
                    offset += pixelSize

                    require(outIndex + 4 <= outRgba.size) { "TGA RLE raw packet overruns" }
                    outRgba[outIndex++] = r.toByte()
                    outRgba[outIndex++] = g.toByte()
                    outRgba[outIndex++] = b.toByte()
                    outRgba[outIndex++] = a.toByte()
                }
            }
        }

        return offset
    }

    private fun flipVertically(buffer: ByteArray, width: Int, height: Int) {
        val rowBytes = width * 4
        val tempRow = ByteArray(rowBytes)
        var top = 0
        var bottom = (height - 1) * rowBytes
        while (top < bottom) {
            buffer.copyInto(tempRow, 0, top, top + rowBytes)
            buffer.copyInto(buffer, top, bottom, bottom + rowBytes)
            tempRow.copyInto(buffer, bottom, 0, rowBytes)
            top += rowBytes
            bottom -= rowBytes
        }
    }

    private fun deriveBasePath(path: String): String? {
        val normalized = path.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) normalized.substring(0, lastSlash + 1) else null
    }

    private fun readUInt16(data: ByteArray, index: Int): Int {
        return (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
    }

    companion object {
        private const val HEADER_SIZE = 18
    }
}
