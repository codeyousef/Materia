package io.kreekt.examples.embeddinggalaxy

import io.kreekt.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.roundToInt

private val scope = MainScope()

fun main() {
    scope.launch {
        val canvas = ensureCanvas()
        val surface = WebGPUSurface(canvas)
        val example = EmbeddingGalaxyExample()
        val boot = example.boot(
            renderSurface = surface,
            widthOverride = canvas.width,
            heightOverride = canvas.height
        )

        val runtime = boot.runtime
        println(boot.log.pretty())

        val overlay = ensureOverlay(boot.log.pretty())
        var lastTimestamp = window.performance.now()

        fun frame(timestamp: Double) {
            val deltaSeconds = ((timestamp - lastTimestamp) / 1000.0).toFloat().coerceIn(0f, 0.1f)
            lastTimestamp = timestamp
            runtime.frame(deltaSeconds)
            updateOverlay(overlay, runtime)
            window.requestAnimationFrame(::frame)
        }

        window.requestAnimationFrame { timestamp ->
            lastTimestamp = timestamp
            window.requestAnimationFrame(::frame)
        }

        window.onresize = {
            val width = canvas.clientWidth.takeIf { it > 0 } ?: window.innerWidth
            val height = canvas.clientHeight.takeIf { it > 0 } ?: window.innerHeight
            canvas.width = width
            canvas.height = height
            runtime.resize(width, height)
            null
        }

        window.addEventListener("keydown", { event ->
            val key = (event as? KeyboardEvent)?.key?.lowercase() ?: return@addEventListener
            when (key) {
                " " -> runtime.togglePause()
                "q" -> runtime.triggerQuery()
                "r" -> runtime.resetSequence()
                "[" -> runtime.setQuality(runtime.quality.previous())
                "]" -> runtime.setQuality(runtime.quality.next())
                "f" -> runtime.toggleFxaa()
            }
        })
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

private fun ensureOverlay(initialMessage: String): HTMLDivElement {
    val existing = document.getElementById("embedding-galaxy-overlay")
    if (existing is HTMLDivElement) return existing

    val wrapper = document.createElement("div") as HTMLDivElement
    wrapper.id = "embedding-galaxy-overlay"
    wrapper.style.position = "absolute"
    wrapper.style.left = "24px"
    wrapper.style.top = "24px"
    wrapper.style.padding = "12px 16px"
    wrapper.style.backgroundColor = "rgba(11, 16, 32, 0.85)"
    wrapper.style.borderRadius = "10px"
    wrapper.style.color = "#f8f8ff"
    wrapper.style.fontFamily = "JetBrains Mono, monospace"
    wrapper.style.fontSize = "13px"
    wrapper.style.lineHeight = "1.6"
    wrapper.style.setProperty("pointer-events", "none")
    wrapper.style.maxWidth = "320px"

    val header = document.createElement("pre") as HTMLPreElement
    header.textContent = initialMessage
    header.style.margin = "0 0 12px 0"

    val info = document.createElement("div") as HTMLDivElement
    info.id = "overlay-info"
    info.innerText = "Booting Embedding Galaxy…"

    wrapper.appendChild(header)
    wrapper.appendChild(info)
    document.body?.appendChild(wrapper)
    return wrapper
}

private fun updateOverlay(container: HTMLDivElement, runtime: EmbeddingGalaxyRuntime) {
    val info = container.querySelector("#overlay-info") as? HTMLDivElement ?: return
    val metrics = runtime.metrics()
    val fps = if (metrics.frameTimeMs > 0.0) {
        1000.0 / metrics.frameTimeMs
    } else {
        0.0
    }

    info.innerHTML = buildString {
        appendLine("Points   : ${runtime.activePointCount}")
        appendLine("Quality  : ${runtime.quality}")
        appendLine("FXAA     : ${if (runtime.fxaaEnabled) "on" else "off"}")
        appendLine("Frame    : ${metrics.frameTimeMs.format(2)} ms")
        append("FPS      : ${fps.format(1)}")
    }
}

private fun Double.format(decimals: Int): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10.0 }
    return ((this * factor).roundToInt() / factor).toString()
}

private fun EmbeddingGalaxyScene.Quality.previous(): EmbeddingGalaxyScene.Quality = when (this) {
    EmbeddingGalaxyScene.Quality.Performance -> EmbeddingGalaxyScene.Quality.Performance
    EmbeddingGalaxyScene.Quality.Balanced -> EmbeddingGalaxyScene.Quality.Performance
    EmbeddingGalaxyScene.Quality.Fidelity -> EmbeddingGalaxyScene.Quality.Balanced
}

private fun EmbeddingGalaxyScene.Quality.next(): EmbeddingGalaxyScene.Quality = when (this) {
    EmbeddingGalaxyScene.Quality.Performance -> EmbeddingGalaxyScene.Quality.Balanced
    EmbeddingGalaxyScene.Quality.Balanced -> EmbeddingGalaxyScene.Quality.Fidelity
    EmbeddingGalaxyScene.Quality.Fidelity -> EmbeddingGalaxyScene.Quality.Fidelity
}
