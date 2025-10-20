package io.kreekt.examples.common

import io.kreekt.camera.Camera
import io.kreekt.core.scene.Scene
import io.kreekt.core.time.Clock
import io.kreekt.renderer.Renderer

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
