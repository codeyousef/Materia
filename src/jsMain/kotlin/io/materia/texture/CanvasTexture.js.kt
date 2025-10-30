package io.materia.texture

import io.materia.renderer.TextureFormat
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

/**
 * JavaScript implementation of CanvasTexture using HTMLCanvasElement
 */
actual class CanvasTexture private actual constructor(
    actual override val width: Int,
    actual override val height: Int
) : Texture() {

    private val canvas: HTMLCanvasElement = (document.createElement("canvas") as? HTMLCanvasElement
        ?: throw IllegalStateException("Failed to create canvas element")).apply {
        this.width = width
        this.height = height
    }

    private val context: CanvasRenderingContext2D =
        canvas.getContext("2d") as? CanvasRenderingContext2D
            ?: throw IllegalStateException("Failed to get 2D rendering context")

    // Override parent properties to work around Kotlin compiler bug with expect/actual inheritance
    override var name: String
        get() = super.name
        set(value) {
            super.name = value
        }

    override var format: TextureFormat
        get() = super.format
        set(value) {
            super.format = value
        }

    override var generateMipmaps: Boolean
        get() = super.generateMipmaps
        set(value) {
            super.generateMipmaps = value
        }

    override var needsUpdate: Boolean
        get() = super.needsUpdate
        set(value) {
            super.needsUpdate = value
        }

    actual companion object {
        actual operator fun invoke(width: Int, height: Int): CanvasTexture {
            val texture = CanvasTexture(width, height)
            texture.format = TextureFormat.RGBA8
            texture.generateMipmaps = false
            texture.needsUpdate = true
            texture.name = "CanvasTexture"
            texture.context.imageSmoothingEnabled = true
            return texture
        }
    }

    /**
     * Clear canvas to solid color
     */
    actual fun clear(r: Float, g: Float, b: Float, a: Float) {
        context.save()
        context.globalCompositeOperation = "source-over"
        context.fillStyle =
            "rgba(${(r * 255).toInt()}, ${(g * 255).toInt()}, ${(b * 255).toInt()}, $a)"
        context.fillRect(0.0, 0.0, width.toDouble(), height.toDouble())
        context.restore()
        markTextureNeedsUpdate()
    }

    /**
     * Get the 2D rendering context
     */
    actual fun getContext(): Any = context

    /**
     * Update texture from canvas content
     */
    actual fun update() {
        // In WebGL, this would trigger texture upload from canvas
        needsUpdate = true
        version++
    }

    /**
     * Get the HTML canvas element
     */
    fun getCanvas(): HTMLCanvasElement = canvas

    /**
     * Draw text on the canvas
     */
    fun drawText(
        text: String,
        x: Double,
        y: Double,
        font: String = "16px Arial",
        color: String = "white"
    ) {
        context.font = font
        context.fillStyle = color
        context.fillText(text, x, y)
        markTextureNeedsUpdate()
    }

    /**
     * Draw a rectangle on the canvas
     */
    fun drawRect(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        color: String = "white",
        fill: Boolean = true
    ) {
        if (fill) {
            context.fillStyle = color
            context.fillRect(x, y, width, height)
        } else {
            context.strokeStyle = color
            context.strokeRect(x, y, width, height)
        }
        markTextureNeedsUpdate()
    }

    /**
     * Draw a circle on the canvas
     */
    fun drawCircle(
        x: Double,
        y: Double,
        radius: Double,
        color: String = "white",
        fill: Boolean = true
    ) {
        context.beginPath()
        context.arc(x, y, radius, 0.0, 2 * kotlin.math.PI)
        if (fill) {
            context.fillStyle = color
            context.fill()
        } else {
            context.strokeStyle = color
            context.stroke()
        }
        markTextureNeedsUpdate()
    }

    override fun dispose() {
        super.dispose()
        // Canvas will be garbage collected
    }

    actual override fun clone(): Texture {
        val cloned = CanvasTexture(width, height)
        cloned.copy(this)

        // Copy canvas content
        val ctx = cloned.context
        ctx.drawImage(canvas, 0.0, 0.0)

        return cloned
    }
}