/**
 * Native stub for SurfaceFactory.
 * Native platforms are not primary targets for KreeKt.
 */

package io.kreekt.renderer

/**
 * Native actual for SurfaceFactory object.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual object SurfaceFactory {
    actual fun create(handle: Any): RenderSurface {
        throw UnsupportedOperationException("Native platforms are not supported")
    }
}
