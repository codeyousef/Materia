package io.kreekt.renderer

import android.view.SurfaceHolder
import android.view.SurfaceView

actual object SurfaceFactory {
    actual fun create(handle: Any): RenderSurface {
        return when (handle) {
            is RenderSurface -> handle
            is SurfaceView -> AndroidRenderSurface(handle.holder, handle)
            is SurfaceHolder -> AndroidRenderSurface(handle)
            else -> throw IllegalArgumentException(
                "Unsupported handle type for Android SurfaceFactory: ${handle::class.qualifiedName}"
            )
        }
    }
}
