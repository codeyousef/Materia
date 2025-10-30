/**
 * JS SurfaceFactory implementation
 * Creates WebGPUSurface from HTMLCanvasElement
 */

package io.materia.renderer

import io.materia.renderer.webgpu.WebGPUSurface
import org.w3c.dom.HTMLCanvasElement

/**
 * JS implementation of SurfaceFactory.
 * Creates WebGPUSurface from HTMLCanvasElement.
 */
actual object SurfaceFactory {
    /**
     * Create WebGPUSurface from HTMLCanvasElement.
     *
     * @param handle HTMLCanvasElement from DOM
     * @return WebGPUSurface ready for WebGPU renderer
     * @throws IllegalArgumentException if handle is not an HTMLCanvasElement
     */
    actual fun create(handle: Any): RenderSurface {
        return when (handle) {
            is HTMLCanvasElement -> WebGPUSurface(handle)
            else -> throw IllegalArgumentException(
                "JS SurfaceFactory.create() expects HTMLCanvasElement, got ${handle::class.simpleName}"
            )
        }
    }
}
