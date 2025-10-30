package io.materia.loader

import io.materia.renderer.TextureFilter
import io.materia.renderer.TextureFormat
import io.materia.renderer.TextureWrap
import io.materia.texture.Texture2D
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal OpenEXR loader supporting scanline images with no compression and
 * float RGB channels. This covers the majority of HDR environment maps exported
 * specifically for real-time engines.
 */
class EXRLoader(
    private val resolver: AssetResolver = AssetResolver.default()
) : AssetLoader<Texture2D> {

    override suspend fun load(path: String): Texture2D {
        val basePath = path.substringBeforeLast('/', "")
        val bytes = resolver.load(path, if (basePath.isEmpty()) null else "$basePath/")
        return decode(bytes)
    }

    private fun decode(bytes: ByteArray): Texture2D {
        require(bytes.size >= 4) { "EXR file too small" }
        val magic = readInt(bytes, 0)
        require(magic == 0x762f3101) { "Invalid EXR magic" }
        val version = readInt(bytes, 4)
        require(version and 0xFF == 2) { "Unsupported EXR version" }

        var cursor = 8
        var compression = 0
        var dataWindow = IntArray(4)
        var channels: List<Channel> = emptyList()

        while (true) {
            val nameEnd = bytes.indexOfZero(cursor)
            if (nameEnd == -1) throw IllegalArgumentException("Malformed EXR header")
            if (nameEnd == cursor) {
                cursor += 1
                break
            }
            val attrName = bytes.decodeToString(cursor, nameEnd)
            cursor = nameEnd + 1
            val typeEnd = bytes.indexOfZero(cursor)
            val attrType = bytes.decodeToString(cursor, typeEnd)
            cursor = typeEnd + 1
            val size = readInt(bytes, cursor)
            cursor += 4
            require(cursor + size <= bytes.size) { "EXR attribute $attrName exceeds file size" }
            when (attrName) {
                "compression" -> compression = bytes[cursor].toInt()
                "dataWindow" -> {
                    for (i in 0 until 4) {
                        dataWindow[i] = readInt(bytes, cursor + i * 4)
                    }
                }
                "channels" -> channels = parseChannels(bytes.copyOfRange(cursor, cursor + size))
            }
            cursor += size
        }

        require(compression == 0) { "Only uncompressed EXR files are supported" }
        require(channels.isNotEmpty()) { "EXR missing channel list" }

        val minX = dataWindow[0]
        val maxX = dataWindow[1]
        val minY = dataWindow[2]
        val maxY = dataWindow[3]
        val width = maxX - minX + 1
        val height = maxY - minY + 1
        require(width > 0 && height > 0)

        val expectedOffsetsEnd = cursor + height * 8
        require(expectedOffsetsEnd <= bytes.size) { "EXR missing line offset table" }
        val lineOffsets = LongArray(height) { readLong(bytes, cursor + it * 8) }
        cursor += height * 8

        val channelOrder = channels.sortedBy { it.name }
        val channelMaps = channelOrder.associateBy { it.name }
        val hasAlpha = channelMaps.containsKey("A")

        val floatPixels = FloatArray(width * height * 4) { if (it % 4 == 3) 1f else 0f }

        channelOrder.forEach { require(it.pixelType == 2) { "Only FLOAT channels supported" } }

        channelOrder.forEach { channel ->
            channel.buffers = FloatArray(width * height)
        }

        for (row in 0 until height) {
            val offset = lineOffsets[row]
            require(offset >= 0 && offset < bytes.size) { "EXR scanline offset out of bounds" }
            var ptr = offset.toInt()
            val y = readInt(bytes, ptr); ptr += 4
            require(y == minY + row) { "Unexpected scanline order" }
            val dataSize = readInt(bytes, ptr); ptr += 4
            require(ptr + dataSize <= bytes.size) { "EXR scanline data exceeds file size" }
            var consumed = 0
            for (channel in channelOrder) {
                val buffer = channel.buffers ?: continue
                for (x in 0 until width) {
                    val sampleOffset = ptr + consumed
                    require(sampleOffset + 4 <= bytes.size) { "EXR pixel data truncated" }
                    buffer[row * width + x] = readFloat(bytes, sampleOffset)
                    consumed += 4
                }
            }
            require(consumed == dataSize) { "EXR scanline size mismatch" }
            ptr += consumed
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = (y * width + x) * 4
                floatPixels[idx] = channelMaps["R"]?.buffers?.get(y * width + x) ?: 0f
                floatPixels[idx + 1] = channelMaps["G"]?.buffers?.get(y * width + x) ?: floatPixels[idx]
                floatPixels[idx + 2] = channelMaps["B"]?.buffers?.get(y * width + x) ?: floatPixels[idx]
                floatPixels[idx + 3] = if (hasAlpha) channelMaps["A"]!!.buffers!![y * width + x] else 1f
            }
        }

        val clamped = FloatArray(floatPixels.size)
        for (i in floatPixels.indices) {
            clamped[i] = max(0f, min(1f, floatPixels[i]))
        }

        val texture = Texture2D(
            width = width,
            height = height,
            format = TextureFormat.RGBA32F,
            magFilter = TextureFilter.LINEAR,
            minFilter = TextureFilter.LINEAR,
            textureName = "EXR_${width}x$height"
        )
        texture.wrapS = TextureWrap.CLAMP_TO_EDGE
        texture.wrapT = TextureWrap.CLAMP_TO_EDGE
        texture.setFloatData(clamped)
        return texture
    }

    private fun parseChannels(bytes: ByteArray): List<Channel> {
        val channels = mutableListOf<Channel>()
        var offset = 0
        while (offset < bytes.size) {
            val nameEnd = bytes.indexOfZero(offset)
            if (nameEnd == -1) break
            if (nameEnd == offset) {
                offset++
                break
            }
            val name = bytes.decodeToString(offset, nameEnd)
            offset = nameEnd + 1
            require(offset + 16 <= bytes.size) { "EXR channel header truncated" }
            val pixelType = readInt(bytes, offset)
            offset += 4
            offset += 4 // pLinear + reserved
            offset += 4 // xSampling
            offset += 4 // ySampling
            channels += Channel(name, pixelType)
        }
        return channels
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((bytes[offset + i].toLong() and 0xFFL) shl (8 * i))
        }
        return result
    }

    private fun readFloat(bytes: ByteArray, offset: Int): Float {
        val bits = readInt(bytes, offset)
        return Float.fromBits(bits)
    }

    private fun ByteArray.decodeToString(start: Int, end: Int): String {
        val slice = copyOfRange(start, end)
        return slice.decodeToString()
    }

    private fun ByteArray.indexOfZero(start: Int): Int {
        for (i in start until size) {
            if (this[i] == 0.toByte()) return i
        }
        return -1
    }

    private data class Channel(val name: String, val pixelType: Int, var buffers: FloatArray? = null)
}
