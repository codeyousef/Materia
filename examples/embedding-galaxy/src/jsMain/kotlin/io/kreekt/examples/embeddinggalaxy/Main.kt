package io.kreekt.examples.embeddinggalaxy

import io.kreekt.examples.common.ExampleRunner
import io.kreekt.examples.common.Hud
import io.kreekt.renderer.BackendType
import io.kreekt.renderer.RendererConfig
import io.kreekt.renderer.RendererFactory
import io.kreekt.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement

private val scope = MainScope()

fun main() {
    scope.launch {
        val canvas = ensureCanvas()
        val surface = WebGPUSurface(canvas)
        val rendererResult = RendererFactory.create(
            surface,
            RendererConfig(preferredBackend = BackendType.WEBGL)
        )
        val renderer = rendererResult.getOrThrow()
        renderer.initialize(RendererConfig()).getOrThrow()

        val scene = EmbeddingGalaxyScene()
        val hud = Hud()
        scene.attachHud(hud)

        val runner = ExampleRunner(scene.scene, scene.camera, renderer) { delta ->
            scene.update(delta)
        }
        runner.start()
        scene.resize(canvas.width.toFloat() / canvas.height.toFloat())

        fun resize() {
            val width = window.innerWidth
            val height = window.innerHeight
            canvas.width = width
            canvas.height = height
            renderer.resize(width, height)
            if (height > 0) {
                scene.resize(width.toFloat() / height.toFloat())
            }
        }

        window.onresize = { resize(); null }
        resize()

        window.addEventListener("keydown", { event ->
            when ((event as? org.w3c.dom.events.KeyboardEvent)?.key?.lowercase()) {
                "q" -> scene.triggerQuery()
                "r" -> scene.resetSequence()
                "p" -> scene.togglePause()
            }
        })

        fun animate() {
            runner.tick()
            window.requestAnimationFrame { animate() }
        }
        animate()
    }
}

private fun ensureCanvas(): HTMLCanvasElement {
    val existing = document.getElementById("embedding-galaxy-canvas")
    if (existing is HTMLCanvasElement) return existing

    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.id = "embedding-galaxy-canvas"
    canvas.style.width = "100vw"
    canvas.style.height = "100vh"
    canvas.style.display = "block"
    canvas.style.backgroundColor = "#0b1020"
    document.body?.appendChild(canvas)
    return canvas
}
