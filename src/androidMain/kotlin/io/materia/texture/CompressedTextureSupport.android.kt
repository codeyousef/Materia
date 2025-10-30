package io.materia.texture

actual object CompressedTextureSupport {
    actual fun isFormatSupported(format: CompressedTextureFormat): Boolean = false

    actual fun getSupportedFormats(): Set<CompressedTextureFormat> = emptySet()

    actual fun getBestFormat(requireAlpha: Boolean): CompressedTextureFormat? = null
}
