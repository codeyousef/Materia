/**
 * T022: Renderer Actual (JS)
 * Feature: 019-we-should-not
 *
 * JS actual declaration for Renderer interface.
 */

package io.materia.renderer

import io.materia.camera.Camera
import io.materia.core.scene.Scene

/**
 * JS actual for Renderer interface.
 *
 * Implemented by WebGPURenderer (primary) or WebGLRenderer (fallback).
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
