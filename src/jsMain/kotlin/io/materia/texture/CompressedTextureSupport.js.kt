package io.materia.texture

import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement
import org.khronos.webgl.WebGLRenderingContext

/**
 * JavaScript/WebGL compressed texture support detection
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
        // Priority order for web platforms
        val preferredFormats = if (requireAlpha) {
            listOf(
                CompressedTextureFormat.ASTC_6x6,
                CompressedTextureFormat.BC7_RGBA,
                CompressedTextureFormat.ETC2_RGBA,
                CompressedTextureFormat.BC3_RGBA,
                CompressedTextureFormat.PVRTC_4BPP_RGBA
            )
        } else {
            listOf(
                CompressedTextureFormat.ASTC_6x6,
                CompressedTextureFormat.BC7_RGBA,
                CompressedTextureFormat.ETC2_RGB,
                CompressedTextureFormat.BC1_RGB,
                CompressedTextureFormat.PVRTC_4BPP_RGB
            )
        }

        return preferredFormats.firstOrNull { it in cachedSupportedFormats }
    }

    private fun detectSupportedFormats(): Set<CompressedTextureFormat> {
        val formats = mutableSetOf<CompressedTextureFormat>()

        // Create a temporary WebGL context to check extensions
        val canvas = document.createElement("canvas") as? HTMLCanvasElement ?: return formats
        val gl = canvas.getContext("webgl") as? WebGLRenderingContext ?: return formats

        // Check for extension support
        val extensions = gl.getSupportedExtensions()?.toList() ?: return formats

        // BC formats (DXT/S3TC)
        if (extensions.any { ext ->
                ext.contains("s3tc", ignoreCase = true) ||
                        ext.contains("dxt", ignoreCase = true)
            }) {
            formats.add(CompressedTextureFormat.BC1_RGB)
            formats.add(CompressedTextureFormat.BC1_RGBA)
            formats.add(CompressedTextureFormat.BC2_RGBA)
            formats.add(CompressedTextureFormat.BC3_RGBA)
        }

        // BC7 (BPTC)
        if (extensions.any { ext -> ext.contains("bptc", ignoreCase = true) }) {
            formats.add(CompressedTextureFormat.BC7_RGBA)
        }

        // ETC formats
        if (extensions.any { ext -> ext.contains("etc1", ignoreCase = true) }) {
            formats.add(CompressedTextureFormat.ETC1_RGB)
        }
        if (extensions.any { ext ->
                ext.contains("etc", ignoreCase = true) &&
                        !ext.contains("etc1", ignoreCase = true)
            }) {
            formats.add(CompressedTextureFormat.ETC2_RGB)
            formats.add(CompressedTextureFormat.ETC2_RGBA)
            formats.add(CompressedTextureFormat.ETC2_RGB_A1)
        }

        // ASTC formats
        if (extensions.any { ext -> ext.contains("astc", ignoreCase = true) }) {
            formats.add(CompressedTextureFormat.ASTC_4x4)
            formats.add(CompressedTextureFormat.ASTC_5x5)
            formats.add(CompressedTextureFormat.ASTC_6x6)
            formats.add(CompressedTextureFormat.ASTC_8x8)
            formats.add(CompressedTextureFormat.ASTC_10x10)
            formats.add(CompressedTextureFormat.ASTC_12x12)
        }

        // PVRTC formats
        if (extensions.any { ext -> ext.contains("pvrtc", ignoreCase = true) }) {
            formats.add(CompressedTextureFormat.PVRTC_2BPP_RGB)
            formats.add(CompressedTextureFormat.PVRTC_2BPP_RGBA)
            formats.add(CompressedTextureFormat.PVRTC_4BPP_RGB)
            formats.add(CompressedTextureFormat.PVRTC_4BPP_RGBA)
        }

        return formats
    }
}