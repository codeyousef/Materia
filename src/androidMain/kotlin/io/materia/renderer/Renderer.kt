package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.scene.Scene

actual interface Renderer {
    actual val backend: BackendType
    actual val capabilities: RendererCapabilities
    actual val stats: RenderStats

    actual suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit>
    actual fun render(scene: Scene, camera: Camera)
    actual fun resize(width: Int, height: Int)
    actual fun dispose()
}
