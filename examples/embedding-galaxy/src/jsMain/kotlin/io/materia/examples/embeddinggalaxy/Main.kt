package io.materia.examples.embeddinggalaxy

import io.materia.renderer.webgpu.WebGPUSurface
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLPreElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.roundToInt

private val scope = MainScope()

fun main() {
    scope.launch {
        val canvas = ensureCanvas()
        val surface = WebGPUSurface(canvas)
        val example = EmbeddingGalaxyExample(performanceProfile = PerformanceProfile.Web)
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

        canvas.style.cursor = "grab"
        canvas.style.setProperty("touch-action", "none")
        canvas.oncontextmenu = { event ->
            event.preventDefault()
            false
        }

        val pointerState = PointerState()

        canvas.addEventListener("pointerdown", { event ->
            val pointerEvent = event as? PointerEvent ?: return@addEventListener
            pointerState.active = true
            pointerState.pointerId = pointerEvent.pointerId
            pointerState.lastX = pointerEvent.clientX.toDouble()
            pointerState.lastY = pointerEvent.clientY.toDouble()
            canvas.setPointerCapture(pointerState.pointerId)
            canvas.style.cursor = "grabbing"
            pointerEvent.preventDefault()
        })

        canvas.addEventListener("pointermove", { event ->
            val pointerEvent = event as? PointerEvent ?: return@addEventListener
            if (!pointerState.active || pointerEvent.pointerId != pointerState.pointerId) return@addEventListener
            val currentX = pointerEvent.clientX.toDouble()
            val currentY = pointerEvent.clientY.toDouble()
            val deltaX = (currentX - pointerState.lastX).toFloat()
            val deltaY = (currentY - pointerState.lastY).toFloat()
            pointerState.lastX = currentX
            pointerState.lastY = currentY
            val orbitSensitivity = 0.0035f
            runtime.orbit(deltaX * orbitSensitivity, deltaY * orbitSensitivity)
            pointerEvent.preventDefault()
        })

        val endDrag: (PointerEvent) -> Unit = { pointerEvent ->
            if (pointerState.active && pointerEvent.pointerId == pointerState.pointerId) {
                pointerState.active = false
                if (pointerState.pointerId != -1) {
                    canvas.releasePointerCapture(pointerState.pointerId)
                }
                pointerState.pointerId = -1
                canvas.style.cursor = "grab"
            }
            pointerEvent.preventDefault()
        }

        canvas.addEventListener("pointerup", { event ->
            val pointerEvent = event as? PointerEvent ?: return@addEventListener
            endDrag(pointerEvent)
        })

        canvas.addEventListener("pointercancel", { event ->
            val pointerEvent = event as? PointerEvent ?: return@addEventListener
            endDrag(pointerEvent)
        })

        canvas.addEventListener("wheel", { event ->
            val wheel = event as? WheelEvent ?: return@addEventListener
            val zoomDelta = (-wheel.deltaY / 900.0).toFloat()
            runtime.zoom(zoomDelta)
            wheel.preventDefault()
        }, js("{ passive: false }"))

        canvas.addEventListener("dblclick", { event ->
            runtime.resetView()
            runtime.resetSequence()
            event.preventDefault()
        })

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

private class PointerState {
    var active: Boolean = false
    var pointerId: Int = -1
    var lastX: Double = 0.0
    var lastY: Double = 0.0
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
