package io.materia.examples.volumetexture

import io.materia.renderer.BackendType
import io.materia.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil

fun main() = runBlocking {
    println("🧊 Materia Volume Texture Example (JVM)")
    println("======================================")

    val errorCallback = GLFWErrorCallback.createPrint(System.err).set()
    val glfwInitialised = glfwInit()
    if (!glfwInitialised) {
        errorCallback.free()
        error("Failed to initialise GLFW")
    }

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    val window = glfwCreateWindow(
        1280,
        720,
        "Materia Volume Texture (Vulkan)",
        MemoryUtil.NULL,
        MemoryUtil.NULL
    )
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        errorCallback.free()
        error("Failed to create GLFW window")
    }

    glfwShowWindow(window)

    val example = VolumeTextureExample(preferredBackend = BackendType.VULKAN)
    val bootResult = runCatching {
        val surface = SurfaceFactory.create(window)
        example.boot(renderSurface = surface)
    }.getOrElse { throwable ->
        println(
            "⚠️ Volume texture renderer failed to acquire GPU surface: " +
                (throwable.message ?: throwable::class.simpleName)
        )
        println("   Falling back to headless bootstrap so the scene setup can still be inspected.")
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()

        val headless = example.boot(renderSurface = null)
        println(headless.log.pretty())
        return@runBlocking
    }

    println(bootResult.log.pretty())
    println("✅ Volume texture scene is running. Close the window to exit.")

    var lastTimeNanos = System.nanoTime()
    glfwSetFramebufferSizeCallback(window) { _, width, height ->
        bootResult.runtime.resize(width, height)
    }

    try {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            val currentTimeNanos = System.nanoTime()
            val deltaSeconds = ((currentTimeNanos - lastTimeNanos) / 1_000_000_000.0).toFloat()
            lastTimeNanos = currentTimeNanos
            bootResult.runtime.frame(deltaSeconds)
        }
    } finally {
        bootResult.runtime.dispose()
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}