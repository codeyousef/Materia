package io.kreekt.loader

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

internal actual object PlatformImageDecoder {
    actual suspend fun decode(bytes: ByteArray): DecodedImage {
        val image = ImageIO.read(ByteArrayInputStream(bytes))
            ?: throw IllegalArgumentException("Unsupported image format")

        val width = image.width
        val height = image.height
        val rgba = ByteArray(width * height * 4)
        var offset = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                rgba[offset] = ((argb shr 16) and 0xFF).toByte()
                rgba[offset + 1] = ((argb shr 8) and 0xFF).toByte()
                rgba[offset + 2] = (argb and 0xFF).toByte()
                rgba[offset + 3] = ((argb shr 24) and 0xFF).toByte()
                offset += 4
            }
        }

        return DecodedImage(width, height, rgba)
    }
}
