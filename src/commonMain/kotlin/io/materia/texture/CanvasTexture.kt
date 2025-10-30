package io.materia.texture

/**
 * CanvasTexture - Texture from HTML Canvas or similar drawing surface
 * T079 - Canvas rendering as texture source
 *
 * Uses canvas drawing surface as texture source, enabling dynamic texture creation
 * through 2D drawing operations.
 */
expect class CanvasTexture private constructor(
    width: Int,
    height: Int
) : Texture {

    override val width: Int
    override val height: Int

    companion object {
        operator fun invoke(width: Int = 256, height: Int = 256): CanvasTexture
    }

    /**
     * Clear the canvas to a solid color
     */
    fun clear(r: Float = 0f, g: Float = 0f, b: Float = 0f, a: Float = 1f)

    /**
     * Get the drawing context for this canvas
     * Platform-specific return type
     */
    fun getContext(): Any

    /**
     * Update texture from current canvas content
     */
    fun update()

    /**
     * Clone this canvas texture
     */
    override fun clone(): Texture
}