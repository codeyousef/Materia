package io.materia.loader

import io.materia.texture.Texture2D
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KTX2LoaderTest {

    @Test
    fun `load uncompressed ktx2 texture`() = runTest {
        val pixelData = byteArrayOf(
            -1, 0, 0, -1,    // red
            0, -1, 0, -1,    // green
            0, 0, -1, -1,    // blue
            -1, -1, -1, -1   // white
        )
        val file = buildKtx2(width = 2, height = 2, data = pixelData)
        val loader = KTX2Loader(InMemoryResolver(mapOf("tex.ktx2" to file)))
        val texture = loader.load("tex.ktx2")
        assertIs<Texture2D>(texture)
        assertEquals(2, texture.width)
        assertEquals(2, texture.height)
    }

    private fun buildKtx2(width: Int, height: Int, data: ByteArray): ByteArray {
        val writer = ByteArrayWriter()
        val identifier = byteArrayOf(
            0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A
        )
        writer.writeBytes(identifier)
        writer.writeUInt32(37u)                // vkFormat (RGBA8)
        writer.writeUInt32(1u)                 // typeSize
        writer.writeUInt32(width.toUInt())     // pixelWidth
        writer.writeUInt32(height.toUInt())    // pixelHeight
        writer.writeUInt32(0u)                 // pixelDepth
        writer.writeUInt32(0u)                 // layerCount
        writer.writeUInt32(1u)                 // faceCount
        writer.writeUInt32(1u)                 // levelCount
        writer.writeUInt32(0u)                 // supercompressionScheme
        writer.writeUInt32(0u)                 // dfdByteOffset
        writer.writeUInt32(0u)                 // dfdByteLength
        writer.writeUInt32(0u)                 // kvDataByteOffset
        writer.writeUInt32(0u)                 // kvDataByteLength
        writer.writeUInt64(0u)                 // sgdByteOffset
        writer.writeUInt64(0u)                 // sgdByteLength

        require(writer.size == 80) { "Unexpected KTX2 header size: ${writer.size}" }

        val levelOffset = writer.size + 24
        writer.writeUInt64(levelOffset.toULong())
        writer.writeUInt64(data.size.toULong())
        writer.writeUInt64(data.size.toULong())
        writer.writeBytes(data)
        return writer.toByteArray()
    }

    private class ByteArrayWriter {
        private val bytes = mutableListOf<Byte>()
        val size: Int get() = bytes.size

        fun writeByte(value: Int) {
            bytes.add((value and 0xFF).toByte())
        }

        fun writeBytes(value: ByteArray) {
            bytes.addAll(value.asList())
        }

        fun writeUInt32(value: UInt) {
            repeat(4) { i -> writeByte(((value shr (8 * i)) and 0xFFu).toInt()) }
        }

        fun writeUInt64(value: ULong) {
            repeat(8) { i -> writeByte(((value shr (8 * i)) and 0xFFu).toInt()) }
        }

        fun toByteArray(): ByteArray = bytes.toByteArray()
    }

    private class InMemoryResolver(private val files: Map<String, ByteArray>) : AssetResolver {
        override suspend fun load(uri: String, basePath: String?): ByteArray =
            files[uri] ?: error("Missing asset: $uri")
    }
}
