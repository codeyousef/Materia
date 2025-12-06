package io.materia.examples.embeddinggalaxy

import io.materia.gpu.GpuBackend
import io.materia.gpu.initializeGpuContext
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET
import org.lwjgl.glfw.GLFW.GLFW_KEY_Q
import org.lwjgl.glfw.GLFW.GLFW_KEY_R
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET
import org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil
import kotlin.math.max

fun main() = runBlocking {
    println("🌌 Materia Embedding Galaxy (JVM)")
    println("================================")

    val errorCallback = GLFWErrorCallback.createPrint(System.err).set()
    if (!glfwInit()) {
        errorCallback.free()
        error("Failed to initialise GLFW")
    }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    val width = 1280
    val height = 720

    val window = glfwCreateWindow(
        width,
        height,
        "Embedding Galaxy (Vulkan)",
        MemoryUtil.NULL,
        MemoryUtil.NULL
    )
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        errorCallback.free()
        error("Failed to create GLFW window")
    }

    glfwShowWindow(window)

    val example = EmbeddingGalaxyExample(preferredBackends = listOf(GpuBackend.VULKAN))

    val boot = runCatching {
        val surface = SurfaceFactory.create(window)
        initializeGpuContext(surface)  // Pre-initialize wgpu4k context with existing window
        example.boot(renderSurface = surface, widthOverride = width, heightOverride = height)
    }.getOrElse { throwable ->
        println("⚠️ Embedding Galaxy failed to acquire a GPU surface: ${throwable.message ?: throwable::class.simpleName}")
        println("   Falling back to headless bootstrap to keep the smoke test runnable.")
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()

        val headless =
            example.boot(renderSurface = null, widthOverride = width, heightOverride = height)
        println(headless.log.pretty())
        println("⚠️ Headless mode active – rendering loop skipped.")
        return@runBlocking
    }

    val runtime = boot.runtime
    println(boot.log.pretty())
    println("Controls: SPACE pause | Q shockwave | R reset | [ / ] quality")

    glfwSetFramebufferSizeCallback(window) { _, w, h ->
        val clampedW = max(1, w)
        val clampedH = max(1, h)
        runtime.resize(clampedW, clampedH)
    }

    glfwSetKeyCallback(window) { _, key, _, action, _ ->
        if (action != GLFW_PRESS) return@glfwSetKeyCallback
        when (key) {
            GLFW_KEY_SPACE -> runtime.togglePause()
            GLFW_KEY_Q -> runtime.triggerQuery()
            GLFW_KEY_R -> runtime.resetSequence()
            GLFW_KEY_LEFT_BRACKET -> runtime.setQuality(runtime.quality.previous())
            GLFW_KEY_RIGHT_BRACKET -> runtime.setQuality(runtime.quality.next())
        }
    }

    var lastTime = System.nanoTime()
    try {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            val now = System.nanoTime()
            val deltaSeconds = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
            lastTime = now
            runtime.frame(deltaSeconds)
            Thread.sleep(1)
        }
    } finally {
        runtime.dispose()
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
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
