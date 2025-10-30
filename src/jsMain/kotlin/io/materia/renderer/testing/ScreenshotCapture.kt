/**
 * T035: Screenshot Capture (JS)
 * Feature: 019-we-should-not
 *
 * Capture canvas framebuffer as PNG for visual regression testing.
 */

package io.materia.renderer.testing

import kotlinx.browser.document
import org.khronos.webgl.Uint8ClampedArray
import org.w3c.dom.HTMLCanvasElement
import org.w3c.files.Blob
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Screenshot capture utility for JS platform.
 *
 * Uses HTMLCanvasElement.toBlob() to capture framebuffer and download as PNG.
 *
 * Usage:
 * ```kotlin
 * // After rendering a frame
 * renderer.render(scene, camera)
 * ScreenshotCapture.capture(
 *     canvas = canvasElement,
 *     filename = "webgpu-simple-cube.png"
 * )
 * ```
 */
object ScreenshotCapture {

    /**
     * Capture current canvas framebuffer and download as PNG.
     *
     * @param canvas HTMLCanvasElement to capture
     * @param filename Output filename (e.g., "test.png")
     * @return Promise<Boolean> - true on success, false on failure
     */
    suspend fun capture(canvas: HTMLCanvasElement, filename: String): Boolean {
        return try {
            // Convert canvas to Blob (PNG format)
            val blob = canvasToBlob(canvas, "image/png")

            // Download blob as file
            downloadBlob(blob, filename)

            console.log("[ScreenshotCapture] Saved: $filename")
            true
        } catch (e: Throwable) {
            console.error("[ScreenshotCapture] Failed to capture: ${e.message}")
            false
        }
    }

    /**
     * Capture canvas as data URL (base64 PNG).
     *
     * Useful for in-memory comparison without downloading.
     *
     * @param canvas HTMLCanvasElement to capture
     * @return Data URL string (e.g., "data:image/png;base64,...")
     */
    fun captureAsDataURL(canvas: HTMLCanvasElement): String {
        return canvas.toDataURL("image/png")
    }

    /**
     * Capture with metadata annotation.
     *
     * Downloads screenshot with scene name, backend, and timestamp.
     *
     * @param canvas HTMLCanvasElement to capture
     * @param sceneName Scene identifier (e.g., "simple-cube")
     * @param backend Backend identifier (e.g., "webgpu", "webgl")
     * @return Promise<String?> - Filename or null on failure
     */
    suspend fun captureWithMetadata(
        canvas: HTMLCanvasElement,
        sceneName: String,
        backend: String
    ): String? {
        val timestamp = js("Date.now()") as Long
        val filename = "${backend}-${sceneName}-${timestamp}.png"

        return if (capture(canvas, filename)) {
            filename
        } else {
            null
        }
    }

    /**
     * Capture for visual regression testing.
     *
     * Uses consistent naming without timestamp for comparison.
     *
     * @param canvas HTMLCanvasElement to capture
     * @param sceneName Scene identifier
     * @param backend Backend identifier
     * @return Promise<String?> - Filename or null on failure
     */
    suspend fun captureForRegression(
        canvas: HTMLCanvasElement,
        sceneName: String,
        backend: String
    ): String? {
        val filename = "${backend}-${sceneName}.png"

        return if (capture(canvas, filename)) {
            filename
        } else {
            null
        }
    }

    /**
     * Convert canvas to Blob (PNG format).
     *
     * @param canvas HTMLCanvasElement to convert
     * @param mimeType MIME type (default: "image/png")
     * @return Blob containing PNG data
     */
    private suspend fun canvasToBlob(
        canvas: HTMLCanvasElement,
        mimeType: String = "image/png"
    ): Blob {
        return suspendCoroutine { cont ->
            canvas.toBlob({ blob: Blob? ->
                if (blob != null) {
                    cont.resume(blob)
                } else {
                    cont.resumeWithException(RuntimeException("Canvas toBlob returned null"))
                }
            }, mimeType)
        }
    }

    /**
     * Download Blob as file using browser download mechanism.
     *
     * Creates temporary anchor element and triggers download.
     *
     * @param blob Blob to download
     * @param filename Download filename
     */
    private fun downloadBlob(blob: Blob, filename: String) {
        // Create object URL for blob
        val url = js("URL.createObjectURL(blob)") as String

        // Create temporary anchor element
        val anchor = document.createElement("a").unsafeCast<org.w3c.dom.HTMLAnchorElement>()
        anchor.href = url
        anchor.download = filename

        // Trigger download
        document.body?.appendChild(anchor)
        anchor.click()

        // Cleanup
        document.body?.removeChild(anchor)
        js("URL.revokeObjectURL(url)")
    }

    /**
     * Get canvas pixels as Uint8ClampedArray (RGBA format).
     *
     * Useful for programmatic image comparison.
     *
     * @param canvas HTMLCanvasElement to read
     * @return Uint8ClampedArray with pixel data (width * height * 4 bytes)
     */
    fun getCanvasPixels(canvas: HTMLCanvasElement): Uint8ClampedArray {
        val context = canvas.getContext("2d").unsafeCast<org.w3c.dom.CanvasRenderingContext2D>()
        val imageData =
            context.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
        return imageData.data
    }

    /**
     * Compare two canvases for pixel equality.
     *
     * Simple pixel-perfect comparison (not SSIM).
     *
     * @param canvas1 First canvas
     * @param canvas2 Second canvas
     * @return true if all pixels match exactly
     */
    fun compareCanvases(canvas1: HTMLCanvasElement, canvas2: HTMLCanvasElement): Boolean {
        if (canvas1.width != canvas2.width || canvas1.height != canvas2.height) {
            return false
        }

        val pixels1 = getCanvasPixels(canvas1)
        val pixels2 = getCanvasPixels(canvas2)

        if (pixels1.length != pixels2.length) {
            return false
        }

        for (i in 0 until pixels1.length) {
            if (pixels1.asDynamic()[i] != pixels2.asDynamic()[i]) {
                return false
            }
        }

        return true
    }
}
