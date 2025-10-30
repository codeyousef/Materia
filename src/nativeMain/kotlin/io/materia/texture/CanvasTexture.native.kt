package io.materia.texture

import io.materia.renderer.TextureFormat

/**
 * Native implementation of CanvasTexture (stub for Native platforms)
 */
actual class CanvasTexture private actual constructor(
    actual override val width: Int,
    actual override val height: Int
) : Texture() {

    private val imageData = ByteArray(width * height * 4)

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
            return texture
        }
    }

    /**
     * Clear canvas to solid color
     */
    actual fun clear(r: Float, g: Float, b: Float, a: Float) {
        val rByte = (r * 255).toInt().toByte()
        val gByte = (g * 255).toInt().toByte()
        val bByte = (b * 255).toInt().toByte()
        val aByte = (a * 255).toInt().toByte()

        for (i in imageData.indices step 4) {
            imageData[i] = rByte
            imageData[i + 1] = gByte
            imageData[i + 2] = bByte
            imageData[i + 3] = aByte
        }
        markTextureNeedsUpdate()
    }

    /**
     * Get the drawing context - returns ByteArray on Native
     */
    actual fun getContext(): Any = imageData

    /**
     * Update texture from canvas content
     */
    actual fun update() {
        needsUpdate = true
        version++
    }

    /**
     * Get image data for GPU upload
     */
    fun getImageData(): ByteArray = imageData

    actual override fun clone(): Texture {
        val cloned = CanvasTexture(width, height)
        cloned.copy(this)
        imageData.copyInto(cloned.imageData)
        return cloned
    }
}
