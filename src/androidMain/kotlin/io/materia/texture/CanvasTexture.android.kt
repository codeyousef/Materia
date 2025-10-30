package io.materia.texture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import io.materia.renderer.TextureFormat

actual class CanvasTexture private actual constructor(
    width: Int,
    height: Int
) : Texture() {

    private val internalWidth = width.coerceAtLeast(1)
    private val internalHeight = height.coerceAtLeast(1)

    private val bitmap: Bitmap?
    private val canvas: Canvas?
    private val fallbackPixels: ByteArray?
    private val fallbackContext: CanvasFallbackContext?

    actual override val width: Int = internalWidth
    actual override val height: Int = internalHeight

    init {
        var createdBitmap: Bitmap? = null
        var createdCanvas: Canvas? = null
        var pixels: ByteArray? = null
        var imageRef: Any? = null

        try {
            createdBitmap = Bitmap.createBitmap(
                internalWidth,
                internalHeight,
                Bitmap.Config.ARGB_8888
            )
            createdCanvas = Canvas(createdBitmap)
            imageRef = createdBitmap
        } catch (runtime: RuntimeException) {
            val message = runtime.message.orEmpty()
            val isAndroidStub = message == "Stub!" ||
                    message.contains("not mocked", ignoreCase = true)

            if (!isAndroidStub) throw runtime

            pixels = ByteArray(internalWidth * internalHeight * 4)
            imageRef = pixels
        }

        bitmap = createdBitmap
        canvas = createdCanvas
        fallbackPixels = pixels
        fallbackContext = pixels?.let {
            CanvasFallbackContext(it, internalWidth, internalHeight)
        }

        image = imageRef
        initializeAsCanvasTexture()
        format = TextureFormat.RGBA8
        generateMipmaps = false
        needsUpdate = true
    }

    actual companion object {
        actual operator fun invoke(width: Int, height: Int): CanvasTexture =
            CanvasTexture(width, height)
    }

    actual fun clear(r: Float, g: Float, b: Float, a: Float) {
        val red = r.coerceIn(0f, 1f)
        val green = g.coerceIn(0f, 1f)
        val blue = b.coerceIn(0f, 1f)
        val alpha = a.coerceIn(0f, 1f)

        if (bitmap != null && canvas != null) {
            val color = Color.argb(
                (alpha * 255f).toInt(),
                (red * 255f).toInt(),
                (green * 255f).toInt(),
                (blue * 255f).toInt()
            )
            canvas.drawColor(color)
        } else {
            val rByte = (red * 255f).toInt().toByte()
            val gByte = (green * 255f).toInt().toByte()
            val bByte = (blue * 255f).toInt().toByte()
            val aByte = (alpha * 255f).toInt().toByte()
            fallbackPixels?.let { pixels ->
                for (index in pixels.indices step 4) {
                    pixels[index] = rByte
                    pixels[index + 1] = gByte
                    pixels[index + 2] = bByte
                    pixels[index + 3] = aByte
                }
            }
        }
        markTextureNeedsUpdate()
    }

    actual fun getContext(): Any = canvas ?: fallbackContext!!

    actual fun update() {
        markTextureNeedsUpdate()
    }

    actual override fun clone(): Texture {
        val clone = CanvasTexture(width, height)
        clone.copy(this)

        when {
            bitmap != null && clone.bitmap != null && clone.canvas != null -> {
                clone.canvas.drawBitmap(bitmap, 0f, 0f, null)
            }

            fallbackPixels != null && clone.fallbackPixels != null -> {
                fallbackPixels.copyInto(clone.fallbackPixels)
            }
        }
        return clone
    }

    private class CanvasFallbackContext(
        val pixels: ByteArray,
        val width: Int,
        val height: Int
    )
}
