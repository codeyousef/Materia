package io.materia.engine.window

import io.materia.renderer.RenderSurface

actual interface KmpWindow : io.materia.engine.core.Disposable {
    actual val width: Int
    actual val height: Int
    actual val pixelRatio: Float
    actual val physicalWidth: Int
    actual val physicalHeight: Int
    actual var title: String
    actual val shouldClose: Boolean
    actual val isFocused: Boolean
    actual val isVisible: Boolean
    actual fun getNativeHandle(): Any
    actual fun createRenderSurface(): RenderSurface
    actual fun pollEvents()
    actual fun requestClose()
    actual fun addEventListener(listener: WindowEventListener)
    actual fun removeEventListener(listener: WindowEventListener)
}