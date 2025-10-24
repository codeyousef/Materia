package io.kreekt.texture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color

actual class CanvasTexture private actual constructor(
    width: Int,
    height: Int
) : Texture() {

    private val bitmap: Bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    private val canvas: Canvas = Canvas(bitmap)

    actual override val width: Int = bitmap.width
    actual override val height: Int = bitmap.height

    init {
        image = bitmap
        initializeAsCanvasTexture()
    }

    actual companion object {
        actual operator fun invoke(width: Int, height: Int): CanvasTexture = CanvasTexture(width, height)
    }

    actual fun clear(r: Float, g: Float, b: Float, a: Float) {
        val color = Color.argb(
            (a.coerceIn(0f, 1f) * 255f).toInt(),
            (r.coerceIn(0f, 1f) * 255f).toInt(),
            (g.coerceIn(0f, 1f) * 255f).toInt(),
            (b.coerceIn(0f, 1f) * 255f).toInt()
        )
        bitmap.eraseColor(color)
        markTextureNeedsUpdate()
    }

    actual fun getContext(): Any = canvas

    actual fun update() {
        markTextureNeedsUpdate()
    }

    actual override fun clone(): Texture {
        val clone = CanvasTexture(width, height)
        clone.copy(this)
        return clone
    }
}
