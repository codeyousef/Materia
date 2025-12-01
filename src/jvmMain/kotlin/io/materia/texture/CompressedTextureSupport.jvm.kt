package io.materia.texture

import org.lwjgl.vulkan.*

/**
 * JVM/Vulkan compressed texture support detection
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
        // Priority order for desktop platforms
        val preferredFormats = if (requireAlpha) {
            listOf(
                CompressedTextureFormat.BC7_RGBA,     // Best quality on desktop
                CompressedTextureFormat.BC3_RGBA,     // DXT5 fallback
                CompressedTextureFormat.BC2_RGBA,     // DXT3 fallback
                CompressedTextureFormat.BC1_RGBA      // DXT1 with alpha
            )
        } else {
            listOf(
                CompressedTextureFormat.BC7_RGBA,     // BC7 can handle RGB too
                CompressedTextureFormat.BC1_RGB,      // DXT1 for RGB
                CompressedTextureFormat.BC6H_RGB,     // HDR support
                CompressedTextureFormat.BC5_RG        // For normal maps
            )
        }

        return preferredFormats.firstOrNull { it in cachedSupportedFormats }
    }

    private fun detectSupportedFormats(): Set<CompressedTextureFormat> {
        val formats = mutableSetOf<CompressedTextureFormat>()

        // On desktop platforms (Windows, Linux, macOS), BC formats are widely supported
        // These are standard on all modern desktop GPUs

        // BC formats (always supported on desktop)
        formats.add(CompressedTextureFormat.BC1_RGB)
        formats.add(CompressedTextureFormat.BC1_RGBA)
        formats.add(CompressedTextureFormat.BC2_RGBA)
        formats.add(CompressedTextureFormat.BC3_RGBA)
        formats.add(CompressedTextureFormat.BC4_R)
        formats.add(CompressedTextureFormat.BC5_RG)

        // BC6H and BC7 (supported on DX11+ hardware)
        if (isModernGPU()) {
            formats.add(CompressedTextureFormat.BC6H_RGB)
            formats.add(CompressedTextureFormat.BC7_RGBA)
        }

        // ETC2 is mandatory in Vulkan
        formats.add(CompressedTextureFormat.ETC2_RGB)
        formats.add(CompressedTextureFormat.ETC2_RGBA)
        formats.add(CompressedTextureFormat.ETC2_RGB_A1)

        // ASTC support varies but is becoming more common
        if (hasASTCSupport()) {
            formats.add(CompressedTextureFormat.ASTC_4x4)
            formats.add(CompressedTextureFormat.ASTC_5x5)
            formats.add(CompressedTextureFormat.ASTC_6x6)
            formats.add(CompressedTextureFormat.ASTC_8x8)
            formats.add(CompressedTextureFormat.ASTC_10x10)
            formats.add(CompressedTextureFormat.ASTC_12x12)
        }

        // PVRTC is rare on desktop but check anyway
        if (hasPVRTCSupport()) {
            formats.add(CompressedTextureFormat.PVRTC_2BPP_RGB)
            formats.add(CompressedTextureFormat.PVRTC_2BPP_RGBA)
            formats.add(CompressedTextureFormat.PVRTC_4BPP_RGB)
            formats.add(CompressedTextureFormat.PVRTC_4BPP_RGBA)
        }

        return formats
    }

    private fun isModernGPU(): Boolean {
        // Check for DX11+ level hardware
        // Desktop platforms with Vulkan support meet this requirement
        return true
    }

    private fun hasASTCSupport(): Boolean {
        // ASTC is optional but increasingly common
        // Would need to query Vulkan physical device features
        return false  // Conservative default
    }

    private fun hasPVRTCSupport(): Boolean {
        // PVRTC is mainly for PowerVR GPUs (mobile)
        return false
    }
}