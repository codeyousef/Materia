/**
 * T035: Screenshot Capture (JVM)
 * Feature: 019-we-should-not
 *
 * Capture framebuffer as PNG for visual regression testing.
 * Uses Vulkan backend's capture capabilities.
 */

package io.materia.renderer.testing

import java.awt.image.BufferedImage
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO

/**
 * Screenshot capture utility for JVM platform.
 *
 * Uses Vulkan backend's buffer readback to capture framebuffer and save as PNG.
 * The VulkanRenderer provides the actual GPU buffer capture; this utility
 * handles image conversion and file I/O.
 *
 * Usage:
 * ```kotlin
 * // Use via VulkanRenderer's built-in screenshot capture
 * // Or convert a captured buffer:
 * val buffer: ByteBuffer = vulkanRenderer.captureFramebuffer()
 * ScreenshotCapture.saveBuffer(buffer, width, height, "output.png")
 * ```
 */
object ScreenshotCapture {

    /**
     * Save a captured framebuffer buffer to PNG.
     *
     * @param buffer Pixel data buffer (RGBA or BGRA format)
     * @param width Framebuffer width in pixels
     * @param height Framebuffer height in pixels
     * @param outputPath Output file path (e.g., "build/visual-regression/vulkan-simple-cube.png")
     * @param isBGRA Whether the buffer is BGRA format (Vulkan default) instead of RGBA
     * @param flipVertical Whether to flip the image vertically (Vulkan has top-left origin)
     * @return true on success, false on failure
     */
    fun saveBuffer(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        outputPath: String,
        isBGRA: Boolean = true,
        flipVertical: Boolean = false
    ): Boolean {
        return try {
            // Convert to BufferedImage
            val image = bufferToImage(buffer, width, height, isBGRA, flipVertical)

            // Ensure output directory exists
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            // Save as PNG
            ImageIO.write(image, "PNG", outputFile)

            println("[ScreenshotCapture] Saved: $outputPath")
            true
        } catch (e: Exception) {
            System.err.println("[ScreenshotCapture] Failed to save screenshot: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Convert ByteBuffer to BufferedImage.
     *
     * @param buffer Pixel data buffer
     * @param width Image width
     * @param height Image height
     * @param isBGRA Whether buffer is BGRA format (swap R and B)
     * @param flipVertical Whether to flip vertically
     * @return BufferedImage with pixel data
     */
    fun bufferToImage(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        isBGRA: Boolean = true,
        flipVertical: Boolean = false
    ): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            val srcY = if (flipVertical) (height - 1 - y) else y
            for (x in 0 until width) {
                val i = (srcY * width + x) * 4
                
                val c0 = buffer.get(i).toInt() and 0xFF
                val c1 = buffer.get(i + 1).toInt() and 0xFF
                val c2 = buffer.get(i + 2).toInt() and 0xFF
                val a = buffer.get(i + 3).toInt() and 0xFF
                
                // BGRA vs RGBA
                val r = if (isBGRA) c2 else c0
                val g = c1
                val b = if (isBGRA) c0 else c2

                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                image.setRGB(x, y, argb)
            }
        }

        return image
    }

    /**
     * Save buffer with metadata annotation.
     *
     * Saves screenshot with scene name, backend, and timestamp.
     *
     * @param buffer Captured framebuffer pixel data
     * @param width Framebuffer width
     * @param height Framebuffer height
     * @param sceneName Scene identifier (e.g., "simple-cube")
     * @param backend Backend identifier (e.g., "vulkan")
     * @param outputDir Output directory (default: "build/visual-regression")
     * @param isBGRA Whether buffer is BGRA format
     * @return Saved file path or null on failure
     */
    fun saveBufferWithMetadata(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        sceneName: String,
        backend: String,
        outputDir: String = "build/visual-regression",
        isBGRA: Boolean = true
    ): String? {
        val timestamp = System.currentTimeMillis()
        val filename = "${backend}-${sceneName}-${timestamp}.png"
        val outputPath = "$outputDir/$filename"

        return if (saveBuffer(buffer, width, height, outputPath, isBGRA)) {
            outputPath
        } else {
            null
        }
    }

    /**
     * Save buffer for visual regression testing.
     *
     * Uses consistent naming without timestamp for comparison.
     *
     * @param buffer Captured framebuffer pixel data
     * @param width Framebuffer width
     * @param height Framebuffer height
     * @param sceneName Scene identifier
     * @param backend Backend identifier
     * @param outputDir Output directory
     * @param isBGRA Whether buffer is BGRA format
     * @return Saved file path or null on failure
     */
    fun saveBufferForRegression(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        sceneName: String,
        backend: String,
        outputDir: String = "build/visual-regression",
        isBGRA: Boolean = true
    ): String? {
        val filename = "${backend}-${sceneName}.png"
        val outputPath = "$outputDir/$filename"

        return if (saveBuffer(buffer, width, height, outputPath, isBGRA)) {
            outputPath
        } else {
            null
        }
    }
}
