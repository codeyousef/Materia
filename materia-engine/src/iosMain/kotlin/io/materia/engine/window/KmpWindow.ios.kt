package io.materia.engine.window

actual object WindowFactory {
    actual fun create(config: WindowConfig): KmpWindow {
        throw UnsupportedOperationException(
            "iOS window creation is host-owned. Provide an MTKView-backed RenderSurface instead of calling WindowFactory.create()."
        )
    }
}