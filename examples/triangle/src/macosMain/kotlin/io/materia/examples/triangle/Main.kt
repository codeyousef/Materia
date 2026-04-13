package io.materia.examples.triangle

import io.materia.engine.window.WindowConfig
import io.materia.engine.window.WindowEventListener
import io.materia.engine.window.WindowFactory
import io.materia.gpu.GpuBackend
import kotlinx.coroutines.runBlocking
import platform.posix.usleep

fun main() = runBlocking {
    println("🍎 Materia Triangle (macOS Native)")
    println("===================================")

    val example = TriangleExample(preferredBackends = listOf(GpuBackend.MOLTENVK))
    var window = runCatching {
        WindowFactory.create(
            WindowConfig(
                title = "Materia Triangle (MoltenVK)",
                width = 960,
                height = 640,
                resizable = true
            )
        )
    }.getOrElse { error ->
        println("⚠️ Failed to create macOS window: ${error.message ?: error::class.simpleName}")
        println("   Falling back to headless bootstrap so the native smoke path can still report state.")

        val headless = example.boot(renderSurface = null)
        println(headless.log.pretty())
        println("⚠️ Headless mode active – native window bootstrap unavailable in this environment.")
        return@runBlocking
    }

    val runtime = runCatching {
        val renderSurface = window.createRenderSurface()
        example.boot(
            renderSurface = renderSurface,
            widthOverride = window.physicalWidth,
            heightOverride = window.physicalHeight
        )
    }.getOrElse { error ->
        println("⚠️ Triangle renderer failed to acquire native Apple surface: ${error.message ?: error::class.simpleName}")
        println("   Falling back to headless bootstrap so the native smoke path can still report state.")
        window.dispose()

        val headless = example.boot(renderSurface = null)
        println(headless.log.pretty())
        println("⚠️ Headless mode active – no native swapchain available in this environment.")
        return@runBlocking
    }

    println(runtime.log.pretty())
    println("✅ Native triangle rendered. Close the window to exit.")

    window.addEventListener(object : WindowEventListener {
        override fun onResize(width: Int, height: Int) {
            runtime.resize(width, height)
        }
    })

    try {
        while (!window.shouldClose) {
            window.pollEvents()
            runtime.renderFrame()
            usleep(16_000u)
        }
    } finally {
        runtime.dispose()
        window.dispose()
    }
}