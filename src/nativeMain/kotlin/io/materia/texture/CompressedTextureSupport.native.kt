package io.materia.texture

/**
 * Native platform compressed texture support detection
 */
actual object CompressedTextureSupport {

    private val cachedSupportedFormats: Set<CompressedTextureFormat> by lazy {
        detectSupportedFormats()
    }

    actual fun isFormatSupported(format: CompressedTextureFormat): Boolean {
        return format in cachedSupportedFormats
    }

    actual fun getSupportedFormats(): Set<CompressedTextureFormat> {
        return cachedSupportedFormats
    }

    actual fun getBestFormat(requireAlpha: Boolean): CompressedTextureFormat? {
        // Priority order for native platforms (varies by OS)
        val preferredFormats = if (requireAlpha) {
            listOf(
                CompressedTextureFormat.BC7_RGBA,     // Best quality on desktop
                CompressedTextureFormat.BC3_RGBA,     // DXT5 fallback
                CompressedTextureFormat.BC2_RGBA,     // DXT3 fallback
                CompressedTextureFormat.BC1_RGBA,     // DXT1 with alpha
                CompressedTextureFormat.ETC2_RGBA     // Mobile fallback
            )
        } else {
            listOf(
                CompressedTextureFormat.BC7_RGBA,     // BC7 can handle RGB too
                CompressedTextureFormat.BC1_RGB,      // DXT1 for RGB
                CompressedTextureFormat.BC6H_RGB,     // HDR support
                CompressedTextureFormat.BC5_RG,       // For normal maps
                CompressedTextureFormat.ETC2_RGB      // Mobile fallback
            )
        }

        return preferredFormats.firstOrNull { it in cachedSupportedFormats }
    }

    private fun detectSupportedFormats(): Set<CompressedTextureFormat> {
        val formats = mutableSetOf<CompressedTextureFormat>()

        // BC formats (common on desktop native platforms)
        formats.add(CompressedTextureFormat.BC1_RGB)
        formats.add(CompressedTextureFormat.BC1_RGBA)
        formats.add(CompressedTextureFormat.BC2_RGBA)
        formats.add(CompressedTextureFormat.BC3_RGBA)
        formats.add(CompressedTextureFormat.BC4_R)
        formats.add(CompressedTextureFormat.BC5_RG)
        formats.add(CompressedTextureFormat.BC6H_RGB)
        formats.add(CompressedTextureFormat.BC7_RGBA)

        // ETC2 is mandatory in Vulkan
        formats.add(CompressedTextureFormat.ETC2_RGB)
        formats.add(CompressedTextureFormat.ETC2_RGBA)
        formats.add(CompressedTextureFormat.ETC2_RGB_A1)

        return formats
    }
}
