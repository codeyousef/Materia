/**
 * Native stub for Renderer interface.
 * Native platforms are not primary targets for Materia.
 */

package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.scene.Scene

/**
 * Native actual for Renderer interface.
 * This is a stub implementation as native platforms are not primary targets.
 */
actual interface Renderer {
    actual val backend: BackendType
    actual val capabilities: RendererCapabilities
    actual val stats: RenderStats

    actual suspend fun initialize(config: RendererConfig): io.materia.core.Result<Unit>
    actual fun render(scene: Scene, camera: Camera)
    actual fun resize(width: Int, height: Int)
    actual fun dispose()
}
