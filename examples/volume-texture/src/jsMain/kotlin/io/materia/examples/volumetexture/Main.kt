package io.materia.examples.volumetexture

import io.materia.renderer.SurfaceFactory
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

private val console = js("console")
private val scope = MainScope()

fun main() {
    scope.launch {
        val canvas = ensureCanvas()
        val overlay = ensureOverlay()
        val example = VolumeTextureExample()

        resizeCanvas(canvas)

        val bootResult = runCatching {
            val surface = SurfaceFactory.create(canvas)
            example.boot(
                renderSurface = surface,
                widthOverride = canvas.width,
                heightOverride = canvas.height
            )
        }.getOrElse { error ->
            val message = buildString {
                appendLine("Volume texture example failed to start.")
                appendLine(error.message ?: error::class.simpleName ?: "Unknown error")
            }
            overlay.textContent = message
            console.error(message)
            return@launch
        }

        overlay.textContent = bootResult.log.pretty()
        console.log(bootResult.log.pretty())

        var lastTimestamp = 0.0
        fun renderLoop(timestamp: Double) {
            val deltaSeconds = if (lastTimestamp == 0.0) {
                1.0f / 60.0f
            } else {
                ((timestamp - lastTimestamp) / 1000.0).toFloat()
            }
            lastTimestamp = timestamp
            bootResult.runtime.frame(deltaSeconds)
            window.requestAnimationFrame(::renderLoop)
        }

        window.onresize = {
            resizeCanvas(canvas)
            bootResult.runtime.resize(canvas.width, canvas.height)
            null
        }

        window.requestAnimationFrame(::renderLoop)
    }
}

private fun ensureCanvas(): HTMLCanvasElement {
    val existing = document.getElementById("volume-texture-canvas")
    if (existing is HTMLCanvasElement) {
        return existing
    }

    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.id = "volume-texture-canvas"
    document.getElementById("app-root")?.appendChild(canvas)
    return canvas
}

private fun ensureOverlay(): HTMLElement {
    val existing = document.getElementById("volume-texture-overlay")
    if (existing is HTMLElement) {
        return existing
    }

    val overlay = document.createElement("pre") as HTMLElement
    overlay.id = "volume-texture-overlay"
    overlay.className = "volume-overlay"
    document.getElementById("app-root")?.appendChild(overlay)
    return overlay
}

private fun resizeCanvas(canvas: HTMLCanvasElement) {
    val parent = canvas.parentElement ?: document.body
    val width = parent?.clientWidth?.takeIf { it > 0 } ?: window.innerWidth
    val height = parent?.clientHeight?.takeIf { it > 0 } ?: window.innerHeight
    canvas.width = width
    canvas.height = height
    canvas.style.width = "${width}px"
    canvas.style.height = "${height}px"
}