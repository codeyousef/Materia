package io.materia.renderer

/**
 * Render target interface for off-screen rendering.
 * Used for shadow maps, reflections, post-processing, and cube map rendering.
 */
interface RenderTarget {
    /** Width of the render target in pixels */
    val width: Int
    
    /** Height of the render target in pixels */
    val height: Int
    
    /** Color texture attachment */
    val texture: Texture?
    
    /** Depth texture attachment (optional) */
    val depthTexture: Texture?
    
    /** Whether depth buffer is enabled */
    val depthBuffer: Boolean
    
    /** Whether stencil buffer is enabled */
    val stencilBuffer: Boolean
    
    /** Resize the render target */
    fun setSize(width: Int, height: Int)
    
    /** Release GPU resources */
    fun dispose()
}

/**
 * Default render target implementation for 2D rendering.
 */
open class DefaultRenderTarget(
    override var width: Int,
    override var height: Int,
    override val depthBuffer: Boolean = true,
    override val stencilBuffer: Boolean = false,
    override val texture: Texture? = null,
    override val depthTexture: Texture? = null
) : RenderTarget {
    
    override fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }
    
    override fun dispose() {
        texture?.dispose()
        depthTexture?.dispose()
    }
}
