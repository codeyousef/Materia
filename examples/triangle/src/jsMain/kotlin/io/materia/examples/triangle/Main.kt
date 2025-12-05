package io.materia.examples.triangle

import io.materia.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement

private val console = js("console")

fun main() {
    val scope = MainScope()
    scope.launch {
        val canvas = ensureCanvas()
        
        // Ensure canvas has proper render dimensions (not just CSS size)
        val renderWidth = canvas.clientWidth.takeIf { it > 0 } ?: canvas.width.takeIf { it > 0 } ?: 1280
        val renderHeight = canvas.clientHeight.takeIf { it > 0 } ?: canvas.height.takeIf { it > 0 } ?: 720
        canvas.width = renderWidth
        canvas.height = renderHeight
        
        console.log("Canvas: render=${canvas.width}x${canvas.height}, client=${canvas.clientWidth}x${canvas.clientHeight}")
        
        val surface = WebGPUSurface(canvas)

        val example = TriangleExample()
        val result = example.boot(
            renderSurface = surface,
            widthOverride = canvas.width,
            heightOverride = canvas.height
        )
        val message = result.log.pretty()

        println(message)

        // Update the info overlay stats instead of appending a pre element
        document.getElementById("objects")?.textContent = "2"
        document.getElementById("renderer")?.textContent = "WebGPU"
        
        // Hide loading overlay
        document.getElementById("loading-overlay")?.let { 
            it.asDynamic().style.display = "none"
        }

        var frameCount = 0
        fun renderLoop(timestamp: Double) {
            result.renderFrame()
            frameCount++
            if (frameCount % 60 == 0) {
                console.log("Rendered $frameCount frames")
            }
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
    // Try to find the canvas from the HTML template first
    val existing = document.getElementById("materia-canvas") 
        ?: document.getElementById("triangle-canvas")
    if (existing is HTMLCanvasElement) {
        // Set proper dimensions if not already set
        if (existing.width == 0) existing.width = 640
        if (existing.height == 0) existing.height = 480
        return existing
    }

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
