package io.kreekt.loader

/**
 * CPU-side decoded image used to initialize textures prior to GPU upload.
 */
internal data class DecodedImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
)

internal expect object PlatformImageDecoder {
    fun decode(bytes: ByteArray): DecodedImage
}
