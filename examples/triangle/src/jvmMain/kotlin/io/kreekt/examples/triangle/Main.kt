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
    if (!glfwInit()) {
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

    try {
        val surface = SurfaceFactory.create(window)
        val log = example.boot(renderSurface = surface)

        println(log.pretty())
        println("✅ Triangle rendered. Close the window to exit.")

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            Thread.sleep(16)
        }
    } finally {
        glfwDestroyWindow(window)
        glfwTerminate()
        errorCallback.free()
    }
}
