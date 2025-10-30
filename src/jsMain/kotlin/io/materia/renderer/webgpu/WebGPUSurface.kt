/**
 * T023: WebGPUSurface Implementation
 * Feature: 019-we-should-not
 *
 * WebGPU surface wrapper for HTMLCanvasElement.
 */

package io.materia.renderer.webgpu

import io.materia.renderer.RenderSurface
import org.w3c.dom.HTMLCanvasElement

/**
 * WebGPU surface implementation wrapping an HTMLCanvasElement.
 *
 * Usage:
 * ```kotlin
 * val canvas = document.getElementById("canvas") as HTMLCanvasElement
 * val surface = WebGPUSurface(canvas)
 * val renderer = RendererFactory.create(surface).getOrThrow()
 * ```
 *
 * @property canvas HTML canvas element
 */
class WebGPUSurface(
    private val canvas: HTMLCanvasElement
) : RenderSurface {

    /**
     * Canvas width in pixels.
     */
    override val width: Int
        get() = canvas.width

    /**
     * Canvas height in pixels.
     */
    override val height: Int
        get() = canvas.height

    /**
     * Get HTMLCanvasElement.
     *
     * @return HTMLCanvasElement instance
     */
    override fun getHandle(): Any = canvas

    /**
     * Get canvas element (typed accessor).
     *
     * @return HTMLCanvasElement instance
     */
    fun getCanvasElement(): HTMLCanvasElement = canvas

    /**
     * Get WebGPU canvas context.
     *
     * @return GPUCanvasContext or null if WebGPU not available
     */
    fun getWebGPUContext(): dynamic {
        return canvas.getContext("webgpu")
    }

    /**
     * Get WebGL2 context (fallback).
     *
     * @return WebGL2RenderingContext or null if WebGL2 not available
     */
    fun getWebGLContext(): dynamic {
        return canvas.getContext("webgl2") ?: canvas.getContext("webgl")
    }

    /**
     * Resize canvas to new dimensions.
     *
     * @param width New width in pixels
     * @param height New height in pixels
     */
    fun resize(width: Int, height: Int) {
        canvas.width = width
        canvas.height = height
    }
}
