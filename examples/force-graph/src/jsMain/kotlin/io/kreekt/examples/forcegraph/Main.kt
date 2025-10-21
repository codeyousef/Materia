package io.kreekt.examples.forcegraph

import io.kreekt.examples.common.ExampleRunner
import io.kreekt.examples.common.Hud
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
        val renderer = RendererFactory.create(surface, RendererConfig()).getOrThrow()
        renderer.initialize(RendererConfig()).getOrThrow()

        val scene = ForceGraphRerankScene()
        val hud = Hud()
        scene.attachHud(hud)

        val runner = ExampleRunner(scene.scene, scene.camera, renderer) { delta ->
            scene.update(delta)
        }
        runner.start()

        fun resize() {
            val width = canvas.clientWidth
            val height = canvas.clientHeight
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
            val key = (event as? org.w3c.dom.events.KeyboardEvent)?.key?.lowercase()
            when (key) {
                "t" -> scene.setMode(ForceGraphRerankScene.Mode.TfIdf)
                "s" -> scene.setMode(ForceGraphRerankScene.Mode.Semantic)
                "space" -> scene.toggleMode()
            }
        })

        fun loop() {
            runner.tick()
            window.requestAnimationFrame { loop() }
        }
        loop()
    }
}

private fun ensureCanvas(): HTMLCanvasElement {
    val existing = document.getElementById("force-graph-canvas")
    if (existing is HTMLCanvasElement) return existing
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.id = "force-graph-canvas"
    canvas.style.width = "100vw"
    canvas.style.height = "100vh"
    canvas.style.display = "block"
    canvas.style.backgroundColor = "#0b1020"
    document.body?.appendChild(canvas)
    return canvas
}
