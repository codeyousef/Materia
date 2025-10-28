package io.kreekt.examples.triangle

import io.kreekt.gpu.GpuBackend
import io.kreekt.renderer.SurfaceFactory
import kotlinx.coroutines.runBlocking
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil

fun main() = runBlocking {
    println("🚀 KreeKt Triangle MVP (JVM)")
    println("================================")

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

    val window = glfwCreateWindow(640, 480, "KreeKt Triangle (Vulkan)", MemoryUtil.NULL, MemoryUtil.NULL)
    if (window == MemoryUtil.NULL) {
        glfwTerminate()
        errorCallback.free()
        error("Failed to create GLFW window")
    }

    glfwShowWindow(window)

    val example = TriangleExample(preferredBackends = listOf(GpuBackend.VULKAN))

    val runtime = runCatching {
        val surface = SurfaceFactory.create(window)
        example.boot(renderSurface = surface)
    }.getOrElse { throwable ->
        println("⚠️ Triangle renderer failed to acquire GPU surface: ${throwable.message ?: throwable::class.simpleName}")
        println("   Falling back to headless bootstrap so the smoke test can proceed.")
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()

        val headlessResult = example.boot(renderSurface = null)
        println(headlessResult.log.pretty())
        println("⚠️ Headless mode active – no swapchain available in this environment.")
        return@runBlocking
    }

    println(runtime.log.pretty())
    println("✅ Triangle rendered. Close the window to exit.")

    glfwSetFramebufferSizeCallback(window) { _, width, height ->
        runtime.resize(width, height)
    }

    try {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            runtime.renderFrame()
            Thread.sleep(16)
        }
    } finally {
        runtime.dispose()
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}
