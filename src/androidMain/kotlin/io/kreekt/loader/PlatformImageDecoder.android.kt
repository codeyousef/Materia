package io.kreekt.loader

import android.graphics.BitmapFactory

internal actual object PlatformImageDecoder {
    actual suspend fun decode(bytes: ByteArray): DecodedImage {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("Unsupported image format")

        val width = bitmap.width
        val height = bitmap.height
        val rgba = ByteArray(width * height * 4)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var offset = 0
        for (pixel in pixels) {
            rgba[offset] = ((pixel shr 16) and 0xFF).toByte()
            rgba[offset + 1] = ((pixel shr 8) and 0xFF).toByte()
            rgba[offset + 2] = (pixel and 0xFF).toByte()
            rgba[offset + 3] = ((pixel shr 24) and 0xFF).toByte()
            offset += 4
        }

        bitmap.recycle()

        return DecodedImage(width, height, rgba)
    }
}
