/**
 * Native stub for RenderSurface.
 * Native platforms are not primary targets for KreeKt.
 */

package io.kreekt.renderer

/**
 * Native actual for RenderSurface interface.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual interface RenderSurface {
    actual val width: Int
    actual val height: Int
    actual fun getHandle(): Any
}
