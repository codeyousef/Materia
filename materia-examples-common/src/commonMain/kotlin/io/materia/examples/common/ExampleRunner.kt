package io.materia.examples.common

import io.materia.camera.Camera
import io.materia.core.scene.Scene
import io.materia.core.time.Clock
import io.materia.renderer.Renderer

class ExampleRunner(
    private val scene: Scene,
    private val camera: Camera,
    private val renderer: Renderer,
    private val onUpdate: (deltaSeconds: Float) -> Unit
) {
    private val clock = Clock()

    fun start() {
        clock.reset()
    }

    fun tick() {
        val delta = clock.getDeltaSeconds()
        onUpdate(delta)
        renderer.render(scene, camera)
    }
}
