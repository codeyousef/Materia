package io.materia.texture

import io.materia.core.math.Color
import kotlin.math.roundToInt

internal class VolumeTextureSampler private constructor(
    private val width: Int,
    private val height: Int,
    private val depth: Int,
    private val byteData: ByteArray?,
    private val floatData: FloatArray?,
    private val intData: IntArray?
) {

    companion object {
        fun from(texture: Data3DTexture): VolumeTextureSampler = VolumeTextureSampler(
            width = texture.width,
            height = texture.height,
            depth = texture.depth,
            byteData = texture.getData().takeIf { it.isNotEmpty() },
            floatData = texture.getFloatData(),
            intData = texture.getIntData()
        )
    }

    fun sampleLocalPosition(x: Float, y: Float, z: Float): Color = sampleNormalized(
        x = x * 0.5f + 0.5f,
        y = y * 0.5f + 0.5f,
        z = z * 0.5f + 0.5f
    )

    fun sampleNormalized(x: Float, y: Float, z: Float): Color {
        val sampleX = resolveCoordinate(x, width)
        val sampleY = resolveCoordinate(y, height)
        val sampleZ = resolveCoordinate(z, depth)
        val index = (((sampleZ * height) + sampleY) * width + sampleX) * 4

        floatData?.let { data ->
            if (index + 3 < data.size) {
                return Color(
                    data[index].coerceIn(0f, 1f),
                    data[index + 1].coerceIn(0f, 1f),
                    data[index + 2].coerceIn(0f, 1f),
                    data[index + 3].coerceIn(0f, 1f)
                )
            }
        }

        intData?.let { data ->
            if (index + 3 < data.size) {
                return Color(
                    data[index].coerceIn(0, 255) / 255f,
                    data[index + 1].coerceIn(0, 255) / 255f,
                    data[index + 2].coerceIn(0, 255) / 255f,
                    data[index + 3].coerceIn(0, 255) / 255f
                )
            }
        }

        byteData?.let { data ->
            if (index + 3 < data.size) {
                return Color(
                    data[index].toUByte().toFloat() / 255f,
                    data[index + 1].toUByte().toFloat() / 255f,
                    data[index + 2].toUByte().toFloat() / 255f,
                    data[index + 3].toUByte().toFloat() / 255f
                )
            }
        }

        return Color.WHITE
    }

    private fun resolveCoordinate(value: Float, size: Int): Int {
        if (size <= 1) return 0
        return (value.coerceIn(0f, 1f) * (size - 1).toFloat()).roundToInt().coerceIn(0, size - 1)
    }
}