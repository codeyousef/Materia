package io.materia.renderer

import android.graphics.Rect
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

actual interface RenderSurface {
    actual val width: Int
    actual val height: Int
    actual fun getHandle(): Any
}

internal class AndroidRenderSurface(
    private val surfaceHolder: SurfaceHolder,
    private val surfaceView: SurfaceView? = null
) : RenderSurface {

    override val width: Int
        get() = surfaceView?.width?.takeIf { it > 0 }
            ?: surfaceHolder.surfaceFrameOrNull()?.width()
            ?: 0

    override val height: Int
        get() = surfaceView?.height?.takeIf { it > 0 }
            ?: surfaceHolder.surfaceFrameOrNull()?.height()
            ?: 0

    val surface: Surface
        get() = surfaceHolder.surface

    val holder: SurfaceHolder
        get() = surfaceHolder

    override fun getHandle(): Any = surfaceHolder

    private fun SurfaceHolder.surfaceFrameOrNull(): Rect? = try {
        surfaceFrame
    } catch (_: IllegalArgumentException) {
        null
    }
}
