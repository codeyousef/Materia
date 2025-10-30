package io.materia.examples.triangle

import io.materia.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLPreElement

fun main() {
    val scope = MainScope()
    scope.launch {
        val canvas = ensureCanvas()
        val surface = WebGPUSurface(canvas)

        val example = TriangleExample()
        val result = example.boot(
            renderSurface = surface,
            widthOverride = canvas.width,
            heightOverride = canvas.height
        )
        val message = result.log.pretty()

        println(message)

        val pre = (document.createElement("pre") as HTMLPreElement).apply {
            textContent = message
            style.fontFamily = "monospace"
            style.backgroundColor = "#111"
            style.color = "#50fa7b"
            style.padding = "16px"
            style.margin = "24px"
            style.borderRadius = "8px"
        }

        document.body?.appendChild(pre)

        fun renderLoop(timestamp: Double) {
            result.renderFrame()
            window.requestAnimationFrame(::renderLoop)
        }

        window.requestAnimationFrame(::renderLoop)

        window.onresize = {
            val width = canvas.clientWidth.takeIf { it > 0 } ?: canvas.width
            val height = canvas.clientHeight.takeIf { it > 0 } ?: canvas.height
            canvas.width = width
            canvas.height = height
            surface.resize(width, height)
            result.resize(width, height)
            result.renderFrame()
            null
        }
    }
}

private fun ensureCanvas(): HTMLCanvasElement {
    val existing = document.getElementById("triangle-canvas")
    if (existing is HTMLCanvasElement) return existing

    val canvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
        id = "triangle-canvas"
        width = 640
        height = 480
        style.width = "640px"
        style.height = "480px"
        style.display = "block"
        style.margin = "24px auto"
        style.backgroundColor = "#000"
    }
    document.body?.appendChild(canvas)
    return canvas
}
