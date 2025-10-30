package io.materia.loader

import io.kreekt.texture.Texture2D
import kotlinx.coroutines.test.runTest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EXRLoaderTest {

    @Test
    fun `load minimal exr`() = runTest {
        val data = buildMinimalExr(r = 0.5f, g = 0.25f, b = 0.75f)
        val loader = EXRLoader(InMemoryResolver(mapOf("pixel.exr" to data)))
        val texture = loader.load("pixel.exr")
        assertIs<Texture2D>(texture)
        assertEquals(1, texture.width)
        val rgb = texture.getFloatData() ?: error("Expected float data")
        assertEquals(0.5f.roundToInt(), rgb[0].roundToInt())
    }

    private fun buildMinimalExr(r: Float, g: Float, b: Float): ByteArray {
        val writer = ByteArrayBuilder()
        writer.writeInt(0x762f3101)
        writer.writeInt(2)

        // channels attribute (R, G, B)
        val channels = ByteArrayBuilder().apply {
            writeChannel("R")
            writeChannel("G")
            writeChannel("B")
            writeByte(0)
        }.toByteArray()
        writer.writeAttribute("channels", "chlist", channels)

        // compression (no compression)
        writer.writeAttribute("compression", "compression", byteArrayOf(0))

        // data window {0,0,0,0}
        val dataWindow = ByteArrayBuilder().apply {
            writeInt(0); writeInt(0); writeInt(0); writeInt(0)
        }.toByteArray()
        writer.writeAttribute("dataWindow", "box2i", dataWindow)
        writer.writeAttribute("lineOrder", "lineOrder", byteArrayOf(0))
        writer.writeByte(0) // end of attributes

        val headerSize = writer.size
        val lineOffsetStart = headerSize + 8
        val pixelDataOffset = lineOffsetStart + 8

        writer.writeLong(pixelDataOffset.toLong())

        val pixel = FloatArray(3)
        pixel[0] = r
        pixel[1] = g
        pixel[2] = b

        val dataBuilder = ByteArrayBuilder()
        dataBuilder.writeInt(0) // y coordinate
        dataBuilder.writeInt(3 * 4) // data size
        pixel.forEach { dataBuilder.writeFloat(it) }

        writer.writeBytes(ByteArray(pixelDataOffset - writer.size))
        writer.writeBytes(dataBuilder.toByteArray())
        return writer.toByteArray()
    }

    private fun ByteArrayBuilder.writeChannel(name: String) {
        writeCString(name)
        writeInt(2) // FLOAT
        writeByte(0) // pLinear
        writeByte(0); writeByte(0); writeByte(0)
        writeInt(1); writeInt(1)
    }

    private class ByteArrayBuilder {
        private val data = mutableListOf<Byte>()
        val size: Int get() = data.size

        fun writeByte(value: Int) { data.add(value.toByte()) }
        fun writeByte(value: Byte) { data.add(value) }
        fun writeBytes(bytes: ByteArray) { data.addAll(bytes.toList()) }
        fun writeInt(value: Int) {
            for (i in 0 until 4) data.add(((value ushr (8 * i)) and 0xFF).toByte())
        }

        fun writeLong(value: Long) {
            for (i in 0 until 8) data.add(((value ushr (8 * i)) and 0xFF).toByte())
        }

        fun writeFloat(value: Float) = writeInt(value.toBits())
        fun writeCString(value: String) { writeBytes(value.encodeToByteArray()); writeByte(0) }

        fun writeAttribute(name: String, type: String, payload: ByteArray) {
            writeCString(name)
            writeCString(type)
            writeInt(payload.size)
            writeBytes(payload)
        }

        fun toByteArray(): ByteArray = data.toByteArray()
    }

    private class InMemoryResolver(private val files: Map<String, ByteArray>) : AssetResolver {
        override suspend fun load(uri: String, basePath: String?): ByteArray =
            files[uri] ?: error("Missing asset: $uri")
    }
}
