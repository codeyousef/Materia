package io.materia.postprocessing

import io.materia.core.Disposable

/**
 * Base class for all post-processing passes.
 * A pass represents a single rendering operation in the post-processing pipeline.
 */
abstract class Pass : Disposable {

    /**
     * Whether this pass is enabled and should be executed.
     */
    var enabled: Boolean = true

    /**
     * Whether this pass should swap read/write buffers after rendering.
     * Most passes need to swap to propagate results to next pass.
     */
    var needsSwap: Boolean = true

    /**
     * Whether to clear the render target before rendering.
     */
    var clear: Boolean = false

    /**
     * Whether this pass renders directly to screen (final pass).
     */
    var renderToScreen: Boolean = false

    /**
     * Current width of the pass in pixels.
     */
    protected var width: Int = 0
        private set

    /**
     * Current height of the pass in pixels.
     */
    protected var height: Int = 0
        private set

    /**
     * Sets the size of this pass.
     * @param width New width in pixels
     * @param height New height in pixels
     */
    open fun setSize(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /**
     * Renders this pass.
     * @param renderer The renderer instance
     * @param writeBuffer Target buffer to write to
     * @param readBuffer Source buffer to read from
     * @param deltaTime Time since last frame in seconds
     * @param maskActive Whether stencil mask is active
     */
    abstract fun render(
        renderer: io.materia.postprocessing.Renderer,
        writeBuffer: io.materia.postprocessing.RenderTarget,
        readBuffer: io.materia.postprocessing.RenderTarget,
        deltaTime: Float,
        maskActive: Boolean
    )

    /**
     * Disposes of resources used by this pass.
     * Override to clean up pass-specific resources.
     */
    override fun dispose() {
        // Base implementation - override in subclasses
    }
}

/**
 * Abstract base class for passes that use full-screen rendering.
 * Provides common functionality for shader-based post-processing.
 */
abstract class FullScreenPass : Pass() {

    /**
     * The full-screen quad used for rendering.
     */
    protected val fullScreenQuad = FullScreenQuad()

    /**
     * Renders a full-screen quad with the given material.
     */
    protected fun renderFullScreen(
        renderer: io.materia.postprocessing.Renderer,
        material: io.materia.postprocessing.ShaderMaterial,
        renderTarget: io.materia.postprocessing.RenderTarget?
    ) {
        renderer.setRenderTarget(renderTarget)
        fullScreenQuad.render(renderer, material)
    }

    override fun dispose() {
        fullScreenQuad.dispose()
        super.dispose()
    }
}

/**
 * Pass that can render to a stencil mask.
 * Used for selective post-processing on masked areas.
 */
abstract class MaskPass : Pass() {

    /**
     * Whether this mask pass is inverted (process outside mask).
     */
    var inverse: Boolean = false

    /**
     * The stencil buffer bit to use for masking.
     */
    var stencilBuffer: Int = 1

    /**
     * Clears the stencil buffer.
     */
    protected fun clearStencil(renderer: io.materia.postprocessing.Renderer) {
        renderer.state.buffers.stencil.setTest(false)
        renderer.state.buffers.stencil.setOp(
            StencilOp.Replace,
            StencilOp.Replace,
            StencilOp.Replace
        )
        renderer.state.buffers.stencil.setFunc(StencilFunc.Always, 0, 0xff)
        renderer.state.buffers.stencil.setClear(0)
        renderer.clear(false, false, true)
    }

    /**
     * Writes to the stencil buffer.
     */
    protected fun writeStencil(renderer: io.materia.postprocessing.Renderer, value: Int) {
        renderer.state.buffers.stencil.setTest(true)
        renderer.state.buffers.stencil.setOp(
            StencilOp.Keep,
            StencilOp.Keep,
            StencilOp.Replace
        )
        renderer.state.buffers.stencil.setFunc(StencilFunc.Always, value, 0xff)
    }

    /**
     * Tests against the stencil buffer.
     */
    protected fun testStencil(renderer: io.materia.postprocessing.Renderer, value: Int) {
        renderer.state.buffers.stencil.setTest(true)
        renderer.state.buffers.stencil.setFunc(
            if (inverse) StencilFunc.NotEqual else StencilFunc.Equal,
            value,
            0xff
        )
    }
}

/**
 * Enum for stencil operations.
 */
enum class StencilOp {
    Keep,
    Zero,
    Replace,
    Increment,
    IncrementWrap,
    Decrement,
    DecrementWrap,
    Invert
}

/**
 * Enum for stencil test functions.
 */
enum class StencilFunc {
    Never,
    Less,
    Equal,
    LessEqual,
    Greater,
    NotEqual,
    GreaterEqual,
    Always
}

/**
 * Full-screen quad for rendering post-processing effects
 */
class FullScreenQuad : Disposable {
    fun render(
        renderer: io.materia.postprocessing.Renderer,
        material: io.materia.postprocessing.ShaderMaterial
    ) {
        // Render full-screen quad with material
    }

    override fun dispose() {
        // Clean up quad resources
    }
}